@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapConfig
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapCameraStateFitBoundsTest {
    /**
     * Verifies that center-only changes do not invalidate zoom-only consumers.
     *
     * Input: one center update followed by one zoom update.
     * Expected: `zoomRevision` changes only for the zoom update.
     */
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
    fun rotationControlsPublishNormalizedBearingAndCameraRevision() {
        val cameraState = MapCameraState(MapState())

        cameraState.rotateBy(30.0)
        cameraState.setBearing(-10.0)

        assertEquals(350.0, cameraState.bearing)
        assertEquals(2, cameraState.cameraControlRevision)
        assertEquals(2, cameraState.revision)
        assertEquals(0, cameraState.zoomRevision)
    }

    @Test
    fun animatedBearingUsesShortestPathAndKeepsFocusFixed() =
        runTest {
            val map =
                MapState(
                    bearing = 350.0,
                    viewport = Viewport(width = 300, height = 200),
                )
            val cameraState = MapCameraState(map)
            val focus = Point(240.0, 35.0)
            val worldBefore = map.screenToWorld(focus)

            withContext(AdvancingFrameClock()) {
                cameraState.animateBearingTo(
                    bearing = 10.0,
                    focus = focus,
                    animationSpec = tween(durationMillis = 64, easing = LinearEasing),
                )
            }

            assertEquals(10.0, cameraState.bearing, absoluteTolerance = 1e-6)
            assertEquals(worldBefore.x, map.screenToWorld(focus).x, absoluteTolerance = 1e-9)
            assertEquals(worldBefore.y, map.screenToWorld(focus).y, absoluteTolerance = 1e-9)
        }

    /**
     * Verifies safe default padding for a viewport smaller than twice the requested padding.
     *
     * Input: an `80 x 60` viewport and bounds fitted with the default padding.
     * Expected: fitted bounds remain completely inside the measured viewport.
     */
    @Test
    fun fitBoundsCapsDefaultPaddingForSmallViewport() {
        val map =
            MapState(
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

    @Test
    fun fitBoundsPublishesBearingResetEvenWhenCenterAndZoomStayUnchanged() {
        val map =
            MapState(
                center = Point(0.0, 0.0),
                zoom = 2.0,
                bearing = 90.0,
                viewport = Viewport(width = 100, height = 100),
            )
        val cameraState = MapCameraState(map)
        val bounds = BoundingBox.fromExtents(-12.5, 12.5, -12.5, 12.5)

        cameraState.fitBounds(bounds, padding = 0.dp)

        assertEquals(0.0, cameraState.bearing)
        assertEquals(1, cameraState.cameraControlRevision)
    }

    /**
     * Verifies visible and padded snapshot geometry for a measured camera.
     *
     * Input: a `200 x 100` viewport at center `(100, 200)` and padding fraction `0.25`.
     * Expected: exact visible bounds, resolution, and bounds expanded by 25 percent per side.
     */
    @Test
    fun viewportSnapshotReportsVisibleAndPaddedBounds() {
        val map =
            MapState(
                center = Point(100.0, 200.0),
                zoom = 0.0,
                viewport = Viewport(width = 200, height = 100),
            )
        val cameraState = MapCameraState(map)

        val visible = cameraState.viewportSnapshot()
        val padded = cameraState.viewportSnapshot(paddingFraction = 0.25)

        assertEquals(BoundingBox.fromExtents(0.0, 200.0, 150.0, 250.0), visible.bounds)
        assertEquals(1.0, visible.resolution)
        assertEquals(BoundingBox.fromExtents(-50.0, 250.0, 125.0, 275.0), padded.bounds)
    }

    @Test
    fun viewportSnapshotReportsRotatedEnvelopeBearingAndScaleResolution() {
        val cameraState =
            MapCameraState(
                MapState(
                    bearing = 45.0,
                    viewport = Viewport(width = 100, height = 100, pixelRatio = 2.0),
                ),
            )

        val snapshot = cameraState.viewportSnapshot()

        assertEquals(45.0, snapshot.bearing)
        assertEquals(-35.3553390593, snapshot.bounds.minX, absoluteTolerance = 1e-9)
        assertEquals(35.3553390593, snapshot.bounds.maxX, absoluteTolerance = 1e-9)
        assertEquals(0.5, snapshot.resolution, absoluteTolerance = 1e-12)
    }

    /**
     * Verifies the viewport lifecycle before and after the first layout measurement.
     *
     * Input: a default map state, followed by a measured `320 x 240` viewport.
     * Expected: the initial snapshot is not ready and the next snapshot reports the measured size as ready.
     */
    @Test
    fun viewportSnapshotBecomesReadyOnlyAfterMeasuredViewportArrives() {
        val map = MapState()
        val cameraState = MapCameraState(map)

        val initial = cameraState.viewportSnapshot()
        assertFalse(initial.isReady)
        assertEquals(0, initial.viewportWidth)
        assertEquals(0, initial.viewportHeight)

        map.viewport = Viewport(width = 320, height = 240, pixelRatio = 2.0)
        cameraState.markChanged()
        val measured = cameraState.viewportSnapshot()

        assertTrue(measured.isReady)
        assertEquals(320, measured.viewportWidth)
        assertEquals(240, measured.viewportHeight)
    }

    private class AdvancingFrameClock : MonotonicFrameClock {
        private var frameTimeNanos = 0L

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            frameTimeNanos += 16_000_000L
            return onFrame(frameTimeNanos)
        }
    }
}
