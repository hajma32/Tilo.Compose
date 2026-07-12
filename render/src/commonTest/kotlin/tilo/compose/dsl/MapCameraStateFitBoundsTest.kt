package tilo.compose.dsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapConfig
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport

class MapCameraStateFitBoundsTest {

    @Test
    fun zoomRevisionChangesOnlyWhenZoomChanges() {
        val map = MapState()
        val cameraState = MapCameraState(map)

        map.center = Point(10.0, 20.0)
        cameraState.markChanged()
        assertEquals(0, cameraState.zoomRevision)

        map.zoom = 2.0
        cameraState.markChanged()
        assertEquals(1, cameraState.zoomRevision)
        assertEquals(2.0, cameraState.zoom)
    }

    @Test
    fun fitBoundsCapsDefaultPaddingForSmallViewport() {
        val map = MapState(
            viewport = Viewport(width = 80, height = 60),
            config = MapConfig(minZoom = 0.0, maxZoom = 20.0),
        )
        val cameraState = MapCameraState(map)
        val bounds = BoundingBox.fromExtents(-10.0, 10.0, -10.0, 10.0)

        cameraState.fitBounds(bounds)

        assertEquals(Point(0.0, 0.0), cameraState.center)
        val topLeft = map.worldToScreen(Point(bounds.minX, bounds.maxY))
        val bottomRight = map.worldToScreen(Point(bounds.maxX, bounds.minY))
        assertTrue(topLeft.x >= 0.0)
        assertTrue(topLeft.y >= 0.0)
        assertTrue(bottomRight.x <= map.viewport.width)
        assertTrue(bottomRight.y <= map.viewport.height)
    }
}
