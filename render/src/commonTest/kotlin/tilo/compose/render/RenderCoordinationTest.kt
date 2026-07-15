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
    fun rapidViewportChangesPublishOnlyLatestFrame() = runTest {
        val requests = MutableSharedFlow<String>(extraBufferCapacity = 2)
        val firstStarted = CompletableDeferred<Unit>()
        val neverReleaseObsolete = CompletableDeferred<Unit>()
        val published = mutableListOf<String>()
        val collector = backgroundScope.launch {
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
        val first = tracker.next(testMap(center = tilo.compose.core.geometry.Point(1.0, 0.0)))
        val latest = tracker.next(testMap(center = tilo.compose.core.geometry.Point(2.0, 0.0)))

        assertFalse(tracker.isLatest(first))
        assertTrue(tracker.isLatest(latest))
    }

    /**
     * Verifies supervisor isolation when vector rendering fails.
     *
     * Input: a throwing vector branch and a healthy raster branch running as siblings.
     * Expected: the vector error is contained and raster publication still occurs.
     */
    @Test
    fun vectorFailureDoesNotPreventRasterBranchPublication() = runTest {
        var rasterPublished = false

        supervisorScope {
            launch {
                runRenderBranch { error("broken vector source") }
            }
            launch {
                runRenderBranch { rasterPublished = true }
            }
        }

        assertTrue(rasterPublished)
    }

    /**
     * Verifies supervisor isolation when raster rendering fails.
     *
     * Input: a throwing raster branch and a healthy vector branch running as siblings.
     * Expected: the raster error is contained and vector publication still occurs.
     */
    @Test
    fun rasterFailureDoesNotPreventVectorBranchPublication() = runTest {
        var vectorPublished = false

        supervisorScope {
            launch {
                runRenderBranch { error("broken tile decoder") }
            }
            launch {
                runRenderBranch { vectorPublished = true }
            }
        }

        assertTrue(vectorPublished)
    }

    /**
     * Verifies that branch-level fault isolation never hides coroutine cancellation.
     *
     * Input: a render branch throwing `CancellationException`.
     * Expected: the cancellation is rethrown to stop obsolete render work.
     */
    @Test
    fun renderBranchNeverSwallowsCancellation() = runTest {
        assertFailsWith<CancellationException> {
            runRenderBranch { throw CancellationException("obsolete") }
        }
    }
}
