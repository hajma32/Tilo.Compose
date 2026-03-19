package tilo.compose.data.utils

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

