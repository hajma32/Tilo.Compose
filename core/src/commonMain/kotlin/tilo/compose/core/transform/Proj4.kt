package tilo.compose.core.transform

import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.Epsg3857Projection

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

/** Derives provider coordinate units represented by one map unit from CRS metadata. */
internal expect fun proj4WorldUnitsPerMapUnit(definition: String): Double

/** Shared zoom baseline owned by the built-in Web Mercator projection. */
internal val metersPerMapUnit: Double
    get() = Epsg3857Projection.worldUnitsPerMapUnit
