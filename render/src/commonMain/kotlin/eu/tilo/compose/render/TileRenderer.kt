package eu.tilo.compose.render

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import tilo.compose.core.map.Map
import tilo.compose.core.tile.Tile
import kotlin.math.roundToInt

/**
 * Draws [tiles] onto the canvas using [map].worldToScreen for positioning.
 */
internal fun DrawScope.drawTiles(
    tiles: List<Tile>,
    tileDecoder: (ByteArray) -> ImageBitmap?,
    map: Map
) {
    tiles.forEach { tile ->
        val bytes = tile.bytes ?: return@forEach
        val image = tileDecoder(bytes) ?: return@forEach

        val topLeft     = map.worldToScreen(tile.bounds.topLeft)
        val bottomRight = map.worldToScreen(tile.bounds.bottomRight)

        val screenX = topLeft.x.roundToInt()
        val screenY = topLeft.y.roundToInt()
        val screenW = (bottomRight.x - topLeft.x).roundToInt().coerceAtLeast(1)
        val screenH = (bottomRight.y - topLeft.y).roundToInt().coerceAtLeast(1)

        drawImage(
            image     = image,
            dstOffset = IntOffset(screenX, screenY),
            dstSize   = IntSize(screenW, screenH)
        )
    }
}
