package tilo.compose.core.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MapStateTests {

    @Test
    fun zoomIsClampedToSettings() {
        val state = MapState(zoom = 5.0, settings = MapSettings(minZoom = 2.0, maxZoom = 10.0))
        state.zoom = 20.0
        assertEquals(10.0, state.zoom)
        state.zoom = 1.0
        assertEquals(2.0, state.zoom)
    }

    @Test
    fun panByChangesCenter() {
        val state = MapState(center = tilo.compose.core.geometry.Point(0.0, 0.0), zoom = 1.0, viewport = Viewport(100, 100))
        state.panBy(10.0, 0.0)
        // Expect center.x to change (simple sanity check)
        assertNotEquals(state.center.x, 0.0)
    }

    @Test
    fun zoomByWithFocusKeepsWorldPointUnderFocus() {
        val state = MapState(center = tilo.compose.core.geometry.Point(0.0, 0.0), zoom = 1.0, viewport = Viewport(200, 200))
        val focusScreen = tilo.compose.core.geometry.Point(50.0, 50.0)
        val worldBefore = state.screenToWorld(focusScreen)
        state.zoomBy(1.0, focusScreen)
        val worldAfter = state.screenToWorld(focusScreen)
        assertEquals(worldBefore, worldAfter)
    }
}
