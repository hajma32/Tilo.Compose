package tilo.compose.core.geometry

/**
 * Polygon represented as a list of rings. The first ring is the exterior ring.
 * Each ring is a list of Points where the first and last point SHOULD be equal (closed ring).
 */
data class Polygon(
    val rings: List<List<Point>>,
) : Geometry {
    init {
        require(rings.isNotEmpty()) { "Polygon must have at least one ring" }
        rings.forEach { ring ->
            require(ring.size >= 4) { "Each ring must contain at least 4 points (closed ring)" }
            require(ring.first() == ring.last()) { "Ring must be closed (first and last point equal)" }
        }
    }
}
