package eu.tilo.compose.render

import kotlin.math.abs
import kotlin.math.hypot
import tilo.compose.core.geometry.Point

internal object VectorMeshBuilder {

    fun buildLineMesh(
        commands: List<RenderLineString>,
        widthWorldUnits: Double? = null
    ): VectorMeshBatch? {
        if (commands.isEmpty()) return null
        val style = VectorMeshStyle.from(commands.first().style)
        val width = (widthWorldUnits ?: (commands.first().style.strokeWidth ?: 2.0)).coerceAtLeast(1e-9)

        val vertices = mutableListOf<Point>()
        val indices = mutableListOf<Int>()

        commands.forEach { command ->
            val mesh = buildPolylineMesh(command.points, width) ?: return@forEach
            val baseIndex = vertices.size
            vertices += mesh.vertices
            indices += mesh.indices.map { it + baseIndex }
        }

        if (vertices.isEmpty() || indices.isEmpty()) return null
        return VectorMeshBatch(
            primitive = VectorMeshPrimitive.LINE,
            style = style,
            vertices = vertices,
            indices = indices
        )
    }

    fun buildPolygonMesh(commands: List<RenderPolygon>): PreparedPolygonBatch {
        if (commands.isEmpty()) return PreparedPolygonBatch(emptyList(), emptyList())
        val style = VectorMeshStyle.from(commands.first().style)
        val vertices = mutableListOf<Point>()
        val indices = mutableListOf<Int>()
        val fallback = mutableListOf<RenderPolygon>()

        commands.forEach { command ->
            val mesh = triangulatePolygon(command) ?: run {
                fallback += command
                return@forEach
            }
            val baseIndex = vertices.size
            vertices += mesh.vertices
            indices += mesh.indices.map { it + baseIndex }
        }

        val batch = if (vertices.isNotEmpty() && indices.isNotEmpty()) {
            VectorMeshBatch(
                primitive = VectorMeshPrimitive.POLYGON_FILL,
                style = style,
                vertices = vertices,
                indices = indices
            )
        } else {
            null
        }

        return PreparedPolygonBatch(
            meshBatch = batch?.let(::listOf).orEmpty(),
            fallbackPolygons = fallback
        )
    }

    private fun triangulatePolygon(command: RenderPolygon): IndexedMesh? {
        val flattened = flattenPolygonRings(command.rings) ?: return null
        if (flattened.vertices.size < 3) return null
        val triangles = PlatformPolygonTriangulator.triangulate(flattened) ?: return null
        if (triangles.isEmpty()) return null
        return IndexedMesh(vertices = flattened.vertices, indices = triangles)
    }

    private fun buildPolylineMesh(points: List<Point>, width: Double): IndexedMesh? {
        if (points.size < 2) return null
        val halfWidth = width / 2.0
        val segmentNormals = points.zipWithNext { a, b -> perpendicularUnit(a, b) }
        if (segmentNormals.any { it == ZeroPoint }) return null

        val vertices = ArrayList<Point>(points.lastIndex * 4)
        val indices = ArrayList<Int>(points.lastIndex * 9)

        for (segmentIndex in 0 until points.lastIndex) {
            val start = points[segmentIndex]
            val end = points[segmentIndex + 1]
            val normal = segmentNormals[segmentIndex]
            val base = vertices.size

            val startLeft = offsetPoint(start, normal, halfWidth)
            val startRight = offsetPoint(start, normal, -halfWidth)
            val endLeft = offsetPoint(end, normal, halfWidth)
            val endRight = offsetPoint(end, normal, -halfWidth)

            vertices += startLeft
            vertices += startRight
            vertices += endLeft
            vertices += endRight

            indices += base
            indices += base + 1
            indices += base + 2
            indices += base + 1
            indices += base + 3
            indices += base + 2

            if (segmentIndex > 0) {
                appendBevelJoin(
                    joinPoint = start,
                    previousNormal = segmentNormals[segmentIndex - 1],
                    currentNormal = normal,
                    previousSegmentBase = base - 4,
                    currentSegmentBase = base,
                    vertices = vertices,
                    indices = indices
                )
            }
        }

        return IndexedMesh(vertices = vertices, indices = indices)
    }

    private fun appendBevelJoin(
        joinPoint: Point,
        previousNormal: Point,
        currentNormal: Point,
        previousSegmentBase: Int,
        currentSegmentBase: Int,
        vertices: MutableList<Point>,
        indices: MutableList<Int>
    ) {
        val turn = cross(previousNormal, currentNormal)
        if (abs(turn) <= 1e-9) return

        val joinVertex = vertices.size
        vertices += joinPoint

        if (turn > 0.0) {
            // Left turn -> outer side is on the left offsets.
            indices += previousSegmentBase + 2
            indices += currentSegmentBase
            indices += joinVertex
        } else {
            // Right turn -> outer side is on the right offsets.
            indices += previousSegmentBase + 3
            indices += joinVertex
            indices += currentSegmentBase + 1
        }
    }

    private fun offsetPoint(point: Point, normal: Point, distance: Double): Point = Point(
        x = point.x + normal.x * distance,
        y = point.y + normal.y * distance
    )

    private fun cross(a: Point, b: Point): Double = a.x * b.y - a.y * b.x

    private fun perpendicularUnit(a: Point, b: Point): Point {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val length = hypot(dx, dy)
        if (length <= 1e-9) return ZeroPoint
        return Point(x = -dy / length, y = dx / length)
    }

    private data class IndexedMesh(
        val vertices: List<Point>,
        val indices: List<Int>
    )

    internal data class PreparedPolygonBatch(
        val meshBatch: List<VectorMeshBatch>,
        val fallbackPolygons: List<RenderPolygon>
    )

    private val ZeroPoint = Point(0.0, 0.0)
}
