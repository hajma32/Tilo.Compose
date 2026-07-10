package eu.tilo.compose.render

import androidx.compose.ui.graphics.Color
import tilo.compose.core.feature.ColorValue

internal fun Long.toColor(): Color = Color((this and 0xFFFFFFFFL).toInt())

internal fun ColorValue.toColor(opacity: Double = 1.0): Color {
    val color = Color((argb.toLong() and 0xFFFFFFFFL).toInt())
    return color.copy(alpha = (color.alpha * opacity.toFloat()).coerceIn(0f, 1f))
}
