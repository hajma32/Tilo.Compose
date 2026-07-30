package tilo.compose.core.geometry

import tilo.spatial.SpatialRect

/** Returns the smallest axis-aligned box containing every coordinate in this geometry. */
fun Geometry.bounds(): BoundingBox = BoundingBox.fromPoints(pointsForBounds())

/** Converts this geometry bounding box to the spatial-index rectangle model. */
fun BoundingBox.toSpatialRect(): SpatialRect =
    SpatialRect(
        minX = minX,
        minY = minY,
        maxX = maxX,
        maxY = maxY,
    )

/** Converts this spatial-index rectangle to the geometry bounding-box model. */
fun SpatialRect.toBoundingBox(): BoundingBox =
    BoundingBox.fromExtents(
        minX = minX,
        maxX = maxX,
        minY = minY,
        maxY = maxY,
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
