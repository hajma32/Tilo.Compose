package eu.tilo.compose

import androidx.compose.ui.graphics.ImageBitmap
import platform.UIKit.UIDevice
import tilo.compose.core.tile.source.TileDownloader

class IOSPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    override val tileDownloader: TileDownloader? = null
    override fun tileImageDecoder(bytes: ByteArray): ImageBitmap? = null
}

actual fun getPlatform(): Platform = IOSPlatform()
