@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.withSave
import tilo.compose.core.feature.CasingStyle
import tilo.compose.core.feature.DashPattern
import tilo.compose.core.feature.LineCap
import tilo.compose.core.feature.LineJoin
import tilo.compose.core.feature.LineStyle
import tilo.compose.core.feature.PointShape
import tilo.compose.core.feature.PointStyle
import tilo.compose.core.feature.PolygonStyle
import tilo.compose.core.feature.StrokeStyle
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapState

/** Camera-independent paths drawn through a Canvas matrix without copying their vertices per frame. */
@ExperimentalTiloRenderingApi
class CachedGeometry internal constructor(
    internal val points: List<CachedPointBatch>,
    internal val lines: List<CachedLineBatch>,
    internal val polygons: List<CachedPolygonBatch>,
    internal val fallbackCommands: List<RenderCommand>,
    internal val commandCount: Int,
    internal val styleBatchCount: Int,
) {
    internal val renderBatchCount: Int
        get() = points.size + lines.size + polygons.size

    internal companion object {
        fun build(
            commands: List<RenderCommand>,
            pointWorldUnitsPerPixel: Double,
        ): CachedGeometry {
            val batches = GeometryCommandBatches.build(commands)
            val points = mutableListOf<CachedPointBatch>()
            val lines = mutableListOf<CachedLineBatch>()
            val polygons = mutableListOf<CachedPolygonBatch>()
            val fallback = mutableListOf<RenderCommand>()

            batches.points.forEach { (style, commandsForStyle) ->
                if (style.canUseCachedPath()) {
                    preparePointPath(commandsForStyle, style, pointWorldUnitsPerPixel)?.let(points::add)
                } else {
                    fallback += commandsForStyle
                }
            }
            batches.lines.forEach { (style, commandsForStyle) ->
                commandsForStyle.forEachVertexChunk { it.points.size }.forEach { chunk ->
                    prepareLinePath(chunk, style)?.let(lines::add)
                }
            }
            batches.polygons.forEach { (style, commandsForStyle) ->
                if (style.fill?.pattern != null) {
                    fallback += commandsForStyle
                } else {
                    commandsForStyle.forEachVertexChunk { command -> command.rings.sumOf(List<Point>::size) }
                        .forEach { chunk -> preparePolygonPath(chunk, style)?.let(polygons::add) }
                }
            }
            fallback += commands.filterIsInstance<RenderLabel>()

            return CachedGeometry(
                points = points,
                lines = lines,
                polygons = polygons,
                fallbackCommands = fallback,
                commandCount = commands.size,
                styleBatchCount = batches.points.size + batches.lines.size + batches.polygons.size,
            )
        }
    }
}

internal data class CachedPointBatch(val origin: Point, val path: Path, val style: PointStyle)

internal data class CachedLineBatch(val origin: Point, val path: Path, val style: LineStyle)

internal data class CachedPolygonBatch(val origin: Point, val path: Path, val style: PolygonStyle)

internal fun DrawScope.drawCachedGeometry(
    cached: CachedGeometry,
    map: MapState,
): GeometryDrawStats {
    val transform = WorldToScreenTransform.from(map)
    val inversePixelScale = density / transform.pixelScale.coerceAtLeast(MIN_PIXEL_SCALE)

    cached.points.forEach { batch ->
        batch.style.fill?.let { fill ->
            drawLocalPath(batch.path, batch.origin, transform, fillPaint(fill.color.toColor(fill.opacity)))
        }
    }
    cached.lines.forEach { batch ->
        batch.style.casing?.let { casing ->
            drawLocalPath(
                batch.path,
                batch.origin,
                transform,
                strokePaint(casing, batch.style.stroke.width, inversePixelScale),
            )
        }
        drawLocalPath(batch.path, batch.origin, transform, strokePaint(batch.style.stroke, inversePixelScale))
    }
    cached.polygons.forEach { batch ->
        batch.style.fill?.let { fill ->
            drawLocalPath(batch.path, batch.origin, transform, fillPaint(fill.color.toColor(fill.opacity)))
        }
        batch.style.casing?.let { casing ->
            drawLocalPath(
                batch.path,
                batch.origin,
                transform,
                strokePaint(casing, batch.style.stroke?.width ?: 0.0, inversePixelScale),
            )
        }
        batch.style.stroke?.let { stroke ->
            drawLocalPath(batch.path, batch.origin, transform, strokePaint(stroke, inversePixelScale))
        }
    }

    val fallbackStats =
        if (cached.fallbackCommands.any { it !is RenderLabel }) {
            drawFeatureGeometry(cached.fallbackCommands, map)
        } else {
            GeometryDrawStats(0, 0)
        }
    return GeometryDrawStats(
        styleBatchCount = cached.styleBatchCount,
        renderBatchCount = cached.renderBatchCount + fallbackStats.renderBatchCount,
    )
}

private fun DrawScope.drawLocalPath(
    path: Path,
    origin: Point,
    transform: WorldToScreenTransform,
    paint: Paint,
) {
    val canvas = drawContext.canvas
    canvas.withSave {
        canvas.concat(transform.localToScreenMatrix(origin))
        canvas.drawPath(path, paint)
    }
}

private fun fillPaint(color: androidx.compose.ui.graphics.Color): Paint =
    Paint().also {
        it.color = color
        it.style = PaintingStyle.Fill
    }

private fun strokePaint(stroke: StrokeStyle, inversePixelScale: Float): Paint =
    Paint().also {
        it.color = stroke.color.toColor(stroke.opacity)
        it.style = PaintingStyle.Stroke
        it.strokeWidth = stroke.width.toFloat() * inversePixelScale
        it.strokeCap = stroke.lineCap.toComposeCap()
        it.strokeJoin = stroke.lineJoin.toComposeJoin()
        it.pathEffect = stroke.dash.toLocalPathEffect(inversePixelScale)
    }

private fun strokePaint(
    casing: CasingStyle,
    foregroundWidth: Double,
    inversePixelScale: Float,
): Paint =
    Paint().also {
        it.color = casing.color.toColor(casing.opacity)
        it.style = PaintingStyle.Stroke
        it.strokeWidth = casing.outerWidth(foregroundWidth).toFloat() * inversePixelScale
        it.strokeCap = casing.lineCap.toComposeCap()
        it.strokeJoin = casing.lineJoin.toComposeJoin()
        it.pathEffect = casing.dash.toLocalPathEffect(inversePixelScale)
    }

private fun DashPattern?.toLocalPathEffect(inversePixelScale: Float): PathEffect? {
    this ?: return null
    val intervals = intervals.map { (it.toFloat() * inversePixelScale).coerceAtLeast(0.0001f) }
    if (intervals.size < 2) return null
    return PathEffect.dashPathEffect(intervals.toFloatArray(), phase.toFloat() * inversePixelScale)
}

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

private fun PointStyle.canUseCachedPath(): Boolean =
    shape == PointShape.Circle && stroke == null && icon == null

private fun preparePointPath(
    commands: List<RenderPoint>,
    style: PointStyle,
    worldUnitsPerPixel: Double,
): CachedPointBatch? {
    val origin = commands.firstOrNull()?.point ?: return null
    val radius = style.size * worldUnitsPerPixel / 2.0
    val path = Path()
    commands.forEach { command ->
        val x = (command.point.x - origin.x).toFloat()
        val y = (command.point.y - origin.y).toFloat()
        val r = radius.toFloat()
        path.addOval(Rect(x - r, y - r, x + r, y + r))
    }
    return CachedPointBatch(origin, path, style)
}

private fun prepareLinePath(commands: List<RenderLineString>, style: LineStyle): CachedLineBatch? {
    val origin = commands.firstNotNullOfOrNull { it.points.firstOrNull() } ?: return null
    val path = Path()
    commands.forEach { it.points.appendLocalOpenPath(path, origin) }
    return CachedLineBatch(origin, path, style)
}

private fun preparePolygonPath(commands: List<RenderPolygon>, style: PolygonStyle): CachedPolygonBatch? {
    val origin = commands.firstNotNullOfOrNull { it.rings.firstNotNullOfOrNull(List<Point>::firstOrNull) } ?: return null
    val path = Path().apply { fillType = PathFillType.EvenOdd }
    commands.forEach { it.rings.appendLocalClosedPath(path, origin) }
    return CachedPolygonBatch(origin, path, style)
}

private fun List<Point>.appendLocalOpenPath(path: Path, origin: Point) {
    val first = firstOrNull() ?: return
    path.moveTo((first.x - origin.x).toFloat(), (first.y - origin.y).toFloat())
    drop(1).forEach { point -> path.lineTo((point.x - origin.x).toFloat(), (point.y - origin.y).toFloat()) }
}

private fun List<List<Point>>.appendLocalClosedPath(path: Path, origin: Point) {
    forEach { ring ->
        ring.appendLocalOpenPath(path, origin)
        if (ring.isNotEmpty()) path.close()
    }
}

private inline fun <T> List<T>.forEachVertexChunk(verticesOf: (T) -> Int): List<List<T>> {
    if (isEmpty()) return emptyList()
    val chunks = mutableListOf<List<T>>()
    var start = 0
    var vertices = 0
    forEachIndexed { index, item ->
        val itemVertices = verticesOf(item)
        if (vertices > 0 && vertices + itemVertices > MAX_CACHED_PATH_VERTICES) {
            chunks += subList(start, index)
            start = index
            vertices = 0
        }
        vertices += itemVertices
    }
    if (start < size) chunks += subList(start, size)
    return chunks
}

private const val MAX_CACHED_PATH_VERTICES = 4_096
private const val MIN_PIXEL_SCALE = 0.000001f
