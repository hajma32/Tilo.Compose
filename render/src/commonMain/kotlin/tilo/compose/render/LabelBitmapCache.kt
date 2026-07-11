package tilo.compose.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import tilo.compose.core.feature.LabelFontStyle
import tilo.compose.core.feature.LabelFontWeight
import tilo.compose.core.feature.LabelStyle
import kotlin.math.ceil

private const val DEFAULT_LABEL_CACHE_SIZE = 2_048

class LabelBitmapCache(
    private val maxEntries: Int = DEFAULT_LABEL_CACHE_SIZE,
) {
    private val bitmaps = LinkedHashMap<LabelBitmapKey, ImageBitmap>()

    internal fun getOrPut(key: LabelBitmapKey, create: () -> ImageBitmap): ImageBitmap {
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
    val style: LabelStyle,
    val density: Float,
    val fontScale: Float,
    val layoutDirection: LayoutDirection,
)

internal data class LabelBitmapMetrics(
    val width: Int,
    val height: Int,
)

internal fun DrawScope.cachedLabelBitmap(
    text: String,
    style: LabelStyle,
    textMeasurer: TextMeasurer,
    offscreenDrawScope: CanvasDrawScope,
    cache: LabelBitmapCache,
): ImageBitmap =
    cache.getOrPut(
        LabelBitmapKey(
            text = text,
            style = style,
            density = density,
            fontScale = fontScale,
            layoutDirection = layoutDirection,
        )
    ) {
        createLabelBitmap(
            text = text,
            style = style,
            textMeasurer = textMeasurer,
            offscreenDrawScope = offscreenDrawScope,
        )
    }

internal fun DrawScope.measureLabelBitmap(
    text: String,
    style: LabelStyle,
    textMeasurer: TextMeasurer,
): LabelBitmapMetrics {
    val textLayout = textMeasurer.measure(text = text, style = style.toTextStyle())
    val padding = labelBitmapPadding(style)
    return LabelBitmapMetrics(
        width = (textLayout.size.width + padding.x * 2).coerceAtLeast(1),
        height = (textLayout.size.height + padding.y * 2).coerceAtLeast(1),
    )
}

internal fun DrawScope.createLabelBitmap(
    text: String,
    style: LabelStyle,
    textMeasurer: TextMeasurer,
    offscreenDrawScope: CanvasDrawScope
): ImageBitmap {
    val textColor = style.color.toColor()
    val haloColor = style.haloColor.toColor()
    val textLayout = textMeasurer.measure(text = text, style = style.toTextStyle())
    val padding = labelBitmapPadding(style)

    val haloWidthPx = (style.haloWidth * density).toFloat()
    val background = style.background
    val backgroundPaddingX = ((background?.paddingHorizontal ?: 0.0) * density).toFloat()
    val backgroundPaddingY = ((background?.paddingVertical ?: 0.0) * density).toFloat()
    val width = (textLayout.size.width + padding.x * 2).coerceAtLeast(1)
    val height = (textLayout.size.height + padding.y * 2).coerceAtLeast(1)

    val bitmap = ImageBitmap(width, height)
    val canvas = GraphicsCanvas(bitmap)
    val baseTopLeft = Offset(padding.x.toFloat(), padding.y.toFloat())

    offscreenDrawScope.draw(
        density = this,
        layoutDirection = layoutDirection,
        canvas = canvas,
        size = Size(width.toFloat(), height.toFloat())
    ) {
        if (background != null) {
            drawRoundRect(
                color = background.color.toColor().copy(alpha = background.opacity.toFloat()),
                topLeft = Offset(
                    x = baseTopLeft.x - backgroundPaddingX,
                    y = baseTopLeft.y - backgroundPaddingY,
                ),
                size = Size(
                    width = textLayout.size.width + backgroundPaddingX * 2,
                    height = textLayout.size.height + backgroundPaddingY * 2,
                ),
                cornerRadius = CornerRadius(
                    x = (background.cornerRadius * density).toFloat(),
                    y = (background.cornerRadius * density).toFloat(),
                ),
            )
        }
        if (haloWidthPx > 0f) {
            drawText(
                textLayoutResult = textLayout,
                color = haloColor,
                topLeft = baseTopLeft,
                drawStyle = Stroke(width = haloWidthPx),
            )
        }
        drawText(
            textLayoutResult = textLayout,
            color = textColor,
            topLeft = baseTopLeft,
            drawStyle = Fill,
        )
    }

    return bitmap
}

private data class LabelBitmapPadding(
    val x: Int,
    val y: Int,
)

private fun DrawScope.labelBitmapPadding(style: LabelStyle): LabelBitmapPadding {
    val haloWidthPx = (style.haloWidth * density).toFloat()
    val bitmapPaddingPx = (style.bitmapPadding * density).toFloat()
    val background = style.background
    val backgroundPaddingX = ((background?.paddingHorizontal ?: 0.0) * density).toFloat()
    val backgroundPaddingY = ((background?.paddingVertical ?: 0.0) * density).toFloat()
    return LabelBitmapPadding(
        x = ceil(bitmapPaddingPx + maxOf(haloWidthPx, backgroundPaddingX)).toInt(),
        y = ceil(bitmapPaddingPx + maxOf(haloWidthPx, backgroundPaddingY)).toInt(),
    )
}

private fun LabelStyle.toTextStyle(): TextStyle =
    TextStyle(
        color = color.toColor(),
        fontSize = fontSize.sp,
        fontWeight = fontWeight.toComposeFontWeight(),
        fontStyle = fontStyle.toComposeFontStyle(),
    )

private fun LabelFontWeight.toComposeFontWeight(): FontWeight =
    when (this) {
        LabelFontWeight.Normal -> FontWeight.Normal
        LabelFontWeight.Medium -> FontWeight.Medium
        LabelFontWeight.SemiBold -> FontWeight.SemiBold
        LabelFontWeight.Bold -> FontWeight.Bold
    }

private fun LabelFontStyle.toComposeFontStyle(): FontStyle =
    when (this) {
        LabelFontStyle.Normal -> FontStyle.Normal
        LabelFontStyle.Italic -> FontStyle.Italic
    }
