package tilo.compose.core.geometry

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Returns the planar Euclidean distance to [other] in the points' coordinate units. */
fun Point.distanceTo(other: Point): Double = hypot(x - other.x, y - other.y)

/** Returns the shortest planar distance to the closed segment from [segmentStart] to [segmentEnd]. */
fun Point.distanceToSegment(
    segmentStart: Point,
    segmentEnd: Point,
): Double {
    val dx = segmentEnd.x - segmentStart.x
    val dy = segmentEnd.y - segmentStart.y
    if (dx == 0.0 && dy == 0.0) {
        return distanceTo(segmentStart)
    }

    val t = (((x - segmentStart.x) * dx) + ((y - segmentStart.y) * dy)) / ((dx * dx) + (dy * dy))
    val clamped = min(1.0, max(0.0, t))
    return distanceTo(
        Point(
            x = segmentStart.x + clamped * dx,
            y = segmentStart.y + clamped * dy,
        ),
    )
}

/** Returns the shortest planar distance to a line of at least two [points]. */
fun Point.distanceToLine(points: List<Point>): Double {
    require(points.size >= 2) { "Line distance requires at least two points" }
    return points.zipWithNext().minOf { (a, b) -> distanceToSegment(a, b) }
}

/** Tests this point against a closed polygon [ring] using the even-odd rule. */
fun Point.isInsideRing(ring: List<Point>): Boolean {
    if (ring.size < 4) return false

    var inside = false
    var previous = ring.last()
    ring.forEach { current ->
        val intersects =
            ((current.y > y) != (previous.y > y)) &&
                (x < (previous.x - current.x) * (y - current.y) / (previous.y - current.y) + current.x)
        if (intersects) inside = !inside
        previous = current
    }
    return inside
}

/** Returns whether this point is inside [polygon]'s exterior and outside all holes. */
fun Point.isInside(polygon: Polygon): Boolean {
    val exterior = polygon.rings.firstOrNull() ?: return false
    if (!isInsideRing(exterior)) return false
    return polygon.rings.drop(1).none { ring -> isInsideRing(ring) }
}

/** Returns the shortest planar distance to any ring boundary in [polygon]. */
fun Point.distanceToBoundary(polygon: Polygon): Double =
    polygon.rings.minOfOrNull { ring ->
        if (ring.size < 2) Double.POSITIVE_INFINITY else distanceToLine(ring)
    } ?: Double.POSITIVE_INFINITY
