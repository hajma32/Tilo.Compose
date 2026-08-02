@file:OptIn(ExperimentalTiloApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package tilo.compose.dsl

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.CameraPosition
import tilo.compose.core.map.MapConfig
import tilo.compose.core.map.MapState
import tilo.compose.core.map.ScreenPoint
import tilo.compose.core.map.Viewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapCameraStateFitBoundsTest {
    @Test
    fun centerAndPositionIndependentlyEstablishSnapshotDependencies() {
        val cameraState = MapCameraState(MapState())
        val centerInvalidations = observeInvalidations(read = { cameraState.center })
        val positionInvalidations = observeInvalidations(read = { cameraState.position })

        cameraState.setCenter(Point(10.0, 20.0))
        Snapshot.sendApplyNotifications()

        assertEquals(1, centerInvalidations())
        assertEquals(1, positionInvalidations())
        assertEquals(Point(10.0, 20.0), cameraState.center)
    }

    @Test
    fun scalarCameraReadsInvalidateOnlyWhenTheirComponentChanges() {
        val cameraState = MapCameraState(MapState(zoom = 3.0, bearing = 15.0))
        val zoomInvalidations = observeInvalidations(read = { cameraState.zoom })
        val bearingInvalidations = observeInvalidations(read = { cameraState.bearing })

        cameraState.setCenter(Point(10.0, 20.0))
        Snapshot.sendApplyNotifications()

        assertEquals(0, zoomInvalidations())
        assertEquals(0, bearingInvalidations())
    }

    @Test
    fun absoluteCameraOperationsPublishOneImmutablePosition() {
        val cameraState =
            MapCameraState(
                MapState(config = MapConfig(minZoom = 2.0, maxZoom = 10.0)),
            )

        val positionInvalidations = observeInvalidations(read = { cameraState.position })
        cameraState.setCamera(CameraPosition(Point(12.0, 34.0), zoom = 20.0, bearing = -15.0))
        Snapshot.sendApplyNotifications()

        assertEquals(
            CameraPosition(center = Point(12.0, 34.0), zoom = 10.0, bearing = 345.0),
            cameraState.position,
        )
        assertEquals(1, positionInvalidations())
        assertEquals(1, cameraState.cameraControlRevision)

        cameraState.setZoom(4.0)
        cameraState.setCenter(Point(56.0, 78.0))
        assertEquals(CameraPosition(Point(56.0, 78.0), 4.0, 345.0), cameraState.position)
    }

    @Test
    fun shortestBearingDeltaCrossesNorthInsteadOfRotatingTheLongWay() {
        assertEquals(20.0, shortestBearingDelta(from = 350.0, to = 10.0))
        assertEquals(-20.0, shortestBearingDelta(from = 10.0, to = 350.0))
    }

    @Test
    fun animateToUsesTheShortestBearingPath() =
        runTest {
            val cameraState = MapCameraState(MapState(bearing = 350.0))
            val frameClock = ManualFrameClock()
            val animation =
                launch(frameClock) {
                    cameraState.animateTo(
                        CameraPosition(Point(0.0, 0.0), zoom = 0.0, bearing = 10.0),
                        animationSpec = tween(durationMillis = 1_000, easing = LinearEasing),
                    )
                }
            runCurrent()
            frameClock.advanceTo(0L)
            runCurrent()
            frameClock.advanceTo(250_000_000L)
            runCurrent()

            assertEquals(355.0, cameraState.bearing, absoluteTolerance = 1e-6)

            frameClock.advanceTo(1_000_000_000L)
            runCurrent()
            assertTrue(animation.isCompleted)
            assertEquals(10.0, cameraState.bearing, absoluteTolerance = 1e-6)
        }

    @Test
    fun immediateCameraMutationCancelsRunningAnimation() =
        runTest {
            val cameraState = MapCameraState(MapState())
            val frameClock = ManualFrameClock()
            val animation =
                launch(frameClock) {
                    cameraState.animateTo(
                        CameraPosition(Point(100.0, 100.0), zoom = 5.0),
                        animationSpec = tween(durationMillis = 1_000, easing = LinearEasing),
                    )
                }
            runCurrent()
            frameClock.advanceTo(0L)
            runCurrent()
            frameClock.advanceTo(16_000_000L)
            runCurrent()
            assertTrue(animation.isActive)

            cameraState.setCenter(Point(25.0, 30.0))
            runCurrent()

            assertTrue(animation.isCancelled)
            assertEquals(Point(25.0, 30.0), cameraState.center)
        }

    @Test
    fun newerCameraAnimationCancelsThePreviousAnimation() =
        runTest {
            val cameraState = MapCameraState(MapState())
            val firstFrameClock = ManualFrameClock()
            val firstAnimation =
                launch(firstFrameClock) {
                    cameraState.animateTo(
                        CameraPosition(Point(100.0, 100.0), zoom = 5.0),
                        animationSpec = tween(durationMillis = 1_000, easing = LinearEasing),
                    )
                }
            runCurrent()
            firstFrameClock.advanceTo(0L)
            runCurrent()
            firstFrameClock.advanceTo(16_000_000L)
            runCurrent()

            withContext(AdvancingFrameClock()) {
                cameraState.animateTo(
                    CameraPosition(Point(20.0, 30.0), zoom = 2.0, bearing = 45.0),
                    animationSpec = tween(durationMillis = 64, easing = LinearEasing),
                )
            }
            runCurrent()

            assertTrue(firstAnimation.isCancelled)
            assertEquals(CameraPosition(Point(20.0, 30.0), 2.0, 45.0), cameraState.position)
        }

    @Test
    fun animateToReachesCameraPosition() =
        runTest {
            val cameraState =
                MapCameraState(
                    MapState(center = Point(0.0, 0.0), zoom = 2.0, bearing = 350.0),
                )

            withContext(AdvancingFrameClock()) {
                cameraState.animateTo(
                    position = CameraPosition(Point(100.0, 50.0), zoom = 6.0, bearing = 10.0),
                    animationSpec = tween(durationMillis = 64, easing = LinearEasing),
                )
            }

            assertEquals(Point(100.0, 50.0), cameraState.center)
            assertEquals(6.0, cameraState.zoom, absoluteTolerance = 1e-6)
            assertEquals(10.0, cameraState.bearing, absoluteTolerance = 1e-6)
        }

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
            val focus = ScreenPoint(240.0, 35.0)
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

    @Test
    fun animatedZoomByReachesTargetAndHonorsCameraBounds() =
        runTest {
            val cameraState =
                MapCameraState(
                    MapState(
                        zoom = 3.0,
                        config = MapConfig(minZoom = 2.0, maxZoom = 4.0),
                    ),
                )

            withContext(AdvancingFrameClock()) {
                cameraState.animateZoomBy(
                    delta = 5.0,
                    animationSpec = tween(durationMillis = 64, easing = LinearEasing),
                )
            }

            assertEquals(4.0, cameraState.zoom, absoluteTolerance = 1e-6)
            assertTrue(cameraState.zoomRevision > 0)
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

    private class ManualFrameClock : MonotonicFrameClock {
        private val frames = Channel<Long>(capacity = Channel.UNLIMITED)

        fun advanceTo(frameTimeNanos: Long) {
            frames.trySend(frameTimeNanos).getOrThrow()
        }

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = onFrame(frames.receive())
    }

    private fun observeInvalidations(read: () -> Unit): () -> Int {
        var invalidations = 0
        val observer = SnapshotStateObserver { command -> command() }
        observer.start()
        observer.observeReads(
            scope = observer,
            onValueChangedForScope = { invalidations += 1 },
            block = read,
        )
        return {
            observer.stop()
            invalidations
        }
    }
}
