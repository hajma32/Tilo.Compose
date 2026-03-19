package tilo.compose.data.vectortile

import tilo.compose.core.tile.vector.DEFAULT_VECTOR_TILE_EXTENT
import tilo.compose.core.tile.vector.VectorTileFeature
import tilo.compose.core.tile.vector.VectorTileGeometryType
import tilo.compose.core.tile.vector.VectorTileLayer
import tilo.compose.data.utils.ProtoReader

internal class MvtLayerParser {
    fun parseLayers(bytes: ByteArray): List<VectorTileLayer> {
        val reader = ProtoReader(bytes)
        val layers = mutableListOf<VectorTileLayer>()

        while (!reader.isAtEnd()) {
            val tag = reader.readVarInt32()
            val field = tag ushr 3
            val wire = tag and 0x7
            if (field == 3 && wire == 2) {
                val layerBytes = reader.readLengthDelimited()
                parseLayer(layerBytes)?.let(layers::add)
            } else {
                reader.skipField(wire)
            }
        }

        return layers
    }

    private fun parseLayer(bytes: ByteArray): VectorTileLayer? {
        val reader = ProtoReader(bytes)
        var name = ""
        var extent = DEFAULT_VECTOR_TILE_EXTENT
        val rawFeatures = mutableListOf<ByteArray>()
        val keys = mutableListOf<String>()
        val values = mutableListOf<String>()

        while (!reader.isAtEnd()) {
            val tag = reader.readVarInt32()
            val field = tag ushr 3
            val wire = tag and 0x7
            when (field) {
                1 -> if (wire == 2) name = reader.readString() else reader.skipField(wire)
                2 -> if (wire == 2) rawFeatures += reader.readLengthDelimited() else reader.skipField(wire)
                3 -> if (wire == 2) keys += reader.readString() else reader.skipField(wire)
                4 -> if (wire == 2) parseValue(reader.readLengthDelimited())?.let(values::add) else reader.skipField(wire)
                5 -> if (wire == 0) extent = reader.readVarInt32() else reader.skipField(wire)
                else -> reader.skipField(wire)
            }
        }

        val features = rawFeatures.mapNotNull { featureBytes ->
            parseFeature(featureBytes = featureBytes, keys = keys, values = values)
        }

        if (name.isBlank() || features.isEmpty()) return null
        return VectorTileLayer(name = name, extent = extent, features = features)
    }

    private fun parseFeature(
        featureBytes: ByteArray,
        keys: List<String>,
        values: List<String>
    ): VectorTileFeature? {
        val reader = ProtoReader(featureBytes)
        var type = 0
        var geometry = IntArray(0)
        var tags = IntArray(0)

        while (!reader.isAtEnd()) {
            val tag = reader.readVarInt32()
            val field = tag ushr 3
            val wire = tag and 0x7
            when (field) {
                2 -> if (wire == 2) tags = reader.readPackedVarInt32() else reader.skipField(wire)
                3 -> if (wire == 0) type = reader.readVarInt32() else reader.skipField(wire)
                4 -> if (wire == 2) geometry = reader.readPackedVarInt32() else reader.skipField(wire)
                else -> reader.skipField(wire)
            }
        }

        val geometryType = VectorTileGeometryType.fromEncoded(type) ?: return null
        if (geometry.isEmpty()) return null

        return VectorTileFeature(
            geometryType = geometryType,
            geometryCommands = geometry.toList(),
            attributes = buildAttributes(tags = tags, keys = keys, values = values)
        )
    }

    private fun buildAttributes(
        tags: IntArray,
        keys: List<String>,
        values: List<String>
    ): Map<String, String> {
        if (tags.isEmpty()) return emptyMap()

        val attributes = linkedMapOf<String, String>()
        var index = 0
        while (index + 1 < tags.size) {
            val keyIndex = tags[index]
            val valueIndex = tags[index + 1]
            index += 2

            val key = keys.getOrNull(keyIndex)
            val value = values.getOrNull(valueIndex)
            if (key != null && value != null) {
                attributes[key] = value
            }
        }
        return attributes
    }

    private fun parseValue(bytes: ByteArray): String? {
        val reader = ProtoReader(bytes)
        while (!reader.isAtEnd()) {
            val tag = reader.readVarInt32()
            val field = tag ushr 3
            val wire = tag and 0x7
            when (field) {
                1 -> if (wire == 2) return reader.readString() else reader.skipField(wire)
                2 -> if (wire == 5) return reader.readFloat32().toString() else reader.skipField(wire)
                3 -> if (wire == 1) return reader.readDouble64().toString() else reader.skipField(wire)
                4, 5, 6 -> if (wire == 0) return reader.readVarInt64SignedString() else reader.skipField(wire)
                7 -> if (wire == 0) return (reader.readVarInt32() != 0).toString() else reader.skipField(wire)
                else -> reader.skipField(wire)
            }
        }
        return null
    }
}
