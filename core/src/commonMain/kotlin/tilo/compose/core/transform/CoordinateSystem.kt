package tilo.compose.core.transform

import tilo.compose.core.geometry.Point
import tilo.compose.core.map.Viewport

/**
 * Axis-aligned WMS bounding box in source projection units.
 */
data class WmsBbox(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
)

/**
 * Coordinate system abstraction used across rendering and tile sourcing.
 */
interface CoordinateSystem {
    fun worldToScreen(world: Point, center: Point, zoom: Double, viewport: Viewport): Point
    fun screenToWorld(screen: Point, center: Point, zoom: Double, viewport: Viewport): Point

    /** WMS projection parameter name (for example `SRS` or `CRS`). */
    val wmsProjectionParameterName: String
        get() = throw UnsupportedOperationException("WMS projection parameter is not supported by this coordinate system")

    /** WMS projection code value (for example `EPSG:3857`). */
    val wmsProjectionCode: String
        get() = throw UnsupportedOperationException("WMS projection code is not supported by this coordinate system")

    /** Convert longitude to tile X index at zoom level for WMS tile addressing. */
    fun lonToTileX(lon: Double, zoomLevel: Int): Int {
        throw UnsupportedOperationException("Tile addressing is not supported by this coordinate system")
    }

    /** Convert latitude to tile Y index at zoom level for WMS tile addressing. */
    fun latToTileY(lat: Double, zoomLevel: Int): Int {
        throw UnsupportedOperationException("Tile addressing is not supported by this coordinate system")
    }

    /** Compute WMS BBOX for a tile in this coordinate system. */
    fun tileBbox(x: Int, y: Int, zoomLevel: Int): WmsBbox {
        throw UnsupportedOperationException("WMS BBOX is not supported by this coordinate system")
    }
}
