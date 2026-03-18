package tilo.compose.core.map

import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.IdentityProjection
import tilo.compose.core.projection.Projection

/**
 * Represents the state of a map, including its center, zoom level, projection, and viewport.
 */
class Map(
    var center: Point = Point(0.0, 0.0),
    var zoom: Double = 0.0,
    val projection: Projection = IdentityProjection,
    val config: MapConfig = MapConfig.Default,
    var viewport: Viewport = Viewport(256, 256)
) {

    fun panBy(dx: Double, dy: Double) {
        val worldDelta = viewport.screenToWorld(Point(dx, dy), center, zoom, projection.worldUnitsPerMapUnit)
        val originWorld = viewport.screenToWorld(Point(0.0, 0.0), center, zoom, projection.worldUnitsPerMapUnit)
        center = Point(
            x = center.x + (worldDelta.x - originWorld.x),
            y = center.y + (worldDelta.y - originWorld.y)
        )
    }

    fun zoomBy(delta: Double, focus: Point? = null) {
        val newZoom = (zoom + delta).coerceIn(config.minZoom, config.maxZoom)
        if (focus == null) {
            zoom = newZoom
            return
        }
        val worldBefore = viewport.screenToWorld(focus, center, zoom, projection.worldUnitsPerMapUnit)
        zoom = newZoom
        val worldAfter = viewport.screenToWorld(focus, center, zoom, projection.worldUnitsPerMapUnit)
        center = Point(center.x + (worldBefore.x - worldAfter.x), center.y + (worldBefore.y - worldAfter.y))
    }

    fun transformSourceToTarget(point: Point, source: Projection, target: Projection): Point {
        return config.transformer.sourceToTarget(point, source, target)
    }

    fun transformTargetToSource(point: Point, source: Projection, target: Projection): Point {
        return config.transformer.targetToSource(point, source, target)
    }

    fun worldToScreen(world: Point): Point =
        viewport.worldToScreen(world, center, zoom, projection.worldUnitsPerMapUnit)

    fun screenToWorld(screen: Point): Point =
        viewport.screenToWorld(screen, center, zoom, projection.worldUnitsPerMapUnit)
}
