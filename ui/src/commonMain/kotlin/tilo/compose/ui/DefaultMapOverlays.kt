package tilo.compose.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import tilo.compose.core.layers.Attribution
import tilo.compose.core.map.MapCameraController
import tilo.compose.core.scale.ScaleBar
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.MapCameraState

fun defaultAttributionContent(): @Composable BoxScope.(List<Attribution>) -> Unit =
    { attributions -> DefaultAttributionOverlay(attributions) }

fun defaultScaleBarContent(): @Composable BoxScope.(ScaleBar) -> Unit = { scaleBar -> DefaultScaleBar(scaleBar) }

fun defaultZoomControlsContent(): @Composable BoxScope.(MapCameraController) -> Unit =
    { cameraState -> DefaultZoomControls(cameraState) }

/** Default zoom and north-reset controls for the camera slot in `TiloMap`. */
@ExperimentalTiloApi
fun defaultCameraControlsContent(): @Composable BoxScope.(MapCameraState) -> Unit =
    { cameraState ->
        DefaultZoomControls(cameraState)
        DefaultCompassControl(cameraState)
    }
