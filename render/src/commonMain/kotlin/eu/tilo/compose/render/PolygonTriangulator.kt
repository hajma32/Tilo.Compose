package eu.tilo.compose.render

import tilo.compose.core.geometry.Point

internal data class FlattenedPolygon(
    val vertices: List<Point>,
    val holeIndices: List<Int>
)

internal fun flattenPolygonRings(rings: List<List<Point>>): FlattenedPolygon? {
    if (rings.isEmpty()) return null

    val vertices = mutableListOf<Point>()
    val holeIndices = mutableListOf<Int>()

    rings.forEachIndexed { index, ring ->
        val sanitized = sanitizeRing(ring)
        if (sanitized.size < 3) return@forEachIndexed
        if (index > 0) {
            holeIndices += vertices.size
        }
        vertices += sanitized
    }

    if (vertices.size < 3) return null
    return FlattenedPolygon(vertices = vertices, holeIndices = holeIndices)
}

private fun sanitizeRing(ring: List<Point>): List<Point> {
    if (ring.size < 2) return ring
    return if (ring.first() == ring.last()) ring.dropLast(1) else ring
}

internal expect object PlatformPolygonTriangulator {
    fun triangulate(flattened: FlattenedPolygon): List<Int>?
}
