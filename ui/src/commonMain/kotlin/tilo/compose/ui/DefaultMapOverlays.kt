package tilo.compose.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import tilo.compose.core.scale.ScaleBar
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.MapCameraState

/** Returns the default content slot for displaying the current map scale. */
fun defaultScaleBarContent(
    accessibility: MapUiAccessibility = MapUiAccessibility(),
): @Composable BoxScope.(ScaleBar) -> Unit = { scaleBar -> DefaultScaleBar(scaleBar, accessibility) }

/** Returns animated zoom controls for the camera slot in `TiloMap`. */
@ExperimentalTiloApi
fun defaultZoomControlsContent(
    style: CameraControlsStyle = CameraControlsStyle(),
    accessibility: MapUiAccessibility = MapUiAccessibility(),
): @Composable BoxScope.(MapCameraState) -> Unit =
    { cameraState -> DefaultZoomControls(cameraState, style = style, accessibility = accessibility) }

/** Default zoom and north-reset controls for the camera slot in `TiloMap`. */
@ExperimentalTiloApi
fun defaultCameraControlsContent(
    style: CameraControlsStyle = CameraControlsStyle(),
    accessibility: MapUiAccessibility = MapUiAccessibility(),
): @Composable BoxScope.(MapCameraState) -> Unit =
    { cameraState ->
        DefaultZoomControls(cameraState, style = style, accessibility = accessibility)
        DefaultCompassControl(cameraState, style = style, accessibility = accessibility)
    }
