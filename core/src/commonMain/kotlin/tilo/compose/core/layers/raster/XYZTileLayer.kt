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
 * XYZ (slippy-map) tile layer.
 *
 * [urlTemplate] uses `{z}`, `{x}`, `{y}` placeholders, e.g.:
 *   `"https://tile.openstreetmap.org/{z}/{x}/{y}.png"`
 *
 * For TMS (y flipped) set [tms] = true.
 */
class XYZTileLayer(
    override val id: String,
    override val projection: Projection = Epsg4326Projection,
    override val grid: TileGrid = TileGrid.defaultFor(projection),
    private val urlTemplate: String,
    private val tms: Boolean = false,
    private val maxVisibleTiles: Int = 9,
    private val prefetchMargin: Int = 1,
    private val fetchConfig: TileFetchConfig = TileFetchConfig(),
) : TileLayer {
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

    private fun buildUrl(request: TileRequest): String {
        val (z, x, y) = request.coordinate
        val sourceY = if (tms) (1 shl z) - 1 - y else y
        return urlTemplate
            .replace("{z}", z.toString())
            .replace("{x}", x.toString())
            .replace("{y}", sourceY.toString())
    }
}
