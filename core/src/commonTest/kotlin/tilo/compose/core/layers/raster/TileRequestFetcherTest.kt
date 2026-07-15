package tilo.compose.core.layers.raster

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tilo.compose.core.geometry.Point
import tilo.compose.core.tile.TileBounds
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.TileRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TileRequestFetcherTest {
    /** Invalid limits fail when the public configuration is constructed. */
    @Test
    fun invalidConfigurationIsRejectedImmediately() {
        assertFailsWith<IllegalArgumentException> {
            TileFetchConfig(maxCacheEntries = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            TileFetchConfig(concurrency = 0)
        }
    }

    /**
     * Verifies that a transport failure is observable without cancelling healthy tile results.
     *
     * Input: one request whose source throws and one request returning bytes.
     * Expected: the failed tile is empty, the healthy tile succeeds, and the callback receives
     * the original error once.
     */
    @Test
    fun sourceFailureIsReportedAndIsolated() =
        runTest {
            val expectedError = IllegalStateException("DNS lookup failed")
            val reportedErrors = mutableListOf<Throwable>()
            val fetcher =
                TileRequestFetcher(
                    dispatcher = StandardTestDispatcher(testScheduler),
                    onError = reportedErrors::add,
                    cacheKey = { request -> request.coordinate.toString() },
                    fetchBytes = { request ->
                        if (request.coordinate.x == 1) throw expectedError
                        byteArrayOf(2)
                    },
                )

            val tiles = fetcher.fetchTiles(listOf(request(1), request(2)))

            assertEquals(null, tiles[0].bytes)
            assertEquals(2, tiles[1].bytes?.single())
            assertEquals(1, reportedErrors.size)
            assertSame(expectedError, reportedErrors.single())
        }

    /**
     * Verifies that a successful tile is reused from the in-memory cache.
     *
     * Input: two sequential requests with the same cache key and a source returning byte `7`.
     * Expected: both results contain byte `7`, while the source is invoked exactly once.
     */
    @Test
    fun successfulTileIsServedFromMemoryCache() =
        runTest {
            var fetchCount = 0
            val fetcher =
                TileRequestFetcher(
                    dispatcher = StandardTestDispatcher(testScheduler),
                    cacheKey = { request -> request.coordinate.toString() },
                    fetchBytes = {
                        fetchCount += 1
                        byteArrayOf(7)
                    },
                )

            assertEquals(
                7,
                fetcher
                    .fetchTiles(listOf(request(1)))
                    .single()
                    .bytes
                    ?.single(),
            )
            assertEquals(
                7,
                fetcher
                    .fetchTiles(listOf(request(1)))
                    .single()
                    .bytes
                    ?.single(),
            )
            assertEquals(1, fetchCount)
        }

    /**
     * Verifies that an unavailable tile is not cached and can recover on a later request.
     *
     * Input: a source returning `null` first and byte `9` for the same key second.
     * Expected: the first tile has no bytes, the second succeeds, and the source runs twice.
     */
    @Test
    fun unavailableTileIsNotCachedAndCanRecover() =
        runTest {
            var fetchCount = 0
            val fetcher =
                TileRequestFetcher(
                    dispatcher = StandardTestDispatcher(testScheduler),
                    cacheKey = { "same" },
                    fetchBytes = {
                        fetchCount += 1
                        if (fetchCount == 1) null else byteArrayOf(9)
                    },
                )

            assertEquals(null, fetcher.fetchTiles(listOf(request(1))).single().bytes)
            assertEquals(
                9,
                fetcher
                    .fetchTiles(listOf(request(1)))
                    .single()
                    .bytes
                    ?.single(),
            )
            assertEquals(2, fetchCount)
        }

    /**
     * Verifies least-recently-used eviction when the cache reaches its entry limit.
     *
     * Input: a two-entry cache accessed in the order `1, 2, 1, 3, 2`.
     * Expected: tile `2` is evicted and fetched twice; tiles `1` and `3` are fetched once.
     */
    @Test
    fun cacheEvictsLeastRecentlyUsedTile() =
        runTest {
            val fetchCount = mutableMapOf<Int, Int>()
            val fetcher =
                TileRequestFetcher(
                    config =
                        TileFetchConfig(
                            maxCacheEntries = 2,
                        ),
                    dispatcher = StandardTestDispatcher(testScheduler),
                    cacheKey = { request -> request.coordinate.x.toString() },
                    fetchBytes = { request ->
                        val x = request.coordinate.x
                        fetchCount[x] = fetchCount.getOrElse(x) { 0 } + 1
                        byteArrayOf(x.toByte())
                    },
                )

            fetcher.fetchTiles(listOf(request(1)))
            fetcher.fetchTiles(listOf(request(2)))
            fetcher.fetchTiles(listOf(request(1))) // 1 is now most recently used.
            fetcher.fetchTiles(listOf(request(3))) // evicts 2.
            fetcher.fetchTiles(listOf(request(2)))

            assertEquals(mapOf(1 to 1, 2 to 2, 3 to 1), fetchCount)
        }

    /**
     * Verifies that configuring zero cache entries disables byte reuse.
     *
     * Input: two sequential requests for one key with `maxCacheEntries = 0`.
     * Expected: the source is invoked twice.
     */
    @Test
    fun zeroSizedCacheAlwaysRefetches() =
        runTest {
            var fetchCount = 0
            val fetcher =
                TileRequestFetcher(
                    config =
                        TileFetchConfig(
                            maxCacheEntries = 0,
                        ),
                    dispatcher = StandardTestDispatcher(testScheduler),
                    cacheKey = { "same" },
                    fetchBytes = {
                        fetchCount += 1
                        byteArrayOf(1)
                    },
                )

            fetcher.fetchTiles(listOf(request(1)))
            fetcher.fetchTiles(listOf(request(1)))

            assertEquals(2, fetchCount)
        }

    /**
     * Verifies that closing a fetcher cancels network work owned by its private scope.
     *
     * Input: one request blocked on a deferred value, followed by `close()`.
     * Expected: the caller awaiting the request is cancelled.
     */
    @Test
    fun closeCancelsOwnedFetches() =
        runTest {
            val neverCompletes = CompletableDeferred<Unit>()
            val fetcher =
                TileRequestFetcher(
                    dispatcher = StandardTestDispatcher(testScheduler),
                    cacheKey = { "same" },
                    fetchBytes = {
                        neverCompletes.await()
                        byteArrayOf(1)
                    },
                )
            val result = async { fetcher.fetchTiles(listOf(request(1))) }
            runCurrent()

            fetcher.close()
            advanceUntilIdle()

            assertTrue(result.isCancelled)
        }

    /**
     * Verifies the concurrency cap within one multi-tile fetch operation.
     *
     * Input: three blocked tile requests with a configured concurrency of two.
     * Expected: at most two sources run simultaneously and all three tiles finish after release.
     */
    @Test
    fun concurrencyLimitAppliesAcrossTheWholeFetcher() =
        runTest {
            var active = 0
            var maxActive = 0
            val release = CompletableDeferred<Unit>()
            val fetcher =
                TileRequestFetcher(
                    config =
                        TileFetchConfig(
                            concurrency = 2,
                        ),
                    dispatcher = StandardTestDispatcher(testScheduler),
                    cacheKey = { request -> request.coordinate.toString() },
                    fetchBytes = {
                        active += 1
                        maxActive = maxOf(maxActive, active)
                        release.await()
                        active -= 1
                        byteArrayOf(1)
                    },
                )

            val result = async { fetcher.fetchTiles(listOf(request(1), request(2), request(3))) }
            runCurrent()

            assertEquals(2, maxActive)
            release.complete(Unit)
            advanceUntilIdle()
            assertEquals(3, result.await().size)
        }

    /**
     * Verifies that the concurrency cap is shared by visible, prefetch, and overview consumers.
     *
     * Input: five blocked requests started by three concurrent callers with concurrency two.
     * Expected: global active work never exceeds two and every caller receives its tiles.
     */
    @Test
    fun concurrencyLimitAppliesAcrossSimultaneousConsumers() =
        runTest {
            var active = 0
            var maxActive = 0
            val release = CompletableDeferred<Unit>()
            val fetcher =
                TileRequestFetcher(
                    config =
                        TileFetchConfig(
                            concurrency = 2,
                        ),
                    dispatcher = StandardTestDispatcher(testScheduler),
                    cacheKey = { request -> request.coordinate.toString() },
                    fetchBytes = {
                        active += 1
                        maxActive = maxOf(maxActive, active)
                        release.await()
                        active -= 1
                        byteArrayOf(1)
                    },
                )

            val visible = async { fetcher.fetchTiles(listOf(request(1), request(2))) }
            val prefetch = async { fetcher.fetchTiles(listOf(request(3), request(4))) }
            val overview = async { fetcher.fetchTiles(listOf(request(5))) }
            runCurrent()

            assertEquals(2, maxActive)
            release.complete(Unit)
            advanceUntilIdle()
            assertEquals(2, visible.await().size)
            assertEquals(2, prefetch.await().size)
            assertEquals(1, overview.await().size)
        }

    /**
     * Verifies in-flight request coalescing across independent render consumers.
     *
     * Input: visible, prefetch, and overview callers requesting the same key concurrently.
     * Expected: one source invocation supplies one result to each of the three callers.
     */
    @Test
    fun overlappingConsumersShareTheSameRequest() =
        runTest {
            var fetchCount = 0
            val release = CompletableDeferred<Unit>()
            val fetcher =
                TileRequestFetcher(
                    dispatcher = StandardTestDispatcher(testScheduler),
                    cacheKey = { "shared" },
                    fetchBytes = {
                        fetchCount += 1
                        release.await()
                        byteArrayOf(1)
                    },
                )

            val visible = async { fetcher.fetchTiles(listOf(request(1))) }
            val prefetch = async { fetcher.fetchTiles(listOf(request(1))) }
            val overview = async { fetcher.fetchTiles(listOf(request(1))) }
            runCurrent()

            assertEquals(1, fetchCount)
            release.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, visible.await().size)
            assertEquals(1, prefetch.await().size)
            assertEquals(1, overview.await().size)
        }

    /**
     * Verifies duplicate coordinates within one batch also share one in-flight request.
     *
     * Input: a batch containing the same tile request twice while the source is blocked.
     * Expected: the source runs once and the returned batch still contains two tiles.
     */
    @Test
    fun concurrentWaitersShareOneFetch() =
        runTest {
            var fetchCount = 0
            val release = CompletableDeferred<Unit>()
            val fetcher =
                TileRequestFetcher(
                    dispatcher = StandardTestDispatcher(testScheduler),
                    cacheKey = { "same" },
                    fetchBytes = {
                        fetchCount += 1
                        release.await()
                        byteArrayOf(1)
                    },
                )

            val result = async { fetcher.fetchTiles(listOf(request(1), request(1))) }
            runCurrent()

            assertEquals(1, fetchCount)
            release.complete(Unit)
            advanceUntilIdle()
            assertEquals(2, result.await().size)
        }

    /**
     * Verifies that queued work is removed when its final waiter disappears before execution.
     *
     * Input: one active request, one queued request, then cancellation of the queued caller.
     * Expected: the queued source never starts and the active request completes normally.
     */
    @Test
    fun queuedFetchIsCancelledWhenItsLastWaiterIsCancelled() =
        runTest {
            var queuedFetchStarted = false
            val releaseActiveFetch = CompletableDeferred<Unit>()
            val fetcher =
                TileRequestFetcher(
                    config =
                        TileFetchConfig(
                            concurrency = 1,
                        ),
                    dispatcher = StandardTestDispatcher(testScheduler),
                    cacheKey = { request -> request.coordinate.toString() },
                    fetchBytes = { request ->
                        if (request.coordinate.x == 1) {
                            releaseActiveFetch.await()
                        } else {
                            queuedFetchStarted = true
                        }
                        byteArrayOf(1)
                    },
                )

            val active = async { fetcher.fetchTiles(listOf(request(1))) }
            runCurrent()
            val queued = async { fetcher.fetchTiles(listOf(request(2))) }
            runCurrent()

            queued.cancelAndJoin()
            releaseActiveFetch.complete(Unit)
            advanceUntilIdle()

            assertFalse(queuedFetchStarted)
            assertEquals(1, active.await().size)
        }

    /**
     * Verifies that active network work remains reusable during a caller handover.
     *
     * Input: cancel the only waiter of a started request, then immediately request the same key.
     * Expected: the replacement joins the original work, so the source is invoked only once.
     */
    @Test
    fun activeFetchCanBeReusedAfterItsOnlyWaiterIsCancelled() =
        runTest {
            var fetchCount = 0
            val release = CompletableDeferred<Unit>()
            val fetcher =
                TileRequestFetcher(
                    dispatcher = StandardTestDispatcher(testScheduler),
                    cacheKey = { "same" },
                    fetchBytes = {
                        fetchCount += 1
                        release.await()
                        byteArrayOf(1)
                    },
                )

            val first = async { fetcher.fetchTiles(listOf(request(1))) }
            runCurrent()
            assertEquals(1, fetchCount)

            first.cancelAndJoin()
            val replacement = async { fetcher.fetchTiles(listOf(request(1))) }
            runCurrent()
            assertEquals(1, fetchCount)

            release.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, replacement.await().size)
        }

    private fun request(x: Int): TileRequest =
        TileRequest(
            coordinate = TileCoordinate(z = 1, x = x, y = 0),
            bounds =
                TileBounds(
                    topLeft = Point(x.toDouble(), 1.0),
                    bottomRight = Point(x + 1.0, 0.0),
                ),
        )
}
