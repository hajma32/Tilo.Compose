package eu.tilo.compose

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"

    private val mbtilesLoader: AndroidMbtilesVectorLoader by lazy {
        AndroidMbtilesVectorLoader(
            context = AndroidAppContext.require(),
            rawResourceId = R.raw.brno
        )
    }

    override fun tileImageDecoder(bytes: ByteArray): ImageBitmap? {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return bmp.asImageBitmap()
    }

    override suspend fun loadMbtilesVectorFeatures(center: Point, zoom: Double, tileCount: Int): List<Feature> {
        return withContext(Dispatchers.Default) {
            mbtilesLoader.loadFeatures(center = center, zoom = zoom, tileCount = tileCount)
        }
    }
}

actual fun getPlatform(): Platform = AndroidPlatform()
