package tilo.compose.data.vectortile

import tilo.compose.core.tile.vector.VectorTile

class MvtParser {
    private val layerParser = MvtLayerParser()

    fun parseTile(bytes: ByteArray): VectorTile {
        return VectorTile(layers = layerParser.parseLayers(bytes))
    }
}
