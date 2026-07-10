package tilo.compose.core.layers.raster

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import tilo.compose.core.tile.Tile
import tilo.compose.core.tile.TileRequest

data class TileFetchConfig(
    val maxCacheEntries: Int = 200,
    val concurrency: Int = 8,
    val dispatcher: CoroutineDispatcher = Dispatchers.Default,
)

internal class TileRequestFetcher(
    private val config: TileFetchConfig = TileFetchConfig(),
    private val cacheKey: (TileRequest) -> String,
    private val fetchBytes: suspend (String) -> ByteArray?,
) {
    private val fetchScope = CoroutineScope(config.dispatcher + SupervisorJob())

    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<String, ByteArray>()
    private val accessOrder = ArrayDeque<String>()

    private val inFlightMutex = Mutex()
    private val inFlight = mutableMapOf<String, kotlinx.coroutines.Deferred<ByteArray?>>()

    suspend fun fetchTiles(requests: List<TileRequest>): List<Tile> =
        coroutineScope {
            val semaphore = Semaphore(config.concurrency)
            requests.map { request ->
                async(config.dispatcher) {
                    semaphore.withPermit {
                        fetchTile(request)
                    }
                }
            }
                .awaitAll()
        }

    private suspend fun fetchTile(request: TileRequest): Tile {
        val key = cacheKey(request)
        val cached = cacheGet(key)
        if (cached != null) {
            return Tile(coordinate = request.coordinate, bounds = request.bounds, bytes = cached)
        }

        val deferred =
            inFlightMutex.withLock {
                inFlight[key]?.takeUnless { it.isCancelled } ?: fetchScope.async {
                        fetchBytes(key)?.also { bytes ->
                            cachePut(key, bytes)
                        }
                    }
                    .also {
                        inFlight[key] = it
                    }
            }

        return try {
            val bytes = deferred.await()
            Tile(coordinate = request.coordinate, bounds = request.bounds, bytes = bytes)
        } finally {
            inFlightMutex.withLock {
                if (inFlight[key] === deferred) {
                    inFlight.remove(key)
                }
            }
        }
    }

    private suspend fun cacheGet(key: String): ByteArray? =
        cacheMutex.withLock {
            cache[key]?.also {
                accessOrder.remove(key)
                accessOrder.addLast(key)
            }
        }

    private suspend fun cachePut(
        key: String,
        bytes: ByteArray,
    ) =
        cacheMutex.withLock {
            if (!cache.containsKey(key)) {
                accessOrder.addLast(key)
            } else {
                accessOrder.remove(key)
                accessOrder.addLast(key)
            }
            cache[key] = bytes
            while (accessOrder.size > config.maxCacheEntries) {
                cache.remove(accessOrder.removeFirst())
            }
        }
}
