package tilo.compose.core.tile.source

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan
import tilo.compose.core.map.Viewport
import tilo.compose.core.tile.Tile
import tilo.compose.core.tile.TileCoordinate

/**
 * Downloader abstraction so Source can optionally fetch bytes.
 * Keep this simple and platform-agnostic in core.
 */
fun interface TileDownloader {
    fun download(url: String): ByteArray?
}

/**
 * OSM source implemented as WMS GetMap requests over EPSG:3857.
 *
 * Note: OSM is commonly consumed as XYZ. This implementation intentionally uses WMS URL building
 * to satisfy WMS-style source requirements.
 */
class OSMSource(
    private val wmsBaseUrl: String = "https://ows.terrestris.de/osm/service",
    private val layers: String = "OSM-WMS",
    private var centerLat: Double = 0.0,
    private var centerLon: Double = 0.0,
    val tileSizePx: Int = 256,
    private val downloader: TileDownloader? = null
) : Source {

    fun setCenter(lat: Double, lon: Double) {
        centerLat = lat
        centerLon = lon
    }

    override fun getTiles(zoomLevel: Int, viewport: Viewport, tileCount: Int): List<Tile> {
        require(zoomLevel >= 0) { "zoomLevel must be >= 0" }
        require(tileCount > 0) { "tileCount must be > 0" }

        val z = zoomLevel
        val centerX = lonToTileX(centerLon, z)
        val centerY = latToTileY(centerLat, z)

        // Use ceil so the square grid can always provide at least tileCount entries.
        val gridSide = max(1, ceil(sqrt(tileCount.toDouble())).toInt())
        val radius = gridSide / 2
        val maxIndex = (2.0.pow(z.toDouble()).toInt() - 1)

        val tiles = mutableListOf<Tile>()

        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (tiles.size >= tileCount) break

                val rawX = centerX + dx
                val x = wrapX(rawX, z)
                val y = min(max(centerY + dy, 0), maxIndex)

                val coord = TileCoordinate(z = z, x = x, y = y)
                val bbox = tileBboxWebMercator(x, y, z)
                val url = buildWmsUrl(bbox, tileSizePx, tileSizePx)
                val bytes = downloader?.download(url)

                tiles += Tile(
                    coordinate = coord,
                    url = url,
                    bytes = bytes
                )
            }
        }

        return tiles
    }

    private fun buildWmsUrl(bbox: BBox3857, width: Int, height: Int): String {
        return buildString {
            append(wmsBaseUrl)
            append(if (wmsBaseUrl.contains("?")) "&" else "?")
            append("SERVICE=WMS")
            append("&REQUEST=GetMap")
            append("&VERSION=1.1.1")
            append("&LAYERS=$layers")
            append("&STYLES=")
            append("&FORMAT=image/png")
            append("&TRANSPARENT=FALSE")
            append("&SRS=EPSG:3857")
            append("&WIDTH=$width")
            append("&HEIGHT=$height")
            append("&BBOX=${bbox.minX},${bbox.minY},${bbox.maxX},${bbox.maxY}")
        }
    }

    private fun lonToTileX(lon: Double, z: Int): Int {
        val n = 2.0.pow(z.toDouble())
        return floor((lon + 180.0) / 360.0 * n).toInt()
    }

    private fun latToTileY(lat: Double, z: Int): Int {
        val latRad = lat * PI / 180.0
        val n = 2.0.pow(z.toDouble())
        return floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()
    }

    private fun wrapX(x: Int, z: Int): Int {
        val n = 2.0.pow(z.toDouble()).toInt()
        val mod = x % n
        return if (mod < 0) mod + n else mod
    }

    private data class BBox3857(
        val minX: Double,
        val minY: Double,
        val maxX: Double,
        val maxY: Double
    )

    private fun tileBboxWebMercator(x: Int, y: Int, z: Int): BBox3857 {
        val n = 2.0.pow(z.toDouble())

        val lonLeft = x / n * 360.0 - 180.0
        val lonRight = (x + 1.0) / n * 360.0 - 180.0

        val latTop = tileYToLat(y.toDouble(), n)
        val latBottom = tileYToLat(y + 1.0, n)

        val left = lonToMercX(lonLeft)
        val right = lonToMercX(lonRight)
        val top = latToMercY(latTop)
        val bottom = latToMercY(latBottom)

        return BBox3857(minX = left, minY = bottom, maxX = right, maxY = top)
    }

    private fun tileYToLat(y: Double, n: Double): Double {
        val t = PI * (1.0 - 2.0 * y / n)
        return atan(kotlin.math.sinh(t)) * 180.0 / PI
    }

    private fun lonToMercX(lon: Double): Double = lon * 20037508.34 / 180.0

    private fun latToMercY(lat: Double): Double {
        val y = ln(tan((90.0 + lat) * PI / 360.0)) / (PI / 180.0)
        return y * 20037508.34 / 180.0
    }
}
