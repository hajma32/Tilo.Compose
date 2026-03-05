package tilo.compose.core.tile.source

/**
 * OSM source implemented as WMS GetMap requests over EPSG:3857.
 *
 * Note: OSM is commonly consumed as XYZ. This implementation intentionally uses WMS URL building
 * to satisfy WMS-style source requirements.
 */
class OSMSource(
    wmsBaseUrl: String = "https://ows.terrestris.de/osm/service",
    layers: String = "OSM-WMS",
    crs: String = "EPSG:3857",
    crsParameterName: String = "SRS"
) : WMSSource(
    wmsBaseUrl = wmsBaseUrl,
    layers = layers,
    crs = crs,
    crsParameterName = crsParameterName
)
