package eu.tilo.compose

import android.os.Build
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.layers.vector.VectorTileStyleConfigMap

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"

    private val mbtilesLoader by lazy {
        AndroidMbtilesVectorLoader(
            context = AndroidAppContext.require(),
            rawResourceId = R.raw.brno
        )
    }

    override fun createMbtilesVectorLayers(styleConfig: VectorTileStyleConfigMap): List<VectorLayer> =
        mbtilesLoader.createLayers("mbtiles-brno", styleConfigMap = styleConfig)
}

actual fun getPlatform(): Platform = AndroidPlatform()
