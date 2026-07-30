package tilo.compose.core.map

import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertEquals

class MapCameraBoundsTest {
    private val bounds = BoundingBox.fromExtents(minX = -100.0, maxX = 100.0, minY = -100.0, maxY = 100.0)

    @Test
    fun initialCameraAndPanKeepVisibleViewportInsideBounds() {
        val map =
            MapState(
                center = Point(500.0, -500.0),
                zoom = 0.0,
                config = MapConfig(cameraBounds = bounds),
                viewport = Viewport(width = 100, height = 80),
            )

        assertEquals(Point(50.0, -60.0), map.center)

        map.panBy(dx = 1_000.0, dy = -1_000.0)

        assertEquals(Point(50.0, 60.0), map.center)
        assertEquals(bounds.maxX, map.screenToWorld(Point(100.0, 40.0)).x)
        assertEquals(bounds.maxY, map.screenToWorld(Point(50.0, 0.0)).y)
    }

    @Test
    fun focusedZoomCannotMoveCameraOutsideBounds() {
        val map =
            MapState(
                center = Point(50.0, 0.0),
                zoom = 0.0,
                config = MapConfig(cameraBounds = bounds),
                viewport = Viewport(width = 100, height = 100),
            )

        map.zoomBy(delta = 2.0, focus = Point(100.0, 50.0))

        assertEquals(87.5, map.center.x)
        assertEquals(bounds.maxX, map.screenToWorld(Point(100.0, 50.0)).x)
    }

    @Test
    fun viewportResizeReappliesBounds() {
        val map =
            MapState(
                center = Point(50.0, 0.0),
                config = MapConfig(cameraBounds = bounds),
                viewport = Viewport(width = 100, height = 100),
            )

        map.viewport = Viewport(width = 160, height = 100)

        assertEquals(Point(20.0, 0.0), map.center)
    }

    @Test
    fun viewportLargerThanBoundsCentersConstrainedAxis() {
        val map =
            MapState(
                center = Point(80.0, 20.0),
                config = MapConfig(cameraBounds = bounds),
                viewport = Viewport(width = 300, height = 100),
            )

        assertEquals(Point(0.0, 20.0), map.center)
    }

    @Test
    fun zoomAlwaysUsesConfiguredRange() {
        val map = MapState(zoom = 100.0, config = MapConfig(minZoom = 3.0, maxZoom = 8.0))

        assertEquals(8.0, map.zoom)
        map.zoomBy(-100.0)
        assertEquals(3.0, map.zoom)
    }
}
