@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import tilo.compose.core.feature.LabelFontStyle
import tilo.compose.core.feature.LabelFontWeight
import tilo.compose.core.feature.LabelStyle
import tilo.compose.core.feature.LabelTextAlign
import kotlin.math.ceil
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas

private const val DEFAULT_LABEL_CACHE_SIZE = 2_048

@ExperimentalTiloRenderingApi
class LabelBitmapCache(
    private val maxEntries: Int = DEFAULT_LABEL_CACHE_SIZE,
) {
    private val entries = LinkedHashMap<LabelBitmapKey, LabelCacheEntry>()

    internal fun layoutOrPut(
        key: LabelBitmapKey,
        create: () -> LabelBitmapLayout,
    ): LabelBitmapLayout {
        entries.remove(key)?.let { cached ->
            entries[key] = cached
            return cached.layout
        }

        val layout = create()
        entries[key] = LabelCacheEntry(layout = layout)
        trimToSize()
        return layout
    }

    internal fun bitmapOrPut(
        key: LabelBitmapKey,
        createLayout: () -> LabelBitmapLayout,
        createBitmap: (LabelBitmapLayout) -> ImageBitmap,
    ): ImageBitmap {
        val cached = entries.remove(key)
        if (cached != null) {
            cached.bitmap?.let { bitmap ->
                entries[key] = cached
                return bitmap
            }
            val bitmap = createBitmap(cached.layout)
            entries[key] = cached.copy(bitmap = bitmap)
            return bitmap
        }

        val layout = createLayout()
        val bitmap = createBitmap(layout)
        entries[key] = LabelCacheEntry(layout = layout, bitmap = bitmap)
        trimToSize()
        return bitmap
    }

    private fun trimToSize() {
        while (entries.size > maxEntries) {
            val eldestKey = entries.keys.firstOrNull() ?: return
            entries.remove(eldestKey)
        }
    }
}

private data class LabelCacheEntry(
    val layout: LabelBitmapLayout,
    val bitmap: ImageBitmap? = null,
)

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

internal data class LabelBitmapLayout(
    val textLayout: TextLayoutResult,
    val paddingX: Int,
    val paddingY: Int,
) {
    val metrics =
        LabelBitmapMetrics(
            width = (textLayout.size.width + paddingX * 2).coerceAtLeast(1),
            height = (textLayout.size.height + paddingY * 2).coerceAtLeast(1),
        )
}

internal fun DrawScope.cachedLabelBitmap(
    text: String,
    style: LabelStyle,
    textMeasurer: TextMeasurer,
    offscreenDrawScope: CanvasDrawScope,
    cache: LabelBitmapCache,
): ImageBitmap =
    labelBitmapKey(text, style).let { key ->
        cache.bitmapOrPut(
            key = key,
            createLayout = { measureLabelBitmapLayout(text, style, textMeasurer) },
            createBitmap = { layout -> createLabelBitmap(style, layout, offscreenDrawScope) },
        )
    }

internal fun DrawScope.cachedLabelBitmapMetrics(
    text: String,
    style: LabelStyle,
    textMeasurer: TextMeasurer,
    cache: LabelBitmapCache,
): LabelBitmapMetrics {
    val key = labelBitmapKey(text, style)
    return cache
        .layoutOrPut(key) {
            measureLabelBitmapLayout(text, style, textMeasurer)
        }.metrics
}

private fun DrawScope.labelBitmapKey(
    text: String,
    style: LabelStyle,
): LabelBitmapKey =
    LabelBitmapKey(
        text = text,
        style = style,
        density = density,
        fontScale = fontScale,
        layoutDirection = layoutDirection,
    )

private fun DrawScope.measureLabelBitmapLayout(
    text: String,
    style: LabelStyle,
    textMeasurer: TextMeasurer,
): LabelBitmapLayout {
    val textLayout = textMeasurer.measure(text = text, style = style.toTextStyle())
    val padding = labelBitmapPadding(style)
    return LabelBitmapLayout(
        textLayout = textLayout,
        paddingX = padding.x,
        paddingY = padding.y,
    )
}

internal fun DrawScope.createLabelBitmap(
    style: LabelStyle,
    layout: LabelBitmapLayout,
    offscreenDrawScope: CanvasDrawScope,
): ImageBitmap {
    val textColor = style.color.toColor()
    val haloColor = style.haloColor.toColor()
    val textLayout = layout.textLayout

    val haloWidthPx = (style.haloWidth * density).toFloat()
    val background = style.background
    val backgroundPaddingX = ((background?.paddingHorizontal ?: 0.0) * density).toFloat()
    val backgroundPaddingY = ((background?.paddingVertical ?: 0.0) * density).toFloat()
    val width = layout.metrics.width
    val height = layout.metrics.height

    val bitmap = ImageBitmap(width, height)
    val canvas = GraphicsCanvas(bitmap)
    val baseTopLeft = Offset(layout.paddingX.toFloat(), layout.paddingY.toFloat())

    offscreenDrawScope.draw(
        density = this,
        layoutDirection = layoutDirection,
        canvas = canvas,
        size = Size(width.toFloat(), height.toFloat()),
    ) {
        if (background != null) {
            drawRoundRect(
                color = background.color.toColor().copy(alpha = background.opacity.toFloat()),
                topLeft =
                    Offset(
                        x = baseTopLeft.x - backgroundPaddingX,
                        y = baseTopLeft.y - backgroundPaddingY,
                    ),
                size =
                    Size(
                        width = textLayout.size.width + backgroundPaddingX * 2,
                        height = textLayout.size.height + backgroundPaddingY * 2,
                    ),
                cornerRadius =
                    CornerRadius(
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

internal fun LabelStyle.toTextStyle(): TextStyle =
    TextStyle(
        color = color.toColor(),
        fontSize = fontSize.sp,
        fontWeight = fontWeight.toComposeFontWeight(),
        fontStyle = fontStyle.toComposeFontStyle(),
        textAlign = textAlign.toComposeTextAlign(),
    )

private fun LabelTextAlign.toComposeTextAlign(): TextAlign =
    when (this) {
        LabelTextAlign.Left -> TextAlign.Left
        LabelTextAlign.Center -> TextAlign.Center
        LabelTextAlign.Right -> TextAlign.Right
    }

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
