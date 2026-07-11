package tilo.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import tilo.compose.dsl.MapCameraState
import tilocompose.ui.generated.resources.Res
import tilocompose.ui.generated.resources.ic_add_24
import tilocompose.ui.generated.resources.ic_remove_24

@Composable
fun BoxScope.DefaultZoomControls(
    cameraState: MapCameraState,
    zoomStep: Double = 1.0,
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 16.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ZoomButton(
            icon = Res.drawable.ic_add_24,
            contentDescription = "Zoom in",
            onClick = { cameraState.zoomIn(zoomStep) },
        )
        ZoomButton(
            icon = Res.drawable.ic_remove_24,
            contentDescription = "Zoom out",
            onClick = { cameraState.zoomOut(zoomStep) },
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ZoomButton(
    icon: DrawableResource,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .shadow(elevation = 4.dp, shape = CircleShape)
            .background(Color.White, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(ZoomButtonIconColor),
            modifier = Modifier.size(24.dp),
        )
    }
}

private val ZoomButtonIconColor = Color(0xFF111827)
