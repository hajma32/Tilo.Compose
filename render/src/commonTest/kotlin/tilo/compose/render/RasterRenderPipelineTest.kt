package tilo.compose.render

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.map.MapState
import tilo.compose.core.projection.IdentityProjection
import tilo.compose.core.tile.Tile
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.TileGrid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class RasterRenderPipelineTest {
    /**
     * Verifies that missing or corrupt images do not discard successful tiles in the same frame.
     *
     * Input: one valid tile, one tile without bytes, and one decoder-throwing tile.
     * Expected: all tile positions remain, with an image only for the valid tile.
     */
    @Test
    fun partialDecodeFailureDoesNotDropSuccessfulTiles() =
        runTest {
            val goodImage = TestImageBitmap()
            val tiles =
                listOf(
                    testTile(x = 1, bytes = byteArrayOf(1)),
                    testTile(x = 2, bytes = null),
                    testTile(x = 3, bytes = byteArrayOf(3)),
                )
            val layer = TestTileLayer(load = { tiles })
            val pipeline = RasterRenderPipeline(StandardTestDispatcher(testScheduler))
            var decodeFailure: Triple<String, TileCoordinate, Throwable>? = null

            val frame =
                pipeline.buildVisibleFrame(
                    tileLayers = listOf(layer),
                    map = testMap(),
                    onDecodeFailure = { layerId, coordinate, error ->
                        decodeFailure = Triple(layerId, coordinate, error)
                    },
                    tileDecoder = { bytes ->
                        if (bytes.single() == 3.toByte()) error("corrupt image")
                        goodImage
                    },
                )

            assertEquals(tiles, frame.tilesByLayer.getValue(layer.id))
            val images = frame.decodedImagesByLayer.getValue(layer.id)
            assertSame(goodImage, images[0])
            assertNull(images[1])
            assertNull(images[2])
            assertEquals(layer.id, decodeFailure?.first)
            assertEquals(tiles[2].coordinate, decodeFailure?.second)
            assertEquals("corrupt image", decodeFailure?.third?.message)
        }

    /**
     * Verifies that pipeline decode results, including `null`, are authoritative for the canvas.
     *
     * Input: two downloaded tiles where one decodes successfully and one returns `null`.
     * Expected: two decoder calls total; canvas image resolution performs no second decode.
     */
    @Test
    fun decoderRunsOncePerDownloadedTileAndCanvasUsesPipelineResult() =
        runTest {
            var decodeCount = 0
            var decodeFailureCount = 0
            val tiles = listOf(testTile(1), testTile(2))
            val layer = TestTileLayer(load = { tiles })
            val pipeline = RasterRenderPipeline(StandardTestDispatcher(testScheduler))
            val frame =
                pipeline.buildVisibleFrame(
                    tileLayers = listOf(layer),
                    map = testMap(),
                    onDecodeFailure = { _, _, _ -> decodeFailureCount += 1 },
                    tileDecoder = {
                        decodeCount += 1
                        if (it.single() == 1.toByte()) TestImageBitmap() else null
                    },
                )

            val resolved =
                resolveTileImages(
                    tiles = tiles,
                    tileDecoder = {
                        decodeCount += 1
                        TestImageBitmap()
                    },
                    decodedImages = frame.decodedImagesByLayer.getValue(layer.id),
                )

            assertEquals(2, decodeCount)
            assertEquals(1, decodeFailureCount)
            assertEquals(2, resolved.size)
            assertNull(resolved[1].second)
        }

    @Test
    fun decodeDiagnosticCallbackFailureIsIsolated() =
        runTest {
            var callbackCount = 0
            val layer = TestTileLayer(load = { listOf(testTile(1)) })

            val frame =
                RasterRenderPipeline(StandardTestDispatcher(testScheduler)).buildVisibleFrame(
                    tileLayers = listOf(layer),
                    map = testMap(),
                    onDecodeFailure = { _, _, _ ->
                        callbackCount += 1
                        error("observer failed")
                    },
                    tileDecoder = { null },
                )

            assertEquals(1, callbackCount)
            assertNull(frame.decodedImagesByLayer.getValue(layer.id).single())
        }

    /**
     * Verifies positional alignment between tiles and their decoded-image list.
     *
     * Input: valid, missing, and valid tile bytes at indexes zero through two.
     * Expected: the image list has `[first, null, third]` at the matching indexes.
     */
    @Test
    fun decodedImagesRemainIndexAlignedWithTiles() =
        runTest {
            val first = TestImageBitmap(width = 1)
            val third = TestImageBitmap(width = 3)
            val tiles =
                listOf(
                    testTile(x = 1, bytes = byteArrayOf(1)),
                    testTile(x = 2, bytes = null),
                    testTile(x = 3, bytes = byteArrayOf(3)),
                )
            val frame =
                RasterRenderPipeline(StandardTestDispatcher(testScheduler)).buildVisibleFrame(
                    tileLayers = listOf(TestTileLayer(load = { tiles })),
                    map = testMap(),
                    tileDecoder = { bytes -> if (bytes.single() == 1.toByte()) first else third },
                )

            val images = frame.decodedImagesByLayer.getValue("tiles")
            assertEquals(3, images.size)
            assertSame(first, images[0])
            assertNull(images[1])
            assertSame(third, images[2])
        }

    /**
     * Verifies that prefetch only warms byte caches and never performs image decoding.
     *
     * Input: one tile layer with a counting prefetch callback.
     * Expected: prefetch runs exactly once without requiring a decoder.
     */
    @Test
    fun prefetchNeverDecodesTiles() =
        runTest {
            var prefetchCount = 0
            val layer = TestTileLayer(prefetch = { prefetchCount += 1 })

            RasterRenderPipeline(StandardTestDispatcher(testScheduler)).prefetch(listOf(layer), testMap())

            assertEquals(1, prefetchCount)
        }

    private class TestTileLayer(
        override val id: String = "tiles",
        private val load: suspend (MapState) -> List<Tile> = { emptyList() },
        private val prefetch: suspend (MapState) -> Unit = {},
    ) : TileLayer {
        override val projection = IdentityProjection
        override val grid = TileGrid.defaultFor(projection)

        override suspend fun loadTiles(map: MapState): List<Tile> = load(map)

        override suspend fun prefetchTiles(map: MapState) = prefetch(map)
    }
}
