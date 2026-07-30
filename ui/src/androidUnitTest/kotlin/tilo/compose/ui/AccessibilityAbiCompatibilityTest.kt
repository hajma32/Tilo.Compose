package tilo.compose.ui

import kotlin.test.Test
import kotlin.test.assertContains

class AccessibilityAbiCompatibilityTest {
    @Test
    fun previousJvmDescriptorsRemainAvailable() {
        assertDescriptor("DefaultMapOverlaysKt", "defaultAttributionContent")
        assertDescriptor("DefaultMapOverlaysKt", "defaultScaleBarContent")
        assertDescriptor("DefaultMapOverlaysKt", "defaultZoomControlsContent", CAMERA_CONTROLS_STYLE)
        assertDescriptor("DefaultMapOverlaysKt", "defaultCameraControlsContent", CAMERA_CONTROLS_STYLE)
        assertDescriptor(
            "DefaultMapOverlaysKt",
            "defaultZoomControlsContent\$default",
            CAMERA_CONTROLS_STYLE,
            INT,
            OBJECT,
        )
        assertDescriptor(
            "DefaultMapOverlaysKt",
            "defaultCameraControlsContent\$default",
            CAMERA_CONTROLS_STYLE,
            INT,
            OBJECT,
        )
        assertDescriptor(
            "DefaultAttributionOverlayKt",
            "DefaultAttributionOverlay",
            BOX_SCOPE,
            LIST,
            COMPOSER,
            INT,
        )
        assertDescriptor(
            "DefaultScaleBarKt",
            "DefaultScaleBar",
            BOX_SCOPE,
            SCALE_BAR,
            COMPOSER,
            INT,
        )
        assertDescriptor(
            "DefaultCompassControlKt",
            "DefaultCompassControl",
            BOX_SCOPE,
            MAP_CAMERA_STATE,
            CAMERA_CONTROLS_STYLE,
            COMPOSER,
            INT,
            INT,
        )
        assertDescriptor(
            "DefaultZoomControlsKt",
            "DefaultZoomControls",
            BOX_SCOPE,
            MAP_CAMERA_STATE,
            DOUBLE,
            CAMERA_CONTROLS_STYLE,
            COMPOSER,
            INT,
            INT,
        )
        assertDescriptor(
            "DefaultZoomControlsKt",
            "DefaultZoomControls",
            BOX_SCOPE,
            MAP_CAMERA_CONTROLLER,
            DOUBLE,
            CAMERA_CONTROLS_STYLE,
            COMPOSER,
            INT,
            INT,
        )
        assertDescriptor(
            "DefaultZoomControlsKt",
            "DefaultZoomControls",
            BOX_SCOPE,
            DOUBLE,
            FUNCTION_2,
            CAMERA_CONTROLS_STYLE,
            COMPOSER,
            INT,
            INT,
        )
    }

    private fun assertDescriptor(
        fileFacade: String,
        methodName: String,
        vararg parameterTypes: String,
    ) {
        val descriptors =
            Class
                .forName("tilo.compose.ui.$fileFacade")
                .declaredMethods
                .filter { it.name == methodName }
                .map { method -> method.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.name } }
        val expected = parameterTypes.joinToString(prefix = "(", postfix = ")")
        assertContains(descriptors, expected, "$fileFacade.$methodName no longer exposes $expected")
    }

    private companion object {
        const val BOX_SCOPE = "androidx.compose.foundation.layout.BoxScope"
        const val CAMERA_CONTROLS_STYLE = "tilo.compose.ui.CameraControlsStyle"
        const val COMPOSER = "androidx.compose.runtime.Composer"
        const val DOUBLE = "double"
        const val FUNCTION_2 = "kotlin.jvm.functions.Function2"
        const val INT = "int"
        const val LIST = "java.util.List"
        const val MAP_CAMERA_CONTROLLER = "tilo.compose.core.map.MapCameraController"
        const val MAP_CAMERA_STATE = "tilo.compose.dsl.MapCameraState"
        const val OBJECT = "java.lang.Object"
        const val SCALE_BAR = "tilo.compose.core.scale.ScaleBar"
    }
}
