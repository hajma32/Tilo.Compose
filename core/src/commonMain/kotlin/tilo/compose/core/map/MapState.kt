package tilo.compose.core.map

import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.Projection
import tilo.compose.core.projection.IdentityProjection

/** Mutable-ish map viewport state that stores center, zoom and exposes helpers. */
class MapState(
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

    fun worldToScreen(world: Point): Point = viewport.worldToScreen(world, center, zoom)
    fun screenToWorld(screen: Point): Point = viewport.screenToWorld(screen, center, zoom)
}
