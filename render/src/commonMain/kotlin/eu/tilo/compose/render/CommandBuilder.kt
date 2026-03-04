package eu.tilo.compose.render

import tilo.compose.core.feature.BaseStyle
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Geometry
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.MultiLineString
import tilo.compose.core.geometry.MultiPoint
import tilo.compose.core.geometry.MultiPolygon
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon
import tilo.compose.core.map.MapState

object CommandBuilder {

    fun build(mapState: MapState, features: List<Feature>): List<RenderCommand> {
        val commands = mutableListOf<RenderCommand>()
        features.forEach { feature ->
            val baseId = feature.key
            val style = feature.style ?: BaseStyle()
            commands.addAll(geometryToCommands(baseId, mapState, feature.geometry, style))
        }
        return commands
    }

    private fun geometryToCommands(
        baseId: String,
        mapState: MapState,
        geometry: Geometry,
        style: BaseStyle
    ): List<RenderCommand> {
        return when (geometry) {
            is Point -> listOf(
                RenderPoint(
                    id = "$baseId:point",
                    point = mapState.worldToScreen(geometry),
                    style = style
                )
            )
            is MultiPoint -> geometry.points.mapIndexed { i, p ->
                RenderPoint(
                    id = "$baseId:point:$i",
                    point = mapState.worldToScreen(p),
                    style = style
                )
            }
            is LineString -> listOf(
                RenderLineString(
                    id = "$baseId:line",
                    points = geometry.points.map(mapState::worldToScreen),
                    style = style
                )
            )
            is MultiLineString -> geometry.lines.mapIndexed { i, line ->
                RenderLineString(
                    id = "$baseId:line:$i",
                    points = line.points.map(mapState::worldToScreen),
                    style = style
                )
            }
            is Polygon -> listOf(
                RenderPolygon(
                    id = "$baseId:polygon",
                    rings = geometry.rings.map { ring -> ring.map(mapState::worldToScreen) },
                    style = style
                )
            )
            is MultiPolygon -> geometry.polygons.mapIndexed { i, polygon ->
                RenderPolygon(
                    id = "$baseId:polygon:$i",
                    rings = polygon.rings.map { ring -> ring.map(mapState::worldToScreen) },
                    style = style
                )
            }
        }
    }
}
