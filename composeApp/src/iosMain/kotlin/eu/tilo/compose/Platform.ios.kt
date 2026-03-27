package eu.tilo.compose

import platform.UIKit.UIDevice
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.layers.vector.VectorTileStyleConfigMap
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

    override fun createMbtilesVectorLayers(styleConfig: VectorTileStyleConfigMap): List<VectorLayer> =
        mbtilesService.createLayers("mbtiles-brno", styleConfigMap = styleConfig)
}

actual fun getPlatform(): Platform = IOSPlatform()
