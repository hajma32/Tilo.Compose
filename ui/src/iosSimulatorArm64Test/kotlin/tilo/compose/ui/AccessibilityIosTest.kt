@file:OptIn(ExperimentalTestApi::class, ExperimentalTiloApi::class)

package tilo.compose.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tilo.compose.core.layers.Attribution
import tilo.compose.core.layers.Layer
import tilo.compose.core.map.MapConfig
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.TiloMap
import tilo.compose.dsl.rememberMapCameraState
import tilo.compose.dsl.tiloMapFocusTarget
import kotlin.test.Test
import kotlin.test.assertEquals

class AccessibilityIosTest {
    @Test
    fun defaultControlsExposeSemanticsAndKeyboardTraversal() =
        runComposeUiTest {
            lateinit var cameraState: tilo.compose.dsl.MapCameraState
            var modifiedArrowEscaped = false
            var modifiedTabEscaped = false
            setContent {
                cameraState = rememberMapCameraState(initialZoom = 3.0, initialBearing = 15.0)
                Box(
                    modifier =
                        Modifier.onKeyEvent { event ->
                            if (event.isCtrlPressed && event.type == KeyEventType.KeyDown) {
                                modifiedArrowEscaped = modifiedArrowEscaped || event.key == Key.DirectionRight
                                modifiedTabEscaped = modifiedTabEscaped || event.key == Key.Tab
                                event.key == Key.DirectionRight || event.key == Key.Tab
                            } else {
                                false
                            }
                        },
                ) {
                    TiloMap(
                        cameraState = cameraState,
                        modifier = Modifier.size(320.dp),
                        cameraControlsContent = defaultCameraControlsContent(),
                        layers = {},
                    )
                }
            }

            val map =
                onNodeWithContentDescription("Interactive map")
                    .assert(
                        SemanticsMatcher("has locale-aware camera state") { node ->
                            val description = node.config[SemanticsProperties.StateDescription]
                            description.startsWith("Zoom ") && description.endsWith(", rotation 15 degrees")
                        },
                    ).requestFocus()
                    .assertIsFocused()
            val centerBefore = cameraState.center
            map.performKeyInput {
                keyDown(Key.CtrlLeft)
                pressKey(Key.DirectionRight)
                pressKey(Key.Tab)
                keyUp(Key.CtrlLeft)
            }
            runOnIdle {
                assertEquals(centerBefore, cameraState.center)
                assertEquals(true, modifiedArrowEscaped)
                assertEquals(true, modifiedTabEscaped)
            }

            map.requestFocus().performKeyInput { pressKey(Key.Tab) }
            onNodeWithContentDescription("Zoom in")
                .assertIsFocused()
                .assert(buttonRole() and hasClickAction())
                .assertWidthIsEqualTo(48.dp)
                .assertHeightIsEqualTo(48.dp)
        }

    @Test
    fun traversalUsesOnlyControlsAndLinksThatAreActuallyComposed() =
        runComposeUiTest {
            setContent {
                val cameraState =
                    rememberMapCameraState(
                        initialZoom = 5.0,
                        config = MapConfig(minZoom = 0.0, maxZoom = 10.0),
                    )
                TiloMap(
                    cameraState = cameraState,
                    modifier = Modifier.size(320.dp),
                    attributionContent = defaultAttributionContent(),
                    cameraControlsContent = { state ->
                        DefaultZoomControls(state)
                        BasicText(
                            text = "Custom control",
                            modifier =
                                Modifier
                                    .tiloMapFocusTarget(2.5f)
                                    .clickable {},
                        )
                    },
                    layers = {
                        layer(
                            object : Layer {
                                override val id: String = "partial"
                                override val attributions: List<Attribution> =
                                    listOf(
                                        Attribution("Static credit"),
                                        Attribution("Linked credit", "https://example.com"),
                                    )
                            },
                        )
                    },
                )
            }

            onNodeWithContentDescription("Interactive map").requestFocus().performKeyInput { pressKey(Key.Tab) }
            onNodeWithContentDescription("Zoom in").assertIsFocused().performKeyInput { pressKey(Key.Tab) }
            onNodeWithContentDescription("Zoom out").assertIsFocused().performKeyInput { pressKey(Key.Tab) }
            onNodeWithText("Static credit")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 4.0f))
            onNodeWithText("Custom control")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 2.5f))
                .assertIsFocused()
                .performKeyInput { pressKey(Key.Tab) }
            val linkedCredit = onNodeWithText("Linked credit")
            linkedCredit.assert(
                SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 5.0f),
            )
            linkedCredit.assert(
                SemanticsMatcher("attribution stays visually compact") { node ->
                    node.boundsInRoot.height < with(density) { 48.dp.toPx() }
                },
            )
            linkedCredit.assertIsFocused().performKeyInput {
                keyDown(Key.ShiftLeft)
                pressKey(Key.Tab)
                keyUp(Key.ShiftLeft)
            }
            onNodeWithText("Custom control").assertIsFocused()
        }

    private fun buttonRole(): SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
}
