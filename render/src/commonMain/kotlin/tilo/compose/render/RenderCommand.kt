@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import tilo.compose.core.feature.ColorValue
import tilo.compose.core.feature.LabelStyle
import tilo.compose.core.feature.LineStyle
import tilo.compose.core.feature.PointStyle
import tilo.compose.core.feature.PolygonStyle
import tilo.compose.core.geometry.Point

@ExperimentalTiloRenderingApi
sealed interface RenderCommand {
    val id: String
}

@ExperimentalTiloRenderingApi
data class RenderPoint(
    override val id: String,
    val point: Point,
    val style: PointStyle = PointStyle(),
) : RenderCommand

@ExperimentalTiloRenderingApi
data class RenderLineString(
    override val id: String,
    val points: List<Point>,
    val style: LineStyle = LineStyle(),
) : RenderCommand

@ExperimentalTiloRenderingApi
data class RenderPolygon(
    override val id: String,
    val rings: List<List<Point>>,
    val style: PolygonStyle = PolygonStyle(),
) : RenderCommand

@ExperimentalTiloRenderingApi
data class RenderLabel(
    override val id: String,
    val text: String,
    val anchor: Point,
    val style: LabelStyle = LabelStyle(color = ColorValue.Black),
    val labelPriority: Int? = null,
    val selected: Boolean = false,
    val rotationDegrees: Double = 0.0,
    val followsLine: Boolean = false,
) : RenderCommand
