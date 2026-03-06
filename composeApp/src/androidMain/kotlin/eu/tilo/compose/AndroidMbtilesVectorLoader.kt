package eu.tilo.compose

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import tilo.compose.core.feature.BaseStyle
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Geometry
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.MultiLineString
import tilo.compose.core.geometry.MultiPoint
import tilo.compose.core.geometry.MultiPolygon
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon

/**
 * Android-only MBTiles reader that loads vector tile (.pbf) blobs from SQLite and maps them to core Features.
 */
class AndroidMbtilesVectorLoader(
    private val context: Context,
    private val rawResourceId: Int
) {
    private companion object {
         const val MIN_POINTS_FOR_DECIMATION = 24
         const val TILE_CACHE_SIZE = 2048
         const val TILE_PRESENCE_ZOOM_CACHE_SIZE = 4
         const val MAX_TILE_PRESENCE_SET_SIZE = 120_000
         const val BUILDING_MAX_VERTICES = 5
    }

    private data class GeoBounds(
        val minLon: Double,
        val minLat: Double,
        val maxLon: Double,
        val maxLat: Double
    ) {
        fun contains(point: Point): Boolean {
            return point.x in minLon..maxLon && point.y in minLat..maxLat
        }
    }

    private data class TileKey(val z: Int, val x: Int, val y: Int)
    private data class SourceTileId(val z: Int, val x: Int, val y: Int)

    private data class TileExtent(
        val minX: Int,
        val maxX: Int,
        val minRow: Int,
        val maxRow: Int
    )

    private val dbFile: File by lazy {
        val outFile = File(context.cacheDir, "vector_dataset.mbtiles")
        if (!outFile.exists()) {
            context.resources.openRawResource(rawResourceId).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        outFile
    }

    private val database: SQLiteDatabase by lazy {
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).also { db ->
            // Ensure lookups by z/x/y are indexed when `tiles` is a real table.
            // Some MBTiles variants expose `tiles` as a view and SQLite forbids indexing views.
            runCatching {
                if (isTilesTable(db)) {
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS tiles_zxy_idx " +
                            "ON tiles (zoom_level, tile_column, tile_row)"
                    )
                }
            }
        }
    }

    private val rowScheme: String by lazy {
        queryMetadataValue("scheme")?.lowercase() ?: "tms"
    }

    private val datasetBounds: GeoBounds? by lazy {
        val rawBounds = queryMetadataValue("bounds") ?: return@lazy null
        val parts = rawBounds.split(',').mapNotNull { it.trim().toDoubleOrNull() }
        if (parts.size != 4) return@lazy null
        GeoBounds(
            minLon = parts[0],
            minLat = parts[1],
            maxLon = parts[2],
            maxLat = parts[3]
        )
    }

    private val minZoomMetadata: Int? by lazy {
        queryMetadataValue("minzoom")?.toIntOrNull()
    }

    private val maxZoomMetadata: Int? by lazy {
        queryMetadataValue("maxzoom")?.toIntOrNull()
    }

    private val tileCache = LinkedHashMap<TileKey, ByteArray?>(TILE_CACHE_SIZE, 0.75f)

    private val availableZoomLevels: Set<Int> by lazy {
         val out = mutableSetOf<Int>()
         val cursor = database.rawQuery("SELECT DISTINCT zoom_level FROM tiles", null)
         cursor.use {
             while (it.moveToNext()) {
                 out += it.getInt(0)
             }
         }
         out
     }

    private val tileExtentsByZoom: Map<Int, TileExtent> by lazy {
         val out = mutableMapOf<Int, TileExtent>()
         val cursor = database.rawQuery(
            "SELECT zoom_level, MIN(tile_column), MAX(tile_column), MIN(tile_row), MAX(tile_row) " +
                "FROM tiles GROUP BY zoom_level",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                val z = it.getInt(0)
                out[z] = TileExtent(
                    minX = it.getInt(1),
                    maxX = it.getInt(2),
                    minRow = it.getInt(3),
                    maxRow = it.getInt(4)
                )
            }
        }
        out
    }

    private val tileCountByZoom: Map<Int, Int> by lazy {
        val out = mutableMapOf<Int, Int>()
        val cursor = database.rawQuery(
            "SELECT zoom_level, COUNT(*) FROM tiles GROUP BY zoom_level",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                out[it.getInt(0)] = it.getInt(1)
            }
        }
        out
    }

    private val tilePresenceByZoom = object : LinkedHashMap<Int, Set<Long>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Set<Long>>): Boolean {
            return size > TILE_PRESENCE_ZOOM_CACHE_SIZE
        }
    }

    fun loadFeatures(center: Point, zoom: Double, tileCount: Int): List<Feature> {
        val requestedZoom = zoom.roundToInt().coerceAtLeast(0)
        val sourceZoom = resolveSourceZoom(requestedZoom) ?: return emptyList()

        val perTileLimit = Int.MAX_VALUE
        val centerTileX = lonToTileX(center.x, requestedZoom)
        val centerTileY = latToTileY(center.y, requestedZoom)

        val gridSide = kotlin.math.max(1, kotlin.math.ceil(sqrt(tileCount.toDouble())).toInt())
        val radius = gridSide / 2
        val maxIndex = (2.0.pow(requestedZoom.toDouble()).toInt() - 1).coerceAtLeast(0)

        val sourceTiles = LinkedHashSet<SourceTileId>()
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val x = wrapX(centerTileX + dx, requestedZoom)
                val y = (centerTileY + dy).coerceIn(0, maxIndex)
                val mapped = mapRequestedTileToSourceTile(x, y, requestedZoom, sourceZoom)
                sourceTiles += SourceTileId(z = sourceZoom, x = mapped.first, y = mapped.second)
            }
        }

        if (sourceTiles.isEmpty()) return emptyList()

        val out = mutableListOf<Feature>()
        var featureCounter = 0

        sourceTiles.forEach { sourceTile ->
            val tileBytes = readTileData(sourceTile.z, sourceTile.x, sourceTile.y) ?: return@forEach
            val decoded = decodeVectorTile(
                tileBytes = tileBytes,
                sourceZoom = sourceTile.z,
                renderZoom = requestedZoom,
                tileX = sourceTile.x,
                tileY = sourceTile.y,
                perTileLimit = perTileLimit
            )
            decoded.forEach { feature ->
                out += feature.copy(key = "${feature.key}:$featureCounter")
                featureCounter++
            }
        }

        return out
    }

    private fun resolveSourceZoom(requestedZoom: Int): Int? {
        val minZoom = minZoomMetadata
        val maxZoom = maxZoomMetadata

        val candidate = availableZoomLevels
            .asSequence()
            .filter { z -> minZoom == null || z >= minZoom }
            .filter { z -> maxZoom == null || z <= maxZoom }
            .filter { z -> z <= requestedZoom }
            .maxOrNull()

        return candidate ?: availableZoomLevels.minOrNull()
    }

    private fun mapRequestedTileToSourceTile(
        requestedX: Int,
        requestedY: Int,
        requestedZoom: Int,
        sourceZoom: Int
    ): Pair<Int, Int> {
        if (sourceZoom == requestedZoom) return requestedX to requestedY

        return if (sourceZoom < requestedZoom) {
            val delta = requestedZoom - sourceZoom
            (requestedX shr delta) to (requestedY shr delta)
        } else {
            val delta = sourceZoom - requestedZoom
            (requestedX shl delta) to (requestedY shl delta)
        }
    }

    private fun readTileData(z: Int, x: Int, y: Int): ByteArray? {
        val tileRow = if (rowScheme == "xyz") {
             y
         } else {
             ((1 shl z) - 1 - y).coerceAtLeast(0)
         }

        if (!hasPotentialTile(z, x, tileRow)) return null

         val key = TileKey(z = z, x = x, y = tileRow)
         if (tileCache.containsKey(key)) return tileCache[key]

        val cursor = database.rawQuery(
            "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
            arrayOf(z.toString(), x.toString(), tileRow.toString())
        )
        cursor.use {
            if (!it.moveToFirst()) {
                cacheTile(key, null)
                return null
            }
            val bytes = it.getBlob(0)
            val decoded = if (bytes != null) ungzipIfNeeded(bytes) else null
            cacheTile(key, decoded)
            return decoded
        }
    }

    private fun cacheTile(key: TileKey, bytes: ByteArray?) {
        if (tileCache.size >= TILE_CACHE_SIZE) {
            val oldest = tileCache.keys.firstOrNull()
            if (oldest != null) tileCache.remove(oldest)
        }
        tileCache[key] = bytes
    }

    private fun hasPotentialTile(z: Int, x: Int, row: Int): Boolean {
        val extent = tileExtentsByZoom[z] ?: return false
        if (x < extent.minX || x > extent.maxX) return false
        if (row < extent.minRow || row > extent.maxRow) return false

        // For very dense zoom levels, loading full presence set can be slower than sparse misses.
        val tileCountForZoom = tileCountByZoom[z] ?: 0
        if (tileCountForZoom <= 0) return false
        if (tileCountForZoom > MAX_TILE_PRESENCE_SET_SIZE) return true

        val presence = synchronized(tilePresenceByZoom) {
            tilePresenceByZoom[z] ?: loadTilePresenceForZoom(z).also { loaded ->
                tilePresenceByZoom[z] = loaded
            }
        }

        return encodeTileKey(x, row) in presence
    }

    private fun loadTilePresenceForZoom(z: Int): Set<Long> {
        val out = HashSet<Long>()
        val cursor = database.rawQuery(
            "SELECT tile_column, tile_row FROM tiles WHERE zoom_level = ?",
            arrayOf(z.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                out += encodeTileKey(it.getInt(0), it.getInt(1))
            }
        }
        return out
    }

    private fun encodeTileKey(x: Int, row: Int): Long {
        return (x.toLong() shl 32) xor (row.toLong() and 0xFFFFFFFFL)
    }

    private fun queryMetadataValue(name: String): String? {
        val cursor = database.rawQuery(
            "SELECT value FROM metadata WHERE name = ? LIMIT 1",
            arrayOf(name)
        )
        cursor.use {
            if (!it.moveToFirst()) return null
            return it.getString(0)
        }
    }

    private fun ungzipIfNeeded(bytes: ByteArray): ByteArray {
        val isGzip = bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
        if (!isGzip) return bytes
        return GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
    }

    private fun decodeVectorTile(
        tileBytes: ByteArray,
        sourceZoom: Int,
        renderZoom: Int,
        tileX: Int,
        tileY: Int,
        perTileLimit: Int
    ): List<Feature> {
        val layers = MvtProto.parseTile(tileBytes)
        val out = ArrayList<Feature>()

        layers.forEach { layer ->
            if (!isLayerEnabled(layer.name, renderZoom)) return@forEach

            layer.features.take(perTileLimit).forEachIndexed { idx, feature ->
                // Ignore POI and other non-municipality point markers for now.
                if (feature.type == 1 && !isMunicipalityPointFeature(layer.name, feature.attributes)) {
                    return@forEachIndexed
                }

                val geometry = decodeGeometry(
                    geomType = feature.type,
                    geometryCommands = feature.geometry,
                    extent = layer.extent,
                    z = sourceZoom,
                    tileX = tileX,
                    tileY = tileY
                ) ?: return@forEachIndexed

                val simplifiedGeometry = simplifyBuildingGeometryIfNeeded(layer.name, geometry)

                val bounds = datasetBounds
                if (bounds != null && geometryPoints(simplifiedGeometry).none(bounds::contains)) return@forEachIndexed

                val label = extractFeatureLabel(layer.name, feature.attributes, renderZoom)
                val style = styleForLayer(layer.name, feature.attributes, renderZoom)

                out += Feature(
                    geometry = simplifiedGeometry,
                    key = "$sourceZoom/$tileX/$tileY:${layer.name}:$idx",
                    style = style,
                    label = label
                )
            }
        }

        return out
    }

    private fun isMunicipalityPointFeature(layerName: String, attributes: Map<String, String>): Boolean {
        val settlementKinds = setOf("city", "town", "village", "hamlet", "suburb", "municipality")
        val lowerLayer = layerName.lowercase()

        val place = attributes["place"]?.lowercase()
        val clazz = attributes["class"]?.lowercase()
        val type = attributes["type"]?.lowercase()
        if (place in settlementKinds || clazz in settlementKinds || type in settlementKinds) {
            return true
        }

        val name = featureName(attributes)
        val isPlaceLayer = lowerLayer.contains("place") ||
            lowerLayer.contains("settlement") ||
            lowerLayer.contains("locality") ||
            lowerLayer.contains("municip")
        if (isPlaceLayer && !name.isNullOrBlank()) {
            return true
        }

        val isAdminLayer = lowerLayer.contains("admin") || lowerLayer.contains("boundary")
        val hasAdminHint = attributes["admin_level"] != null ||
            attributes["boundary"]?.lowercase() == "administrative"
        return isAdminLayer && hasAdminHint && !name.isNullOrBlank()
    }

    private fun extractFeatureLabel(layerName: String, attributes: Map<String, String>, renderZoom: Int): String? {
        val name = featureName(attributes) ?: return null

        if (renderZoom < 13) return null
        if (isMajorCity(attributes)) return name

        val lowerLayer = layerName.lowercase()
        val settlementKinds = setOf("city", "town", "village", "hamlet", "suburb", "municipality")
        val placeClass = attributes["place"]?.lowercase()
        val clazz = attributes["class"]?.lowercase()
        val type = attributes["type"]?.lowercase()

        val isSettlement = placeClass in settlementKinds || clazz in settlementKinds || type in settlementKinds
        if (isSettlement) return name

        val isPlaceLayer = lowerLayer.contains("place") ||
            lowerLayer.contains("settlement") ||
            lowerLayer.contains("locality") ||
            lowerLayer.contains("municip")
        if (isPlaceLayer) return name

        val isAdminBoundary =
            (lowerLayer.contains("boundary") || lowerLayer.contains("admin")) &&
                (attributes["admin_level"] != null || attributes["boundary"]?.lowercase() == "administrative")

        if (isAdminBoundary) return name

        return null
     }

    private fun featureName(attributes: Map<String, String>): String? {
        return attributes["name:cs"]
            ?: attributes["name"]
            ?: attributes["name:en"]
            ?: attributes["official_name"]
            ?: attributes["name_int"]
            ?: attributes["name:latin"]
    }

    private fun settlementPopulation(attributes: Map<String, String>): Long {
        val raw = attributes["population"] ?: return 0L
        return raw.filter { it.isDigit() }.toLongOrNull() ?: 0L
    }

    private fun isMajorCity(attributes: Map<String, String>): Boolean {
        val place = attributes["place"]?.lowercase()
        val clazz = attributes["class"]?.lowercase()
        val type = attributes["type"]?.lowercase()
        val isCityKind = place == "city" || clazz == "city" || type == "city"
        val isCapital = attributes["capital"]?.lowercase() in setOf("yes", "true", "1")
        val population = settlementPopulation(attributes)
        return isCapital || (isCityKind && population >= 80_000L)
    }

    private fun simplifyBuildingGeometryIfNeeded(layerName: String, geometry: Geometry): Geometry {
        val isBuildingLayer = layerName.lowercase().contains("building")
        if (!isBuildingLayer) return geometry

        return when (geometry) {
            is Polygon -> Polygon(rings = geometry.rings.map(::simplifyBuildingRing))
            is MultiPolygon -> MultiPolygon(
                polygons = geometry.polygons.map { poly ->
                    Polygon(rings = poly.rings.map(::simplifyBuildingRing))
                }
            )
            else -> geometry
        }
    }

    private fun simplifyBuildingRing(points: List<Point>): List<Point> {
        if (points.size <= BUILDING_MAX_VERTICES + 1) return points

        val unique = if (points.firstOrNull() == points.lastOrNull()) points.dropLast(1) else points
        if (unique.size <= BUILDING_MAX_VERTICES) return unique + unique.first()

        val step = unique.size.toDouble() / BUILDING_MAX_VERTICES.toDouble()
        val sampled = (0 until BUILDING_MAX_VERTICES).map { i ->
            val idx = (i * step).toInt().coerceIn(0, unique.lastIndex)
            unique[idx]
        }.distinct()

        if (sampled.size < 3) return points
        return sampled + sampled.first()
    }

    private fun geometryPoints(geometry: Geometry): List<Point> {
        return when (geometry) {
            is Point -> listOf(geometry)
            is MultiPoint -> geometry.points
            is LineString -> geometry.points
            is MultiLineString -> geometry.lines.flatMap { it.points }
            is Polygon -> geometry.rings.flatten()
            is MultiPolygon -> geometry.polygons.flatMap { it.rings.flatten() }
        }
    }

    private fun decodeGeometry(
        geomType: Int,
        geometryCommands: IntArray,
        extent: Int,
        z: Int,
        tileX: Int,
        tileY: Int
    ): Geometry? {
        val paths = decodePaths(geometryCommands)
        if (paths.isEmpty()) return null

        return when (geomType) {
            1 -> {
                val points = paths
                    .flatMap { it }
                    .map { p -> tilePointToLonLat(p.x, p.y, extent, z, tileX, tileY) }
                when (points.size) {
                    0 -> null
                    1 -> points.first()
                    else -> {
                        val sampled = if (z < 13 && points.size > 200) {
                            points.filterIndexed { i, _ -> i % 4 == 0 }
                        } else {
                            points
                        }
                        MultiPoint(sampled)
                    }
                }
            }

            2 -> {
                val lines = paths
                    .map { path -> path.map { p -> tilePointToLonLat(p.x, p.y, extent, z, tileX, tileY) } }
                    .map { simplifyLine(it, z) }
                    .filter { it.size >= 2 }
                    .map { pts -> LineString(pts) }
                when (lines.size) {
                    0 -> null
                    1 -> lines.first()
                    else -> MultiLineString(lines)
                }
            }

            3 -> {
                val rings = paths.map { ring ->
                    val pts = ring.map { p -> tilePointToLonLat(p.x, p.y, extent, z, tileX, tileY) }
                    val simplified = simplifyRing(pts, z)
                    if (simplified.firstOrNull() != simplified.lastOrNull() && simplified.isNotEmpty()) {
                        simplified + simplified.first()
                    } else {
                        simplified
                    }
                }.filter { it.size >= 4 }

                when {
                    rings.isEmpty() -> null
                    rings.size == 1 -> Polygon(rings = listOf(rings.first()))
                    else -> MultiPolygon(rings.map { Polygon(rings = listOf(it)) })
                }
            }

            else -> null
        }
    }

    private fun simplifyLine(points: List<Point>, z: Int): List<Point> {
        if (points.size < MIN_POINTS_FOR_DECIMATION) return points
        val step = when {
            z < 11 -> 8
            z < 13 -> 5
            z < 15 -> 3
            else -> 1
        }
        if (step <= 1) return points

        val sampled = points.filterIndexed { index, _ ->
            index == 0 || index == points.lastIndex || index % step == 0
        }
        return if (sampled.size >= 2) sampled else points
    }

    private fun simplifyRing(points: List<Point>, z: Int): List<Point> {
        if (points.size < MIN_POINTS_FOR_DECIMATION) return points
        val step = when {
            z < 11 -> 10
            z < 13 -> 6
            z < 15 -> 3
            else -> 1
        }
        if (step <= 1) return points

        val sampled = points.filterIndexed { index, _ -> index % step == 0 }
        return if (sampled.size >= 4) sampled else points
    }

    private fun isLayerEnabled(layerName: String, zoom: Int): Boolean {
        val lower = layerName.lowercase()
        if (lower.contains("building")) return zoom >= 18

        val isLabelLayer = lower.contains("place") ||
            lower.contains("settlement") ||
            lower.contains("locality") ||
            lower.contains("municip") ||
            lower.contains("admin")
        if (isLabelLayer) return true

        return when {
            zoom >= 13 -> lower.contains("road") ||
                lower.contains("water") ||
                lower.contains("landuse") ||
                lower.contains("boundary")
            zoom >= 10 -> lower.contains("road") ||
                lower.contains("water") ||
                lower.contains("boundary")
            else -> lower.contains("road") || lower.contains("water")
        }
    }

    private data class IPoint(val x: Int, val y: Int)

    private fun decodePaths(commands: IntArray): List<List<IPoint>> {
        var x = 0
        var y = 0
        var i = 0
        val paths = mutableListOf<MutableList<IPoint>>()
        var current: MutableList<IPoint>? = null

        while (i < commands.size) {
            val cmd = commands[i] and 0x7
            val count = commands[i] ushr 3
            i++

            when (cmd) {
                1 -> {
                    repeat(count) {
                        if (i + 1 >= commands.size) return@repeat
                        x += zigZagDecode(commands[i++])
                        y += zigZagDecode(commands[i++])
                        current = mutableListOf(IPoint(x, y))
                        paths += current
                    }
                }

                2 -> {
                    repeat(count) {
                        if (current == null || i + 1 >= commands.size) return@repeat
                        x += zigZagDecode(commands[i++])
                        y += zigZagDecode(commands[i++])
                        current.add(IPoint(x, y))
                    }
                }

                7 -> {
                    repeat(count) {
                        val ring = current
                        if (ring != null && ring.isNotEmpty() && ring.first() != ring.last()) {
                            ring.add(ring.first())
                        }
                    }
                }

                else -> break
            }
        }

        return paths
    }

    private fun zigZagDecode(value: Int): Int = (value ushr 1) xor -(value and 1)

    private fun tilePointToLonLat(localX: Int, localY: Int, extent: Int, z: Int, tileX: Int, tileY: Int): Point {
        val denom = 2.0.pow(z.toDouble())
        val nx = (tileX + (localX / extent.toDouble())) / denom
        val ny = (tileY + (localY / extent.toDouble())) / denom

        val lon = nx * 360.0 - 180.0
        val lat = atan(sinh(PI * (1.0 - 2.0 * ny))) * 180.0 / PI
        return Point(lon, lat)
    }

    private fun lonToTileX(lon: Double, z: Int): Int {
        val n = 2.0.pow(z.toDouble())
        return floor((lon + 180.0) / 360.0 * n).toInt()
    }

    private fun latToTileY(lat: Double, z: Int): Int {
        val latRad = lat.coerceIn(-85.05112878, 85.05112878) * PI / 180.0
        val n = 2.0.pow(z.toDouble())
        return floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()
    }

    private fun wrapX(x: Int, z: Int): Int {
        val n = 2.0.pow(z.toDouble()).toInt().coerceAtLeast(1)
        val mod = x % n
        return if (mod < 0) mod + n else mod
    }

    private fun styleForLayer(layerName: String, attributes: Map<String, String>, renderZoom: Int): BaseStyle {
        val lower = layerName.lowercase()

        if (
            lower.contains("place") ||
            lower.contains("settlement") ||
            lower.contains("locality") ||
            lower.contains("municip") ||
            lower.contains("admin")
        ) {
            // Hide point marker and render only text label for municipality/city layers.
            return BaseStyle(strokeColor = 0xFF111827, fillColor = 0x00000000L, strokeWidth = 0.0)
        }

        if (lower.contains("road")) {
            val roadKind = (attributes["class"] ?: attributes["type"] ?: "").lowercase()
            val major = roadKind.contains("motorway") || roadKind.contains("trunk") || roadKind.contains("primary")
            val strokeColor = if (major) 0xFF4B5563 else 0xFF6B7280
            val strokeWidth = when {
                renderZoom >= 15 && major -> 3.8
                renderZoom >= 15 -> 2.8
                renderZoom >= 12 && major -> 3.0
                renderZoom >= 12 -> 2.2
                renderZoom >= 9 -> 1.8
                else -> 1.4
            }
            return BaseStyle(strokeColor = strokeColor, strokeWidth = strokeWidth)
        }

        if (lower.contains("water")) {
            return BaseStyle(strokeColor = 0xFF60A5FA, fillColor = 0x3360A5FA, strokeWidth = 1.5)
        }

        if (lower.contains("building")) {
            return BaseStyle(strokeColor = 0xFF9CA3AF, fillColor = 0x55D1D5DB, strokeWidth = 1.0)
        }

        if (lower.contains("boundary")) {
            return BaseStyle(strokeColor = 0xFF9CA3AF, strokeWidth = 1.2)
        }

        if (lower.contains("landuse")) {
            return BaseStyle(strokeColor = 0xFF84CC16, fillColor = 0x2284CC16, strokeWidth = 0.8)
        }

        return BaseStyle(strokeColor = 0xFF4B5563, fillColor = 0x224B5563, strokeWidth = 1.0)
    }

    private fun isTilesTable(db: SQLiteDatabase): Boolean {
        val cursor = db.rawQuery(
            "SELECT type FROM sqlite_master WHERE name = 'tiles' LIMIT 1",
            null
        )
        cursor.use {
            if (!it.moveToFirst()) return false
            val type = it.getString(0)?.lowercase() ?: return false
            return type == "table"
        }
    }
}

private object MvtProto {
    data class FeatureMsg(
        val type: Int,
        val geometry: IntArray,
        val attributes: Map<String, String>
    )

    data class LayerMsg(
        val name: String,
        val extent: Int,
        val features: List<FeatureMsg>
    )

    fun parseTile(bytes: ByteArray): List<LayerMsg> {
        val reader = ProtoReader(bytes)
        val layers = mutableListOf<LayerMsg>()
        while (!reader.isAtEnd()) {
            val tag = reader.readVarInt32()
            val field = tag ushr 3
            val wire = tag and 0x7
            if (field == 3 && wire == 2) {
                val msgBytes = reader.readLengthDelimited()
                parseLayer(msgBytes)?.let { layers += it }
            } else {
                reader.skipField(wire)
            }
        }
        return layers
    }

    private fun parseLayer(bytes: ByteArray): LayerMsg? {
        val reader = ProtoReader(bytes)
        var name = ""
        var extent = 4096
        val rawFeatures = mutableListOf<ByteArray>()
        val keys = mutableListOf<String>()
        val values = mutableListOf<String>()

        while (!reader.isAtEnd()) {
            val tag = reader.readVarInt32()
            val field = tag ushr 3
            val wire = tag and 0x7
            when (field) {
                1 -> if (wire == 2) name = reader.readString() else reader.skipField(wire)
                2 -> if (wire == 2) rawFeatures += reader.readLengthDelimited() else reader.skipField(wire)
                3 -> if (wire == 2) keys += reader.readString() else reader.skipField(wire)
                4 -> if (wire == 2) parseValue(reader.readLengthDelimited())?.let { values += it } else reader.skipField(wire)
                5 -> if (wire == 0) extent = reader.readVarInt32() else reader.skipField(wire)
                else -> reader.skipField(wire)
            }
        }

        val features = rawFeatures.mapNotNull { featureBytes -> parseFeature(featureBytes, keys, values) }

        if (name.isBlank() || features.isEmpty()) return null
        return LayerMsg(name = name, extent = extent, features = features)
    }

    private fun parseFeature(bytes: ByteArray, keys: List<String>, values: List<String>): FeatureMsg? {
        val reader = ProtoReader(bytes)
        var type = 0
        var geometry = IntArray(0)
        var tags = IntArray(0)

        while (!reader.isAtEnd()) {
            val tag = reader.readVarInt32()
            val field = tag ushr 3
            val wire = tag and 0x7
            when (field) {
                3 -> if (wire == 0) type = reader.readVarInt32() else reader.skipField(wire)
                4 -> if (wire == 2) geometry = reader.readPackedVarInt32() else reader.skipField(wire)
                2 -> if (wire == 2) tags = reader.readPackedVarInt32() else reader.skipField(wire)
                else -> reader.skipField(wire)
            }
        }

        if (type == 0 || geometry.isEmpty()) return null
        return FeatureMsg(
            type = type,
            geometry = geometry,
            attributes = buildAttributes(tags, keys, values)
        )
    }

    private fun buildAttributes(tags: IntArray, keys: List<String>, values: List<String>): Map<String, String> {
        if (tags.isEmpty()) return emptyMap()

        val out = linkedMapOf<String, String>()
        var i = 0
        while (i + 1 < tags.size) {
            val keyIndex = tags[i]
            val valueIndex = tags[i + 1]
            i += 2

            val key = keys.getOrNull(keyIndex) ?: continue
            val value = values.getOrNull(valueIndex) ?: continue
            out[key] = value
        }
        return out
    }

    private fun parseValue(bytes: ByteArray): String? {
        val reader = ProtoReader(bytes)
        while (!reader.isAtEnd()) {
            val tag = reader.readVarInt32()
            val field = tag ushr 3
            val wire = tag and 0x7
            when (field) {
                1 -> if (wire == 2) return reader.readString() else reader.skipField(wire) // string_value
                2 -> if (wire == 5) return reader.readFloat32().toString() else reader.skipField(wire) // float_value
                3 -> if (wire == 1) return reader.readDouble64().toString() else reader.skipField(wire) // double_value
                4, 5, 6 -> if (wire == 0) return reader.readVarInt64SignedString() else reader.skipField(wire) // int/uint/sint
                7 -> if (wire == 0) return (reader.readVarInt32() != 0).toString() else reader.skipField(wire) // bool
                else -> reader.skipField(wire)
            }
        }
        return null
    }
}

private class ProtoReader(private val bytes: ByteArray) {
    private var index: Int = 0

    fun isAtEnd(): Boolean = index >= bytes.size

    fun readVarInt32(): Int = readVarInt64().toInt()

    fun readVarInt64SignedString(): String = readVarInt64().toString()

    private fun readVarInt64(): Long {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            if (index >= bytes.size) return result
            val b = bytes[index++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) return result
            shift += 7
        }
        return result
    }

    fun readLengthDelimited(): ByteArray {
        val len = readVarInt32().coerceAtLeast(0)
        val end = (index + len).coerceAtMost(bytes.size)
        val out = bytes.copyOfRange(index, end)
        index = end
        return out
    }

    fun readString(): String = readLengthDelimited().decodeToString()

    fun readPackedVarInt32(): IntArray {
        val body = readLengthDelimited()
        val nested = ProtoReader(body)
        val values = mutableListOf<Int>()
        while (!nested.isAtEnd()) {
            values += nested.readVarInt32()
        }
        return values.toIntArray()
    }

    fun readFloat32(): Float {
        if (index + 4 > bytes.size) {
            index = bytes.size
            return 0f
        }
        val b0 = bytes[index++].toInt() and 0xFF
        val b1 = bytes[index++].toInt() and 0xFF
        val b2 = bytes[index++].toInt() and 0xFF
        val b3 = bytes[index++].toInt() and 0xFF
        val bits = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        return Float.fromBits(bits)
    }

    fun readDouble64(): Double {
        if (index + 8 > bytes.size) {
            index = bytes.size
            return 0.0
        }
        var bits = 0L
        repeat(8) { byteIndex ->
            bits = bits or ((bytes[index++].toLong() and 0xFFL) shl (8 * byteIndex))
        }
        return Double.fromBits(bits)
    }

    fun skipField(wireType: Int) {
        when (wireType) {
            0 -> readVarInt64()
            1 -> index = (index + 8).coerceAtMost(bytes.size)
            2 -> {
                val len = readVarInt32().coerceAtLeast(0)
                index = (index + len).coerceAtMost(bytes.size)
            }
            5 -> index = (index + 4).coerceAtMost(bytes.size)
            else -> index = bytes.size
        }
    }
}
