package tilo.compose.render

import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Geometry
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.MultiLineString
import tilo.compose.core.geometry.MultiPoint
import tilo.compose.core.geometry.MultiPolygon
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon

/** Douglas-Peucker simplification for camera-independent projected vector geometry. */
internal object GeometryLodSimplifier {
    fun simplify(
        features: List<Feature>,
        tolerance: Double,
    ): List<Feature> {
        if (tolerance <= 0.0 || features.isEmpty()) return features
        return features.map { feature ->
            val simplified = simplifyGeometry(feature.geometry, tolerance)
            if (simplified === feature.geometry) feature else feature.copy(geometry = simplified)
        }
    }

    private fun simplifyGeometry(
        geometry: Geometry,
        tolerance: Double,
    ): Geometry =
        when (geometry) {
            is Point -> geometry
            is MultiPoint -> geometry
            is LineString -> LineString(simplifyOpenLine(geometry.points, tolerance))
            is MultiLineString ->
                MultiLineString(
                    geometry.lines.map { line -> LineString(simplifyOpenLine(line.points, tolerance)) },
                )
            is Polygon -> Polygon(geometry.rings.map { ring -> simplifyClosedRing(ring, tolerance) })
            is MultiPolygon ->
                MultiPolygon(
                    geometry.polygons.map { polygon ->
                        Polygon(polygon.rings.map { ring -> simplifyClosedRing(ring, tolerance) })
                    },
                )
        }

    internal fun simplifyOpenLine(
        points: List<Point>,
        tolerance: Double,
    ): List<Point> {
        if (points.size <= 2 || tolerance <= 0.0) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        val ranges = mutableListOf(0, points.lastIndex)
        val toleranceSquared = tolerance * tolerance

        while (ranges.isNotEmpty()) {
            val end = ranges.removeAt(ranges.lastIndex)
            val start = ranges.removeAt(ranges.lastIndex)
            var furthestIndex = -1
            var furthestDistance = toleranceSquared
            for (index in start + 1 until end) {
                val distance = points[index].squaredDistanceToSegment(points[start], points[end])
                if (distance > furthestDistance) {
                    furthestDistance = distance
                    furthestIndex = index
                }
            }
            if (furthestIndex >= 0) {
                keep[furthestIndex] = true
                ranges += start
                ranges += furthestIndex
                ranges += furthestIndex
                ranges += end
            }
        }

        return points.filterIndexed { index, _ -> keep[index] }
    }

    internal fun simplifyClosedRing(
        ring: List<Point>,
        tolerance: Double,
    ): List<Point> {
        if (ring.size <= 4 || tolerance <= 0.0) return ring
        val openRing = ring.dropLast(1)
        if (openRing.size <= 3) return ring

        var firstAnchor = openRing.furthestIndexFrom(0)
        val secondAnchor = openRing.furthestIndexFrom(firstAnchor)
        firstAnchor = openRing.furthestIndexFrom(secondAnchor)
        if (firstAnchor == secondAnchor) return ring

        val firstChain = openRing.cyclicSlice(firstAnchor, secondAnchor)
        val secondChain = openRing.cyclicSlice(secondAnchor, firstAnchor)
        val simplifiedOpen =
            simplifyOpenLine(firstChain, tolerance).dropLast(1) +
                simplifyOpenLine(secondChain, tolerance).dropLast(1)
        if (simplifiedOpen.distinct().size < 3) return ring
        return simplifiedOpen + simplifiedOpen.first()
    }
}

private fun Point.squaredDistanceToSegment(
    start: Point,
    end: Point,
): Double {
    val dx = end.x - start.x
    val dy = end.y - start.y
    if (dx == 0.0 && dy == 0.0) return squaredDistanceTo(start)
    val projection = ((x - start.x) * dx + (y - start.y) * dy) / (dx * dx + dy * dy)
    val clamped = projection.coerceIn(0.0, 1.0)
    val nearestX = start.x + clamped * dx
    val nearestY = start.y + clamped * dy
    val offsetX = x - nearestX
    val offsetY = y - nearestY
    return offsetX * offsetX + offsetY * offsetY
}

private fun Point.squaredDistanceTo(other: Point): Double {
    val dx = x - other.x
    val dy = y - other.y
    return dx * dx + dy * dy
}

private fun List<Point>.furthestIndexFrom(index: Int): Int {
    var furthestIndex = index
    var furthestDistance = -1.0
    forEachIndexed { candidateIndex, candidate ->
        val distance = candidate.squaredDistanceTo(this[index])
        if (distance > furthestDistance) {
            furthestDistance = distance
            furthestIndex = candidateIndex
        }
    }
    return furthestIndex
}

private fun List<Point>.cyclicSlice(
    start: Int,
    endInclusive: Int,
): List<Point> =
    buildList {
        var index = start
        while (true) {
            add(this@cyclicSlice[index])
            if (index == endInclusive) break
            index = (index + 1) % this@cyclicSlice.size
        }
    }
