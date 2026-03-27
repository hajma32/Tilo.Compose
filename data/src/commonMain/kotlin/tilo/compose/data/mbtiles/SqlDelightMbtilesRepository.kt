package tilo.compose.data.mbtiles

import app.cash.sqldelight.db.SqlDriver
import kotlin.time.TimeSource
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.data.mbtiles.db.MbtilesDatabase
import tilo.compose.data.utils.CompressionUtils

internal class SqlDelightMbtilesRepository(
    driver: SqlDriver
) : MbtilesRepository {
    private val database = MbtilesDatabase(driver)
    private val queries = database.mbtilesQueries

    override val metadata: MbtilesMetadata by lazy {
        val format = queryMetadataValue("format")?.lowercase()
        MbtilesMetadata(
            availableZoomLevels = queries.selectAvailableZoomLevels()
                .executeAsList()
                .map { zoomLevel -> zoomLevel.toInt() }
                .toSet(),
            minZoom = queryMetadataValue("minzoom")?.toIntOrNull(),
            maxZoom = queryMetadataValue("maxzoom")?.toIntOrNull(),
            bounds = queryBounds(),
            format = format,
            contentType = resolveContentType(format)
        )
    }

    private val rowScheme: String by lazy {
        queryMetadataValue("scheme")?.lowercase() ?: "tms"
    }

    override fun readTileBytes(tile: TileCoordinate): ByteArray? {
        val totalMark = TimeSource.Monotonic.markNow()
        val storageTile = tile.toStorageTile(rowScheme)

        val queryMark = TimeSource.Monotonic.markNow()
        val bytes = queries.selectTileData(
            zoom_level = storageTile.z.toLong(),
            tile_column = storageTile.x.toLong(),
            tile_row = storageTile.y.toLong()
        ).executeAsOneOrNull()?.tile_data
        val queryMs = queryMark.elapsedNow().inWholeMilliseconds

        val decompressionMark = TimeSource.Monotonic.markNow()
        val result = bytes?.let { compressedBytes ->
            CompressionUtils.ungzipIfNeeded(compressedBytes)
        }
        val decompressionMs = decompressionMark.elapsedNow().inWholeMilliseconds
        MbtilesDiagnostics.log(
            "readTile z=${storageTile.z} x=${storageTile.x} y=${storageTile.y} hit=${result != null} " +
                "query=${queryMs}ms decompress=${decompressionMs}ms total=${totalMark.elapsedNow().inWholeMilliseconds}ms"
        )
        return result
    }

    private fun queryBounds(): tilo.compose.core.geometry.BoundingBox? {
        val rawBounds = queryMetadataValue("bounds") ?: return null
        val values = rawBounds.split(',').mapNotNull { value -> value.trim().toDoubleOrNull() }
        if (values.size != 4) return null

        return tilo.compose.core.geometry.BoundingBox.fromExtents(
            minX = values[0],
            maxX = values[2],
            minY = values[1],
            maxY = values[3]
        )
    }

    private fun queryMetadataValue(name: String): String? {
        return queries.selectMetadataValue(name).executeAsOneOrNull()?.value_
    }

    private fun resolveContentType(format: String?): MbtilesContentType {
        return when (format) {
            "pbf", "mvt" -> MbtilesContentType.VECTOR
            "png", "jpg", "jpeg", "webp", "avif" -> MbtilesContentType.RASTER
            else -> MbtilesContentType.UNKNOWN
        }
    }

    private fun TileCoordinate.toStorageTile(rowScheme: String): TileCoordinate {
        val storageRow = if (rowScheme == "xyz") y else ((1 shl z) - 1 - y).coerceAtLeast(0)
        return TileCoordinate(z = z, x = x, y = storageRow)
    }
}
