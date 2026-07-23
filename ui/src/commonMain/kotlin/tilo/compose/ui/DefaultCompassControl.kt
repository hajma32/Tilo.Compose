package tilo.compose.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.MapCameraState
import tilo.compose.ui.generated.resources.Res
import tilo.compose.ui.generated.resources.ic_navigation_24

/** Displays current north orientation and animates the camera back to north when pressed. */
@Composable
@ExperimentalTiloApi
fun BoxScope.DefaultCompassControl(cameraState: MapCameraState) {
    val coroutineScope = rememberCoroutineScope()
    var animationJob by remember { mutableStateOf<Job?>(null) }

    CameraButton(
        icon = Res.drawable.ic_navigation_24,
        contentDescription = "Reset map rotation to north",
        onClick = {
            animationJob?.cancel()
            animationJob = coroutineScope.launch { cameraState.animateBearingTo(0.0) }
        },
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 120.dp, end = 12.dp),
        iconModifier = Modifier.rotate(-cameraState.bearing.toFloat()),
        colorFilter = null,
    )
}
