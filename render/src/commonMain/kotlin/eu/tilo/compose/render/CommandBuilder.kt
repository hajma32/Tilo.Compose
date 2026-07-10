package eu.tilo.compose.render

import tilo.compose.core.feature.BaseStyle
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.geometry.Geometry
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.MultiLineString
import tilo.compose.core.geometry.MultiPoint
import tilo.compose.core.geometry.MultiPolygon
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon
import tilo.compose.core.geometry.bounds
import tilo.compose.core.map.Map

/**
 * Builds a flat list of [RenderCommand]s visible in the current [Map] view.
 *
 * Positioning is done exclusively via [Map.worldToScreen] — no CRS knowledge here.
 */
object CommandBuilder {

    internal fun build(
        map: Map,
        features: List<Feature>
    ): List<RenderCommand> {
        val visible = visibleBounds(map)

        return buildList {
            features.forEach { feature ->
                val featureBounds = feature.geometry.bounds()
                if (!visible.intersects(featureBounds)) return@forEach

                val baseId = feature.key
                val style = feature.style ?: BaseStyle()

                addAll(geometryToCommands(baseId, map, feature.geometry, style))

                feature.label?.takeIf { it.isNotBlank() }?.let { label ->
                    labelAnchorWorld(feature.geometry)?.let { anchor ->
                        add(
                            RenderLabel(
                                id = "$baseId:label",
                                text = label,
                                anchor = anchor,
                                style = style
                            )
                        )
                    }
                }
            }
        }
    }

    private fun visibleBounds(map: Map): BoundingBox {
        val topLeft = map.screenToWorld(Point(0.0, 0.0))
        val bottomRight = map.screenToWorld(Point(map.viewport.width.toDouble(), map.viewport.height.toDouble()))

        val minX = minOf(topLeft.x, bottomRight.x)
        val maxX = maxOf(topLeft.x, bottomRight.x)
        val minY = minOf(topLeft.y, bottomRight.y)
        val maxY = maxOf(topLeft.y, bottomRight.y)

        val padX = (maxX - minX) * 0.1
        val padY = (maxY - minY) * 0.1

        return BoundingBox(
            topLeft = Point(minX - padX, maxY + padY),
            topRight = Point(maxX + padX, maxY + padY),
            bottomLeft = Point(minX - padX, minY - padY),
            bottomRight = Point(maxX + padX, minY - padY)
        )
    }

    private fun geometryToCommands(
        baseId: String,
        map: Map,
        geometry: Geometry,
        style: BaseStyle
    ): List<RenderCommand> {
        val hidePoints = style.fillColor == 0x00000000L
        return when (geometry) {
            is Point -> if (hidePoints) emptyList() else listOf(
                RenderPoint(id = "$baseId:point", point = geometry, style = style)
            )

            is MultiPoint -> if (hidePoints) emptyList() else geometry.points.mapIndexed { i, p ->
                RenderPoint(id = "$baseId:point:$i", point = p, style = style)
            }

            is LineString -> listOf(
                RenderLineString(id = "$baseId:line", points = geometry.points, style = style)
            )

            is MultiLineString -> geometry.lines.mapIndexed { i, line ->
                RenderLineString(id = "$baseId:line:$i", points = line.points, style = style)
            }

            is Polygon -> listOf(
                RenderPolygon(
                    id = "$baseId:polygon",
                    rings = geometry.rings,
                    style = style
                )
            )

            is MultiPolygon -> geometry.polygons.mapIndexed { i, polygon ->
                RenderPolygon(
                    id = "$baseId:polygon:$i",
                    rings = polygon.rings,
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
            is MultiPolygon -> geometry.polygons.flatMap { it.rings.firstOrNull().orEmpty() }
        }
        if (points.isEmpty()) return null
        return Point(
            x = (points.minOf { it.x } + points.maxOf { it.x }) / 2.0,
            y = (points.minOf { it.y } + points.maxOf { it.y }) / 2.0
        )
    }
}
