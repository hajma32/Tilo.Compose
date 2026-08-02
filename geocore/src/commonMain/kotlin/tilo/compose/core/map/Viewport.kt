package tilo.compose.core.map

import tilo.compose.core.geometry.Point
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Represents the current viewport of the map.
 *
 * `width` / `height` are physical pixels. `pixelRatio` converts to DIP.
 *
 * World↔screen math operates in normalized map units (scale = 2^zoom). The
 * `worldUnitsPerMapUnit` function argument converts raw CRS units into those map units.
 */
data class Viewport(
    val width: Int,
    val height: Int,
    val pixelRatio: Double = 1.0,
) {
    companion object {
        /** Viewport used before a map receives its first measured layout size. */
        val Empty: Viewport = Viewport(width = 0, height = 0)
    }

    val dipWidth: Double get() = width / pixelRatio
    val dipHeight: Double get() = height / pixelRatio

    fun worldToScreen(
        world: Point,
        center: Point,
        zoom: Double,
        worldUnitsPerMapUnit: Double = 1.0,
        bearing: Double = 0.0,
    ): ScreenPoint {
        val scale = 2.0.pow(zoom) / worldUnitsPerMapUnit
        val radians = bearing * PI / 180.0
        val x = (world.x - center.x) * scale
        val y = (center.y - world.y) * scale
        return ScreenPoint(
            x = (x * cos(radians) + y * sin(radians) + dipWidth / 2.0) * pixelRatio,
            y = (-x * sin(radians) + y * cos(radians) + dipHeight / 2.0) * pixelRatio,
        )
    }

    fun screenToWorld(
        screen: ScreenPoint,
        center: Point,
        zoom: Double,
        worldUnitsPerMapUnit: Double = 1.0,
        bearing: Double = 0.0,
    ): Point {
        val scale = 2.0.pow(zoom) / worldUnitsPerMapUnit
        val radians = bearing * PI / 180.0
        val screenX = screen.x / pixelRatio - dipWidth / 2.0
        val screenY = screen.y / pixelRatio - dipHeight / 2.0
        return Point(
            x = (screenX * cos(radians) - screenY * sin(radians)) / scale + center.x,
            y = center.y - (screenX * sin(radians) + screenY * cos(radians)) / scale,
        )
    }
}
