package tilo.compose.core.tile.utils

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import tilo.compose.core.tile.TileCoordinate

/**
 * Shared tile grid math for layers.
 */
object TilePlanner {

    fun computeTileCoordinates(
        zoomLevel: Int,
        centerLon: Double,
        centerLat: Double,
        tileCount: Int
    ): List<TileCoordinate> {
        require(zoomLevel >= 0) { "zoomLevel must be >= 0" }
        require(tileCount > 0) { "tileCount must be > 0" }

        val centerX = WebMercatorTileMath.lonToTileX(centerLon, zoomLevel)
        val centerY = WebMercatorTileMath.latToTileY(centerLat, zoomLevel)
        val gridSide = max(1, ceil(sqrt(tileCount.toDouble())).toInt())
        val radius = gridSide / 2
        val maxIndex = (2.0.pow(zoomLevel.toDouble()).toInt() - 1)

        val out = mutableListOf<TileCoordinate>()
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (out.size >= tileCount) break
                val x = wrapX(centerX + dx, zoomLevel)
                val y = (centerY + dy).coerceIn(0, maxIndex)
                out += TileCoordinate(z = zoomLevel, x = x, y = y)
            }
        }
        return out
    }

    private fun wrapX(x: Int, zoomLevel: Int): Int {
        val n = 2.0.pow(zoomLevel.toDouble()).toInt().coerceAtLeast(1)
        val mod = x % n
        return if (mod < 0) mod + n else mod
    }
}
