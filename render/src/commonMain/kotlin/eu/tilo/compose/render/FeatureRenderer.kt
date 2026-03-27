package eu.tilo.compose.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.pow
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.Map

private const val LABEL_VERTICAL_PADDING_PX = 8f

internal fun meshWorldToScreen(point: Point, map: Map): Offset {
    val scalePx = (2.0.pow(map.zoom) / map.projection.worldUnitsPerMapUnit * map.viewport.pixelRatio).toFloat()
    return Offset(
        x = map.viewport.width / 2f + (point.x - map.center.x).toFloat() * scalePx,
        y = map.viewport.height / 2f - (point.y - map.center.y).toFloat() * scalePx
    )
}

/**
 * Draws prepared vector content onto the canvas.
 */
internal fun DrawScope.drawPreparedFeatures(
    prepared: PreparedVectorFrame,
    map: Map,
    offscreenLabelDrawScope: CanvasDrawScope,
    textMeasurer: TextMeasurer
) {
    prepared.meshBatches.forEach { batch ->
        drawMeshBatch(batch, map)
    }

    prepared.points.forEach { command ->
        drawPoint(command, map)
    }

    // Label rendering is intentionally disabled for now; it's known to be slow and
    // not part of the current performance work.
    // prepared.labels.forEach { command ->
    //     drawLabel(command, map, offscreenLabelDrawScope, textMeasurer)
    // }
}

private fun DrawScope.drawMeshBatch(batch: VectorMeshBatch, map: Map) {
    if (batch.vertices.isEmpty() || batch.indices.isEmpty()) return

    val color = when (batch.primitive) {
        VectorMeshPrimitive.POLYGON_FILL -> batch.style.fillColor ?: return
        VectorMeshPrimitive.LINE -> batch.style.strokeColor ?: return
    }

    val positions = batch.vertices.map { point ->
        Offset(point.x.toFloat(), point.y.toFloat())
    }
    val vertices = Vertices(
        vertexMode = VertexMode.Triangles,
        positions = positions,
        textureCoordinates = List(positions.size) { Offset.Zero },
        colors = List(positions.size) { Color(color) },
        indices = batch.indices
    )
    val paint = Paint().apply {
        this.color = Color(color)
    }

    val scalePx = (2.0.pow(map.zoom) / map.projection.worldUnitsPerMapUnit * map.viewport.pixelRatio).toFloat()

    withTransform({
        translate(
            left = map.viewport.width / 2f,
            top = map.viewport.height / 2f
        )
        scale(scaleX = scalePx, scaleY = -scalePx, pivot = Offset.Zero)
        translate(
            left = -map.center.x.toFloat(),
            top = -map.center.y.toFloat()
        )
    }) {
        drawIntoCanvas { canvas ->
            canvas.drawVertices(vertices, BlendMode.SrcOver, paint)
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
