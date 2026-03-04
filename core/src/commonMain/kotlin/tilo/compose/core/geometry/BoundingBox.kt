package tilo.compose.core.geometry

/** Bounding box defined by its four corner points. */
data class BoundingBox(
    val topLeft: Point,
    val topRight: Point,
    val bottomLeft: Point,
    val bottomRight: Point
) {
    companion object {
        fun fromPoints(points: List<Point>): BoundingBox {
            require(points.isNotEmpty()) { "Cannot create BoundingBox from empty list" }
            val xs = points.map { it.x }
            val ys = points.map { it.y }
            val minX = xs.minOrNull() ?: 0.0
            val maxX = xs.maxOrNull() ?: 0.0
            val minY = ys.minOrNull() ?: 0.0
            val maxY = ys.maxOrNull() ?: 0.0
            return BoundingBox(
                topLeft = Point(minX, maxY),
                topRight = Point(maxX, maxY),
                bottomLeft = Point(minX, minY),
                bottomRight = Point(maxX, minY)
            )
        }
    }
}

