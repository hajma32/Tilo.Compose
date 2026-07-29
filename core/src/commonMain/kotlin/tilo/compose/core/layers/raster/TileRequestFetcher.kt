package tilo.compose.core.layers.raster

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
import kotlin.math.min
import kotlin.time.TimeSource

/** Cache, concurrency, and recoverable-failure retry limits for raster tile fetches. */
data class TileFetchConfig(
    val maxCacheEntries: Int = 200,
    val concurrency: Int = 8,
    val failureBackoffMillis: Long = 1_000,
    val maxFailureBackoffMillis: Long = 30_000,
    val maxFailureEntries: Int = maxCacheEntries,
) {
    init {
        require(maxCacheEntries >= 0) { "maxCacheEntries must be non-negative" }
        require(concurrency > 0) { "concurrency must be positive" }
        require(failureBackoffMillis >= 0) { "failureBackoffMillis must be non-negative" }
        require(maxFailureBackoffMillis >= failureBackoffMillis) {
            "maxFailureBackoffMillis must not be less than failureBackoffMillis"
        }
        require(maxFailureEntries >= 0) { "maxFailureEntries must be non-negative" }
    }
}

internal class TileRequestFetcher(
    private val config: TileFetchConfig = TileFetchConfig(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onError: ((Throwable) -> Unit)? = null,
    private val onDiagnostic: (suspend (RasterTileDiagnosticEvent) -> Unit)? = null,
    private val cacheKey: (TileRequest) -> String,
    private val fetchBytes: suspend (TileRequest) -> ByteArray?,
    private val fetchResult: (suspend (TileRequest) -> TileReadResult)? = null,
    private val nowMillis: () -> Long = monotonicClock(),
) {
    private val fetchScope = CoroutineScope(dispatcher + SupervisorJob())

    private val fetchSemaphore = Semaphore(config.concurrency)

    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<String, ByteArray>()
    private val accessOrder = ArrayDeque<String>()
    private var cacheHits = 0L
    private var cacheMisses = 0L
    private var cacheEvictions = 0L

    private val inFlightMutex = Mutex()
    private val inFlight = mutableMapOf<String, InFlightRequest>()
    private var sourceFetches = 0L
    private var coalescedRequests = 0L
    private var successfulFetches = 0L
    private var missingFetches = 0L
    private var failedFetches = 0L
    private var suppressedByBackoff = 0L
    private val failures = mutableMapOf<String, FailureBackoff>()
    private val failureAccessOrder = ArrayDeque<String>()

    suspend fun fetchTiles(
        requests: List<TileRequest>,
        purpose: RasterTileRequestPurpose = RasterTileRequestPurpose.Visible,
    ): List<Tile> {
        val fetched =
            coroutineScope {
                requests
                    .map { request ->
                        async(dispatcher) {
                            fetchTile(request)
                        }
                    }.awaitAll()
            }
        emitDiagnostic(
            RasterTileDiagnosticEvent.BatchCompleted(
                RasterTileBatchSummary(
                    purpose = purpose,
                    requested = fetched.size,
                    succeeded = fetched.count { it.outcome == TileOutcome.Success },
                    missing = fetched.count { it.outcome == TileOutcome.Missing },
                    failed = fetched.count { it.outcome == TileOutcome.Failed },
                    networkFailures = fetched.count { it.failureKind == RasterTileFailureKind.NetworkUnavailable },
                ),
            ),
        )
        return fetched.map(FetchedTile::tile)
    }

    fun close() {
        fetchScope.cancel()
    }

    suspend fun metrics(): TileFetchMetrics {
        val cacheSnapshot =
            cacheMutex.withLock {
                CacheMetricsSnapshot(
                    entries = cache.size,
                    hits = cacheHits,
                    misses = cacheMisses,
                    evictions = cacheEvictions,
                )
            }
        val requestSnapshot =
            inFlightMutex.withLock {
                RequestMetricsSnapshot(
                    sourceFetches = sourceFetches,
                    coalescedRequests = coalescedRequests,
                    inFlightRequests = inFlight.size,
                    succeeded = successfulFetches,
                    missing = missingFetches,
                    failed = failedFetches,
                    suppressedByBackoff = suppressedByBackoff,
                    failureBackoffEntries = failures.size,
                )
            }
        return TileFetchMetrics(
            cacheEntries = cacheSnapshot.entries,
            maxCacheEntries = config.maxCacheEntries,
            cacheHits = cacheSnapshot.hits,
            cacheMisses = cacheSnapshot.misses,
            cacheEvictions = cacheSnapshot.evictions,
            sourceFetches = requestSnapshot.sourceFetches,
            coalescedRequests = requestSnapshot.coalescedRequests,
            inFlightRequests = requestSnapshot.inFlightRequests,
            succeeded = requestSnapshot.succeeded,
            missing = requestSnapshot.missing,
            failed = requestSnapshot.failed,
            suppressedByBackoff = requestSnapshot.suppressedByBackoff,
            failureBackoffEntries = requestSnapshot.failureBackoffEntries,
        )
    }

    private suspend fun fetchTile(request: TileRequest): FetchedTile {
        val key = cacheKey(request)
        val cached = cacheGet(key, recordLookup = true)
        if (cached != null) {
            return request.fetched(cached, TileOutcome.Success)
        }

        val inFlightRequest =
            inFlightMutex.withLock {
                // Another request may have populated the cache after the optimistic lookup
                // above but before this coroutine acquired the in-flight lock.
                cacheGet(key, recordLookup = false)?.let { bytes ->
                    return request.fetched(bytes, TileOutcome.Success)
                }
                failures[key]?.takeIf { it.retryAtMillis > nowMillis() }?.let { failure ->
                    touchFailure(key)
                    suppressedByBackoff += 1
                    return request.fetched(
                        bytes = null,
                        outcome = TileOutcome.Failed,
                        failureKind = failure.kind,
                    )
                }
                val existing = inFlight[key]?.takeUnless { it.deferred.isCancelled }
                if (existing != null) {
                    existing.waiters += 1
                    coalescedRequests += 1
                    existing
                } else {
                    InFlightRequest().also { created ->
                        created.deferred =
                            fetchScope.async {
                                fetchSemaphore.withPermit {
                                    inFlightMutex.withLock {
                                        created.started = true
                                        sourceFetches += 1
                                    }
                                    val result = readResult(request)
                                    recordResult(key, request, result)
                                    if (result is TileReadResult.Success) cachePut(key, result.bytes)
                                    result
                                }
                            }
                        created.waiters = 1
                        inFlight[key] = created
                    }
                }
            }

        return try {
            when (val result = inFlightRequest.deferred.await()) {
                is TileReadResult.Success -> request.fetched(result.bytes, TileOutcome.Success)
                TileReadResult.Missing -> request.fetched(bytes = null, outcome = TileOutcome.Missing)
                is TileReadResult.Failure ->
                    request.fetched(
                        bytes = null,
                        outcome = TileOutcome.Failed,
                        failureKind = result.kind,
                    )
            }
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

    // The source callback is an exception boundary: report recoverable failures, but never
    // convert coroutine cancellation or fatal Errors into an unavailable tile.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun readResult(request: TileRequest): TileReadResult =
        try {
            fetchResult?.invoke(request)
                ?: fetchBytes(request)?.let(TileReadResult::Success)
                ?: TileReadResult.Missing
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            TileReadResult.Failure(
                kind = RasterTileFailureKind.Source,
                message = error.message ?: "Tile source failed",
                cause = error,
            )
        }

    private suspend fun recordResult(
        key: String,
        request: TileRequest,
        result: TileReadResult,
    ) {
        val failure =
            inFlightMutex.withLock {
                when (result) {
                    is TileReadResult.Success -> {
                        successfulFetches += 1
                        removeFailure(key)
                        null
                    }

                    TileReadResult.Missing -> {
                        missingFetches += 1
                        removeFailure(key)
                        null
                    }

                    is TileReadResult.Failure -> {
                        failedFetches += 1
                        val previousAttempts = failures[key]?.attempts ?: 0
                        val attempts = previousAttempts + 1
                        putFailure(
                            key,
                            FailureBackoff(
                                attempts = attempts,
                                retryAtMillis = nowMillis() + backoffMillis(attempts),
                                kind = result.kind,
                            ),
                        )
                        RasterTileFailure(
                            kind = result.kind,
                            coordinate = request.coordinate,
                            message = result.message,
                            httpStatus = result.httpStatus,
                            cause = result.cause,
                        )
                    }
                }
            }
        if (failure != null) {
            failure.cause?.let { onError?.invoke(it) }
            emitDiagnostic(RasterTileDiagnosticEvent.Failure(failure))
        }
    }

    private suspend fun emitDiagnostic(event: RasterTileDiagnosticEvent) {
        val callback = onDiagnostic ?: return
        try {
            callback(event)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Diagnostics are observational and must not fail otherwise healthy tile work.
        }
    }

    private fun putFailure(
        key: String,
        failure: FailureBackoff,
    ) {
        if (config.maxFailureEntries == 0) return
        failures[key] = failure
        touchFailure(key)
        while (failureAccessOrder.size > config.maxFailureEntries) {
            failures.remove(failureAccessOrder.removeFirst())
        }
    }

    private fun touchFailure(key: String) {
        failureAccessOrder.remove(key)
        failureAccessOrder.addLast(key)
    }

    private fun removeFailure(key: String) {
        failures.remove(key)
        failureAccessOrder.remove(key)
    }

    private fun backoffMillis(attempts: Int): Long {
        if (config.failureBackoffMillis == 0L) return 0L
        var delay = config.failureBackoffMillis
        repeat((attempts - 1).coerceAtMost(MAX_BACKOFF_SHIFTS)) {
            delay =
                if (delay >= config.maxFailureBackoffMillis / 2) {
                    config.maxFailureBackoffMillis
                } else {
                    min(delay * 2, config.maxFailureBackoffMillis)
                }
        }
        return delay
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

    private suspend fun cacheGet(
        key: String,
        recordLookup: Boolean,
    ): ByteArray? =
        cacheMutex.withLock {
            val cached = cache[key]
            if (recordLookup) {
                if (cached == null) cacheMisses += 1 else cacheHits += 1
            }
            cached?.also {
                accessOrder.remove(key)
                accessOrder.addLast(key)
            }
        }

    private suspend fun cachePut(
        key: String,
        bytes: ByteArray,
    ) = cacheMutex.withLock {
        if (!cache.containsKey(key)) {
            accessOrder.addLast(key)
        } else {
            accessOrder.remove(key)
            accessOrder.addLast(key)
        }
        cache[key] = bytes
        while (accessOrder.size > config.maxCacheEntries) {
            cache.remove(accessOrder.removeFirst())
            cacheEvictions += 1
        }
    }

    private data class CacheMetricsSnapshot(
        val entries: Int,
        val hits: Long,
        val misses: Long,
        val evictions: Long,
    )

    private data class RequestMetricsSnapshot(
        val sourceFetches: Long,
        val coalescedRequests: Long,
        val inFlightRequests: Int,
        val succeeded: Long,
        val missing: Long,
        val failed: Long,
        val suppressedByBackoff: Long,
        val failureBackoffEntries: Int,
    )

    private data class FailureBackoff(
        val attempts: Int,
        val retryAtMillis: Long,
        val kind: RasterTileFailureKind,
    )

    private data class FetchedTile(
        val tile: Tile,
        val outcome: TileOutcome,
        val failureKind: RasterTileFailureKind? = null,
    )

    private enum class TileOutcome {
        Success,
        Missing,
        Failed,
    }

    private class InFlightRequest {
        lateinit var deferred: Deferred<TileReadResult>
        var waiters: Int = 0
        var started: Boolean = false
    }

    private fun TileRequest.fetched(
        bytes: ByteArray?,
        outcome: TileOutcome,
        failureKind: RasterTileFailureKind? = null,
    ): FetchedTile =
        FetchedTile(
            tile = Tile(coordinate = coordinate, bounds = bounds, bytes = bytes),
            outcome = outcome,
            failureKind = failureKind,
        )

    private companion object {
        const val MAX_BACKOFF_SHIFTS = 30

        fun monotonicClock(): () -> Long {
            val origin = TimeSource.Monotonic.markNow()
            return { origin.elapsedNow().inWholeMilliseconds }
        }
    }
}
