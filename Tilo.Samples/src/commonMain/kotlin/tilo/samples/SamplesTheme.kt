package tilo.samples

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val Ink = Color(0xFF17201C)
internal val Paper = Color(0xFFF6F5F0)
internal val PaperRaised = Color(0xFFEFEEE8)
internal val Orange = Color(0xFFF2663B)
internal val Green = Color(0xFFBFED6F)

private val LightColors =
    lightColorScheme(
        primary = Orange,
        onPrimary = Color.White,
        secondary = Green,
        background = Paper,
        onBackground = Ink,
        surface = Paper,
        onSurface = Ink,
        surfaceContainer = Color(0xFFE5E3DA),
        surfaceContainerHighest = Color(0xFFCFCDC2),
        onSurfaceVariant = Color(0xFF66706A),
        outline = Color(0xFF8B918D),
        outlineVariant = Color(0xFFD3D2C9),
    )

private val DarkColors =
    darkColorScheme(
        primary = Orange,
        onPrimary = Color.White,
        secondary = Green,
        background = Color(0xFF111713),
        onBackground = Color(0xFFE7ECE9),
        surface = Color(0xFF151D18),
        onSurface = Color(0xFFE7ECE9),
        surfaceContainer = Color(0xFF1A231E),
        surfaceContainerHighest = Color(0xFF2B3730),
        onSurfaceVariant = Color(0xFFA3AEA8),
        outline = Color(0xFF526159),
        outlineVariant = Color(0xFF344039),
    )

@Composable
internal fun TiloSamplesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
