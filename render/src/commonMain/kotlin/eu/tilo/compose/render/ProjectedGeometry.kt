package eu.tilo.compose.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan
import tilo.compose.core.geometry.Geometry
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.MultiLineString
import tilo.compose.core.geometry.MultiPoint
import tilo.compose.core.geometry.MultiPolygon
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon

internal data class MercatorPoint(
    val u: Double,
    val v: Double
)

internal data class ProjectedBounds(
    val minU: Double,
    val minV: Double,
    val maxU: Double,
    val maxV: Double
)

internal sealed interface ProjectedGeometry {
    val bounds: ProjectedBounds
}

internal data class ProjectedPointGeometry(
    val point: MercatorPoint,
    override val bounds: ProjectedBounds
) : ProjectedGeometry

internal data class ProjectedMultiPointGeometry(
    val points: List<MercatorPoint>,
    override val bounds: ProjectedBounds
) : ProjectedGeometry

internal data class ProjectedLineGeometry(
    val points: List<MercatorPoint>,
    override val bounds: ProjectedBounds
) : ProjectedGeometry

internal data class ProjectedMultiLineGeometry(
    val lines: List<List<MercatorPoint>>,
    override val bounds: ProjectedBounds
) : ProjectedGeometry

internal data class ProjectedPolygonGeometry(
    val rings: List<List<MercatorPoint>>,
    override val bounds: ProjectedBounds
) : ProjectedGeometry

internal data class ProjectedMultiPolygonGeometry(
    val polygons: List<List<List<MercatorPoint>>>,
    override val bounds: ProjectedBounds
) : ProjectedGeometry

internal fun projectGeometry(geometry: Geometry): ProjectedGeometry {
    return when (geometry) {
        is Point -> {
            val projected = projectPoint(geometry)
            ProjectedPointGeometry(projected, boundsOf(listOf(projected)))
        }

        is MultiPoint -> {
            val points = geometry.points.map(::projectPoint)
            ProjectedMultiPointGeometry(points, boundsOf(points))
        }

        is LineString -> {
            val points = geometry.points.map(::projectPoint)
            ProjectedLineGeometry(points, boundsOf(points))
        }

        is MultiLineString -> {
            val lines = geometry.lines.map { line -> line.points.map(::projectPoint) }
            ProjectedMultiLineGeometry(lines, boundsOf(lines.flatten()))
        }

        is Polygon -> {
            val rings = geometry.rings.map { ring -> ring.map(::projectPoint) }
            ProjectedPolygonGeometry(rings, boundsOf(rings.flatten()))
        }

        is MultiPolygon -> {
            val polygons = geometry.polygons.map { polygon ->
                polygon.rings.map { ring -> ring.map(::projectPoint) }
            }
            val points = polygons.flatMap { poly -> poly.flatten() }
            ProjectedMultiPolygonGeometry(polygons, boundsOf(points))
        }
    }
}

internal fun projectPoint(point: Point): MercatorPoint {
    val u = (point.x + 180.0) / 360.0
    val clampedLat = point.y.coerceIn(-85.05112878, 85.05112878)
    val latRad = clampedLat * PI / 180.0
    val v = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0
    return MercatorPoint(u = u, v = v)
}

internal fun projectedAnchor(geometry: ProjectedGeometry): MercatorPoint {
    return MercatorPoint(
        u = (geometry.bounds.minU + geometry.bounds.maxU) / 2.0,
        v = (geometry.bounds.minV + geometry.bounds.maxV) / 2.0
    )
}

private fun boundsOf(points: List<MercatorPoint>): ProjectedBounds {
    val minU = points.minOf { it.u }
    val minV = points.minOf { it.v }
    val maxU = points.maxOf { it.u }
    val maxV = points.maxOf { it.v }
    return ProjectedBounds(minU = minU, minV = minV, maxU = maxU, maxV = maxV)
}

