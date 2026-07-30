@file:OptIn(ExperimentalTiloApi::class)

package tilo.samples

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.ControlledComposition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.MapCameraState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MapDefaultsCompositionTest {
    @Test
    fun webMercatorCameraSurvivesUnrelatedRecomposition() =
        runTest {
            assertCameraSurvivesRecomposition { rememberWebMercatorCamera(zoom = 12.2) }
        }

    @Test
    fun sjtskCameraSurvivesUnrelatedRecomposition() =
        runTest {
            assertCameraSurvivesRecomposition { rememberSjtskCamera() }
        }

    private suspend fun TestScope.assertCameraSurvivesRecomposition(
        rememberCamera: @androidx.compose.runtime.Composable () -> MapCameraState,
    ) {
        val recompositionTrigger = mutableStateOf(0)
        var camera: MapCameraState? = null
        val composition =
            withCameraComposition {
                recompositionTrigger.value
                camera = rememberCamera()
            }

        composition.use {
            val initialCamera = requireNotNull(camera)
            initialCamera.zoomIn()
            val changedZoom = initialCamera.zoom

            recompositionTrigger.value += 1
            composition.recompose()

            assertSame(initialCamera, camera)
            assertEquals(changedZoom, requireNotNull(camera).zoom)
        }
    }

    private suspend fun TestScope.withCameraComposition(
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
