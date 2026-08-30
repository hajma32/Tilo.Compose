@file:OptIn(tilo.compose.dsl.ExperimentalTiloApi::class)

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.MapCameraState
import tilo.compose.dsl.tiloMapFocusTarget
import tilo.compose.ui.generated.resources.Res
import tilo.compose.ui.generated.resources.ic_add_24
import tilo.compose.ui.generated.resources.ic_remove_24
import tilo.compose.ui.generated.resources.zoom_in
import tilo.compose.ui.generated.resources.zoom_out

/** Default animated zoom controls for a Tilo map camera. */
@Composable
@ExperimentalTiloApi
fun BoxScope.DefaultZoomControls(
    cameraState: MapCameraState,
    zoomStep: Double = 1.0,
    style: CameraControlsStyle = CameraControlsStyle(),
    accessibility: MapUiAccessibility = MapUiAccessibility(),
) {
    DefaultZoomControls(
        zoomStep = zoomStep,
        onZoomBy = { delta -> cameraState.animateZoomBy(delta = delta) },
        style = style,
        accessibility = accessibility,
        canZoomIn = cameraState.zoom < cameraState.config.maxZoom,
        canZoomOut = cameraState.zoom > cameraState.config.minZoom,
    )
}

/**
 * Draws zoom buttons backed by an application-provided suspending operation.
 *
 * Starting a new zoom cancels the previous operation, making this overload suitable for
 * animated camera implementations as well as immediate callbacks.
 */
@Composable
fun BoxScope.DefaultZoomControls(
    zoomStep: Double = 1.0,
    onZoomBy: suspend (delta: Double) -> Unit,
    style: CameraControlsStyle = CameraControlsStyle(),
    accessibility: MapUiAccessibility = MapUiAccessibility(),
    canZoomIn: Boolean = true,
    canZoomOut: Boolean = true,
) {
    val coroutineScope = rememberCoroutineScope()
    val resolvedStyle = style.resolve()
    var zoomAnimationJob by remember { mutableStateOf<Job?>(null) }
    val zoomInDescription = accessibility.zoomInDescription ?: stringResource(Res.string.zoom_in)
    val zoomOutDescription = accessibility.zoomOutDescription ?: stringResource(Res.string.zoom_out)

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
            contentDescription = zoomInDescription,
            onClick = { animateZoom(zoomStep) },
            enabled = canZoomIn,
            traversalIndex = ZOOM_IN_TRAVERSAL_INDEX,
            style = resolvedStyle,
        )
        CameraButton(
            icon = Res.drawable.ic_remove_24,
            contentDescription = zoomOutDescription,
            onClick = { animateZoom(-zoomStep) },
            enabled = canZoomOut,
            traversalIndex = ZOOM_OUT_TRAVERSAL_INDEX,
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
    enabled: Boolean,
    traversalIndex: Float,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    style: ResolvedCameraControlsStyle,
    iconTint: Color? = style.contentColor,
) {
    CameraButtonSurface(
        onClick = onClick,
        contentDescription = contentDescription,
        enabled = enabled,
        traversalIndex = traversalIndex,
        modifier = modifier,
        style = style,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = iconTint?.let(ColorFilter::tint),
            modifier = iconModifier.size(style.iconSize),
        )
    }
}

@Composable
internal fun CameraButtonSurface(
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
    traversalIndex: Float,
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
                .tiloMapFocusTarget(traversalIndex)
                .clickable(
                    enabled = enabled,
                    onClickLabel = contentDescription,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics(mergeDescendants = true) {
                    this.contentDescription = contentDescription
                    role = Role.Button
                },
        contentAlignment = Alignment.Center,
        content = content,
    )
}
