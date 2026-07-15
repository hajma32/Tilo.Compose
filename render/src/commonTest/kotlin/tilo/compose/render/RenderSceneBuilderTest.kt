@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.layers.vector.FeatureLayer
import tilo.compose.core.map.MapState
import tilo.compose.core.projection.IdentityProjection
import tilo.compose.core.tile.Tile
import tilo.compose.core.tile.TileGrid
import tilo.compose.render.backend.RasterRenderSceneLayer
import tilo.compose.render.backend.RenderSceneBuilder
import tilo.compose.render.backend.VectorBitmapRenderSceneLayer
import tilo.compose.render.backend.VectorBitmapSnapshot
import tilo.compose.render.backend.VectorRenderSceneLayer

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
        val immediate = FeatureLayer(id = "immediate", zIndex = 20, features = listOf(Feature(Point(0.0, 0.0), "immediate")))
        val bitmap = VectorBitmapRenderSceneLayer(
            id = cached.id,
            zIndex = cached.zIndex,
            bitmap = TestImageBitmap(),
            snapshot = VectorBitmapSnapshot(Point(0.0, 0.0), 0.0, 1, 1, 1, 1),
        )
        val label = RenderLabel(id = "cached:label", text = "Cached", anchor = Point(0.0, 0.0))
        val point = RenderPoint(id = "immediate:point", point = Point(0.0, 0.0))

        val scene = RenderSceneBuilder.build(
            layers = listOf(raster, cached, immediate),
            tilesByLayer = mapOf(raster.id to listOf(testTile(0))),
            commandsByLayer = mapOf(cached.id to listOf(label), immediate.id to listOf(point)),
            vectorBitmapsByLayer = mapOf(cached.id to bitmap),
        )

        assertEquals(listOf("raster", "cached", "cached", "immediate"), scene.layers.map { it.id })
        assertIs<RasterRenderSceneLayer>(scene.layers[0])
        assertIs<VectorBitmapRenderSceneLayer>(scene.layers[1])
        assertIs<VectorRenderSceneLayer>(scene.layers[2])
        assertIs<VectorRenderSceneLayer>(scene.layers[3])
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

        val scene = RenderSceneBuilder.build(
            layers = listOf(raster, vector),
            tilesByLayer = emptyMap(),
            commandsByLayer = emptyMap(),
        )

        assertEquals(emptyList(), scene.layers)
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
