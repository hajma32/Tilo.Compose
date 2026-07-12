package tilo.compose.core.layers.raster

import tilo.compose.core.layers.Attribution
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.projection.Projection
import tilo.compose.core.tile.TileGrid

/**
 * WMS tile layer (OGC WMS GetMap).
 *
 * Tiles are expected to be served in the same CRS as the map itself.
 * No client-side tile reprojection is performed.
 *
 * [crs] is the WMS SRS/CRS value, e.g. "EPSG:5514".
 * [crsParamName] is "SRS" for WMS 1.1.1 and "CRS" for WMS 1.3.0.
 */
class WMSTileLayer(
    id: String,
    projection: Projection = Epsg4326Projection,
    grid: TileGrid = TileGrid.defaultFor(projection),
    baseUrl: String,
    layers: String,
    crs: String = projection.id,
    styles: String = "",
    format: String = "image/png",
    version: String = "1.1.1",
    crsParamName: String = "SRS",
    zIndex: Int = 0,
    visible: Boolean = true,
    minZoom: Double? = null,
    maxZoom: Double? = null,
    maxVisibleTiles: Int = 9,
    prefetchMargin: Int = 1,
    overviewZoomOffset: Int = 2,
    maxOverviewTiles: Int = 4,
    overviewPrefetchMargin: Int = 1,
    attributions: List<Attribution> = emptyList(),
    fetchConfig: TileFetchConfig = TileFetchConfig(),
) : RasterTileLayer(
    id = id,
    source = WMSTileSource(
        projection = projection,
        grid = grid,
        baseUrl = baseUrl,
        layers = layers,
        crs = crs,
        styles = styles,
        format = format,
        version = version,
        crsParamName = crsParamName,
    ),
    zIndex = zIndex,
    visible = visible,
    minZoom = minZoom,
    maxZoom = maxZoom,
    maxVisibleTiles = maxVisibleTiles,
    prefetchMargin = prefetchMargin,
    overviewZoomOffset = overviewZoomOffset,
    maxOverviewTiles = maxOverviewTiles,
    overviewPrefetchMargin = overviewPrefetchMargin,
    attributions = attributions,
    fetchConfig = fetchConfig,
)
