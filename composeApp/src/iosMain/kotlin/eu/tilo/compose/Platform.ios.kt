package eu.tilo.compose

import androidx.compose.ui.graphics.ImageBitmap
import platform.UIKit.UIDevice

class IOSPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    override fun tileImageDecoder(bytes: ByteArray): ImageBitmap? = null
}

actual fun getPlatform(): Platform = IOSPlatform()
