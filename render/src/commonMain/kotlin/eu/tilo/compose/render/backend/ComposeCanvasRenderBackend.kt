package eu.tilo.compose.render.backend

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import eu.tilo.compose.render.LabelBitmapCache
import eu.tilo.compose.render.drawFeatures
import eu.tilo.compose.render.drawTiles
import tilo.compose.core.map.Map

object ComposeCanvasRenderBackend : RenderBackend {
    override val id: String = "compose-canvas"

    @Composable
    override fun Content(
        modifier: Modifier,
        scene: RenderScene,
        map: Map,
        tileDecoder: ((ByteArray) -> ImageBitmap?)?,
        offscreenLabelDrawScope: CanvasDrawScope,
        textMeasurer: TextMeasurer
    ) {
        val labelBitmapCache = remember { LabelBitmapCache() }
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
        }
    }
}
