package tilo.compose.core.transform

import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan
import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.projection.Projection

/**
 * Geographic WGS84 lon/lat to WebMercator meters.
 */
object Wgs84ToWebMercatorTransformation : Transformation<Projection, Projection> {
    private const val EARTH_RADIUS = 6_378_137.0
    private const val MAX_LATITUDE = 85.05112878

    override val source: Projection = Epsg4326Projection
    override val target: Projection = Epsg3857Projection

    override fun sourceToTarget(point: Point): Point {
        val lon = point.x.coerceIn(-180.0, 180.0)
        val lat = point.y.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val lonRad = lon * PI / 180.0
        val latRad = lat * PI / 180.0

        return Point(
            x = EARTH_RADIUS * lonRad,
            y = EARTH_RADIUS * ln(tan(PI / 4.0 + latRad / 2.0))
        )
    }

    override fun targetToSource(point: Point): Point {
        return WebMercatorToWgs84Transformation.sourceToTarget(point)
    }
}

