package tilo.compose.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Visual and layout configuration for the default camera controls. */
@Immutable
data class CameraControlsStyle(
    val contentPadding: PaddingValues = PaddingValues(top = 16.dp, end = 12.dp),
    val containerColor: Color = Color.Unspecified,
    val contentColor: Color = Color.Unspecified,
    val buttonSize: Dp = 44.dp,
    val iconSize: Dp = 24.dp,
    val spacing: Dp = 8.dp,
    val shadowElevation: Dp = 4.dp,
) {
    init {
        require(buttonSize > 0.dp) { "buttonSize must be positive" }
        require(iconSize > 0.dp) { "iconSize must be positive" }
        require(spacing >= 0.dp) { "spacing must not be negative" }
        require(shadowElevation >= 0.dp) { "shadowElevation must not be negative" }
    }
}

@Immutable
internal data class ResolvedCameraControlsStyle(
    val containerColor: Color,
    val contentColor: Color,
    val buttonSize: Dp,
    val iconSize: Dp,
    val shadowElevation: Dp,
)

@Composable
internal fun CameraControlsStyle.resolve(): ResolvedCameraControlsStyle =
    ResolvedCameraControlsStyle(
        containerColor = containerColor.orThemeFallback(MaterialTheme.colorScheme.surface),
        contentColor = contentColor.orThemeFallback(MaterialTheme.colorScheme.onSurface),
        buttonSize = buttonSize,
        iconSize = iconSize,
        shadowElevation = shadowElevation,
    )

private fun Color.orThemeFallback(fallback: Color): Color = if (this == Color.Unspecified) fallback else this
