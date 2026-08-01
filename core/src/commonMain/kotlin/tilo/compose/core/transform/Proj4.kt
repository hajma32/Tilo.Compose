package tilo.compose.core.transform

import tilo.compose.core.geometry.Point

/**
 * Cross-platform proj4-style transform function.
 */
internal expect fun proj4Transform(
    point: Point,
    sourceCrs: String,
    targetCrs: String,
): Point

/** Returns whether the platform engine can create an operation for the CRS pair. */
internal expect fun supportsProj4Transform(
    sourceCrs: String,
    targetCrs: String,
): Boolean
