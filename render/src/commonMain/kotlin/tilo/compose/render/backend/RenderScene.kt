@file:Suppress("unused")

package tilo.compose.render.backend

import androidx.compose.ui.graphics.ImageBitmap
import tilo.compose.render.RenderCommand
import tilo.compose.core.geometry.Point
import tilo.compose.core.tile.Tile

sealed interface RenderSceneLayer {
    val id: String
    val zIndex: Int
}

data class RasterRenderSceneLayer(
    override val id: String,
    override val zIndex: Int,
    val tiles: List<Tile>,
    val decodedImages: List<ImageBitmap?>? = null
) : RenderSceneLayer

class VectorRenderSceneLayer(
    override val id: String,
    override val zIndex: Int,
    val commands: List<RenderCommand>
) : RenderSceneLayer

data class VectorBitmapRenderSceneLayer(
    override val id: String,
    override val zIndex: Int,
    val bitmap: ImageBitmap,
    val snapshot: VectorBitmapSnapshot,
) : RenderSceneLayer

data class VectorBitmapSnapshot(
    val center: Point,
    val zoom: Double,
    val bitmapWidth: Int,
    val bitmapHeight: Int,
    val displayWidth: Int,
    val displayHeight: Int,
)

data class RenderScene(
    val layers: List<RenderSceneLayer>
) {
    companion object {
        val Empty = RenderScene(emptyList())
    }
}
