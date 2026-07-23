package tilo.compose.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultScaleBarTest {
    @Test
    fun segmentsMeetExactlyWithoutGapOrOverlap() {
        val segments = scaleBarSegments(width = 101.0f, height = 12.0f)

        assertEquals(0.0f, segments.start.left)
        assertEquals(50.5f, segments.start.right)
        assertEquals(segments.start.right, segments.end.left)
        assertEquals(101.0f, segments.end.right)
        assertEquals(12.0f, segments.start.height)
        assertEquals(12.0f, segments.end.height)
    }

    @Test
    fun scaleBarBodyUsesRequestedOpacity() {
        assertEquals(0.8f, SCALE_BAR_OPACITY)
    }
}
