package tilo.compose.core.tile.vector

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.pow
import kotlin.math.sinh
import tilo.compose.core.geometry.Geometry
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.MultiLineString
import tilo.compose.core.geometry.MultiPoint
import tilo.compose.core.geometry.MultiPolygon
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon
import tilo.compose.core.tile.TileCoordinate

class VectorTileGeometryDecoder {
    private companion object {
        const val MIN_POINTS_FOR_DECIMATION = 24
    }

    fun decodeGeometry(
        feature: VectorTileFeature,
        extent: Int,
        tile: TileCoordinate
    ): Geometry? {
        val paths = decodePaths(feature.geometryCommands)
        if (paths.isEmpty()) return null

        return when (feature.geometryType) {
            VectorTileGeometryType.POINT -> decodePoints(paths, extent, tile)
            VectorTileGeometryType.LINESTRING -> decodeLines(paths, extent, tile)
            VectorTileGeometryType.POLYGON -> decodePolygons(paths, extent, tile)
        }
    }

    private fun decodePoints(paths: List<List<IntPoint>>, extent: Int, tile: TileCoordinate): Geometry? {
        val points = paths
            .flatMap { it }
            .map { point -> tilePointToLonLat(point.x, point.y, extent, tile) }

        return when (points.size) {
            0 -> null
            1 -> points.first()
            else -> {
                val sampled = if (tile.z < 13 && points.size > 200) {
                    points.filterIndexed { index, _ -> index % 4 == 0 }
                } else {
                    points
                }
                MultiPoint(sampled)
            }
        }
    }

    private fun decodeLines(paths: List<List<IntPoint>>, extent: Int, tile: TileCoordinate): Geometry? {
        val lines = paths
            .map { path -> path.map { point -> tilePointToLonLat(point.x, point.y, extent, tile) } }
            .map { points -> simplifyLine(points, tile.z) }
            .filter { points -> points.size >= 2 }
            .map(::LineString)

        return when (lines.size) {
            0 -> null
            1 -> lines.first()
            else -> MultiLineString(lines)
        }
    }

    private fun decodePolygons(paths: List<List<IntPoint>>, extent: Int, tile: TileCoordinate): Geometry? {
        val rings = paths.map { ring ->
            val points = ring.map { point -> tilePointToLonLat(point.x, point.y, extent, tile) }
            val simplified = simplifyRing(points, tile.z)
            if (simplified.isNotEmpty() && simplified.first() != simplified.last()) {
                simplified + simplified.first()
            } else {
                simplified
            }
        }.filter { ring -> ring.size >= 4 }

        return when {
            rings.isEmpty() -> null
            rings.size == 1 -> Polygon(rings = listOf(rings.first()))
            else -> MultiPolygon(rings.map { Polygon(rings = listOf(it)) })
        }
    }

    private fun simplifyLine(points: List<Point>, zoom: Int): List<Point> {
        if (points.size < MIN_POINTS_FOR_DECIMATION) return points
        val step = when {
            zoom < 11 -> 8
            zoom < 13 -> 5
            zoom < 15 -> 3
            else -> 1
        }
        if (step <= 1) return points

        val sampled = points.filterIndexed { index, _ ->
            index == 0 || index == points.lastIndex || index % step == 0
        }
        return if (sampled.size >= 2) sampled else points
    }

    private fun simplifyRing(points: List<Point>, zoom: Int): List<Point> {
        if (points.size < MIN_POINTS_FOR_DECIMATION) return points
        val step = when {
            zoom < 11 -> 10
            zoom < 13 -> 6
            zoom < 15 -> 3
            else -> 1
        }
        if (step <= 1) return points

        val sampled = points.filterIndexed { index, _ -> index % step == 0 }
        return if (sampled.size >= 4) sampled else points
    }

    private data class IntPoint(val x: Int, val y: Int)

    private fun decodePaths(commands: List<Int>): List<List<IntPoint>> {
        var x = 0
        var y = 0
        var index = 0
        val paths = mutableListOf<MutableList<IntPoint>>()
        var current: MutableList<IntPoint>? = null

        while (index < commands.size) {
            val command = commands[index] and 0x7
            val count = commands[index] ushr 3
            index++

            when (command) {
                1 -> {
                    repeat(count) {
                        if (index + 1 >= commands.size) return@repeat
                        x += zigZagDecode(commands[index++])
                        y += zigZagDecode(commands[index++])
                        current = mutableListOf(IntPoint(x, y))
                        paths += current
                    }
                }

                2 -> {
                    repeat(count) {
                        val currentPath = current ?: return@repeat
                        if (index + 1 >= commands.size) return@repeat
                        x += zigZagDecode(commands[index++])
                        y += zigZagDecode(commands[index++])
                        currentPath.add(IntPoint(x, y))
                    }
                }

                7 -> {
                    repeat(count) {
                        val ring = current
                        if (ring != null && ring.isNotEmpty() && ring.first() != ring.last()) {
                            ring.add(ring.first())
                        }
                    }
                }

                else -> break
            }
        }

        return paths
    }

    private fun zigZagDecode(value: Int): Int = (value ushr 1) xor -(value and 1)

    private fun tilePointToLonLat(localX: Int, localY: Int, extent: Int, tile: TileCoordinate): Point {
        val denom = 2.0.pow(tile.z.toDouble())
        val nx = (tile.x + (localX / extent.toDouble())) / denom
        val ny = (tile.y + (localY / extent.toDouble())) / denom

        val lon = nx * 360.0 - 180.0
        val lat = atan(sinh(PI * (1.0 - 2.0 * ny))) * 180.0 / PI
        return Point(lon, lat)
    }
}
