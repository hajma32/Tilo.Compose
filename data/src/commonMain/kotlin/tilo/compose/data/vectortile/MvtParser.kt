package tilo.compose.data.vectortile

import tilo.compose.core.vectortile.DEFAULT_VECTOR_TILE_EXTENT
import tilo.compose.core.vectortile.VectorTile
import tilo.compose.core.vectortile.VectorTileFeature
import tilo.compose.core.vectortile.VectorTileGeometryType
import tilo.compose.core.vectortile.VectorTileLayer

class MvtParser {
    fun parseTile(bytes: ByteArray): VectorTile {
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

        return VectorTile(layers = layers)
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
            val key = keys.getOrNull(tags[index]) ?: run {
                index += 2
                continue
            }
            val value = values.getOrNull(tags[index + 1]) ?: run {
                index += 2
                continue
            }
            attributes[key] = value
            index += 2
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

internal class ProtoReader(private val bytes: ByteArray) {
    private var index: Int = 0

    fun isAtEnd(): Boolean = index >= bytes.size

    fun readVarInt32(): Int = readVarInt64().toInt()

    fun readVarInt64SignedString(): String = readVarInt64().toString()

    private fun readVarInt64(): Long {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            if (index >= bytes.size) return result
            val byte = bytes[index++].toInt() and 0xFF
            result = result or ((byte and 0x7F).toLong() shl shift)
            if ((byte and 0x80) == 0) return result
            shift += 7
        }
        return result
    }

    fun readLengthDelimited(): ByteArray {
        val length = readVarInt32().coerceAtLeast(0)
        val endIndex = (index + length).coerceAtMost(bytes.size)
        val out = bytes.copyOfRange(index, endIndex)
        index = endIndex
        return out
    }

    fun readString(): String = readLengthDelimited().decodeToString()

    fun readPackedVarInt32(): IntArray {
        val nestedReader = ProtoReader(readLengthDelimited())
        val values = mutableListOf<Int>()
        while (!nestedReader.isAtEnd()) {
            values += nestedReader.readVarInt32()
        }
        return values.toIntArray()
    }

    fun readFloat32(): Float {
        if (index + 4 > bytes.size) {
            index = bytes.size
            return 0f
        }
        val b0 = bytes[index++].toInt() and 0xFF
        val b1 = bytes[index++].toInt() and 0xFF
        val b2 = bytes[index++].toInt() and 0xFF
        val b3 = bytes[index++].toInt() and 0xFF
        val bits = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        return Float.fromBits(bits)
    }

    fun readDouble64(): Double {
        if (index + 8 > bytes.size) {
            index = bytes.size
            return 0.0
        }
        var bits = 0L
        repeat(8) { byteIndex ->
            bits = bits or ((bytes[index++].toLong() and 0xFFL) shl (8 * byteIndex))
        }
        return Double.fromBits(bits)
    }

    fun skipField(wireType: Int) {
        when (wireType) {
            0 -> readVarInt64()
            1 -> index = (index + 8).coerceAtMost(bytes.size)
            2 -> {
                val length = readVarInt32().coerceAtLeast(0)
                index = (index + length).coerceAtMost(bytes.size)
            }
            5 -> index = (index + 4).coerceAtMost(bytes.size)
            else -> index = bytes.size
        }
    }
}
