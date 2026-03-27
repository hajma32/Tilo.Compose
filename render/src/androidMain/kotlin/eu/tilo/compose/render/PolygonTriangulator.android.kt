package eu.tilo.compose.render

import org.maplibre.earcut4j.Earcut

internal actual object PlatformPolygonTriangulator {
    actual fun triangulate(flattened: FlattenedPolygon): List<Int>? {
        if (flattened.vertices.size < 3) return null

        val coords = DoubleArray(flattened.vertices.size * 2)
        flattened.vertices.forEachIndexed { index, point ->
            coords[index * 2] = point.x
            coords[index * 2 + 1] = point.y
        }
        val holes = flattened.holeIndices.toIntArray()

        return runCatching {
            Earcut.earcut(coords, holes, 2)
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }
}
