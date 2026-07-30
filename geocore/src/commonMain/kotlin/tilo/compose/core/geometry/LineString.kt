package tilo.compose.core.geometry

/** A LineString is a sequence of Points (at least two). */
data class LineString(
    val points: List<Point>,
) : Geometry {
    init {
        require(points.size >= 2) { "LineString must contain at least two points" }
    }
}
