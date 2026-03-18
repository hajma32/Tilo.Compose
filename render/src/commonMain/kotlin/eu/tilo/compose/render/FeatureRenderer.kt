package eu.tilo.compose.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

private const val LABEL_VERTICAL_PADDING_PX = 8f

/**
 * Draws all retained [RenderCommand]s onto the canvas.
 */
internal fun DrawScope.drawFeatures(
    retained: Map<String, RenderCommand>,
    labelBitmapCache: MutableMap<String, ImageBitmap>,
    offscreenLabelDrawScope: CanvasDrawScope,
    textMeasurer: TextMeasurer
) {
    retained.values.forEach { command ->
        when (command) {
            is RenderPoint -> drawPoint(command)
            is RenderLineString -> drawLineString(command)
            is RenderPolygon -> drawPolygon(command)
            is RenderLabel -> drawLabel(command, labelBitmapCache, offscreenLabelDrawScope, textMeasurer)
        }
    }
}

private fun DrawScope.drawPoint(command: RenderPoint) {
    val fill = command.style.fillColor?.toColor() ?: return
    drawCircle(
        color = fill,
        radius = command.radius.toFloat(),
        center = Offset(command.point.x.toFloat(), command.point.y.toFloat())
    )
}

private fun DrawScope.drawLineString(command: RenderLineString) {
    if (command.points.size < 2) return
    val stroke = command.style.strokeColor?.toColor() ?: Color(0xFF1E88E5)
    val width = (command.style.strokeWidth ?: 2.0).toFloat()
    command.points.zipWithNext { a, b ->
        drawLine(
            color = stroke,
            start = Offset(a.x.toFloat(), a.y.toFloat()),
            end = Offset(b.x.toFloat(), b.y.toFloat()),
            strokeWidth = width
        )
    }
}

private fun DrawScope.drawPolygon(command: RenderPolygon) {
    val fill = command.style.fillColor?.toColor() ?: Color(0x331E88E5)
    val stroke = command.style.strokeColor?.toColor() ?: Color(0xFF1E88E5)
    val width = (command.style.strokeWidth ?: 1.5).toFloat()

    val path = Path()
    command.rings.forEach { ring ->
        if (ring.isNotEmpty()) {
            path.moveTo(ring.first().x.toFloat(), ring.first().y.toFloat())
            ring.drop(1).forEach { p -> path.lineTo(p.x.toFloat(), p.y.toFloat()) }
            path.close()
        }
    }
    drawPath(path = path, color = fill)
    drawPath(path = path, color = stroke, style = Stroke(width))
}

private fun DrawScope.drawLabel(
    command: RenderLabel,
    cache: MutableMap<String, ImageBitmap>,
    offscreenDrawScope: CanvasDrawScope,
    textMeasurer: TextMeasurer
) {
    val labelColor = command.style.strokeColor?.toColor() ?: Color(0xFF111827)
    val bitmap = getOrCreateLabelBitmap(
        text = command.text,
        textColor = labelColor,
        textMeasurer = textMeasurer,
        cache = cache,
        offscreenDrawScope = offscreenDrawScope
    )
    drawImage(
        image = bitmap,
        dstOffset = IntOffset(
            x = (command.anchor.x.toFloat() - bitmap.width / 2f).toInt(),
            y = (command.anchor.y.toFloat() + LABEL_VERTICAL_PADDING_PX).toInt()
        ),
        dstSize = IntSize(bitmap.width, bitmap.height)
    )
}


