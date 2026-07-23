@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tilo.compose.core.scale.ScaleBar
import tilo.compose.render.ExperimentalTiloRenderingApi
import tilo.compose.render.drawLabelTextWithHalo

@OptIn(ExperimentalTextApi::class)
@Composable
fun BoxScope.DefaultScaleBar(scaleBar: ScaleBar) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val widthDp = (scaleBar.widthPx / density.density).dp
    val labels =
        remember(textMeasurer, scaleBar.label, scaleBar.midpointLabel) {
            ScaleBarLabelLayouts(
                start = textMeasurer.measureLabel("0"),
                middle = textMeasurer.measureLabel(scaleBar.midpointLabel),
                end = textMeasurer.measureLabel(scaleBar.label),
            )
        }
    Canvas(
        modifier =
            Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Bottom),
                ).padding(start = 12.dp, bottom = 2.dp)
                .requiredWidth(widthDp)
                .height(30.dp),
    ) {
        val barHeight = 12.dp.toPx()
        val radius = 3.dp.toPx()
        val segments = scaleBarSegments(width = size.width, height = barHeight)

        drawScaleBarShadow(
            width = size.width,
            height = barHeight,
            radius = radius,
        )
        val barPath =
            Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(0.0f, 0.0f, size.width, barHeight),
                        cornerRadius = CornerRadius(radius, radius),
                    ),
                )
            }
        clipPath(barPath) {
            drawRect(
                color = ScaleBarColor.copy(alpha = SCALE_BAR_OPACITY),
                topLeft = segments.start.topLeft,
                size = segments.start.size,
            )
            drawRect(
                color = ScaleBarLightColor.copy(alpha = SCALE_BAR_OPACITY),
                topLeft = segments.end.topLeft,
                size = segments.end.size,
            )
        }

        val labelTop = barHeight + 3.dp.toPx()
        val halfWidth = segments.start.right
        drawLabelWithHalo(
            label = labels.start,
            x = 0.0f,
            y = labelTop,
            alignment = LabelAlignment.Start,
        )
        if (labels.middle.fitsBetween(labels.start, labels.end, halfWidth, size.width, spacing = 4.dp.toPx())) {
            drawLabelWithHalo(
                label = labels.middle,
                x = halfWidth,
                y = labelTop,
                alignment = LabelAlignment.Center,
            )
        }
        drawLabelWithHalo(
            label = labels.end,
            x = size.width,
            y = labelTop,
            alignment = LabelAlignment.End,
        )
    }
}

private val ScaleBarColor = Color(0xFF111827)
private val ScaleBarTextColor = Color.Black.copy(alpha = 0.8f)
private val ScaleBarLightColor = Color.White
internal const val SCALE_BAR_OPACITY = 0.8f

private val ScaleBarTextStyle =
    TextStyle(
        color = ScaleBarTextColor,
        fontSize = 9.sp,
        fontWeight = FontWeight.Normal,
        fontSynthesis = FontSynthesis.None,
    )

@OptIn(ExperimentalTextApi::class)
private fun androidx.compose.ui.text.TextMeasurer.measureLabel(text: String): ScaleBarLabelLayout =
    ScaleBarLabelLayout(
        fill = measure(text, ScaleBarTextStyle),
    )

private data class ScaleBarLabelLayouts(
    val start: ScaleBarLabelLayout,
    val middle: ScaleBarLabelLayout,
    val end: ScaleBarLabelLayout,
)

private data class ScaleBarLabelLayout(
    val fill: TextLayoutResult,
)

internal data class ScaleBarSegments(
    val start: Rect,
    val end: Rect,
)

internal fun scaleBarSegments(
    width: Float,
    height: Float,
): ScaleBarSegments {
    val halfWidth = width / 2.0f
    return ScaleBarSegments(
        start = Rect(0.0f, 0.0f, halfWidth, height),
        end = Rect(halfWidth, 0.0f, width, height),
    )
}

private fun DrawScope.drawScaleBarShadow(
    width: Float,
    height: Float,
    radius: Float,
) {
    val cornerRadius = CornerRadius(radius, radius)
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.08f),
        topLeft = Offset(0.0f, 1.dp.toPx()),
        size = Size(width, height),
        cornerRadius = cornerRadius,
    )
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.04f),
        topLeft = Offset(0.0f, 2.dp.toPx()),
        size = Size(width, height),
        cornerRadius = cornerRadius,
    )
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawLabelWithHalo(
    label: ScaleBarLabelLayout,
    x: Float,
    y: Float,
    alignment: LabelAlignment,
) {
    val topLeft =
        Offset(
            x =
                when (alignment) {
                    LabelAlignment.Start -> x
                    LabelAlignment.Center -> x - label.fill.size.width / 2.0f
                    LabelAlignment.End -> x - label.fill.size.width
                },
            y = y,
        )
    drawLabelTextWithHalo(
        textLayoutResult = label.fill,
        textColor = ScaleBarTextColor,
        haloColor = Color.White.copy(alpha = 0.8f),
        haloWidthPx = 3.dp.toPx(),
        topLeft = topLeft,
    )
}

private enum class LabelAlignment {
    Start,
    Center,
    End,
}

private fun ScaleBarLabelLayout.fitsBetween(
    start: ScaleBarLabelLayout,
    end: ScaleBarLabelLayout,
    centerX: Float,
    fullWidth: Float,
    spacing: Float,
): Boolean {
    val middleLeft = centerX - fill.size.width / 2.0f
    val middleRight = centerX + fill.size.width / 2.0f
    val startRight =
        start.fill.size.width
            .toFloat()
    val endLeft = fullWidth - end.fill.size.width
    return middleLeft >= startRight + spacing && middleRight <= endLeft - spacing
}
