@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render.backend

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import tilo.compose.core.map.MapState
import tilo.compose.render.ExperimentalTiloRenderingApi
import tilo.compose.render.LabelBitmapCache
import tilo.compose.render.LabelLayoutEngine
import tilo.compose.render.RenderLabel
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
        Canvas(modifier = modifier) {
            drawRenderScene(
                scene = scene,
                map = map,
                tileDecoder = tileDecoder,
                labelLayoutEngine = labelLayoutEngine,
                offscreenLabelDrawScope = offscreenLabelDrawScope,
                textMeasurer = textMeasurer,
                labelBitmapCache = labelBitmapCache,
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
) {
    val labels = mutableListOf<RenderLabel>()
    scene.layers.forEach { layer ->
        when (layer) {
            is RasterRenderSceneLayer -> {
                if (tileDecoder != null) {
                    drawTiles(
                        tiles = layer.tiles,
                        tileDecoder = tileDecoder,
                        map = map,
                        decodedImages = layer.decodedImages,
                    )
                }
            }

            is VectorRenderSceneLayer -> {
                drawFeatureGeometry(
                    commands = layer.commands,
                    map = map,
                    pointIconPainters = layer.pointIconPainters,
                )
                layer.commands.forEach { command ->
                    if (command is RenderLabel) {
                        labels += command
                    }
                }
            }

            is VectorBitmapRenderSceneLayer -> {
                drawVectorBitmapLayer(layer = layer, map = map)
            }
        }
    }

    drawPlacedLabels(
        labels =
            labelLayoutEngine.layout(
                labels = labels,
                map = map,
                drawScope = this,
                textMeasurer = textMeasurer,
                labelBitmapCache = labelBitmapCache,
            ),
        offscreenDrawScope = offscreenLabelDrawScope,
        textMeasurer = textMeasurer,
        labelBitmapCache = labelBitmapCache,
    )
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
    drawImage(
        image = layer.bitmap,
        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        dstSize = IntSize(width, height),
    )
}
