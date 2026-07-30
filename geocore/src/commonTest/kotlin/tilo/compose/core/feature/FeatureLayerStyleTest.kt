package tilo.compose.core.feature

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureLayerStyleTest {
    @Test
    fun zoomRulesUseInclusiveMinimumAndExclusiveMaximum() {
        val narrow = LineStyle(stroke = StrokeStyle(width = 6.0))
        val wide = LineStyle(stroke = StrokeStyle(width = 20.0))
        val style =
            FeatureLayerStyle(
                line = narrow,
                zoomRules =
                    listOf(
                        FeatureLayerStyleZoomRule(
                            minZoom = 14.0,
                            maxZoomExclusive = 16.0,
                            line = wide,
                            labelsVisible = false,
                        ),
                    ),
            )

        assertEquals(narrow, style.resolveAtZoom(13.999).line)
        assertEquals(wide, style.resolveAtZoom(14.0).line)
        assertFalse(style.resolveAtZoom(15.999).labelsVisible)
        assertEquals(narrow, style.resolveAtZoom(16.0).line)
        assertTrue(style.resolveAtZoom(16.0).labelsVisible)
    }

    @Test
    fun laterMatchingZoomRuleOverridesOnlyFieldsItSupplies() {
        val basePoint = PointStyle(size = 12.0)
        val firstLine = LineStyle(stroke = StrokeStyle(width = 8.0))
        val secondLine = LineStyle(stroke = StrokeStyle(width = 16.0))
        val style =
            FeatureLayerStyle(
                point = basePoint,
                zoomRules =
                    listOf(
                        FeatureLayerStyleZoomRule(minZoom = 10.0, line = firstLine, labelsVisible = false),
                        FeatureLayerStyleZoomRule(minZoom = 12.0, line = secondLine),
                    ),
            )

        val resolved = style.resolveAtZoom(13.0)

        assertEquals(basePoint, resolved.point)
        assertEquals(secondLine, resolved.line)
        assertFalse(resolved.labelsVisible)
        assertTrue(resolved.zoomRules.isEmpty())
    }

    @Test
    fun invalidZoomRangesAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            FeatureLayerStyleZoomRule(minZoom = 14.0, maxZoomExclusive = 14.0)
        }
        assertFailsWith<IllegalArgumentException> {
            FeatureLayerStyleZoomRule(minZoom = Double.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            FeatureLayerStyle().resolveAtZoom(Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun casingWidthIsAdditionalToForegroundStroke() {
        assertEquals(8.0, CasingStyle(width = 2.0).outerWidth(strokeWidth = 6.0))
    }
}
