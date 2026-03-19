package eu.tilo.compose

import android.content.Context
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point
import tilo.compose.data.mbtiles.AndroidMbtilesSqlDriverFactory
import tilo.compose.data.mbtiles.AndroidRawResourceMbtilesFileProvider
import tilo.compose.data.mbtiles.MbtilesVectorFeatureService

/**
 * Thin Android facade that wires resource access to the shared MBTiles feature service.
 */
class AndroidMbtilesVectorLoader(
    context: Context,
    rawResourceId: Int
) {
    private val featureService = MbtilesVectorFeatureService(
        fileProvider = AndroidRawResourceMbtilesFileProvider(
            context = context,
            rawResourceId = rawResourceId
        ),
        driverFactory = AndroidMbtilesSqlDriverFactory(context)
    )

    fun loadFeatures(center: Point, zoom: Double, tileCount: Int): List<Feature> {
        return featureService.loadFeatures(center = center, zoom = zoom, tileCount = tileCount)
    }
}
