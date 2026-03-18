package tilo.compose.core.projection

/**
 * Backwards-compatible alias for Web Mercator (EPSG:3857).
 */
@Deprecated(
    message = "Use Epsg3857Projection.",
    replaceWith = ReplaceWith("Epsg3857Projection")
)
object Wgs84WebMercatorProjection : Projection by Epsg3857Projection
