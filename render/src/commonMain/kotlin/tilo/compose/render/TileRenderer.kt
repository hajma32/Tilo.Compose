package tilo.compose.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import tilo.compose.core.map.MapState
import tilo.compose.core.tile.Tile
import kotlin.math.max
import kotlin.math.min
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
    map: MapState,
    decodedImages: List<ImageBitmap?>? = null,
) {
    val tileImages = resolveTileImages(tiles, tileDecoder, decodedImages)
    tileImages.filter { (_, image) -> image == null }.forEach { (tile, _) ->
        drawTile(tile, image = null, map = map)
    }
    tileImages.filter { (_, image) -> image != null }.forEach { (tile, image) ->
        drawTile(tile, image = image, map = map)
    }
}

internal fun resolveTileImages(
    tiles: List<Tile>,
    tileDecoder: (ByteArray) -> ImageBitmap?,
    decodedImages: List<ImageBitmap?>?,
): List<Pair<Tile, ImageBitmap?>> =
    tiles.mapIndexed { index, tile ->
        val image =
            if (decodedImages != null) {
                decodedImages.getOrNull(index)
            } else {
                tile.bytes?.let(tileDecoder)
            }
        tile to image
    }

private fun DrawScope.drawTile(
    tile: Tile,
    image: ImageBitmap?,
    map: MapState,
) {
    val rect = tile.screenRect(map)

    if (image == null) {
        drawTilePlaceholder(rect.x, rect.y, rect.width, rect.height)
    } else {
        drawImage(
            image = image,
            dstOffset = IntOffset(rect.x, rect.y),
            dstSize = IntSize(rect.width, rect.height),
        )
    }
}

internal fun Tile.screenRect(map: MapState): TileScreenRect {
    val topLeft = map.worldToScreen(bounds.topLeft)
    val bottomRight = map.worldToScreen(bounds.bottomRight)

    val left = min(topLeft.x, bottomRight.x).roundToInt()
    val top = min(topLeft.y, bottomRight.y).roundToInt()
    val right = max(topLeft.x, bottomRight.x).roundToInt()
    val bottom = max(topLeft.y, bottomRight.y).roundToInt()

    return TileScreenRect(
        x = left,
        y = top,
        width = (right - left).coerceAtLeast(1),
        height = (bottom - top).coerceAtLeast(1),
    )
}

internal data class TileScreenRect(
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
