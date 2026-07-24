@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import tilo.compose.core.feature.LineStyle
import tilo.compose.core.feature.PointStyle
import tilo.compose.core.feature.PolygonStyle

/**
 * Geometry commands grouped by their fully resolved style inside one scene layer.
 *
 * Kotlin maps use the style hash to locate candidates and full data-class equality to confirm a
 * match, so hash collisions cannot merge different styles. Linked maps preserve the order in
 * which effective styles first occur in the layer.
 */
internal data class GeometryCommandBatches(
    val points: Map<PointStyle, List<RenderPoint>>,
    val lines: Map<LineStyle, List<RenderLineString>>,
    val polygons: Map<PolygonStyle, List<RenderPolygon>>,
) {
    companion object {
        fun build(commands: List<RenderCommand>): GeometryCommandBatches {
            val points = linkedMapOf<PointStyle, MutableList<RenderPoint>>()
            val lines = linkedMapOf<LineStyle, MutableList<RenderLineString>>()
            val polygons = linkedMapOf<PolygonStyle, MutableList<RenderPolygon>>()
            commands.forEach { command ->
                when (command) {
                    is RenderPoint -> points.getOrPut(command.style, ::mutableListOf).add(command)
                    is RenderLineString -> lines.getOrPut(command.style, ::mutableListOf).add(command)
                    is RenderPolygon -> polygons.getOrPut(command.style, ::mutableListOf).add(command)
                    is RenderLabel -> Unit
                }
            }
            return GeometryCommandBatches(points = points, lines = lines, polygons = polygons)
        }
    }
}
