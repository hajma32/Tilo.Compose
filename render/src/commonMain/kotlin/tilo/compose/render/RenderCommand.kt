@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import tilo.compose.core.feature.ColorValue
import tilo.compose.core.feature.LabelStyle
import tilo.compose.core.feature.LineStyle
import tilo.compose.core.feature.PointStyle
import tilo.compose.core.feature.PolygonStyle
import tilo.compose.core.geometry.Point

/** Platform-neutral command consumed by a [tilo.compose.render.backend.RenderBackend]. */
@ExperimentalTiloRenderingApi
sealed interface RenderCommand {
    val id: String
}

/** Draws a styled point at a map-projection coordinate. */
@ExperimentalTiloRenderingApi
data class RenderPoint(
    override val id: String,
    val point: Point,
    val style: PointStyle = PointStyle(),
) : RenderCommand

/** Draws a styled polyline through map-projection coordinates. */
@ExperimentalTiloRenderingApi
data class RenderLineString(
    override val id: String,
    val points: List<Point>,
    val style: LineStyle = LineStyle(),
) : RenderCommand

/** Draws a styled polygon whose first ring is exterior and remaining rings are holes. */
@ExperimentalTiloRenderingApi
data class RenderPolygon(
    override val id: String,
    val rings: List<List<Point>>,
    val style: PolygonStyle = PolygonStyle(),
) : RenderCommand

/** Draws a collision-managed label anchored in map-projection coordinates. */
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
