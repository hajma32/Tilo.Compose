package tilo.compose.data.mbtiles

import app.cash.sqldelight.db.SqlDriver
import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.TileMatrixBounds
import tilo.compose.data.mbtiles.db.MbtilesDatabase
import tilo.compose.data.utils.CompressionUtils

internal class SqlDelightMbtilesRepository(
    driver: SqlDriver
) : MbtilesRepository {
    private companion object {
        const val TILE_CACHE_SIZE = 2048
        const val TILE_PRESENCE_ZOOM_CACHE_SIZE = 4
        const val MAX_TILE_PRESENCE_SET_SIZE = 120_000
    }

    private val database = MbtilesDatabase(driver)
    private val queries = database.mbtilesQueries

    private val tileCache = LinkedHashMap<TileCoordinate, ByteArray?>(TILE_CACHE_SIZE, 0.75f)
    private val tilePresenceByZoom = mutableMapOf<Int, Set<Long>>()
    private val tilePresenceAccessOrder = mutableListOf<Int>()

    override val metadata: MbtilesMetadata by lazy {
        val format = queryMetadataValue("format")?.lowercase()
        MbtilesMetadata(
            availableZoomLevels = availableZoomLevels,
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

    private val availableZoomLevels: Set<Int> by lazy {
        queries.selectAvailableZoomLevels()
            .executeAsList()
            .map { zoomLevel -> zoomLevel.toInt() }
            .toSet()
    }

    private val tileMatrixBoundsByZoom: Map<Int, TileMatrixBounds> by lazy {
        queries.selectTileExtentsByZoom()
            .executeAsList()
            .associate { row ->
                row.zoom_level.toInt() to TileMatrixBounds(
                    minX = (row.minX ?: 0L).toInt(),
                    maxX = (row.maxX ?: 0L).toInt(),
                    minY = (row.minRow ?: 0L).toInt(),
                    maxY = (row.maxRow ?: 0L).toInt()
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

    override fun readTileBytes(tile: TileCoordinate): ByteArray? {
        val storageTile = tile.toStorageTile(rowScheme)
        if (!hasPotentialTile(storageTile)) return null

        if (tileCache.containsKey(storageTile)) {
            return tileCache[storageTile]
        }

        val bytes = queries.selectTileData(
            zoom_level = storageTile.z.toLong(),
            tile_column = storageTile.x.toLong(),
            tile_row = storageTile.y.toLong()
        ).executeAsOneOrNull()?.tile_data

        val decodedBytes = bytes?.let { compressedBytes ->
            CompressionUtils.ungzipIfNeeded(compressedBytes)
        }
        cacheTile(storageTile, decodedBytes)
        return decodedBytes
    }

    private fun cacheTile(tile: TileCoordinate, bytes: ByteArray?) {
        if (tileCache.size >= TILE_CACHE_SIZE) {
            tileCache.keys.firstOrNull()?.let(tileCache::remove)
        }
        tileCache[tile] = bytes
    }

    private fun queryBounds(): BoundingBox? {
        val rawBounds = queryMetadataValue("bounds") ?: return null
        val values = rawBounds.split(',').mapNotNull { value -> value.trim().toDoubleOrNull() }
        if (values.size != 4) return null

        return BoundingBox.fromExtents(
            minX = values[0],
            maxX = values[2],
            minY = values[1],
            maxY = values[3]
        )
    }

    private fun hasPotentialTile(tile: TileCoordinate): Boolean {
        val bounds = tileMatrixBoundsByZoom[tile.z] ?: return false
        if (!bounds.contains(tile)) return false

        val zoomTileCount = tileCountByZoom[tile.z] ?: 0
        if (zoomTileCount <= 0) return false
        if (zoomTileCount > MAX_TILE_PRESENCE_SET_SIZE) return true

        val presence = tilePresenceByZoom[tile.z] ?: loadTilePresenceForZoom(tile.z).also { loaded ->
            cacheTilePresence(tile.z, loaded)
        }
        return encodeTileKey(x = tile.x, row = tile.y) in presence
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

    private fun encodeTileKey(x: Int, row: Int): Long {
        return (x.toLong() shl 32) xor (row.toLong() and 0xFFFFFFFFL)
    }
}

