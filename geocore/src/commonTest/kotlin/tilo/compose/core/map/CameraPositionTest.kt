package tilo.compose.core.map

import tilo.compose.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CameraPositionTest {
    @Test
    fun setCameraPublishesOneEngineRevisionAndReturnsResolvedPosition() {
        val map = MapState(config = MapConfig(minZoom = 2.0, maxZoom = 10.0))
        val initialRevision = map.cameraRevision

        val resolved = map.setCamera(CameraPosition(Point(12.0, 34.0), zoom = 20.0, bearing = -15.0))

        assertEquals(CameraPosition(Point(12.0, 34.0), zoom = 10.0, bearing = 345.0), resolved)
        assertEquals(initialRevision + 1, map.cameraRevision)
    }

    @Test
    fun noOpCameraUpdateDoesNotAdvanceEngineRevision() {
        val map = MapState(center = Point(1.0, 2.0), zoom = 3.0, bearing = 4.0)
        val initialRevision = map.cameraRevision

        map.setCamera(map.cameraPosition)

        assertEquals(initialRevision, map.cameraRevision)
    }

    @Test
    fun cameraAndScreenValuesRejectNonFiniteComponents() {
        assertFailsWith<IllegalArgumentException> {
            CameraPosition(Point(Double.NaN, 0.0), zoom = 1.0)
        }
        assertFailsWith<IllegalArgumentException> {
            CameraPosition(Point(0.0, 0.0), zoom = Double.POSITIVE_INFINITY)
        }
        assertFailsWith<IllegalArgumentException> {
            CameraPosition(Point(0.0, 0.0), zoom = 1.0, bearing = Double.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            ScreenPoint(Double.NEGATIVE_INFINITY, 0.0)
        }
    }
}
