package tilo.compose.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import tilo.compose.core.layers.Attribution
import tilo.compose.core.scale.ScaleBar
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.MapCameraState

/** Returns the default content slot for displaying active layer attributions. */
fun defaultAttributionContent(): @Composable BoxScope.(List<Attribution>) -> Unit =
    defaultAttributionContent(MapUiAccessibility())

/** Returns the attribution slot with configurable accessibility text. */
fun defaultAttributionContent(accessibility: MapUiAccessibility): @Composable BoxScope.(List<Attribution>) -> Unit =
    { attributions -> DefaultAttributionOverlay(attributions, accessibility) }

/** Returns the default content slot for displaying the current map scale. */
fun defaultScaleBarContent(): @Composable BoxScope.(ScaleBar) -> Unit = defaultScaleBarContent(MapUiAccessibility())

/** Returns the scale-bar slot with configurable accessibility text. */
fun defaultScaleBarContent(accessibility: MapUiAccessibility): @Composable BoxScope.(ScaleBar) -> Unit =
    { scaleBar -> DefaultScaleBar(scaleBar, accessibility) }

/** Returns animated zoom controls for the camera slot in `TiloMap`. */
@ExperimentalTiloApi
fun defaultZoomControlsContent(
    style: CameraControlsStyle = CameraControlsStyle(),
): @Composable BoxScope.(MapCameraState) -> Unit = defaultZoomControlsContent(style, MapUiAccessibility())

/** Returns animated zoom controls with configurable accessibility text. */
@ExperimentalTiloApi
fun defaultZoomControlsContent(
    style: CameraControlsStyle = CameraControlsStyle(),
    accessibility: MapUiAccessibility,
): @Composable BoxScope.(MapCameraState) -> Unit =
    { cameraState -> DefaultZoomControls(cameraState, style = style, accessibility = accessibility) }

/** Default zoom and north-reset controls for the camera slot in `TiloMap`. */
@ExperimentalTiloApi
fun defaultCameraControlsContent(
    style: CameraControlsStyle = CameraControlsStyle(),
): @Composable BoxScope.(MapCameraState) -> Unit = defaultCameraControlsContent(style, MapUiAccessibility())

/** Default zoom and north-reset controls with configurable accessibility text. */
@ExperimentalTiloApi
fun defaultCameraControlsContent(
    style: CameraControlsStyle = CameraControlsStyle(),
    accessibility: MapUiAccessibility,
): @Composable BoxScope.(MapCameraState) -> Unit =
    { cameraState ->
        DefaultZoomControls(cameraState, style = style, accessibility = accessibility)
        DefaultCompassControl(cameraState, style = style, accessibility = accessibility)
    }
