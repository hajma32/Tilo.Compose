package tilo.compose.data.mbtiles

import tilo.compose.core.vectortile.TileCoordinate
import tilo.compose.core.vectortile.VectorTile
import tilo.compose.core.vectortile.VectorTileDatasetMetadata
import tilo.compose.core.vectortile.VectorTileSource
import tilo.compose.data.vectortile.MvtParser

open class SqlDelightMbtilesVectorTileSource(
    fileProvider: MbtilesFileProvider,
    driverFactory: MbtilesSqlDriverFactory,
    private val parser: MvtParser = MvtParser()
) : VectorTileSource {
    private val store by lazy {
        SqlDelightMbtilesStore(
            driver = driverFactory.createDriver(fileProvider.provideDatabasePath())
        )
    }

    override val metadata: VectorTileDatasetMetadata
        get() = store.metadata

    override fun loadTile(tile: TileCoordinate): VectorTile? {
        val bytes = store.readTileBytes(tile) ?: return null
        return parser.parseTile(bytes)
    }
}
