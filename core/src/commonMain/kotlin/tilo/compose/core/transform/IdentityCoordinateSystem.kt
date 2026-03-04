package tilo.compose.core.transform

import tilo.compose.core.geometry.Point
import tilo.compose.core.map.Viewport
import kotlin.math.pow

/** Identity coordinate system that treats world coordinates as cartesian (no projection). */
object IdentityCoordinateSystem : CoordinateSystem {
    override fun worldToScreen(world: Point, center: Point, zoom: Double, viewport: Viewport): Point {
        val scale = zoom.pow(2.0)
        val dx = (world.x - center.x) * scale + viewport.width / 2.0
        val dy = (world.y - center.y) * scale + viewport.height / 2.0
        return Point(dx, dy)
    }

    override fun screenToWorld(screen: Point, center: Point, zoom: Double, viewport: Viewport): Point {
        val scale = zoom.pow(2.0)
        val wx = (screen.x - viewport.width / 2.0) / scale + center.x
        val wy = (screen.y - viewport.height / 2.0) / scale + center.y
        return Point(wx, wy)
    }
}

