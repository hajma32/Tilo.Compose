package tilo.compose.core.projection

/**
 * WGS84 lon/lat CRS used with WebMercator viewport math in the current renderer.
 */
object Wgs84WebMercatorProjection : Projection {
    override val id: String = "EPSG:4326"
}
