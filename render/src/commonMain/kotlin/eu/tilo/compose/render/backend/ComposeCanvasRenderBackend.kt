package eu.tilo.compose.render.backend

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import eu.tilo.compose.render.LabelBitmapCache
import eu.tilo.compose.render.drawFeatures
import eu.tilo.compose.render.drawTiles
import tilo.compose.core.map.Map
import kotlin.math.pow
import kotlin.math.roundToInt

object ComposeCanvasRenderBackend : RenderBackend {
    override val id: String = "compose-canvas"

    @Composable
    override fun Content(
        modifier: Modifier,
        scene: RenderScene,
        map: Map,
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
                offscreenLabelDrawScope = offscreenLabelDrawScope,
                textMeasurer = textMeasurer,
                labelBitmapCache = labelBitmapCache,
            )
        }
    }
}

internal fun DrawScope.drawRenderScene(
    scene: RenderScene,
    map: Map,
    tileDecoder: ((ByteArray) -> ImageBitmap?)?,
    offscreenLabelDrawScope: CanvasDrawScope,
    textMeasurer: TextMeasurer,
    labelBitmapCache: LabelBitmapCache,
) {
    scene.layers.forEach { layer ->
        when (layer) {
            is RasterRenderSceneLayer -> {
                if (tileDecoder != null) {
                    drawTiles(
                        tiles = layer.tiles,
                        tileDecoder = tileDecoder,
                        map = map,
                        decodedImages = layer.decodedImages
                    )
                }
            }

            is VectorRenderSceneLayer -> {
                drawFeatures(
                    commands = layer.commands,
                    map = map,
                    offscreenLabelDrawScope = offscreenLabelDrawScope,
                    textMeasurer = textMeasurer,
                    labelBitmapCache = labelBitmapCache,
                )
            }

            is VectorBitmapRenderSceneLayer -> {
                drawVectorBitmapLayer(layer = layer, map = map)
            }
        }
    }
}

private fun DrawScope.drawVectorBitmapLayer(
    layer: VectorBitmapRenderSceneLayer,
    map: Map,
) {
    val anchor = map.worldToScreen(layer.snapshot.center)
    val scale = 2.0.pow(map.zoom - layer.snapshot.zoom).toFloat()
    val width = (layer.snapshot.displayWidth * scale).roundToInt().coerceAtLeast(1)
    val height = (layer.snapshot.displayHeight * scale).roundToInt().coerceAtLeast(1)
    val topLeft = Offset(
        x = anchor.x.toFloat() - width / 2f,
        y = anchor.y.toFloat() - height / 2f,
    )
    drawImage(
        image = layer.bitmap,
        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        dstSize = IntSize(width, height),
    )
}
