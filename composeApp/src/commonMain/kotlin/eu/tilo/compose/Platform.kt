package eu.tilo.compose

import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.layers.vector.VectorTileStyleConfigMap

interface Platform {
    val name: String

    /**
     * Returns [VectorLayer]s backed by the bundled MBTiles file.
     *
     * Render output is driven strictly by [styleConfig]; missing layers/sub-layers are not rendered.
     */
    fun createMbtilesVectorLayers(styleConfig: VectorTileStyleConfigMap): List<VectorLayer> = emptyList()
}

expect fun getPlatform(): Platform
