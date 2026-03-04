package tilo.compose.core.map

import tilo.compose.core.geometry.Point
import tilo.compose.core.transform.CoordinateSystem
import tilo.compose.core.transform.IdentityCoordinateSystem

/** Mutable-ish map viewport state that stores center, zoom and exposes helpers. */
class MapState(
    var center: Point = Point(0.0, 0.0),
    var zoom: Double = 0.0,
    val settings: MapSettings = MapSettings(),
    val coordSys: CoordinateSystem = IdentityCoordinateSystem,
    var viewport: Viewport = Viewport(256, 256)
) {

    fun panBy(dx: Double, dy: Double) {
        // convert pixel delta to world delta
        val worldDelta = coordSys.screenToWorld(Point(dx, dy), center, zoom, viewport)
        val originWorld = coordSys.screenToWorld(Point(0.0, 0.0), center, zoom, viewport)
        center = Point(center.x + (worldDelta.x - originWorld.x), center.y + (worldDelta.y - originWorld.y))
    }

    fun zoomBy(delta: Double, focus: Point? = null) {
        val newZoom = (zoom + delta).coerceIn(settings.minZoom, settings.maxZoom)
        if (focus == null) {
            zoom = newZoom
            return
        }
        // preserve the world coordinate under the focus point
        val worldBefore = coordSys.screenToWorld(focus, center, zoom, viewport)
        zoom = newZoom
        val worldAfter = coordSys.screenToWorld(focus, center, zoom, viewport)
        center = Point(center.x + (worldBefore.x - worldAfter.x), center.y + (worldBefore.y - worldAfter.y))
    }

    fun worldToScreen(world: Point): Point = coordSys.worldToScreen(world, center, zoom, viewport)
    fun screenToWorld(screen: Point): Point = coordSys.screenToWorld(screen, center, zoom, viewport)
}
