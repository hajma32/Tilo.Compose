package eu.tilo.compose

import androidx.compose.ui.graphics.ImageBitmap
import tilo.compose.core.tile.source.TileDownloader

interface Platform {
    val name: String
    val tileDownloader: TileDownloader?
    fun tileImageDecoder(bytes: ByteArray): ImageBitmap?
}

expect fun getPlatform(): Platform
