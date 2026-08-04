package tilo.compose.core.layers.raster

import tilo.compose.core.layers.Attribution
import tilo.compose.core.map.MapState
import tilo.compose.core.projection.Projection
import tilo.compose.core.tile.Tile
import tilo.compose.core.tile.TileGrid
import tilo.compose.core.tile.TileRequestPlan

/**
 * Generic raster layer for tile sources that already match the map CRS.
 *
 * Rendering remains CRS-agnostic: the layer plans tiles in its grid's world
 * coordinates, asks its source for bytes, and returns tiles with bounds for the
 * renderer to place. No raster reprojection is performed client-side.
 *
 * A directly constructed layer is owned by its caller and must be [close]d
 * when no map uses it anymore. Passing it to a map layer builder only borrows
 * the layer; it does not transfer ownership. High-level map DSL functions that
 * construct raster layers manage their own instances.
 * Nearby-tile prefetching and coarse overview loading are disabled by default;
 * callers must opt in through the corresponding margin and zoom-offset options.
 * `onError` is invoked once for each source request that throws; cancellation
 * still propagates normally and an unavailable (`null`) tile is not an error.
 */
open class RasterTileLayer(
    override val id: String,
    private val source: RasterTileSource,
    override val zIndex: Int = 0,
    override val visible: Boolean = true,
    override val minZoom: Double? = null,
    override val maxZoom: Double? = null,
    private val maxVisibleTiles: Int = 9,
    private val prefetchMargin: Int = 0,
    private val overviewZoomOffset: Int = 0,
    private val maxOverviewTiles: Int = 4,
    private val overviewPrefetchMargin: Int = 0,
    override val attributions: List<Attribution> = emptyList(),
    fetchConfig: TileFetchConfig = TileFetchConfig(),
    onError: ((Throwable) -> Unit)? = null,
    override val opacity: Double = 1.0,
    onDiagnostic: (suspend (RasterTileDiagnosticEvent) -> Unit)? = null,
) : TileLayer,
    AutoCloseable {
    init {
        require(id.isNotBlank()) { "Layer id must not be blank" }
        require(opacity in 0.0..1.0) { "opacity must be between 0.0 and 1.0" }
        val minimum = minZoom
        val maximum = maxZoom
        require(minimum == null || minimum.isFinite()) { "minZoom must be finite" }
        require(maximum == null || maximum.isFinite()) { "maxZoom must be finite" }
        require(minimum == null || maximum == null || minimum <= maximum) {
            "minZoom must not be greater than maxZoom"
        }
    }

    override val projection: Projection = source.projection
    override val grid: TileGrid = source.grid
    override val sourceIdentity: Any = source

    private val fetcher =
        TileRequestFetcher(
            config = fetchConfig,
            onError = onError,
            onDiagnostic = onDiagnostic,
            cacheKey = source::cacheKey,
            fetchBytes = source::readTile,
            fetchResult = (source as? DiagnosticRasterTileSource)?.let { it::readTileResult },
        )

    /** Cancels owned tile requests. The layer must not be used after this call. */
    final override fun close() {
        fetcher.close()
    }

    /** Returns a point-in-time snapshot of this layer's tile fetch and cache activity. */
    suspend fun tileFetchMetrics(): TileFetchMetrics = fetcher.metrics()

    override suspend fun loadTiles(map: MapState): List<Tile> {
        validateProjection(map)
        return fetcher.fetchTiles(requestPlan(map).visible, RasterTileRequestPurpose.Visible)
    }

    override suspend fun loadOverviewTiles(map: MapState): List<Tile> {
        validateProjection(map)
        if (overviewZoomOffset <= 0 || maxOverviewTiles <= 0) return emptyList()
        return fetcher.fetchTiles(overviewRequestPlan(map).visible, RasterTileRequestPurpose.Overview)
    }

    override suspend fun prefetchOverviewTiles(map: MapState) {
        validateProjection(map)
        if (overviewZoomOffset <= 0 || maxOverviewTiles <= 0 || overviewPrefetchMargin <= 0) return
        fetcher.fetchTiles(
            overviewRequestPlan(map, prefetchMargin = overviewPrefetchMargin).prefetch,
            RasterTileRequestPurpose.Prefetch,
        )
    }

    override fun planTiles(map: MapState): List<Tile> {
        validateProjection(map)
        return requestPlan(map).visible.map { request ->
            Tile(coordinate = request.coordinate, bounds = request.bounds, bytes = null)
        }
    }

    override suspend fun prefetchTiles(map: MapState) {
        validateProjection(map)
        fetcher.fetchTiles(requestPlan(map).prefetch, RasterTileRequestPurpose.Prefetch)
    }

    private fun requestPlan(map: MapState): TileRequestPlan {
        val preferredZoom = grid.zoomForViewport(map.zoom, map.viewport, projection)
        return requestPlan(
            map = map,
            preferredZoom = preferredZoom,
            maxVisibleTiles = maxVisibleTiles,
            prefetchMargin = prefetchMargin,
        )
    }

    private fun overviewRequestPlan(
        map: MapState,
        prefetchMargin: Int = 0,
    ): TileRequestPlan {
        val preferredZoom =
            (grid.zoomForViewport(map.zoom, map.viewport, projection) - overviewZoomOffset)
                .coerceAtLeast(0)
        return requestPlan(
            map = map,
            preferredZoom = preferredZoom,
            maxVisibleTiles = maxOverviewTiles,
            prefetchMargin = prefetchMargin,
        )
    }

    private fun requestPlan(
        map: MapState,
        preferredZoom: Int,
        maxVisibleTiles: Int,
        prefetchMargin: Int,
    ): TileRequestPlan {
        val visible = map.viewportBounds()

        return grid.requestPlan(
            minX = visible.minX,
            maxX = visible.maxX,
            minY = visible.minY,
            maxY = visible.maxY,
            preferredZoom = preferredZoom,
            maxVisibleTiles = maxVisibleTiles,
            prefetchMargin = prefetchMargin,
        )
    }
}
