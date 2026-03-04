package eu.tilo.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import eu.tilo.compose.map.mapFeatures
import eu.tilo.compose.render.MapRenderer
import tilo.compose.core.feature.BaseStyle
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapSettings
import tilo.compose.core.map.MapState
import tilo.compose.core.tile.source.OSMSource
import tilo.compose.core.transform.Wgs84WebMercatorCoordinateSystem

@Composable
@Preview
fun App() {
    val platform = remember { getPlatform() }

    val mapState = remember {
        MapState(
            // Prague in lon/lat (WGS84)
            center = Point(14.421, 50.087),
            zoom = 6.0,
            settings = MapSettings(minZoom = 0.0, maxZoom = 20.0),
            coordSys = Wgs84WebMercatorCoordinateSystem
        )
    }

    val tileSource = remember { OSMSource(downloader = platform.tileDownloader) }

    val features = remember {
        mapFeatures {
            // Czech Republic area envelope (rough): lon 12.0..19.0, lat 48.5..51.2
            val lonMin = 12.0
            val lonMax = 19.0
            val latMin = 48.5
            val latMax = 51.2

            fun lerp(min: Double, max: Double, t: Double): Double = min + (max - min) * t

            repeat(100) { i ->
                val t = (i + 1).toDouble() / 101.0
                val s = ((i * 7) % 100 + 1).toDouble() / 101.0
                val lon = lerp(lonMin, lonMax, t)
                val lat = lerp(latMin, latMax, s)

                point(
                    key = "pt-$i",
                    x = lon,
                    y = lat,
                    label = "Point ${i + 1}",
                    style = BaseStyle(fillColor = 0xFFFF6D00, strokeWidth = 8.0)
                )
            }

            repeat(15) { i ->
                val t = (i + 1).toDouble() / 16.0
                val lonA = lerp(lonMin + 0.2, lonMax - 0.6, t)
                val latA = lerp(latMin + 0.2, latMax - 0.6, ((i * 5) % 15 + 1).toDouble() / 16.0)

                val p1 = Point(lonA, latA)
                val p2 = Point((lonA + 0.45).coerceAtMost(lonMax - 0.1), (latA + 0.15).coerceAtMost(latMax - 0.1))
                val p3 = Point((lonA + 0.9).coerceAtMost(lonMax - 0.05), (latA - 0.2).coerceAtLeast(latMin + 0.05))

                lineString(
                    key = "line-$i",
                    points = listOf(p1, p2, p3),
                    style = BaseStyle(strokeColor = 0xFF00ACC1, strokeWidth = 3.0)
                )
            }

            repeat(15) { i ->
                val t = (i + 1).toDouble() / 16.0
                val cx = lerp(lonMin + 0.35, lonMax - 0.35, t)
                val cy = lerp(latMin + 0.25, latMax - 0.25, ((i * 9) % 15 + 1).toDouble() / 16.0)

                val halfW = 0.12
                val halfH = 0.08
                val left = (cx - halfW).coerceAtLeast(lonMin + 0.02)
                val right = (cx + halfW).coerceAtMost(lonMax - 0.02)
                val bottom = (cy - halfH).coerceAtLeast(latMin + 0.02)
                val top = (cy + halfH).coerceAtMost(latMax - 0.02)

                polygon(
                    key = "poly-$i",
                    rings = listOf(
                        listOf(
                            Point(left, bottom),
                            Point(right, bottom),
                            Point(right, top),
                            Point(left, top),
                            Point(left, bottom)
                        )
                    ),
                    style = BaseStyle(
                        strokeColor = 0xFF3949AB,
                        fillColor = 0x663949AB,
                        strokeWidth = 2.0
                    )
                )
            }
        }
    }

    MaterialTheme {
        MapRenderer(
            mapState = mapState,
            features = features,
            tileSource = tileSource,
            tileImageDecoder = platform::tileImageDecoder,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF6F8FA))
        )
    }
}
