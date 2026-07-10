package tilo.compose.core.geometry

import tilo.spatial.SpatialRect

fun Geometry.bounds(): BoundingBox =
    BoundingBox.fromPoints(pointsForBounds())

fun BoundingBox.toSpatialRect(): SpatialRect =
    SpatialRect(
        minX = minX,
        minY = minY,
        maxX = maxX,
        maxY = maxY
    )

fun SpatialRect.toBoundingBox(): BoundingBox =
    BoundingBox.fromExtents(
        minX = minX,
        maxX = maxX,
        minY = minY,
        maxY = maxY
    )

private fun Geometry.pointsForBounds(): List<Point> =
    when (this) {
        is Point -> listOf(this)
        is MultiPoint -> points
        is LineString -> points
        is MultiLineString -> lines.flatMap { it.points }
        is Polygon -> rings.flatten()
        is MultiPolygon -> polygons.flatMap { it.rings.flatten() }
    }
