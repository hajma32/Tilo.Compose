package eu.tilo.compose

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.UIKit.UIDevice
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point
import tilo.compose.data.mbtiles.IosBundledMbtilesFileProvider
import tilo.compose.data.mbtiles.IosMbtilesSqlDriverFactory
import tilo.compose.data.mbtiles.MbtilesVectorFeatureService

class IOSPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion

    private val mbtilesService by lazy {
        MbtilesVectorFeatureService(
            fileProvider = IosBundledMbtilesFileProvider(resourceName = "brno"),
            driverFactory = IosMbtilesSqlDriverFactory()
        )
    }

    override suspend fun loadMbtilesVectorFeatures(center: Point, zoom: Double, tileCount: Int): List<Feature> {
        return withContext(Dispatchers.Default) {
            runCatching {
                mbtilesService.loadFeatures(center = center, zoom = zoom, tileCount = tileCount)
            }.getOrDefault(emptyList())
        }
    }
}

actual fun getPlatform(): Platform = IOSPlatform()
