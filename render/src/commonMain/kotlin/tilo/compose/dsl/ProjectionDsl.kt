@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.DefinedProjection
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.projection.Epsg5514Projection
import tilo.compose.core.projection.IdentityProjection
import tilo.compose.core.projection.Projection
import tilo.compose.core.projection.ReferencedProjection

/**
 * WGS 84 longitude/latitude coordinates, EPSG:4326.
 */
@ExperimentalTiloApi
fun wgs84(): Projection = Epsg4326Projection

/**
 * Web Mercator projected coordinates, EPSG:3857.
 */
@ExperimentalTiloApi
fun webMercator(): Projection = Epsg3857Projection

/**
 * S-JTSK / Krovak East North, EPSG:5514.
 */
@ExperimentalTiloApi
fun sjtsk(): Projection = Epsg5514Projection

/**
 * Explicit alias for [sjtsk].
 */
@ExperimentalTiloApi
fun epsg5514(): Projection = Epsg5514Projection

/**
 * Identity cartesian coordinate space.
 */
@ExperimentalTiloApi
fun identityProjection(): Projection = IdentityProjection

/**
 * Defines a coordinate system from an EPSG identifier or a PROJ string.
 *
 * The platform PROJ/Proj4J provider discovers transformations automatically.
 */
@ExperimentalTiloApi
fun projection(
    definition: String,
    id: String = definition,
): Projection =
    DefinedProjection(
        definition = definition,
        id = id,
    )

/**
 * Defines a non-PROJ coordinate system connected to a known [reference] projection.
 *
 * The transformation is part of the returned projection and is discovered automatically wherever
 * the projection is used. [toReference] converts from the custom projection to [reference], while
 * [fromReference] converts in the opposite direction.
 */
@ExperimentalTiloApi
fun referencedProjection(
    id: String,
    reference: Projection,
    worldUnitsPerMapUnit: Double = 1.0,
    toReference: (Point) -> Point,
    fromReference: (Point) -> Point,
): Projection =
    ReferencedProjection(
        id = id,
        reference = reference,
        toReference = toReference,
        fromReference = fromReference,
        worldUnitsPerMapUnit = worldUnitsPerMapUnit,
    )
