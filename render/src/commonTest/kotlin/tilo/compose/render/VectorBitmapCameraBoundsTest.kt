package tilo.compose.render

import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapConfig
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VectorBitmapCameraBoundsTest {
    @Test
    fun paddedOffscreenViewportKeepsSnapshotCenterNearCameraBounds() {
        val map =
            MapState(
                center = Point(50.0, 0.0),
                config =
                    MapConfig(
                        cameraBounds =
                            BoundingBox.fromExtents(
                                minX = -100.0,
                                maxX = 100.0,
                                minY = -100.0,
                                maxY = 100.0,
                            ),
                    ),
                viewport = Viewport(width = 100, height = 100),
            )

        val offscreenMap = map.forOffscreenViewport(width = 356, height = 356, bitmapScale = 1.0)

        assertEquals(map.center, offscreenMap.center)
        assertNull(offscreenMap.config.cameraBounds)
    }
}
