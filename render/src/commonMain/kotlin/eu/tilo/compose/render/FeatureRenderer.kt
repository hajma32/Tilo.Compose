package eu.tilo.compose.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import tilo.compose.core.map.Map

private const val LABEL_VERTICAL_PADDING_PX = 8f

/**
 * Draws simple vector render commands onto the canvas.
 */
internal fun DrawScope.drawFeatures(
    commands: List<RenderCommand>,
    map: Map,
    offscreenLabelDrawScope: CanvasDrawScope,
    textMeasurer: TextMeasurer
) {
    commands.forEach { command ->
        when (command) {
            is RenderPoint -> drawPoint(command, map)
            is RenderLineString -> drawLineString(command, map)
            is RenderPolygon -> drawPolygon(command, map)
            is RenderLabel -> drawLabel(command, map, offscreenLabelDrawScope, textMeasurer)
        }
    }
}

private fun DrawScope.drawPoint(command: RenderPoint, map: Map) {
    val screenPoint = map.worldToScreen(command.point)
    val fill = command.style.fillColor?.toColor() ?: return
    drawCircle(
        color = fill,
        radius = command.radius.toFloat(),
        center = Offset(screenPoint.x.toFloat(), screenPoint.y.toFloat())
    )
}

private fun DrawScope.drawLineString(command: RenderLineString, map: Map) {
    if (command.points.size < 2) return
    val stroke = command.style.strokeColor?.toColor() ?: return
    command.points.zipWithNext { a, b ->
        val start = map.worldToScreen(a)
        val end = map.worldToScreen(b)
        drawLine(
            color = stroke,
            start = Offset(start.x.toFloat(), start.y.toFloat()),
            end = Offset(end.x.toFloat(), end.y.toFloat()),
            strokeWidth = command.style.strokeWidth?.toFloat() ?: 2f
        )
    }
}

private fun DrawScope.drawPolygon(command: RenderPolygon, map: Map) {
    command.style.fillColor?.toColor()?.let { fill ->
        val path = command.rings.toPath(map)
        if (!path.isEmpty) {
            drawPath(path = path, color = fill)
        }
    }

    command.style.strokeColor?.toColor()?.let {
        command.rings.forEach { ring ->
            drawLineString(
                RenderLineString(
                    id = "${command.id}:ring",
                    points = ring,
                    style = command.style
                ),
                map
            )
        }
    }
}

private fun List<List<tilo.compose.core.geometry.Point>>.toPath(map: Map): Path {
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
    }
    forEach { ring ->
        if (ring.isEmpty()) return@forEach
        val first = map.worldToScreen(ring.first())
        path.moveTo(first.x.toFloat(), first.y.toFloat())
        ring.drop(1).forEach { point ->
            val screen = map.worldToScreen(point)
            path.lineTo(screen.x.toFloat(), screen.y.toFloat())
        }
        path.close()
    }
    return path
}

private fun DrawScope.drawLabel(
    command: RenderLabel,
    map: Map,
    offscreenDrawScope: CanvasDrawScope,
    textMeasurer: TextMeasurer
) {
    val anchor = map.worldToScreen(command.anchor)
    val labelColor = command.style.strokeColor?.toColor() ?: Color(0xFF111827)
    val bitmap = this.createLabelBitmap(
        text = command.text,
        textColor = labelColor,
        textMeasurer = textMeasurer,
        offscreenDrawScope = offscreenDrawScope
    )
    drawImage(
        image = bitmap,
        dstOffset = IntOffset(
            x = (anchor.x.toFloat() - bitmap.width / 2f).toInt(),
            y = (anchor.y.toFloat() + LABEL_VERTICAL_PADDING_PX).toInt()
        ),
        dstSize = IntSize(bitmap.width, bitmap.height)
    )
}
