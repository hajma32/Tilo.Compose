@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.ControlledComposition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import tilo.compose.core.geometry.Point
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
import kotlin.test.assertTrue

/** Regression coverage for ownership of WMS raster runtimes held by Compose state. */
class WMSLayerCompositionRegressionTest {
    /**
     * Verifies that presentation-only recomposition preserves the WMS runtime and tile cache.
     *
     * Input: one loaded tile, followed by visibility and unrelated-state recompositions.
     * Expected: presentation changes, while the source is read exactly once for the cached tile.
     */
    @Test
    fun presentationRecompositionKeepsWMSRuntimeAndItsTileCache() {
        runTest {
            val visible = mutableStateOf(true)
            val unrelated = mutableIntStateOf(0)
            var currentLayer: TileLayer? = null
            var tileReadCount = 0
            val runtime =
                rasterRuntime {
                    tileReadCount += 1
                    byteArrayOf(1)
                }

            withWMSComposition {
                val state = rememberWMSLayerRuntimeState()
                val currentVisible = visible.value
                val currentUnrelated = unrelated.intValue
                SideEffect {
                    state.updatePresentation(
                        id = "wms",
                        zIndex = currentUnrelated,
                        visible = currentVisible,
                        minZoom = null,
                        maxZoom = null,
                        attributions = emptyList(),
                    )
                    state.replaceRuntime(runtime)
                    currentLayer = state.layer
                }
            }.use { composition ->
                val firstLayer = requireNotNull(currentLayer)
                firstLayer.loadTiles(singleTileMap)

                visible.value = false
                composition.recompose()
                val hiddenLayer = requireNotNull(currentLayer)
                assertNotSame(firstLayer, hiddenLayer)
                assertEquals(false, hiddenLayer.visible)

                unrelated.intValue += 1
                composition.recompose()
                requireNotNull(currentLayer).loadTiles(singleTileMap)

                assertEquals(1, tileReadCount, "Presentation changes must not repeat WMS network I/O")
            }
        }
    }

    /**
     * Verifies that replacing the WMS source retires its previous runtime immediately.
     *
     * Input: a blocked request on runtime `first`, then a committed switch to runtime `second`.
     * Expected: the first request is cancelled and the replacement returns byte `2`.
     */
    @Test
    fun replacingWMSRuntimeCancelsOldInFlightRequest() {
        runTest {
            val requestStarted = CompletableDeferred<Unit>()
            val requestCancelled = CompletableDeferred<Unit>()
            val firstRuntime =
                rasterRuntime {
                    requestStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        requestCancelled.complete(Unit)
                    }
                }
            val secondRuntime = rasterRuntime { byteArrayOf(2) }
            val selectedRuntime = mutableStateOf(firstRuntime)
            var currentLayer: TileLayer? = null

            withWMSComposition {
                val state = rememberWMSLayerRuntimeState()
                val runtime = selectedRuntime.value
                SideEffect {
                    state.updatePresentation("wms", 0, true, null, null, emptyList())
                    state.replaceRuntime(runtime)
                    currentLayer = state.layer
                }
            }.use { composition ->
                val firstRequest = async { requireNotNull(currentLayer).loadTiles(singleTileMap) }
                requestStarted.await()

                selectedRuntime.value = secondRuntime
                composition.recompose()
                requestCancelled.await()
                firstRequest.join()

                assertTrue(firstRequest.isCancelled)
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
    }

    /**
     * Verifies that disposing remembered WMS state closes its active raster runtime.
     *
     * Input: a composition containing a WMS state with one blocked tile request, then disposal.
     * Expected: disposal cancels the request so no network work survives the composition.
     */
    @Test
    fun disposingWMSCompositionCancelsCurrentInFlightRequest() {
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
                    val state = rememberWMSLayerRuntimeState()
                    SideEffect {
                        state.updatePresentation("wms", 0, true, null, null, emptyList())
                        state.replaceRuntime(runtime)
                        currentLayer = state.layer
                    }
                }

            val request = async { requireNotNull(currentLayer).loadTiles(singleTileMap) }
            requestStarted.await()
            composition.close()
            requestCancelled.await()
            request.join()

            assertTrue(request.isCancelled, "Disposed WMS state must not retain background I/O")
        }
    }

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

    private companion object {
        val singleTileGrid =
            TileGrid(
                originX = -128.0,
                originY = 128.0,
                worldWidth = 256.0,
                nTilesX0 = 1,
                nTilesY0 = 1,
            )

        val singleTileMap =
            MapState(
                center = Point(0.0, 0.0),
                zoom = 0.0,
                viewport = Viewport(width = 256, height = 256),
                projection = IdentityProjection,
            )
    }
}
