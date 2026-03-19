package tilo.compose.core.layers.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import tilo.compose.core.geometry.Point
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.vector.VectorTile
import tilo.compose.core.tile.vector.VectorTileDatasetMetadata
import tilo.compose.core.tile.vector.VectorTileFeature
import tilo.compose.core.tile.vector.VectorTileGeometryType
import tilo.compose.core.tile.vector.VectorTileLayer
import tilo.compose.core.tile.vector.VectorTileQueryPlanner
import tilo.compose.core.tile.vector.VectorTileSource

class VectorTileFeatureLoaderTests {

    @Test
    fun plannerPrefersNearestAvailableZoomNotAboveRequested() {
        val planner = VectorTileQueryPlanner()

        val sourceZoom = planner.resolveSourceZoom(
            requestedZoom = 11,
            metadata = VectorTileDatasetMetadata(availableZoomLevels = setOf(8, 10, 12))
        )

        assertEquals(10, sourceZoom)
    }

    @Test
    fun featureLoaderMapsPlacePointToFeatureWithLabel() {
        val vectorTile = VectorTile(
            layers = listOf(
                VectorTileLayer(
                    name = "place",
                    features = listOf(
                        VectorTileFeature(
                            geometryType = VectorTileGeometryType.POINT,
                            geometryCommands = listOf(9, 20, 34),
                            attributes = mapOf("name" to "Brno", "place" to "city", "population" to "380000")
                        )
                    )
                )
            )
        )
        val source = object : VectorTileSource {
            override val metadata = VectorTileDatasetMetadata(availableZoomLevels = setOf(14))

            override fun loadTile(tile: TileCoordinate): VectorTile = vectorTile
        }
        val loader = VectorTileFeatureLoader(source = source)

        val features = loader.loadFeatures(
            center = Point(16.6068, 49.1951),
            zoom = 14.0,
            tileCount = 1
        )

        assertEquals(1, features.size)
        assertEquals("Brno", features.single().label)
        assertNotNull(features.single().style)
        assertTrue(features.single().key.startsWith("14/"))
    }
}
