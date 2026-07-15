@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tilo.compose.core.feature.ColorValue
import tilo.compose.core.feature.DashPattern
import tilo.compose.core.feature.FeatureLayerStyle
import tilo.compose.core.feature.FillPattern
import tilo.compose.core.feature.FillStyle
import tilo.compose.core.feature.LabelBackgroundStyle
import tilo.compose.core.feature.LabelFontStyle
import tilo.compose.core.feature.LabelFontWeight
import tilo.compose.core.feature.LabelStyle
import tilo.compose.core.feature.LineCap
import tilo.compose.core.feature.LineJoin
import tilo.compose.core.feature.LineStyle
import tilo.compose.core.feature.PointShape
import tilo.compose.core.feature.PointStyle
import tilo.compose.core.feature.PolygonStyle
import tilo.compose.core.feature.StrokeStyle

/**
 * Converts an ARGB long literal, for example `0xFF1E88E5`, to a GeoCore color.
 */
@ExperimentalTiloApi
fun argb(value: Long): ColorValue =
    ColorValue((value and 0xFFFFFFFFL).toULong())

/**
 * Builds style for point geometries.
 */
@ExperimentalTiloApi
fun pointStyle(block: PointStyleBuilder.() -> Unit = {}): PointStyle =
    PointStyleBuilder().apply(block).build()

/**
 * Builds style for line geometries.
 */
@ExperimentalTiloApi
fun lineStyle(block: LineStyleBuilder.() -> Unit = {}): LineStyle =
    LineStyleBuilder().apply(block).build()

/**
 * Builds style for polygon geometries.
 */
@ExperimentalTiloApi
fun polygonStyle(block: PolygonStyleBuilder.() -> Unit = {}): PolygonStyle =
    PolygonStyleBuilder().apply(block).build()

/**
 * Builds style for feature labels.
 */
@ExperimentalTiloApi
fun labelStyle(block: LabelStyleBuilder.() -> Unit = {}): LabelStyle =
    LabelStyleBuilder().apply(block).build()

/**
 * Small label preset for low-priority local names and dense overlays.
 */
@ExperimentalTiloApi
fun smallLabelStyle(block: LabelStyleBuilder.() -> Unit = {}): LabelStyle =
    LabelStyleBuilder(fontSize = 10.sp, haloWidth = 2.5.dp, offsetY = 10.dp).apply(block).build()

/**
 * Default readable label preset for ordinary feature labels.
 */
@ExperimentalTiloApi
fun mediumLabelStyle(block: LabelStyleBuilder.() -> Unit = {}): LabelStyle =
    LabelStyleBuilder(fontSize = 12.sp, haloWidth = 3.dp, offsetY = 12.dp).apply(block).build()

/**
 * Large label preset for prominent places or important user features.
 */
@ExperimentalTiloApi
fun largeLabelStyle(block: LabelStyleBuilder.() -> Unit = {}): LabelStyle =
    LabelStyleBuilder(fontSize = 15.sp, haloWidth = 3.5.dp, offsetY = 14.dp).apply(block).build()

/**
 * Extra-large label preset for the most important labels in a viewport.
 */
@ExperimentalTiloApi
fun extraLargeLabelStyle(block: LabelStyleBuilder.() -> Unit = {}): LabelStyle =
    LabelStyleBuilder(fontSize = 19.sp, haloWidth = 4.dp, offsetY = 16.dp).apply(block).build()

/**
 * Builds a layer-level style object with defaults for every vector geometry
 * type and its selected state.
 */
@ExperimentalTiloApi
fun featureLayerStyle(block: FeatureLayerStyleBuilder.() -> Unit = {}): FeatureLayerStyle =
    FeatureLayerStyleBuilder().apply(block).build()

@ExperimentalTiloApi
@TiloDsl
class FeatureLayerStyleBuilder {
    private var point: PointStyle? = null
    private var line: LineStyle? = null
    private var polygon: PolygonStyle? = null
    private var label: LabelStyle? = null
    private var selectedPoint: PointStyle? = null
    private var selectedLine: LineStyle? = null
    private var selectedPolygon: PolygonStyle? = null
    private var selectedLabel: LabelStyle? = null

    fun point(block: PointStyleBuilder.() -> Unit) {
        point = pointStyle(block)
    }

    fun line(block: LineStyleBuilder.() -> Unit) {
        line = lineStyle(block)
    }

    fun polygon(block: PolygonStyleBuilder.() -> Unit) {
        polygon = polygonStyle(block)
    }

    fun label(color: Long = 0xFF111827, block: LabelStyleBuilder.() -> Unit = {}) {
        label = LabelStyleBuilder(color = argb(color)).apply(block).build()
    }

    fun label(style: LabelStyle) {
        label = style
    }

    fun selectedPoint(block: PointStyleBuilder.() -> Unit) {
        selectedPoint = pointStyle(block)
    }

    fun selectedLine(block: LineStyleBuilder.() -> Unit) {
        selectedLine = lineStyle(block)
    }

    fun selectedPolygon(block: PolygonStyleBuilder.() -> Unit) {
        selectedPolygon = polygonStyle(block)
    }

    fun selectedLabel(color: Long = 0xFF111827, block: LabelStyleBuilder.() -> Unit = {}) {
        selectedLabel = LabelStyleBuilder(color = argb(color)).apply(block).build()
    }

    fun selectedLabel(style: LabelStyle) {
        selectedLabel = style
    }

    internal fun build(): FeatureLayerStyle =
        FeatureLayerStyle(
            point = point,
            line = line,
            polygon = polygon,
            label = label,
            selectedPoint = selectedPoint,
            selectedLine = selectedLine,
            selectedPolygon = selectedPolygon,
            selectedLabel = selectedLabel,
        )
}

/**
 * Style builder used by [labelStyle] and label preset helpers.
 */
@ExperimentalTiloApi
@TiloDsl
class LabelStyleBuilder internal constructor(
    private var color: ColorValue = ColorValue.Black,
    private var fontSize: TextUnit = 12.sp,
    private var haloColor: ColorValue = ColorValue.White,
    private var haloWidth: Dp = 3.dp,
    private var background: LabelBackgroundStyle? = null,
    private var bitmapPadding: Dp = 2.dp,
    private var offsetY: Dp = 12.dp,
) {
    var fontWeight: LabelFontWeight = LabelFontWeight.Bold
    var fontStyle: LabelFontStyle = LabelFontStyle.Normal

    fun color(value: Long) {
        color = argb(value)
    }

    fun fontSize(value: TextUnit) {
        fontSize = value
    }

    fun fontSize(value: Double) {
        fontSize = value.sp
    }

    fun fontWeight(value: LabelFontWeight) {
        fontWeight = value
    }

    fun fontStyle(value: LabelFontStyle) {
        fontStyle = value
    }

    fun halo(color: Long = 0xFFFFFFFF, width: Dp = 3.dp) {
        haloColor = argb(color)
        haloWidth = width
    }

    fun halo(color: Long = 0xFFFFFFFF, width: Double) {
        haloColor = argb(color)
        haloWidth = width.dp
    }

    fun noHalo() {
        haloWidth = 0.dp
    }

    fun background(
        color: Long,
        opacity: Double = 1.0,
        cornerRadius: Dp = 4.dp,
        paddingHorizontal: Dp = 5.dp,
        paddingVertical: Dp = 2.dp,
    ) {
        background = LabelBackgroundStyle(
            color = argb(color),
            opacity = opacity,
            cornerRadius = cornerRadius.toStyleUnit(),
            paddingHorizontal = paddingHorizontal.toStyleUnit(),
            paddingVertical = paddingVertical.toStyleUnit(),
        )
    }

    fun noBackground() {
        background = null
    }

    fun bitmapPadding(value: Dp) {
        bitmapPadding = value
    }

    fun offsetY(value: Dp) {
        offsetY = value
    }

    internal fun build(): LabelStyle =
        LabelStyle(
            color = color,
            fontSize = fontSize.value.toDouble(),
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            haloColor = haloColor,
            haloWidth = haloWidth.toStyleUnit(),
            background = background,
            bitmapPadding = bitmapPadding.toStyleUnit(),
            offsetY = offsetY.toStyleUnit(),
        )
}

/**
 * Style builder used by [pointStyle].
 */
@ExperimentalTiloApi
@TiloDsl
class PointStyleBuilder {
    var shape: PointShape = PointShape.Circle
    var size: Dp = 14.dp

    private var fill: FillStyle? = FillStyle(color = ColorValue.Blue)
    private var stroke: StrokeStyle? = StrokeStyle(color = ColorValue.White, width = 2.5)

    fun fill(color: Long, opacity: Double = 1.0, block: FillStyleBuilder.() -> Unit = {}) {
        fill = FillStyleBuilder(argb(color), opacity).apply(block).build()
    }

    fun noFill() {
        fill = null
    }

    fun stroke(
        color: Long,
        width: Dp = 1.dp,
        opacity: Double = 1.0,
        block: StrokeStyleBuilder.() -> Unit = {},
    ) {
        stroke = StrokeStyleBuilder(argb(color), width.toStyleUnit(), opacity).apply(block).build()
    }

    fun stroke(
        color: Long,
        width: Double,
        opacity: Double = 1.0,
        block: StrokeStyleBuilder.() -> Unit = {},
    ) {
        stroke = StrokeStyleBuilder(argb(color), width, opacity).apply(block).build()
    }

    fun noStroke() {
        stroke = null
    }

    internal fun build(): PointStyle =
        PointStyle(
            shape = shape,
            size = size.toStyleUnit(),
            fill = fill,
            stroke = stroke,
        )
}

/**
 * Style builder used by [lineStyle].
 */
@ExperimentalTiloApi
@TiloDsl
class LineStyleBuilder {
    private var casing: StrokeStyle? = StrokeStyle(
        color = ColorValue.White,
        width = 6.0,
        lineCap = LineCap.Round,
        lineJoin = LineJoin.Round,
    )
    private var stroke: StrokeStyle = StrokeStyle(
        color = ColorValue.Blue,
        width = 3.0,
        lineCap = LineCap.Round,
        lineJoin = LineJoin.Round,
    )

    fun stroke(
        color: Long,
        width: Dp = 1.dp,
        opacity: Double = 1.0,
        block: StrokeStyleBuilder.() -> Unit = {},
    ) {
        stroke = StrokeStyleBuilder(argb(color), width.toStyleUnit(), opacity).apply(block).build()
    }

    fun stroke(
        color: Long,
        width: Double,
        opacity: Double = 1.0,
        block: StrokeStyleBuilder.() -> Unit = {},
    ) {
        stroke = StrokeStyleBuilder(argb(color), width, opacity).apply(block).build()
    }

    fun casing(
        color: Long,
        width: Dp = 6.dp,
        opacity: Double = 1.0,
        block: StrokeStyleBuilder.() -> Unit = {},
    ) {
        casing = StrokeStyleBuilder(argb(color), width.toStyleUnit(), opacity).apply(block).build()
    }

    fun casing(
        color: Long,
        width: Double,
        opacity: Double = 1.0,
        block: StrokeStyleBuilder.() -> Unit = {},
    ) {
        casing = StrokeStyleBuilder(argb(color), width, opacity).apply(block).build()
    }

    fun noCasing() {
        casing = null
    }

    internal fun build(): LineStyle =
        LineStyle(casing = casing, stroke = stroke)
}

/**
 * Style builder used by [polygonStyle].
 */
@ExperimentalTiloApi
@TiloDsl
class PolygonStyleBuilder {
    private var fill: FillStyle? = FillStyle(color = argb(0x331E88E5))
    private var casing: StrokeStyle? = StrokeStyle(
        color = ColorValue.White,
        width = 5.0,
        lineJoin = LineJoin.Round,
    )
    private var stroke: StrokeStyle? = StrokeStyle(
        color = ColorValue.Blue,
        width = 2.0,
        lineJoin = LineJoin.Round,
    )

    fun fill(color: Long, opacity: Double = 1.0, block: FillStyleBuilder.() -> Unit = {}) {
        fill = FillStyleBuilder(argb(color), opacity).apply(block).build()
    }

    fun noFill() {
        fill = null
    }

    fun casing(
        color: Long,
        width: Dp = 5.dp,
        opacity: Double = 1.0,
        block: StrokeStyleBuilder.() -> Unit = {},
    ) {
        casing = StrokeStyleBuilder(argb(color), width.toStyleUnit(), opacity).apply(block).build()
    }

    fun casing(
        color: Long,
        width: Double,
        opacity: Double = 1.0,
        block: StrokeStyleBuilder.() -> Unit = {},
    ) {
        casing = StrokeStyleBuilder(argb(color), width, opacity).apply(block).build()
    }

    fun noCasing() {
        casing = null
    }

    fun stroke(
        color: Long,
        width: Dp = 1.dp,
        opacity: Double = 1.0,
        block: StrokeStyleBuilder.() -> Unit = {},
    ) {
        stroke = StrokeStyleBuilder(argb(color), width.toStyleUnit(), opacity).apply(block).build()
    }

    fun stroke(
        color: Long,
        width: Double,
        opacity: Double = 1.0,
        block: StrokeStyleBuilder.() -> Unit = {},
    ) {
        stroke = StrokeStyleBuilder(argb(color), width, opacity).apply(block).build()
    }

    fun noStroke() {
        stroke = null
    }

    internal fun build(): PolygonStyle =
        PolygonStyle(fill = fill, casing = casing, stroke = stroke)
}

/**
 * Fill options shared by point and polygon styles.
 */
@ExperimentalTiloApi
@TiloDsl
class FillStyleBuilder internal constructor(
    private val color: ColorValue,
    private val opacity: Double,
) {
    private var pattern: FillPattern? = null

    fun hatch(
        angleDegrees: Double = 45.0,
        spacing: Dp = 8.dp,
        strokeColor: Long = 0xFF111827,
        strokeWidth: Dp = 1.dp,
    ) {
        pattern = FillPattern.Hatch(
            angleDegrees = angleDegrees,
            spacing = spacing.toStyleUnit(),
            stroke = StrokeStyle(color = argb(strokeColor), width = strokeWidth.toStyleUnit()),
        )
    }

    fun hatch(
        angleDegrees: Double = 45.0,
        spacing: Double,
        strokeColor: Long = 0xFF111827,
        strokeWidth: Double = 1.0,
    ) {
        pattern = FillPattern.Hatch(
            angleDegrees = angleDegrees,
            spacing = spacing,
            stroke = StrokeStyle(color = argb(strokeColor), width = strokeWidth),
        )
    }

    fun dots(
        spacing: Dp = 8.dp,
        radius: Dp = 1.5.dp,
        color: Long = 0xFF111827,
    ) {
        pattern = FillPattern.Dots(
            spacing = spacing.toStyleUnit(),
            radius = radius.toStyleUnit(),
            color = argb(color),
        )
    }

    fun dots(
        spacing: Double,
        radius: Double = 1.5,
        color: Long = 0xFF111827,
    ) {
        pattern = FillPattern.Dots(
            spacing = spacing,
            radius = radius,
            color = argb(color),
        )
    }

    internal fun build(): FillStyle =
        FillStyle(color = color, opacity = opacity, pattern = pattern)
}

/**
 * Stroke options shared by point, line, and polygon styles.
 */
@ExperimentalTiloApi
@TiloDsl
class StrokeStyleBuilder internal constructor(
    private val color: ColorValue,
    private val width: Double,
    private val opacity: Double,
) {
    var lineCap: LineCap = LineCap.Butt
    var lineJoin: LineJoin = LineJoin.Miter
    private var dash: DashPattern? = null

    fun dash(first: Dp, second: Dp, phase: Dp = 0.dp) {
        dash = DashPattern(
            intervals = listOf(first.toStyleUnit(), second.toStyleUnit()),
            phase = phase.toStyleUnit(),
        )
    }

    fun dash(first: Dp, second: Dp, third: Dp, fourth: Dp, phase: Dp = 0.dp) {
        dash = DashPattern(
            intervals = listOf(
                first.toStyleUnit(),
                second.toStyleUnit(),
                third.toStyleUnit(),
                fourth.toStyleUnit(),
            ),
            phase = phase.toStyleUnit(),
        )
    }

    fun dash(vararg intervals: Double, phase: Double = 0.0) {
        dash = DashPattern(intervals.toList(), phase)
    }

    internal fun build(): StrokeStyle =
        StrokeStyle(
            color = color,
            width = width,
            opacity = opacity,
            lineCap = lineCap,
            lineJoin = lineJoin,
            dash = dash,
        )
}

private fun Dp.toStyleUnit(): Double = value.toDouble()
