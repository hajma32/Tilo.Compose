@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import tilo.compose.core.feature.LabelTextAlign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StyleDslTest {
    @Test
    fun labelStyleDefaultsToCenterAndSupportsPhysicalAlignment() {
        assertEquals(LabelTextAlign.Center, labelStyle().textAlign)
        assertEquals(
            LabelTextAlign.Right,
            labelStyle { textAlign = LabelTextAlign.Right }.textAlign,
        )
    }

    @Test
    fun labelFontSizeAcceptsOnlyScalablePixels() {
        assertFailsWith<IllegalArgumentException> {
            labelStyle { fontSize(1.em) }
        }
    }

    @Test
    fun casingDslStoresAdditionalWidth() {
        val defaultStyle = lineStyle()
        val customStyle =
            lineStyle {
                stroke(0xFF000000, width = 6.dp)
                casing(0xFFFFFFFF, width = 3.dp)
            }

        assertEquals(2.0, defaultStyle.casing?.width)
        assertEquals(5.0, defaultStyle.casing?.outerWidth(defaultStyle.stroke.width))
        assertEquals(3.0, customStyle.casing?.width)
        assertEquals(9.0, customStyle.casing?.outerWidth(customStyle.stroke.width))
    }

    @Test
    fun featureLayerStyleDslBuildsOrderedZoomRulesAndLabelVisibility() {
        val style =
            featureLayerStyle {
                line { stroke(0xFF000000, width = 6.dp) }
                zoom(minZoom = 12.0, maxZoomExclusive = 14.0) {
                    label { textAlign = LabelTextAlign.Left }
                }
                zoom(minZoom = 14.0) {
                    line { stroke(0xFF000000, width = 20.dp) }
                    labelsVisible = false
                }
            }

        val belowRule = style.resolveAtZoom(11.0)
        val labelRule = style.resolveAtZoom(12.0)
        val wideRule = style.resolveAtZoom(14.0)

        assertEquals(6.0, belowRule.line?.stroke?.width)
        assertEquals(LabelTextAlign.Left, labelRule.label?.textAlign)
        assertEquals(20.0, wideRule.line?.stroke?.width)
        assertFalse(wideRule.labelsVisible)
        assertTrue(style.resolveAtZoom(13.999).labelsVisible)
    }

    @Test
    fun layerLabelColorUsesTheSingleBlockSpelling() {
        val style =
            featureLayerStyle {
                label { color(0xFF123456) }
                selectedLabel { color(0xFF654321) }
                zoom(minZoom = 10.0) {
                    label { color(0xFFABCDEF) }
                    selectedLabel { color(0xFFFEDCBA) }
                }
            }

        assertEquals(argb(0xFF123456), style.label?.color)
        assertEquals(argb(0xFF654321), style.selectedLabel?.color)
        assertEquals(
            argb(0xFFABCDEF),
            style.zoomRules
                .single()
                .label
                ?.color,
        )
        assertEquals(
            argb(0xFFFEDCBA),
            style.zoomRules
                .single()
                .selectedLabel
                ?.color,
        )
    }
}
