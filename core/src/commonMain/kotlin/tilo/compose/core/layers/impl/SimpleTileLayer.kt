package tilo.compose.core.layers.impl

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import tilo.compose.core.layers.TileLayer
import tilo.compose.core.map.Viewport
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.source.WMSSource
import tilo.compose.core.tile.source.WmsTileRequest
import tilo.compose.core.transform.CoordinateSystem
import tilo.compose.core.transform.Wgs84WebMercatorCoordinateSystem

/**
 * Simple concrete TileLayer used as an example and for tests.
 */
class SimpleTileLayer(
    override val id: String,
    override val source: WMSSource,
    private val coordinateSystem: CoordinateSystem = Wgs84WebMercatorCoordinateSystem,
    private val tileSizePx: Int = 256
) : TileLayer {

    override fun buildRequests(
        zoomLevel: Int,
        centerLon: Double,
        centerLat: Double,
        viewport: Viewport,
        tileCount: Int
    ): List<WmsTileRequest> {
        require(zoomLevel >= 0) { "zoomLevel must be >= 0" }
        require(tileCount > 0) { "tileCount must be > 0" }

        val centerX = coordinateSystem.lonToTileX(centerLon, zoomLevel)
        val centerY = coordinateSystem.latToTileY(centerLat, zoomLevel)
        val gridSide = max(1, ceil(sqrt(tileCount.toDouble())).toInt())
        val radius = gridSide / 2
        val maxIndex = (2.0.pow(zoomLevel.toDouble()).toInt() - 1)

        val requests = mutableListOf<WmsTileRequest>()
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (requests.size >= tileCount) break

                val rawX = centerX + dx
                val x = wrapX(rawX, zoomLevel)
                val y = min(max(centerY + dy, 0), maxIndex)
                requests += WmsTileRequest(
                    coordinate = TileCoordinate(z = zoomLevel, x = x, y = y),
                    bbox = coordinateSystem.tileBbox(x, y, zoomLevel),
                    width = tileSizePx,
                    height = tileSizePx
                )
            }
        }

        return requests
    }

    override fun update() {
        // no-op example implementation
    }

    override fun dispose() {
        // no-op example implementation
    }

    private fun wrapX(x: Int, z: Int): Int {
        val n = 2.0.pow(z.toDouble()).toInt()
        val mod = x % n
        return if (mod < 0) mod + n else mod
    }
}
