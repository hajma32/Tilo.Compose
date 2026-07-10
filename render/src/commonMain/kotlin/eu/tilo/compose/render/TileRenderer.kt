package eu.tilo.compose.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import tilo.compose.core.map.Map
import tilo.compose.core.tile.Tile
import kotlin.math.roundToInt

private val TILE_PLACEHOLDER_FILL = Color(0xFFE3F2FD)
private val TILE_PLACEHOLDER_BORDER = Color(0xFF90CAF9)
private const val TILE_PLACEHOLDER_BORDER_WIDTH_PX = 1f

/**
 * Draws [tiles] onto the canvas using [map].worldToScreen for positioning.
 */
internal fun DrawScope.drawTiles(
    tiles: List<Tile>,
    tileDecoder: (ByteArray) -> ImageBitmap?,
    map: Map,
    decodedImages: List<ImageBitmap?>? = null
) {
    val tileImages = tiles.mapIndexed { idx, tile ->
        tile to (decodedImages?.getOrNull(idx) ?: tile.bytes?.let { tileDecoder(it) })
    }
    tileImages.filter { (_, image) -> image == null }.forEach { (tile, _) ->
        drawTile(tile, image = null, map = map)
    }
    tileImages.filter { (_, image) -> image != null }.forEach { (tile, image) ->
        drawTile(tile, image = image, map = map)
    }
}

private fun DrawScope.drawTile(
    tile: Tile,
    image: ImageBitmap?,
    map: Map,
) {
        val topLeft     = map.worldToScreen(tile.bounds.topLeft)
        val bottomRight = map.worldToScreen(tile.bounds.bottomRight)

        val screenX = topLeft.x.roundToInt()
        val screenY = topLeft.y.roundToInt()
        val screenW = (bottomRight.x - topLeft.x).roundToInt().coerceAtLeast(1)
        val screenH = (bottomRight.y - topLeft.y).roundToInt().coerceAtLeast(1)

        if (image == null) {
            drawTilePlaceholder(screenX, screenY, screenW, screenH)
        } else {
            drawImage(
                image = image,
                dstOffset = IntOffset(screenX, screenY),
                dstSize = IntSize(screenW, screenH)
            )
        }
}

private fun DrawScope.drawTilePlaceholder(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
) {
    val topLeft = Offset(x.toFloat(), y.toFloat())
    val size = Size(width.toFloat(), height.toFloat())
    drawRect(
        color = TILE_PLACEHOLDER_FILL,
        topLeft = topLeft,
        size = size,
    )
    drawRect(
        color = TILE_PLACEHOLDER_BORDER,
        topLeft = topLeft,
        size = size,
        style = Stroke(width = TILE_PLACEHOLDER_BORDER_WIDTH_PX),
    )
}
