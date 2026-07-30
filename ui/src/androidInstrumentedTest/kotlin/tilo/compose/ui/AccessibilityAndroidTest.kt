@file:OptIn(ExperimentalTiloApi::class, ExperimentalTestApi::class)

package tilo.compose.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.runner.RunWith
import tilo.compose.core.layers.Attribution
import tilo.compose.core.layers.Layer
import tilo.compose.core.map.MapConfig
import tilo.compose.core.scale.ScaleBar
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.MapAccessibilityOptions
import tilo.compose.dsl.TiloMap
import tilo.compose.dsl.rememberMapCameraState
import tilo.compose.dsl.tiloMapFocusTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@RunWith(AndroidJUnit4::class)
class AccessibilityAndroidTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun cameraControlsExposeButtonSemanticsSizeActionsAndDisabledState() {
        var zoomDelta = 0.0
        rule.setContent {
            Box(Modifier.size(240.dp)) {
                DefaultZoomControls(
                    onZoomBy = { zoomDelta += it },
                    canZoomIn = true,
                    canZoomOut = false,
                    accessibility =
                        MapUiAccessibility(
                            zoomInDescription = "Increase zoom",
                            zoomOutDescription = "Decrease zoom",
                        ),
                )
            }
        }

        rule
            .onNodeWithContentDescription("Increase zoom")
            .assert(buttonRole() and hasClickAction())
            .assertIsEnabled()
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
            .performClick()
        rule
            .onNodeWithContentDescription("Decrease zoom")
            .assert(buttonRole())
            .assertIsNotEnabled()
        rule.runOnIdle { assertEquals(1.0, zoomDelta) }
    }

    @Test
    fun compassScaleAndAttributionExposeAccessibleMeaning() {
        val uriHandler = RecordingUriHandler()
        val accessibility =
            MapUiAccessibility(
                resetNorthDescription = "Point north",
                scaleBarDescription = { "Distance represented: ${it.label}" },
                attributionClickLabel = { "Open ${it.label} credits" },
            )
        rule.setContent {
            val cameraState = rememberMapCameraState(initialBearing = 0.0)
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                Box(Modifier.size(320.dp)) {
                    DefaultCompassControl(cameraState, accessibility = accessibility)
                    DefaultScaleBar(
                        ScaleBar(
                            distanceMeters = 500.0,
                            widthPx = 120.0,
                            label = "500 m",
                            midpointLabel = "250 m",
                        ),
                        accessibility = accessibility,
                    )
                    DefaultAttributionOverlay(
                        listOf(Attribution("OpenStreetMap", "https://openstreetmap.org")),
                        accessibility = accessibility,
                    )
                }
            }
        }

        rule
            .onNodeWithContentDescription("Point north")
            .assert(buttonRole())
            .assertIsNotEnabled()
        rule
            .onNode(hasContentDescription("Distance represented: 500 m"))
            .assertExists()
        rule
            .onNodeWithText("OpenStreetMap")
            .assert(buttonRole() and hasClickAction() and hasClickLabel("Open OpenStreetMap credits"))
            .assert(
                SemanticsMatcher("attribution stays visually compact") { node ->
                    node.boundsInRoot.height < with(rule.density) { 48.dp.toPx() }
                },
            ).performClick()
        rule.runOnIdle { assertEquals("https://openstreetmap.org", uriHandler.openedUri) }
    }

    @Test
    fun mapDescriptionsAreReplaceableAndKeyboardOnlyActsWhenMapIsFocused() {
        lateinit var cameraState: tilo.compose.dsl.MapCameraState
        rule.setContent {
            cameraState =
                rememberMapCameraState(
                    initialZoom = 3.0,
                    initialBearing = 25.0,
                )
            TiloMap(
                cameraState = cameraState,
                modifier = Modifier.size(320.dp),
                accessibility =
                    MapAccessibilityOptions(
                        contentDescription = "Transit map",
                        stateDescription = { state -> "Level ${state.zoom}, heading ${state.bearing}" },
                    ),
                layers = {},
            )
        }

        val map =
            rule.onNode(
                hasContentDescription("Transit map") and
                    hasStateDescription("Level 3.0, heading 25.0"),
            )
        map.requestFocus().assertIsFocused()
        map.performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.Plus)
            pressKey(Key.Home)
        }
        rule.runOnIdle {
            assertNotEquals(0.0, cameraState.center.x)
            assertEquals(4.0, cameraState.zoom)
            assertEquals(0.0, cameraState.bearing)
        }
        rule
            .onNode(
                hasContentDescription("Transit map") and
                    hasStateDescription("Level 4.0, heading 0.0"),
            ).assertExists()
    }

    @Test
    fun modifiedKeysAndFocusedTextInputDoNotControlTheMap() {
        lateinit var cameraState: tilo.compose.dsl.MapCameraState
        var text by mutableStateOf("")
        var modifiedArrowEscaped = false
        var modifiedTabEscaped = false
        rule.setContent {
            cameraState = rememberMapCameraState(initialZoom = 3.0, initialBearing = 25.0)
            Column(
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
                    modifier = Modifier.size(240.dp),
                    layers = {},
                )
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier =
                        Modifier
                            .size(160.dp, 48.dp)
                            .semantics { contentDescription = "Editor" },
                )
            }
        }

        val map = rule.onNodeWithContentDescription("Interactive map").requestFocus().assertIsFocused()
        val centerBefore = cameraState.center
        map.performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.DirectionRight)
            pressKey(Key.Tab)
            keyUp(Key.CtrlLeft)
        }
        rule.runOnIdle {
            assertEquals(centerBefore, cameraState.center)
            assertEquals(true, modifiedArrowEscaped)
            assertEquals(true, modifiedTabEscaped)
        }

        rule.onNodeWithContentDescription("Editor").requestFocus().performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.Plus)
            pressKey(Key.Home)
        }
        rule.runOnIdle {
            assertEquals(centerBefore, cameraState.center)
            assertEquals(3.0, cameraState.zoom)
            assertEquals(25.0, cameraState.bearing)
        }
    }

    @Test
    fun traversalSkipsMissingControlsAndStartsAtFirstLinkedAttribution() {
        rule.setContent {
            val cameraState =
                rememberMapCameraState(
                    initialZoom = 5.0,
                    config = MapConfig(minZoom = 0.0, maxZoom = 10.0),
                )
            TiloMap(
                cameraState = cameraState,
                modifier = Modifier.fillMaxSize(),
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
                            override val id: String = "partially-attributed"
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

        rule.onNodeWithContentDescription("Interactive map").requestFocus().performKeyInput { pressKey(Key.Tab) }
        rule
            .onNodeWithContentDescription("Zoom in")
            .assert(focusedAfter("Tab from map"))
            .performKeyInput { pressKey(Key.Tab) }
        rule
            .onNodeWithContentDescription("Zoom out")
            .assert(focusedAfter("Tab from zoom in"))
            .performKeyInput { pressKey(Key.Tab) }
        rule
            .onNodeWithText("Static credit")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 4.0f))
        rule
            .onNodeWithText("Custom control")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 2.5f))
            .assert(focusedAfter("Tab from zoom out"))
            .performKeyInput { pressKey(Key.Tab) }
        rule
            .onNodeWithText("Linked credit")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 5.0f))
            .assert(focusedAfter("Tab from custom control"))
            .performKeyInput {
                keyDown(Key.ShiftLeft)
                pressKey(Key.Tab)
                keyUp(Key.ShiftLeft)
            }
        rule.onNodeWithText("Custom control").assert(focusedAfter("Shift+Tab from linked credit"))
    }

    @Test
    fun disabledKeyboardNavigationDoesNotMakeMapFocusable() {
        rule.setContent {
            val cameraState = rememberMapCameraState()
            TiloMap(
                cameraState = cameraState,
                modifier = Modifier.size(240.dp),
                accessibility = MapAccessibilityOptions(keyboardNavigationEnabled = false),
                layers = {},
            )
        }

        rule
            .onNodeWithContentDescription("Interactive map")
            .assert(
                SemanticsMatcher("has no focus semantics or focus request action") { node ->
                    SemanticsProperties.Focused !in node.config &&
                        SemanticsActions.RequestFocus !in node.config
                },
            )
    }

    @Test
    fun tabOrderIsMapThenZoomInZoomOutCompassAndAttribution() {
        lateinit var cameraState: tilo.compose.dsl.MapCameraState
        rule.setContent {
            cameraState =
                rememberMapCameraState(
                    initialZoom = 5.0,
                    initialBearing = 15.0,
                    config = MapConfig(minZoom = 0.0, maxZoom = 10.0),
                )
            TiloMap(
                cameraState = cameraState,
                modifier = Modifier.fillMaxSize(),
                attributionContent = defaultAttributionContent(),
                cameraControlsContent = defaultCameraControlsContent(),
                layers = {
                    layer(
                        object : Layer {
                            override val id: String = "attributed"
                            override val attributions: List<Attribution> =
                                listOf(Attribution("Provider", "https://example.com"))
                        },
                    )
                },
            )
        }

        rule
            .onNodeWithContentDescription("Interactive map")
            .requestFocus()
            .assert(focusedAfter("explicit map focus request"))
            .performKeyInput { pressKey(Key.Tab) }
        rule
            .onNodeWithContentDescription("Zoom in")
            .assert(focusedAfter("Tab from map"))
            .performKeyInput { pressKey(Key.Tab) }
        rule
            .onNodeWithContentDescription("Zoom out")
            .assert(focusedAfter("Tab from zoom in"))
            .performKeyInput { pressKey(Key.Tab) }
        rule
            .onNodeWithContentDescription("Reset map rotation to north")
            .assert(focusedAfter("Tab from zoom out"))
            .performKeyInput { pressKey(Key.Tab) }
        rule.onNodeWithText("Provider").assert(focusedAfter("Tab from compass"))

        val bearingBefore = cameraState.bearing
        rule.onNodeWithText("Provider").performKeyInput { pressKey(Key.Home) }
        rule.runOnIdle { assertEquals(bearingBefore, cameraState.bearing) }
    }

    private class RecordingUriHandler : UriHandler {
        var openedUri: String? = null

        override fun openUri(uri: String) {
            openedUri = uri
        }
    }

    private fun buttonRole(): SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun focusedAfter(action: String): SemanticsMatcher {
        val focusedNodes = rule.onAllNodes(isFocused()).fetchSemanticsNodes()
        val focusedSnapshot = focusedNodes.joinToString(prefix = "[", postfix = "]") { it.config.toString() }
        return SemanticsMatcher("is focused after $action; focused nodes: $focusedSnapshot") { node ->
            node.config.getOrElse(SemanticsProperties.Focused) { false }
        }
    }

    private fun hasClickLabel(label: String): SemanticsMatcher =
        SemanticsMatcher("has click label '$label'") { node ->
            node.config[SemanticsActions.OnClick].label == label
        }
}
