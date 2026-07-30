package tilo.compose.render

import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapRotationGestureTest {
    @Test
    fun rotationThresholdSuppressesMovementAndAppliesOnlyExcessOnActivation() {
        val threshold = RotationGestureThreshold(thresholdDegrees = 8.0)

        assertEquals(0.0, threshold.consume(2.0))
        assertEquals(0.0, threshold.consume(2.0))
        assertEquals(0.0, threshold.consume(3.0))
        assertFalse(threshold.isActivated)
        assertEquals(2.0, threshold.consume(3.0))
        assertTrue(threshold.isActivated)
        assertEquals(5.0, threshold.consume(5.0))
    }

    @Test
    fun rotationThresholdSupportsNegativeAndImmediateRotation() {
        val threshold = RotationGestureThreshold(thresholdDegrees = 8.0)
        val immediate = RotationGestureThreshold(thresholdDegrees = 0.0)

        assertEquals(0.0, threshold.consume(-6.0))
        assertEquals(-1.0, threshold.consume(-3.0))
        assertEquals(-4.0, threshold.consume(-4.0))
        assertEquals(0.0, immediate.consume(0.0))
        assertFalse(immediate.isActivated)
        assertEquals(3.0, immediate.consume(3.0))
        assertTrue(immediate.isActivated)
    }

    @Test
    fun clockwiseGestureRotatesMapContentClockwiseAndKeepsCentroidFixed() {
        val map = MapState(viewport = Viewport(width = 300, height = 200))
        val focus = Point(230.0, 40.0)
        val worldBefore = map.screenToWorld(focus)

        applyRotationGesture(map = map, rotationChange = 30.0, focus = focus)

        assertEquals(330.0, map.bearing)
        assertEquals(worldBefore.x, map.screenToWorld(focus).x, absoluteTolerance = 1e-10)
        assertEquals(worldBefore.y, map.screenToWorld(focus).y, absoluteTolerance = 1e-10)
    }
}
