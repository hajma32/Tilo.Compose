package tilo.compose.core.transform

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.projection.Projection

/**
 * WebMercator meters to geographic WGS84 lon/lat.
 */
object WebMercatorToWgs84Transformation : Transformation<Projection, Projection> {
    private const val EARTH_RADIUS = 6_378_137.0

    override val source: Projection = Epsg3857Projection
    override val target: Projection = Epsg4326Projection

    override fun sourceToTarget(point: Point): Point {
        val lon = point.x / EARTH_RADIUS * 180.0 / PI
        val lat = (2.0 * atan(exp(point.y / EARTH_RADIUS)) - PI / 2.0) * 180.0 / PI
        return Point(x = lon, y = lat)
    }

    override fun targetToSource(point: Point): Point {
        return Wgs84ToWebMercatorTransformation.sourceToTarget(point)
    }
}

