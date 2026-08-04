package tilo.compose.core.geometry

/**
 * 2D point with double precision coordinates.
 */

data class Point(
    val x: Double,
    val y: Double,
) : Geometry {
    init {
        require(x.isFinite() && y.isFinite()) { "Point coordinates must be finite" }
    }
}
