@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render.backend

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import tilo.compose.core.map.MapState
import tilo.compose.render.CanvasFramePerformanceEvent
import tilo.compose.render.ExperimentalTiloRenderingApi
import tilo.compose.render.LabelBitmapCache
import tilo.compose.render.LabelLayoutEngine
import tilo.compose.render.LabelLayoutItem
import tilo.compose.render.PlacedLabel
import tilo.compose.render.RenderLabel
import tilo.compose.render.RenderPerformanceLogger
import tilo.compose.render.TilePlaceholderColors
import tilo.compose.render.drawFeatureGeometry
import tilo.compose.render.drawCachedGeometry
import tilo.compose.render.drawPlacedLabels
import tilo.compose.render.drawTiles
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.TimeSource

@ExperimentalTiloRenderingApi
object ComposeCanvasRenderBackend : RenderBackend {
    override val id: String = "compose-canvas"
    private val labelLayoutEngine = LabelLayoutEngine()

    @Composable
    override fun Content(
        modifier: Modifier,
        scene: RenderScene,
        map: MapState,
        tileDecoder: ((ByteArray) -> ImageBitmap?)?,
        offscreenLabelDrawScope: CanvasDrawScope,
        textMeasurer: TextMeasurer,
        labelBitmapCache: LabelBitmapCache,
        performanceLogger: RenderPerformanceLogger?,
    ) {
        val placeholderColors = tilePlaceholderColorsFor(MaterialTheme.colorScheme.surface)
        Canvas(modifier = modifier) {
            drawRenderScene(
                scene = scene,
                map = map,
                tileDecoder = tileDecoder,
                labelLayoutEngine = labelLayoutEngine,
                offscreenLabelDrawScope = offscreenLabelDrawScope,
                textMeasurer = textMeasurer,
                labelBitmapCache = labelBitmapCache,
                placeholderColors = placeholderColors,
                performanceLogger = performanceLogger,
            )
        }
    }
}

internal fun DrawScope.drawRenderScene(
    scene: RenderScene,
    map: MapState,
    tileDecoder: ((ByteArray) -> ImageBitmap?)?,
    labelLayoutEngine: LabelLayoutEngine = LabelLayoutEngine(),
    offscreenLabelDrawScope: CanvasDrawScope,
    textMeasurer: TextMeasurer,
    labelBitmapCache: LabelBitmapCache,
    placeholderColors: TilePlaceholderColors = TilePlaceholderColors.Light,
    performanceLogger: RenderPerformanceLogger? = null,
) {
    val profilingEnabled = performanceLogger != null
    val frameStart = performanceLogger?.let { TimeSource.Monotonic.markNow() }
    val labelCollectionStart = performanceLogger?.let { TimeSource.Monotonic.markNow() }
    val labels =
        scene.layers.flatMapIndexed { layerOrder, layer ->
            if (layer is VectorRenderSceneLayer) {
                layer.commands.filterIsInstance<RenderLabel>().map { label ->
                    LabelLayoutItem(
                        command = label,
                        layerOrder = layerOrder,
                        opacity = layer.opacity,
                    )
                }
            } else {
                emptyList()
            }
        }
    val labelCollectionMillis = labelCollectionStart?.elapsedMillis() ?: 0.0
    val labelLayoutStart = performanceLogger?.let { TimeSource.Monotonic.markNow() }
    val placedLabelsByLayer =
        labelLayoutEngine
            .layout(
                items = labels,
                map = map,
                drawScope = this,
                textMeasurer = textMeasurer,
                labelBitmapCache = labelBitmapCache,
            ).groupBy(PlacedLabel::layerOrder)
    val labelLayoutMillis = labelLayoutStart?.elapsedMillis() ?: 0.0
    var rasterDrawMillis = 0.0
    var vectorDrawMillis = 0.0
    var labelDrawMillis = 0.0
    var vectorCommandCount = 0
    var vectorStyleBatchCount = 0
    var vectorRenderBatchCount = 0

    scene.layers.forEachIndexed { layerOrder, layer ->
        when (layer) {
            is RasterRenderSceneLayer -> {
                rasterDrawMillis +=
                    measureMillis(profilingEnabled) {
                        if (tileDecoder != null) {
                            withLayerOpacity(layer.opacity) {
                                if (layer.placeholderTiles.isNotEmpty()) {
                                    drawTiles(
                                        tiles = layer.placeholderTiles,
                                        tileDecoder = tileDecoder,
                                        map = map,
                                        decodedImages = List(layer.placeholderTiles.size) { null },
                                        placeholderColors = placeholderColors,
                                    )
                                }
                                drawTiles(
                                    tiles = layer.tiles,
                                    tileDecoder = tileDecoder,
                                    map = map,
                                    decodedImages = layer.decodedImages,
                                    placeholderColors = placeholderColors,
                                )
                            }
                        }
                    }
            }

            is VectorRenderSceneLayer -> {
                vectorCommandCount += layer.cachedGeometry?.commandCount ?: layer.commands.size
                vectorDrawMillis +=
                    measureMillis(profilingEnabled) {
                        withLayerOpacity(layer.opacity) {
                            val stats =
                                layer.cachedGeometry?.let { cached -> drawCachedGeometry(cached, map) }
                                    ?: drawFeatureGeometry(
                                        commands = layer.commands,
                                        map = map,
                                        pointIconPainters = layer.pointIconPainters,
                                    )
                            vectorStyleBatchCount += stats.styleBatchCount
                            vectorRenderBatchCount += stats.renderBatchCount
                        }
                    }
            }

            is VectorBitmapRenderSceneLayer -> {
                vectorDrawMillis +=
                    measureMillis(profilingEnabled) {
                        withLayerOpacity(layer.opacity) {
                            drawVectorBitmapLayer(layer = layer, map = map)
                        }
                    }
            }
        }
        placedLabelsByLayer[layerOrder]?.let { placedLabels ->
            labelDrawMillis +=
                measureMillis(profilingEnabled) {
                    drawPlacedLabels(
                        labels = placedLabels,
                        offscreenDrawScope = offscreenLabelDrawScope,
                        textMeasurer = textMeasurer,
                        labelBitmapCache = labelBitmapCache,
                    )
                }
        }
    }
    val totalMillis = frameStart?.elapsedMillis() ?: 0.0
    performanceLogger?.log(
        CanvasFramePerformanceEvent(
            labelCount = labels.size,
            placedLabelCount = placedLabelsByLayer.values.sumOf { placedLabels -> placedLabels.size },
            vectorCommandCount = vectorCommandCount,
            vectorStyleBatchCount = vectorStyleBatchCount,
            vectorRenderBatchCount = vectorRenderBatchCount,
            labelCollectionMillis = labelCollectionMillis,
            labelLayoutMillis = labelLayoutMillis,
            rasterDrawMillis = rasterDrawMillis,
            vectorDrawMillis = vectorDrawMillis,
            labelDrawMillis = labelDrawMillis,
            totalMillis = totalMillis,
        ),
    )
}

private inline fun measureMillis(
    enabled: Boolean,
    block: () -> Unit,
): Double {
    if (!enabled) {
        block()
        return 0.0
    }
    val start = TimeSource.Monotonic.markNow()
    block()
    return start.elapsedMillis()
}

private fun kotlin.time.TimeMark.elapsedMillis(): Double =
    elapsedNow().inWholeNanoseconds / NANOS_PER_MILLISECOND

private const val NANOS_PER_MILLISECOND = 1_000_000.0

internal fun tilePlaceholderColorsFor(surfaceColor: Color): TilePlaceholderColors =
    if (surfaceColor.luminance() < 0.5f) TilePlaceholderColors.Dark else TilePlaceholderColors.Light

private inline fun DrawScope.withLayerOpacity(
    opacity: Double,
    draw: DrawScope.() -> Unit,
) {
    if (opacity <= 0.0) return
    if (opacity >= 1.0) {
        draw()
        return
    }
    val paint = Paint().apply { alpha = opacity.toFloat() }
    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), paint)
    try {
        draw()
    } finally {
        drawContext.canvas.restore()
    }
}

internal fun DrawScope.drawVectorBitmapLayer(
    layer: VectorBitmapRenderSceneLayer,
    map: MapState,
) {
    val anchor = map.worldToScreen(layer.snapshot.center)
    val scale = 2.0.pow(map.zoom - layer.snapshot.zoom).toFloat()
    val width = (layer.snapshot.displayWidth * scale).roundToInt().coerceAtLeast(1)
    val height = (layer.snapshot.displayHeight * scale).roundToInt().coerceAtLeast(1)
    val topLeft =
        Offset(
            x = anchor.x.toFloat() - width / 2f,
            y = anchor.y.toFloat() - height / 2f,
        )
    withTransform({
        rotate(
            degrees = (layer.snapshot.bearing - map.bearing).toFloat(),
            pivot = Offset(anchor.x.toFloat(), anchor.y.toFloat()),
        )
    }) {
        drawImage(
            image = layer.bitmap,
            dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
            dstSize = IntSize(width, height),
        )
    }
}
