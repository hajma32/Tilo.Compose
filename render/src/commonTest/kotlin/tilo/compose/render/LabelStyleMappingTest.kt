package tilo.compose.render

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.style.TextAlign
import tilo.compose.core.feature.LabelStyle
import tilo.compose.core.feature.LabelTextAlign
import kotlin.test.Test
import kotlin.test.assertEquals

class LabelStyleMappingTest {
    @Test
    fun physicalLabelAlignmentMapsToComposeTextStyle() {
        assertEquals(TextAlign.Left, LabelStyle(textAlign = LabelTextAlign.Left).toTextStyle().textAlign)
        assertEquals(TextAlign.Center, LabelStyle().toTextStyle().textAlign)
        assertEquals(TextAlign.Right, LabelStyle(textAlign = LabelTextAlign.Right).toTextStyle().textAlign)
    }

    @Test
    fun labelHaloUsesRoundedCapsAndJoins() {
        val halo = roundedLabelHaloStroke(width = 7.5f)

        assertEquals(7.5f, halo.width)
        assertEquals(StrokeCap.Round, halo.cap)
        assertEquals(StrokeJoin.Round, halo.join)
    }
}
