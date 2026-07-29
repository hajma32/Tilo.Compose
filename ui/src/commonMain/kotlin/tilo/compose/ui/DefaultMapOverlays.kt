package tilo.compose.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import tilo.compose.core.layers.Attribution
import tilo.compose.core.scale.ScaleBar
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.MapCameraState

/** Returns the default content slot for displaying active layer attributions. */
fun defaultAttributionContent(): @Composable BoxScope.(List<Attribution>) -> Unit =
    { attributions -> DefaultAttributionOverlay(attributions) }

/** Returns the default content slot for displaying the current map scale. */
fun defaultScaleBarContent(): @Composable BoxScope.(ScaleBar) -> Unit = { scaleBar -> DefaultScaleBar(scaleBar) }

/** Returns animated zoom controls for the camera slot in `TiloMap`. */
@ExperimentalTiloApi
fun defaultZoomControlsContent(
    style: CameraControlsStyle = CameraControlsStyle(),
): @Composable BoxScope.(MapCameraState) -> Unit = { cameraState -> DefaultZoomControls(cameraState, style = style) }

/** Default zoom and north-reset controls for the camera slot in `TiloMap`. */
@ExperimentalTiloApi
fun defaultCameraControlsContent(
    style: CameraControlsStyle = CameraControlsStyle(),
): @Composable BoxScope.(MapCameraState) -> Unit =
    { cameraState ->
        DefaultZoomControls(cameraState, style = style)
        DefaultCompassControl(cameraState, style = style)
    }
