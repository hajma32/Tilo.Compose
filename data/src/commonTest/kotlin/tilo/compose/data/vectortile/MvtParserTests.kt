package tilo.compose.data.vectortile

import kotlin.test.Test
import kotlin.test.assertEquals
import tilo.compose.core.tile.vector.VectorTileGeometryType

class MvtParserTests {

    @Test
    fun parsesMinimalPointTileWithAttributes() {
        val parser = MvtParser()
        val bytes = vectorTileMessage(
            lengthDelimitedField(3, layerMessage())
        )

        val tile = parser.parseTile(bytes)
        val layer = tile.layers.single()
        val feature = layer.features.single()

        assertEquals("place", layer.name)
        assertEquals(4096, layer.extent)
        assertEquals(VectorTileGeometryType.POINT, feature.geometryType)
        assertEquals(listOf(9, 20, 34), feature.geometryCommands)
        assertEquals("Brno", feature.attributes["name"])
    }

    @Test
    fun ignoresUnknownGeometryType() {
        val parser = MvtParser()
        val bytes = vectorTileMessage(
            lengthDelimitedField(3, layerMessage(featureType = 9))
        )

        val tile = parser.parseTile(bytes)
        assertEquals(0, tile.layers.size)
    }

    private fun layerMessage(featureType: Int = 1): ByteArray {
        return message(
            lengthDelimitedField(1, "place".encodeToByteArray()),
            lengthDelimitedField(2, featureMessage(featureType)),
            lengthDelimitedField(3, "name".encodeToByteArray()),
            lengthDelimitedField(4, valueMessage()),
            varintField(5, 4096)
        )
    }

    private fun featureMessage(featureType: Int): ByteArray {
        return message(
            lengthDelimitedField(2, packedVarInts(0, 0)),
            varintField(3, featureType),
            lengthDelimitedField(4, packedVarInts(9, 20, 34))
        )
    }

    private fun valueMessage(): ByteArray {
        return message(lengthDelimitedField(1, "Brno".encodeToByteArray()))
    }

    private fun vectorTileMessage(vararg fields: ByteArray): ByteArray = message(*fields)

    private fun message(vararg fields: ByteArray): ByteArray = fields.fold(ByteArray(0), ByteArray::plus)

    private fun lengthDelimitedField(number: Int, value: ByteArray): ByteArray {
        return tag(number = number, wireType = 2) + lengthDelimited(value)
    }

    private fun varintField(number: Int, value: Int): ByteArray {
        return tag(number = number, wireType = 0) + varint(value)
    }

    private fun tag(number: Int, wireType: Int): ByteArray = varint((number shl 3) or wireType)

    private fun lengthDelimited(bytes: ByteArray): ByteArray = varint(bytes.size) + bytes

    private fun packedVarInts(vararg values: Int): ByteArray = values.fold(ByteArray(0)) { acc, value -> acc + varint(value) }

    private fun varint(value: Int): ByteArray {
        var remaining = value
        val out = mutableListOf<Byte>()
        while (true) {
            if ((remaining and 0x7F.inv()) == 0) {
                out += remaining.toByte()
                return out.toByteArray()
            }
            out += ((remaining and 0x7F) or 0x80).toByte()
            remaining = remaining ushr 7
        }
    }
}
