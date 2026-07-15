@file:OptIn(ExperimentalTiloApi::class)

package tilo.compose.dsl

import kotlinx.coroutines.test.runTest
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.raster.RasterTileLayer
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.layers.raster.TileRowScheme
import tilo.compose.core.layers.raster.TileStoreTileSource
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import tilo.compose.core.projection.IdentityProjection
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.TileGrid
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame

class MapLayerBuilderTest {
    /**
     * Verifies that tile-reader callback updates become visible only at the commit boundary.
     *
     * Input: initial reader `0`, committed reader `1`, and an abandoned candidate reader `2`.
     * Expected: values progress `0 → 1`, stay `1` before commit, and become `2` after `retain`.
     */
    @Test
    fun tileReaderChangesArePublishedOnlyOnCommit() = runTest {
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
                    if (update is RasterLayerUpdate.TileReader) {
                        publishedReader = update.readTile
                    }
                },
            )
        }

        assertEquals(0, publishedReader(TileCoordinate(x = 0, y = 0, z = 0))?.single())

        store.retain(
            activeKeys = setOf(key),
            updates = mapOf(key to RasterLayerUpdate.TileReader(firstReader)),
        )
        assertEquals(1, publishedReader(TileCoordinate(x = 0, y = 0, z = 0))?.single())

        val abandonedUpdates = mapOf(key to RasterLayerUpdate.TileReader(candidateReader))
        assertEquals(
            expected = 1,
            actual = publishedReader(TileCoordinate(x = 0, y = 0, z = 0))?.single(),
            message = "An abandoned composition must not publish its tile callback",
        )

        store.retain(activeKeys = setOf(key), updates = abandonedUpdates)
        assertEquals(2, publishedReader(TileCoordinate(x = 0, y = 0, z = 0))?.single())
    }

    /**
     * Verifies reuse of a managed raster runtime and its byte cache across equivalent builders.
     *
     * Input: two managed builders with identical tile-store configuration and reader identity.
     * Expected: equivalent presented layers and one underlying tile read across both loads.
     */
    @Test
    fun managedDslReusesUnchangedRasterRuntimeAndCache() = runTest {
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
     * Verifies global layer-ID uniqueness across different DSL layer types.
     *
     * Input: an XYZ layer and a feature layer both named `base`.
     * Expected: the second registration throws an error mentioning the duplicate ID.
     */
    @Test
    fun duplicateLayerIdsAreRejectedBeforeRendering() {
        val builder = MapLayerBuilder()
        builder.xyzTileLayer(id = "base", urlTemplate = "https://a/{z}/{x}/{y}.png")

        val error = assertFailsWith<IllegalArgumentException> {
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
