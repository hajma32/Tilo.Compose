package eu.tilo.compose

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.net.HttpURLConnection
import java.net.URL
import tilo.compose.core.tile.source.TileDownloader

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"

    private val httpTileDownloader = TileDownloader { url ->
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "TiloCompose/0.1")
            setRequestProperty("Accept", "image/png,image/*;q=0.8,*/*;q=0.5")
        }

        try {
            if (conn.responseCode !in 200..299) return@TileDownloader null
            conn.inputStream.use { it.readBytes() }
        } catch (_: Throwable) {
            null
        } finally {
            conn.disconnect()
        }
    }

    override val tileDownloader: TileDownloader? = httpTileDownloader

    override fun tileImageDecoder(bytes: ByteArray): ImageBitmap? {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return bmp.asImageBitmap()
    }
}

actual fun getPlatform(): Platform = AndroidPlatform()
