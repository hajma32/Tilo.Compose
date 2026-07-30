package tilo.compose.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BoundingBoxTest {
    @Test
    fun createsCornersFromValidExtents() {
        val bounds = BoundingBox.fromExtents(minX = 1.0, maxX = 4.0, minY = 2.0, maxY = 8.0)

        assertEquals(Point(1.0, 8.0), bounds.topLeft)
        assertEquals(Point(4.0, 2.0), bounds.bottomRight)
    }

    @Test
    fun rejectsInvalidExtents() {
        assertFailsWith<IllegalArgumentException> {
            BoundingBox.fromExtents(minX = 4.0, maxX = 1.0, minY = 2.0, maxY = 8.0)
        }
        assertFailsWith<IllegalArgumentException> {
            BoundingBox.fromExtents(minX = Double.NaN, maxX = 1.0, minY = 2.0, maxY = 8.0)
        }
    }
}
