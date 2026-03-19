package tilo.compose.core.layers.raster

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
import tilo.compose.core.tile.Tile
import tilo.compose.core.tile.TileGrid
import tilo.compose.core.tile.TileRequest

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
    private val crsParamName: String = "SRS"
) : TileLayer {

    init {
        require(crs == projection.id) {
            "WMS CRS parameter '$crs' must match layer projection ${projection.id}."
        }
    }

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
        val bottomRight = map.screenToWorld(
            Point(
                map.viewport.width.toDouble(),
                map.viewport.height.toDouble()
            )
        )

        val requests = grid.visibleTiles(
            minX = minOf(topLeft.x, bottomRight.x),
            maxX = maxOf(topLeft.x, bottomRight.x),
            minY = minOf(topLeft.y, bottomRight.y),
            maxY = maxOf(topLeft.y, bottomRight.y),
            zoom = zoom
        )

        return coroutineScope {
            requests.map { req -> async { fetchTile(req) } }.awaitAll()
        }
    }

    private suspend fun fetchTile(request: TileRequest): Tile {
        val bytes = runCatching {
            val response = http.get(buildUrl(request))
            if (response.status.isSuccess()) response.body<ByteArray>() else null
        }.getOrNull()
        return Tile(coordinate = request.coordinate, bounds = request.bounds, bytes = bytes)
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
        return "${baseUrl}${sep}" +
            "SERVICE=WMS&REQUEST=GetMap&VERSION=1.1.1" +
            "&LAYERS=$layers&STYLES=$styles" +
            "&FORMAT=$format&TRANSPARENT=FALSE" +
            "&$crsParamName=$crs" +
            "&WIDTH=${grid.tileSize}&HEIGHT=${grid.tileSize}" +
            "&BBOX=$bbox"
    }
}
