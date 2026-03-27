package tilo.compose.core.render

import eu.tilo.compose.render.RenderPoint
import eu.tilo.compose.render.backend.RasterRenderSceneLayer
import eu.tilo.compose.render.backend.RenderSceneState
import eu.tilo.compose.render.backend.VectorRenderSceneLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.source.FeatureSource
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.Layer
import tilo.compose.core.layers.raster.TileLayer
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.map.Map
import tilo.compose.core.projection.IdentityProjection
import tilo.compose.core.tile.Tile
import tilo.compose.core.tile.TileBounds
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.TileGrid

class RenderSceneStateTests {

    @Test
    fun withCommandsRetainsExistingTilesUntilNewTilesArrive() {
        val rasterLayer = testRasterLayer()
        val vectorLayer = testVectorLayer()
        val layers = listOf(rasterLayer as Layer, vectorLayer as Layer)
        val tile = testTile(id = 1)

        val initialState = RenderSceneState(
            tilesByLayer = mapOf(rasterLayer.id to listOf(tile))
        )

        val updatedState = initialState.withCommands(
            layers = layers,
            nextCommandsByLayer = mapOf(vectorLayer.id to listOf(RenderPoint(id = "poi", point = Point(5.0, 6.0))))
        )

        val scene = updatedState.toScene(layers)
        assertEquals(2, scene.layers.size)
        assertTrue(scene.layers[0] is RasterRenderSceneLayer)
        assertEquals(listOf(tile), (scene.layers[0] as RasterRenderSceneLayer).tiles)
        assertTrue(scene.layers[1] is VectorRenderSceneLayer)
    }

    @Test
    fun withTilesRetainsExistingCommandsUntilNewCommandsArrive() {
        val rasterLayer = testRasterLayer()
        val vectorLayer = testVectorLayer()
        val layers = listOf(rasterLayer as Layer, vectorLayer as Layer)
        val command = RenderPoint(id = "poi", point = Point(5.0, 6.0))

        val initialState = RenderSceneState(
            commandsByLayer = mapOf(vectorLayer.id to listOf(command))
        )

        val updatedState = initialState.withTiles(
            layers = layers,
            nextTilesByLayer = mapOf(rasterLayer.id to listOf(testTile(id = 2)))
        )

        val scene = updatedState.toScene(layers)
        assertEquals(2, scene.layers.size)
        assertTrue(scene.layers[0] is RasterRenderSceneLayer)
        assertTrue(scene.layers[1] is VectorRenderSceneLayer)
        assertEquals(listOf(command), (scene.layers[1] as VectorRenderSceneLayer).commands)
    }

    @Test
    fun retainOnlyPrunesLayersThatWereRemoved() {
        val rasterLayer = testRasterLayer()
        val vectorLayer = testVectorLayer()

        val state = RenderSceneState(
            tilesByLayer = mapOf(rasterLayer.id to listOf(testTile(id = 1))),
            commandsByLayer = mapOf(vectorLayer.id to listOf(RenderPoint(id = "poi", point = Point(5.0, 6.0))))
        )

        val retained = state.retainOnly(listOf(vectorLayer))

        assertTrue(retained.tilesByLayer.isEmpty())
        assertEquals(setOf(vectorLayer.id), retained.commandsByLayer.keys)
    }

    private fun testRasterLayer(): TileLayer = object : TileLayer {
        override val id: String = "raster"
        override val zIndex: Int = 0
        override val projection = IdentityProjection
        override val grid: TileGrid = TileGrid.WebMercator
        override suspend fun loadTiles(map: Map): List<Tile> = emptyList()
    }

    private fun testVectorLayer(): VectorLayer = object : VectorLayer {
        override val id: String = "vector"
        override val zIndex: Int = 1
        override val projection = IdentityProjection
        override val source: FeatureSource = object : FeatureSource {
            override fun getFeatures(map: Map): List<Feature> = emptyList()
        }
    }

    private fun testTile(id: Int): Tile = Tile(
        coordinate = TileCoordinate(0, id, id),
        bounds = TileBounds(Point(id.toDouble(), id.toDouble() + 1.0), Point(id.toDouble() + 1.0, id.toDouble())),
        bytes = byteArrayOf(id.toByte())
    )
}
