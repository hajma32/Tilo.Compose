package tilo.compose.core.scale

import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.projection.Projection
import tilo.compose.core.transform.TransformationRegistry
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * Computes real-world distance between two points expressed in the supplied projection.
 *
 * Return null when the CRS cannot be measured without application-provided
 * knowledge. This keeps custom or licensed transformations injectable instead
 * of hard-wired into GeoCore.
 */
fun interface DistanceCalculator {
    fun distanceMeters(
        from: Point,
        to: Point,
        projection: Projection,
        transformationRegistry: TransformationRegistry,
    ): Double?
}

/** Built-in real-world distance strategies for scale and measurement UI. */
object DistanceCalculators {
    /**
     * Uses geodesic distance when possible.
     */
    val Auto: DistanceCalculator =
        DistanceCalculator { from, to, projection, transformationRegistry ->
            when {
                projection.sameCrsAs(Epsg4326Projection) -> haversineMeters(from, to)
                projection.sameCrsAs(Epsg3857Projection) ->
                    haversineMeters(from.webMercatorToWgs84(), to.webMercatorToWgs84())

                else -> {
                    val toWgs84 = transformationRegistry.find(projection, Epsg4326Projection)
                    if (toWgs84 != null) {
                        haversineMeters(
                            toWgs84.sourceToTarget(from),
                            toWgs84.sourceToTarget(to),
                        )
                    } else {
                        null
                    }
                }
            }
        }

    /**
     * For projected coordinate systems whose units are meters.
     */
    val PlanarMeters: DistanceCalculator =
        DistanceCalculator { from, to, _, _ ->
            planarMeters(from, to)
        }
}

private fun Projection.sameCrsAs(other: Projection): Boolean = id == other.id && definition == other.definition

internal fun planarMeters(
    from: Point,
    to: Point,
): Double {
    val dx = to.x - from.x
    val dy = to.y - from.y
    return sqrt(dx * dx + dy * dy)
}

internal fun haversineMeters(
    fromLonLat: Point,
    toLonLat: Point,
): Double {
    val lat1 = fromLonLat.y.toRadians()
    val lat2 = toLonLat.y.toRadians()
    val deltaLat = (toLonLat.y - fromLonLat.y).toRadians()
    val deltaLon = (toLonLat.x - fromLonLat.x).toRadians()
    val a =
        sin(deltaLat / 2.0).pow(2.0) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2.0).pow(2.0)
    val c = 2.0 * atan2Sqrt(a)
    return EARTH_RADIUS_METERS * c
}

private fun Point.webMercatorToWgs84(): Point {
    val lon = x / WEB_MERCATOR_RADIUS_METERS * 180.0 / PI
    val lat = atan(sinh(y / WEB_MERCATOR_RADIUS_METERS)) * 180.0 / PI
    return Point(lon, lat)
}

private fun atan2Sqrt(a: Double): Double = kotlin.math.atan2(sqrt(a), sqrt(1.0 - a))

private fun Double.toRadians(): Double = this * PI / 180.0

private const val EARTH_RADIUS_METERS = 6_371_008.8
private const val WEB_MERCATOR_RADIUS_METERS = 6_378_137.0
