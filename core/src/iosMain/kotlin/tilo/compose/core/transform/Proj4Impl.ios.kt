package tilo.compose.core.transform

import tilo.compose.core.geometry.Point

actual fun proj4Transform(point: Point, sourceCrs: String, targetCrs: String): Point {
    // iOS: no-op for now
    return point
}

