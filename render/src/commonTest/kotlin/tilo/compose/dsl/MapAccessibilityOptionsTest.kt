@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tilo.compose.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertFailsWith

class MapAccessibilityOptionsTest {
    @Test
    fun rejectsInvalidKeyboardStepsAndBlankDescription() {
        assertFailsWith<IllegalArgumentException> {
            MapAccessibilityOptions(contentDescription = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            MapAccessibilityOptions(keyboardPanStepPx = 0.0)
        }
        assertFailsWith<IllegalArgumentException> {
            MapAccessibilityOptions(keyboardZoomStep = Double.NaN)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    @Composable
    private fun originalPositionalOverloadsStillCompile(
        cameraState: MapCameraState,
        diagnosticsState: MapDiagnosticsState,
    ) {
        TiloMap(cameraState, Modifier, { _: Point -> }, layers = {})
        TiloMap(cameraState, diagnosticsState, Modifier, { _: Point -> }, layers = {})
    }
}
