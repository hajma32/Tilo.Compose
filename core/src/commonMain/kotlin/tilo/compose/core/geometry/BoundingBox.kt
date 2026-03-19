package tilo.compose.core.geometry

/** Axis-aligned bounding box defined by its four corners. */
data class BoundingBox(
    val topLeft: Point,
    val topRight: Point,
    val bottomLeft: Point,
    val bottomRight: Point
) {
    val minX: Double get() = topLeft.x
    val maxX: Double get() = topRight.x
    val minY: Double get() = bottomLeft.y
    val maxY: Double get() = topLeft.y

    fun intersects(other: BoundingBox): Boolean =
        maxX >= other.minX && other.maxX >= minX &&
            maxY >= other.minY && other.maxY >= minY

    fun contains(point: Point): Boolean {
        return point.x in minX..maxX && point.y in minY..maxY
    }

    companion object {
        fun fromPoints(points: List<Point>): BoundingBox {
            require(points.isNotEmpty()) { "Cannot create BoundingBox from empty list" }
            val minX = points.minOf { it.x }
            val maxX = points.maxOf { it.x }
            val minY = points.minOf { it.y }
            val maxY = points.maxOf { it.y }
            return fromExtents(minX = minX, maxX = maxX, minY = minY, maxY = maxY)
        }

        fun fromExtents(minX: Double, maxX: Double, minY: Double, maxY: Double): BoundingBox {
            return BoundingBox(
                topLeft = Point(minX, maxY),
                topRight = Point(maxX, maxY),
                bottomLeft = Point(minX, minY),
                bottomRight = Point(maxX, minY)
            )
        }
    }
}
