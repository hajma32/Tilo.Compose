@file:Suppress("unused")
@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render.backend

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import tilo.compose.core.map.MapState
import tilo.compose.render.ExperimentalTiloRenderingApi
import tilo.compose.render.LabelBitmapCache

@ExperimentalTiloRenderingApi
interface RenderBackend {
    val id: String

    fun onScene(scene: RenderScene) = Unit

    @Composable
    fun Content(
        modifier: Modifier,
        scene: RenderScene,
        map: MapState,
        tileDecoder: ((ByteArray) -> ImageBitmap?)?,
        offscreenLabelDrawScope: CanvasDrawScope,
        textMeasurer: TextMeasurer,
        labelBitmapCache: LabelBitmapCache,
    ) {
        Box(modifier = modifier)
    }
}
