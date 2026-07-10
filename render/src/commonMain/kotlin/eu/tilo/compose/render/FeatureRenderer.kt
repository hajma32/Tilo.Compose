package eu.tilo.compose.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import tilo.compose.core.feature.DashPattern
import tilo.compose.core.feature.FillPattern
import tilo.compose.core.feature.FillStyle
import tilo.compose.core.feature.LineCap
import tilo.compose.core.feature.LineJoin
import tilo.compose.core.feature.PointShape
import tilo.compose.core.feature.StrokeStyle
import tilo.compose.core.map.Map
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val LABEL_VERTICAL_PADDING_PX = 8f

/**
 * Draws simple vector render commands onto the canvas.
 */
internal fun DrawScope.drawFeatures(
    commands: List<RenderCommand>,
    map: Map,
    offscreenLabelDrawScope: CanvasDrawScope,
    textMeasurer: TextMeasurer,
    labelBitmapCache: LabelBitmapCache,
) {
    commands.forEach { command ->
        when (command) {
            is RenderPoint -> drawPoint(command, map)
            is RenderLineString -> drawLineString(command, map)
            is RenderPolygon -> drawPolygon(command, map)
            is RenderLabel -> drawLabel(command, map, offscreenLabelDrawScope, textMeasurer, labelBitmapCache)
        }
    }
}

internal fun DrawScope.drawFeatureGeometry(
    commands: List<RenderCommand>,
    map: Map,
) {
    commands.forEach { command ->
        when (command) {
            is RenderPoint -> drawPoint(command, map)
            is RenderLineString -> drawLineString(command, map)
            is RenderPolygon -> drawPolygon(command, map)
            is RenderLabel -> Unit
        }
    }
}

private fun DrawScope.drawPoint(command: RenderPoint, map: Map) {
    val screenPoint = map.worldToScreen(command.point)
    val center = Offset(screenPoint.x.toFloat(), screenPoint.y.toFloat())
    val size = command.style.size.toFloat().coerceAtLeast(1f)

    // TODO: render command.style.icon once icon sources and caching are part of the public API.
    command.style.fill?.let { fill ->
        drawPointFill(shape = command.style.shape, center = center, size = size, fill = fill)
    }
    command.style.stroke?.let { stroke ->
        drawPointStroke(shape = command.style.shape, center = center, size = size, stroke = stroke)
    }
}

private fun DrawScope.drawLineString(command: RenderLineString, map: Map) {
    if (command.points.size < 2) return
    drawPath(
        path = command.points.toOpenPath(map),
        color = command.style.stroke.color.toColor(command.style.stroke.opacity),
        style = command.style.stroke.toComposeStroke()
    )
}

private fun DrawScope.drawPolygon(command: RenderPolygon, map: Map) {
    val path = command.rings.toPath(map)
    if (path.isEmpty) return

    command.style.fill?.let { fill ->
        drawPath(path = path, color = fill.color.toColor(fill.opacity))
        fill.pattern?.let { pattern ->
            drawFillPattern(pattern = pattern, path = path, bounds = command.rings.screenBounds(map))
        }
    }

    command.style.stroke?.let { stroke ->
        drawPath(
            path = path,
            color = stroke.color.toColor(stroke.opacity),
            style = stroke.toComposeStroke()
        )
    }
}

private fun DrawScope.drawPointFill(shape: PointShape, center: Offset, size: Float, fill: FillStyle) {
    val color = fill.color.toColor(fill.opacity)
    val path = pointPath(shape, center, size)
    if (path != null) {
        drawPath(path = path, color = color)
    } else {
        drawCircle(color = color, radius = size / 2f, center = center)
    }
}

private fun DrawScope.drawPointStroke(shape: PointShape, center: Offset, size: Float, stroke: StrokeStyle) {
    val style = stroke.toComposeStroke()
    val color = stroke.color.toColor(stroke.opacity)
    val path = pointPath(shape, center, size)
    if (path != null) {
        drawPath(path = path, color = color, style = style)
    } else {
        drawCircle(color = color, radius = size / 2f, center = center, style = style)
    }
}

private fun pointPath(shape: PointShape, center: Offset, size: Float): Path? {
    val half = size / 2f
    return when (shape) {
        PointShape.Circle -> null
        PointShape.Square -> Path().apply {
            moveTo(center.x - half, center.y - half)
            lineTo(center.x + half, center.y - half)
            lineTo(center.x + half, center.y + half)
            lineTo(center.x - half, center.y + half)
            close()
        }
        PointShape.Diamond -> Path().apply {
            moveTo(center.x, center.y - half)
            lineTo(center.x + half, center.y)
            lineTo(center.x, center.y + half)
            lineTo(center.x - half, center.y)
            close()
        }
        PointShape.Triangle -> Path().apply {
            moveTo(center.x, center.y - half)
            lineTo(center.x + half, center.y + half)
            lineTo(center.x - half, center.y + half)
            close()
        }
        PointShape.Cross -> Path().apply {
            val arm = size / 5f
            moveTo(center.x - arm, center.y - half)
            lineTo(center.x + arm, center.y - half)
            lineTo(center.x + arm, center.y - arm)
            lineTo(center.x + half, center.y - arm)
            lineTo(center.x + half, center.y + arm)
            lineTo(center.x + arm, center.y + arm)
            lineTo(center.x + arm, center.y + half)
            lineTo(center.x - arm, center.y + half)
            lineTo(center.x - arm, center.y + arm)
            lineTo(center.x - half, center.y + arm)
            lineTo(center.x - half, center.y - arm)
            lineTo(center.x - arm, center.y - arm)
            close()
        }
    }
}

private fun List<tilo.compose.core.geometry.Point>.toOpenPath(map: Map): Path {
    val path = Path()
    val first = map.worldToScreen(first())
    path.moveTo(first.x.toFloat(), first.y.toFloat())
    drop(1).forEach { point ->
        val screen = map.worldToScreen(point)
        path.lineTo(screen.x.toFloat(), screen.y.toFloat())
    }
    return path
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

private fun DrawScope.drawFillPattern(
    pattern: FillPattern,
    path: Path,
    bounds: ScreenBounds,
) {
    clipPath(path) {
        when (pattern) {
            is FillPattern.Hatch -> drawHatchPattern(pattern, bounds)
            is FillPattern.Dots -> drawDotsPattern(pattern, bounds)
        }
    }
}

private fun DrawScope.drawHatchPattern(pattern: FillPattern.Hatch, bounds: ScreenBounds) {
    val spacing = pattern.spacing.toFloat().coerceAtLeast(1f)
    val angle = (pattern.angleDegrees / 180.0 * PI).toFloat()
    val dx = cos(angle)
    val dy = sin(angle)
    val nx = -dy
    val ny = dx
    val diagonal = sqrt(bounds.width * bounds.width + bounds.height * bounds.height)
    var offset = -diagonal
    while (offset <= diagonal) {
        val cx = bounds.center.x + nx * offset
        val cy = bounds.center.y + ny * offset
        drawLine(
            color = pattern.stroke.color.toColor(pattern.stroke.opacity),
            start = Offset(cx - dx * diagonal, cy - dy * diagonal),
            end = Offset(cx + dx * diagonal, cy + dy * diagonal),
            strokeWidth = pattern.stroke.width.toFloat(),
            cap = pattern.stroke.lineCap.toComposeCap(),
            pathEffect = pattern.stroke.dash.toPathEffect(),
        )
        offset += spacing
    }
}

private fun DrawScope.drawDotsPattern(pattern: FillPattern.Dots, bounds: ScreenBounds) {
    val spacing = pattern.spacing.toFloat().coerceAtLeast(1f)
    var y = bounds.top
    while (y <= bounds.bottom) {
        var x = bounds.left
        while (x <= bounds.right) {
            drawCircle(
                color = pattern.color.toColor(),
                radius = pattern.radius.toFloat(),
                center = Offset(x, y)
            )
            x += spacing
        }
        y += spacing
    }
}

private fun StrokeStyle.toComposeStroke(): Stroke =
    Stroke(
        width = width.toFloat(),
        cap = lineCap.toComposeCap(),
        join = lineJoin.toComposeJoin(),
        pathEffect = dash.toPathEffect(),
    )

private fun LineCap.toComposeCap(): StrokeCap =
    when (this) {
        LineCap.Butt -> StrokeCap.Butt
        LineCap.Round -> StrokeCap.Round
        LineCap.Square -> StrokeCap.Square
    }

private fun LineJoin.toComposeJoin(): StrokeJoin =
    when (this) {
        LineJoin.Miter -> StrokeJoin.Miter
        LineJoin.Round -> StrokeJoin.Round
        LineJoin.Bevel -> StrokeJoin.Bevel
    }

private fun DashPattern?.toPathEffect(): PathEffect? {
    this ?: return null
    val validIntervals = intervals.map { it.toFloat().coerceAtLeast(0.1f) }
    if (validIntervals.size < 2) return null
    return PathEffect.dashPathEffect(validIntervals.toFloatArray(), phase.toFloat())
}

private fun List<List<tilo.compose.core.geometry.Point>>.screenBounds(map: Map): ScreenBounds {
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    forEach { ring ->
        ring.forEach { point ->
            val screen = map.worldToScreen(point)
            left = min(left, screen.x.toFloat())
            top = min(top, screen.y.toFloat())
            right = max(right, screen.x.toFloat())
            bottom = max(bottom, screen.y.toFloat())
        }
    }
    return ScreenBounds(left = left, top = top, right = right, bottom = bottom)
}

private data class ScreenBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float = right - left
    val height: Float = bottom - top
    val center: Offset = Offset((left + right) / 2f, (top + bottom) / 2f)
}

private fun DrawScope.drawLabel(
    command: RenderLabel,
    map: Map,
    offscreenDrawScope: CanvasDrawScope,
    textMeasurer: TextMeasurer,
    labelBitmapCache: LabelBitmapCache,
) {
    val anchor = map.worldToScreen(command.anchor)
    val bitmap = this.cachedLabelBitmap(
        text = command.text,
        textColor = command.textColor.toColor(),
        textMeasurer = textMeasurer,
        offscreenDrawScope = offscreenDrawScope,
        cache = labelBitmapCache,
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
