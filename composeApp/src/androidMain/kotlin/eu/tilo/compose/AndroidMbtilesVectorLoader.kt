package eu.tilo.compose

import tilo.compose.core.layers.vector.VectorTileStyleConfigMap
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.data.mbtiles.AndroidMbtilesSqlDriverFactory
import tilo.compose.data.mbtiles.AndroidRawResourceMbtilesFileProvider
import tilo.compose.data.mbtiles.MbtilesVectorFeatureService

/**
 * Thin Android facade that wires resource access to the shared MBTiles feature service.
 */
class AndroidMbtilesVectorLoader(
    context: android.content.Context,
    rawResourceId: Int
) {
    private val featureService = MbtilesVectorFeatureService(
        fileProvider = AndroidRawResourceMbtilesFileProvider(
            context = context,
            rawResourceId = rawResourceId
        ),
        driverFactory = AndroidMbtilesSqlDriverFactory(context)
    )

    fun createLayers(
        idPrefix: String,
        styleConfigMap: VectorTileStyleConfigMap
    ): List<VectorLayer> = featureService.createLayers(
        idPrefix = idPrefix,
        styleConfigMap = styleConfigMap
    )
}
