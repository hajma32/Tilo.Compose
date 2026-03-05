package tilo.compose.core.transform

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
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

    override val wmsProjectionParameterName: String = "SRS"
    override val wmsProjectionCode: String = "EPSG:3857"

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

    override fun lonToTileX(lon: Double, zoomLevel: Int): Int {
        val n = 2.0.pow(zoomLevel.toDouble())
        return floor((lon + 180.0) / 360.0 * n).toInt()
    }

    override fun latToTileY(lat: Double, zoomLevel: Int): Int {
        val latRad = lat * PI / 180.0
        val n = 2.0.pow(zoomLevel.toDouble())
        return floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()
    }

    override fun tileBbox(x: Int, y: Int, zoomLevel: Int): WmsBbox {
        val n = 2.0.pow(zoomLevel.toDouble())

        val lonLeft = x / n * 360.0 - 180.0
        val lonRight = (x + 1.0) / n * 360.0 - 180.0

        val latTop = tileYToLat(y.toDouble(), n)
        val latBottom = tileYToLat(y + 1.0, n)

        val left = lonToMercX(lonLeft)
        val right = lonToMercX(lonRight)
        val top = latToMercY(latTop)
        val bottom = latToMercY(latBottom)

        return WmsBbox(minX = left, minY = bottom, maxX = right, maxY = top)
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

    private fun tileYToLat(y: Double, n: Double): Double {
        val t = PI * (1.0 - 2.0 * y / n)
        return atan(sinh(t)) * 180.0 / PI
    }

    private fun lonToMercX(lon: Double): Double = lon * 20037508.34 / 180.0

    private fun latToMercY(lat: Double): Double {
        val value = ln(tan((90.0 + lat) * PI / 360.0)) / (PI / 180.0)
        return value * 20037508.34 / 180.0
    }
}
