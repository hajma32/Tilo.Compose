package tilo.compose.core.render

import eu.tilo.compose.render.RenderPoint
import eu.tilo.compose.render.backend.RasterRenderSceneLayer
import eu.tilo.compose.render.backend.RenderSceneBuilder
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
import tilo.compose.core.tile.TileGrid

class RenderSceneBuilderTests {

    @Test
    fun sceneBuilderPreservesLayerOrderAcrossRasterAndVectorLayers() {
        val rasterLayer = object : TileLayer {
            override val id: String = "raster"
            override val zIndex: Int = 0
            override val projection = IdentityProjection
            override val grid: TileGrid = TileGrid.WebMercator
            override suspend fun loadTiles(map: Map): List<Tile> = emptyList()
        }
        val vectorLayer = object : VectorLayer {
            override val id: String = "vector"
            override val zIndex: Int = 1
            override val projection = IdentityProjection
            override val source: FeatureSource = object : FeatureSource {
                override fun getFeatures(map: Map): List<Feature> = emptyList()
            }
        }

        val scene = RenderSceneBuilder.build(
            layers = listOf(rasterLayer as Layer, vectorLayer as Layer),
            tilesByLayer = mapOf(
                rasterLayer.id to listOf(
                    Tile(
                        coordinate = tilo.compose.core.tile.TileCoordinate(0, 0, 0),
                        bounds = TileBounds(Point(0.0, 1.0), Point(1.0, 0.0)),
                        bytes = byteArrayOf(1)
                    )
                )
            ),
            commandsByLayer = mapOf(
                vectorLayer.id to listOf(RenderPoint(id = "p", point = Point(1.0, 2.0)))
            )
        )

        assertEquals(2, scene.layers.size)
        assertTrue(scene.layers[0] is RasterRenderSceneLayer)
        assertTrue(scene.layers[1] is VectorRenderSceneLayer)
    }
}

