package tilo.compose.core.map

import kotlin.math.pow
import tilo.compose.core.geometry.Point

/**
 * Represents the current viewport of the map.
 *
 * [width] / [height] are physical pixels. [pixelRatio] converts to DIP.
 *
 * World↔screen math operates in normalized map units (scale = 2^zoom) and
 * [worldUnitsPerMapUnit] converts raw CRS units into those map units.
 */
data class Viewport(val width: Int, val height: Int, val pixelRatio: Double = 1.0) {

    val dipWidth: Double get() = width / pixelRatio
    val dipHeight: Double get() = height / pixelRatio

    fun worldToScreen(
        world: Point,
        center: Point,
        zoom: Double,
        worldUnitsPerMapUnit: Double = 1.0
    ): Point {
        val scale = 2.0.pow(zoom) / worldUnitsPerMapUnit
        return Point(
            x = ((world.x - center.x) * scale + dipWidth / 2.0) * pixelRatio,
            y = ((center.y - world.y) * scale + dipHeight / 2.0) * pixelRatio
        )
    }

    fun screenToWorld(
        screen: Point,
        center: Point,
        zoom: Double,
        worldUnitsPerMapUnit: Double = 1.0
    ): Point {
        val scale = 2.0.pow(zoom) / worldUnitsPerMapUnit
        return Point(
            x = (screen.x / pixelRatio - dipWidth / 2.0) / scale + center.x,
            y = center.y - (screen.y / pixelRatio - dipHeight / 2.0) / scale
        )
    }
}
