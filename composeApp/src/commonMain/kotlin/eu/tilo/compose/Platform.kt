package eu.tilo.compose

import androidx.compose.ui.graphics.ImageBitmap
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point

interface Platform {
    val name: String
    fun tileImageDecoder(bytes: ByteArray): ImageBitmap?

    /**
     * Optional platform hook for loading vector features from MBTiles.
     * Platforms that do not support MBTiles parsing can return an empty list.
     */
    suspend fun loadMbtilesVectorFeatures(center: Point, zoom: Double, tileCount: Int): List<Feature> = emptyList()
}

expect fun getPlatform(): Platform
