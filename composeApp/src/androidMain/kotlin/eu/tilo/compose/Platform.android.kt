package eu.tilo.compose

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"

    private val mbtilesLoader by lazy {
        AndroidMbtilesVectorLoader(
            context = AndroidAppContext.require(),
            rawResourceId = R.raw.brno
        )
    }

    override suspend fun loadMbtilesVectorFeatures(center: Point, zoom: Double, tileCount: Int): List<Feature> {
        return withContext(Dispatchers.Default) {
            mbtilesLoader.loadFeatures(center = center, zoom = zoom, tileCount = tileCount)
        }
    }
}

actual fun getPlatform(): Platform = AndroidPlatform()
