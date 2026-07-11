package tilo.compose.dsl

import tilo.compose.core.feature.ColorValue
import tilo.compose.core.feature.DashPattern
import tilo.compose.core.feature.FeatureLayerStyle
import tilo.compose.core.feature.FillPattern
import tilo.compose.core.feature.FillStyle
import tilo.compose.core.feature.LabelStyle
import tilo.compose.core.feature.LineCap
import tilo.compose.core.feature.LineJoin
import tilo.compose.core.feature.LineStyle
import tilo.compose.core.feature.PointIcon
import tilo.compose.core.feature.PointShape
import tilo.compose.core.feature.PointStyle
import tilo.compose.core.feature.PolygonStyle
import tilo.compose.core.feature.StrokeStyle

/**
 * Converts an ARGB long literal, for example `0xFF1E88E5`, to a GeoCore color.
 */
fun argb(value: Long): ColorValue =
    ColorValue((value and 0xFFFFFFFFL).toULong())

/**
 * Builds style for point geometries.
 */
fun pointStyle(block: PointStyleBuilder.() -> Unit = {}): PointStyle =
    PointStyleBuilder().apply(block).build()

/**
 * Builds style for line geometries.
 */
fun lineStyle(block: LineStyleBuilder.() -> Unit = {}): LineStyle =
    LineStyleBuilder().apply(block).build()

/**
 * Builds style for polygon geometries.
 */
fun polygonStyle(block: PolygonStyleBuilder.() -> Unit = {}): PolygonStyle =
    PolygonStyleBuilder().apply(block).build()

/**
 * Builds a layer-level style object with defaults for every vector geometry
 * type and its selected state.
 */
fun featureLayerStyle(block: FeatureLayerStyleBuilder.() -> Unit = {}): FeatureLayerStyle =
    FeatureLayerStyleBuilder().apply(block).build()

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

    fun label(color: Long = 0xFF111827) {
        label = LabelStyle(color = argb(color))
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

    fun selectedLabel(color: Long = 0xFF111827) {
        selectedLabel = LabelStyle(color = argb(color))
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
 * Style builder used by [pointStyle].
 */
class PointStyleBuilder {
    var shape: PointShape = PointShape.Circle
    var size: Double = 10.0
    var icon: PointIcon? = null

    private var fill: FillStyle? = FillStyle(color = ColorValue.Blue)
    private var stroke: StrokeStyle? = StrokeStyle(color = ColorValue.White, width = 2.0)

    fun fill(color: Long, opacity: Double = 1.0, block: FillStyleBuilder.() -> Unit = {}) {
        fill = FillStyleBuilder(argb(color), opacity).apply(block).build()
    }

    fun noFill() {
        fill = null
    }

    fun stroke(
        color: Long,
        width: Double = 1.0,
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
            size = size,
            fill = fill,
            stroke = stroke,
            icon = icon,
        )
}

/**
 * Style builder used by [lineStyle].
 */
class LineStyleBuilder {
    private var stroke: StrokeStyle = StrokeStyle(color = ColorValue.Blue, width = 2.0)

    fun stroke(
        color: Long,
        width: Double = 1.0,
        opacity: Double = 1.0,
        block: StrokeStyleBuilder.() -> Unit = {},
    ) {
        stroke = StrokeStyleBuilder(argb(color), width, opacity).apply(block).build()
    }

    internal fun build(): LineStyle =
        LineStyle(stroke = stroke)
}

/**
 * Style builder used by [polygonStyle].
 */
class PolygonStyleBuilder {
    private var fill: FillStyle? = FillStyle(color = argb(0x331E88E5))
    private var stroke: StrokeStyle? = StrokeStyle(color = ColorValue.Blue, width = 1.5)

    fun fill(color: Long, opacity: Double = 1.0, block: FillStyleBuilder.() -> Unit = {}) {
        fill = FillStyleBuilder(argb(color), opacity).apply(block).build()
    }

    fun noFill() {
        fill = null
    }

    fun stroke(
        color: Long,
        width: Double = 1.0,
        opacity: Double = 1.0,
        block: StrokeStyleBuilder.() -> Unit = {},
    ) {
        stroke = StrokeStyleBuilder(argb(color), width, opacity).apply(block).build()
    }

    fun noStroke() {
        stroke = null
    }

    internal fun build(): PolygonStyle =
        PolygonStyle(fill = fill, stroke = stroke)
}

/**
 * Fill options shared by point and polygon styles.
 */
class FillStyleBuilder internal constructor(
    private val color: ColorValue,
    private val opacity: Double,
) {
    private var pattern: FillPattern? = null

    fun hatch(
        angleDegrees: Double = 45.0,
        spacing: Double = 8.0,
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
        spacing: Double = 8.0,
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
class StrokeStyleBuilder internal constructor(
    private val color: ColorValue,
    private val width: Double,
    private val opacity: Double,
) {
    var lineCap: LineCap = LineCap.Butt
    var lineJoin: LineJoin = LineJoin.Miter
    private var dash: DashPattern? = null

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
