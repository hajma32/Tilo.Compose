package tilo.compose.draw

import tilo.compose.core.feature.ColorValue
import tilo.compose.core.feature.FillStyle
import tilo.compose.core.feature.LineCap
import tilo.compose.core.feature.LineJoin
import tilo.compose.core.feature.LineStyle
import tilo.compose.core.feature.PointShape
import tilo.compose.core.feature.PointStyle
import tilo.compose.core.feature.PolygonStyle
import tilo.compose.core.feature.StrokeStyle

interface DrawStyle {
    val point: PointStyle
    val line: LineStyle
    val polygon: PolygonStyle
}

data class DefaultDrawStyle(
    override val point: PointStyle = PointStyle(
        shape = PointShape.Circle,
        size = 12.0,
        fill = FillStyle(color = color(0xFFFFC107)),
        stroke = StrokeStyle(color = color(0xFF263238), width = 2.0),
    ),
    override val line: LineStyle = LineStyle(
        stroke = StrokeStyle(
            color = color(0xFFFFC107),
            width = 3.0,
            lineCap = LineCap.Round,
            lineJoin = LineJoin.Round,
        )
    ),
    override val polygon: PolygonStyle = PolygonStyle(
        fill = FillStyle(color = color(0x55FFC107)),
        stroke = StrokeStyle(
            color = color(0xFFFF8F00),
            width = 2.5,
            lineJoin = LineJoin.Round,
        )
    ),
) : DrawStyle

private fun color(argb: Long): ColorValue =
    ColorValue((argb and 0xFFFFFFFFL).toULong())
