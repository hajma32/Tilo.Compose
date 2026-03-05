package eu.tilo.compose

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"

    override fun tileImageDecoder(bytes: ByteArray): ImageBitmap? {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return bmp.asImageBitmap()
    }
}

actual fun getPlatform(): Platform = AndroidPlatform()
