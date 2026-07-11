package tilo.compose.core.layers.raster

import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.Attribution
import tilo.compose.core.map.Map
import tilo.compose.core.projection.Projection
import tilo.compose.core.tile.Tile
import tilo.compose.core.tile.TileGrid
import tilo.compose.core.tile.TileRequestPlan

/**
 * Generic raster layer for tile sources that already match the map CRS.
 *
 * Rendering remains CRS-agnostic: the layer plans tiles in [grid] world
 * coordinates, asks [source] for bytes, and returns tiles with bounds for the
 * renderer to place. No raster reprojection is performed client-side.
 */
open class RasterTileLayer(
    override val id: String,
    private val source: RasterTileSource,
    override val zIndex: Int = 0,
    private val maxVisibleTiles: Int = 9,
    private val prefetchMargin: Int = 1,
    override val attributions: List<Attribution> = emptyList(),
    fetchConfig: TileFetchConfig = TileFetchConfig(),
) : TileLayer {
    override val projection: Projection = source.projection
    override val grid: TileGrid = source.grid

    private val fetcher =
        TileRequestFetcher(
            config = fetchConfig,
            cacheKey = source::cacheKey,
            fetchBytes = source::readTile,
        )

    override suspend fun loadTiles(map: Map): List<Tile> {
        validateProjection(map)
        return fetcher.fetchTiles(requestPlan(map).visible)
    }

    override fun planTiles(map: Map): List<Tile> {
        validateProjection(map)
        return requestPlan(map).visible.map { request ->
            Tile(coordinate = request.coordinate, bounds = request.bounds, bytes = null)
        }
    }

    override suspend fun prefetchTiles(map: Map) {
        validateProjection(map)
        fetcher.fetchTiles(requestPlan(map).prefetch)
    }

    private fun requestPlan(map: Map): TileRequestPlan {
        val topLeft = map.screenToWorld(Point(0.0, 0.0))
        val bottomRight =
            map.screenToWorld(
                Point(
                    map.viewport.width.toDouble(),
                    map.viewport.height.toDouble(),
                ),
            )

        return grid.requestPlan(
            minX = minOf(topLeft.x, bottomRight.x),
            maxX = maxOf(topLeft.x, bottomRight.x),
            minY = minOf(topLeft.y, bottomRight.y),
            maxY = maxOf(topLeft.y, bottomRight.y),
            preferredZoom = grid.zoomForViewport(map.zoom, map.viewport, projection),
            maxVisibleTiles = maxVisibleTiles,
            prefetchMargin = prefetchMargin,
        )
    }
}
