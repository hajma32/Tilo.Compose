package tilo.compose.core.layers.vector

import tilo.compose.core.geometry.Geometry
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.vector.VectorTileFeature
import tilo.compose.core.tile.vector.VectorTileGeometryDecoder
import tilo.compose.core.tile.vector.VectorTileLoadPlan
import tilo.compose.core.tile.vector.VectorTileSource

class VectorTileDecodedFeatureCache(
    private val maxTiles: Int = 24
) {
    private val cache = mutableMapOf<TileCoordinate, List<DecodedVectorFeature>>()
    private val accessOrder = mutableListOf<TileCoordinate>()

    fun getOrDecode(
        source: VectorTileSource,
        plan: VectorTileLoadPlan,
        geometryDecoder: VectorTileGeometryDecoder
    ): List<DecodedVectorFeature> {
        val out = mutableListOf<DecodedVectorFeature>()
        var hits = 0
        var misses = 0

        plan.tiles.forEach { tile ->
            val decoded = cache[tile]
            if (decoded != null) {
                hits++
                touch(tile)
                out += decoded
                return@forEach
            }

            misses++
            val decodedTileFeatures = decodeTile(
                source = source,
                tile = tile,
                geometryDecoder = geometryDecoder
            )
            cache[tile] = decodedTileFeatures
            touch(tile)
            trimToSize()
            out += decodedTileFeatures
        }

        return out
    }

    private fun decodeTile(
        source: VectorTileSource,
        tile: TileCoordinate,
        geometryDecoder: VectorTileGeometryDecoder
    ): List<DecodedVectorFeature> {
        val vectorTile = source.loadTile(tile) ?: return emptyList()
        return buildList {
            vectorTile.layers.forEach { layer ->
                layer.features.forEachIndexed { featureIndex, feature ->
                    val geometry = geometryDecoder.decodeGeometry(
                        feature = feature,
                        extent = layer.extent,
                        tile = tile
                    ) ?: return@forEachIndexed

                    add(
                        DecodedVectorFeature(
                            tile = tile,
                            layerName = layer.name,
                            featureIndex = featureIndex,
                            feature = feature,
                            geometry = geometry
                        )
                    )
                }
            }
        }
    }

    private fun touch(tile: TileCoordinate) {
        accessOrder.remove(tile)
        accessOrder += tile
    }

    private fun trimToSize() {
        while (accessOrder.size > maxTiles) {
            val eldest = accessOrder.removeAt(0)
            cache.remove(eldest)
        }
    }

    fun invalidate() {
        cache.clear()
        accessOrder.clear()
    }
}

data class DecodedVectorFeature(
    val tile: TileCoordinate,
    val layerName: String,
    val featureIndex: Int,
    val feature: VectorTileFeature,
    val geometry: Geometry
)
