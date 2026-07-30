package tilo.compose.render

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.ControlledComposition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import tilo.compose.core.map.MapState
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MapGestureCoordinatorTest {
    @Test
    fun mapReplacementAndCompositionDisposalCancelAnimations() =
        runTest {
            val map = mutableStateOf(MapState())
            var coordinator: MapGestureCoordinator? = null
            val composition =
                withCoordinatorComposition {
                    coordinator = rememberMapGestureCoordinator(map.value)
                }
            val initialCoordinator = requireNotNull(coordinator)
            val replacementJobs = trackRunningJobs(initialCoordinator)

            map.value = MapState()
            composition.recompose()

            assertSame(initialCoordinator, coordinator)
            assertTrue(replacementJobs.all(Job::isCancelled))

            val disposalJobs = trackRunningJobs(initialCoordinator)
            composition.close()

            assertTrue(disposalJobs.all(Job::isCancelled))
        }

    private fun TestScope.trackRunningJobs(coordinator: MapGestureCoordinator): List<Job> {
        val jobs = List(3) { launch { awaitCancellation() } }
        coordinator.panFlingJob = jobs[0]
        coordinator.zoomFlingJob = jobs[1]
        coordinator.doubleTapZoomJob = jobs[2]
        return jobs
    }

    private suspend fun TestScope.withCoordinatorComposition(
        content: @androidx.compose.runtime.Composable () -> Unit,
    ): RunningComposition {
        val recomposer = Recomposer(coroutineContext)
        val composition = ControlledComposition(EmptyApplier(), recomposer)
        composition.composeContent(content)
        composition.applyChanges()
        composition.changesApplied()
        return RunningComposition(composition, recomposer)
    }

    private class RunningComposition(
        private val composition: ControlledComposition,
        private val recomposer: Recomposer,
    ) : AutoCloseable {
        fun recompose() {
            composition.invalidateAll()
            if (composition.recompose()) {
                composition.applyChanges()
                composition.changesApplied()
            }
        }

        override fun close() {
            composition.dispose()
            recomposer.close()
        }
    }

    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(
            index: Int,
            instance: Unit,
        ) = Unit

        override fun insertBottomUp(
            index: Int,
            instance: Unit,
        ) = Unit

        override fun remove(
            index: Int,
            count: Int,
        ) = Unit

        override fun move(
            from: Int,
            to: Int,
            count: Int,
        ) = Unit

        override fun onClear() = Unit
    }
}
