package eu.tilo.compose.render

import tilo.compose.core.feature.ColorValue
import tilo.compose.core.feature.LineStyle
import tilo.compose.core.feature.PointStyle
import tilo.compose.core.feature.PolygonStyle
import tilo.compose.core.geometry.Point

sealed interface RenderCommand {
    val id: String
}

data class RenderPoint(
    override val id: String,
    val point: Point,
    val style: PointStyle = PointStyle()
) : RenderCommand

data class RenderLineString(
    override val id: String,
    val points: List<Point>,
    val style: LineStyle = LineStyle()
) : RenderCommand

data class RenderPolygon(
    override val id: String,
    val rings: List<List<Point>>,
    val style: PolygonStyle = PolygonStyle()
) : RenderCommand

data class RenderLabel(
    override val id: String,
    val text: String,
    val anchor: Point,
    val textColor: ColorValue = ColorValue.Black,
) : RenderCommand
