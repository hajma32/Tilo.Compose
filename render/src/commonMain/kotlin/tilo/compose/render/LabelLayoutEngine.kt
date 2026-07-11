package tilo.compose.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import tilo.compose.core.map.Map
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class PlacedLabel(
    val command: RenderLabel,
    val center: Offset,
    val topLeft: Offset,
    val width: Int,
    val height: Int,
    val order: Int,
)

internal class LabelLayoutEngine(
    private val collisionPaddingStyleUnits: Double = 2.0,
) {
    fun layout(
        labels: List<RenderLabel>,
        map: Map,
        drawScope: DrawScope,
        textMeasurer: TextMeasurer,
    ): List<PlacedLabel> {
        if (labels.isEmpty()) return emptyList()

        val candidates = labels.mapIndexed { index, label ->
            label.toCandidate(
                order = index,
                map = map,
                drawScope = drawScope,
                textMeasurer = textMeasurer,
            )
        }
        val acceptedBounds = mutableListOf<ScreenBounds>()
        val accepted = mutableListOf<LabelCandidate>()
        val collisionPadding = drawScope.styleUnitToPx(collisionPaddingStyleUnits)

        candidates.sortedWith(labelCollisionOrder()).forEach { candidate ->
            val bounds = candidate.bounds.padded(collisionPadding)
            if (acceptedBounds.none { it.intersects(bounds) }) {
                acceptedBounds += bounds
                accepted += candidate
            }
        }

        return accepted
            .sortedBy { it.order }
            .map { candidate ->
                PlacedLabel(
                    command = candidate.command,
                    center = candidate.center,
                    topLeft = candidate.topLeft,
                    width = candidate.width,
                    height = candidate.height,
                    order = candidate.order,
                )
            }
    }

    private fun RenderLabel.toCandidate(
        order: Int,
        map: Map,
        drawScope: DrawScope,
        textMeasurer: TextMeasurer,
    ): LabelCandidate {
        val anchor = map.worldToScreen(anchor)
        val metrics = drawScope.measureLabelBitmap(
            text = text,
            style = style,
            textMeasurer = textMeasurer,
        )
        val center = if (followsLine) {
            val offset = metrics.height / 2f + drawScope.styleUnitToPx(style.offsetY)
            val radians = rotationDegrees * PI / 180.0
            Offset(
                x = anchor.x.toFloat() - sin(radians).toFloat() * offset,
                y = anchor.y.toFloat() + cos(radians).toFloat() * offset,
            )
        } else {
            Offset(
                x = anchor.x.toFloat(),
                y = anchor.y.toFloat() + drawScope.styleUnitToPx(style.offsetY) + metrics.height / 2f,
            )
        }
        val topLeft = Offset(
            x = center.x - metrics.width / 2f,
            y = center.y - metrics.height / 2f,
        )

        return LabelCandidate(
            command = this,
            center = center,
            topLeft = topLeft,
            width = metrics.width,
            height = metrics.height,
            bounds = rotatedBounds(
                center = center,
                width = metrics.width.toFloat(),
                height = metrics.height.toFloat(),
                degrees = rotationDegrees.toFloat(),
            ),
            order = order,
        )
    }

    private fun labelCollisionOrder(): Comparator<LabelCandidate> =
        compareByDescending<LabelCandidate> { it.command.selected }
            .thenComparator { a, b ->
                val leftPriority = a.command.labelPriority
                val rightPriority = b.command.labelPriority
                when {
                    leftPriority != null && rightPriority != null -> rightPriority.compareTo(leftPriority)
                    leftPriority != null -> -1
                    rightPriority != null -> 1
                    else -> b.area.compareTo(a.area)
                }
            }
            .thenByDescending { it.area }
            .thenBy { it.order }

    private fun rotatedBounds(center: Offset, width: Float, height: Float, degrees: Float): ScreenBounds {
        val radians = degrees * PI.toFloat() / 180f
        val rotatedWidth = abs(width * cos(radians)) + abs(height * sin(radians))
        val rotatedHeight = abs(width * sin(radians)) + abs(height * cos(radians))
        return ScreenBounds(
            left = center.x - rotatedWidth / 2f,
            top = center.y - rotatedHeight / 2f,
            right = center.x + rotatedWidth / 2f,
            bottom = center.y + rotatedHeight / 2f,
        )
    }

    private data class LabelCandidate(
        val command: RenderLabel,
        val center: Offset,
        val topLeft: Offset,
        val width: Int,
        val height: Int,
        val bounds: ScreenBounds,
        val order: Int,
    ) {
        val area: Float = width.toFloat() * height.toFloat()
    }
}

internal fun DrawScope.drawPlacedLabels(
    labels: List<PlacedLabel>,
    offscreenDrawScope: CanvasDrawScope,
    textMeasurer: TextMeasurer,
    labelBitmapCache: LabelBitmapCache,
) {
    labels.forEach { label ->
        val bitmap = cachedLabelBitmap(
            text = label.command.text,
            style = label.command.style,
            textMeasurer = textMeasurer,
            offscreenDrawScope = offscreenDrawScope,
            cache = labelBitmapCache,
        )
        rotate(degrees = label.command.rotationDegrees.toFloat(), pivot = label.center) {
            drawImage(
                image = bitmap,
                dstOffset = IntOffset(
                    x = label.topLeft.x.roundToInt(),
                    y = label.topLeft.y.roundToInt(),
                ),
                dstSize = IntSize(label.width, label.height),
            )
        }
    }
}

internal data class ScreenBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float = right - left
    val height: Float = bottom - top
    val center: Offset = Offset((left + right) / 2f, (top + bottom) / 2f)

    fun intersects(other: ScreenBounds): Boolean =
        left < other.right &&
            right > other.left &&
            top < other.bottom &&
            bottom > other.top

    fun padded(padding: Float): ScreenBounds =
        ScreenBounds(
            left = left - padding,
            top = top - padding,
            right = right + padding,
            bottom = bottom + padding,
        )
}

internal fun DrawScope.styleUnitToPx(value: Double): Float = (value * density).toFloat()
