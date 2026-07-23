@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.layers.vector.FeatureLayer
import tilo.compose.core.map.MapState
import tilo.compose.core.projection.IdentityProjection
import tilo.compose.core.tile.Tile
import tilo.compose.core.tile.TileGrid
import tilo.compose.render.backend.RasterRenderSceneLayer
import tilo.compose.render.backend.RenderScene
import tilo.compose.render.backend.RenderSceneBuilder
import tilo.compose.render.backend.VectorBitmapRenderSceneLayer
import tilo.compose.render.backend.VectorBitmapSnapshot
import tilo.compose.render.backend.VectorRenderSceneLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RenderSceneBuilderTest {
    /**
     * Verifies scene order across raster, cached geometry, live labels, and immediate vectors.
     *
     * Input: three ordered layers with bitmap geometry and label commands on the middle layer.
     * Expected: raster, cached bitmap, cached live commands, then immediate vector commands.
     */
    @Test
    fun rasterCachedGeometryLiveLabelsAndVectorsKeepLayerOrder() {
        val raster = TestTileLayer(id = "raster", zIndex = 0)
        val cached = FeatureLayer(id = "cached", zIndex = 10, features = listOf(Feature(Point(0.0, 0.0), "cached")))
        val immediate =
            FeatureLayer(id = "immediate", zIndex = 20, features = listOf(Feature(Point(0.0, 0.0), "immediate")))
        val bitmap =
            VectorBitmapRenderSceneLayer(
                id = cached.id,
                zIndex = cached.zIndex,
                bitmap = TestImageBitmap(),
                snapshot = VectorBitmapSnapshot(Point(0.0, 0.0), 0.0, 1, 1, 1, 1),
            )
        val label = RenderLabel(id = "cached:label", text = "Cached", anchor = Point(0.0, 0.0))
        val point = RenderPoint(id = "immediate:point", point = Point(0.0, 0.0))

        val scene =
            RenderSceneBuilder.build(
                layers = listOf(raster, cached, immediate),
                tilesByLayer = mapOf(raster.id to listOf(testTile(0))),
                commandsByLayer = mapOf(cached.id to listOf(label), immediate.id to listOf(point)),
                vectorBitmapsByLayer = mapOf(cached.id to bitmap),
                effectiveOpacitiesByLayerId =
                    mapOf(
                        raster.id to 0.2,
                        cached.id to 0.4,
                        immediate.id to 0.6,
                    ),
            )

        assertEquals(listOf("raster", "cached", "cached", "immediate"), scene.layers.map { it.id })
        assertIs<RasterRenderSceneLayer>(scene.layers[0])
        assertIs<VectorBitmapRenderSceneLayer>(scene.layers[1])
        assertIs<VectorRenderSceneLayer>(scene.layers[2])
        assertIs<VectorRenderSceneLayer>(scene.layers[3])
        assertEquals(listOf(0.2, 0.4, 0.4, 0.6), scene.layers.map { it.opacity })
    }

    /**
     * Verifies that layers without renderable output do not create empty scene entries.
     *
     * Input: raster and vector layers with no tiles, commands, or bitmap.
     * Expected: an empty render-scene layer list.
     */
    @Test
    fun emptyLayerOutputsDoNotCreateSceneLayers() {
        val raster = TestTileLayer(id = "raster", zIndex = 0)
        val vector = FeatureLayer(id = "vector", features = emptyList())

        val scene =
            RenderSceneBuilder.build(
                layers = listOf(raster, vector),
                tilesByLayer = emptyMap(),
                commandsByLayer = emptyMap(),
            )

        assertEquals(emptyList(), scene.layers)
    }

    /**
     * Verifies that synchronous placeholder geometry is attached even while raster content is stale or absent.
     *
     * Input: an active raster layer, an empty published scene, and a placeholder planned for the live camera.
     * Expected: the display scene contains that placeholder immediately without waiting for an async render request.
     */
    @Test
    fun liveCameraPlaceholderIsPresentBeforeAsyncRasterSceneArrives() {
        val raster = TestTileLayer(id = "raster", zIndex = 3)
        val placeholder = testTile(1, bytes = null)

        val displayScene =
            RenderScene.Empty.withLiveRasterPlaceholders(
                activeLayers = listOf(raster),
                placeholderFrame =
                    RasterFrame(
                        tilesByLayer = mapOf(raster.id to listOf(placeholder)),
                        decodedImagesByLayer = emptyMap(),
                    ),
                effectiveOpacitiesByLayerId = mapOf(raster.id to 0.7),
            )

        val layer = assertIs<RasterRenderSceneLayer>(displayScene.layers.single())
        assertEquals(emptyList(), layer.tiles)
        assertEquals(listOf(placeholder), layer.placeholderTiles)
        assertEquals(0.7, layer.opacity)
    }

    /** Verifies that refreshing placeholder coverage preserves already decoded fallback content. */
    @Test
    fun liveCameraPlaceholderDoesNotReplacePublishedRasterContent() {
        val raster = TestTileLayer(id = "raster", zIndex = 0)
        val loaded = testTile(1)
        val freshPlaceholder = testTile(2, bytes = null)
        val published =
            RasterRenderSceneLayer(
                id = raster.id,
                zIndex = raster.zIndex,
                tiles = listOf(loaded),
                decodedImages = listOf(TestImageBitmap()),
            )

        val displayScene =
            RenderScene(listOf(published)).withLiveRasterPlaceholders(
                activeLayers = listOf(raster),
                placeholderFrame =
                    RasterFrame(
                        tilesByLayer = mapOf(raster.id to listOf(freshPlaceholder)),
                        decodedImagesByLayer = emptyMap(),
                    ),
                effectiveOpacitiesByLayerId = emptyMap(),
            )

        val layer = assertIs<RasterRenderSceneLayer>(displayScene.layers.single())
        assertEquals(listOf(loaded), layer.tiles)
        assertEquals(published.decodedImages, layer.decodedImages)
        assertEquals(listOf(freshPlaceholder), layer.placeholderTiles)
    }

    private class TestTileLayer(
        override val id: String,
        override val zIndex: Int,
    ) : TileLayer {
        override val projection = IdentityProjection
        override val grid = TileGrid.defaultFor(projection)

        override suspend fun loadTiles(map: MapState): List<Tile> = emptyList()
    }
}
