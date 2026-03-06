package tilo.compose.core.tile.utils

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan
import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.geometry.Point

/**
 * Tile math helpers for WebMercator tiling.
 *
 * This object keeps current WebMercator convenience APIs but also exposes
 * resolution/origin-based BBOX math used by generic tile grids.
 */
object WebMercatorTileMath {

    fun lonToTileX(lon: Double, zoomLevel: Int): Int {
        val n = 2.0.pow(zoomLevel.toDouble())
        return floor((lon + 180.0) / 360.0 * n).toInt()
    }

    fun latToTileY(lat: Double, zoomLevel: Int): Int {
        val latRad = lat * PI / 180.0
        val n = 2.0.pow(zoomLevel.toDouble())
        return floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()
    }

    fun tileBbox(x: Int, y: Int, zoomLevel: Int): BoundingBox {
        val resolutions = webMercatorResolutions(zoomLevel)
        return tileBbox(
            z = zoomLevel,
            x = x,
            y = y,
            originX = WEB_MERCATOR_MIN_X,
            originY = WEB_MERCATOR_MAX_Y,
            tileSize = DEFAULT_TILE_SIZE,
            resolutions = resolutions
        )
    }

    fun tileBbox(
        z: Int,
        x: Int,
        y: Int,
        originX: Double,
        originY: Double,
        tileSize: Int,
        resolutions: DoubleArray
    ): BoundingBox {
        val res = resolutions[z]
        val tileWidth = tileSize * res
        val tileHeight = tileSize * res

        val minX = originX + x * tileWidth
        val maxX = originX + (x + 1) * tileWidth

        val maxY = originY - y * tileHeight
        val minY = originY - (y + 1) * tileHeight

        return BoundingBox(
            topLeft = Point(minX, maxY),
            topRight = Point(maxX, maxY),
            bottomLeft = Point(minX, minY),
            bottomRight = Point(maxX, minY)
        )
    }

    fun webMercatorResolution(zoomLevel: Int, tileSize: Int = DEFAULT_TILE_SIZE): Double {
        val initial = (2.0 * PI * WEB_MERCATOR_RADIUS) / tileSize.toDouble()
        return initial / 2.0.pow(zoomLevel.toDouble())
    }

    fun webMercatorResolutions(maxZoomLevel: Int, tileSize: Int = DEFAULT_TILE_SIZE): DoubleArray {
        return DoubleArray(maxZoomLevel + 1) { z -> webMercatorResolution(z, tileSize) }
    }


    const val WEB_MERCATOR_RADIUS = 6378137.0
    const val WEB_MERCATOR_MIN_X = -20037508.342789244
    const val WEB_MERCATOR_MAX_Y = 20037508.342789244
    const val DEFAULT_TILE_SIZE = 256

}
