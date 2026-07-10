package tilo.compose.render

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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

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
    val rect = tile.screenRect(map)

    if (image == null) {
        drawTilePlaceholder(rect.x, rect.y, rect.width, rect.height)
    } else {
        drawImage(
            image = image,
            dstOffset = IntOffset(rect.x, rect.y),
            dstSize = IntSize(rect.width, rect.height)
        )
    }
}

private fun Tile.screenRect(map: Map): TileScreenRect {
    val topLeft = map.worldToScreen(bounds.topLeft)
    val bottomRight = map.worldToScreen(bounds.bottomRight)

    val left = floor(min(topLeft.x, bottomRight.x)).toInt()
    val top = floor(min(topLeft.y, bottomRight.y)).toInt()
    val right = ceil(max(topLeft.x, bottomRight.x)).toInt()
    val bottom = ceil(max(topLeft.y, bottomRight.y)).toInt()

    return TileScreenRect(
        x = left,
        y = top,
        width = (right - left).coerceAtLeast(1),
        height = (bottom - top).coerceAtLeast(1),
    )
}

private data class TileScreenRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

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
