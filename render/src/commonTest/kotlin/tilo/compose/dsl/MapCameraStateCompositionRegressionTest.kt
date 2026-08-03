@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.ControlledComposition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.CameraPosition
import tilo.compose.core.map.MapConfig
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.projection.IdentityProjection
import tilo.compose.core.projection.Projection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/** Regression coverage for the input semantics of [rememberMapCameraState]. */
class MapCameraStateCompositionRegressionTest {
    @Test
    fun immutableInitialPositionUsesTheSameOneTimeInputSemantics() =
        runTest {
            val position = mutableStateOf(CameraPosition(Point(1.0, 2.0), zoom = 3.0, bearing = 15.0))
            var cameraState: MapCameraState? = null

            withCameraComposition {
                cameraState = rememberMapCameraState(initialPosition = position.value)
            }.use { composition ->
                val initialState = requireNotNull(cameraState)
                position.value = CameraPosition(Point(10.0, 20.0), zoom = 8.0, bearing = 120.0)
                composition.recompose()

                assertSame(initialState, cameraState)
                assertEquals(CameraPosition(Point(1.0, 2.0), 3.0, 15.0), initialState.position)
            }
        }

    @Test
    fun rememberedCameraResolvesPlatformCrsWithoutExplicitRegistration() =
        runTest {
            var cameraState: MapCameraState? = null

            withCameraComposition {
                cameraState = rememberMapCameraState(projection = Epsg3857Projection)
            }.use {
                val transformed =
                    requireNotNull(cameraState)
                        .mapState
                        .transformSourceToTarget(
                            Point(0.0, 0.0),
                            Epsg4326Projection,
                            Epsg3857Projection,
                        )

                assertEquals(0.0, transformed.x, absoluteTolerance = 1e-6)
                assertEquals(0.0, transformed.y, absoluteTolerance = 1e-6)
            }
        }

    @Test
    fun rememberedCameraDiscoversCustomProjectionTransformationWithoutMapConfig() =
        runTest {
            val localGrid =
                referencedProjection(
                    id = "TEST:LOCAL-GRID",
                    reference = wgs84(),
                    toReference = { point -> Point(point.x / 2.0, point.y / 2.0) },
                    fromReference = { point -> Point(point.x * 2.0, point.y * 2.0) },
                )
            var cameraState: MapCameraState? = null

            withCameraComposition {
                cameraState = rememberMapCameraState(projection = localGrid)
            }.use {
                val state = requireNotNull(cameraState).mapState

                assertEquals(
                    Point(4.0, 6.0),
                    state.transformSourceToTarget(Point(2.0, 3.0), wgs84(), localGrid),
                )
                assertEquals(
                    Point(2.0, 3.0),
                    state.transformSourceToTarget(Point(4.0, 6.0), localGrid, wgs84()),
                )
            }
        }

    @Test
    fun inlineProjectionDslKeepsCameraStableAcrossRecomposition() =
        runTest {
            val trigger = mutableStateOf(0)
            var cameraState: MapCameraState? = null

            withCameraComposition {
                trigger.value
                cameraState =
                    rememberMapCameraState(
                        initialCenter = Point(1.0, 2.0),
                        projection = projection("EPSG:3857"),
                    )
            }.use { composition ->
                val initial = requireNotNull(cameraState)

                trigger.value += 1
                composition.recompose()

                assertSame(initial, cameraState)
                assertEquals(Point(1.0, 2.0), requireNotNull(cameraState).center)
            }
        }

    @Test
    fun inlineReferencedProjectionDslKeepsCameraStableAcrossRecomposition() =
        runTest {
            val trigger = mutableStateOf(0)
            var cameraState: MapCameraState? = null

            withCameraComposition {
                trigger.value
                cameraState =
                    rememberMapCameraState(
                        projection =
                            referencedProjection(
                                id = "TEST:LOCAL",
                                reference = wgs84(),
                                toReference = { it },
                                fromReference = { it },
                            ),
                    )
            }.use { composition ->
                val initial = requireNotNull(cameraState)

                trigger.value += 1
                composition.recompose()

                assertSame(initial, cameraState)
            }
        }

    /**
     * Verifies that initial camera values are one-time inputs.
     *
     * Input: initial center and zoom values changed during recomposition.
     * Expected: the same camera state keeps the center and zoom with which it was created.
     */
    @Test
    fun initialCameraValuesDoNotResetExistingState() =
        runTest {
            val center = mutableStateOf(Point(1.0, 2.0))
            val zoom = mutableStateOf(3.0)
            val bearing = mutableStateOf(15.0)
            var cameraState: MapCameraState? = null

            withCameraComposition {
                cameraState =
                    rememberMapCameraState(
                        initialCenter = center.value,
                        initialZoom = zoom.value,
                        initialBearing = bearing.value,
                    )
            }.use { composition ->
                val initialState = requireNotNull(cameraState)

                center.value = Point(10.0, 20.0)
                zoom.value = 8.0
                bearing.value = 120.0
                composition.recompose()
                val recomposedState = requireNotNull(cameraState)

                assertSame(initialState, recomposedState)
                assertEquals(Point(1.0, 2.0), recomposedState.center)
                assertEquals(3.0, recomposedState.zoom)
                assertEquals(15.0, recomposedState.bearing)
            }
        }

    /**
     * Verifies that projection and map configuration define camera-state identity.
     *
     * Input: a projection replacement and then a configuration replacement across recompositions.
     * Expected: each immutable input change creates a new state initialized from current initial values.
     */
    @Test
    fun projectionAndConfigChangesReplaceRememberedState() =
        runTest {
            val center = mutableStateOf(Point(1.0, 2.0))
            val zoom = mutableStateOf(3.0)
            val projection = mutableStateOf<Projection>(IdentityProjection)
            val config = mutableStateOf(MapConfig.Default)
            var cameraState: MapCameraState? = null

            withCameraComposition {
                cameraState =
                    rememberMapCameraState(
                        initialCenter = center.value,
                        initialZoom = zoom.value,
                        projection = projection.value,
                        config = config.value,
                    )
            }.use { composition ->
                val identityState = requireNotNull(cameraState)

                center.value = Point(10.0, 20.0)
                zoom.value = 8.0
                projection.value = Epsg3857Projection
                composition.recompose()
                val projectedState = requireNotNull(cameraState)

                assertNotSame(identityState, projectedState)
                assertSame(Epsg3857Projection, projectedState.projection)
                assertEquals(Point(10.0, 20.0), projectedState.center)
                assertEquals(8.0, projectedState.zoom)

                val replacementConfig = MapConfig(minZoom = 2.0, maxZoom = 18.0)
                config.value = replacementConfig
                composition.recompose()
                val configuredState = requireNotNull(cameraState)

                assertNotSame(projectedState, configuredState)
                assertSame(replacementConfig, configuredState.config)
                assertEquals(Point(10.0, 20.0), configuredState.center)
                assertEquals(8.0, configuredState.zoom)
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
