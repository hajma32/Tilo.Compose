package tilo.compose.core.map

import kotlin.math.pow
import tilo.compose.core.geometry.Point

/**
 * Represents the current viewport of the map, including dimensions and zoom level.
 */
data class Viewport(val width: Int, val height: Int, val pixelRatio: Double = 1.0) {
    fun worldToScreen(world: Point, center: Point, zoom: Double): Point {
        val scale = 2.0.pow(zoom)
        val dx = (world.x - center.x) * scale + width / 2.0
        val dy = (center.y - world.y) * scale + height / 2.0
        return Point(dx, dy)
    }

    fun screenToWorld(screen: Point, center: Point, zoom: Double): Point {
        val scale = 2.0.pow(zoom)
        val wx = (screen.x - width / 2.0) / scale + center.x
        val wy = center.y - (screen.y - height / 2.0) / scale
        return Point(wx, wy)
    }
}
