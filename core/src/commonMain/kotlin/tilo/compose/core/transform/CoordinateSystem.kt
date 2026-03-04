package tilo.compose.core.transform

import tilo.compose.core.geometry.Point
import tilo.compose.core.map.Viewport

/**
 * Coordinate system abstraction. Map consumes coordinates in 'world' coordinates (doubles).
 * The default implementation is identity (world == cartesian or any projected system the app chooses).
 */
interface CoordinateSystem {
    fun worldToScreen(world: Point, center: Point, zoom: Double, viewport: Viewport): Point
    fun screenToWorld(screen: Point, center: Point, zoom: Double, viewport: Viewport): Point
}

