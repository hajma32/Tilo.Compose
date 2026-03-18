package tilo.compose.core.tile

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.Map
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.projection.Projection

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
    private val tms: Boolean = false
) : TileLayer {

    private val http = HttpClient {
        expectSuccess = false
        install(HttpCache)
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
        }
    }

    override suspend fun loadTiles(map: Map): List<Tile> {
        validateProjection(map)
        val zoom = grid.zoomForViewport(map.zoom, map.viewport, projection)

        val topLeft = map.screenToWorld(Point(0.0, 0.0))
        val bottomRight = map.screenToWorld(Point(map.viewport.width.toDouble(), map.viewport.height.toDouble()))

        val requests = grid.visibleTiles(
            minX = minOf(topLeft.x, bottomRight.x),
            maxX = maxOf(topLeft.x, bottomRight.x),
            minY = minOf(topLeft.y, bottomRight.y),
            maxY = maxOf(topLeft.y, bottomRight.y),
            zoom = zoom
        )

        return coroutineScope {
            requests.map { request -> async { fetchTile(request) } }.awaitAll()
        }
    }

    private suspend fun fetchTile(request: TileRequest): Tile {
        val (z, x, y) = request.coordinate
        val sourceY = if (tms) (1 shl z) - 1 - y else y
        val url = urlTemplate
            .replace("{z}", z.toString())
            .replace("{x}", x.toString())
            .replace("{y}", sourceY.toString())

        val bytes = runCatching {
            val response = http.get(url)
            if (response.status.isSuccess()) response.body<ByteArray>() else null
        }.getOrNull()

        return Tile(coordinate = request.coordinate, bounds = request.bounds, bytes = bytes)
    }
}
