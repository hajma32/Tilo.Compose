@file:OptIn(tilo.compose.dsl.ExperimentalTiloApi::class)

package tilo.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import tilo.compose.dsl.MapCameraState
import tilo.compose.dsl.MapDiagnosticsState

/** Frame statistics sampled by [DefaultMapDebugOverlay]. */
data class MapDebugMetrics(
    val framesPerSecond: Double,
    val averageFramesPerSecond30Seconds: Double,
    val averageFrameTimeMillis: Double,
    val maxFrameTimeMillis: Double,
    val skippedFrames: Int,
) {
    companion object {
        val Empty =
            MapDebugMetrics(
                framesPerSecond = 0.0,
                averageFramesPerSecond30Seconds = 0.0,
                averageFrameTimeMillis = 0.0,
                maxFrameTimeMillis = 0.0,
                skippedFrames = 0,
            )
    }
}

/**
 * Displays live map zoom and Compose frame statistics.
 *
 * This overlay continuously requests frame-clock ticks while [enabled], so it
 * should only be enabled in diagnostic builds or user-invoked debug modes.
 * [targetFrameRate] defines the frame budget used for skipped-frame estimates.
 */
@Composable
fun BoxScope.DefaultMapDebugOverlay(
    cameraState: MapCameraState,
    enabled: Boolean = true,
    targetFrameRate: Int = 60,
    sampleWindowMillis: Long = 1_000L,
    alignment: Alignment = Alignment.TopStart,
    modifier: Modifier = Modifier,
) = DefaultMapDebugOverlayImpl(
    cameraState = cameraState,
    diagnosticsState = null,
    enabled = enabled,
    targetFrameRate = targetFrameRate,
    sampleWindowMillis = sampleWindowMillis,
    alignment = alignment,
    modifier = modifier,
)

/** Displays frame statistics and renderer diagnostics published by a `TiloMap`. */
@Composable
fun BoxScope.DefaultMapDebugOverlay(
    cameraState: MapCameraState,
    diagnosticsState: MapDiagnosticsState,
    enabled: Boolean = true,
    targetFrameRate: Int = 60,
    sampleWindowMillis: Long = 1_000L,
    alignment: Alignment = Alignment.TopStart,
    modifier: Modifier = Modifier,
) = DefaultMapDebugOverlayImpl(
    cameraState = cameraState,
    diagnosticsState = diagnosticsState,
    enabled = enabled,
    targetFrameRate = targetFrameRate,
    sampleWindowMillis = sampleWindowMillis,
    alignment = alignment,
    modifier = modifier,
)

@Composable
private fun BoxScope.DefaultMapDebugOverlayImpl(
    cameraState: MapCameraState,
    diagnosticsState: MapDiagnosticsState?,
    enabled: Boolean,
    targetFrameRate: Int,
    sampleWindowMillis: Long,
    alignment: Alignment,
    modifier: Modifier,
) {
    require(targetFrameRate in 1..1_000) { "targetFrameRate must be between 1 and 1000" }
    require(sampleWindowMillis in 1L..60_000L) { "sampleWindowMillis must be between 1 and 60000" }
    if (!enabled) return

    val accumulator =
        remember(targetFrameRate, sampleWindowMillis) {
            FrameMetricsAccumulator(
                targetFrameRate = targetFrameRate,
                sampleWindowNanos = sampleWindowMillis * NANOS_PER_MILLISECOND,
            )
        }
    var metrics by remember(targetFrameRate, sampleWindowMillis) {
        mutableStateOf(MapDebugMetrics.Empty)
    }

    LaunchedEffect(accumulator) {
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                accumulator.recordFrame(frameTimeNanos)?.let { metrics = it }
            }
        }
    }

    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier =
            modifier
                .align(alignment)
                .padding(12.dp)
                .shadow(elevation = 4.dp, shape = shape)
                .background(DEBUG_OVERLAY_BACKGROUND, shape)
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        DebugLine("Zoom", cameraState.zoom.format(2))
        DebugLine("FPS", metrics.framesPerSecond.format(1))
        DebugLine("FPS avg 30s", metrics.averageFramesPerSecond30Seconds.format(1))
        DebugLine("Frame avg", "${metrics.averageFrameTimeMillis.format(1)} ms")
        DebugLine("Frame max", "${metrics.maxFrameTimeMillis.format(1)} ms")
        DebugLine("Skipped", metrics.skippedFrames.toString())
        diagnosticsState?.metrics?.let { renderMetrics ->
            val tiles = renderMetrics.tiles
            val tileCache = tiles.cache
            val features = renderMetrics.features
            val labels = renderMetrics.labels
            DebugLine("Tiles", "${tiles.loaded}/${tiles.planned} loaded, ${tiles.decoded} decoded")
            DebugLine("Displayed", tiles.displayed.toString())
            DebugLine("Missing", tiles.missing.toString())
            DebugLine(
                "Tile cache",
                "${tileCache.entries}/${tileCache.maxEntries}, ${hitRate(tileCache.hits, tileCache.misses)} hit",
            )
            DebugLine(
                "Fetch",
                "${tileCache.sourceFetches} source, ${tileCache.coalescedRequests} shared, ${tileCache.inFlightRequests} active",
            )
            DebugLine("Features", "${features.visible}/${features.returned} visible")
            DebugLine("Geometry", "${features.geometryCommands} commands")
            DebugLine(
                "Bitmap",
                "${features.bitmapLayersReused} reused, ${features.bitmapLayersRebuilt} rebuilt",
            )
            DebugLine("Labels", "${labels.placed}/${labels.candidates} placed")
            DebugLine("Rejected", labels.rejected.toString())
            DebugLine(
                "Label cache",
                "${labels.cacheEntries}/${labels.maxCacheEntries}, ${hitRate(
                    labels.bitmapHits,
                    labels.bitmapMisses,
                )} hit",
            )
        }
    }
}

@Composable
private fun DebugLine(
    label: String,
    value: String,
) {
    BasicText(
        text = "$label: $value",
        style = DEBUG_OVERLAY_TEXT_STYLE,
    )
}

internal class FrameMetricsAccumulator(
    targetFrameRate: Int,
    private val sampleWindowNanos: Long,
) {
    private val frameBudgetNanos = NANOS_PER_SECOND / targetFrameRate
    private var previousFrameNanos: Long? = null
    private var windowStartNanos: Long? = null
    private var frameIntervals = 0
    private var totalFrameTimeNanos = 0L
    private var maxFrameTimeNanos = 0L
    private var skippedFrames = 0
    private val rollingFrameTimes = ArrayDeque<Long>()

    fun recordFrame(frameTimeNanos: Long): MapDebugMetrics? {
        val previous = previousFrameNanos
        previousFrameNanos = frameTimeNanos
        rollingFrameTimes.addLast(frameTimeNanos)
        val rollingWindowStart = frameTimeNanos - ROLLING_FPS_WINDOW_NANOS
        while (rollingFrameTimes.size > 1 && rollingFrameTimes.first() < rollingWindowStart) {
            rollingFrameTimes.removeFirst()
        }
        if (previous == null) {
            windowStartNanos = frameTimeNanos
            return null
        }

        val frameTimeNanosDelta = (frameTimeNanos - previous).coerceAtLeast(0L)
        frameIntervals += 1
        totalFrameTimeNanos += frameTimeNanosDelta
        maxFrameTimeNanos = maxOf(maxFrameTimeNanos, frameTimeNanosDelta)
        skippedFrames += ((frameTimeNanosDelta / frameBudgetNanos).toInt() - 1).coerceAtLeast(0)

        val windowStart = windowStartNanos ?: frameTimeNanos
        val elapsedNanos = frameTimeNanos - windowStart
        if (elapsedNanos < sampleWindowNanos) return null

        val result =
            MapDebugMetrics(
                framesPerSecond = frameIntervals * NANOS_PER_SECOND.toDouble() / elapsedNanos,
                averageFramesPerSecond30Seconds = rollingFramesPerSecond(),
                averageFrameTimeMillis = totalFrameTimeNanos.toDouble() / frameIntervals / NANOS_PER_MILLISECOND,
                maxFrameTimeMillis = maxFrameTimeNanos.toDouble() / NANOS_PER_MILLISECOND,
                skippedFrames = skippedFrames,
            )
        windowStartNanos = frameTimeNanos
        frameIntervals = 0
        totalFrameTimeNanos = 0L
        maxFrameTimeNanos = 0L
        skippedFrames = 0
        return result
    }

    private fun rollingFramesPerSecond(): Double {
        if (rollingFrameTimes.size < 2) return 0.0
        val elapsedNanos = rollingFrameTimes.last() - rollingFrameTimes.first()
        if (elapsedNanos <= 0L) return 0.0
        return (rollingFrameTimes.size - 1) * NANOS_PER_SECOND.toDouble() / elapsedNanos
    }
}

private fun Double.format(decimalPlaces: Int): String {
    val factor =
        when (decimalPlaces) {
            1 -> 10.0
            2 -> 100.0
            else -> 1.0
        }
    val rounded = kotlin.math.round(this * factor).toLong()
    val absolute = kotlin.math.abs(rounded)
    val sign = if (rounded < 0L) "-" else ""
    val whole = absolute / factor.toLong()
    if (decimalPlaces == 0) return "$sign$whole"
    val fraction = (absolute % factor.toLong()).toString().padStart(decimalPlaces, '0')
    return "$sign$whole.$fraction"
}

internal fun hitRate(
    hits: Long,
    misses: Long,
): String {
    val total = hits + misses
    if (total <= 0L) return "0%"
    return "${(hits * 100.0 / total).format(0)}%"
}

private const val NANOS_PER_MILLISECOND = 1_000_000L
private const val NANOS_PER_SECOND = 1_000_000_000L
private const val ROLLING_FPS_WINDOW_NANOS = 30L * NANOS_PER_SECOND
private val DEBUG_OVERLAY_BACKGROUND = Color(0xE6111827)
private val DEBUG_OVERLAY_TEXT_STYLE =
    TextStyle(
        color = Color.White,
        fontSize = 11.sp,
    )
