package tilo.compose.render

import androidx.compose.ui.graphics.painter.Painter

internal interface PointIconPainterLayer {
    val pointIconPainters: Map<String, Painter>
}
