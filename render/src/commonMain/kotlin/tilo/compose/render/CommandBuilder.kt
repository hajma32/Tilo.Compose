@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import tilo.compose.core.feature.ColorValue
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.FeatureLayerStyle
import tilo.compose.core.feature.FillStyle
import tilo.compose.core.feature.GeometryStyle
import tilo.compose.core.feature.LabelStyle
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
import tilo.compose.core.map.MapState
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Builds a flat list of [RenderCommand]s visible in the current [MapState] view.
 *
 * Positioning is done exclusively via [MapState.worldToScreen] — no CRS knowledge here.
 */
internal object CommandBuilder {
    internal fun build(
        map: MapState,
        features: List<Feature>,
        layerId: String? = null,
        selectedFeatureKeys: Set<String> = emptySet(),
        layerStyle: FeatureLayerStyle = FeatureLayerStyle(),
    ): List<RenderCommand> =
        buildWithMetrics(
            map = map,
            features = features,
            layerId = layerId,
            selectedFeatureKeys = selectedFeatureKeys,
            layerStyle = layerStyle,
        ).commands

    internal fun buildWithMetrics(
        map: MapState,
        features: List<Feature>,
        layerId: String? = null,
        selectedFeatureKeys: Set<String> = emptySet(),
        layerStyle: FeatureLayerStyle = FeatureLayerStyle(),
    ): CommandBuildResult {
        val visible = visibleBounds(map)
        val resolvedLayerStyle = layerStyle.resolveAtZoom(map.zoom)
        var visibleFeatureCount = 0
        var geometryCommandCount = 0
        var labelCommandCount = 0

        val commands =
            buildList {
                features.forEach { feature ->
                    val featureBounds = feature.geometry.bounds()
                    if (!visible.intersects(featureBounds)) return@forEach
                    visibleFeatureCount += 1

                    val baseId = feature.key
                    val isSelected = layerId != null && feature.key in selectedFeatureKeys
                    val style = feature.resolvedStyle(isSelected, resolvedLayerStyle)

                    val geometryCommands = geometryToCommands(baseId, feature.geometry, style.geometry)
                    geometryCommandCount += geometryCommands.size
                    addAll(geometryCommands)

                    feature.label?.takeIf { resolvedLayerStyle.labelsVisible && it.isNotBlank() }?.let { label ->
                        labelPlacement(feature.geometry, map)?.let { placement ->
                            labelCommandCount += 1
                            add(
                                RenderLabel(
                                    id = "$baseId:label",
                                    text = label,
                                    anchor = placement.anchor,
                                    style = style.label,
                                    labelPriority = feature.labelPriority,
                                    selected = isSelected,
                                    rotationDegrees = placement.rotationDegrees,
                                    followsLine = placement.followsLine,
                                ),
                            )
                        }
                    }
                }
            }
        return CommandBuildResult(
            commands = commands,
            visibleFeatureCount = visibleFeatureCount,
            geometryCommandCount = geometryCommandCount,
            labelCommandCount = labelCommandCount,
        )
    }

    private fun visibleBounds(map: MapState): BoundingBox {
        val visible = map.viewportBounds()

        val padX = (visible.maxX - visible.minX) * 0.1
        val padY = (visible.maxY - visible.minY) * 0.1

        return BoundingBox.fromExtents(
            minX = visible.minX - padX,
            maxX = visible.maxX + padX,
            minY = visible.minY - padY,
            maxY = visible.maxY + padY,
        )
    }

    private fun geometryToCommands(
        baseId: String,
        geometry: Geometry,
        style: GeometryStyle?,
    ): List<RenderCommand> {
        val pointStyle = style.toPointStyle()
        val hidePoints =
            pointStyle.icon == null &&
                pointStyle.fill?.color == ColorValue.Transparent &&
                pointStyle.stroke == null
        return when (geometry) {
            is Point ->
                if (hidePoints) {
                    emptyList()
                } else {
                    listOf(
                        RenderPoint(id = "$baseId:point", point = geometry, style = pointStyle),
                    )
                }

            is MultiPoint ->
                if (hidePoints) {
                    emptyList()
                } else {
                    geometry.points.mapIndexed { i, p ->
                        RenderPoint(id = "$baseId:point:$i", point = p, style = pointStyle)
                    }
                }

            is LineString ->
                listOf(
                    RenderLineString(id = "$baseId:line", points = geometry.points, style = style.toLineStyle()),
                )

            is MultiLineString ->
                geometry.lines.mapIndexed { i, line ->
                    RenderLineString(id = "$baseId:line:$i", points = line.points, style = style.toLineStyle())
                }

            is Polygon ->
                listOf(
                    RenderPolygon(
                        id = "$baseId:polygon",
                        rings = geometry.rings,
                        style = style.toPolygonStyle(),
                    ),
                )

            is MultiPolygon ->
                geometry.polygons.mapIndexed { i, polygon ->
                    RenderPolygon(
                        id = "$baseId:polygon:$i",
                        rings = polygon.rings,
                        style = style.toPolygonStyle(),
                    )
                }
        }
    }

    private fun labelPlacement(
        geometry: Geometry,
        map: MapState,
    ): LabelPlacement? =
        when (geometry) {
            is LineString -> lineLabelPlacement(geometry.points, map)
            is MultiLineString ->
                geometry.lines
                    .maxByOrNull { line -> line.screenLength(map) }
                    ?.let { lineLabelPlacement(it.points, map) }
            else ->
                labelAnchorWorld(geometry)?.let { anchor ->
                    LabelPlacement(anchor = anchor, rotationDegrees = 0.0, followsLine = false)
                }
        }

    private fun labelAnchorWorld(geometry: Geometry): Point? {
        val points =
            when (geometry) {
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
            y = (points.minOf { it.y } + points.maxOf { it.y }) / 2.0,
        )
    }

    private fun lineLabelPlacement(
        points: List<Point>,
        map: MapState,
    ): LabelPlacement? {
        if (points.isEmpty()) return null
        if (points.size == 1) return LabelPlacement(anchor = points.first(), rotationDegrees = 0.0, followsLine = false)

        val segments =
            points.zipWithNext().mapNotNull { (start, end) ->
                val startScreen = map.worldToScreen(start)
                val endScreen = map.worldToScreen(end)
                val dx = endScreen.x - startScreen.x
                val dy = endScreen.y - startScreen.y
                val length = hypot(dx, dy)
                if (length <= 0.0) {
                    null
                } else {
                    ScreenSegment(start = start, end = end, dx = dx, dy = dy, length = length)
                }
            }
        if (segments.isEmpty()) {
            return LabelPlacement(
                anchor = points.first(),
                rotationDegrees = 0.0,
                followsLine = false,
            )
        }

        val target = segments.sumOf { it.length } / 2.0
        var walked = 0.0
        val segment =
            segments.firstOrNull { segment ->
                if (walked + segment.length >= target) {
                    true
                } else {
                    walked += segment.length
                    false
                }
            } ?: segments.last()

        val ratio = ((target - walked) / segment.length).coerceIn(0.0, 1.0)
        val anchor =
            Point(
                x = segment.start.x + (segment.end.x - segment.start.x) * ratio,
                y = segment.start.y + (segment.end.y - segment.start.y) * ratio,
            )
        val angle = atan2(segment.dy, segment.dx) * 180.0 / PI
        return LabelPlacement(
            anchor = anchor,
            rotationDegrees = angle.normalizedForReadableText(),
            followsLine = true,
        )
    }

    private fun LineString.screenLength(map: MapState): Double =
        points.zipWithNext().sumOf { (start, end) ->
            val startScreen = map.worldToScreen(start)
            val endScreen = map.worldToScreen(end)
            hypot(endScreen.x - startScreen.x, endScreen.y - startScreen.y)
        }

    private fun Double.normalizedForReadableText(): Double {
        var angle = this
        while (angle <= -90.0) angle += 180.0
        while (angle > 90.0) angle -= 180.0
        return angle
    }
}

internal data class CommandBuildResult(
    val commands: List<RenderCommand>,
    val visibleFeatureCount: Int,
    val geometryCommandCount: Int,
    val labelCommandCount: Int,
)

private data class LabelPlacement(
    val anchor: Point,
    val rotationDegrees: Double,
    val followsLine: Boolean,
)

private data class ScreenSegment(
    val start: Point,
    val end: Point,
    val dx: Double,
    val dy: Double,
    val length: Double,
)

private data class ResolvedFeatureStyle(
    val geometry: GeometryStyle?,
    val label: LabelStyle,
)

private fun Feature.resolvedStyle(
    isSelected: Boolean,
    layerStyle: FeatureLayerStyle,
): ResolvedFeatureStyle {
    val baseGeometryStyle = style ?: layerStyle.geometryStyleFor(geometry)
    if (!isSelected) {
        return ResolvedFeatureStyle(
            geometry = baseGeometryStyle,
            label = labelStyle ?: layerStyle.label ?: LabelStyle(color = baseGeometryStyle.labelColor()),
        )
    }

    return ResolvedFeatureStyle(
        geometry =
            selectedStyle ?: layerStyle.selectedGeometryStyleFor(geometry)
                ?: geometry.defaultSelectedStyle(baseGeometryStyle),
        label =
            selectedLabelStyle ?: layerStyle.selectedLabel ?: labelStyle ?: layerStyle.label ?: LabelStyle(
                color = baseGeometryStyle.labelColor(),
            ),
    )
}

private fun FeatureLayerStyle.geometryStyleFor(geometry: Geometry): GeometryStyle? =
    when (geometry) {
        is Point, is MultiPoint -> point
        is LineString, is MultiLineString -> line
        is Polygon, is MultiPolygon -> polygon
    }

private fun FeatureLayerStyle.selectedGeometryStyleFor(geometry: Geometry): GeometryStyle? =
    when (geometry) {
        is Point, is MultiPoint -> selectedPoint
        is LineString, is MultiLineString -> selectedLine
        is Polygon, is MultiPolygon -> selectedPolygon
    }

private fun Geometry.defaultSelectedStyle(baseStyle: GeometryStyle?): GeometryStyle =
    when (this) {
        is Point, is MultiPoint ->
            PointStyle(
                shape = (baseStyle as? PointStyle)?.shape ?: PointStyle().shape,
                size = maxOf((baseStyle as? PointStyle)?.size ?: PointStyle().size, 22.0),
                fill = (baseStyle as? PointStyle)?.fill ?: FillStyle(color = ColorValue(0xFFFFD54Fu)),
                stroke = StrokeStyle(color = ColorValue(0xFF111827u), width = 4.0),
            )
        is LineString, is MultiLineString ->
            LineStyle(
                stroke =
                    StrokeStyle(
                        color = ColorValue(0xFFFFD54Fu),
                        width = maxOf((baseStyle as? LineStyle)?.stroke?.width ?: 2.0, 7.0),
                        lineCap = (baseStyle as? LineStyle)?.stroke?.lineCap ?: tilo.compose.core.feature.LineCap.Round,
                        lineJoin =
                            (baseStyle as? LineStyle)?.stroke?.lineJoin ?: tilo.compose.core.feature.LineJoin.Round,
                    ),
            )
        is Polygon, is MultiPolygon ->
            PolygonStyle(
                fill = (baseStyle as? PolygonStyle)?.fill ?: FillStyle(color = ColorValue(0x33FFD54Fu)),
                stroke =
                    StrokeStyle(
                        color = ColorValue(0xFFFFD54Fu),
                        width = maxOf((baseStyle as? PolygonStyle)?.stroke?.width ?: 2.0, 5.0),
                        lineJoin = tilo.compose.core.feature.LineJoin.Round,
                    ),
            )
    }

private fun GeometryStyle?.toPointStyle(): PointStyle =
    when (this) {
        is PointStyle -> this
        else -> PointStyle()
    }

private fun GeometryStyle?.toLineStyle(): LineStyle =
    when (this) {
        is LineStyle -> this
        else -> LineStyle()
    }

private fun GeometryStyle?.toPolygonStyle(): PolygonStyle =
    when (this) {
        is PolygonStyle -> this
        else -> PolygonStyle()
    }

private fun GeometryStyle?.labelColor(): ColorValue =
    when (this) {
        is PointStyle -> stroke?.color ?: fill?.color ?: ColorValue.Black
        is LineStyle -> stroke.color
        is PolygonStyle -> stroke?.color ?: fill?.color ?: ColorValue.Black
        else -> ColorValue.Black
    }
