package tilo.compose.core.render

import eu.tilo.compose.render.RenderLineString
import eu.tilo.compose.render.RenderPolygon
import eu.tilo.compose.render.VectorMeshBuilder
import eu.tilo.compose.render.VectorMeshPrimitive
import eu.tilo.compose.render.VectorTileMeshCache
import eu.tilo.compose.render.meshWorldToScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import tilo.compose.core.feature.BaseStyle
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.Map
import tilo.compose.core.map.Viewport

class VectorMeshTests {

    @Test
    fun lineMeshBuildsQuadStripTriangles() {
        val mesh = VectorMeshBuilder.buildLineMesh(
            listOf(
                RenderLineString(
                    id = "10/20/30:road",
                    points = listOf(Point(0.0, 0.0), Point(10.0, 0.0), Point(20.0, 0.0)),
                    style = BaseStyle(strokeColor = 0xFF112233, strokeWidth = 4.0)
                )
            )
        )

        assertNotNull(mesh)
        assertEquals(VectorMeshPrimitive.LINE, mesh.primitive)
        assertEquals(6, mesh.vertices.size)
        assertEquals(12, mesh.indices.size)
    }

    @Test
    fun polygonMeshTriangulatesSimpleRing() {
        val prepared = VectorMeshBuilder.buildPolygonMesh(
            listOf(
                RenderPolygon(
                    id = "10/20/30:land",
                    rings = listOf(
                        listOf(
                            Point(0.0, 0.0),
                            Point(10.0, 0.0),
                            Point(10.0, 10.0),
                            Point(0.0, 10.0),
                            Point(0.0, 0.0)
                        )
                    ),
                    style = BaseStyle(fillColor = 0xFF00AA00)
                )
            )
        )

        assertEquals(1, prepared.meshBatch.size)
        assertTrue(prepared.fallbackPolygons.isEmpty())
        val batch = prepared.meshBatch.single()
        assertEquals(VectorMeshPrimitive.POLYGON_FILL, batch.primitive)
        assertEquals(4, batch.vertices.size)
        assertEquals(6, batch.indices.size)
    }

    @Test
    fun tileMeshCacheEvictsOldestPolygonTileByLru() {
        val cache = VectorTileMeshCache(maxTiles = 1)
        val map = testMap()

        cache.prepare(
            commands = listOf(
                RenderPolygon(
                    id = "1/1/1:land",
                    rings = listOf(
                        listOf(
                            Point(0.0, 0.0),
                            Point(10.0, 0.0),
                            Point(10.0, 10.0),
                            Point(0.0, 10.0),
                            Point(0.0, 0.0)
                        )
                    ),
                    style = BaseStyle(fillColor = 0xFF00AA00)
                )
            ),
            map = map
        )
        assertTrue(cache.debugSnapshotKeys().single().startsWith("1/1/1|"))

        cache.prepare(
            commands = listOf(
                RenderPolygon(
                    id = "1/1/2:land",
                    rings = listOf(
                        listOf(
                            Point(0.0, 0.0),
                            Point(10.0, 0.0),
                            Point(10.0, 10.0),
                            Point(0.0, 10.0),
                            Point(0.0, 0.0)
                        )
                    ),
                    style = BaseStyle(fillColor = 0xFF00AA00)
                )
            ),
            map = map
        )

        val keys = cache.debugSnapshotKeys()
        assertEquals(1, keys.size)
        assertTrue(keys.single().startsWith("1/1/2|"))
    }

    @Test
    fun meshTransformMatchesMapWorldToScreen() {
        val map = Map(
            center = Point(100.0, 50.0),
            zoom = 3.5,
            viewport = Viewport(width = 1080, height = 1920, pixelRatio = 3.0)
        )
        val point = Point(110.0, 47.5)

        val expected = map.worldToScreen(point)
        val actual = meshWorldToScreen(point, map)

        assertEquals(expected.x.toFloat(), actual.x, 0.001f)
        assertEquals(expected.y.toFloat(), actual.y, 0.001f)
    }

    @Test
    fun lineMeshAddsBevelJoinTriangleForTurn() {
        val mesh = VectorMeshBuilder.buildLineMesh(
            listOf(
                RenderLineString(
                    id = "10/20/30:road-turn",
                    points = listOf(Point(0.0, 0.0), Point(10.0, 0.0), Point(10.0, 10.0)),
                    style = BaseStyle(strokeColor = 0xFF112233, strokeWidth = 4.0)
                )
            )
        )

        assertNotNull(mesh)
        assertEquals(9, mesh.vertices.size)
        assertEquals(15, mesh.indices.size)
    }

    private fun testMap(): Map = Map(
        center = Point(0.0, 0.0),
        zoom = 0.0,
        viewport = Viewport(width = 256, height = 256, pixelRatio = 1.0)
    )
}
