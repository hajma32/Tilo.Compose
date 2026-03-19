package tilo.compose.core.vectortile

import tilo.compose.core.geometry.Point

const val DEFAULT_VECTOR_TILE_EXTENT = 4096

data class GeoBounds(
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double
) {
    fun contains(point: Point): Boolean {
        return point.x in minLon..maxLon && point.y in minLat..maxLat
    }
}

data class TileCoordinate(
    val z: Int,
    val x: Int,
    val y: Int
)

data class VectorTileDatasetMetadata(
    val availableZoomLevels: Set<Int>,
    val minZoom: Int? = null,
    val maxZoom: Int? = null,
    val bounds: GeoBounds? = null
)

interface VectorTileSource {
    val metadata: VectorTileDatasetMetadata

    fun loadTile(tile: TileCoordinate): VectorTile?
}

data class VectorTile(
    val layers: List<VectorTileLayer>
)

data class VectorTileLayer(
    val name: String,
    val extent: Int = DEFAULT_VECTOR_TILE_EXTENT,
    val features: List<VectorTileFeature>
)

data class VectorTileFeature(
    val geometryType: VectorTileGeometryType,
    val geometryCommands: List<Int>,
    val attributes: Map<String, String> = emptyMap()
)

enum class VectorTileGeometryType {
    POINT,
    LINESTRING,
    POLYGON;

    companion object {
        fun fromEncoded(value: Int): VectorTileGeometryType? {
            return when (value) {
                1 -> POINT
                2 -> LINESTRING
                3 -> POLYGON
                else -> null
            }
        }
    }
}
