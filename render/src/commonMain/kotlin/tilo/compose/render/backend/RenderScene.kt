@file:Suppress("unused")
@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render.backend

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import tilo.compose.core.geometry.Point
import tilo.compose.core.tile.Tile
import tilo.compose.render.ExperimentalTiloRenderingApi
import tilo.compose.render.RenderCommand

/** Ordered raster or vector content resolved for one render frame. */
@ExperimentalTiloRenderingApi
sealed interface RenderSceneLayer {
    val id: String
    val zIndex: Int
    val opacity: Double
}

/** Raster tiles and their optional decoded images at one position in the scene stack. */
@ExperimentalTiloRenderingApi
data class RasterRenderSceneLayer(
    override val id: String,
    override val zIndex: Int,
    val tiles: List<Tile>,
    val decodedImages: List<ImageBitmap?>? = null,
    override val opacity: Double = 1.0,
    val placeholderTiles: List<Tile> = emptyList(),
) : RenderSceneLayer {
    init {
        require(opacity in 0.0..1.0) { "opacity must be between 0.0 and 1.0" }
    }
}

/** Immediate vector commands and layer-local point icon painters. */
@ExperimentalTiloRenderingApi
class VectorRenderSceneLayer(
    override val id: String,
    override val zIndex: Int,
    val commands: List<RenderCommand>,
    val pointIconPainters: Map<String, Painter> = emptyMap(),
    override val opacity: Double = 1.0,
) : RenderSceneLayer {
    init {
        require(opacity in 0.0..1.0) { "opacity must be between 0.0 and 1.0" }
    }
}

/** Cached vector bitmap plus the camera snapshot at which it was rendered. */
@ExperimentalTiloRenderingApi
data class VectorBitmapRenderSceneLayer(
    override val id: String,
    override val zIndex: Int,
    val bitmap: ImageBitmap,
    val snapshot: VectorBitmapSnapshot,
    override val opacity: Double = 1.0,
) : RenderSceneLayer {
    init {
        require(opacity in 0.0..1.0) { "opacity must be between 0.0 and 1.0" }
    }
}

/** Camera and viewport values required to place a cached vector bitmap in a later frame. */
@ExperimentalTiloRenderingApi
data class VectorBitmapSnapshot(
    val center: Point,
    val zoom: Double,
    val bitmapWidth: Int,
    val bitmapHeight: Int,
    val displayWidth: Int,
    val displayHeight: Int,
    val bearing: Double = 0.0,
)

/** Immutable, back-to-front ordered input passed to a [RenderBackend]. */
@ExperimentalTiloRenderingApi
data class RenderScene(
    val layers: List<RenderSceneLayer>,
) {
    companion object {
        val Empty = RenderScene(emptyList())
    }
}
