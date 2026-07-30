package tilo.compose.core.map

import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MapFitBoundsTest {
    @Test
    fun fitBoundsCentersBoundsAndKeepsCornersInsidePadding() {
        val map =
            MapState(
                viewport = Viewport(width = 1_000, height = 600, pixelRatio = 2.0),
                config = MapConfig(minZoom = 0.0, maxZoom = 20.0),
            )
        val bounds = BoundingBox.fromExtents(minX = -100.0, maxX = 100.0, minY = -50.0, maxY = 50.0)

        map.fitBounds(bounds, paddingPx = 40.0)

        assertEquals(Point(0.0, 0.0), map.center)
        val topLeft = map.worldToScreen(Point(bounds.minX, bounds.maxY))
        val bottomRight = map.worldToScreen(Point(bounds.maxX, bounds.minY))
        assertTrue(topLeft.x >= 39.999)
        assertTrue(topLeft.y >= 39.999)
        assertTrue(bottomRight.x <= 960.001)
        assertTrue(bottomRight.y <= 560.001)
    }

    @Test
    fun fitBoundsClampsToConfiguredZoomRange() {
        val map =
            MapState(
                viewport = Viewport(width = 1_000, height = 600),
                config = MapConfig(minZoom = 3.0, maxZoom = 8.0),
            )

        map.fitBounds(BoundingBox.fromExtents(-10_000.0, 10_000.0, -10_000.0, 10_000.0))

        assertEquals(3.0, map.zoom)
    }

    @Test
    fun fitBoundsResetsBearingToNorth() {
        val map = MapState(bearing = 127.0, viewport = Viewport(width = 300, height = 200))

        map.fitBounds(BoundingBox.fromExtents(-20.0, 20.0, -10.0, 10.0), paddingPx = 10.0)

        assertEquals(0.0, map.bearing)
    }

    @Test
    fun rejectedFitBoundsDoesNotResetBearing() {
        val map = MapState(bearing = 127.0, viewport = Viewport(width = 300, height = 200))

        assertFailsWith<IllegalArgumentException> {
            map.fitBounds(BoundingBox.fromExtents(-20.0, 20.0, -10.0, 10.0), paddingPx = 100.0)
        }

        assertEquals(127.0, map.bearing)
    }

    @Test
    fun fitBoundsRejectsInvalidOrUnusablePadding() {
        val map = MapState(viewport = Viewport(width = 300, height = 200))
        val bounds = BoundingBox.fromExtents(-10.0, 10.0, -10.0, 10.0)

        assertFailsWith<IllegalArgumentException> { map.fitBounds(bounds, paddingPx = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { map.fitBounds(bounds, paddingPx = 100.0) }
    }

    @Test
    fun fitBoundsRejectsNonFiniteOrReversedBounds() {
        val map = MapState(viewport = Viewport(width = 300, height = 200))

        assertFailsWith<IllegalArgumentException> {
            map.fitBounds(BoundingBox.fromExtents(Double.NaN, 10.0, -10.0, 10.0))
        }
        assertFailsWith<IllegalArgumentException> {
            map.fitBounds(BoundingBox.fromExtents(10.0, -10.0, -10.0, 10.0))
        }
        assertFailsWith<IllegalArgumentException> {
            map.fitBounds(BoundingBox.fromExtents(-10.0, 10.0, 10.0, -10.0))
        }
    }
}
