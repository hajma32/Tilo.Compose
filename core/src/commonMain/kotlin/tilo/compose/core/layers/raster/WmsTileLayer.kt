package tilo.compose.core.layers.raster

import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.projection.Projection
import tilo.compose.core.tile.TileGrid

/**
 * Caller-owned WMS GetMap tile layer whose source CRS matches the map CRS.
 *
 * `layerNames` and [WmsLayerOptions.styles] preserve their one-to-one mapping
 * until the source serializes the request. Directly constructed instances must
 * be closed after their last use.
 */
class WmsTileLayer(
    id: String,
    baseUrl: String,
    layerNames: List<String>,
    projection: Projection = Epsg4326Projection,
    grid: TileGrid = TileGrid.defaultFor(projection),
    options: WmsLayerOptions = WmsLayerOptions(),
) : RasterTileLayer(
        id = id,
        source =
            WmsTileSource(
                projection = projection,
                grid = grid,
                baseUrl = baseUrl,
                layerNames = layerNames,
                styles = options.styles,
                format = options.format ?: WmsImageFormat.Png,
                version = options.version ?: WmsVersion.V1_1_1,
                axisOrder = options.axisOrder ?: WmsAxisOrder.forCrs(projection.id),
                transport = options.http.tileHttpTransport(),
            ),
        zIndex = options.zIndex,
        visible = options.visible,
        opacity = options.opacity,
        minZoom = options.minZoom,
        maxZoom = options.maxZoom,
        maxVisibleTiles = options.maxVisibleTiles,
        prefetchMargin = options.prefetchMargin,
        overviewZoomOffset = options.overviewZoomOffset,
        maxOverviewTiles = options.maxOverviewTiles,
        overviewPrefetchMargin = options.overviewPrefetchMargin,
        attributions = options.attributions,
        fetchConfig = options.fetchConfig,
        onError = options.onError,
        onDiagnostic = options.onDiagnostic,
    )
