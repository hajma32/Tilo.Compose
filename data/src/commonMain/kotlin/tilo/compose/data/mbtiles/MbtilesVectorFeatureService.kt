package tilo.compose.data.mbtiles

import tilo.compose.core.layers.vector.VectorTileDecodedFeatureCache
import tilo.compose.core.layers.vector.VectorTileLayer
import tilo.compose.core.layers.vector.VectorTileStyleConfigMap
import tilo.compose.core.layers.vector.VectorTileStyleMapCompiler

class MbtilesVectorFeatureService(
    fileProvider: MbtilesFileProvider,
    driverFactory: MbtilesSqlDriverFactory
) {
    private val repository: MbtilesRepository by lazy {
        SqlDelightMbtilesRepository(
            driver = driverFactory.createDriver(fileProvider.provideDatabasePath())
        )
    }

    private val source by lazy {
        SqlDelightMbtilesVectorTileSource(repository = repository)
    }

    private val decodedFeatureCache by lazy {
        VectorTileDecodedFeatureCache(maxTiles = 24)
    }

    fun createLayers(
        idPrefix: String,
        styleConfigMap: VectorTileStyleConfigMap,
        zIndexStart: Int = 0,
        tileCount: Int = 9
    ): List<VectorTileLayer> {
        val styles = VectorTileStyleMapCompiler.compile(styleConfigMap)
        return styles.mapIndexed { index, style ->
            VectorTileLayer(
                id = "$idPrefix-${style.id}",
                style = style,
                zIndex = zIndexStart + index,
                source = source,
                tileCount = tileCount,
                decodedFeatureCache = decodedFeatureCache
            )
        }
    }
}
