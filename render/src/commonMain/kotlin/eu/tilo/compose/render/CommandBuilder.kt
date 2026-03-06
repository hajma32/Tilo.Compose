package eu.tilo.compose.render

import kotlin.math.pow
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
import tilo.compose.core.projection.Wgs84WebMercatorProjection

object CommandBuilder {

    private data class WorldBounds(
        val minX: Double,
        val minY: Double,
        val maxX: Double,
        val maxY: Double
    )

    private data class ScreenTransform(
        val scale: Double,
        val tx: Double,
        val ty: Double
    ) {
        fun toScreen(point: MercatorPoint): Point {
            return Point(
                x = point.u * scale + tx,
                y = point.v * scale + ty
            )
        }
    }

    private data class ProjectedCacheEntry(
        val geometryHash: Int,
        val projected: ProjectedGeometry
    )

    private const val TILE_SIZE = 256.0
    private const val MAX_BOUNDS_CACHE_SIZE = 50_000
    private const val MAX_PROJECTED_CACHE_SIZE = 50_000

    private val geometryBoundsCache = linkedMapOf<String, WorldBounds>()
    private val projectedGeometryCache = linkedMapOf<String, ProjectedCacheEntry>()

    fun build(mapState: MapState, features: List<Feature>): List<RenderCommand> {
        val commands = mutableListOf<RenderCommand>()
        val visible = visibleWorldBounds(mapState)
        val useProjectedPath = mapState.projection === Wgs84WebMercatorProjection
        val transform = if (useProjectedPath) screenTransform(mapState) else null

        features.forEach { feature ->
            val featureBounds = worldBoundsForFeature(feature)
            if (!intersects(visible, featureBounds)) return@forEach

            val baseId = feature.key
            val style = feature.style ?: BaseStyle()

            if (useProjectedPath && transform != null) {
                val projected = projectedGeometryForFeature(feature)
                commands.addAll(projectedToCommands(baseId, projected, transform, style))

                feature.label
                    ?.takeIf { it.isNotBlank() }
                    ?.let { labelText ->
                        val anchor = projectedAnchor(projected)
                        commands += RenderLabel(
                            id = "$baseId:label",
                            text = labelText,
                            anchor = transform.toScreen(anchor),
                            style = style
                        )
                    }
            } else {
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
        }

        return commands
    }

    private fun screenTransform(mapState: MapState): ScreenTransform {
        val centerProjected = projectPoint(mapState.center)
        val scale = TILE_SIZE * 2.0.pow(mapState.zoom)
        val tx = -centerProjected.u * scale + mapState.viewport.width / 2.0
        val ty = -centerProjected.v * scale + mapState.viewport.height / 2.0
        return ScreenTransform(scale = scale, tx = tx, ty = ty)
    }

    private fun worldBoundsForFeature(feature: Feature): WorldBounds {
        geometryBoundsCache[feature.key]?.let { return it }

        val computed = geometryWorldBounds(feature.geometry)
        geometryBoundsCache[feature.key] = computed
        trimBoundsCacheIfNeeded()
        return computed
    }

    private fun projectedGeometryForFeature(feature: Feature): ProjectedGeometry {
        val geometryHash = feature.geometry.hashCode()
        val cached = projectedGeometryCache[feature.key]
        if (cached != null && cached.geometryHash == geometryHash) return cached.projected

        val projected = projectGeometry(feature.geometry)
        projectedGeometryCache[feature.key] = ProjectedCacheEntry(geometryHash = geometryHash, projected = projected)
        trimProjectedCacheIfNeeded()
        return projected
    }

    private fun trimBoundsCacheIfNeeded() {
        while (geometryBoundsCache.size > MAX_BOUNDS_CACHE_SIZE) {
            val oldestKey = geometryBoundsCache.keys.firstOrNull() ?: return
            geometryBoundsCache.remove(oldestKey)
        }
    }

    private fun trimProjectedCacheIfNeeded() {
        while (projectedGeometryCache.size > MAX_PROJECTED_CACHE_SIZE) {
            val oldestKey = projectedGeometryCache.keys.firstOrNull() ?: return
            projectedGeometryCache.remove(oldestKey)
        }
    }

    private fun visibleWorldBounds(mapState: MapState): WorldBounds {
        val topLeft = mapState.screenToWorld(Point(0.0, 0.0))
        val bottomRight = mapState.screenToWorld(
            Point(mapState.viewport.width.toDouble(), mapState.viewport.height.toDouble())
        )

        val minX = minOf(topLeft.x, bottomRight.x)
        val maxX = maxOf(topLeft.x, bottomRight.x)
        val minY = minOf(topLeft.y, bottomRight.y)
        val maxY = maxOf(topLeft.y, bottomRight.y)

        val padX = (maxX - minX) * 0.1
        val padY = (maxY - minY) * 0.1

        return WorldBounds(
            minX = minX - padX,
            minY = minY - padY,
            maxX = maxX + padX,
            maxY = maxY + padY
        )
    }

    private fun geometryWorldBounds(geometry: Geometry): WorldBounds {
        val points = when (geometry) {
            is Point -> listOf(geometry)
            is MultiPoint -> geometry.points
            is LineString -> geometry.points
            is MultiLineString -> geometry.lines.flatMap { it.points }
            is Polygon -> geometry.rings.flatten()
            is MultiPolygon -> geometry.polygons.flatMap { it.rings.flatten() }
        }

        val minX = points.minOf { it.x }
        val minY = points.minOf { it.y }
        val maxX = points.maxOf { it.x }
        val maxY = points.maxOf { it.y }

        return WorldBounds(minX = minX, minY = minY, maxX = maxX, maxY = maxY)
    }

    private fun intersects(a: WorldBounds, b: WorldBounds): Boolean {
        if (a.maxX < b.minX || b.maxX < a.minX) return false
        if (a.maxY < b.minY || b.maxY < a.minY) return false
        return true
    }

    private fun projectedToCommands(
        baseId: String,
        geometry: ProjectedGeometry,
        transform: ScreenTransform,
        style: BaseStyle
    ): List<RenderCommand> {
        val hidePointMarkers = style.fillColor == 0x00000000L
        return when (geometry) {
            is ProjectedPointGeometry -> if (hidePointMarkers) {
                emptyList()
            } else {
                listOf(
                 RenderPoint(
                     id = "$baseId:point",
                     point = transform.toScreen(geometry.point),
                     style = style
                 )
                )
            }

            is ProjectedMultiPointGeometry -> if (hidePointMarkers) {
                emptyList()
            } else {
                geometry.points.mapIndexed { i, point ->
                    RenderPoint(
                        id = "$baseId:point:$i",
                        point = transform.toScreen(point),
                        style = style
                    )
                }
            }

            is ProjectedLineGeometry -> listOf(
                RenderLineString(
                    id = "$baseId:line",
                    points = geometry.points.map(transform::toScreen),
                    style = style
                )
            )

            is ProjectedMultiLineGeometry -> geometry.lines.mapIndexed { i, line ->
                RenderLineString(
                    id = "$baseId:line:$i",
                    points = line.map(transform::toScreen),
                    style = style
                )
            }

            is ProjectedPolygonGeometry -> listOf(
                RenderPolygon(
                    id = "$baseId:polygon",
                    rings = geometry.rings.map { ring -> ring.map(transform::toScreen) },
                    style = style
                )
            )

            is ProjectedMultiPolygonGeometry -> geometry.polygons.mapIndexed { i, polygon ->
                RenderPolygon(
                    id = "$baseId:polygon:$i",
                    rings = polygon.map { ring -> ring.map(transform::toScreen) },
                    style = style
                )
            }
        }
    }

    private fun geometryToCommands(
        baseId: String,
        mapState: MapState,
        geometry: Geometry,
        style: BaseStyle
    ): List<RenderCommand> {
        val hidePointMarkers = style.fillColor == 0x00000000L
         return when (geometry) {
            is Point -> if (hidePointMarkers) {
                emptyList()
            } else {
                listOf(
                    RenderPoint(
                        id = "$baseId:point",
                        point = mapState.worldToScreen(geometry),
                        style = style
                    )
                )
            }
            is MultiPoint -> if (hidePointMarkers) {
                emptyList()
            } else {
                geometry.points.mapIndexed { i, p ->
                    RenderPoint(
                        id = "$baseId:point:$i",
                        point = mapState.worldToScreen(p),
                        style = style
                    )
                }
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
