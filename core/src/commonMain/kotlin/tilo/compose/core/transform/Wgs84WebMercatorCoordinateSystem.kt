package tilo.compose.core.transform

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.Viewport

/**
 * WGS-84 lon/lat coordinate system rendered using WebMercator pixel math.
 *
 * - world.x = longitude in degrees
 * - world.y = latitude in degrees
 */
object Wgs84WebMercatorCoordinateSystem : CoordinateSystem {
    private const val TILE_SIZE = 256.0

    override fun worldToScreen(world: Point, center: Point, zoom: Double, viewport: Viewport): Point {
        val worldPx = lonLatToGlobalPixel(world.x, world.y, zoom)
        val centerPx = lonLatToGlobalPixel(center.x, center.y, zoom)
        return Point(
            x = worldPx.x - centerPx.x + viewport.width / 2.0,
            y = worldPx.y - centerPx.y + viewport.height / 2.0
        )
    }

    override fun screenToWorld(screen: Point, center: Point, zoom: Double, viewport: Viewport): Point {
        val centerPx = lonLatToGlobalPixel(center.x, center.y, zoom)
        val globalX = screen.x + centerPx.x - viewport.width / 2.0
        val globalY = screen.y + centerPx.y - viewport.height / 2.0
        return globalPixelToLonLat(globalX, globalY, zoom)
    }

    private fun lonLatToGlobalPixel(lon: Double, lat: Double, zoom: Double): Point {
        val scale = TILE_SIZE * 2.0.pow(zoom)
        val x = (lon + 180.0) / 360.0 * scale

        val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
        val latRad = clampedLat * PI / 180.0
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * scale

        return Point(x, y)
    }

    private fun globalPixelToLonLat(x: Double, y: Double, zoom: Double): Point {
        val scale = TILE_SIZE * 2.0.pow(zoom)
        val lon = x / scale * 360.0 - 180.0

        val n = PI - 2.0 * PI * y / scale
        val lat = atan(sinh(n)) * 180.0 / PI
        return Point(lon, lat)
    }
}

