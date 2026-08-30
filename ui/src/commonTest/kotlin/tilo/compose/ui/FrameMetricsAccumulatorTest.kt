@file:OptIn(tilo.compose.render.ExperimentalTiloRenderingApi::class)

package tilo.compose.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class FrameMetricsAccumulatorTest {
    @Test
    fun reportsFpsFrameTimesAndSkippedFrames() {
        val accumulator =
            FrameMetricsAccumulator(
                targetFrameRate = 60,
                sampleWindowNanos = 50_000_000L,
            )

        accumulator.recordFrame(0L)
        accumulator.recordFrame(16_666_666L)
        accumulator.recordFrame(33_333_332L)
        val metrics = accumulator.recordFrame(66_666_664L)

        requireNotNull(metrics)
        assertEquals(45.0, metrics.framesPerSecond, absoluteTolerance = 0.01)
        assertEquals(45.0, metrics.averageFramesPerSecond30Seconds, absoluteTolerance = 0.01)
        assertEquals(22.22, metrics.averageFrameTimeMillis, absoluteTolerance = 0.01)
        assertEquals(33.33, metrics.maxFrameTimeMillis, absoluteTolerance = 0.01)
        assertEquals(1, metrics.skippedFrames)
    }

    @Test
    fun cacheHitRateHandlesEmptyAndPopulatedCounters() {
        assertEquals("0%", hitRate(hits = 0, misses = 0))
        assertEquals("75%", hitRate(hits = 3, misses = 1))
        assertEquals("33%", hitRate(hits = 1, misses = 2))
    }
}
