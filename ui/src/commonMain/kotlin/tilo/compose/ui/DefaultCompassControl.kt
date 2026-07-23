package tilo.compose.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.MapCameraState

/** Displays current north orientation and animates the camera back to north when pressed. */
@Composable
@ExperimentalTiloApi
fun BoxScope.DefaultCompassControl(
    cameraState: MapCameraState,
    style: CameraControlsStyle = CameraControlsStyle(),
) {
    val coroutineScope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current
    val resolvedStyle = style.resolve()
    var animationJob by remember { mutableStateOf<Job?>(null) }
    val topPadding = style.contentPadding.calculateTopPadding() + style.buttonSize * 2 + style.spacing * 2
    val endPadding =
        when (layoutDirection) {
            LayoutDirection.Ltr -> style.contentPadding.calculateRightPadding(layoutDirection)
            LayoutDirection.Rtl -> style.contentPadding.calculateLeftPadding(layoutDirection)
        }

    CameraButtonSurface(
        onClick = {
            animationJob?.cancel()
            animationJob = coroutineScope.launch { cameraState.animateBearingTo(0.0) }
        },
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End),
                ).padding(top = topPadding, end = endPadding),
        style = resolvedStyle,
    ) {
        val colors = compassNeedleColors(resolvedStyle)
        Canvas(
            modifier =
                Modifier
                    .size(resolvedStyle.iconSize)
                    .rotate(-cameraState.bearing.toFloat())
                    .semantics { contentDescription = "Reset map rotation to north" },
        ) {
            drawCompassNeedle(colors)
        }
    }
}

internal data class CompassNeedleColors(
    val north: Color,
    val south: Color,
)

internal fun compassNeedleColors(style: ResolvedCameraControlsStyle): CompassNeedleColors =
    CompassNeedleColors(
        north = CompassNorthColor,
        south = style.contentColor,
    )

private fun DrawScope.drawCompassNeedle(colors: CompassNeedleColors) {
    val center = Offset(size.width / 2.0f, size.height / 2.0f)
    val halfNeedleWidth = size.width * 5.0f / 24.0f
    drawPath(
        path =
            Path().apply {
                moveTo(center.x, 0.0f)
                lineTo(center.x - halfNeedleWidth, center.y)
                lineTo(center.x + halfNeedleWidth, center.y)
                close()
            },
        color = colors.north,
    )
    drawPath(
        path =
            Path().apply {
                moveTo(center.x, size.height)
                lineTo(center.x - halfNeedleWidth, center.y)
                lineTo(center.x + halfNeedleWidth, center.y)
                close()
            },
        color = colors.south,
    )
}

private val CompassNorthColor = Color(0xFFD32F2F)
