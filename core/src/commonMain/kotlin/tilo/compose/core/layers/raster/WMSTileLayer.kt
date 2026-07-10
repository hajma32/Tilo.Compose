package tilo.compose.core.layers.raster

import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.Map
import tilo.compose.core.net.sharedHttpClient
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.projection.Projection
import tilo.compose.core.tile.Tile
import tilo.compose.core.tile.TileGrid
import tilo.compose.core.tile.TileRequest
import tilo.compose.core.tile.TileRequestPlan

/**
 * WMS tile layer (OGC WMS GetMap).
 *
 * Tiles are expected to be served in the same CRS as the map itself.
 * No client-side tile reprojection is performed.
 *
 * [crs]          WMS SRS/CRS value, e.g. "EPSG:4326".
 * [crsParamName] "SRS" for WMS 1.1.1, "CRS" for WMS 1.3.0.
 */
class WMSTileLayer(
    override val id: String,
    override val projection: Projection = Epsg4326Projection,
    override val grid: TileGrid = TileGrid.defaultFor(projection),
    private val baseUrl: String,
    private val layers: String,
    private val crs: String = projection.id,
    private val styles: String = "",
    private val format: String = "image/png",
    private val crsParamName: String = "SRS",
    private val maxVisibleTiles: Int = 9,
    private val prefetchMargin: Int = 1,
    private val fetchConfig: TileFetchConfig = TileFetchConfig(),
) : TileLayer {
    init {
        require(crs == projection.id) {
            "WMS CRS parameter '$crs' must match layer projection ${projection.id}."
        }
    }

    // Use shared HttpClient with platform-specific pooling
    private val http = sharedHttpClient()
    private val fetcher =
        TileRequestFetcher(
            config = fetchConfig,
            cacheKey = ::buildUrl,
        ) { url ->
            try {
                val response = http.get(url)
                response.readTileImageBytesOrNull()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                null
            }
        }

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

    /**
     * Builds the WMS GetMap URL.
     * Bounds are already in the tile layer CRS.
     */
    private fun buildUrl(request: TileRequest): String {
        val b = request.bounds
        val west = minOf(b.topLeft.x, b.bottomRight.x)
        val east = maxOf(b.topLeft.x, b.bottomRight.x)
        val south = minOf(b.topLeft.y, b.bottomRight.y)
        val north = maxOf(b.topLeft.y, b.bottomRight.y)
        val bbox = "$west,$south,$east,$north"
        val sep = if ('?' in baseUrl) "&" else "?"
        return "$baseUrl$sep" +
            "SERVICE=WMS&REQUEST=GetMap&VERSION=1.1.1" +
            "&LAYERS=$layers&STYLES=$styles" +
            "&FORMAT=$format&TRANSPARENT=FALSE" +
            "&$crsParamName=$crs" +
            "&WIDTH=${grid.tileSize}&HEIGHT=${grid.tileSize}" +
            "&BBOX=$bbox"
    }
}
