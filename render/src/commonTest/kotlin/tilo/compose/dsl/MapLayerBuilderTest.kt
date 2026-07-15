@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

import kotlinx.coroutines.test.runTest
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.Attribution
import tilo.compose.core.layers.raster.RasterTileLayer
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.layers.raster.TileRowScheme
import tilo.compose.core.layers.raster.TileStoreTileSource
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.IdentityProjection
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.TileGrid
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class MapLayerBuilderTest {
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
                updates = mapOf(key to RasterLayerUpdate.TileStore(firstReader, onError = null)),
            )
            assertEquals(1, publishedReader(TileCoordinate(x = 0, y = 0, z = 0))?.single())

            val abandonedUpdates = mapOf(key to RasterLayerUpdate.TileStore(candidateReader, onError = null))
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
                onError = { firstReports += 1 },
            )
            val firstLayer = firstBuilder.build().single() as TileLayer
            store.retain(firstBuilder.managedRasterKeys, firstBuilder.managedRasterUpdates)

            val map =
                MapState(
                    center = Point(0.0, 0.0),
                    zoom = 0.0,
                    viewport = Viewport(width = 256, height = 256),
                    projection = IdentityProjection,
                )
            firstLayer.loadTiles(map)
            assertTrue(firstReports > 0)
            val firstReportCount = firstReports

            val secondBuilder = MapLayerBuilder.managed(store)
            secondBuilder.tileStoreLayer(
                id = "base",
                projection = IdentityProjection,
                grid = grid,
                readTile = readTile,
                scheme = TileRowScheme.XYZ,
                maxVisibleTiles = 1,
                prefetchMargin = 0,
                onError = { secondReports += 1 },
            )
            val secondLayer = secondBuilder.build().single() as TileLayer
            store.retain(secondBuilder.managedRasterKeys, secondBuilder.managedRasterUpdates)
            secondLayer.loadTiles(map)

            assertEquals(firstReportCount, firstReports)
            assertTrue(secondReports > 0)
            store.close()
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
}
