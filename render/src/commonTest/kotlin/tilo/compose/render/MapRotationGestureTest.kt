package tilo.compose.render

import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import kotlin.test.Test
import kotlin.test.assertEquals

class MapRotationGestureTest {
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
