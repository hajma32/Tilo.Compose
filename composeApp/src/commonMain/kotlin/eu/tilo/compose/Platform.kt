package eu.tilo.compose

import androidx.compose.ui.graphics.ImageBitmap

interface Platform {
    val name: String
    fun tileImageDecoder(bytes: ByteArray): ImageBitmap?
}

expect fun getPlatform(): Platform
