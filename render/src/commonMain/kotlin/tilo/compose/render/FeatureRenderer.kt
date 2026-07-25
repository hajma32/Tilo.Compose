@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import tilo.compose.core.feature.CasingStyle
import tilo.compose.core.feature.DashPattern
import tilo.compose.core.feature.FillPattern
import tilo.compose.core.feature.FillStyle
import tilo.compose.core.feature.LineCap
import tilo.compose.core.feature.LineJoin
import tilo.compose.core.feature.PointShape
import tilo.compose.core.feature.StrokeStyle
import tilo.compose.core.map.MapState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws vector geometry commands onto the canvas. Labels are handled by
 * [LabelLayoutEngine] after all scene layers have contributed their labels.
 */
internal fun DrawScope.drawFeatureGeometry(
    commands: List<RenderCommand>,
    map: MapState,
    pointIconPainters: Map<String, Painter> = emptyMap(),
) {
    val worldToScreen = WorldToScreenTransform.from(map)
    for (command in commands) {
        when (command) {
            is RenderPoint -> drawPoint(command, worldToScreen, pointIconPainters)
            is RenderLineString -> drawLineString(command, worldToScreen)
            is RenderPolygon -> drawPolygon(command, worldToScreen)
            is RenderLabel -> Unit
        }
    }
}

private fun DrawScope.drawPoint(
    command: RenderPoint,
    worldToScreen: WorldToScreenTransform,
    pointIconPainters: Map<String, Painter>,
) {
    val center =
        Offset(
            worldToScreen.screenX(command.point.x, command.point.y).toFloat(),
            worldToScreen.screenY(command.point.x, command.point.y).toFloat(),
        )
    val size = styleUnitToPx(command.style.size).coerceAtLeast(1f)

    command.style.fill?.let { fill ->
        drawPointFill(shape = command.style.shape, center = center, size = size, fill = fill)
    }
    command.style.stroke?.let { stroke ->
        drawPointStroke(shape = command.style.shape, center = center, size = size, stroke = stroke)
    }
    command.style.icon?.let { icon ->
        val painter =
            checkNotNull(pointIconPainters[icon.id]) {
                "Point icon '${icon.id}' is not registered on the feature layer"
            }
        val iconSize = styleUnitToPx(icon.size).coerceAtLeast(1f)
        translate(left = center.x - iconSize / 2f, top = center.y - iconSize / 2f) {
            with(painter) {
                draw(
                    size = Size(iconSize, iconSize),
                    alpha = icon.opacity.toFloat(),
                    colorFilter = icon.tint?.let { ColorFilter.tint(it.toColor()) },
                )
            }
        }
    }
}

private fun DrawScope.drawLineString(
    command: RenderLineString,
    worldToScreen: WorldToScreenTransform,
) {
    if (command.points.size < 2) return
    val path = command.points.toOpenPath(worldToScreen)
    command.style.casing?.let { casing ->
        drawPath(
            path = path,
            color = casing.color.toColor(casing.opacity),
            style = toComposeStroke(casing, command.style.stroke.width),
        )
    }
    drawPath(
        path = path,
        color =
            command.style.stroke.color
                .toColor(command.style.stroke.opacity),
        style = toComposeStroke(command.style.stroke),
    )
}

private fun DrawScope.drawPolygon(
    command: RenderPolygon,
    worldToScreen: WorldToScreenTransform,
) {
    val path = command.rings.toPath(worldToScreen)
    if (path.isEmpty) return

    command.style.fill?.let { fill ->
        drawPath(path = path, color = fill.color.toColor(fill.opacity))
        fill.pattern?.let { pattern ->
            drawFillPattern(pattern = pattern, path = path, bounds = command.rings.screenBounds(worldToScreen))
        }
    }

    command.style.casing?.let { casing ->
        drawPath(
            path = path,
            color = casing.color.toColor(casing.opacity),
            style = toComposeStroke(casing, command.style.stroke?.width ?: 0.0),
        )
    }

    command.style.stroke?.let { stroke ->
        drawPath(
            path = path,
            color = stroke.color.toColor(stroke.opacity),
            style = toComposeStroke(stroke),
        )
    }
}

private fun DrawScope.drawPointFill(
    shape: PointShape,
    center: Offset,
    size: Float,
    fill: FillStyle,
) {
    val color = fill.color.toColor(fill.opacity)
    val path = pointPath(shape, center, size)
    if (path != null) {
        drawPath(path = path, color = color)
    } else {
        drawCircle(color = color, radius = size / 2f, center = center)
    }
}

private fun DrawScope.drawPointStroke(
    shape: PointShape,
    center: Offset,
    size: Float,
    stroke: StrokeStyle,
) {
    val style = toComposeStroke(stroke)
    val color = stroke.color.toColor(stroke.opacity)
    val path = pointPath(shape, center, size)
    if (path != null) {
        drawPath(path = path, color = color, style = style)
    } else {
        drawCircle(color = color, radius = size / 2f, center = center, style = style)
    }
}

private fun pointPath(
    shape: PointShape,
    center: Offset,
    size: Float,
): Path? {
    val half = size / 2f
    return when (shape) {
        PointShape.Circle -> null
        PointShape.Square ->
            Path().apply {
                moveTo(center.x - half, center.y - half)
                lineTo(center.x + half, center.y - half)
                lineTo(center.x + half, center.y + half)
                lineTo(center.x - half, center.y + half)
                close()
            }
        PointShape.Diamond ->
            Path().apply {
                moveTo(center.x, center.y - half)
                lineTo(center.x + half, center.y)
                lineTo(center.x, center.y + half)
                lineTo(center.x - half, center.y)
                close()
            }
        PointShape.Triangle ->
            Path().apply {
                moveTo(center.x, center.y - half)
                lineTo(center.x + half, center.y + half)
                lineTo(center.x - half, center.y + half)
                close()
            }
        PointShape.Cross ->
            Path().apply {
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

private fun List<tilo.compose.core.geometry.Point>.toOpenPath(worldToScreen: WorldToScreenTransform): Path {
    val path = Path()
    val first = first()
    path.moveTo(
        worldToScreen.screenX(first.x, first.y).toFloat(),
        worldToScreen.screenY(first.x, first.y).toFloat(),
    )
    for (index in 1 until size) {
        val point = this[index]
        path.lineTo(
            worldToScreen.screenX(point.x, point.y).toFloat(),
            worldToScreen.screenY(point.x, point.y).toFloat(),
        )
    }
    return path
}

private fun List<List<tilo.compose.core.geometry.Point>>.toPath(worldToScreen: WorldToScreenTransform): Path {
    val path =
        Path().apply {
            fillType = PathFillType.EvenOdd
        }
    for (ring in this) {
        if (ring.isEmpty()) continue
        val first = ring.first()
        path.moveTo(
            worldToScreen.screenX(first.x, first.y).toFloat(),
            worldToScreen.screenY(first.x, first.y).toFloat(),
        )
        for (index in 1 until ring.size) {
            val point = ring[index]
            path.lineTo(
                worldToScreen.screenX(point.x, point.y).toFloat(),
                worldToScreen.screenY(point.x, point.y).toFloat(),
            )
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

private fun DrawScope.drawHatchPattern(
    pattern: FillPattern.Hatch,
    bounds: ScreenBounds,
) {
    val spacing = styleUnitToPx(pattern.spacing).coerceAtLeast(1f)
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
            strokeWidth = styleUnitToPx(pattern.stroke.width),
            cap = pattern.stroke.lineCap.toComposeCap(),
            pathEffect = toPathEffect(pattern.stroke.dash),
        )
        offset += spacing
    }
}

private fun DrawScope.drawDotsPattern(
    pattern: FillPattern.Dots,
    bounds: ScreenBounds,
) {
    val spacing = styleUnitToPx(pattern.spacing).coerceAtLeast(1f)
    val radius = styleUnitToPx(pattern.radius).coerceAtLeast(0.5f)
    var y = bounds.top
    while (y <= bounds.bottom) {
        var x = bounds.left
        while (x <= bounds.right) {
            drawCircle(
                color = pattern.color.toColor(),
                radius = radius,
                center = Offset(x, y),
            )
            x += spacing
        }
        y += spacing
    }
}

private fun DrawScope.toComposeStroke(stroke: StrokeStyle): Stroke =
    Stroke(
        width = styleUnitToPx(stroke.width),
        cap = stroke.lineCap.toComposeCap(),
        join = stroke.lineJoin.toComposeJoin(),
        pathEffect = toPathEffect(stroke.dash),
    )

private fun DrawScope.toComposeStroke(
    casing: CasingStyle,
    foregroundWidth: Double,
): Stroke =
    Stroke(
        width = styleUnitToPx(casing.outerWidth(foregroundWidth)),
        cap = casing.lineCap.toComposeCap(),
        join = casing.lineJoin.toComposeJoin(),
        pathEffect = toPathEffect(casing.dash),
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

private fun DrawScope.toPathEffect(dash: DashPattern?): PathEffect? {
    dash ?: return null
    if (dash.intervals.size < 2) return null
    val validIntervals = FloatArray(dash.intervals.size)
    for (index in dash.intervals.indices) {
        validIntervals[index] = styleUnitToPx(dash.intervals[index]).coerceAtLeast(0.1f)
    }
    return PathEffect.dashPathEffect(validIntervals, styleUnitToPx(dash.phase))
}

private fun List<List<tilo.compose.core.geometry.Point>>.screenBounds(
    worldToScreen: WorldToScreenTransform,
): ScreenBounds {
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    for (ring in this) {
        for (point in ring) {
            val screenX = worldToScreen.screenX(point.x, point.y).toFloat()
            val screenY = worldToScreen.screenY(point.x, point.y).toFloat()
            left = min(left, screenX)
            top = min(top, screenY)
            right = max(right, screenX)
            bottom = max(bottom, screenY)
        }
    }
    return ScreenBounds(left = left, top = top, right = right, bottom = bottom)
}
