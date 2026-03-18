package tilo.compose.core.map

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.transform.Wgs84ToWebMercatorTransformation

class MapTests {

    @Test
    fun zoomByIsClampedToConfig() {
        val state = Map(zoom = 5.0, config = MapConfig(minZoom = 2.0, maxZoom = 10.0))
        state.zoomBy(100.0)
        assertEquals(10.0, state.zoom)
        state.zoomBy(-100.0)
        assertEquals(2.0, state.zoom)
    }

    @Test
    fun panByChangesCenter() {
        val state = Map(center = Point(0.0, 0.0), zoom = 1.0, viewport = Viewport(100, 100))
        state.panBy(10.0, 0.0)
        // Expect center.x to change (simple sanity check)
        assertNotEquals(state.center.x, 0.0)
    }

    @Test
    fun zoomByWithFocusKeepsWorldPointUnderFocus() {
        val state = Map(center = Point(0.0, 0.0), zoom = 1.0, viewport = Viewport(200, 200))
        val focusScreen = Point(50.0, 50.0)
        val worldBefore = state.screenToWorld(focusScreen)
        state.zoomBy(1.0, focusScreen)
        val worldAfter = state.screenToWorld(focusScreen)
        assertEquals(worldBefore, worldAfter)
    }

    @Test
    fun transformSourceToTargetUsesConfiguredRegistry() {
        val state = Map(projection = Epsg3857Projection)

        val projected = state.transformSourceToTarget(
            point = Point(16.6068, 49.1951),
            source = Epsg4326Projection,
            target = Epsg3857Projection
        )

        assertTrue(projected.x > 1_000_000.0)
        assertTrue(projected.y > 1_000_000.0)
    }

    @Test
    fun webMercatorWorldToScreenAndBackRemainInverses() {
        val center = Wgs84ToWebMercatorTransformation.sourceToTarget(Point(16.6068, 49.1951))
        val world = Wgs84ToWebMercatorTransformation.sourceToTarget(Point(16.7068, 49.2451))
        val state = Map(
            center = center,
            zoom = 11.5,
            projection = Epsg3857Projection,
            viewport = Viewport(1200, 800, pixelRatio = 2.0)
        )

        val screen = state.worldToScreen(world)
        val roundTrip = state.screenToWorld(screen)

        assertTrue(abs(world.x - roundTrip.x) < 1e-6, "x differs: $world vs $roundTrip")
        assertTrue(abs(world.y - roundTrip.y) < 1e-6, "y differs: $world vs $roundTrip")
    }
}
