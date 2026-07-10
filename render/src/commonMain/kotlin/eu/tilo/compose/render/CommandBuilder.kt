package eu.tilo.compose.render

import tilo.compose.core.feature.BaseStyle
import tilo.compose.core.feature.ColorValue
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.FillStyle
import tilo.compose.core.feature.GeometryStyle
import tilo.compose.core.feature.LineStyle
import tilo.compose.core.feature.PointStyle
import tilo.compose.core.feature.PolygonStyle
import tilo.compose.core.feature.StrokeStyle
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
                val style = feature.style

                addAll(geometryToCommands(baseId, map, feature.geometry, style))

                feature.label?.takeIf { it.isNotBlank() }?.let { label ->
                    labelAnchorWorld(feature.geometry)?.let { anchor ->
                        add(
                            RenderLabel(
                                id = "$baseId:label",
                                text = label,
                                anchor = anchor,
                                textColor = style.labelColor()
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
        style: GeometryStyle?
    ): List<RenderCommand> {
        val pointStyle = style.toPointStyle()
        val hidePoints = pointStyle.fill?.color == ColorValue.Transparent && pointStyle.stroke == null
        return when (geometry) {
            is Point -> if (hidePoints) emptyList() else listOf(
                RenderPoint(id = "$baseId:point", point = geometry, style = pointStyle)
            )

            is MultiPoint -> if (hidePoints) emptyList() else geometry.points.mapIndexed { i, p ->
                RenderPoint(id = "$baseId:point:$i", point = p, style = pointStyle)
            }

            is LineString -> listOf(
                RenderLineString(id = "$baseId:line", points = geometry.points, style = style.toLineStyle())
            )

            is MultiLineString -> geometry.lines.mapIndexed { i, line ->
                RenderLineString(id = "$baseId:line:$i", points = line.points, style = style.toLineStyle())
            }

            is Polygon -> listOf(
                RenderPolygon(
                    id = "$baseId:polygon",
                    rings = geometry.rings,
                    style = style.toPolygonStyle()
                )
            )

            is MultiPolygon -> geometry.polygons.mapIndexed { i, polygon ->
                RenderPolygon(
                    id = "$baseId:polygon:$i",
                    rings = polygon.rings,
                    style = style.toPolygonStyle()
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

private fun GeometryStyle?.toPointStyle(): PointStyle =
    when (this) {
        is PointStyle -> this
        is BaseStyle -> PointStyle(
            fill = fillColor?.let { FillStyle(color = it.toColorValue()) },
            stroke = strokeColor?.let {
                StrokeStyle(
                    color = it.toColorValue(),
                    width = strokeWidth ?: 2.0,
                )
            },
        )
        else -> PointStyle()
    }

private fun GeometryStyle?.toLineStyle(): LineStyle =
    when (this) {
        is LineStyle -> this
        is BaseStyle -> LineStyle(
            stroke = StrokeStyle(
                color = strokeColor?.toColorValue() ?: ColorValue.Blue,
                width = strokeWidth ?: 2.0,
            )
        )
        else -> LineStyle()
    }

private fun GeometryStyle?.toPolygonStyle(): PolygonStyle =
    when (this) {
        is PolygonStyle -> this
        is BaseStyle -> PolygonStyle(
            fill = fillColor?.let { FillStyle(color = it.toColorValue()) },
            stroke = strokeColor?.let {
                StrokeStyle(
                    color = it.toColorValue(),
                    width = strokeWidth ?: 1.5,
                )
            },
        )
        else -> PolygonStyle()
    }

private fun GeometryStyle?.labelColor(): ColorValue =
    when (this) {
        is PointStyle -> stroke?.color ?: fill?.color ?: ColorValue.Black
        is LineStyle -> stroke.color
        is PolygonStyle -> stroke?.color ?: fill?.color ?: ColorValue.Black
        is BaseStyle -> strokeColor?.toColorValue() ?: fillColor?.toColorValue() ?: ColorValue.Black
        else -> ColorValue.Black
    }

private fun Long.toColorValue(): ColorValue =
    ColorValue((this and 0xFFFFFFFFL).toULong())
