package tilo.compose.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeometryMeasurementsTest {
    @Test
    fun measuresDistanceToSegment() {
        val point = Point(5.0, 4.0)

        val distance =
            point.distanceToSegment(
                segmentStart = Point(0.0, 0.0),
                segmentEnd = Point(10.0, 0.0),
            )

        assertEquals(4.0, distance)
    }

    @Test
    fun detectsPointInsidePolygonWithHole() {
        val polygon =
            Polygon(
                rings =
                    listOf(
                        listOf(
                            Point(0.0, 0.0),
                            Point(10.0, 0.0),
                            Point(10.0, 10.0),
                            Point(0.0, 10.0),
                            Point(0.0, 0.0),
                        ),
                        listOf(
                            Point(3.0, 3.0),
                            Point(7.0, 3.0),
                            Point(7.0, 7.0),
                            Point(3.0, 7.0),
                            Point(3.0, 3.0),
                        ),
                    ),
            )

        assertTrue(Point(2.0, 2.0).isInside(polygon))
        assertFalse(Point(5.0, 5.0).isInside(polygon))
    }
}
