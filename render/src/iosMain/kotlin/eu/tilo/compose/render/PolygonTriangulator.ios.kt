package eu.tilo.compose.render

import tilo.compose.core.geometry.Point

internal actual object PlatformPolygonTriangulator {
    actual fun triangulate(flattened: FlattenedPolygon): List<Int>? {
        if (flattened.holeIndices.isNotEmpty()) return null
        return earClip(flattened.vertices)
    }

    private fun earClip(points: List<Point>): List<Int>? {
        if (points.size < 3) return null

        val vertexIndices = if (signedArea(points) >= 0.0) {
            points.indices.toMutableList()
        } else {
            points.indices.reversed().toMutableList()
        }

        val triangles = mutableListOf<Int>()
        var guard = 0
        while (vertexIndices.size > 3 && guard < points.size * points.size) {
            var earFound = false
            for (i in vertexIndices.indices) {
                val prevIndex = vertexIndices[(i - 1 + vertexIndices.size) % vertexIndices.size]
                val currIndex = vertexIndices[i]
                val nextIndex = vertexIndices[(i + 1) % vertexIndices.size]
                val a = points[prevIndex]
                val b = points[currIndex]
                val c = points[nextIndex]
                if (!isConvex(a, b, c)) continue
                if (containsPoint(points, vertexIndices, prevIndex, currIndex, nextIndex)) continue

                triangles += prevIndex
                triangles += currIndex
                triangles += nextIndex
                vertexIndices.removeAt(i)
                earFound = true
                break
            }
            if (!earFound) return null
            guard++
        }

        if (vertexIndices.size == 3) {
            triangles += vertexIndices[0]
            triangles += vertexIndices[1]
            triangles += vertexIndices[2]
        }
        return triangles
    }

    private fun containsPoint(
        points: List<Point>,
        remaining: List<Int>,
        aIndex: Int,
        bIndex: Int,
        cIndex: Int
    ): Boolean {
        val a = points[aIndex]
        val b = points[bIndex]
        val c = points[cIndex]
        return remaining.any { index ->
            if (index == aIndex || index == bIndex || index == cIndex) return@any false
            pointInTriangle(points[index], a, b, c)
        }
    }

    private fun pointInTriangle(p: Point, a: Point, b: Point, c: Point): Boolean {
        val d1 = cross(p, a, b)
        val d2 = cross(p, b, c)
        val d3 = cross(p, c, a)
        val hasNeg = d1 < 0.0 || d2 < 0.0 || d3 < 0.0
        val hasPos = d1 > 0.0 || d2 > 0.0 || d3 > 0.0
        return !(hasNeg && hasPos)
    }

    private fun isConvex(a: Point, b: Point, c: Point): Boolean = cross(a, b, c) > 0.0

    private fun cross(a: Point, b: Point, c: Point): Double =
        (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    private fun signedArea(points: List<Point>): Double {
        var area = 0.0
        for (i in points.indices) {
            val current = points[i]
            val next = points[(i + 1) % points.size]
            area += current.x * next.y - next.x * current.y
        }
        return area / 2.0
    }
}

