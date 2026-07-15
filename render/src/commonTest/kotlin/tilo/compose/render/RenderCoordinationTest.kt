package tilo.compose.render

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RenderCoordinationTest {
    /**
     * Verifies latest-request-wins behavior during rapid viewport changes.
     *
     * Input: requests `A`, `B`, and `C`, with obsolete work blocked indefinitely.
     * Expected: obsolete collectors are cancelled and only `C` reaches publication.
     */
    @Test
    fun rapidViewportChangesPublishOnlyLatestFrame() =
        runTest {
            val requests = MutableSharedFlow<String>(extraBufferCapacity = 2)
            val firstStarted = CompletableDeferred<Unit>()
            val neverReleaseObsolete = CompletableDeferred<Unit>()
            val published = mutableListOf<String>()
            val collector =
                backgroundScope.launch {
                    requests.collectLatestRenderRequest { viewport ->
                        if (viewport != "C") {
                            if (viewport == "A") firstStarted.complete(Unit)
                            neverReleaseObsolete.await()
                        }
                        published += viewport
                    }
                }
            runCurrent()

            requests.emit("A")
            firstStarted.await()
            requests.emit("B")
            runCurrent()
            requests.emit("C")
            runCurrent()

            assertEquals(listOf("C"), published)
            assertFalse(neverReleaseObsolete.isCompleted)
            collector.cancel()
        }

    /**
     * Verifies rejection of an overview result created for an obsolete viewport.
     *
     * Input: two overview requests issued sequentially by one tracker.
     * Expected: the first is stale and only the second is recognized as latest.
     */
    @Test
    fun staleOverviewRequestCannotReplaceLatestViewport() {
        val tracker = OverviewRequestTracker()
        val first =
            tracker.next(
                testMap(
                    center =
                        tilo.compose.core.geometry
                            .Point(1.0, 0.0),
                ),
            )
        val latest =
            tracker.next(
                testMap(
                    center =
                        tilo.compose.core.geometry
                            .Point(2.0, 0.0),
                ),
            )

        assertFalse(tracker.isLatest(first))
        assertTrue(tracker.isLatest(latest))
    }

    /**
     * Verifies supervisor isolation when vector rendering fails.
     *
     * Input: a throwing vector branch and a healthy raster branch running as siblings.
     * Expected: the error is reported exactly once and raster publication still occurs.
     */
    @Test
    fun vectorFailureDoesNotPreventRasterBranchPublication() =
        runTest {
            var rasterPublished = false
            val failure = IllegalStateException("broken vector source")
            val reported = mutableListOf<Throwable>()

            supervisorScope {
                launch {
                    runRenderBranch(onError = reported::add) { throw failure }
                }
                launch {
                    runRenderBranch { rasterPublished = true }
                }
            }

            assertTrue(rasterPublished)
            assertEquals(1, reported.size)
            assertSame(failure, reported.single())
        }

    /**
     * Verifies supervisor isolation when raster rendering fails.
     *
     * Input: a throwing raster branch and a healthy vector branch running as siblings.
     * Expected: the error is reported exactly once and vector publication still occurs.
     */
    @Test
    fun rasterFailureDoesNotPreventVectorBranchPublication() =
        runTest {
            var vectorPublished = false
            val failure = IllegalArgumentException("broken tile source")
            val reported = mutableListOf<Throwable>()

            supervisorScope {
                launch {
                    runRenderBranch(onError = reported::add) { throw failure }
                }
                launch {
                    runRenderBranch { vectorPublished = true }
                }
            }

            assertTrue(vectorPublished)
            assertEquals(1, reported.size)
            assertSame(failure, reported.single())
        }

    /**
     * Verifies that branch-level fault isolation never hides coroutine cancellation.
     *
     * Input: a render branch throwing `CancellationException`.
     * Expected: the cancellation is rethrown to stop obsolete render work and is not reported as an error.
     */
    @Test
    fun renderBranchNeverSwallowsCancellation() =
        runTest {
            val reported = mutableListOf<Throwable>()

            assertFailsWith<CancellationException> {
                runRenderBranch(onError = reported::add) { throw CancellationException("obsolete") }
            }
            assertTrue(reported.isEmpty())
        }
}
