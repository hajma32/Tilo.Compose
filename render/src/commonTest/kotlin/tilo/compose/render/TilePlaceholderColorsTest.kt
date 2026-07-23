package tilo.compose.render

import androidx.compose.ui.graphics.Color
import tilo.compose.render.backend.tilePlaceholderColorsFor
import kotlin.test.Test
import kotlin.test.assertEquals

class TilePlaceholderColorsTest {
    @Test
    fun darkApplicationSurfaceUsesDarkBluePlaceholderPalette() {
        val colors = tilePlaceholderColorsFor(Color(0xFF101712))

        assertEquals(Color(0xFF102A43), colors.fill)
        assertEquals(Color(0xFF28547A), colors.border)
    }

    @Test
    fun lightApplicationSurfaceKeepsLightPlaceholderPalette() {
        val colors = tilePlaceholderColorsFor(Color(0xFFF6F5F0))

        assertEquals(Color(0xFFE3F2FD), colors.fill)
        assertEquals(Color(0xFF90CAF9), colors.border)
    }
}
