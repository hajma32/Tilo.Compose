package tilo.compose.core.layers.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tilo.compose.core.feature.BaseStyle
import tilo.compose.core.geometry.Point
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.vector.VectorTile
import tilo.compose.core.tile.vector.VectorTileDatasetMetadata
import tilo.compose.core.tile.vector.VectorTileFeature
import tilo.compose.core.tile.vector.VectorTileGeometryType
import tilo.compose.core.tile.vector.VectorTileLayer
import tilo.compose.core.tile.vector.VectorTileSource

class VectorTileStyleMapCompilerTests {

    @Test
    fun compileKeepsTopLevelMapOrder() {
        val config = linkedMapOf(
            "water" to VectorTileLayerConfig(
                layerNameMatches = { it.contains("water", ignoreCase = true) },
                subLayers = linkedMapOf(
                    "default" to VectorTileSubLayerConfig(
                        visibility = { true },
                        matches = { _, _, _ -> true },
                        style = { _, _ -> BaseStyle(fillColor = 0xFF0000FF) },
                        label = { _, _ -> null }
                    )
                )
            ),
            "roads" to VectorTileLayerConfig(
                layerNameMatches = { it.contains("road", ignoreCase = true) },
                subLayers = linkedMapOf(
                    "default" to VectorTileSubLayerConfig(
                        visibility = { true },
                        matches = { _, _, _ -> true },
                        style = { _, _ -> BaseStyle(strokeColor = 0xFF000000, strokeWidth = 2.0) },
                        label = { _, _ -> null }
                    )
                )
            )
        )

        val styles = VectorTileStyleMapCompiler.compile(config)

        assertEquals(listOf("water", "roads"), styles.map { it.id })
    }

    @Test
    fun unmatchedSublayerRendersNothing() {
        val vectorTile = VectorTile(
            layers = listOf(
                VectorTileLayer(
                    name = "road",
                    features = listOf(
                        VectorTileFeature(
                            geometryType = VectorTileGeometryType.LINESTRING,
                            geometryCommands = listOf(9, 20, 34, 18, 6, 6),
                            attributes = mapOf("class" to "primary")
                        )
                    )
                )
            )
        )
        val source = object : VectorTileSource {
            override val metadata = VectorTileDatasetMetadata(availableZoomLevels = setOf(14))
            override fun loadTile(tile: TileCoordinate): VectorTile = vectorTile
        }

        val config = linkedMapOf(
            "roads" to VectorTileLayerConfig(
                layerNameMatches = { it.contains("road", ignoreCase = true) },
                subLayers = linkedMapOf(
                    "secondaryOnly" to VectorTileSubLayerConfig(
                        visibility = { true },
                        matches = { _, _, attributes ->
                            (attributes["class"] ?: "").equals("secondary", ignoreCase = true)
                        },
                        style = { _, _ -> BaseStyle(strokeColor = 0xFF111111, strokeWidth = 1.0) },
                        label = { _, _ -> null }
                    )
                )
            )
        )

        val style = VectorTileStyleMapCompiler.compile(config).single()
        val loader = VectorTileFeatureLoader(source = source, layerStyle = style)
        val features = loader.loadFeatures(center = Point(16.6, 49.19), zoom = 14.0, tileCount = 1)

        assertTrue(features.isEmpty())
    }
}

