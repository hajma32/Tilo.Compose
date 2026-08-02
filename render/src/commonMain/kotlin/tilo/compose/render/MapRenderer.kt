@file:OptIn(ExperimentalTiloRenderingApi::class, tilo.compose.dsl.ExperimentalTiloApi::class)

package tilo.compose.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import tilo.compose.core.layers.raster.RasterTileFailure
import tilo.compose.core.layers.raster.RasterTileFailureKind
import tilo.compose.core.layers.raster.TileFetchMetrics
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.layers.vector.VectorRenderStrategy
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import tilo.compose.core.selection.FeatureSelection
import tilo.compose.core.selection.FeatureSelectionRef
import tilo.compose.core.tile.Tile
import tilo.compose.dsl.MapDiagnosticsState
import tilo.compose.dsl.MapFeatureMetrics
import tilo.compose.dsl.MapGestureConfig
import tilo.compose.dsl.MapRasterLayerMetrics
import tilo.compose.dsl.MapTileCacheMetrics
import tilo.compose.dsl.MapTileMetrics
import tilo.compose.dsl.PresentedTileLayer
import tilo.compose.dsl.RasterLayerDiagnostics
import tilo.compose.dsl.tileFetchMetricsOrNull
import tilo.compose.render.backend.ComposeCanvasRenderBackend
import tilo.compose.render.backend.RasterRenderSceneLayer
import tilo.compose.render.backend.RenderBackend
import tilo.compose.render.backend.RenderScene
import tilo.compose.render.backend.RenderSceneBuilder
import tilo.compose.render.backend.VectorBitmapRenderSceneLayer

/**
 * Compose-first map renderer that builds a backend-agnostic [RenderScene].
 *
 * Expected missing or undecodable tiles remain isolated inside the raster
 * pipeline. [onRenderError] receives unexpected branch failures; coroutine
 * cancellation is always propagated. [onCameraInteractionStarted] runs on pointer down before a
 * gesture mutates the camera, so callers can cancel motion they own.
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
    onCameraInteractionStarted: (() -> Unit)? = null,
    gestureConfig: MapGestureConfig = MapGestureConfig.Default,
) = MapRendererImpl(
    map = map,
    layers = layers,
    gestureConfig = gestureConfig,
    tileDecoder = tileDecoder,
    modifier = modifier,
    backend = backend,
    onTapWorld = onTapWorld,
    onFeatureSelect = onFeatureSelect,
    onRenderError = onRenderError,
    selectedFeatures = selectedFeatures,
    invalidationKey = invalidationKey,
    onMapChanged = onMapChanged,
    onCameraInteractionStarted = onCameraInteractionStarted,
    diagnosticsState = null,
)

/** Opt-in diagnostics overload of [MapRenderer]. */
@Composable
@ExperimentalTiloRenderingApi
fun MapRenderer(
    map: MapState,
    layers: List<Layer>,
    diagnosticsState: MapDiagnosticsState,
    tileDecoder: (ByteArray) -> ImageBitmap? = ::decodeTileImageBitmap,
    modifier: Modifier = Modifier,
    backend: RenderBackend = ComposeCanvasRenderBackend,
    onTapWorld: ((Point) -> Unit)? = null,
    onFeatureSelect: ((List<FeatureSelection>) -> Unit)? = null,
    onRenderError: ((Throwable) -> Unit)? = null,
    selectedFeatures: Set<FeatureSelectionRef> = emptySet(),
    invalidationKey: Any? = null,
    onMapChanged: (() -> Unit)? = null,
    onCameraInteractionStarted: (() -> Unit)? = null,
    gestureConfig: MapGestureConfig = MapGestureConfig.Default,
) = MapRendererImpl(
    map = map,
    layers = layers,
    gestureConfig = gestureConfig,
    tileDecoder = tileDecoder,
    modifier = modifier,
    backend = backend,
    onTapWorld = onTapWorld,
    onFeatureSelect = onFeatureSelect,
    onRenderError = onRenderError,
    selectedFeatures = selectedFeatures,
    invalidationKey = invalidationKey,
    onMapChanged = onMapChanged,
    onCameraInteractionStarted = onCameraInteractionStarted,
    diagnosticsState = diagnosticsState,
)

@Composable
@Suppress("LongMethod")
private fun MapRendererImpl(
    map: MapState,
    layers: List<Layer>,
    gestureConfig: MapGestureConfig,
    tileDecoder: (ByteArray) -> ImageBitmap?,
    modifier: Modifier,
    backend: RenderBackend,
    onTapWorld: ((Point) -> Unit)?,
    onFeatureSelect: ((List<FeatureSelection>) -> Unit)?,
    onRenderError: ((Throwable) -> Unit)?,
    selectedFeatures: Set<FeatureSelectionRef>,
    invalidationKey: Any?,
    onMapChanged: (() -> Unit)?,
    onCameraInteractionStarted: (() -> Unit)?,
    diagnosticsState: MapDiagnosticsState?,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val textMeasurer = rememberTextMeasurer(cacheSize = LABEL_TEXT_LAYOUT_CACHE_SIZE)
    val offscreenLabelDrawScope = remember { CanvasDrawScope() }
    val labelBitmapCache = remember { LabelBitmapCache() }
    SideEffect {
        labelBitmapCache.diagnosticsState = diagnosticsState
    }
    val rasterPipeline = remember { RasterRenderPipeline() }
    val vectorPipeline = remember { VectorRenderPipeline() }
    val featureHitTester = remember { FeatureHitTester() }
    val gestureCoordinator = rememberMapGestureCoordinator(map)
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

    val activeLayerSet = remember(layerTree, map.zoom) { layerTree.activeLayersAt(map.zoom) }
    val activeLayers = activeLayerSet.layers
    val effectiveOpacitiesByLayerId = activeLayerSet.effectiveOpacitiesByLayerId
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
            activeLayers = activeLayers,
            effectiveOpacitiesByLayerId = effectiveOpacitiesByLayerId,
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
    var rasterFallbackHistory by remember { mutableStateOf<List<RasterFrame>>(emptyList()) }
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
                overviewFrame = combineRasterFrames(rasterFallbackHistory + lastOverviewFrame),
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
                effectiveOpacitiesByLayerId = effectiveOpacitiesByLayerId,
            )
        scene = nextScene
        backend.onScene(nextScene)
    }

    LaunchedEffect(
        activeLayers,
        redrawVersion,
        map.center,
        map.zoom,
        map.bearing,
        map.viewport.width,
        map.viewport.height,
        map.viewport.pixelRatio,
        density,
        layoutDirection,
        vectorLayerCacheSignature,
        selectedFeatures,
        invalidationKey,
        diagnosticsState,
    ) {
        if (map.viewport.width <= 0 || map.viewport.height <= 0) return@LaunchedEffect
        val renderMap =
            MapState(
                center = map.center,
                zoom = map.zoom,
                bearing = map.bearing,
                projection = map.projection,
                config = map.config,
                viewport = map.viewport,
            )
        renderRequests.emit(renderMap)
        overviewRequests.emit(overviewRequestTracker.next(renderMap))
    }

    LaunchedEffect(activeLayers, diagnosticsState) {
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
                            overviewFrame = combineRasterFrames(rasterFallbackHistory + lastOverviewFrame),
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
                            layers = input.activeLayers,
                            tilesByLayer = displayRasterFrame.tilesByLayer,
                            commandsByLayer = commandsByLayer,
                            vectorBitmapsByLayer = vectorBitmapsByLayer,
                            decodedImagesByLayer = displayRasterFrame.decodedImagesByLayer,
                            effectiveOpacitiesByLayerId = input.effectiveOpacitiesByLayerId,
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
                        diagnosticsState?.publishFeatures(
                            MapFeatureMetrics(
                                returned = vectorFrame.metrics.returnedFeatures,
                                visible = vectorFrame.metrics.visibleFeatures,
                                geometryCommands = vectorFrame.metrics.geometryCommands,
                                bitmapLayersReused = vectorFrame.metrics.bitmapLayersReused,
                                bitmapLayersRebuilt = vectorFrame.metrics.bitmapLayersRebuilt,
                            ),
                        )
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
                                onDecodeFailure = { layerId, coordinate, error ->
                                    input.tileLayers.reportDecodeFailure(
                                        layerId = layerId,
                                        coordinate = coordinate,
                                        error = error,
                                        affectsAvailability = true,
                                    )
                                },
                            )
                        tilesByLayer = rasterFrame.tilesByLayer
                        decodedImagesByLayer = rasterFrame.decodedImagesByLayer
                        val renderableRasterFrame = rasterFrame.withRenderableTilesOnly()
                        if (renderableRasterFrame.hasTiles()) {
                            if (lastRasterFrame.hasTiles() && lastRasterFrame != renderableRasterFrame) {
                                rasterFallbackHistory =
                                    (rasterFallbackHistory + lastRasterFrame)
                                        .takeLast(MAX_RASTER_FALLBACK_FRAMES)
                            }
                            lastRasterFrame = renderableRasterFrame
                        }
                        diagnosticsState?.let { diagnostics ->
                            val metricSnapshots = input.tileLayers.rasterMetricSnapshots()
                            diagnostics.publishTiles(
                                MapTileMetrics(
                                    planned = placeholderFrame.tilesByLayer.totalItemCount(),
                                    loaded = rasterFrame.tilesByLayer.countItems { tile -> tile.bytes != null },
                                    missing = rasterFrame.tilesByLayer.countItems { tile -> tile.bytes == null },
                                    decoded = rasterFrame.decodedImagesByLayer.countItems { image -> image != null },
                                    displayed = diagnostics.metrics.tiles.displayed,
                                    cache = metricSnapshots.aggregateTileCacheMetrics(),
                                    layers = metricSnapshots.perLayerMetrics(),
                                ),
                            )
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
                            onDecodeFailure = { layerId, coordinate, error ->
                                input.tileLayers.reportDecodeFailure(
                                    layerId = layerId,
                                    coordinate = coordinate,
                                    error = error,
                                    affectsAvailability = false,
                                )
                            },
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
                        overviewFrame = combineRasterFrames(rasterFallbackHistory + overviewFrame),
                        currentFrame = RasterFrame.Empty,
                        currentSourceIdentities = input.currentRasterSourceIdentities,
                    )
                val nextScene =
                    RenderSceneBuilder.build(
                        layers = input.activeLayers,
                        tilesByLayer = displayRasterFrame.tilesByLayer,
                        commandsByLayer = validVectorCommands,
                        vectorBitmapsByLayer = validVectorBitmaps,
                        decodedImagesByLayer = displayRasterFrame.decodedImagesByLayer,
                        effectiveOpacitiesByLayerId = input.effectiveOpacitiesByLayerId,
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
            }.mapGestureInput(
                map = map,
                gestureConfig = gestureConfig,
                onInteractionStarted = onCameraInteractionStarted,
                onChanged = {
                    redrawVersion++
                    onMapChanged?.invoke()
                },
                coordinator = gestureCoordinator,
            ).mapTapInput(
                map = map,
                onInteractionStarted = onCameraInteractionStarted,
                onTap =
                    if (onTapWorld == null && onFeatureSelect == null) {
                        null
                    } else {
                        { screenPoint, worldPoint ->
                            onFeatureSelect?.invoke(featureHitTester.hitTest(map, vectorLayers, screenPoint))
                            onTapWorld?.invoke(worldPoint)
                        }
                    },
                onChanged = {
                    redrawVersion++
                    onMapChanged?.invoke()
                },
                coordinator = gestureCoordinator,
            ).drawWithContent {
                redrawVersion
                drawContent()
            }

    val liveRenderMap =
        MapState(
            center = map.center,
            zoom = map.zoom,
            bearing = map.bearing,
            projection = map.projection,
            config = map.config,
            viewport = map.viewport,
        )
    val livePlaceholderFrame =
        if (liveRenderMap.viewport.width > 0 && liveRenderMap.viewport.height > 0) {
            rasterPipeline.buildPlaceholderFrame(tileLayers, liveRenderMap)
        } else {
            RasterFrame.Empty
        }
    val displayScene =
        scene.withLiveRasterPlaceholders(
            activeLayers = activeLayers,
            placeholderFrame = livePlaceholderFrame,
            effectiveOpacitiesByLayerId = effectiveOpacitiesByLayerId,
        )
    SideEffect {
        diagnosticsState?.publishDisplayedTiles(displayScene.displayedTileCount())
    }

    backend.Content(
        modifier = interactionModifier,
        scene = displayScene,
        map = map,
        tileDecoder = tileDecoder,
        offscreenLabelDrawScope = offscreenLabelDrawScope,
        textMeasurer = textMeasurer,
        labelBitmapCache = labelBitmapCache,
    )
}

/**
 * Adds placeholder coverage planned from the camera used by the current draw frame.
 * Tile loading remains asynchronous, but placeholder geometry must not lag behind
 * camera animation or it exposes the Canvas background around stale fallback tiles.
 */
internal fun RenderScene.withLiveRasterPlaceholders(
    activeLayers: List<Layer>,
    placeholderFrame: RasterFrame,
    effectiveOpacitiesByLayerId: Map<String, Double>,
): RenderScene {
    if (activeLayers.none { it is TileLayer }) return this

    val sceneLayersById = layers.groupBy { it.id }
    val mergedLayers =
        buildList {
            activeLayers.forEach { layer ->
                val existingLayers = sceneLayersById[layer.id].orEmpty()
                if (layer is TileLayer) {
                    val existingRaster = existingLayers.filterIsInstance<RasterRenderSceneLayer>().singleOrNull()
                    val placeholders = placeholderFrame.tilesByLayer[layer.id].orEmpty()
                    when {
                        existingRaster != null -> add(existingRaster.copy(placeholderTiles = placeholders))
                        placeholders.isNotEmpty() -> {
                            add(
                                RasterRenderSceneLayer(
                                    id = layer.id,
                                    zIndex = layer.zIndex,
                                    tiles = emptyList(),
                                    decodedImages = emptyList(),
                                    opacity = effectiveOpacitiesByLayerId[layer.id] ?: layer.opacity,
                                    placeholderTiles = placeholders,
                                ),
                            )
                        }
                    }
                } else {
                    addAll(existingLayers)
                }
            }
        }
    return RenderScene(mergedLayers)
}

internal suspend fun <T> Flow<T>.collectLatestRenderRequest(block: suspend (T) -> Unit) {
    collectLatest(block)
}

private data class RenderLoopInput(
    val activeLayers: List<Layer>,
    val effectiveOpacitiesByLayerId: Map<String, Double>,
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
private const val MAX_RASTER_FALLBACK_FRAMES = 4

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

/**
 * Combines a small chronological cache of completed frames for navigation fallback.
 * Coarser zooms are drawn first and newer same-zoom frames later. Frames belonging
 * to a replaced raster source are discarded before their tiles can reappear.
 */
internal fun combineRasterFrames(frames: List<RasterFrame>): RasterFrame {
    val populatedFrames = frames.filter(RasterFrame::hasTiles)
    if (populatedFrames.isEmpty()) return RasterFrame.Empty

    val sourceIdentitiesByLayer =
        buildMap {
            populatedFrames.forEach { frame ->
                frame.sourceIdentitiesByLayer.forEach { (layerId, sourceIdentity) ->
                    put(layerId, sourceIdentity)
                }
            }
        }
    val orderedFrames = populatedFrames.sortedBy(RasterFrame::minimumTileZoom)
    val layerIds = orderedFrames.flatMap { it.tilesByLayer.keys }.distinct()
    val tilesByLayer =
        buildMap {
            layerIds.forEach { layerId ->
                val expectedSource = sourceIdentitiesByLayer[layerId]
                put(
                    layerId,
                    orderedFrames.flatMap { frame ->
                        if (frame.matchesSource(layerId, expectedSource)) {
                            frame.tilesByLayer[layerId].orEmpty()
                        } else {
                            emptyList()
                        }
                    },
                )
            }
        }
    val decodedImagesByLayer =
        buildMap {
            layerIds.forEach { layerId ->
                val expectedSource = sourceIdentitiesByLayer[layerId]
                put(
                    layerId,
                    orderedFrames.flatMap { frame ->
                        if (frame.matchesSource(layerId, expectedSource)) {
                            frame.imagesForLayer(layerId)
                        } else {
                            emptyList()
                        }
                    },
                )
            }
        }
    return RasterFrame(
        tilesByLayer = tilesByLayer,
        decodedImagesByLayer = decodedImagesByLayer,
        sourceIdentitiesByLayer = sourceIdentitiesByLayer,
    )
}

private fun RasterFrame.minimumTileZoom(): Int =
    tilesByLayer.values.flatten().minOfOrNull { tile -> tile.coordinate.z } ?: Int.MAX_VALUE

private fun RasterFrame.matchesSource(
    layerId: String,
    expectedSource: Any?,
): Boolean {
    val frameSource = sourceIdentitiesByLayer[layerId]
    return expectedSource == null || frameSource == null || frameSource === expectedSource
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

private fun RenderScene.displayedTileCount(): Int =
    layers.filterIsInstance<RasterRenderSceneLayer>().sumOf { layer -> layer.tiles.size }

private fun <T> Map<String, List<T>>.totalItemCount(): Int = values.sumOf(List<T>::size)

private fun <T> Map<String, List<T>>.countItems(predicate: (T) -> Boolean): Int =
    values.sumOf { items -> items.count(predicate) }

private data class RasterMetricSnapshot(
    val layerId: String,
    val fetch: TileFetchMetrics,
    val diagnostics: RasterLayerDiagnostics?,
)

private suspend fun List<TileLayer>.rasterMetricSnapshots(): List<RasterMetricSnapshot> =
    mapNotNull { layer ->
        val fetch = layer.tileFetchMetricsOrNull() ?: return@mapNotNull null
        RasterMetricSnapshot(
            layerId = layer.id,
            fetch = fetch,
            diagnostics = (layer as? PresentedTileLayer)?.rasterDiagnostics(),
        )
    }

private fun List<RasterMetricSnapshot>.aggregateTileCacheMetrics(): MapTileCacheMetrics =
    MapTileCacheMetrics(
        entries = sumOf { it.fetch.cacheEntries },
        maxEntries = sumOf { it.fetch.maxCacheEntries },
        hits = sumOf { it.fetch.cacheHits },
        misses = sumOf { it.fetch.cacheMisses },
        evictions = sumOf { it.fetch.cacheEvictions },
        sourceFetches = sumOf { it.fetch.sourceFetches },
        coalescedRequests = sumOf { it.fetch.coalescedRequests },
        inFlightRequests = sumOf { it.fetch.inFlightRequests },
    )

private fun List<RasterMetricSnapshot>.perLayerMetrics(): Map<String, MapRasterLayerMetrics> =
    associate { snapshot ->
        snapshot.layerId to
            MapRasterLayerMetrics(
                succeeded = snapshot.fetch.succeeded,
                missing = snapshot.fetch.missing,
                failed = snapshot.fetch.failed,
                suppressedByBackoff = snapshot.fetch.suppressedByBackoff,
                failureBackoffEntries = snapshot.fetch.failureBackoffEntries,
                decodeFailures = snapshot.diagnostics?.decodeFailures ?: 0,
                lastFailure = snapshot.diagnostics?.lastFailure,
            )
    }

private suspend fun List<TileLayer>.reportDecodeFailure(
    layerId: String,
    coordinate: tilo.compose.core.tile.TileCoordinate,
    error: Throwable,
    affectsAvailability: Boolean,
) {
    val failure =
        RasterTileFailure(
            kind = RasterTileFailureKind.Decode,
            coordinate = coordinate,
            message = error.message ?: "Tile image decoding failed",
            cause = error,
        )
    (firstOrNull { it.id == layerId } as? PresentedTileLayer)?.reportDecodeFailure(
        failure,
        affectsAvailability,
    )
}

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
