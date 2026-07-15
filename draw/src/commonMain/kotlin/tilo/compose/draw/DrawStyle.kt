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
    override val point: PointStyle =
        PointStyle(
            shape = PointShape.Circle,
            size = 14.0,
            fill = FillStyle(color = color(0xFFF97316)),
            stroke = StrokeStyle(color = color(0xFFFFFFFF), width = 3.75),
        ),
    override val line: LineStyle =
        LineStyle(
            casing =
                StrokeStyle(
                    color = color(0xFFFFFFFF),
                    width = 7.0,
                    lineCap = LineCap.Round,
                    lineJoin = LineJoin.Round,
                ),
            stroke =
                StrokeStyle(
                    color = color(0xFFF97316),
                    width = 3.75,
                    lineCap = LineCap.Round,
                    lineJoin = LineJoin.Round,
                ),
        ),
    override val polygon: PolygonStyle =
        PolygonStyle(
            fill = FillStyle(color = color(0x33F97316)),
            casing =
                StrokeStyle(
                    color = color(0xFFFFFFFF),
                    width = 7.0,
                    lineJoin = LineJoin.Round,
                ),
            stroke =
                StrokeStyle(
                    color = color(0xFFF97316),
                    width = 3.75,
                    lineJoin = LineJoin.Round,
                ),
        ),
) : DrawStyle

private fun color(argb: Long): ColorValue = ColorValue((argb and 0xFFFFFFFFL).toULong())
