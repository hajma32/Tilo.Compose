package tilo.compose.data.mbtiles

import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point
import tilo.compose.core.vectortile.VectorTileFeatureLoader

class MbtilesVectorFeatureService(
    fileProvider: MbtilesFileProvider,
    driverFactory: MbtilesSqlDriverFactory
) {
    private val featureLoader = VectorTileFeatureLoader(
        source = SqlDelightMbtilesVectorTileSource(
            fileProvider = fileProvider,
            driverFactory = driverFactory
        )
    )

    fun loadFeatures(center: Point, zoom: Double, tileCount: Int): List<Feature> {
        return featureLoader.loadFeatures(center = center, zoom = zoom, tileCount = tileCount)
    }
}

