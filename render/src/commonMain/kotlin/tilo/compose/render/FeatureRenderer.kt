@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import tilo.compose.core.feature.LineCap
import tilo.compose.core.feature.LineJoin
import tilo.compose.core.feature.LineStyle
import tilo.compose.core.feature.PointShape
import tilo.compose.core.feature.PointStyle
import tilo.compose.core.feature.PolygonStyle
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
): GeometryDrawStats {
    val worldToScreen = WorldToScreenTransform.from(map)
    val batches = GeometryCommandBatches.build(commands)
    var renderBatchCount = 0
    batches.points.forEach { (style, points) ->
        renderBatchCount += drawPointBatch(points, style, worldToScreen, pointIconPainters)
    }
    batches.lines.forEach { (style, lines) ->
        renderBatchCount += drawLineBatch(lines, style, worldToScreen)
    }
    batches.polygons.forEach { (style, polygons) ->
        renderBatchCount += drawPolygonBatch(polygons, style, worldToScreen)
    }
    return GeometryDrawStats(
        styleBatchCount = batches.points.size + batches.lines.size + batches.polygons.size,
        renderBatchCount = renderBatchCount,
    )
}

internal data class GeometryDrawStats(
    val styleBatchCount: Int,
    val renderBatchCount: Int,
)

private fun DrawScope.drawPointBatch(
    commands: List<RenderPoint>,
    style: PointStyle,
    worldToScreen: WorldToScreenTransform,
    pointIconPainters: Map<String, Painter>,
): Int {
    val centers =
        commands.mapTo(ArrayList(commands.size)) { command ->
            Offset(
                x = worldToScreen.screenX(command.point.x, command.point.y).toFloat(),
                y = worldToScreen.screenY(command.point.x, command.point.y).toFloat(),
            )
        }
    val size = styleUnitToPx(style.size).coerceAtLeast(1f)

    val path = Path()
    centers.forEach { center -> path.addPointShape(style.shape, center, size) }
    style.fill?.let { fill -> drawPath(path = path, color = fill.color.toColor(fill.opacity)) }
    style.stroke?.let { stroke ->
        drawPath(path = path, color = stroke.color.toColor(stroke.opacity), style = toComposeStroke(stroke))
    }

    style.icon?.let { icon ->
        val painter =
            checkNotNull(pointIconPainters[icon.id]) {
                "Point icon '${icon.id}' is not registered on the feature layer"
            }
        val iconSize = styleUnitToPx(icon.size).coerceAtLeast(1f)
        centers.forEach { center ->
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
    return 1
}

private fun DrawScope.drawLineBatch(
    commands: List<RenderLineString>,
    style: LineStyle,
    worldToScreen: WorldToScreenTransform,
): Int {
    var renderBatchCount = 0
    commands.forEachVertexChunk(verticesOf = { command -> command.points.size }) { chunk ->
        val path = Path()
        chunk.forEach { command -> command.points.appendOpenPath(path, worldToScreen) }
        style.casing?.let { casing ->
            drawPath(
                path = path,
                color = casing.color.toColor(casing.opacity),
                style = toComposeStroke(casing, style.stroke.width),
            )
        }
        drawPath(
            path = path,
            color = style.stroke.color.toColor(style.stroke.opacity),
            style = toComposeStroke(style.stroke),
        )
        renderBatchCount++
    }
    return renderBatchCount
}

private fun DrawScope.drawPolygonBatch(
    commands: List<RenderPolygon>,
    style: PolygonStyle,
    worldToScreen: WorldToScreenTransform,
): Int {
    if (style.fill?.pattern != null) {
        commands.forEach { command -> drawPatternedPolygon(command, worldToScreen) }
        return commands.size
    }
    var renderBatchCount = 0
    commands.forEachVertexChunk(verticesOf = { command -> command.rings.sumOf { ring -> ring.size } }) { chunk ->
        val path = Path().apply { fillType = PathFillType.EvenOdd }
        chunk.forEach { command -> command.rings.appendClosedPath(path, worldToScreen) }
        if (!path.isEmpty) {
            style.fill?.let { fill ->
                drawPath(path = path, color = fill.color.toColor(fill.opacity))
            }
            style.casing?.let { casing ->
                drawPath(
                    path = path,
                    color = casing.color.toColor(casing.opacity),
                    style = toComposeStroke(casing, style.stroke?.width ?: 0.0),
                )
            }
            style.stroke?.let { stroke ->
                drawPath(
                    path = path,
                    color = stroke.color.toColor(stroke.opacity),
                    style = toComposeStroke(stroke),
                )
            }
            renderBatchCount++
        }
    }
    return renderBatchCount
}

private inline fun <T> List<T>.forEachVertexChunk(
    verticesOf: (T) -> Int,
    draw: (List<T>) -> Unit,
) {
    var chunkStart = 0
    var chunkVertices = 0
    forEachIndexed { index, item ->
        val itemVertices = verticesOf(item)
        if (chunkVertices > 0 && chunkVertices + itemVertices > MAX_PATH_BATCH_VERTICES) {
            draw(subList(chunkStart, index))
            chunkStart = index
            chunkVertices = 0
        }
        chunkVertices += itemVertices
    }
    if (chunkStart < size) draw(subList(chunkStart, size))
}

private const val MAX_PATH_BATCH_VERTICES = 4_096

private fun Path.addPointShape(
    shape: PointShape,
    center: Offset,
    size: Float,
) {
    val half = size / 2f
    when (shape) {
        PointShape.Circle -> addOval(Rect(center.x - half, center.y - half, center.x + half, center.y + half))
        PointShape.Square -> {
            moveTo(center.x - half, center.y - half)
            lineTo(center.x + half, center.y - half)
            lineTo(center.x + half, center.y + half)
            lineTo(center.x - half, center.y + half)
            close()
        }
        PointShape.Diamond -> {
            moveTo(center.x, center.y - half)
            lineTo(center.x + half, center.y)
            lineTo(center.x, center.y + half)
            lineTo(center.x - half, center.y)
            close()
        }
        PointShape.Triangle -> {
            moveTo(center.x, center.y - half)
            lineTo(center.x + half, center.y + half)
            lineTo(center.x - half, center.y + half)
            close()
        }
        PointShape.Cross -> {
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

private fun List<tilo.compose.core.geometry.Point>.appendOpenPath(
    path: Path,
    worldToScreen: WorldToScreenTransform,
) {
    if (isEmpty()) return
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
}

private fun List<List<tilo.compose.core.geometry.Point>>.appendClosedPath(
    path: Path,
    worldToScreen: WorldToScreenTransform,
) {
    forEach { ring ->
        if (ring.isEmpty()) return@forEach
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
}

private fun DrawScope.drawPatternedPolygon(
    command: RenderPolygon,
    worldToScreen: WorldToScreenTransform,
) {
    val path = Path().apply { fillType = PathFillType.EvenOdd }
    command.rings.appendClosedPath(path, worldToScreen)
    if (path.isEmpty) return
    command.style.fill?.let { fill ->
        drawPath(path = path, color = fill.color.toColor(fill.opacity))
        fill.pattern?.let { pattern ->
            drawFillPattern(pattern, path, command.rings.screenBounds(worldToScreen))
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
        drawPath(path = path, color = stroke.color.toColor(stroke.opacity), style = toComposeStroke(stroke))
    }
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
    val validIntervals = dash.intervals.map { styleUnitToPx(it).coerceAtLeast(0.1f) }
    if (validIntervals.size < 2) return null
    return PathEffect.dashPathEffect(validIntervals.toFloatArray(), styleUnitToPx(dash.phase))
}

private fun List<List<tilo.compose.core.geometry.Point>>.screenBounds(
    worldToScreen: WorldToScreenTransform,
): ScreenBounds {
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    forEach { ring ->
        ring.forEach { point ->
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
