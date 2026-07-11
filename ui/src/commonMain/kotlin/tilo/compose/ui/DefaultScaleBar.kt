package tilo.compose.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tilo.compose.core.scale.ScaleBar

@OptIn(ExperimentalTextApi::class)
@Composable
fun BoxScope.DefaultScaleBar(scaleBar: ScaleBar) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val widthDp = (scaleBar.widthPx / density.density).dp
    val labels = remember(textMeasurer, scaleBar.label, scaleBar.midpointLabel) {
        ScaleBarLabelLayouts(
            start = textMeasurer.measureLabel("0"),
            middle = textMeasurer.measureLabel(scaleBar.midpointLabel),
            end = textMeasurer.measureLabel(scaleBar.label),
        )
    }
    Canvas(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 12.dp, bottom = 2.dp)
            .requiredWidth(widthDp)
            .height(30.dp),
    ) {
        val barHeight = 12.dp.toPx()
        val radius = 3.dp.toPx()
        val halfWidth = size.width / 2.0f

        drawScaleBarShadow(
            width = size.width,
            height = barHeight,
            radius = radius,
        )
        drawRoundRect(
            color = ScaleBarLightColor,
            topLeft = Offset.Zero,
            size = Size(size.width, barHeight),
            cornerRadius = CornerRadius(radius, radius),
        )
        drawRoundRect(
            color = ScaleBarColor,
            topLeft = Offset.Zero,
            size = Size(halfWidth + radius, barHeight),
            cornerRadius = CornerRadius(radius, radius),
        )
        drawRect(
            color = ScaleBarLightColor,
            topLeft = Offset(halfWidth, 0.0f),
            size = Size(radius, barHeight),
        )

        val labelTop = barHeight + 3.dp.toPx()
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
private val ScaleBarTextColor = Color.Black
private val ScaleBarLightColor = Color.White

private val ScaleBarTextStyle = TextStyle(
    color = ScaleBarTextColor,
    fontSize = 9.sp,
    fontWeight = FontWeight.Normal,
    fontSynthesis = FontSynthesis.None,
)

private val ScaleBarHaloTextStyle = ScaleBarTextStyle.copy(color = Color.White)

private val ScaleBarHaloOffsets = listOf(
    Offset(-3.0f, -3.0f),
    Offset(0.0f, -3.0f),
    Offset(3.0f, -3.0f),
    Offset(-2.0f, -2.0f),
    Offset(0.0f, -2.0f),
    Offset(2.0f, -2.0f),
    Offset(-3.0f, 0.0f),
    Offset(-2.0f, 0.0f),
    Offset(2.0f, 0.0f),
    Offset(3.0f, 0.0f),
    Offset(-2.0f, 2.0f),
    Offset(0.0f, 2.0f),
    Offset(2.0f, 2.0f),
    Offset(-3.0f, 3.0f),
    Offset(0.0f, 3.0f),
    Offset(3.0f, 3.0f),
)

@OptIn(ExperimentalTextApi::class)
private fun androidx.compose.ui.text.TextMeasurer.measureLabel(text: String): ScaleBarLabelLayout =
    ScaleBarLabelLayout(
        fill = measure(text, ScaleBarTextStyle),
        halo = measure(text, ScaleBarHaloTextStyle),
    )

private data class ScaleBarLabelLayouts(
    val start: ScaleBarLabelLayout,
    val middle: ScaleBarLabelLayout,
    val end: ScaleBarLabelLayout,
)

private data class ScaleBarLabelLayout(
    val fill: TextLayoutResult,
    val halo: TextLayoutResult,
)

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
    val topLeft = Offset(
        x = when (alignment) {
            LabelAlignment.Start -> x
            LabelAlignment.Center -> x - label.fill.size.width / 2.0f
            LabelAlignment.End -> x - label.fill.size.width
        },
        y = y,
    )
    ScaleBarHaloOffsets.forEach { offset ->
        drawText(
            textLayoutResult = label.halo,
            topLeft = topLeft + offset,
        )
    }
    drawText(textLayoutResult = label.fill, topLeft = topLeft)
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
    val startRight = start.fill.size.width.toFloat()
    val endLeft = fullWidth - end.fill.size.width
    return middleLeft >= startRight + spacing && middleRight <= endLeft - spacing
}
