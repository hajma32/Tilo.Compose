@file:OptIn(ExperimentalTiloApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package tilo.compose.dsl

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.ControlledComposition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.raster.RasterHttpConfig
import tilo.compose.core.layers.raster.RasterHttpResponse
import tilo.compose.core.layers.raster.RasterHttpTransport
import tilo.compose.core.layers.raster.RasterTileLayer
import tilo.compose.core.layers.raster.RasterTileSource
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import tilo.compose.core.projection.IdentityProjection
import tilo.compose.core.projection.Projection
import tilo.compose.core.tile.TileGrid
import tilo.compose.core.tile.TileRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Regression coverage for WMS runtimes owned by the declarative map DSL. */
class WmsLayerCompositionRegressionTest {
    @Test
    fun inlineTransportLambdaDoesNotRestartRuntimeOnRecomposition() =
        runTest {
            val trigger = mutableStateOf(0)
            var createCount = 0

            withWMSComposition {
                trigger.value
                val http =
                    RasterHttpConfig(
                        transport = RasterHttpTransport { RasterHttpResponse(statusCode = 200) },
                    )
                rememberManagedWmsLayer(
                    declaration(
                        key = ManagedWmsLayerKey("wms", http),
                        create = {
                            createCount += 1
                            rasterRuntime { byteArrayOf(1) }
                        },
                    ),
                )
            }.use { composition ->
                runCurrent()
                composition.recompose()
                trigger.value += 1
                composition.recompose()
                runCurrent()

                assertEquals(1, createCount)
            }
        }

    @Test
    fun capabilitiesFailureReachesLayerErrorCallback() =
        runTest {
            val expected = IllegalStateException("offline")
            val state = RasterLayerState()
            var reported: Throwable? = null
            val composition =
                withWMSComposition {
                    rememberManagedWmsLayer(
                        declaration(
                            key = ManagedWmsLayerKey("wms", "failing-source"),
                            state = state,
                            onError = { reported = it },
                            create = { throw expected },
                        ),
                    )
                }

            runCurrent()
            assertSame(expected, reported)
            assertEquals(RasterLayerStatus.Failed(expected), state.status)
            composition.close()
        }

    @Test
    fun retryAfterCapabilitiesFailureCreatesFreshRuntime() =
        runTest {
            val state = RasterLayerState()
            val expected = IllegalStateException("temporary DNS failure")
            val replacement = rasterRuntime { byteArrayOf(7) }
            var attempts = 0
            var currentLayer: TileLayer? = null

            withWMSComposition {
                currentLayer =
                    rememberManagedWmsLayer(
                        declaration(
                            key = ManagedWmsLayerKey("wms", "source-${state.retryKey}"),
                            state = state,
                            create = {
                                attempts += 1
                                if (attempts == 1) throw expected
                                replacement
                            },
                        ),
                    )
            }.use { composition ->
                runCurrent()
                assertEquals(RasterLayerStatus.Failed(expected), state.status)

                state.retry()
                composition.recompose()
                runCurrent()
                composition.recompose()

                assertEquals(2, attempts)
                assertEquals(RasterLayerStatus.Ready, state.status)
                assertEquals(
                    7,
                    requireNotNull(currentLayer)
                        .loadTiles(singleTileMap)
                        .single()
                        .bytes
                        ?.single(),
                )
            }
        }

    @Test
    fun presentationRecompositionKeepsWMSRuntimeAndTileCache() =
        runTest {
            val visible = mutableStateOf(true)
            var currentLayer: TileLayer? = null
            var createCount = 0
            var tileReadCount = 0
            val runtime =
                rasterRuntime {
                    tileReadCount += 1
                    byteArrayOf(1)
                }
            val key = ManagedWmsLayerKey("wms", "stable-source")
            val state = RasterLayerState()

            withWMSComposition {
                currentLayer =
                    rememberManagedWmsLayer(
                        declaration(
                            key = key,
                            visible = visible.value,
                            state = state,
                            create = {
                                createCount += 1
                                runtime
                            },
                        ),
                    )
            }.use { composition ->
                runCurrent()
                composition.recompose()
                val firstLayer = requireNotNull(currentLayer)
                assertEquals(RasterLayerStatus.Ready, state.status)
                firstLayer.loadTiles(singleTileMap)

                visible.value = false
                composition.recompose()
                val hiddenLayer = requireNotNull(currentLayer)
                assertNotSame(firstLayer, hiddenLayer)
                assertEquals(false, hiddenLayer.visible)
                hiddenLayer.loadTiles(singleTileMap)

                assertEquals(1, createCount)
                assertEquals(1, tileReadCount, "Presentation changes must preserve the WMS tile cache")
            }
        }

    @Test
    fun changingSourceCancelsCapabilitiesLoadAndCreatesReplacement() =
        runTest {
            val firstStarted = CompletableDeferred<Unit>()
            val firstCancelled = CompletableDeferred<Unit>()
            val useSecond = mutableStateOf(false)
            val state = RasterLayerState()
            val secondRuntime = rasterRuntime { byteArrayOf(2) }
            var currentLayer: TileLayer? = null

            withWMSComposition {
                val second = useSecond.value
                currentLayer =
                    rememberManagedWmsLayer(
                        if (second) {
                            declaration(
                                key = ManagedWmsLayerKey("wms", "second"),
                                state = state,
                                create = { secondRuntime },
                            )
                        } else {
                            declaration(
                                key = ManagedWmsLayerKey("wms", "first"),
                                state = state,
                                create = {
                                    firstStarted.complete(Unit)
                                    try {
                                        awaitCancellation()
                                    } finally {
                                        firstCancelled.complete(Unit)
                                    }
                                },
                            )
                        },
                    )
            }.use { composition ->
                runCurrent()
                firstStarted.await()
                assertEquals(RasterLayerStatus.Loading, state.status)

                useSecond.value = true
                composition.recompose()
                runCurrent()
                firstCancelled.await()
                composition.recompose()
                assertEquals(RasterLayerStatus.Ready, state.status)

                assertEquals(
                    2,
                    requireNotNull(currentLayer)
                        .loadTiles(singleTileMap)
                        .single()
                        .bytes
                        ?.single(),
                )
            }
        }

    @Test
    fun replacingStateDuringCapabilitiesLoadPublishesLoadingToNewState() =
        runTest {
            val firstState = RasterLayerState()
            val secondState = RasterLayerState()
            val selectedState = mutableStateOf(firstState)
            val loadingStarted = CompletableDeferred<Unit>()
            val composition =
                withWMSComposition {
                    rememberManagedWmsLayer(
                        declaration(
                            key = ManagedWmsLayerKey("wms", "stable-source"),
                            state = selectedState.value,
                            create = {
                                loadingStarted.complete(Unit)
                                awaitCancellation()
                            },
                        ),
                    )
                }

            runCurrent()
            loadingStarted.await()
            assertEquals(RasterLayerStatus.Loading, firstState.status)

            selectedState.value = secondState
            composition.recompose()

            assertEquals(RasterLayerStatus.Idle, firstState.status)
            assertEquals(RasterLayerStatus.Loading, secondState.status)
            composition.close()
            assertEquals(RasterLayerStatus.Idle, secondState.status)
        }

    @Test
    fun disposingCompositionClosesLoadedWMSRuntime() =
        runTest {
            val requestStarted = CompletableDeferred<Unit>()
            val requestCancelled = CompletableDeferred<Unit>()
            val runtime =
                rasterRuntime {
                    requestStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        requestCancelled.complete(Unit)
                    }
                }
            var currentLayer: TileLayer? = null
            val composition =
                withWMSComposition {
                    currentLayer =
                        rememberManagedWmsLayer(
                            declaration(
                                key = ManagedWmsLayerKey("wms", "source"),
                                create = { runtime },
                            ),
                        )
                }

            runCurrent()
            composition.recompose()
            val request = async { requireNotNull(currentLayer).loadTiles(singleTileMap) }
            requestStarted.await()
            composition.close()
            requestCancelled.await()
            request.join()

            assertTrue(request.isCancelled, "Disposed WMS declarations must not retain tile I/O")
        }

    private fun declaration(
        key: ManagedWmsLayerKey,
        visible: Boolean = true,
        state: RasterLayerState? = null,
        onError: ((Throwable) -> Unit)? = null,
        create: suspend ((Throwable) -> Unit) -> RasterTileLayer,
    ): ManagedWmsLayerDeclaration =
        ManagedWmsLayerDeclaration(
            key = key,
            id = "wms",
            zIndex = 0,
            visible = visible,
            minZoom = null,
            maxZoom = null,
            attributions = emptyList(),
            state = state,
            onError = onError,
            create = { reportError, _ -> create(reportError) },
        )

    private fun rasterRuntime(readTile: suspend (TileRequest) -> ByteArray?): RasterTileLayer =
        RasterTileLayer(
            id = "wms-runtime",
            source =
                object : RasterTileSource {
                    override val projection: Projection = IdentityProjection
                    override val grid: TileGrid = singleTileGrid

                    override fun cacheKey(request: TileRequest): String = request.coordinate.toString()

                    override suspend fun readTile(request: TileRequest): ByteArray? = readTile.invoke(request)
                },
            maxVisibleTiles = 1,
            prefetchMargin = 0,
            overviewZoomOffset = 0,
            maxOverviewTiles = 0,
            overviewPrefetchMargin = 0,
        )

    private suspend fun TestScope.withWMSComposition(
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

        override fun move(
            from: Int,
            to: Int,
            count: Int,
        ) = Unit

        override fun onClear() = Unit

        override fun remove(
            index: Int,
            count: Int,
        ) = Unit
    }

    private companion object {
        val singleTileGrid =
            TileGrid(
                tileSize = 1,
                originX = 0.0,
                originY = 1.0,
                worldWidth = 1.0,
                nTilesX0 = 1,
                nTilesY0 = 1,
            )
        val singleTileMap =
            MapState(
                center = Point(0.5, 0.5),
                zoom = 0.0,
                projection = IdentityProjection,
                viewport = Viewport(width = 1, height = 1),
            )
    }
}
