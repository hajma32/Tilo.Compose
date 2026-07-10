@file:Suppress("unused")

package eu.tilo.compose.render.backend

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import eu.tilo.compose.render.LabelBitmapCache
import tilo.compose.core.map.Map

interface RenderBackend {
    val id: String

    fun onScene(scene: RenderScene) = Unit

    @Composable
    fun Content(
        modifier: Modifier,
        scene: RenderScene,
        map: Map,
        tileDecoder: ((ByteArray) -> ImageBitmap?)?,
        offscreenLabelDrawScope: CanvasDrawScope,
        textMeasurer: TextMeasurer,
        labelBitmapCache: LabelBitmapCache,
    ) {
        Box(modifier = modifier)
    }
}
