@file:OptIn(tilo.compose.dsl.ExperimentalTiloApi::class)

package tilo.compose.render

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapState
import tilo.compose.core.map.ScreenPoint
import tilo.compose.dsl.MapGestureConfig
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Handles pan and pinch-to-zoom gestures on the map.
 * Calls [onChanged] after each applied transform update so the caller can trigger recomposition.
 */
@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod") // Pointer gesture arbitration is intentionally one state machine.
internal fun Modifier.mapGestureInput(
    map: MapState,
    gestureConfig: MapGestureConfig,
    onInteractionStarted: (() -> Unit)? = null,
    onChanged: () -> Unit,
    coordinator: MapGestureCoordinator,
): Modifier {
    val currentGestureConfig = rememberUpdatedState(gestureConfig)
    val currentOnInteractionStarted = rememberUpdatedState(onInteractionStarted)
    val currentOnChanged = rememberUpdatedState(onChanged)
    return pointerInput(map) {
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val rotationThreshold = RotationGestureThreshold(currentGestureConfig.value.rotationThresholdDegrees)
                var accumulatedZoom = 1f
                var accumulatedPan = Offset.Zero
                var pastTouchSlop = false
                var cleanSinglePointerPan = true
                coordinator.cancelAnimations()
                currentOnInteractionStarted.value?.invoke()
                val downTimeMillis = down.uptimeMillis
                var lastEventTimeMillis = down.uptimeMillis
                var touchSlopReachedAtMillis = down.uptimeMillis
                var panVelocity = Offset.Zero
                var zoomVelocity = 0.0
                var zoomFocus: ScreenPoint? = null
                var hadZoomGesture = false

                do {
                    val event = awaitPointerEvent()
                    val previousEventTimeMillis = lastEventTimeMillis
                    lastEventTimeMillis = event.changes.maxOf { it.uptimeMillis }
                    val canceled = event.changes.any { it.isConsumed }
                    if (!canceled) {
                        val zoomChange = event.calculateZoom()
                        val rotationChange = event.calculateRotation()
                        val rotationToApply = rotationThreshold.consume(rotationChange.toDouble())
                        val panChange = event.calculatePan()
                        val pressedCount = event.pressedCount()
                        if (pressedCount > 0) {
                            cleanSinglePointerPan = cleanSinglePointerPan &&
                                pressedCount == 1 &&
                                zoomChange == 1f &&
                                rotationChange == 0f
                        }

                        if (!pastTouchSlop) {
                            accumulatedZoom *= zoomChange
                            accumulatedPan += panChange

                            val centroidSize = event.calculateCentroidSize(useCurrent = false)
                            val zoomMotion = abs(1 - accumulatedZoom) * centroidSize
                            val panMotion = accumulatedPan.getDistance()
                            pastTouchSlop = zoomMotion > viewConfiguration.touchSlop ||
                                rotationThreshold.isActivated ||
                                panMotion > viewConfiguration.touchSlop
                            if (pastTouchSlop) {
                                touchSlopReachedAtMillis = lastEventTimeMillis
                            }
                        }

                        if (pastTouchSlop) {
                            val centroid = event.calculateCentroid(useCurrent = false)
                            if (panChange != Offset.Zero) {
                                val mapPan = Offset(-panChange.x, -panChange.y)
                                map.panBy(mapPan.x.toDouble(), mapPan.y.toDouble())
                                val deltaSeconds =
                                    ((lastEventTimeMillis - previousEventTimeMillis).coerceAtLeast(1L))
                                        .toFloat() / 1_000f
                                val instantVelocity = mapPan / deltaSeconds
                                panVelocity = panVelocity * 0.25f + instantVelocity * 0.75f
                            }
                            if (zoomChange != 1f) {
                                val zoomDelta = ln(zoomChange.toDouble()) / ln(2.0)
                                val deltaSeconds =
                                    ((lastEventTimeMillis - previousEventTimeMillis).coerceAtLeast(1L))
                                        .toDouble() / 1_000.0
                                val instantZoomVelocity = zoomDelta / deltaSeconds
                                zoomVelocity = zoomVelocity * 0.25 + instantZoomVelocity * 0.75
                                zoomFocus = ScreenPoint(centroid.x.toDouble(), centroid.y.toDouble())
                                hadZoomGesture = true
                                map.zoomBy(
                                    delta = zoomDelta,
                                    focus = zoomFocus,
                                )
                            }
                            if (rotationToApply != 0.0) {
                                applyRotationGesture(
                                    map = map,
                                    rotationChange = rotationToApply,
                                    focus = ScreenPoint(centroid.x.toDouble(), centroid.y.toDouble()),
                                )
                            }
                            if (panChange != Offset.Zero || zoomChange != 1f || rotationToApply != 0.0) {
                                currentOnChanged.value()
                            }
                            event.changes.forEach { change ->
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        }
                    }
                } while (!canceled && event.changes.any { it.pressed })

                val startedAsLongPress =
                    touchSlopReachedAtMillis - downTimeMillis >= viewConfiguration.longPressTimeoutMillis
                if (pastTouchSlop && cleanSinglePointerPan && !startedAsLongPress) {
                    coordinator.panFlingJob =
                        launch {
                            animateInertialPan(
                                initialVelocity = panVelocity.clampDistance(MAX_PAN_VELOCITY),
                                map = map,
                                onChanged = { currentOnChanged.value() },
                            )
                        }
                }
                if (pastTouchSlop && hadZoomGesture && !startedAsLongPress) {
                    coordinator.zoomFlingJob =
                        launch {
                            animateInertialZoom(
                                initialVelocity = zoomVelocity.coerceIn(-MAX_ZOOM_VELOCITY, MAX_ZOOM_VELOCITY),
                                focus = zoomFocus,
                                map = map,
                                onChanged = { currentOnChanged.value() },
                            )
                        }
                }
            }
        }
    }
}

/** Adds angular hysteresis without introducing a bearing jump when rotation activates. */
internal class RotationGestureThreshold(
    private val thresholdDegrees: Double,
) {
    private var accumulatedRotation = 0.0

    var isActivated: Boolean = false
        private set

    fun consume(rotationChange: Double): Double {
        if (isActivated) return rotationChange

        accumulatedRotation += rotationChange
        val excess = abs(accumulatedRotation) - thresholdDegrees
        if (excess <= 0.0) return 0.0

        isActivated = true
        return if (accumulatedRotation < 0.0) -excess else excess
    }
}

/** Applies Compose's clockwise-positive gesture rotation to map bearing. */
internal fun applyRotationGesture(
    map: MapState,
    rotationChange: Double,
    focus: ScreenPoint,
) {
    map.rotateBy(delta = -rotationChange, focus = focus)
}

@Composable
internal fun Modifier.mapTapInput(
    map: MapState,
    onTap: ((screenPoint: ScreenPoint, worldPoint: Point) -> Unit)?,
    onInteractionStarted: (() -> Unit)? = null,
    onChanged: () -> Unit,
    coordinator: MapGestureCoordinator,
): Modifier {
    val currentMap = rememberUpdatedState(map)
    val currentOnTap = rememberUpdatedState(onTap)
    val currentOnInteractionStarted = rememberUpdatedState(onInteractionStarted)
    val currentOnChanged = rememberUpdatedState(onChanged)
    return pointerInput(Unit) {
        coroutineScope {
            detectTapGestures(
                onPress = {
                    coordinator.cancelAnimations()
                    currentOnInteractionStarted.value?.invoke()
                },
                onDoubleTap = { offset ->
                    coordinator.doubleTapZoomJob =
                        launch {
                            animateDoubleTapZoom(
                                focus = ScreenPoint(offset.x.toDouble(), offset.y.toDouble()),
                                map = currentMap.value,
                                onChanged = { currentOnChanged.value() },
                            )
                        }
                },
                onTap = { offset ->
                    val screenPoint = ScreenPoint(offset.x.toDouble(), offset.y.toDouble())
                    val onTap = currentOnTap.value
                    if (onTap != null) {
                        val map = currentMap.value
                        onTap(screenPoint, map.screenToWorld(screenPoint))
                        currentOnChanged.value()
                    }
                },
            )
        }
    }
}

/** Coordinates animations owned by the map's independent transform and tap recognizers. */
internal class MapGestureCoordinator {
    var panFlingJob: Job? = null
    var zoomFlingJob: Job? = null
    var doubleTapZoomJob: Job? = null

    fun cancelAnimations() {
        panFlingJob?.cancel()
        zoomFlingJob?.cancel()
        doubleTapZoomJob?.cancel()
        panFlingJob = null
        zoomFlingJob = null
        doubleTapZoomJob = null
    }
}

/** Owns gesture animation jobs for one composed map and cancels them when its map is replaced or disposed. */
@Composable
internal fun rememberMapGestureCoordinator(map: MapState): MapGestureCoordinator {
    val coordinator = remember { MapGestureCoordinator() }
    DisposableEffect(map, coordinator) {
        onDispose(coordinator::cancelAnimations)
    }
    return coordinator
}

private const val MAX_PAN_VELOCITY = 9_500f

private const val MINIMUM_PAN_FLING_VELOCITY = 350.0f
private const val VELOCITY_BOOST_START = 700f
private const val VELOCITY_BOOST_END = 6_000f
private const val MAXIMUM_VELOCITY_BOOST = 1.25f
private const val PAN_FLING_FRICTION = 3.6f
private const val MAXIMUM_PAN_FLING_DURATION_NANOS = 1_200_000_000L
private const val MAXIMUM_FRAME_SECONDS = 0.05f
private const val MINIMUM_ZOOM_FLING_VELOCITY = 0.25
private const val MAX_ZOOM_VELOCITY = 4.5
private const val ZOOM_VELOCITY_BOOST_START = 0.8
private const val ZOOM_VELOCITY_BOOST_END = 3.5
private const val MAXIMUM_ZOOM_VELOCITY_BOOST = 1.3
private const val ZOOM_FLING_FRICTION = 5.4
private const val MAXIMUM_ZOOM_FLING_DURATION_NANOS = 700_000_000L
private const val DOUBLE_TAP_ZOOM_DELTA = 2.0
private const val DOUBLE_TAP_ZOOM_DURATION_NANOS = 420_000_000L

private suspend fun animateInertialPan(
    initialVelocity: Offset,
    map: MapState,
    onChanged: () -> Unit,
) {
    var velocity = initialVelocity * initialVelocity.boostMultiplier()
    if (velocity.getDistance() < MINIMUM_PAN_FLING_VELOCITY) return

    var expectedRevision = map.cameraRevision
    var previousFrame = withFrameNanos { it }
    val startedAt = previousFrame
    while (velocity.getDistance() >= MINIMUM_PAN_FLING_VELOCITY) {
        val frame = withFrameNanos { it }
        if (frame - startedAt > MAXIMUM_PAN_FLING_DURATION_NANOS) break

        val deltaSeconds =
            ((frame - previousFrame).toFloat() / 1_000_000_000f)
                .coerceIn(0f, MAXIMUM_FRAME_SECONDS)
        previousFrame = frame
        if (map.cameraRevision != expectedRevision) return

        val pan = velocity * deltaSeconds
        map.panBy(pan.x.toDouble(), pan.y.toDouble())
        expectedRevision = map.cameraRevision
        onChanged()
        velocity *= exp(-PAN_FLING_FRICTION * deltaSeconds)
    }
}

private suspend fun animateDoubleTapZoom(
    focus: ScreenPoint,
    map: MapState,
    onChanged: () -> Unit,
) {
    val targetZoom = (map.zoom + DOUBLE_TAP_ZOOM_DELTA).coerceIn(map.config.minZoom, map.config.maxZoom)
    if (targetZoom == map.zoom) return

    val startZoom = map.zoom
    var expectedRevision = map.cameraRevision
    var previousProgress = 0.0
    val startedAt = withFrameNanos { it }

    while (true) {
        val frame = withFrameNanos { it }
        val rawProgress =
            ((frame - startedAt).toDouble() / DOUBLE_TAP_ZOOM_DURATION_NANOS)
                .coerceIn(0.0, 1.0)
        val progress = rawProgress.easeInOutCubic()
        if (map.cameraRevision != expectedRevision) return
        map.zoomBy(delta = (targetZoom - startZoom) * (progress - previousProgress), focus = focus)
        expectedRevision = map.cameraRevision
        onChanged()
        previousProgress = progress
        if (rawProgress >= 1.0) break
    }
}

private suspend fun animateInertialZoom(
    initialVelocity: Double,
    focus: ScreenPoint?,
    map: MapState,
    onChanged: () -> Unit,
) {
    var velocity = initialVelocity * initialVelocity.zoomBoostMultiplier()
    if (abs(velocity) < MINIMUM_ZOOM_FLING_VELOCITY) return

    var expectedRevision = map.cameraRevision
    var previousFrame = withFrameNanos { it }
    val startedAt = previousFrame
    while (abs(velocity) >= MINIMUM_ZOOM_FLING_VELOCITY) {
        val frame = withFrameNanos { it }
        if (frame - startedAt > MAXIMUM_ZOOM_FLING_DURATION_NANOS) return

        val deltaSeconds =
            ((frame - previousFrame).toDouble() / 1_000_000_000.0)
                .coerceIn(0.0, MAXIMUM_FRAME_SECONDS.toDouble())
        previousFrame = frame
        if (map.cameraRevision != expectedRevision) return

        val previousZoom = map.zoom
        map.zoomBy(delta = velocity * deltaSeconds, focus = focus)
        expectedRevision = map.cameraRevision
        if (map.zoom == previousZoom) return
        onChanged()

        velocity *= exp(-ZOOM_FLING_FRICTION * deltaSeconds)
    }
}

private fun Double.easeInOutCubic(): Double =
    if (this < 0.5) {
        4.0 * this * this * this
    } else {
        val shifted = -2.0 * this + 2.0
        1.0 - shifted * shifted * shifted / 2.0
    }

private fun Double.zoomBoostMultiplier(): Double {
    val speed = abs(this)
    val intensity =
        ((speed - ZOOM_VELOCITY_BOOST_START) / (ZOOM_VELOCITY_BOOST_END - ZOOM_VELOCITY_BOOST_START))
            .coerceIn(0.0, 1.0)
    return 1.0 + (MAXIMUM_ZOOM_VELOCITY_BOOST - 1.0) * intensity * intensity
}

private fun PointerEvent.pressedCount(): Int = changes.count { it.pressed }

private fun Offset.boostMultiplier(): Float {
    val intensity = velocityIntensity()
    return 1f + (MAXIMUM_VELOCITY_BOOST - 1f) * intensity * intensity
}

private fun Offset.velocityIntensity(): Float {
    val speed = getDistance()
    return ((speed - VELOCITY_BOOST_START) / (VELOCITY_BOOST_END - VELOCITY_BOOST_START)).coerceIn(0f, 1f)
}

private fun Offset.clampDistance(maxDistance: Float): Offset {
    val distance = getDistance()
    if (distance <= maxDistance || distance == 0f) return this
    return this * (maxDistance / distance)
}
