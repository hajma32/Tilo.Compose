package tilo.compose.render

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
}
