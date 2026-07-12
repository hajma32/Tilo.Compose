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
import tilo.compose.core.map.Map
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Handles pan and pinch-to-zoom gestures on the map.
 * Calls [onChanged] after each gesture so the caller can trigger recomposition.
 */
@Composable
internal fun Modifier.mapGestureInput(
    map: Map,
    onChanged: () -> Unit
): Modifier {
    val currentMap = rememberUpdatedState(map)
    val currentOnChanged = rememberUpdatedState(onChanged)
    return pointerInput(Unit) {
    coroutineScope {
        var panFlingJob: Job? = null
        var zoomFlingJob: Job? = null
        awaitEachGesture {
            val map = currentMap.value
            var accumulatedZoom = 1f
            var accumulatedRotation = 0f
            var accumulatedPan = Offset.Zero
            var pastTouchSlop = false
            var cleanSinglePointerPan = true
            val down = awaitFirstDown(requireUnconsumed = false)
            panFlingJob?.cancel()
            zoomFlingJob?.cancel()
            val downTimeMillis = down.uptimeMillis
            var lastEventTimeMillis = down.uptimeMillis
            var touchSlopReachedAtMillis = down.uptimeMillis
            var panVelocity = Offset.Zero
            var zoomVelocity = 0.0
            var zoomFocus: Point? = null
            var hadZoomGesture = false

            do {
                val event = awaitPointerEvent()
                val previousEventTimeMillis = lastEventTimeMillis
                lastEventTimeMillis = event.changes.maxOf { it.uptimeMillis }
                val canceled = event.changes.any { it.isConsumed }
                if (!canceled) {
                    val zoomChange = event.calculateZoom()
                    val rotationChange = event.calculateRotation()
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
                        accumulatedRotation += rotationChange
                        accumulatedPan += panChange

                        val centroidSize = event.calculateCentroidSize(useCurrent = false)
                        val zoomMotion = abs(1 - accumulatedZoom) * centroidSize
                        val rotationMotion = abs(accumulatedRotation * PI.toFloat() * centroidSize / 180f)
                        val panMotion = accumulatedPan.getDistance()
                        pastTouchSlop = zoomMotion > viewConfiguration.touchSlop ||
                            rotationMotion > viewConfiguration.touchSlop ||
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
                            val deltaSeconds = ((lastEventTimeMillis - previousEventTimeMillis).coerceAtLeast(1L))
                                .toFloat() / 1_000f
                            val instantVelocity = mapPan / deltaSeconds
                            panVelocity = panVelocity * 0.25f + instantVelocity * 0.75f
                        }
                        if (zoomChange != 1f) {
                            val zoomDelta = ln(zoomChange.toDouble()) / ln(2.0)
                            val deltaSeconds = ((lastEventTimeMillis - previousEventTimeMillis).coerceAtLeast(1L))
                                .toDouble() / 1_000.0
                            val instantZoomVelocity = zoomDelta / deltaSeconds
                            zoomVelocity = zoomVelocity * 0.25 + instantZoomVelocity * 0.75
                            zoomFocus = Point(centroid.x.toDouble(), centroid.y.toDouble())
                            hadZoomGesture = true
                            map.zoomBy(
                                delta = zoomDelta,
                                focus = zoomFocus
                            )
                        }
                        if (panChange != Offset.Zero || zoomChange != 1f) {
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

            val startedAsLongPress = touchSlopReachedAtMillis - downTimeMillis >= viewConfiguration.longPressTimeoutMillis
            if (pastTouchSlop && cleanSinglePointerPan && !startedAsLongPress) {
                panFlingJob = launch {
                    animateInertialPan(
                        initialVelocity = panVelocity.clampDistance(MaxPanVelocity),
                        map = map,
                        onChanged = { currentOnChanged.value() },
                    )
                }
            }
            if (pastTouchSlop && hadZoomGesture && !startedAsLongPress) {
                zoomFlingJob = launch {
                    animateInertialZoom(
                        initialVelocity = zoomVelocity.coerceIn(-MaxZoomVelocity, MaxZoomVelocity),
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

@Composable
internal fun Modifier.mapTapInput(
    map: Map,
    onTap: ((screenPoint: Point, worldPoint: Point) -> Unit)?,
    onChanged: () -> Unit,
): Modifier {
    val currentMap = rememberUpdatedState(map)
    val currentOnTap = rememberUpdatedState(onTap)
    val currentOnChanged = rememberUpdatedState(onChanged)
    return pointerInput(Unit) {
        coroutineScope {
            var doubleTapZoomJob: Job? = null
            detectTapGestures(
                onDoubleTap = { offset ->
                    doubleTapZoomJob?.cancel()
                    doubleTapZoomJob = launch {
                        animateDoubleTapZoom(
                            focus = Point(offset.x.toDouble(), offset.y.toDouble()),
                            map = currentMap.value,
                            onChanged = { currentOnChanged.value() },
                        )
                    }
                },
                onTap = { offset ->
                    val screenPoint = Point(offset.x.toDouble(), offset.y.toDouble())
                    val onTap = currentOnTap.value
                    if (onTap != null) {
                        val map = currentMap.value
                        onTap(screenPoint, map.screenToWorld(screenPoint))
                        currentOnChanged.value()
                    }
                }
            )
        }
    }
}

private const val MaxPanVelocity = 9_500f

private const val MinimumPanFlingVelocity = 350.0f
private const val VelocityBoostStart = 700f
private const val VelocityBoostEnd = 6_000f
private const val MaximumVelocityBoost = 1.25f
private const val PanFlingFriction = 3.6f
private const val MaximumPanFlingDurationNanos = 1_200_000_000L
private const val MaximumFrameSeconds = 0.05f
private const val MinimumZoomFlingVelocity = 0.25
private const val MaxZoomVelocity = 4.5
private const val ZoomVelocityBoostStart = 0.8
private const val ZoomVelocityBoostEnd = 3.5
private const val MaximumZoomVelocityBoost = 1.3
private const val ZoomFlingFriction = 5.4
private const val MaximumZoomFlingDurationNanos = 700_000_000L
private const val DoubleTapZoomDelta = 2.0
private const val DoubleTapZoomDurationNanos = 420_000_000L

private suspend fun animateInertialPan(
    initialVelocity: Offset,
    map: Map,
    onChanged: () -> Unit,
) {
    var velocity = initialVelocity * initialVelocity.boostMultiplier()
    if (velocity.getDistance() < MinimumPanFlingVelocity) return

    var previousFrame = withFrameNanos { it }
    val startedAt = previousFrame
    while (velocity.getDistance() >= MinimumPanFlingVelocity) {
        val frame = withFrameNanos { it }
        if (frame - startedAt > MaximumPanFlingDurationNanos) break

        val deltaSeconds = ((frame - previousFrame).toFloat() / 1_000_000_000f)
            .coerceIn(0f, MaximumFrameSeconds)
        previousFrame = frame

        val pan = velocity * deltaSeconds
        map.panBy(pan.x.toDouble(), pan.y.toDouble())
        onChanged()
        velocity *= exp(-PanFlingFriction * deltaSeconds)
    }
}

private suspend fun animateDoubleTapZoom(
    focus: Point,
    map: Map,
    onChanged: () -> Unit,
) {
    val targetZoom = (map.zoom + DoubleTapZoomDelta).coerceIn(map.config.minZoom, map.config.maxZoom)
    if (targetZoom == map.zoom) return

    val startZoom = map.zoom
    var previousProgress = 0.0
    val startedAt = withFrameNanos { it }

    while (true) {
        val frame = withFrameNanos { it }
        val rawProgress = ((frame - startedAt).toDouble() / DoubleTapZoomDurationNanos)
            .coerceIn(0.0, 1.0)
        val progress = rawProgress.easeInOutCubic()
        map.zoomBy(delta = (targetZoom - startZoom) * (progress - previousProgress), focus = focus)
        onChanged()
        previousProgress = progress
        if (rawProgress >= 1.0) break
    }
}

private suspend fun animateInertialZoom(
    initialVelocity: Double,
    focus: Point?,
    map: Map,
    onChanged: () -> Unit,
) {
    var velocity = initialVelocity * initialVelocity.zoomBoostMultiplier()
    if (abs(velocity) < MinimumZoomFlingVelocity) return

    var previousFrame = withFrameNanos { it }
    val startedAt = previousFrame
    while (abs(velocity) >= MinimumZoomFlingVelocity) {
        val frame = withFrameNanos { it }
        if (frame - startedAt > MaximumZoomFlingDurationNanos) break

        val deltaSeconds = ((frame - previousFrame).toDouble() / 1_000_000_000.0)
            .coerceIn(0.0, MaximumFrameSeconds.toDouble())
        previousFrame = frame

        val previousZoom = map.zoom
        map.zoomBy(delta = velocity * deltaSeconds, focus = focus)
        if (map.zoom == previousZoom) break
        onChanged()

        velocity *= exp(-ZoomFlingFriction * deltaSeconds)
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
    val intensity = ((speed - ZoomVelocityBoostStart) / (ZoomVelocityBoostEnd - ZoomVelocityBoostStart))
        .coerceIn(0.0, 1.0)
    return 1.0 + (MaximumZoomVelocityBoost - 1.0) * intensity * intensity
}

private fun PointerEvent.pressedCount(): Int =
    changes.count { it.pressed }

private fun Offset.boostMultiplier(): Float {
    val intensity = velocityIntensity()
    return 1f + (MaximumVelocityBoost - 1f) * intensity * intensity
}

private fun Offset.velocityIntensity(): Float {
    val speed = getDistance()
    return ((speed - VelocityBoostStart) / (VelocityBoostEnd - VelocityBoostStart)).coerceIn(0f, 1f)
}

private fun Offset.clampDistance(maxDistance: Float): Offset {
    val distance = getDistance()
    if (distance <= maxDistance || distance == 0f) return this
    return this * (maxDistance / distance)
}
