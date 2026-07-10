package tilo.compose.dsl

import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.projection.Epsg5514Projection
import tilo.compose.core.projection.IdentityProjection
import tilo.compose.core.projection.Projection

/**
 * WGS 84 longitude/latitude coordinates, EPSG:4326.
 */
fun wgs84(): Projection = Epsg4326Projection

/**
 * Web Mercator projected coordinates, EPSG:3857.
 */
fun webMercator(): Projection = Epsg3857Projection

/**
 * S-JTSK / Krovak East North, EPSG:5514.
 */
fun sjtsk(): Projection = Epsg5514Projection

/**
 * Explicit alias for [sjtsk].
 */
fun epsg5514(): Projection = Epsg5514Projection

/**
 * Identity cartesian coordinate space.
 */
fun identityProjection(): Projection = IdentityProjection
