package tilo.compose.core.layers.raster

import tilo.compose.core.layers.Attribution
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.Projection
import tilo.compose.core.tile.TileGrid

/**
 * XYZ (slippy-map) tile layer.
 *
 * [urlTemplate] uses `{z}`, `{x}`, `{y}` placeholders, e.g.:
 * `"https://tile.openstreetmap.org/{z}/{x}/{y}.png"`.
 *
 * For TMS row addressing set [tms] = true.
 * Prefetching and coarse overview loading are opt-in.
 * The caller owns directly constructed instances and must close them after use.
 */
class XYZTileLayer(
    id: String,
    projection: Projection = Epsg3857Projection,
    grid: TileGrid = TileGrid.defaultFor(projection),
    urlTemplate: String,
    tms: Boolean = false,
    zIndex: Int = 0,
    visible: Boolean = true,
    minZoom: Double? = null,
    maxZoom: Double? = null,
    maxVisibleTiles: Int = 9,
    prefetchMargin: Int = 0,
    overviewZoomOffset: Int = 0,
    maxOverviewTiles: Int = 4,
    overviewPrefetchMargin: Int = 0,
    attributions: List<Attribution> = emptyList(),
    fetchConfig: TileFetchConfig = TileFetchConfig(),
    onError: ((Throwable) -> Unit)? = null,
    opacity: Double = 1.0,
    onDiagnostic: (suspend (RasterTileDiagnosticEvent) -> Unit)? = null,
) : RasterTileLayer(
        id = id,
        source =
            XYZTileSource(
                urlTemplate = urlTemplate,
                projection = projection,
                grid = grid,
                tms = tms,
            ),
        zIndex = zIndex,
        visible = visible,
        opacity = opacity,
        minZoom = minZoom,
        maxZoom = maxZoom,
        maxVisibleTiles = maxVisibleTiles,
        prefetchMargin = prefetchMargin,
        overviewZoomOffset = overviewZoomOffset,
        maxOverviewTiles = maxOverviewTiles,
        overviewPrefetchMargin = overviewPrefetchMargin,
        attributions = attributions,
        fetchConfig = fetchConfig,
        onError = onError,
        onDiagnostic = onDiagnostic,
    )
