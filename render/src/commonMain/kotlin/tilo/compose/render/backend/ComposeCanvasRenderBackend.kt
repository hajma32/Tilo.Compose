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
import tilo.compose.render.ExperimentalTiloRenderingApi
import tilo.compose.render.LabelBitmapCache
import tilo.compose.render.LabelLayoutEngine
import tilo.compose.render.LabelLayoutItem
import tilo.compose.render.PlacedLabel
import tilo.compose.render.RenderLabel
import tilo.compose.render.TilePlaceholderColors
import tilo.compose.render.drawFeatureGeometry
import tilo.compose.render.drawPlacedLabels
import tilo.compose.render.drawTiles
import kotlin.math.pow
import kotlin.math.roundToInt

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
) {
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
    val placedLabelsByLayer =
        labelLayoutEngine
            .layout(
                items = labels,
                map = map,
                drawScope = this,
                textMeasurer = textMeasurer,
                labelBitmapCache = labelBitmapCache,
            ).groupBy(PlacedLabel::layerOrder)

    scene.layers.forEachIndexed { layerOrder, layer ->
        when (layer) {
            is RasterRenderSceneLayer -> {
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

            is VectorRenderSceneLayer -> {
                withLayerOpacity(layer.opacity) {
                    drawFeatureGeometry(
                        commands = layer.commands,
                        map = map,
                        pointIconPainters = layer.pointIconPainters,
                    )
                }
            }

            is VectorBitmapRenderSceneLayer -> {
                withLayerOpacity(layer.opacity) {
                    drawVectorBitmapLayer(layer = layer, map = map)
                }
            }
        }
        placedLabelsByLayer[layerOrder]?.let { placedLabels ->
            drawPlacedLabels(
                labels = placedLabels,
                offscreenDrawScope = offscreenLabelDrawScope,
                textMeasurer = textMeasurer,
                labelBitmapCache = labelBitmapCache,
            )
        }
    }
    labelBitmapCache.publishDiagnostics(
        candidates = labels.size,
        placed = placedLabelsByLayer.values.sumOf { placedLabels -> placedLabels.size },
    )
}

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
