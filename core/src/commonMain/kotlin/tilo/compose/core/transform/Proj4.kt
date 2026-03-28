package tilo.compose.core.transform

import tilo.compose.core.geometry.Point

/**
 * Cross-platform proj4-style transform function.
 */
expect fun proj4Transform(point: Point, sourceCrs: String, targetCrs: String): Point
