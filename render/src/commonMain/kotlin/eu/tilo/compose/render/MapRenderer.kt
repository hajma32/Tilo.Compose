package eu.tilo.compose.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import eu.tilo.compose.render.backend.ComposeCanvasRenderBackend
import eu.tilo.compose.render.backend.RenderBackend
import eu.tilo.compose.render.backend.RenderScene
import eu.tilo.compose.render.backend.RenderSceneBuilder
import eu.tilo.compose.render.backend.VectorBitmapRenderSceneLayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import tilo.compose.core.layers.Layer
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.map.Viewport
import tilo.compose.core.tile.Tile
import tilo.compose.core.map.Map as MapState

/**
 * Compose-first map renderer that builds a backend-agnostic [RenderScene].
 */
@Composable
fun MapRenderer(
    map: MapState,
    layers: List<Layer>,
    tileDecoder: ((ByteArray) -> ImageBitmap?)? = null,
    modifier: Modifier = Modifier,
    backend: RenderBackend = ComposeCanvasRenderBackend
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val textMeasurer = rememberTextMeasurer()
    val offscreenLabelDrawScope = remember { CanvasDrawScope() }
    val labelBitmapCache = remember { LabelBitmapCache() }
    val rasterPipeline = remember { RasterRenderPipeline() }
    val vectorPipeline = remember { VectorRenderPipeline() }

    val renderRequests = remember(sortedLayersKey(layers)) {
        MutableSharedFlow<MapState>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }

    val sortedLayers = remember(layers) { layers.sortedWith(compareBy(Layer::zIndex)) }
    val tileLayers = sortedLayers.filterIsInstance<TileLayer>()
    val vectorLayers = sortedLayers.filterIsInstance<VectorLayer>()
    val vectorLayerCacheSignature = vectorLayers.cacheSignature()

    var redrawVersion by remember { mutableStateOf(0) }
    var scene by remember { mutableStateOf(RenderScene.Empty) }
    var lastRasterFrame by remember { mutableStateOf(RasterFrame.Empty) }
    var lastVectorCommandsByLayer by remember { mutableStateOf<Map<String, List<RenderCommand>>>(emptyMap()) }
    var lastVectorBitmapsByLayer by remember { mutableStateOf<Map<String, VectorBitmapRenderSceneLayer>>(emptyMap()) }
    var lastVectorCacheKeysByLayer by remember { mutableStateOf<Map<String, VectorLayerCacheKey>>(emptyMap()) }

    LaunchedEffect(
        sortedLayers,
        redrawVersion,
        map.center,
        map.zoom,
        map.viewport.width,
        map.viewport.height,
        map.viewport.pixelRatio,
        density,
        layoutDirection,
        vectorLayerCacheSignature,
    ) {
        if (map.viewport.width <= 0 || map.viewport.height <= 0) return@LaunchedEffect
        val renderMap = MapState(
            center = map.center,
            zoom = map.zoom,
            projection = map.projection,
            config = map.config,
            viewport = map.viewport
        )
        renderRequests.tryEmit(renderMap)
    }

    LaunchedEffect(sortedLayers) {
        renderRequests.collectLatest { renderMap ->
            supervisorScope {
                var tilesByLayer: Map<String, List<Tile>> = emptyMap()
                var decodedImagesByLayer: Map<String, List<ImageBitmap?>> = emptyMap()
                val currentVectorCacheKeys = vectorLayers.associate { layer -> layer.id to layer.cacheKey() }
                var commandsByLayer: Map<String, List<RenderCommand>> =
                    lastVectorCommandsByLayer.validFor(currentVectorCacheKeys, lastVectorCacheKeysByLayer)
                var vectorBitmapsByLayer: Map<String, VectorBitmapRenderSceneLayer> =
                    lastVectorBitmapsByLayer.validFor(currentVectorCacheKeys, lastVectorCacheKeysByLayer)
                val placeholderFrame = rasterPipeline.buildPlaceholderFrame(tileLayers, renderMap)

                fun publishScene() {
                    val displayRasterFrame = mergeRasterFrames(
                        placeholderFrame = placeholderFrame,
                        fallbackFrame = lastRasterFrame,
                        currentFrame = RasterFrame(
                            tilesByLayer = tilesByLayer,
                            decodedImagesByLayer = decodedImagesByLayer,
                        ),
                    )
                    val nextScene = RenderSceneBuilder.build(
                        layers = sortedLayers,
                        tilesByLayer = displayRasterFrame.tilesByLayer,
                        commandsByLayer = commandsByLayer,
                        vectorBitmapsByLayer = vectorBitmapsByLayer,
                        decodedImagesByLayer = displayRasterFrame.decodedImagesByLayer,
                    )
                    scene = nextScene
                    backend.onScene(nextScene)
                }

                publishScene()

                launch {
                    runRenderBranch {
                        val vectorFrame = vectorPipeline.buildFrame(
                            vectorLayers = vectorLayers,
                            map = renderMap,
                            density = density,
                            layoutDirection = layoutDirection,
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
                    runRenderBranch {
                        val rasterFrame = rasterPipeline.buildVisibleFrame(
                            tileLayers = tileLayers,
                            map = renderMap,
                            tileDecoder = tileDecoder,
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
                    runRenderBranch {
                        rasterPipeline.prefetch(tileLayers, renderMap)
                    }
                }
            }
        }
    }

    val interactionModifier = modifier
        .onSizeChanged { size ->
            map.viewport = Viewport(
                width = size.width,
                height = size.height,
                pixelRatio = density.density.toDouble()
            )
            redrawVersion++
        }
        .mapGestureInput(map) { redrawVersion++ }
        .drawWithContent {
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

private fun sortedLayersKey(layers: List<Layer>): String =
    layers.sortedWith(compareBy(Layer::zIndex)).joinToString(separator = "|") { layer -> "${layer.id}:${layer.zIndex}" }

private fun List<VectorLayer>.cacheSignature(): String =
    joinToString(separator = "|") { layer -> layer.cacheKey().toString() }

private fun <T> Map<String, T>.validFor(
    currentKeys: Map<String, VectorLayerCacheKey>,
    previousKeys: Map<String, VectorLayerCacheKey>,
): Map<String, T> =
    filterKeys { layerId -> previousKeys[layerId] == currentKeys[layerId] }

private fun mergeRasterFrames(
    placeholderFrame: RasterFrame,
    fallbackFrame: RasterFrame,
    currentFrame: RasterFrame,
): RasterFrame {
    val layerIds = placeholderFrame.tilesByLayer.keys + fallbackFrame.tilesByLayer.keys + currentFrame.tilesByLayer.keys
    val tilesByLayer = buildMap {
        layerIds.forEach { layerId ->
            put(
                layerId,
                placeholderFrame.tilesByLayer[layerId].orEmpty() +
                    fallbackFrame.tilesByLayer[layerId].orEmpty() +
                    currentFrame.tilesByLayer[layerId].orEmpty(),
            )
        }
    }
    val decodedImagesByLayer = buildMap {
        layerIds.forEach { layerId ->
            put(
                layerId,
                placeholderFrame.imagesForLayer(layerId) +
                    fallbackFrame.imagesForLayer(layerId) +
                    currentFrame.imagesForLayer(layerId),
            )
        }
    }
    return RasterFrame(
        tilesByLayer = tilesByLayer,
        decodedImagesByLayer = decodedImagesByLayer,
    )
}

private fun RasterFrame.imagesForLayer(layerId: String): List<ImageBitmap?> {
    val tiles = tilesByLayer[layerId].orEmpty()
    val images = decodedImagesByLayer[layerId].orEmpty()
    return tiles.indices.map { index -> images.getOrNull(index) }
}

private fun RasterFrame.withRenderableTilesOnly(): RasterFrame {
    val keptIndicesByLayer = buildMap {
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
    val tilesByLayer = buildMap {
        this@withRenderableTilesOnly.tilesByLayer.forEach { (layerId, tiles) ->
            put(layerId, keptIndicesByLayer[layerId].orEmpty().map { index -> tiles[index] })
        }
    }
    val decodedImagesByLayer = buildMap {
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
    )
}

private fun RasterFrame.hasTiles(): Boolean =
    tilesByLayer.values.any { tiles -> tiles.isNotEmpty() }

private suspend fun runRenderBranch(block: suspend () -> Unit) {
    try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        // Keep the render collector alive. A failed tile decode/fetch/prefetch should
        // affect only that render branch; the next pan/zoom must still recover.
    }
}
