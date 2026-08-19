@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.render

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.runner.RunWith
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.source.FeatureSource
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.Attribution
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.map.CameraPosition
import tilo.compose.core.map.MapState
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.MapCameraState
import tilo.compose.dsl.MapGestureConfig
import tilo.compose.dsl.TiloMap
import tilo.compose.dsl.TiloMapOptions
import tilo.compose.dsl.rememberMapCameraState
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MapEventRoutingAndroidTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun activeLayerAttributionUsesTheDefaultAccessibilityContract() {
        composeRule.setContent {
            camera = rememberMapCameraState()
            TiloMap(
                cameraState = camera,
                modifier = Modifier.size(MAP_SIZE),
            ) {
                featureLayer(id = "credited", features = emptyList()) {
                    attributions =
                        listOf(
                            Attribution("Required provider credit"),
                            Attribution("Linked provider credit", "https://example.com/credit"),
                        )
                }
            }
        }

        composeRule
            .onNodeWithText("Required provider credit")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 4.0f))
        composeRule
            .onNodeWithText("Linked provider credit")
            .assert(hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 5.0f))
            .assert(
                SemanticsMatcher("has a descriptive attribution click label") { node ->
                    node.config[SemanticsActions.OnClick].label ==
                        "Open attribution for Linked provider credit"
                },
            )
    }

    private lateinit var camera: MapCameraState
    private var mapTaps = 0
    private var overlayClicks = 0
    private val callbackCalls = mutableListOf<String>()

    @Test
    fun interactivePublicOverlayOwnsTap() {
        setMapContent(overlay = Overlay.Interactive)

        composeRule.onNodeWithTag(OVERLAY_TAG).performTouchInput { click() }

        composeRule.runOnIdle {
            assertEquals(1, overlayClicks)
            assertEquals(0, mapTaps)
        }
    }

    @Test
    fun dragStartingOnInteractivePublicOverlayDoesNotPanMap() {
        setMapContent(overlay = Overlay.Interactive)
        val initialCenter = camera.center

        composeRule.onNodeWithTag(OVERLAY_TAG).performTouchInput {
            swipe(start = center, end = center + Offset(60f, 0f), durationMillis = 200)
        }

        composeRule.runOnIdle {
            assertEquals(initialCenter, camera.center)
            assertEquals(0, mapTaps)
        }
    }

    @Test
    fun nonInteractivePublicOverlayPassesTapToMap() {
        setMapContent(overlay = Overlay.NonInteractive)

        composeRule.onNodeWithTag(MAP_TAG).performTouchInput { click(Offset(40f, 40f)) }

        composeRule.waitUntil(timeoutMillis = 2_000) { mapTaps == 1 }
        composeRule.runOnIdle { assertEquals(1, mapTaps) }
    }

    @Test
    fun mapDragSuppressesTap() {
        setMapContent()
        val initialCenter = camera.center

        composeRule.onNodeWithTag(MAP_TAG).performTouchInput {
            swipe(start = center, end = center + Offset(80f, 0f), durationMillis = 300)
        }

        composeRule.runOnIdle {
            assertNotEquals(initialCenter, camera.center)
            assertEquals(0, mapTaps)
        }
    }

    @Test
    fun pinchZoomSuppressesTap() {
        setMapContent()

        composeRule.onNodeWithTag(MAP_TAG).performTouchInput {
            pinch(
                start0 = center + Offset(-20f, 0f),
                start1 = center + Offset(20f, 0f),
                end0 = center + Offset(-70f, 0f),
                end1 = center + Offset(70f, 0f),
                durationMillis = 300,
            )
        }

        composeRule.runOnIdle {
            assertTrue(camera.zoom > 0.0)
            assertEquals(0, mapTaps)
        }
    }

    @Test
    fun pinchBelowDefaultAngularThresholdZoomsWithoutRotating() {
        setMapContent()

        performFourDegreePinch()

        composeRule.runOnIdle {
            assertTrue(camera.zoom > 0.0)
            assertEquals(0.0, camera.bearing, absoluteTolerance = 0.1)
            assertEquals(0, mapTaps)
        }
    }

    @Test
    fun configuredAngularThresholdAllowsMoreSensitiveRotation() {
        setMapContent(gestureConfig = MapGestureConfig(rotationThresholdDegrees = 2.0))

        performFourDegreePinch()

        composeRule.runOnIdle {
            assertTrue(camera.zoom > 0.0)
            assertTrue(angularDistanceFromZero(camera.bearing) > 0.5)
            assertEquals(0, mapTaps)
        }
    }

    @Test
    fun zeroAngularThresholdDoesNotTurnSingleTapIntoTransformGesture() {
        setMapContent(gestureConfig = MapGestureConfig(rotationThresholdDegrees = 0.0))
        val initialCenter = camera.center

        composeRule.onNodeWithTag(MAP_TAG).performTouchInput { click(center) }

        composeRule.waitUntil(timeoutMillis = 2_000) { mapTaps == 1 }
        composeRule.runOnIdle {
            assertEquals(initialCenter, camera.center)
            assertEquals(0.0, camera.zoom)
            assertEquals(0.0, camera.bearing)
            assertEquals(1, mapTaps)
        }
    }

    @Test
    fun zeroAngularThresholdRotatesOnFirstAngularMovement() {
        setMapContent(gestureConfig = MapGestureConfig(rotationThresholdDegrees = 0.0))

        performFourDegreePinch()

        composeRule.runOnIdle {
            assertTrue(angularDistanceFromZero(camera.bearing) > 3.0)
            assertEquals(0, mapTaps)
        }
    }

    @Test
    fun recomposedAngularThresholdAppliesToNextGesture() {
        val gestureConfig = mutableStateOf(MapGestureConfig.Default)
        mapTaps = 0
        composeRule.setContent {
            camera = rememberMapCameraState()
            TiloMap(
                cameraState = camera,
                modifier = Modifier.size(MAP_SIZE).testTag(MAP_TAG),
                options = TiloMapOptions(gestureConfig = gestureConfig.value),
                onTapWorld = { mapTaps += 1 },
                layers = {},
            )
        }

        performFourDegreePinch()
        composeRule.runOnIdle {
            assertEquals(0.0, camera.bearing, absoluteTolerance = 0.1)
            gestureConfig.value = MapGestureConfig(rotationThresholdDegrees = 2.0)
        }

        performFourDegreePinch()

        composeRule.runOnIdle {
            assertTrue(angularDistanceFromZero(camera.bearing) > 0.5)
            assertEquals(0, mapTaps)
        }
    }

    @Test
    fun replacedCameraReceivesNextGestureInsteadOfPreviousCamera() {
        val useReplacement = mutableStateOf(false)
        lateinit var originalCamera: MapCameraState
        lateinit var replacementCamera: MapCameraState
        composeRule.setContent {
            originalCamera = rememberMapCameraState(initialPosition = CameraPosition(Point(0.0, 0.0), 0.0))
            replacementCamera = rememberMapCameraState(initialPosition = CameraPosition(Point(100.0, 100.0), 0.0))
            camera = if (useReplacement.value) replacementCamera else originalCamera
            TiloMap(
                cameraState = camera,
                modifier = Modifier.size(MAP_SIZE).testTag(MAP_TAG),
                layers = {},
            )
        }
        val originalCenter = originalCamera.center

        composeRule.runOnIdle { useReplacement.value = true }
        composeRule.waitForIdle()
        val replacementCenter = replacementCamera.center

        composeRule.onNodeWithTag(MAP_TAG).performTouchInput {
            swipe(start = center, end = center + Offset(80f, 0f), durationMillis = 300)
        }

        composeRule.runOnIdle {
            assertEquals(originalCenter, originalCamera.center)
            assertNotEquals(replacementCenter, replacementCamera.center)
        }
    }

    @Test
    fun replacingCameraDuringActiveGestureCancelsSequenceWithoutMutatingEitherCamera() {
        val useReplacement = mutableStateOf(false)
        lateinit var originalCamera: MapCameraState
        lateinit var replacementCamera: MapCameraState
        composeRule.setContent {
            originalCamera = rememberMapCameraState(initialPosition = CameraPosition(Point(0.0, 0.0), 0.0))
            replacementCamera = rememberMapCameraState(initialPosition = CameraPosition(Point(100.0, 100.0), 0.0))
            camera = if (useReplacement.value) replacementCamera else originalCamera
            TiloMap(
                cameraState = camera,
                modifier = Modifier.size(MAP_SIZE).testTag(MAP_TAG),
                layers = {},
            )
        }
        val mapNode = composeRule.onNodeWithTag(MAP_TAG)
        val originalCenter = originalCamera.center

        mapNode.performTouchInput { down(center) }
        composeRule.runOnIdle { useReplacement.value = true }
        composeRule.waitForIdle()
        val replacementCenter = replacementCamera.center

        mapNode.performTouchInput {
            moveTo(center + Offset(80f, 0f), delayMillis = 200)
            up()
        }

        composeRule.runOnIdle {
            assertEquals(originalCenter, originalCamera.center)
            assertEquals(replacementCenter, replacementCamera.center)
        }
    }

    @Test
    fun rotationSuppressesTap() {
        setMapContent()

        composeRule.onNodeWithTag(MAP_TAG).performTouchInput {
            down(0, center + Offset(-45f, 0f))
            down(1, center + Offset(45f, 0f))
            moveTo(0, center + Offset(0f, -45f), delayMillis = 150)
            moveTo(1, center + Offset(0f, 45f), delayMillis = 150)
            up(0)
            up(1)
        }

        composeRule.runOnIdle {
            assertTrue(angularDistanceFromZero(camera.bearing) in 75.0..90.0)
            assertEquals(0, mapTaps)
        }
    }

    @Test
    fun doubleTapZoomsWithoutDispatchingSingleTap() {
        setMapContent()

        composeRule.onNodeWithTag(MAP_TAG).performTouchInput { doubleClick() }

        composeRule.waitUntil(timeoutMillis = 2_000) { camera.zoom > 0.0 }
        composeRule.runOnIdle {
            assertTrue(camera.zoom > 0.0)
            assertEquals(0, mapTaps)
        }
    }

    @Test
    fun newTapStopsDoubleTapZoomAnimation() {
        composeRule.mainClock.autoAdvance = false
        try {
            setMapContent()
            val mapNode = composeRule.onNodeWithTag(MAP_TAG)

            mapNode.performTouchInput { doubleClick() }
            composeRule.mainClock.advanceTimeBy(96)
            var interruptedZoom = 0.0
            composeRule.runOnIdle { interruptedZoom = camera.zoom }

            mapNode.performTouchInput { click() }
            composeRule.mainClock.advanceTimeBy(1_000)

            composeRule.runOnIdle {
                assertTrue(interruptedZoom > 0.0)
                assertEquals(interruptedZoom, camera.zoom, absoluteTolerance = 0.001)
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun featureSelectionRunsBeforeWorldTapThroughPublicDsl() {
        setMapContent(includeCenterFeature = true, recordSelection = true)

        composeRule.onNodeWithTag(MAP_TAG).performTouchInput { click(center) }

        composeRule.waitUntil(timeoutMillis = 2_000) { callbackCalls.size == 2 }
        composeRule.runOnIdle {
            assertEquals(listOf("selection:center", "world"), callbackCalls)
        }
    }

    @Test
    fun emptyFeatureSelectionStillRunsBeforeWorldTapThroughPublicDsl() {
        setMapContent(recordSelection = true)

        composeRule.onNodeWithTag(MAP_TAG).performTouchInput { click(center) }

        composeRule.waitUntil(timeoutMillis = 2_000) { callbackCalls.size == 2 }
        composeRule.runOnIdle {
            assertEquals(listOf("selection:none", "world"), callbackCalls)
        }
    }

    @Test
    fun featureSourceInvalidationRendersWithoutAnotherComposeStateChange() {
        val source = MutableInvalidatingFeatureSource()
        var selectedFeatureKey: String? = null
        composeRule.setContent {
            camera = rememberMapCameraState()
            TiloMap(
                cameraState = camera,
                modifier = Modifier.size(MAP_SIZE).testTag(MAP_TAG),
                onFeatureSelect = { hits -> selectedFeatureKey = hits.singleOrNull()?.feature?.key },
                layers = { layer(TestInvalidatingVectorLayer(source)) },
            )
        }
        composeRule.waitUntil(timeoutMillis = 2_000) { source.queriedVersion == 0L }

        source.update(listOf(Feature(key = "updated", geometry = Point(0.0, 0.0))))

        composeRule.waitUntil(timeoutMillis = 2_000) { source.queriedVersion == 1L }
        composeRule.onNodeWithTag(MAP_TAG).performTouchInput { click(center) }
        composeRule.waitUntil(timeoutMillis = 2_000) { selectedFeatureKey != null }
        composeRule.runOnIdle { assertEquals("updated", selectedFeatureKey) }
    }

    private fun setMapContent(
        overlay: Overlay = Overlay.None,
        includeCenterFeature: Boolean = false,
        recordSelection: Boolean = false,
        gestureConfig: MapGestureConfig = MapGestureConfig.Default,
    ) {
        mapTaps = 0
        overlayClicks = 0
        callbackCalls.clear()
        composeRule.setContent {
            camera = rememberMapCameraState()
            TiloMap(
                cameraState = camera,
                modifier = Modifier.size(MAP_SIZE).testTag(MAP_TAG),
                options = TiloMapOptions(gestureConfig = gestureConfig),
                onTapWorld = {
                    mapTaps += 1
                    if (recordSelection) callbackCalls += "world"
                },
                onFeatureSelect =
                    if (recordSelection) {
                        { hits -> callbackCalls += "selection:${hits.singleOrNull()?.feature?.key ?: "none"}" }
                    } else {
                        null
                    },
                cameraControlsContent = {
                    when (overlay) {
                        Overlay.None -> Unit
                        Overlay.Interactive -> {
                            Box(
                                Modifier
                                    .size(OVERLAY_SIZE)
                                    .align(Alignment.TopStart)
                                    .background(Color.Red)
                                    .clickable { overlayClicks += 1 }
                                    .testTag(OVERLAY_TAG),
                            )
                        }
                        Overlay.NonInteractive -> {
                            Box(
                                Modifier
                                    .size(OVERLAY_SIZE)
                                    .align(Alignment.TopStart)
                                    .background(Color.Red)
                                    .testTag(OVERLAY_TAG),
                            )
                        }
                    }
                },
                layers = {
                    if (includeCenterFeature) {
                        featureLayer(
                            id = "features",
                            features = listOf(Feature(key = "center", geometry = Point(0.0, 0.0))),
                        )
                    }
                },
            )
        }
    }

    private fun performFourDegreePinch() {
        composeRule.onNodeWithTag(MAP_TAG).performTouchInput {
            pinch(
                start0 = center + Offset(-20f, 0f),
                start1 = center + Offset(20f, 0f),
                end0 = center + Offset(-69.83f, -4.88f),
                end1 = center + Offset(69.83f, 4.88f),
                durationMillis = 300,
            )
        }
    }

    private fun angularDistanceFromZero(bearing: Double): Double {
        val normalized = ((bearing % 360.0) + 360.0) % 360.0
        return min(normalized, 360.0 - normalized)
    }

    private enum class Overlay {
        None,
        Interactive,
        NonInteractive,
    }

    private class MutableInvalidatingFeatureSource : FeatureSource {
        private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        @Volatile
        private var features: List<Feature> = emptyList()

        @Volatile
        override var version: Long = 0L
            private set

        @Volatile
        var queriedVersion: Long = -1L
            private set

        override val invalidations = changes

        fun update(features: List<Feature>) {
            this.features = features
            version += 1
            check(changes.tryEmit(Unit))
        }

        override fun getFeatures(map: MapState): List<Feature> {
            queriedVersion = version
            return features
        }
    }

    private class TestInvalidatingVectorLayer(
        override val source: FeatureSource,
    ) : VectorLayer {
        override val id = "mutable-features"
    }

    private companion object {
        val MAP_SIZE = 240.dp
        val OVERLAY_SIZE = 80.dp
        const val MAP_TAG = "map"
        const val OVERLAY_TAG = "overlay"
    }
}
