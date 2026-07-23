package tilo.compose.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import tilo.compose.core.map.MapState
import tilo.compose.core.tile.Tile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val TILE_PLACEHOLDER_BORDER_WIDTH_PX = 1f

internal data class TilePlaceholderColors(
    val fill: Color,
    val border: Color,
) {
    companion object {
        val Light = TilePlaceholderColors(fill = Color(0xFFE3F2FD), border = Color(0xFF90CAF9))
        val Dark = TilePlaceholderColors(fill = Color(0xFF102A43), border = Color(0xFF28547A))
    }
}

/**
 * Draws [tiles] onto the canvas using [map].worldToScreen for positioning.
 */
internal fun DrawScope.drawTiles(
    tiles: List<Tile>,
    tileDecoder: (ByteArray) -> ImageBitmap?,
    map: MapState,
    decodedImages: List<ImageBitmap?>? = null,
    opacity: Double = 1.0,
    placeholderColors: TilePlaceholderColors = TilePlaceholderColors.Light,
) {
    val tileImages = resolveTileImages(tiles, tileDecoder, decodedImages)
    withTransform({
        rotate(
            degrees = -map.bearing.toFloat(),
            pivot = Offset(map.viewport.width / 2f, map.viewport.height / 2f),
        )
    }) {
        tileImages.filter { (_, image) -> image == null }.forEach { (tile, _) ->
            drawTile(tile, image = null, map = map, opacity = opacity, placeholderColors = placeholderColors)
        }
        tileImages.filter { (_, image) -> image != null }.forEach { (tile, image) ->
            drawTile(tile, image = image, map = map, opacity = opacity, placeholderColors = placeholderColors)
        }
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
    opacity: Double,
    placeholderColors: TilePlaceholderColors,
) {
    val rect = tile.screenRect(map)

    if (image == null) {
        drawTilePlaceholder(rect.x, rect.y, rect.width, rect.height, opacity, placeholderColors)
    } else {
        drawImage(
            image = image,
            dstOffset = IntOffset(rect.x, rect.y),
            dstSize = IntSize(rect.width, rect.height),
            alpha = opacity.toFloat(),
        )
    }
}

internal fun Tile.screenRect(map: MapState): TileScreenRect {
    val topLeft =
        map.viewport.worldToScreen(
            bounds.topLeft,
            map.center,
            map.zoom,
            map.projection.worldUnitsPerMapUnit,
        )
    val bottomRight =
        map.viewport.worldToScreen(
            bounds.bottomRight,
            map.center,
            map.zoom,
            map.projection.worldUnitsPerMapUnit,
        )

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
    opacity: Double,
    colors: TilePlaceholderColors,
) {
    val topLeft = Offset(x.toFloat(), y.toFloat())
    val size = Size(width.toFloat(), height.toFloat())
    drawRect(
        color = colors.fill.copy(alpha = colors.fill.alpha * opacity.toFloat()),
        topLeft = topLeft,
        size = size,
    )
    drawRect(
        color = colors.border.copy(alpha = colors.border.alpha * opacity.toFloat()),
        topLeft = topLeft,
        size = size,
        style = Stroke(width = TILE_PLACEHOLDER_BORDER_WIDTH_PX),
    )
}
