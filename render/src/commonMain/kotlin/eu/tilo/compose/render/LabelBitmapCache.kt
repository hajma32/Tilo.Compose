package eu.tilo.compose.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp

private const val LABEL_HALO_RADIUS_PX = 1f
private const val LABEL_BITMAP_PADDING_PX = 2

private val HALO_OFFSETS = arrayOf(
    Offset(-LABEL_HALO_RADIUS_PX, 0f),
    Offset(LABEL_HALO_RADIUS_PX, 0f),
    Offset(0f, -LABEL_HALO_RADIUS_PX),
    Offset(0f, LABEL_HALO_RADIUS_PX),
    Offset(-LABEL_HALO_RADIUS_PX, -LABEL_HALO_RADIUS_PX),
    Offset(LABEL_HALO_RADIUS_PX, -LABEL_HALO_RADIUS_PX),
    Offset(-LABEL_HALO_RADIUS_PX, LABEL_HALO_RADIUS_PX),
    Offset(LABEL_HALO_RADIUS_PX, LABEL_HALO_RADIUS_PX)
)

/**
 * Returns a cached offscreen bitmap for [text] rendered with [textColor] and a white halo.
 * The bitmap is created lazily and stored in [cache] keyed by text + color.
 */
internal fun DrawScope.getOrCreateLabelBitmap(
    text: String,
    textColor: Color,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    cache: MutableMap<String, ImageBitmap>,
    offscreenDrawScope: CanvasDrawScope
): ImageBitmap {
    val cacheKey = "$text|${textColor.toArgb()}"
    cache[cacheKey]?.let { return it }

    val labelStyle = TextStyle(color = textColor, fontSize = 12.sp)
    val textLayout = textMeasurer.measure(text = text, style = labelStyle)

    val haloPadding = LABEL_BITMAP_PADDING_PX + LABEL_HALO_RADIUS_PX.toInt()
    val width = (textLayout.size.width + haloPadding * 2).coerceAtLeast(1)
    val height = (textLayout.size.height + haloPadding * 2).coerceAtLeast(1)

    val bitmap = ImageBitmap(width, height)
    val canvas = GraphicsCanvas(bitmap)
    val baseTopLeft = Offset(haloPadding.toFloat(), haloPadding.toFloat())
    val haloStyle = labelStyle.copy(color = Color.White)

    offscreenDrawScope.draw(
        density = this,
        layoutDirection = layoutDirection,
        canvas = canvas,
        size = Size(width.toFloat(), height.toFloat())
    ) {
        HALO_OFFSETS.forEach { offset ->
            drawText(textMeasurer = textMeasurer, text = text, topLeft = baseTopLeft + offset, style = haloStyle)
        }
        drawText(textMeasurer = textMeasurer, text = text, topLeft = baseTopLeft, style = labelStyle)
    }

    cache[cacheKey] = bitmap
    return bitmap
}

