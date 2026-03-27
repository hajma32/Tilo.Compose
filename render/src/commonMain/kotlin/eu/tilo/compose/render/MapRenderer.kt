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
import androidx.compose.ui.text.rememberTextMeasurer
import eu.tilo.compose.render.backend.ComposeCanvasRenderBackend
import eu.tilo.compose.render.backend.RenderBackend
import eu.tilo.compose.render.backend.RenderScene
import eu.tilo.compose.render.backend.RenderSceneBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import tilo.compose.core.layers.Layer
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.map.Map
import tilo.compose.core.map.Viewport

/**
 * Compose-first map renderer that builds a backend-agnostic [RenderScene].
 */
@Composable
fun MapRenderer(
    map: Map,
    layers: List<Layer>,
    tileDecoder: ((ByteArray) -> ImageBitmap?)? = null,
    modifier: Modifier = Modifier,
    backend: RenderBackend = ComposeCanvasRenderBackend
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val offscreenLabelDrawScope = remember { CanvasDrawScope() }
    val vectorMeshCache = remember { VectorTileMeshCache(maxTiles = 256) }
    val renderRequests = remember(sortedLayersKey(layers)) {
        MutableSharedFlow<Map>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }

    val sortedLayers = remember(layers) { layers.sortedWith(compareBy(Layer::zIndex)) }
    val tileLayers = sortedLayers.filterIsInstance<TileLayer>()
    val vectorLayers = sortedLayers.filterIsInstance<VectorLayer>()

    var redrawVersion by remember { mutableStateOf(0) }
    var scene by remember { mutableStateOf(RenderScene.Empty) }

    LaunchedEffect(
        sortedLayers,
        redrawVersion,
        map.center,
        map.zoom,
        map.viewport.width,
        map.viewport.height,
        map.viewport.pixelRatio
    ) {
        if (map.viewport.width <= 0 || map.viewport.height <= 0) return@LaunchedEffect
        val renderMap = Map(
            center = map.center,
            zoom = map.zoom,
            projection = map.projection,
            config = map.config,
            viewport = map.viewport
        )
        delay(60)
        renderRequests.tryEmit(renderMap)
    }

    LaunchedEffect(sortedLayers) {
        renderRequests.collectLatest { renderMap ->
            val nextScene = withContext(Dispatchers.IO) {
                val tilesByLayer = buildMap {
                    tileLayers.forEach { layer ->
                        put(layer.id, layer.loadTiles(renderMap))
                    }
                }
                val commandsByLayer = buildMap {
                    vectorLayers.forEach { layer ->
                        val features = layer.source.getFeatures(renderMap)
                        val projected = transformFeaturesToMapProjection(features, layer.projection, renderMap)
                        put(layer.id, CommandBuilder.build(renderMap, projected))
                    }
                }
                val preparedFramesByLayer = buildMap {
                    commandsByLayer.forEach { (layerId, commands) ->
                        put(layerId, vectorMeshCache.prepare(commands, renderMap))
                    }
                }
                RenderSceneBuilder.build(
                    layers = sortedLayers,
                    tilesByLayer = tilesByLayer,
                    commandsByLayer = commandsByLayer,
                    preparedFramesByLayer = preparedFramesByLayer
                )
            }

            scene = nextScene
            backend.onScene(nextScene)
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
        textMeasurer = textMeasurer
    )
}

private fun sortedLayersKey(layers: List<Layer>): String =
    layers.sortedWith(compareBy(Layer::zIndex)).joinToString(separator = "|") { layer -> "${layer.id}:${layer.zIndex}" }
