package tilo.compose.render

import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GeometryLodSimplifierTest {
    @Test
    fun douglasPeuckerRemovesNearCollinearVerticesAndKeepsEndpoints() {
        val points =
            listOf(
                Point(0.0, 0.0),
                Point(1.0, 0.01),
                Point(2.0, -0.01),
                Point(3.0, 0.0),
            )

        val simplified = GeometryLodSimplifier.simplifyOpenLine(points, tolerance = 0.1)

        assertEquals(listOf(points.first(), points.last()), simplified)
    }

    @Test
    fun douglasPeuckerRetainsVerticesOutsideTolerance() {
        val corner = Point(1.0, 1.0)
        val points = listOf(Point(0.0, 0.0), corner, Point(2.0, 0.0))

        val simplified = GeometryLodSimplifier.simplifyOpenLine(points, tolerance = 0.25)

        assertEquals(points, simplified)
    }

    @Test
    fun polygonRingRemainsClosedAndValidAfterSimplification() {
        val ring =
            listOf(
                Point(0.0, 0.0),
                Point(1.0, 0.01),
                Point(2.0, 0.0),
                Point(2.01, 1.0),
                Point(2.0, 2.0),
                Point(1.0, 1.99),
                Point(0.0, 2.0),
                Point(-0.01, 1.0),
                Point(0.0, 0.0),
            )
        val feature = Feature(key = "polygon", geometry = Polygon(listOf(ring)))

        val simplified = GeometryLodSimplifier.simplify(listOf(feature), tolerance = 0.1)
        val polygon = assertIs<Polygon>(simplified.single().geometry)
        val simplifiedRing = polygon.rings.single()

        assertTrue(simplifiedRing.size < ring.size)
        assertTrue(simplifiedRing.size >= 4)
        assertEquals(simplifiedRing.first(), simplifiedRing.last())
    }

    @Test
    fun pointsAreNotChangedByLod() {
        val feature = Feature(key = "point", geometry = Point(1.0, 2.0))

        val simplified = GeometryLodSimplifier.simplify(listOf(feature), tolerance = 100.0)

        assertTrue(simplified.single() === feature)
    }
}
