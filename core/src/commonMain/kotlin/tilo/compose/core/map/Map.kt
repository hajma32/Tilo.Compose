package tilo.compose.core.map

import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.Projection
import tilo.compose.core.projection.IdentityProjection
import tilo.compose.core.transform.Transformation

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
        val worldDelta = viewport.screenToWorld(Point(dx, dy), center, zoom)
        val originWorld = viewport.screenToWorld(Point(0.0, 0.0), center, zoom)
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
        val worldBefore = viewport.screenToWorld(focus, center, zoom)
        zoom = newZoom
        val worldAfter = viewport.screenToWorld(focus, center, zoom)
        center = Point(center.x + (worldBefore.x - worldAfter.x), center.y + (worldBefore.y - worldAfter.y))
    }

    //TODO could be one transform method
    fun transformSourceToTarget(point: Point, source: Projection, target: Projection): Point {
        val transformation = requireTransformation(source, target)
        return transformation.sourceToTarget(point)
    }

    fun transformTargetToSource(point: Point, source: Projection, target: Projection): Point {
        val transformation = requireTransformation(source, target)
        return transformation.targetToSource(point)
    }

    fun worldToScreen(world: Point): Point = viewport.worldToScreen(world, center, zoom)
    fun screenToWorld(screen: Point): Point = viewport.screenToWorld(screen, center, zoom)

    private fun requireTransformation(
        source: Projection,
        target: Projection
    ): Transformation<Projection, Projection> {
        return config.transformations.firstOrNull { it.source === source && it.target === target }
            ?: throw IllegalStateException(
                "No transformation registered for ${source::class.simpleName} -> ${target::class.simpleName}."
            )
    }
}
