package tilo.compose.data.mbtiles

import app.cash.sqldelight.db.SqlDriver
import tilo.compose.core.vectortile.GeoBounds
import tilo.compose.core.vectortile.TileCoordinate
import tilo.compose.core.vectortile.VectorTileDatasetMetadata
import tilo.compose.data.mbtiles.db.MbtilesDatabase

internal class SqlDelightMbtilesStore(
    driver: SqlDriver
) {
    private companion object {
        const val TILE_CACHE_SIZE = 2048
        const val TILE_PRESENCE_ZOOM_CACHE_SIZE = 4
        const val MAX_TILE_PRESENCE_SET_SIZE = 120_000
    }

    private data class TileExtent(
        val minX: Long,
        val maxX: Long,
        val minRow: Long,
        val maxRow: Long
    )

    private data class TileCacheKey(
        val z: Int,
        val x: Int,
        val row: Int
    )

    private val database = MbtilesDatabase(driver)
    private val queries = database.mbtilesQueries

    private val tileCache = LinkedHashMap<TileCacheKey, ByteArray?>(TILE_CACHE_SIZE, 0.75f)
    private val tilePresenceByZoom = mutableMapOf<Int, Set<Long>>()
    private val tilePresenceAccessOrder = mutableListOf<Int>()

    val metadata: VectorTileDatasetMetadata by lazy {
        VectorTileDatasetMetadata(
            availableZoomLevels = availableZoomLevels,
            minZoom = queryMetadataValue("minzoom")?.toIntOrNull(),
            maxZoom = queryMetadataValue("maxzoom")?.toIntOrNull(),
            bounds = queryBounds()
        )
    }

    private val rowScheme: String by lazy {
        queryMetadataValue("scheme")?.lowercase() ?: "tms"
    }

    private val availableZoomLevels: Set<Int> by lazy {
        queries.selectAvailableZoomLevels()
            .executeAsList()
            .map { zoomLevel -> zoomLevel.toInt() }
            .toSet()
    }

    private val tileExtentsByZoom: Map<Int, TileExtent> by lazy {
        queries.selectTileExtentsByZoom()
            .executeAsList()
            .associate { row ->
                row.zoom_level.toInt() to TileExtent(
                    minX = row.minX ?: 0L,
                    maxX = row.maxX ?: 0L,
                    minRow = row.minRow ?: 0L,
                    maxRow = row.maxRow ?: 0L
                )
            }
    }

    private val tileCountByZoom: Map<Int, Int> by lazy {
        queries.selectTileCountByZoom()
            .executeAsList()
            .associate { row ->
                row.zoom_level.toInt() to row.tileCount.toInt()
            }
    }

    fun readTileBytes(tile: TileCoordinate): ByteArray? {
        val row = if (rowScheme == "xyz") {
            tile.y
        } else {
            ((1 shl tile.z) - 1 - tile.y).coerceAtLeast(0)
        }

        if (!hasPotentialTile(tile.z, tile.x, row)) return null

        val cacheKey = TileCacheKey(z = tile.z, x = tile.x, row = row)
        if (tileCache.containsKey(cacheKey)) {
            return tileCache[cacheKey]
        }

        val bytes = queries.selectTileData(
            zoom_level = tile.z.toLong(),
            tile_column = tile.x.toLong(),
            tile_row = row.toLong()
        ).executeAsOneOrNull()?.tile_data

        val decodedBytes = bytes?.let { compressedBytes ->
            MbtilesCompression.ungzipIfNeeded(compressedBytes)
        }
        cacheTile(cacheKey, decodedBytes)
        return decodedBytes
    }

    private fun cacheTile(key: TileCacheKey, bytes: ByteArray?) {
        if (tileCache.size >= TILE_CACHE_SIZE) {
            tileCache.keys.firstOrNull()?.let(tileCache::remove)
        }
        tileCache[key] = bytes
    }

    private fun queryBounds(): GeoBounds? {
        val rawBounds = queryMetadataValue("bounds") ?: return null
        val values = rawBounds.split(',').mapNotNull { value -> value.trim().toDoubleOrNull() }
        if (values.size != 4) return null

        return GeoBounds(
            minLon = values[0],
            minLat = values[1],
            maxLon = values[2],
            maxLat = values[3]
        )
    }

    private fun hasPotentialTile(z: Int, x: Int, row: Int): Boolean {
        val extent = tileExtentsByZoom[z] ?: return false
        if (x.toLong() !in extent.minX..extent.maxX) return false
        if (row.toLong() !in extent.minRow..extent.maxRow) return false

        val zoomTileCount = tileCountByZoom[z] ?: 0
        if (zoomTileCount <= 0) return false
        if (zoomTileCount > MAX_TILE_PRESENCE_SET_SIZE) return true

        val presence = tilePresenceByZoom[z] ?: loadTilePresenceForZoom(z).also { loaded ->
            cacheTilePresence(z, loaded)
        }
        return encodeTileKey(x = x, row = row) in presence
    }

    private fun cacheTilePresence(zoom: Int, presence: Set<Long>) {
        tilePresenceByZoom[zoom] = presence
        tilePresenceAccessOrder.remove(zoom)
        tilePresenceAccessOrder += zoom
        while (tilePresenceAccessOrder.size > TILE_PRESENCE_ZOOM_CACHE_SIZE) {
            val oldestZoom = tilePresenceAccessOrder.removeAt(0)
            tilePresenceByZoom.remove(oldestZoom)
        }
    }

    private fun loadTilePresenceForZoom(z: Int): Set<Long> {
        return queries.selectTilePresenceByZoom(zoom_level = z.toLong())
            .executeAsList()
            .mapTo(LinkedHashSet()) { row ->
                encodeTileKey(x = row.tile_column.toInt(), row = row.tile_row.toInt())
            }
    }

    private fun queryMetadataValue(name: String): String? {
        return queries.selectMetadataValue(name).executeAsOneOrNull()?.value_
    }

    private fun encodeTileKey(x: Int, row: Int): Long {
        return (x.toLong() shl 32) xor (row.toLong() and 0xFFFFFFFFL)
    }
}
