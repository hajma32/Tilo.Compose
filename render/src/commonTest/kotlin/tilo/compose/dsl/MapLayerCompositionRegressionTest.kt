@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.ControlledComposition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.Layer
import tilo.compose.core.layers.LayerGroup
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.layers.raster.TileRowScheme
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import tilo.compose.core.projection.IdentityProjection
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.TileGrid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Regression coverage for the Compose lifecycle of DSL-created raster layers.
 *
 * These tests intentionally use a real [Recomposer]. A repeated builder call
 * alone cannot detect runtime/cache churn caused by commit and disposal timing.
 */
class MapLayerCompositionRegressionTest {
    /**
     * Verifies that nested managed layers participate in the map's normal ownership lifecycle.
     *
     * Input: a blocking tile layer inside a group, followed by removal of the complete group.
     * Expected: the child request is cancelled and the group leaves the committed layer list.
     */
    @Test
    fun removingGroupFromCompositionRetiresItsManagedChildren() {
        runTest {
            val includeGroup = mutableStateOf(true)
            val requestStarted = CompletableDeferred<Unit>()
            val requestCancelled = CompletableDeferred<Unit>()
            var currentLayers: List<Layer> = emptyList()

            withLayerComposition {
                currentLayers =
                    rememberManagedMapLayers {
                        if (includeGroup.value) {
                            layerGroup(id = "base-maps") {
                                tileStoreLayer(
                                    id = "base",
                                    projection = IdentityProjection,
                                    grid = singleTileGrid,
                                    sourceId = "grouped-blocking-source",
                                    readTile = {
                                        requestStarted.complete(Unit)
                                        try {
                                            awaitCancellation()
                                        } finally {
                                            requestCancelled.complete(Unit)
                                        }
                                    },
                                    scheme = TileRowScheme.XYZ,
                                )
                            }
                        }
                    }
            }.use { composition ->
                val group = currentLayers.single() as LayerGroup
                val request = async { (group.children.single() as TileLayer).loadTiles(singleTileMap) }
                requestStarted.await()

                includeGroup.value = false
                composition.recompose()
                requestCancelled.await()
                request.join()

                assertTrue(request.isCancelled)
                assertTrue(currentLayers.isEmpty())
            }
        }
    }

    /**
     * Verifies that unrelated Compose state changes preserve a managed raster runtime and cache.
     *
     * Input: one loaded tile followed by recomposition triggered by an unrelated integer state.
     * Expected: the runtime remains equal and the tile reader is invoked only for the first load.
     */
    @Test
    fun unrelatedRecompositionKeepsRasterRuntimeAndItsTileCache() {
        runTest {
            val trigger = mutableIntStateOf(0)
            var compositionCount = 0
            var currentLayers: List<Layer> = emptyList()
            var tileReadCount = 0
            val reader: suspend (TileCoordinate) -> ByteArray? = {
                tileReadCount += 1
                byteArrayOf(1)
            }

            withLayerComposition {
                val currentTrigger = trigger.intValue
                compositionCount += if (currentTrigger >= 0) 1 else 1
                currentLayers =
                    rememberManagedMapLayers {
                        tileStoreLayer(
                            id = "base",
                            projection = IdentityProjection,
                            grid = singleTileGrid,
                            readTile = reader,
                            scheme = TileRowScheme.XYZ,
                        )
                    }
            }.use { composition ->
                val firstLayer = currentLayers.single() as TileLayer
                firstLayer.loadTiles(singleTileMap)

                trigger.intValue += 1
                composition.recompose()
                val recomposedLayer = currentLayers.single() as TileLayer
                recomposedLayer.loadTiles(singleTileMap)

                assertEquals(2, compositionCount, "The test must exercise a real recomposition")
                assertEquals(firstLayer, recomposedLayer)
                assertEquals(1, tileReadCount, "An unrelated recomposition must not repeat network/store I/O")
            }
        }
    }

    /**
     * Verifies disposal of a managed layer removed from a committed composition.
     *
     * Input: a layer with a blocked tile request, followed by recomposition without that layer.
     * Expected: the request is cancelled and the committed layer list becomes empty.
     */
    @Test
    fun removingLayerFromCompositionCancelsItsInFlightRequest() {
        runTest {
            val includeLayer = mutableStateOf(true)
            val requestStarted = CompletableDeferred<Unit>()
            val requestCancelled = CompletableDeferred<Unit>()
            var currentLayers: List<Layer> = emptyList()

            withLayerComposition {
                currentLayers =
                    rememberManagedMapLayers {
                        if (includeLayer.value) {
                            tileStoreLayer(
                                id = "base",
                                projection = IdentityProjection,
                                grid = singleTileGrid,
                                sourceId = "blocking-source",
                                readTile = {
                                    requestStarted.complete(Unit)
                                    try {
                                        awaitCancellation()
                                    } finally {
                                        requestCancelled.complete(Unit)
                                    }
                                },
                                scheme = TileRowScheme.XYZ,
                            )
                        }
                    }
            }.use { composition ->
                val request =
                    async {
                        (currentLayers.single() as TileLayer).loadTiles(singleTileMap)
                    }
                requestStarted.await()

                includeLayer.value = false
                composition.recompose()
                requestCancelled.await()
                request.join()

                assertTrue(request.isCancelled, "Retired layers must not keep background I/O alive")
                assertTrue(currentLayers.isEmpty())
            }
        }
    }

    /**
     * Verifies replacement semantics when source identity changes during recomposition.
     *
     * Input: a blocked request for source `first`, then a committed change to source `second`.
     * Expected: a new runtime replaces the old one, cancels its request, and returns byte `2`.
     */
    @Test
    fun changingSourceReplacesRuntimeAndCancelsOldInFlightRequest() {
        runTest {
            val sourceId = mutableStateOf("first")
            val firstRequestStarted = CompletableDeferred<Unit>()
            val firstRequestCancelled = CompletableDeferred<Unit>()
            var currentLayers: List<Layer> = emptyList()

            withLayerComposition {
                val currentSourceId = sourceId.value
                currentLayers =
                    rememberManagedMapLayers {
                        tileStoreLayer(
                            id = "base",
                            projection = IdentityProjection,
                            grid = singleTileGrid,
                            sourceId = currentSourceId,
                            readTile = {
                                if (currentSourceId == "first") {
                                    firstRequestStarted.complete(Unit)
                                    try {
                                        awaitCancellation()
                                    } finally {
                                        firstRequestCancelled.complete(Unit)
                                    }
                                } else {
                                    byteArrayOf(2)
                                }
                            },
                            scheme = TileRowScheme.XYZ,
                        )
                    }
            }.use { composition ->
                val firstLayer = currentLayers.single() as TileLayer
                val firstRequest = async { firstLayer.loadTiles(singleTileMap) }
                firstRequestStarted.await()

                sourceId.value = "second"
                composition.recompose()
                val secondLayer = currentLayers.single() as TileLayer
                firstRequestCancelled.await()
                firstRequest.join()

                assertNotSame(firstLayer, secondLayer)
                assertTrue(firstRequest.isCancelled)
                assertEquals(
                    2,
                    secondLayer
                        .loadTiles(singleTileMap)
                        .single()
                        .bytes
                        ?.single(),
                )
            }
        }
    }

    /**
     * Verifies that presentation-only visibility changes do not invalidate source tile bytes.
     *
     * Input: load a tile, recompose visibility `true → false → true`, then load it again.
     * Expected: visibility follows composition and the tile reader is invoked only once.
     */
    @Test
    fun visibilityRoundTripDoesNotDownloadCachedTileAgain() {
        runTest {
            val visible = mutableStateOf(true)
            var currentLayers: List<Layer> = emptyList()
            var tileReadCount = 0

            withLayerComposition {
                val isVisible = visible.value
                currentLayers =
                    rememberManagedMapLayers {
                        tileStoreLayer(
                            id = "base",
                            projection = IdentityProjection,
                            grid = singleTileGrid,
                            sourceId = "stable-source",
                            visible = isVisible,
                            readTile = {
                                tileReadCount += 1
                                byteArrayOf(1)
                            },
                            scheme = TileRowScheme.XYZ,
                        )
                    }
            }.use { composition ->
                (currentLayers.single() as TileLayer).loadTiles(singleTileMap)

                visible.value = false
                composition.recompose()
                assertEquals(false, currentLayers.single().visible)

                visible.value = true
                composition.recompose()
                (currentLayers.single() as TileLayer).loadTiles(singleTileMap)

                assertEquals(
                    expected = 1,
                    actual = tileReadCount,
                    message = "Presentation-only recompositions must preserve the source tile cache",
                )
            }
        }
    }

    /**
     * Verifies that a new tile-reader closure becomes visible only after recomposition commits.
     *
     * Input: reader version `1` for the left tile, then committed version `2` for the right tile.
     * Expected: the two uncached tile reads return bytes `1` and `2` respectively.
     */
    @Test
    fun committedRecompositionPublishesLatestTileReader() {
        runTest {
            val readerVersion = mutableIntStateOf(1)
            var currentLayers: List<Layer> = emptyList()

            withLayerComposition {
                val currentVersion = readerVersion.intValue
                currentLayers =
                    rememberManagedMapLayers {
                        tileStoreLayer(
                            id = "base",
                            projection = IdentityProjection,
                            grid = twoTileGrid,
                            sourceId = "stable-source",
                            readTile = { byteArrayOf(currentVersion.toByte()) },
                            scheme = TileRowScheme.XYZ,
                        )
                    }
            }.use { composition ->
                val firstTile = (currentLayers.single() as TileLayer).loadTiles(leftTileMap).single()
                assertEquals(1, firstTile.bytes?.single())

                readerVersion.intValue = 2
                composition.recompose()

                val secondTile = (currentLayers.single() as TileLayer).loadTiles(rightTileMap).single()
                assertEquals(2, secondTile.bytes?.single())
            }
        }
    }

    private suspend fun TestScope.withLayerComposition(
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

        val twoTileGrid =
            TileGrid(
                originX = -128.0,
                originY = 128.0,
                worldWidth = 256.0,
                nTilesX0 = 2,
                nTilesY0 = 1,
            )

        val leftTileMap =
            MapState(
                center = Point(-64.0, 64.0),
                zoom = 0.0,
                viewport = Viewport(width = 64, height = 64),
                projection = IdentityProjection,
            )

        val rightTileMap =
            MapState(
                center = Point(64.0, 64.0),
                zoom = 0.0,
                viewport = Viewport(width = 64, height = 64),
                projection = IdentityProjection,
            )
    }
}
