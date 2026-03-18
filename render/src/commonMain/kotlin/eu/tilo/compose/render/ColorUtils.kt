package eu.tilo.compose.render

import androidx.compose.ui.graphics.Color

internal fun Long.toColor(): Color = Color((this and 0xFFFFFFFFL).toInt())

