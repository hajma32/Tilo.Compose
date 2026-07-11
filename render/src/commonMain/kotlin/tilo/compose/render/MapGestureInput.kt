package tilo.compose.render

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
internal fun Modifier.mapGestureInput(
    map: Map,
    onChanged: () -> Unit
): Modifier = pointerInput(map) {
    coroutineScope {
        var panFlingJob: Job? = null
        awaitEachGesture {
            var accumulatedZoom = 1f
            var accumulatedRotation = 0f
            var accumulatedPan = Offset.Zero
            var pastTouchSlop = false
            var cleanSinglePointerPan = true
            val down = awaitFirstDown(requireUnconsumed = false)
            panFlingJob?.cancel()
            val downTimeMillis = down.uptimeMillis
            var lastEventTimeMillis = down.uptimeMillis
            var touchSlopReachedAtMillis = down.uptimeMillis
            var panVelocity = Offset.Zero

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
                            map.zoomBy(
                                delta = ln(zoomChange.toDouble()) / ln(2.0),
                                focus = Point(centroid.x.toDouble(), centroid.y.toDouble())
                            )
                        }
                        if (panChange != Offset.Zero || zoomChange != 1f) {
                            onChanged()
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
                        onChanged = onChanged,
                    )
                }
            }
        }
    }
}

internal fun Modifier.mapTapInput(
    map: Map,
    onTap: ((screenPoint: Point, worldPoint: Point) -> Unit)?,
    onChanged: () -> Unit,
): Modifier {
    if (onTap == null) return this
    return pointerInput(map, onTap) {
        detectTapGestures { offset ->
            val screenPoint = Point(offset.x.toDouble(), offset.y.toDouble())
            onTap(screenPoint, map.screenToWorld(screenPoint))
            onChanged()
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
