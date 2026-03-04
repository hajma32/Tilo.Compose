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

            feature.label
                ?.takeIf { it.isNotBlank() }
                ?.let { labelText ->
                    labelAnchorWorld(feature.geometry)?.let { worldAnchor ->
                        commands += RenderLabel(
                            id = "$baseId:label",
                            text = labelText,
                            anchor = mapState.worldToScreen(worldAnchor),
                            style = style
                        )
                    }
                }
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

    private fun labelAnchorWorld(geometry: Geometry): Point? {
        val points = when (geometry) {
            is Point -> listOf(geometry)
            is MultiPoint -> geometry.points
            is LineString -> geometry.points
            is MultiLineString -> geometry.lines.flatMap { it.points }
            is Polygon -> geometry.rings.firstOrNull().orEmpty()
            is MultiPolygon -> geometry.polygons.flatMap { polygon ->
                polygon.rings.firstOrNull().orEmpty()
            }
        }
        if (points.isEmpty()) return null

        val minX = points.minOf { it.x }
        val minY = points.minOf { it.y }
        val maxX = points.maxOf { it.x }
        val maxY = points.maxOf { it.y }
        return Point(
            x = (minX + maxX) / 2.0,
            y = (minY + maxY) / 2.0
        )
    }
}
