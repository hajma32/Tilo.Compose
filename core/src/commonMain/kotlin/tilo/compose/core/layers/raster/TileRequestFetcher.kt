package tilo.compose.core.layers.raster

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
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
    private val fetchBytes: suspend (TileRequest) -> ByteArray?,
) {
    private val fetchScope = CoroutineScope(config.dispatcher + SupervisorJob())

    init {
        require(config.maxCacheEntries >= 0) { "maxCacheEntries must be non-negative" }
        require(config.concurrency > 0) { "concurrency must be positive" }
    }

    private val fetchSemaphore = Semaphore(config.concurrency)

    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<String, ByteArray>()
    private val accessOrder = ArrayDeque<String>()

    private val inFlightMutex = Mutex()
    private val inFlight = mutableMapOf<String, InFlightRequest>()

    suspend fun fetchTiles(requests: List<TileRequest>): List<Tile> =
        coroutineScope {
            requests.map { request ->
                async(config.dispatcher) {
                    fetchTile(request)
                }
            }
                .awaitAll()
        }

    fun close() {
        fetchScope.cancel()
    }

    private suspend fun fetchTile(request: TileRequest): Tile {
        val key = cacheKey(request)
        val cached = cacheGet(key)
        if (cached != null) {
            return Tile(coordinate = request.coordinate, bounds = request.bounds, bytes = cached)
        }

        val inFlightRequest =
            inFlightMutex.withLock {
                val existing = inFlight[key]?.takeUnless { it.deferred.isCancelled }
                if (existing != null) {
                    existing.waiters += 1
                    existing
                } else {
                    InFlightRequest().also { created ->
                        created.deferred = fetchScope.async {
                            fetchSemaphore.withPermit {
                                inFlightMutex.withLock {
                                    created.started = true
                                }
                                fetchBytes(request)?.also { bytes ->
                                    cachePut(key, bytes)
                                }
                            }
                        }
                        created.waiters = 1
                        inFlight[key] = created
                    }
                }
            }

        return try {
            val bytes = inFlightRequest.deferred.await()
            Tile(coordinate = request.coordinate, bounds = request.bounds, bytes = bytes)
        } finally {
            withContext(NonCancellable) {
                var cleanUpAfterCompletion = false
                inFlightMutex.withLock {
                    val current = inFlight[key]
                    if (current === inFlightRequest) {
                        current.waiters -= 1
                    }
                    if (current === inFlightRequest && current.waiters == 0) {
                        if (current.started && !current.deferred.isCompleted) {
                            cleanUpAfterCompletion = true
                        } else {
                            inFlight.remove(key)
                            if (!current.deferred.isCompleted) {
                                current.deferred.cancel()
                            }
                        }
                    }
                }
                if (cleanUpAfterCompletion) {
                    cleanUpWhenUnused(key, inFlightRequest)
                }
            }
        }
    }

    private fun cleanUpWhenUnused(
        key: String,
        request: InFlightRequest,
    ) {
        fetchScope.launch {
            request.deferred.join()
            inFlightMutex.withLock {
                val current = inFlight[key]
                if (current === request && current.waiters == 0) {
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

    private class InFlightRequest {
        lateinit var deferred: Deferred<ByteArray?>
        var waiters: Int = 0
        var started: Boolean = false
    }
}
