package tilo.compose.core.tile.source

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import tilo.compose.core.map.Viewport
import tilo.compose.core.tile.Tile
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.transform.WmsBbox

/**
 * Precomputed WMS tile request.
 */
data class WmsTileRequest(
    val coordinate: TileCoordinate,
    val bbox: WmsBbox,
    val width: Int = 256,
    val height: Int = 256
)

/**
 * WMS source that only maps requests to WMS URLs and downloads payload.
 *
 * It intentionally does not compute tile grid, center-based selection, or BBOX values.
 * Those concerns belong to higher-level layer logic.
 */
open class WMSSource(
    private val wmsBaseUrl: String,
    private val layers: String,
    private val crs: String = "EPSG:3857",
    private val crsParameterName: String = "SRS"
) : Source {

    private companion object {
        const val MAX_CONCURRENT_REQUESTS = 6
        const val MAX_CACHE_ENTRIES = 256
    }

    private val httpClient = HttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 8_000
            connectTimeoutMillis = 8_000
            socketTimeoutMillis = 8_000
        }
        defaultRequest {
            headers.append(HttpHeaders.UserAgent, "TiloCompose/0.1")
            headers.append(HttpHeaders.Accept, "image/png,image/*;q=0.8,*/*;q=0.5")
        }
    }

    private val requestSemaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
    private val cacheMutex = Mutex()
    private val tileBytesCache = mutableMapOf<String, ByteArray>()
    private val cacheOrder = ArrayDeque<String>()

    suspend fun getTiles(requests: List<WmsTileRequest>): List<Tile> {
        return coroutineScope {
            requests.map { request ->
                async {
                    val url = buildWmsUrl(request.bbox, request.width, request.height)
                    val cached = cacheMutex.withLock {
                        val hit = tileBytesCache[url]
                        if (hit != null) {
                            cacheOrder.remove(url)
                            cacheOrder.addLast(url)
                        }
                        hit
                    }
                    val bytes = cached ?: requestSemaphore.withPermit {
                        val fetched = runCatching {
                            val response = httpClient.get(url)
                            if (response.status.isSuccess()) response.body<ByteArray>() else null
                        }.getOrNull()

                        if (fetched != null) {
                            cacheMutex.withLock {
                                if (!tileBytesCache.containsKey(url) && cacheOrder.size >= MAX_CACHE_ENTRIES) {
                                    val oldest = cacheOrder.removeFirstOrNull()
                                    if (oldest != null) tileBytesCache.remove(oldest)
                                }
                                tileBytesCache[url] = fetched
                                cacheOrder.remove(url)
                                cacheOrder.addLast(url)
                            }
                        }

                        fetched
                    }
                    Tile(
                        coordinate = request.coordinate,
                        url = url,
                        bytes = bytes
                    )
                }
            }.awaitAll()
        }
    }

    override fun getTiles(zoomLevel: Int, viewport: Viewport, tileCount: Int): List<Tile> {
        throw UnsupportedOperationException(
            "WMSSource does not compute tile requests. " +
                "Use TileLayer.buildRequests(...) and call WMSSource.getTiles(requests)."
        )
    }

    private fun buildWmsUrl(bbox: WmsBbox, width: Int, height: Int): String {
        return buildString {
            append(wmsBaseUrl)
            append(if (wmsBaseUrl.contains("?")) "&" else "?")
            append("SERVICE=WMS")
            append("&REQUEST=GetMap")
            append("&VERSION=1.1.1")
            append("&LAYERS=$layers")
            append("&STYLES=")
            append("&FORMAT=image/png")
            append("&TRANSPARENT=FALSE")
            append("&$crsParameterName=$crs")
            append("&WIDTH=$width")
            append("&HEIGHT=$height")
            append("&BBOX=${bbox.minX},${bbox.minY},${bbox.maxX},${bbox.maxY}")
        }
    }
}
