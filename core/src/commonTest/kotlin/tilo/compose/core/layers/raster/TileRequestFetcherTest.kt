package tilo.compose.core.layers.raster

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import tilo.compose.core.geometry.Point
import tilo.compose.core.tile.TileBounds
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.TileRequest

@OptIn(ExperimentalCoroutinesApi::class)
class TileRequestFetcherTest {

    @Test
    fun concurrencyLimitAppliesAcrossTheWholeFetcher() = runTest {
        var active = 0
        var maxActive = 0
        val release = CompletableDeferred<Unit>()
        val fetcher = TileRequestFetcher(
            config = TileFetchConfig(
                concurrency = 2,
                dispatcher = StandardTestDispatcher(testScheduler),
            ),
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

    @Test
    fun concurrentWaitersShareOneFetch() = runTest {
        var fetchCount = 0
        val release = CompletableDeferred<Unit>()
        val fetcher = TileRequestFetcher(
            config = TileFetchConfig(dispatcher = StandardTestDispatcher(testScheduler)),
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

    @Test
    fun queuedFetchIsCancelledWhenItsLastWaiterIsCancelled() = runTest {
        var queuedFetchStarted = false
        val releaseActiveFetch = CompletableDeferred<Unit>()
        val fetcher = TileRequestFetcher(
            config = TileFetchConfig(
                concurrency = 1,
                dispatcher = StandardTestDispatcher(testScheduler),
            ),
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

    @Test
    fun activeFetchCanBeReusedAfterItsOnlyWaiterIsCancelled() = runTest {
        var fetchCount = 0
        val release = CompletableDeferred<Unit>()
        val fetcher = TileRequestFetcher(
            config = TileFetchConfig(dispatcher = StandardTestDispatcher(testScheduler)),
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
            bounds = TileBounds(
                topLeft = Point(x.toDouble(), 1.0),
                bottomRight = Point(x + 1.0, 0.0),
            ),
        )
}
