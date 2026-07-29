@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.FeatureLayerStyle
import tilo.compose.core.feature.PointIconStyle
import tilo.compose.core.feature.PointStyle
import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.Attribution
import tilo.compose.core.layers.LayerGroup
import tilo.compose.core.layers.raster.RasterTileBatchSummary
import tilo.compose.core.layers.raster.RasterTileDiagnosticEvent
import tilo.compose.core.layers.raster.RasterTileFailure
import tilo.compose.core.layers.raster.RasterTileFailureKind
import tilo.compose.core.layers.raster.RasterTileLayer
import tilo.compose.core.layers.raster.RasterTileRequestPurpose
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.layers.raster.TileRowScheme
import tilo.compose.core.layers.raster.TileStoreTileSource
import tilo.compose.core.layers.raster.WMSCapabilities
import tilo.compose.core.layers.raster.WMSLayerCapabilities
import tilo.compose.core.layers.vector.FeatureLayer
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.IdentityProjection
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.TileGrid
import tilo.compose.render.PointIconPainterLayer
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class MapLayerBuilderTest {
    @Test
    fun opacityDoesNotChangeExistingDslPositionalArguments() {
        val builder = MapLayerBuilder()

        builder.layerGroup("group", 2, true, 0.5, 2.0, null, emptyList()) {
            featureLayer(
                "features",
                emptyList(),
                3,
                true,
                0.75,
                1.75,
                null,
                immediate(),
                FeatureLayerStyle(),
                null,
                emptyList(),
            )
        }

        val group = builder.build().single() as LayerGroup
        val layer = group.children.single()
        assertEquals(0.5, group.minZoom)
        assertEquals(2.0, group.maxZoom)
        assertEquals(1.0, group.opacity)
        assertEquals(0.75, layer.minZoom)
        assertEquals(1.75, layer.maxZoom)
        assertEquals(1.0, layer.opacity)
    }

    @Test
    fun layerGroupBuildsNestedMixedLayerTree() {
        val builder = MapLayerBuilder()

        builder.layerGroup(
            id = "transport",
            zIndex = 10,
            opacity = 0.5,
            minZoom = 11.0,
            attribution = Attribution("Transport"),
        ) {
            featureLayer(id = "roads", features = emptyList(), zIndex = 0)
            layerGroup(id = "labels", zIndex = 10) {
                featureLayer(id = "road-labels", features = emptyList())
            }
        }

        val group = builder.build().single() as LayerGroup
        assertEquals("transport", group.id)
        assertEquals(10, group.zIndex)
        assertEquals(0.5, group.opacity)
        assertEquals(11.0, group.minZoom)
        assertEquals(listOf("Transport"), group.attributions.map(Attribution::label))
        assertEquals(listOf("roads", "labels"), group.children.map { it.id })
        assertEquals(
            listOf("road-labels"),
            (group.children.last() as LayerGroup).children.map { it.id },
        )
    }

    @Test
    fun featureLayerDslExposesOpacity() {
        val builder = MapLayerBuilder()

        builder.featureLayer(id = "places", features = emptyList()) {
            opacity = 0.35
        }

        assertEquals(0.35, builder.build().single().opacity)
    }

    @Test
    fun nestedManagedRasterLayersShareTheMapStore() {
        val store = RasterLayerStore()
        try {
            val builder = MapLayerBuilder.managed(store)
            builder.layerGroup(id = "base-maps", opacity = 0.5) {
                xyzTileLayer(
                    id = "base",
                    urlTemplate = "https://example.test/{z}/{x}/{y}.png",
                    opacity = 0.4,
                )
            }

            val group = builder.build().single() as LayerGroup

            assertEquals(1, builder.managedRasterKeys.size)
            assertTrue(group.children.single() is TileLayer)
            assertEquals(0.4, group.children.single().opacity)
        } finally {
            store.close()
        }
    }

    @Test
    fun duplicateIdsAreRejectedAcrossGroupBoundaries() {
        val builder = MapLayerBuilder()
        builder.featureLayer(id = "roads", features = emptyList())

        val error =
            assertFailsWith<IllegalArgumentException> {
                builder.layerGroup(id = "transport") {
                    featureLayer(id = "roads", features = emptyList())
                }
            }

        assertContains(error.message.orEmpty(), "Duplicate layer id 'roads'")
    }

    @Test
    fun prebuiltGroupRegistersAllDescendantIds() {
        val builder = MapLayerBuilder()
        builder.layer(
            LayerGroup(
                id = "transport",
                children =
                    listOf(
                        FeatureLayer(id = "roads", features = emptyList()),
                    ),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            builder.featureLayer(id = "roads", features = emptyList())
        }
    }

    @Test
    fun featureLayerRegistersPointIconsById() {
        val painter = ColorPainter(Color.Red)
        val builder = MapLayerBuilder()

        builder.featureLayer(id = "places", features = emptyList()) {
            pointIcon("stop", painter)
        }

        val layer = builder.build().single() as PointIconPainterLayer
        assertEquals(painter, layer.pointIconPainters["stop"])
    }

    @Test
    fun duplicatePointIconIdsAreRejected() {
        val builder = MapLayerBuilder()

        val error =
            assertFailsWith<IllegalArgumentException> {
                builder.featureLayer(id = "places", features = emptyList()) {
                    pointIcon("stop", ColorPainter(Color.Red))
                    pointIcon("stop", ColorPainter(Color.Blue))
                }
            }

        assertContains(error.message.orEmpty(), "Point icon id 'stop' is already registered")
    }

    @Test
    fun unregisteredPointIconReferencesAreRejected() {
        val builder = MapLayerBuilder()
        val features =
            listOf(
                Feature(
                    key = "stop",
                    geometry = Point(0.0, 0.0),
                    style = PointStyle(icon = PointIconStyle("missing")),
                ),
            )

        val error =
            assertFailsWith<IllegalArgumentException> {
                builder.featureLayer(id = "places", features = features) {}
            }

        assertContains(error.message.orEmpty(), "unregistered point icon IDs: missing")
        assertContains(error.message.orEmpty(), "pointIcon(id, painter)")
    }

    @Test
    fun publicWMSBuilderLoadsCapabilitiesAndResolvesMultipleLayers() =
        runTest {
            val capabilitiesUrl = "https://example.test/wms"
            var loadedUrl: String? = null
            val capabilities =
                WMSCapabilities(
                    version = "1.3.0",
                    getMapUrl = "https://example.test/get-map",
                    formats = listOf("image/png"),
                    layers =
                        listOf(
                            wmsCapabilitiesLayer("first", minX = 0.0, maxX = 10.0),
                            wmsCapabilitiesLayer("second", minX = 10.0, maxX = 30.0),
                        ),
                )
            val builder =
                MapLayerBuilder.managed(RasterLayerStore()) { url ->
                    loadedUrl = url
                    capabilities
                }
            builder.wmsTileLayer(
                id = "wms",
                capabilitiesUrl = capabilitiesUrl,
                layerNames = listOf("first", "second"),
                projection = IdentityProjection,
                tileSize = 512,
            )

            val declaration = builder.managedWMSDeclarations.single()
            val runtime = declaration.create({}, {})
            try {
                val layer = builder.build(mapOf(declaration.key to runtime)).single() as TileLayer
                assertEquals(capabilitiesUrl, loadedUrl)
                assertEquals("wms", layer.id)
                assertEquals(512, layer.grid.tileSize)
                assertEquals(30.0, layer.grid.worldWidth)
            } finally {
                runtime.close()
            }
        }

    /**
     * Verifies that the advanced pre-built-layer path borrows its raster runtime.
     *
     * Input: a caller-owned layer passed to a managed builder, followed by store shutdown.
     * Expected: the layer remains usable until the caller closes it.
     */
    @Test
    fun prebuiltRasterLayerRemainsCallerOwned() =
        runTest {
            val grid = TileGrid()
            val layer =
                RasterTileLayer(
                    id = "borrowed",
                    source =
                        TileStoreTileSource(
                            projection = IdentityProjection,
                            grid = grid,
                            scheme = TileRowScheme.XYZ,
                            readTile = { byteArrayOf(1) },
                        ),
                )
            val store = RasterLayerStore()
            val builder = MapLayerBuilder.managed(store)
            builder.rasterLayer(layer)
            store.retain(builder.managedRasterKeys)
            store.close()

            try {
                val map =
                    MapState(
                        center = Point(0.0, 0.0),
                        zoom = 0.0,
                        viewport = Viewport(width = 256, height = 256),
                        projection = IdentityProjection,
                    )
                val tiles = layer.loadTiles(map)
                assertTrue(tiles.isNotEmpty())
                assertTrue(tiles.all { tile -> tile.bytes?.single() == 1.toByte() })
            } finally {
                layer.close()
            }
        }

    /**
     * Verifies that tile-reader callback updates become visible only at the commit boundary.
     *
     * Input: initial reader `0`, committed reader `1`, and an abandoned candidate reader `2`.
     * Expected: values progress `0 → 1`, stay `1` before commit, and become `2` after `retain`.
     */
    @Test
    fun tileReaderChangesArePublishedOnlyOnCommit() =
        runTest {
            val store = RasterLayerStore()
            val key = ManagedRasterLayerKey(layerId = "base", configuration = "stable-source")
            val initialReader: suspend (TileCoordinate) -> ByteArray? = { byteArrayOf(0) }
            val firstReader: suspend (TileCoordinate) -> ByteArray? = { byteArrayOf(1) }
            val candidateReader: suspend (TileCoordinate) -> ByteArray? = { byteArrayOf(2) }
            var publishedReader = initialReader

            store.getOrCreate(key) {
                StoredRasterLayer(
                    layer =
                        RasterTileLayer(
                            id = "base",
                            source =
                                TileStoreTileSource(
                                    projection = IdentityProjection,
                                    grid =
                                        TileGrid(
                                            originX = -128.0,
                                            originY = 128.0,
                                            worldWidth = 256.0,
                                            nTilesX0 = 1,
                                            nTilesY0 = 1,
                                        ),
                                    scheme = TileRowScheme.XYZ,
                                    sourceId = "stable-source",
                                    readTile = { null },
                                ),
                        ),
                    update = { update ->
                        if (update is RasterLayerUpdate.TileStore) {
                            publishedReader = update.readTile
                        }
                    },
                )
            }

            assertEquals(0, publishedReader(TileCoordinate(x = 0, y = 0, z = 0))?.single())

            store.retain(
                activeKeys = setOf(key),
                updates = mapOf(key to RasterLayerUpdate.TileStore(firstReader, state = null, onError = null)),
            )
            assertEquals(1, publishedReader(TileCoordinate(x = 0, y = 0, z = 0))?.single())

            val abandonedUpdates =
                mapOf(key to RasterLayerUpdate.TileStore(candidateReader, state = null, onError = null))
            assertEquals(
                expected = 1,
                actual = publishedReader(TileCoordinate(x = 0, y = 0, z = 0))?.single(),
                message = "An abandoned composition must not publish its tile callback",
            )

            store.retain(activeKeys = setOf(key), updates = abandonedUpdates)
            assertEquals(2, publishedReader(TileCoordinate(x = 0, y = 0, z = 0))?.single())
        }

    /**
     * Verifies callback updates without replacing the managed raster runtime.
     *
     * Input: the same failing tile store is recomposed with a different error callback.
     * Expected: the second failure reaches only the latest callback.
     */
    @Test
    fun managedRasterUsesLatestErrorCallback() =
        runTest {
            val store = RasterLayerStore()
            val grid = TileGrid()
            val expectedError = IllegalStateException("offline")
            val readTile: suspend (TileCoordinate) -> ByteArray? = { throw expectedError }
            val state = RasterLayerState()
            var firstReports = 0
            var secondReports = 0

            val firstBuilder = MapLayerBuilder.managed(store)
            firstBuilder.tileStoreLayer(
                id = "base",
                projection = IdentityProjection,
                grid = grid,
                readTile = readTile,
                scheme = TileRowScheme.XYZ,
                maxVisibleTiles = 1,
                prefetchMargin = 0,
                state = state,
                onError = { firstReports += 1 },
            )
            val firstLayer = firstBuilder.build().single() as TileLayer
            store.retain(firstBuilder.managedRasterKeys, firstBuilder.managedRasterUpdates)
            assertEquals(RasterLayerStatus.Ready, state.status)

            val map =
                MapState(
                    center = Point(0.0, 0.0),
                    zoom = 0.0,
                    viewport = Viewport(width = 256, height = 256),
                    projection = IdentityProjection,
                )
            firstLayer.loadTiles(map)
            assertTrue(firstReports > 0)
            assertEquals(RasterLayerStatus.Ready, state.status)
            assertEquals(expectedError, state.lastTileError)
            val firstReportCount = firstReports

            val secondBuilder = MapLayerBuilder.managed(store)
            state.retry()
            secondBuilder.tileStoreLayer(
                id = "base",
                projection = IdentityProjection,
                grid = grid,
                readTile = readTile,
                scheme = TileRowScheme.XYZ,
                maxVisibleTiles = 1,
                prefetchMargin = 0,
                state = state,
                onError = { secondReports += 1 },
            )
            val secondLayer = secondBuilder.build().single() as TileLayer
            store.retain(secondBuilder.managedRasterKeys, secondBuilder.managedRasterUpdates)
            secondLayer.loadTiles(map)

            assertEquals(firstReportCount, firstReports)
            assertTrue(secondReports > 0)
            store.close()
            assertEquals(RasterLayerStatus.Idle, state.status)
        }

    @Test
    fun structuredRasterDiagnosticsDriveOfflineRecoveryAndLocalEmptyState() =
        runTest {
            val coordinate = TileCoordinate(z = 1, x = 2, y = 3)
            val networkError = IllegalStateException("offline")
            val networkState = RasterLayerState()
            val networkDiagnostics = MutableRasterLayerDiagnostics(networkState, onError = null)
            networkDiagnostics.ready()
            networkDiagnostics.onDiagnostic(
                RasterTileDiagnosticEvent.Failure(
                    RasterTileFailure(
                        kind = RasterTileFailureKind.NetworkUnavailable,
                        coordinate = coordinate,
                        message = "offline",
                        cause = networkError,
                    ),
                ),
            )
            networkDiagnostics.onDiagnostic(
                RasterTileDiagnosticEvent.BatchCompleted(
                    RasterTileBatchSummary(
                        requested = 1,
                        succeeded = 0,
                        missing = 0,
                        failed = 1,
                        networkFailures = 1,
                    ),
                ),
            )

            assertEquals(RasterLayerAvailability.Offline, networkState.availability)
            assertEquals(RasterTileFailureKind.NetworkUnavailable, networkState.diagnostics.lastFailure?.kind)
            assertEquals(1, networkState.diagnostics.failed)
            assertEquals(networkError, networkState.lastTileError)

            networkState.clearTileError()

            networkDiagnostics.onDiagnostic(
                RasterTileDiagnosticEvent.BatchCompleted(
                    RasterTileBatchSummary(requested = 1, succeeded = 1, missing = 0, failed = 0),
                ),
            )
            assertEquals(RasterLayerAvailability.Available, networkState.availability)
            assertEquals(null, networkState.lastTileError)

            networkDiagnostics.onDiagnostic(
                RasterTileDiagnosticEvent.BatchCompleted(
                    RasterTileBatchSummary(
                        purpose = RasterTileRequestPurpose.Prefetch,
                        requested = 1,
                        succeeded = 0,
                        missing = 0,
                        failed = 1,
                        networkFailures = 1,
                    ),
                ),
            )
            assertEquals(RasterLayerAvailability.Available, networkState.availability)

            val localState = RasterLayerState()
            val localDiagnostics =
                MutableRasterLayerDiagnostics(localState, onError = null, localSource = true)
            localDiagnostics.ready()
            localDiagnostics.onDiagnostic(
                RasterTileDiagnosticEvent.BatchCompleted(
                    RasterTileBatchSummary(requested = 1, succeeded = 1, missing = 0, failed = 0),
                ),
            )
            localDiagnostics.onDiagnostic(
                RasterTileDiagnosticEvent.BatchCompleted(
                    RasterTileBatchSummary(requested = 2, succeeded = 0, missing = 2, failed = 0),
                ),
            )
            assertEquals(RasterLayerAvailability.Empty, localState.availability)
        }

    @Test
    fun concurrentRasterBatchesDoNotLoseDiagnosticCounts() =
        runTest {
            val state = RasterLayerState()
            val diagnostics = MutableRasterLayerDiagnostics(state, onError = null)
            diagnostics.ready()

            coroutineScope {
                repeat(100) {
                    launch(Dispatchers.Default) {
                        diagnostics.onDiagnostic(
                            RasterTileDiagnosticEvent.BatchCompleted(
                                RasterTileBatchSummary(
                                    purpose = RasterTileRequestPurpose.Prefetch,
                                    requested = 1,
                                    succeeded = 1,
                                    missing = 0,
                                    failed = 0,
                                ),
                            ),
                        )
                    }
                }
            }

            assertEquals(100, state.diagnostics.requested)
            assertEquals(100, state.diagnostics.succeeded)
            assertEquals(RasterLayerAvailability.Unknown, state.availability)
        }

    @Test
    fun xyzRetryReplacesRuntimeAndReturnsStateToReady() {
        val store = RasterLayerStore()
        val state = RasterLayerState()
        val firstBuilder = MapLayerBuilder.managed(store)
        firstBuilder.xyzTileLayer(
            id = "base",
            urlTemplate = "https://example.test/{z}/{x}/{y}.png",
            state = state,
        )
        val firstLayer = firstBuilder.build().single()
        store.retain(firstBuilder.managedRasterKeys, firstBuilder.managedRasterUpdates)
        assertEquals(RasterLayerStatus.Ready, state.status)

        state.retry()
        val secondBuilder = MapLayerBuilder.managed(store)
        secondBuilder.xyzTileLayer(
            id = "base",
            urlTemplate = "https://example.test/{z}/{x}/{y}.png",
            state = state,
        )
        val secondLayer = secondBuilder.build().single()
        store.retain(secondBuilder.managedRasterKeys, secondBuilder.managedRasterUpdates)

        assertNotSame(firstLayer, secondLayer)
        assertEquals(RasterLayerStatus.Ready, state.status)
        store.close()
        assertEquals(RasterLayerStatus.Idle, state.status)
    }

    @Test
    fun xyzSourceReplacementClearsPreviousTileError() {
        val store = RasterLayerStore()
        val state = RasterLayerState()
        val previousError = IllegalStateException("old source failed")
        val firstBuilder = MapLayerBuilder.managed(store)
        firstBuilder.xyzTileLayer(
            id = "base",
            urlTemplate = "https://first.test/{z}/{x}/{y}.png",
            state = state,
        )
        store.retain(firstBuilder.managedRasterKeys, firstBuilder.managedRasterUpdates)
        state.tileFailed(previousError)
        assertEquals(previousError, state.lastTileError)

        val replacementBuilder = MapLayerBuilder.managed(store)
        replacementBuilder.xyzTileLayer(
            id = "base",
            urlTemplate = "https://second.test/{z}/{x}/{y}.png",
            state = state,
        )
        store.retain(replacementBuilder.managedRasterKeys, replacementBuilder.managedRasterUpdates)

        assertEquals(RasterLayerStatus.Ready, state.status)
        assertEquals(null, state.lastTileError)
        store.close()
    }

    @Test
    fun retiredDiagnosticsIgnoreLateErrorsAndCallbacks() {
        val state = RasterLayerState()
        val firstError = IllegalStateException("first")
        val lateError = IllegalStateException("late")
        var reports = 0
        val diagnostics = MutableRasterLayerDiagnostics(state) { reports += 1 }
        diagnostics.ready()
        diagnostics.tileFailed(firstError)

        diagnostics.retire()
        diagnostics.tileFailed(lateError)

        assertEquals(RasterLayerStatus.Idle, state.status)
        assertEquals(firstError, state.lastTileError)
        assertEquals(1, reports)
    }

    /**
     * Verifies reuse of a managed raster runtime and its byte cache across equivalent builders.
     *
     * Input: two managed builders with identical tile-store configuration and reader identity.
     * Expected: equivalent presented layers and one underlying tile read across both loads.
     */
    @Test
    fun managedDslReusesUnchangedRasterRuntimeAndCache() =
        runTest {
            val store = RasterLayerStore()
            val grid =
                TileGrid(
                    originX = -128.0,
                    originY = 128.0,
                    worldWidth = 256.0,
                    nTilesX0 = 1,
                    nTilesY0 = 1,
                )
            var readCount = 0
            val readTile: suspend (TileCoordinate) -> ByteArray? = {
                readCount += 1
                byteArrayOf(1)
            }
            val firstBuilder = MapLayerBuilder.managed(store)
            firstBuilder.tileStoreLayer(
                id = "base",
                projection = IdentityProjection,
                grid = grid,
                readTile = readTile,
                scheme = TileRowScheme.XYZ,
            )
            val firstLayer = firstBuilder.build().single() as TileLayer
            store.retain(firstBuilder.managedRasterKeys)

            val map =
                MapState(
                    center = Point(0.0, 0.0),
                    zoom = 0.0,
                    viewport = Viewport(width = 256, height = 256),
                    projection = IdentityProjection,
                )
            firstLayer.loadTiles(map)

            val secondBuilder = MapLayerBuilder.managed(store)
            secondBuilder.tileStoreLayer(
                id = "base",
                projection = IdentityProjection,
                grid = grid,
                readTile = readTile,
                scheme = TileRowScheme.XYZ,
            )
            val secondLayer = secondBuilder.build().single() as TileLayer
            store.retain(secondBuilder.managedRasterKeys)
            secondLayer.loadTiles(map)

            assertEquals(firstLayer, secondLayer)
            assertEquals(1, readCount)
        }

    /**
     * Verifies replacement of a managed raster runtime when source configuration changes.
     *
     * Input: two XYZ builders sharing layer ID `base` but using different URL templates.
     * Expected: the second build returns a different runtime instance.
     */
    @Test
    fun managedDslReplacesRasterRuntimeWhenSourceChanges() {
        val store = RasterLayerStore()
        val firstBuilder = MapLayerBuilder.managed(store)
        firstBuilder.xyzTileLayer(id = "base", urlTemplate = "https://a/{z}/{x}/{y}.png")
        val firstLayer = firstBuilder.build().single()
        store.retain(firstBuilder.managedRasterKeys)

        val secondBuilder = MapLayerBuilder.managed(store)
        secondBuilder.xyzTileLayer(id = "base", urlTemplate = "https://b/{z}/{x}/{y}.png")
        val secondLayer = secondBuilder.build().single()
        store.retain(secondBuilder.managedRasterKeys)

        assertNotSame(firstLayer, secondLayer)
    }

    /**
     * Verifies that the zero-argument OSM preset creates a correctly configured XYZ layer.
     *
     * Input: a fresh layer builder and `osmLayer()` with all default arguments.
     * Expected: one Web Mercator layer named `osm` with the required OpenStreetMap attribution.
     */
    @Test
    fun osmLayerProvidesStandardWebMercatorSetup() {
        val builder = MapLayerBuilder()
        builder.osmLayer()

        val layer = builder.build().single() as RasterTileLayer
        try {
            assertEquals("osm", layer.id)
            assertEquals(Epsg3857Projection, layer.projection)
            assertEquals(
                expected =
                    listOf(
                        Attribution(
                            label = "© OpenStreetMap contributors",
                            url = "https://www.openstreetmap.org/copyright",
                        ),
                    ),
                actual = layer.attributions,
            )
        } finally {
            layer.close()
        }
    }

    /**
     * Verifies that every high-level tile declaration defaults to visible requests only.
     *
     * Input: XYZ, tile-store, and WMS declarations without speculative-loading options.
     * Expected: nearby and overview prefetch margins and the overview offset are all zero.
     */
    @Test
    fun tileLayerDslDefaultsSpeculativeLoadingToDisabled() {
        val store = RasterLayerStore()
        try {
            val xyzBuilder = MapLayerBuilder.managed(store)
            xyzBuilder.xyzTileLayer(id = "xyz", urlTemplate = "https://example.test/{z}/{x}/{y}.png")
            val xyz = xyzBuilder.managedRasterKeys.single().configuration as XyzRasterConfiguration

            val tileStoreBuilder = MapLayerBuilder.managed(store)
            tileStoreBuilder.tileStoreLayer(
                id = "store",
                projection = IdentityProjection,
                grid = TileGrid(),
                readTile = { byteArrayOf(1) },
            )
            val tileStore =
                tileStoreBuilder.managedRasterKeys.single().configuration as TileStoreRasterConfiguration

            val wmsBuilder = MapLayerBuilder.managed(store)
            wmsBuilder.wmsTileLayer(
                id = "wms",
                capabilitiesUrl = "https://example.test/wms",
                layerName = "base",
                projection = IdentityProjection,
            )
            val wms =
                wmsBuilder.managedWMSDeclarations
                    .single()
                    .key.configuration as WMSRasterConfiguration

            listOf(
                Triple(xyz.prefetchMargin, xyz.overviewZoomOffset, xyz.overviewPrefetchMargin),
                Triple(tileStore.prefetchMargin, tileStore.overviewZoomOffset, tileStore.overviewPrefetchMargin),
                Triple(wms.prefetchMargin, wms.overviewZoomOffset, wms.overviewPrefetchMargin),
            ).forEach { defaults ->
                assertEquals(Triple(0, 0, 0), defaults)
            }
        } finally {
            store.close()
        }
    }

    /**
     * Verifies that the OSM preset pins the public-service-safe loading policy.
     *
     * Input: a managed `osmLayer()` declaration.
     * Expected: all speculative loading limits are explicitly disabled in its configuration.
     */
    @Test
    fun osmLayerExplicitlyDisablesSpeculativeLoading() {
        val store = RasterLayerStore()
        try {
            val builder = MapLayerBuilder.managed(store)
            builder.osmLayer()

            val configuration =
                builder.managedRasterKeys.single().configuration as XyzRasterConfiguration

            assertEquals(0, configuration.prefetchMargin)
            assertEquals(0, configuration.overviewZoomOffset)
            assertEquals(0, configuration.maxOverviewTiles)
            assertEquals(0, configuration.overviewPrefetchMargin)
        } finally {
            store.close()
        }
    }

    /**
     * Verifies global layer-ID uniqueness across different DSL layer types.
     *
     * Input: an XYZ layer and a feature layer both named `base`.
     * Expected: the second registration throws an error mentioning the duplicate ID.
     */
    @Test
    fun duplicateLayerIdsAreRejectedBeforeRendering() {
        val builder = MapLayerBuilder()
        builder.xyzTileLayer(id = "base", urlTemplate = "https://a/{z}/{x}/{y}.png")

        val error =
            assertFailsWith<IllegalArgumentException> {
                builder.featureLayer(id = "base", features = emptyList())
            }

        assertContains(error.message.orEmpty(), "Duplicate layer id 'base'")
    }

    /**
     * Verifies layer-ID uniqueness within one DSL layer type.
     *
     * Input: two XYZ layers named `base` with different URL templates.
     * Expected: the second registration throws `IllegalArgumentException`.
     */
    @Test
    fun duplicateIdsAreRejectedWithinTheSameLayerType() {
        val builder = MapLayerBuilder()
        builder.xyzTileLayer(id = "base", urlTemplate = "https://a/{z}/{x}/{y}.png")

        assertFailsWith<IllegalArgumentException> {
            builder.xyzTileLayer(id = "base", urlTemplate = "https://b/{z}/{x}/{y}.png")
        }
    }

    private fun wmsCapabilitiesLayer(
        name: String,
        minX: Double,
        maxX: Double,
    ): WMSLayerCapabilities =
        WMSLayerCapabilities(
            name = name,
            crs = setOf(IdentityProjection.id),
            boundingBoxes =
                mapOf(
                    IdentityProjection.id to
                        BoundingBox.fromExtents(
                            minX = minX,
                            maxX = maxX,
                            minY = 0.0,
                            maxY = 10.0,
                        ),
                ),
        )
}
