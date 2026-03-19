package tilo.compose.data.mbtiles

import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.vector.VectorTileFeatureLoader

class MbtilesVectorFeatureService(
    fileProvider: MbtilesFileProvider,
    driverFactory: MbtilesSqlDriverFactory
) {
    private val repository: MbtilesRepository by lazy {
        SqlDelightMbtilesRepository(
            driver = driverFactory.createDriver(fileProvider.provideDatabasePath())
        )
    }

    private val featureLoader = VectorTileFeatureLoader(
        source = SqlDelightMbtilesVectorTileSource(repository = repository)
    )

    fun loadFeatures(center: Point, zoom: Double, tileCount: Int): List<Feature> {
        return featureLoader.loadFeatures(center = center, zoom = zoom, tileCount = tileCount)
    }
}
