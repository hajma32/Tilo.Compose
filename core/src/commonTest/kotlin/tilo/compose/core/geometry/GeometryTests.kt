package tilo.compose.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GeometryTests {

    @Test
    fun pointHasCoordinates() {
        val p = Point(1.0, 2.0)
        assertEquals(1.0, p.x)
        assertEquals(2.0, p.y)
    }

    @Test
    fun lineStringRequiresAtLeastTwoPoints() {
        assertFailsWith<IllegalArgumentException> {
            LineString(listOf(Point(0.0, 0.0)))
        }
    }

    @Test
    fun polygonRequiresClosedRingAndMinPoints() {
        // ring too short
        assertFailsWith<IllegalArgumentException> {
            Polygon(listOf(listOf(Point(0.0, 0.0), Point(1.0, 1.0), Point(0.0, 0.0))))
        }

        // not closed
        assertFailsWith<IllegalArgumentException> {
            Polygon(listOf(listOf(Point(0.0, 0.0), Point(1.0, 1.0), Point(1.0, 0.0), Point(0.0, 1.0))))
        }

        // valid polygon
        val ring = listOf(
            Point(0.0, 0.0),
            Point(1.0, 0.0),
            Point(1.0, 1.0),
            Point(0.0, 0.0)
        )
        val poly = Polygon(listOf(ring))
        // ring must be closed
        assertTrue(poly.rings.first().first() == poly.rings.first().last())
    }

    @Test
    fun boundingBoxFromPointsCalculatesCorners() {
        val pts = listOf(
            Point(0.0, 0.0),
            Point(2.0, 3.0),
            Point(1.0, -1.0)
        )
        val box = BoundingBox.fromPoints(pts)
        assertEquals(Point(0.0, 3.0), box.topLeft)
        assertEquals(Point(2.0, 3.0), box.topRight)
        assertEquals(Point(0.0, -1.0), box.bottomLeft)
        assertEquals(Point(2.0, -1.0), box.bottomRight)
    }
}
