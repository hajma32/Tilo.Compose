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
 */
class XYZTileLayer(
    id: String,
    projection: Projection = Epsg3857Projection,
    grid: TileGrid = TileGrid.defaultFor(projection),
    urlTemplate: String,
    tms: Boolean = false,
    zIndex: Int = 0,
    maxVisibleTiles: Int = 9,
    prefetchMargin: Int = 1,
    attributions: List<Attribution> = emptyList(),
    fetchConfig: TileFetchConfig = TileFetchConfig(),
) : RasterTileLayer(
    id = id,
    source = XYZTileSource(
        urlTemplate = urlTemplate,
        projection = projection,
        grid = grid,
        tms = tms,
    ),
    zIndex = zIndex,
    maxVisibleTiles = maxVisibleTiles,
    prefetchMargin = prefetchMargin,
    attributions = attributions,
    fetchConfig = fetchConfig,
)
