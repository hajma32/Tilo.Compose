@file:Suppress("unused")
@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render.backend

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import tilo.compose.core.geometry.Point
import tilo.compose.core.tile.Tile
import tilo.compose.render.ExperimentalTiloRenderingApi
import tilo.compose.render.RenderCommand

@ExperimentalTiloRenderingApi
sealed interface RenderSceneLayer {
    val id: String
    val zIndex: Int
}

@ExperimentalTiloRenderingApi
data class RasterRenderSceneLayer(
    override val id: String,
    override val zIndex: Int,
    val tiles: List<Tile>,
    val decodedImages: List<ImageBitmap?>? = null,
) : RenderSceneLayer

@ExperimentalTiloRenderingApi
class VectorRenderSceneLayer(
    override val id: String,
    override val zIndex: Int,
    val commands: List<RenderCommand>,
    val pointIconPainters: Map<String, Painter> = emptyMap(),
) : RenderSceneLayer

@ExperimentalTiloRenderingApi
data class VectorBitmapRenderSceneLayer(
    override val id: String,
    override val zIndex: Int,
    val bitmap: ImageBitmap,
    val snapshot: VectorBitmapSnapshot,
) : RenderSceneLayer

@ExperimentalTiloRenderingApi
data class VectorBitmapSnapshot(
    val center: Point,
    val zoom: Double,
    val bitmapWidth: Int,
    val bitmapHeight: Int,
    val displayWidth: Int,
    val displayHeight: Int,
)

@ExperimentalTiloRenderingApi
data class RenderScene(
    val layers: List<RenderSceneLayer>,
) {
    companion object {
        val Empty = RenderScene(emptyList())
    }
}
