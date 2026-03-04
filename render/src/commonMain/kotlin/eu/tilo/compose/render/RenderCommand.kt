package eu.tilo.compose.render

import tilo.compose.core.feature.BaseStyle
import tilo.compose.core.geometry.Point

sealed interface RenderCommand {
    val id: String
}

data class RenderPoint(
    override val id: String,
    val point: Point,
    val radius: Double = 15.0,
    val style: BaseStyle = BaseStyle(strokeColor = 0xFF1E88E5)
) : RenderCommand

data class RenderLineString(
    override val id: String,
    val points: List<Point>,
    val style: BaseStyle = BaseStyle(strokeColor = 0xFF1E88E5, strokeWidth = 2.0)
) : RenderCommand

data class RenderPolygon(
    override val id: String,
    val rings: List<List<Point>>,
    val style: BaseStyle = BaseStyle(
        strokeColor = 0xFF1E88E5,
        fillColor = 0x331E88E5,
        strokeWidth = 1.5
    )
) : RenderCommand

data class RenderLabel(
    override val id: String,
    val text: String,
    val anchor: Point,
    val style: BaseStyle = BaseStyle()
) : RenderCommand
