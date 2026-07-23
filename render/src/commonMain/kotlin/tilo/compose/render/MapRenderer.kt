@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.Layer
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.layers.vector.VectorRenderStrategy
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import tilo.compose.core.selection.FeatureSelection
import tilo.compose.core.selection.FeatureSelectionRef
import tilo.compose.core.tile.Tile
import tilo.compose.render.backend.ComposeCanvasRenderBackend
import tilo.compose.render.backend.RenderBackend
import tilo.compose.render.backend.RenderScene
import tilo.compose.render.backend.RenderSceneBuilder
import tilo.compose.render.backend.VectorBitmapRenderSceneLayer

/**
 * Compose-first map renderer that builds a backend-agnostic [RenderScene].
 *
 * Expected missing or undecodable tiles remain isolated inside the raster
 * pipeline. [onRenderError] receives unexpected branch failures; coroutine
 * cancellation is always propagated.
 */
@Composable
@ExperimentalTiloRenderingApi
@Suppress("LongMethod") // The renderer keeps its remembered resources and effects in one Compose lifecycle.
fun MapRenderer(
    map: MapState,
    layers: List<Layer>,
    tileDecoder: (ByteArray) -> ImageBitmap? = ::decodeTileImageBitmap,
    modifier: Modifier = Modifier,
    backend: RenderBackend = ComposeCanvasRenderBackend,
    onTapWorld: ((Point) -> Unit)? = null,
    onFeatureSelect: ((List<FeatureSelection>) -> Unit)? = null,
    onRenderError: ((Throwable) -> Unit)? = null,
    selectedFeatures: Set<FeatureSelectionRef> = emptySet(),
    invalidationKey: Any? = null,
    onMapChanged: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val textMeasurer = rememberTextMeasurer(cacheSize = LABEL_TEXT_LAYOUT_CACHE_SIZE)
    val offscreenLabelDrawScope = remember { CanvasDrawScope() }
    val labelBitmapCache = remember { LabelBitmapCache() }
    val rasterPipeline = remember { RasterRenderPipeline() }
    val vectorPipeline = remember { VectorRenderPipeline() }
    val featureHitTester = remember { FeatureHitTester() }
    val layerTree = remember(layers) { ResolvedLayerTree.resolve(layers) }
    val layerFlowKey = layerTree.key
    val overviewRequestTracker = remember(layerFlowKey) { OverviewRequestTracker() }

    val renderRequests =
        remember(layerFlowKey) {
            MutableSharedFlow<MapState>(
                replay = 1,
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        }
    val overviewRequests =
        remember(layerFlowKey) {
            MutableSharedFlow<OverviewRenderRequest>(
                replay = 1,
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        }

    val activeLayers = remember(layerTree, map.zoom) { layerTree.activeAt(map.zoom) }
    val tileLayers = remember(activeLayers) { activeLayers.filterIsInstance<TileLayer>() }
    val vectorLayers = remember(activeLayers) { activeLayers.filterIsInstance<VectorLayer>() }
    val currentRasterSourceIdentities = remember(tileLayers) { tileLayers.sourceIdentitiesByLayer() }
    val vectorLayerCacheSignature = vectorLayers.cacheSignature()
    val currentVectorCacheKeys =
        vectorLayers.associate { layer ->
            layer.id to layer.cacheKey(selectedFeatures.keysForLayer(layer.id), map.zoom)
        }
    val renderLoopInput by rememberUpdatedState(
        RenderLoopInput(
            sortedLayers = activeLayers,
            tileLayers = tileLayers,
            vectorLayers = vectorLayers,
            currentVectorCacheKeys = currentVectorCacheKeys,
            currentRasterSourceIdentities = currentRasterSourceIdentities,
            density = density,
            layoutDirection = layoutDirection,
            selectedFeatures = selectedFeatures,
            tileDecoder = tileDecoder,
            backend = backend,
            onRenderError = onRenderError,
        ),
    )

    var redrawVersion by remember { mutableStateOf(0) }
    var scene by remember { mutableStateOf(RenderScene.Empty) }
    var lastRasterFrame by remember { mutableStateOf(RasterFrame.Empty) }
    var lastOverviewFrame by remember { mutableStateOf(RasterFrame.Empty) }
    var lastVectorCommandsByLayer by remember { mutableStateOf<Map<String, List<RenderCommand>>>(emptyMap()) }
    var lastVectorBitmapsByLayer by remember { mutableStateOf<Map<String, VectorBitmapRenderSceneLayer>>(emptyMap()) }
    var lastVectorCacheKeysByLayer by remember { mutableStateOf<Map<String, VectorLayerCacheKey>>(emptyMap()) }

    LaunchedEffect(activeLayers, vectorLayerCacheSignature, invalidationKey) {
        val validVectorCommands =
            lastVectorCommandsByLayer.validCommandsFor(currentVectorCacheKeys, lastVectorCacheKeysByLayer)
        val validVectorBitmaps = lastVectorBitmapsByLayer.validFor(currentVectorCacheKeys, lastVectorCacheKeysByLayer)
        if (validVectorCommands.size != lastVectorCommandsByLayer.size) {
            lastVectorCommandsByLayer = validVectorCommands
        }
        if (validVectorBitmaps.size != lastVectorBitmapsByLayer.size) {
            lastVectorBitmapsByLayer = validVectorBitmaps
        }

        val displayRasterFrame =
            mergeRasterFrames(
                placeholderFrame = RasterFrame.Empty,
                fallbackFrame = lastRasterFrame,
                overviewFrame = lastOverviewFrame,
                currentFrame = RasterFrame.Empty,
                currentSourceIdentities = currentRasterSourceIdentities,
            )
        val nextScene =
            RenderSceneBuilder.build(
                layers = activeLayers,
                tilesByLayer = displayRasterFrame.tilesByLayer,
                commandsByLayer = validVectorCommands,
                vectorBitmapsByLayer = validVectorBitmaps,
                decodedImagesByLayer = displayRasterFrame.decodedImagesByLayer,
            )
        scene = nextScene
        backend.onScene(nextScene)
    }

    LaunchedEffect(
        activeLayers,
        redrawVersion,
        map.center,
        map.zoom,
        map.viewport.width,
        map.viewport.height,
        map.viewport.pixelRatio,
        density,
        layoutDirection,
        vectorLayerCacheSignature,
        selectedFeatures,
        invalidationKey,
    ) {
        if (map.viewport.width <= 0 || map.viewport.height <= 0) return@LaunchedEffect
        val renderMap =
            MapState(
                center = map.center,
                zoom = map.zoom,
                projection = map.projection,
                config = map.config,
                viewport = map.viewport,
            )
        renderRequests.emit(renderMap)
        overviewRequests.emit(overviewRequestTracker.next(renderMap))
    }

    LaunchedEffect(activeLayers) {
        renderRequests.collectLatestRenderRequest { renderMap ->
            val input = renderLoopInput
            supervisorScope {
                var tilesByLayer: Map<String, List<Tile>> = emptyMap()
                var decodedImagesByLayer: Map<String, List<ImageBitmap?>> = emptyMap()
                var commandsByLayer: Map<String, List<RenderCommand>> =
                    lastVectorCommandsByLayer.validCommandsFor(input.currentVectorCacheKeys, lastVectorCacheKeysByLayer)
                var vectorBitmapsByLayer: Map<String, VectorBitmapRenderSceneLayer> =
                    lastVectorBitmapsByLayer.validFor(input.currentVectorCacheKeys, lastVectorCacheKeysByLayer)
                val placeholderFrame = rasterPipeline.buildPlaceholderFrame(input.tileLayers, renderMap)

                fun publishScene() {
                    val displayRasterFrame =
                        mergeRasterFrames(
                            placeholderFrame = placeholderFrame,
                            fallbackFrame = lastRasterFrame,
                            overviewFrame = lastOverviewFrame,
                            currentFrame =
                                RasterFrame(
                                    tilesByLayer = tilesByLayer,
                                    decodedImagesByLayer = decodedImagesByLayer,
                                    sourceIdentitiesByLayer = input.currentRasterSourceIdentities,
                                ),
                            currentSourceIdentities = input.currentRasterSourceIdentities,
                        )
                    val nextScene =
                        RenderSceneBuilder.build(
                            layers = input.sortedLayers,
                            tilesByLayer = displayRasterFrame.tilesByLayer,
                            commandsByLayer = commandsByLayer,
                            vectorBitmapsByLayer = vectorBitmapsByLayer,
                            decodedImagesByLayer = displayRasterFrame.decodedImagesByLayer,
                        )
                    scene = nextScene
                    input.backend.onScene(nextScene)
                }

                publishScene()

                launch {
                    runRenderBranch(onError = input.onRenderError) {
                        val vectorFrame =
                            vectorPipeline.buildFrame(
                                vectorLayers = input.vectorLayers,
                                map = renderMap,
                                density = input.density,
                                layoutDirection = input.layoutDirection,
                                selectedFeatures = input.selectedFeatures,
                                reusableBitmapsByLayer = vectorBitmapsByLayer,
                            )
                        commandsByLayer = vectorFrame.commandsByLayer
                        vectorBitmapsByLayer = vectorFrame.bitmapLayersByLayer
                        lastVectorCommandsByLayer = commandsByLayer
                        lastVectorBitmapsByLayer = vectorBitmapsByLayer
                        lastVectorCacheKeysByLayer = vectorFrame.cacheKeysByLayer
                        publishScene()
                    }
                }

                launch {
                    runRenderBranch(onError = input.onRenderError) {
                        val rasterFrame =
                            rasterPipeline.buildVisibleFrame(
                                tileLayers = input.tileLayers,
                                map = renderMap,
                                tileDecoder = input.tileDecoder,
                            )
                        tilesByLayer = rasterFrame.tilesByLayer
                        decodedImagesByLayer = rasterFrame.decodedImagesByLayer
                        val renderableRasterFrame = rasterFrame.withRenderableTilesOnly()
                        if (renderableRasterFrame.hasTiles()) {
                            lastRasterFrame = renderableRasterFrame
                        }
                        publishScene()
                    }
                }

                launch {
                    runRenderBranch(onError = input.onRenderError) {
                        rasterPipeline.prefetch(input.tileLayers, renderMap)
                    }
                }
            }
        }
    }

    LaunchedEffect(activeLayers) {
        var overviewPrefetchJob: Job? = null
        overviewRequests.collectLatest { request ->
            val input = renderLoopInput
            runRenderBranch(onError = input.onRenderError) {
                val overviewMap = request.map
                val overviewFrame =
                    rasterPipeline
                        .buildOverviewFrame(
                            tileLayers = input.tileLayers,
                            map = overviewMap,
                            tileDecoder = input.tileDecoder,
                        ).withRenderableTilesOnly()
                if (!overviewFrame.hasTiles()) return@runRenderBranch
                if (!overviewRequestTracker.isLatest(request)) return@runRenderBranch

                lastOverviewFrame = overviewFrame
                val placeholderFrame = rasterPipeline.buildPlaceholderFrame(input.tileLayers, overviewMap)
                val validVectorCommands =
                    lastVectorCommandsByLayer.validCommandsFor(input.currentVectorCacheKeys, lastVectorCacheKeysByLayer)
                val validVectorBitmaps =
                    lastVectorBitmapsByLayer.validFor(input.currentVectorCacheKeys, lastVectorCacheKeysByLayer)
                val displayRasterFrame =
                    mergeRasterFrames(
                        placeholderFrame = placeholderFrame,
                        fallbackFrame = lastRasterFrame,
                        overviewFrame = overviewFrame,
                        currentFrame = RasterFrame.Empty,
                        currentSourceIdentities = input.currentRasterSourceIdentities,
                    )
                val nextScene =
                    RenderSceneBuilder.build(
                        layers = input.sortedLayers,
                        tilesByLayer = displayRasterFrame.tilesByLayer,
                        commandsByLayer = validVectorCommands,
                        vectorBitmapsByLayer = validVectorBitmaps,
                        decodedImagesByLayer = displayRasterFrame.decodedImagesByLayer,
                    )
                scene = nextScene
                input.backend.onScene(nextScene)

                overviewPrefetchJob?.cancel()
                overviewPrefetchJob =
                    launch {
                        runRenderBranch(onError = input.onRenderError) {
                            rasterPipeline.prefetchOverview(input.tileLayers, overviewMap)
                        }
                    }
            }
        }
    }

    val interactionModifier =
        modifier
            .onSizeChanged { size ->
                map.viewport =
                    Viewport(
                        width = size.width,
                        height = size.height,
                        pixelRatio = density.density.toDouble(),
                    )
                redrawVersion++
                onMapChanged?.invoke()
            }.mapGestureInput(map) {
                redrawVersion++
                onMapChanged?.invoke()
            }.mapTapInput(
                map = map,
                onTap =
                    if (onTapWorld == null && onFeatureSelect == null) {
                        null
                    } else {
                        { screenPoint, worldPoint ->
                            onFeatureSelect?.invoke(featureHitTester.hitTest(map, vectorLayers, screenPoint))
                            onTapWorld?.invoke(worldPoint)
                        }
                    },
            ) {
                redrawVersion++
                onMapChanged?.invoke()
            }.drawWithContent {
                redrawVersion
                drawContent()
            }

    backend.Content(
        modifier = interactionModifier,
        scene = scene,
        map = map,
        tileDecoder = tileDecoder,
        offscreenLabelDrawScope = offscreenLabelDrawScope,
        textMeasurer = textMeasurer,
        labelBitmapCache = labelBitmapCache,
    )
}

internal suspend fun <T> Flow<T>.collectLatestRenderRequest(block: suspend (T) -> Unit) {
    collectLatest(block)
}

private data class RenderLoopInput(
    val sortedLayers: List<Layer>,
    val tileLayers: List<TileLayer>,
    val vectorLayers: List<VectorLayer>,
    val currentVectorCacheKeys: Map<String, VectorLayerCacheKey>,
    val currentRasterSourceIdentities: Map<String, Any>,
    val density: Density,
    val layoutDirection: LayoutDirection,
    val selectedFeatures: Set<FeatureSelectionRef>,
    val tileDecoder: (ByteArray) -> ImageBitmap?,
    val backend: RenderBackend,
    val onRenderError: ((Throwable) -> Unit)?,
)

internal data class OverviewRenderRequest(
    val id: Long,
    val map: MapState,
)

internal class OverviewRequestTracker {
    private var latestId = 0L

    fun next(map: MapState): OverviewRenderRequest {
        latestId += 1
        return OverviewRenderRequest(id = latestId, map = map)
    }

    fun isLatest(request: OverviewRenderRequest): Boolean = request.id == latestId
}

private fun List<VectorLayer>.cacheSignature(): String =
    joinToString(separator = "|") { layer -> layer.cacheKey().toString() }

private const val LABEL_TEXT_LAYOUT_CACHE_SIZE = 128

private fun Set<FeatureSelectionRef>.keysForLayer(layerId: String): Set<String> =
    asSequence()
        .filter { it.layerId == layerId }
        .map { it.featureKey }
        .toSet()

internal fun <T> Map<String, T>.validFor(
    currentKeys: Map<String, VectorLayerCacheKey>,
    previousKeys: Map<String, VectorLayerCacheKey>,
): Map<String, T> = filterKeys { layerId -> previousKeys[layerId] == currentKeys[layerId] }

internal fun Map<String, List<RenderCommand>>.validCommandsFor(
    currentKeys: Map<String, VectorLayerCacheKey>,
    previousKeys: Map<String, VectorLayerCacheKey>,
): Map<String, List<RenderCommand>> =
    filterKeys { layerId ->
        val currentKey = currentKeys[layerId] ?: return@filterKeys false
        previousKeys[layerId] == currentKey || currentKey.renderStrategy is VectorRenderStrategy.Immediate
    }

internal fun mergeRasterFrames(
    placeholderFrame: RasterFrame,
    fallbackFrame: RasterFrame,
    overviewFrame: RasterFrame,
    currentFrame: RasterFrame,
    currentSourceIdentities: Map<String, Any>? = null,
): RasterFrame {
    val compatiblePlaceholder = placeholderFrame.compatibleWith(currentSourceIdentities)
    val compatibleFallback = fallbackFrame.compatibleWith(currentSourceIdentities)
    val compatibleOverview = overviewFrame.compatibleWith(currentSourceIdentities)
    val compatibleCurrent = currentFrame.compatibleWith(currentSourceIdentities)
    val layerIds =
        compatiblePlaceholder.tilesByLayer.keys +
            compatibleOverview.tilesByLayer.keys +
            compatibleFallback.tilesByLayer.keys +
            compatibleCurrent.tilesByLayer.keys
    val tilesByLayer =
        buildMap {
            layerIds.forEach { layerId ->
                put(
                    layerId,
                    compatiblePlaceholder.tilesByLayer[layerId].orEmpty() +
                        compatibleOverview.tilesByLayer[layerId].orEmpty() +
                        compatibleFallback.tilesByLayer[layerId].orEmpty() +
                        compatibleCurrent.tilesByLayer[layerId].orEmpty(),
                )
            }
        }
    val decodedImagesByLayer =
        buildMap {
            layerIds.forEach { layerId ->
                put(
                    layerId,
                    compatiblePlaceholder.imagesForLayer(layerId) +
                        compatibleOverview.imagesForLayer(layerId) +
                        compatibleFallback.imagesForLayer(layerId) +
                        compatibleCurrent.imagesForLayer(layerId),
                )
            }
        }
    return RasterFrame(
        tilesByLayer = tilesByLayer,
        decodedImagesByLayer = decodedImagesByLayer,
        sourceIdentitiesByLayer =
            currentSourceIdentities
                ?: compatibleCurrent.sourceIdentitiesByLayer +
                compatibleFallback.sourceIdentitiesByLayer +
                compatibleOverview.sourceIdentitiesByLayer +
                compatiblePlaceholder.sourceIdentitiesByLayer,
    )
}

private fun RasterFrame.compatibleWith(currentSourceIdentities: Map<String, Any>?): RasterFrame {
    if (currentSourceIdentities == null) return this
    if (tilesByLayer.keys.all { layerId ->
            sourceIdentitiesByLayer[layerId] === currentSourceIdentities[layerId]
        }
    ) {
        return this
    }
    val compatibleLayerIds =
        tilesByLayer.keys.filterTo(mutableSetOf()) { layerId ->
            sourceIdentitiesByLayer[layerId] === currentSourceIdentities[layerId]
        }
    return RasterFrame(
        tilesByLayer = tilesByLayer.filterKeys(compatibleLayerIds::contains),
        decodedImagesByLayer = decodedImagesByLayer.filterKeys(compatibleLayerIds::contains),
        sourceIdentitiesByLayer = sourceIdentitiesByLayer.filterKeys(compatibleLayerIds::contains),
    )
}

private fun RasterFrame.imagesForLayer(layerId: String): List<ImageBitmap?> {
    val tiles = tilesByLayer[layerId].orEmpty()
    val images = decodedImagesByLayer[layerId].orEmpty()
    return tiles.indices.map { index -> images.getOrNull(index) }
}

internal fun RasterFrame.withRenderableTilesOnly(): RasterFrame {
    val keptIndicesByLayer =
        buildMap {
            this@withRenderableTilesOnly.tilesByLayer.forEach { (layerId, tiles) ->
                val decodedImages = decodedImagesByLayer[layerId].orEmpty()
                put(
                    layerId,
                    tiles.indices.filter { index ->
                        decodedImages.getOrNull(index) != null
                    },
                )
            }
        }
    val tilesByLayer =
        buildMap {
            this@withRenderableTilesOnly.tilesByLayer.forEach { (layerId, tiles) ->
                put(layerId, keptIndicesByLayer[layerId].orEmpty().map { index -> tiles[index] })
            }
        }
    val decodedImagesByLayer =
        buildMap {
            this@withRenderableTilesOnly.tilesByLayer.keys.forEach { layerId ->
                val decodedImages = this@withRenderableTilesOnly.decodedImagesByLayer[layerId].orEmpty()
                put(
                    layerId,
                    keptIndicesByLayer[layerId].orEmpty().map { index -> decodedImages.getOrNull(index) },
                )
            }
        }
    return RasterFrame(
        tilesByLayer = tilesByLayer,
        decodedImagesByLayer = decodedImagesByLayer,
        sourceIdentitiesByLayer = sourceIdentitiesByLayer.filterKeys(tilesByLayer::containsKey),
    )
}

private fun RasterFrame.hasTiles(): Boolean = tilesByLayer.values.any { tiles -> tiles.isNotEmpty() }

@Suppress("TooGenericExceptionCaught") // Render branches isolate any backend failure while preserving cancellation.
internal suspend fun runRenderBranch(
    onError: ((Throwable) -> Unit)? = null,
    block: suspend () -> Unit,
) {
    try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        onError?.invoke(error)
    }
}
