package tilo.compose.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CameraControlsStyleTest {
    @Test
    fun defaultsPreserveExistingControlGeometryAndUseThemeColors() {
        val style = CameraControlsStyle()

        assertEquals(16.dp, style.contentPadding.calculateTopPadding())
        assertEquals(12.dp, style.contentPadding.calculateRightPadding(LayoutDirection.Ltr))
        assertEquals(Color.Unspecified, style.containerColor)
        assertEquals(Color.Unspecified, style.contentColor)
        assertEquals(48.dp, style.buttonSize)
        assertEquals(24.dp, style.iconSize)
        assertEquals(8.dp, style.spacing)
        assertEquals(4.dp, style.shadowElevation)
    }

    @Test
    fun acceptsExplicitApplicationColors() {
        val style =
            CameraControlsStyle(
                containerColor = Color(0xFF123456),
                contentColor = Color(0xFFABCDEF),
            )

        assertEquals(Color(0xFF123456), style.containerColor)
        assertEquals(Color(0xFFABCDEF), style.contentColor)
    }

    @Test
    fun compassSouthNeedleUsesResolvedThemeContentColor() {
        val resolvedStyle =
            ResolvedCameraControlsStyle(
                containerColor = Color.Black,
                contentColor = Color.White,
                buttonSize = 44.dp,
                iconSize = 24.dp,
                shadowElevation = 4.dp,
            )

        val colors = compassNeedleColors(resolvedStyle)

        assertEquals(Color(0xFFD32F2F), colors.north)
        assertEquals(Color.White, colors.south)
    }

    @Test
    fun rejectsInvalidDimensions() {
        assertFailsWith<IllegalArgumentException> { CameraControlsStyle(buttonSize = 0.dp) }
        assertFailsWith<IllegalArgumentException> { CameraControlsStyle(iconSize = 0.dp) }
        assertFailsWith<IllegalArgumentException> { CameraControlsStyle(spacing = (-1).dp) }
        assertFailsWith<IllegalArgumentException> { CameraControlsStyle(shadowElevation = (-1).dp) }
    }
}
