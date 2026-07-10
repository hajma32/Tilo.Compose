package eu.tilo.compose.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp

private const val LABEL_HALO_RADIUS_PX = 1f
private const val LABEL_BITMAP_PADDING_PX = 2
private const val DEFAULT_LABEL_CACHE_SIZE = 2_048

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

internal class LabelBitmapCache(
    private val maxEntries: Int = DEFAULT_LABEL_CACHE_SIZE,
) {
    private val bitmaps = LinkedHashMap<LabelBitmapKey, ImageBitmap>()

    fun getOrPut(key: LabelBitmapKey, create: () -> ImageBitmap): ImageBitmap {
        bitmaps.remove(key)?.let { cached ->
            bitmaps[key] = cached
            return cached
        }

        val bitmap = create()
        bitmaps[key] = bitmap
        trimToSize()
        return bitmap
    }

    private fun trimToSize() {
        while (bitmaps.size > maxEntries) {
            val eldestKey = bitmaps.keys.firstOrNull() ?: return
            bitmaps.remove(eldestKey)
        }
    }
}

internal data class LabelBitmapKey(
    val text: String,
    val textColor: ULong,
    val density: Float,
    val fontScale: Float,
    val layoutDirection: LayoutDirection,
)

internal fun DrawScope.cachedLabelBitmap(
    text: String,
    textColor: Color,
    textMeasurer: TextMeasurer,
    offscreenDrawScope: CanvasDrawScope,
    cache: LabelBitmapCache,
): ImageBitmap =
    cache.getOrPut(
        LabelBitmapKey(
            text = text,
            textColor = textColor.value,
            density = density,
            fontScale = fontScale,
            layoutDirection = layoutDirection,
        )
    ) {
        createLabelBitmap(
            text = text,
            textColor = textColor,
            textMeasurer = textMeasurer,
            offscreenDrawScope = offscreenDrawScope,
        )
    }

internal fun DrawScope.createLabelBitmap(
    text: String,
    textColor: Color,
    textMeasurer: TextMeasurer,
    offscreenDrawScope: CanvasDrawScope
): ImageBitmap {
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

    return bitmap
}
