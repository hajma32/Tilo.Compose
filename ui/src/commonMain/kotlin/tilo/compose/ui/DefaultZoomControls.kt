package tilo.compose.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import tilo.compose.core.map.MapCameraController
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.MapCameraState
import tilo.compose.ui.generated.resources.Res
import tilo.compose.ui.generated.resources.ic_add_24
import tilo.compose.ui.generated.resources.ic_remove_24

/** Default animated zoom controls for a Tilo map camera. */
@Composable
@ExperimentalTiloApi
fun BoxScope.DefaultZoomControls(
    cameraState: MapCameraState,
    zoomStep: Double = 1.0,
    style: CameraControlsStyle = CameraControlsStyle(),
) {
    DefaultZoomControls(
        zoomStep = zoomStep,
        onZoomBy = { delta -> cameraState.animateZoomBy(delta = delta) },
        style = style,
    )
}

/**
 * Compatibility overload for camera implementations that only expose immediate zoom operations.
 * Prefer the [MapCameraState] overload to get animation automatically.
 */
@Composable
fun BoxScope.DefaultZoomControls(
    cameraState: MapCameraController,
    zoomStep: Double = 1.0,
    style: CameraControlsStyle = CameraControlsStyle(),
) {
    DefaultZoomControls(
        zoomStep = zoomStep,
        onZoomBy = { delta -> cameraState.zoomBy(delta, focus = null) },
        style = style,
    )
}

@Composable
fun BoxScope.DefaultZoomControls(
    zoomStep: Double = 1.0,
    onZoomBy: suspend (delta: Double) -> Unit,
    style: CameraControlsStyle = CameraControlsStyle(),
) {
    val coroutineScope = rememberCoroutineScope()
    val resolvedStyle = style.resolve()
    var zoomAnimationJob by remember { mutableStateOf<Job?>(null) }

    fun animateZoom(delta: Double) {
        zoomAnimationJob?.cancel()
        zoomAnimationJob =
            coroutineScope.launch {
                onZoomBy(delta)
            }
    }

    Column(
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End),
                ).padding(style.contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CameraButton(
            icon = Res.drawable.ic_add_24,
            contentDescription = "Zoom in",
            onClick = { animateZoom(zoomStep) },
            style = resolvedStyle,
        )
        CameraButton(
            icon = Res.drawable.ic_remove_24,
            contentDescription = "Zoom out",
            onClick = { animateZoom(-zoomStep) },
            modifier = Modifier.padding(top = style.spacing),
            style = resolvedStyle,
        )
    }
}

@Composable
internal fun CameraButton(
    icon: DrawableResource,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    style: ResolvedCameraControlsStyle,
    iconTint: Color? = style.contentColor,
) {
    CameraButtonSurface(
        onClick = onClick,
        modifier = modifier,
        style = style,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            colorFilter = iconTint?.let(ColorFilter::tint),
            modifier = iconModifier.size(style.iconSize),
        )
    }
}

@Composable
internal fun CameraButtonSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ResolvedCameraControlsStyle,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .size(style.buttonSize)
                .shadow(elevation = style.shadowElevation, shape = CircleShape)
                .background(style.containerColor, CircleShape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
