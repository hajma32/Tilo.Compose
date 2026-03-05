package eu.tilo.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.tilo.compose.map.mapFeatures
import eu.tilo.compose.render.MapRenderer
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import tilo.compose.core.feature.BaseStyle
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.MultiLineString
import tilo.compose.core.geometry.MultiPolygon
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon
import tilo.compose.core.map.MapSettings
import tilo.compose.core.map.MapState
import tilo.compose.core.tile.source.OSMSource
import tilo.compose.core.transform.Wgs84WebMercatorCoordinateSystem

private enum class TestScreen(val title: String) {
    MultipleLabelsTest("Multiple labels"),
    LineTest("Line"),
    PolygonTest("Polygon"),
    MultiLineStringTest("MultiLineString"),
    MultiPolygonTest("MultiPolygon"),
    PolygonMultiRingTest("Polygon (multi-ring)")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Preview
fun App() {
    val platform = remember { getPlatform() }
    val tileSource = remember { OSMSource(downloader = platform.tileDownloader) }

    var selectedScreen by remember { mutableStateOf(TestScreen.MultipleLabelsTest) }
    val drawerState = androidx.compose.material3.rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val mapState = remember(selectedScreen) {
        MapState(
            center = Point(14.421, 50.087),
            zoom = 6.0,
            settings = MapSettings(minZoom = 0.0, maxZoom = 20.0),
            coordSys = Wgs84WebMercatorCoordinateSystem
        )
    }

    val features = remember(selectedScreen) {
        when (selectedScreen) {
            TestScreen.MultipleLabelsTest -> buildMultipleLabelsTestFeatures()
            TestScreen.LineTest -> buildLineTestFeatures()
            TestScreen.PolygonTest -> buildPolygonTestFeatures()
            TestScreen.MultiLineStringTest -> buildMultiLineStringTestFeatures()
            TestScreen.MultiPolygonTest -> buildMultiPolygonTestFeatures()
            TestScreen.PolygonMultiRingTest -> buildPolygonMultiRingTestFeatures()
        }
    }

    MaterialTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Text(
                        text = "Tests",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                    )
                    TestScreen.entries.forEach { screen ->
                        NavigationDrawerItem(
                            label = { Text(screen.title) },
                            selected = selectedScreen == screen,
                            onClick = {
                                selectedScreen = screen
                                coroutineScope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Tilo.Compose") },
                        subtitle = { Text(selectedScreen.title) },
                        navigationIcon = {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                HamburgerIcon()
                            }
                        }
                    )
                }
            ) { innerPadding ->
                MapRenderer(
                    mapState = mapState,
                    features = features,
                    tileSource = tileSource,
                    tileImageDecoder = platform::tileImageDecoder,
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .background(Color(0xFFF6F8FA))
                )
            }
        }
    }
}

@Composable
private fun HamburgerIcon() {
    Column(
        modifier = Modifier.size(width = 20.dp, height = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color(0xFF111827))
            )
        }
    }
}

private fun buildMultipleLabelsTestFeatures(): List<Feature> {
    val lonMin = 12.0
    val lonMax = 19.0
    val latMin = 48.5
    val latMax = 51.2

    fun lerp(min: Double, max: Double, t: Double): Double = min + (max - min) * t

    return mapFeatures {
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
    }
}

private fun buildLineTestFeatures(): List<Feature> {
    val baseLon = 12.0
    val spanLon = 7.0
    val baseLat = 49.6
    val amplitudeLat = 0.9

    val linePoints = (0 until 100).map { i ->
        val t = i.toDouble() / 99.0
        val lon = baseLon + spanLon * t
        val lat = baseLat + sin(t * PI * 3.0) * amplitudeLat
        Point(lon, lat)
    }

    return mapFeatures {
        lineString(
            key = "line-test-100",
            points = linePoints,
            style = BaseStyle(strokeColor = 0xFF00ACC1, strokeWidth = 3.0)
        )
    }
}

private fun buildPolygonTestFeatures(): List<Feature> {
    val centerLon = 14.8
    val centerLat = 49.9
    val radiusLon = 1.2
    val radiusLat = 0.8

    val ringPoints = (0 until 99).map { i ->
        val angle = (2.0 * PI * i) / 99.0
        val lon = centerLon + cos(angle) * radiusLon
        val lat = centerLat + sin(angle) * radiusLat
        Point(lon, lat)
    }
    val closedRing = ringPoints + ringPoints.first()

    return mapFeatures {
        polygon(
            key = "polygon-test-100",
            rings = listOf(closedRing),
            style = BaseStyle(
                strokeColor = 0xFF3949AB,
                fillColor = 0x663949AB,
                strokeWidth = 2.0
            )
        )
    }
}

private fun buildMultiLineStringTestFeatures(): List<Feature> {
    val lineA = (0 until 100).map { i ->
        val t = i.toDouble() / 99.0
        Point(
            x = 12.0 + 7.0 * t,
            y = 49.2 + sin(t * PI * 2.0) * 0.45
        )
    }
    val lineB = (0 until 100).map { i ->
        val t = i.toDouble() / 99.0
        Point(
            x = 12.0 + 7.0 * t,
            y = 49.9 + sin(t * PI * 2.0 + (PI / 2.0)) * 0.45
        )
    }
    val lineC = (0 until 100).map { i ->
        val t = i.toDouble() / 99.0
        Point(
            x = 12.0 + 7.0 * t,
            y = 50.6 + sin(t * PI * 2.0 + PI) * 0.45
        )
    }

    return listOf(
        Feature(
            key = "multiline-test-100",
            geometry = MultiLineString(
                lines = listOf(
                    LineString(lineA),
                    LineString(lineB),
                    LineString(lineC)
                )
            ),
            style = BaseStyle(strokeColor = 0xFF00897B, strokeWidth = 2.5)
        )
    )
}

private fun buildMultiPolygonTestFeatures(): List<Feature> {
    fun ellipseRing(centerLon: Double, centerLat: Double, radiusLon: Double, radiusLat: Double): List<Point> {
        val ring = (0 until 99).map { i ->
            val angle = (2.0 * PI * i) / 99.0
            Point(
                x = centerLon + cos(angle) * radiusLon,
                y = centerLat + sin(angle) * radiusLat
            )
        }
        return ring + ring.first()
    }

    val polygonA = Polygon(rings = listOf(ellipseRing(14.2, 50.1, 0.55, 0.35)))
    val polygonB = Polygon(rings = listOf(ellipseRing(15.4, 49.7, 0.5, 0.3)))
    val polygonC = Polygon(rings = listOf(ellipseRing(13.2, 49.4, 0.45, 0.28)))

    return listOf(
        Feature(
            key = "multipolygon-test-100",
            geometry = MultiPolygon(polygons = listOf(polygonA, polygonB, polygonC)),
            style = BaseStyle(
                strokeColor = 0xFF5E35B1,
                fillColor = 0x665E35B1,
                strokeWidth = 2.0
            )
        )
    )
}

private fun buildPolygonMultiRingTestFeatures(): List<Feature> {
    fun ellipseRing(
        centerLon: Double,
        centerLat: Double,
        radiusLon: Double,
        radiusLat: Double,
        points: Int = 96,
        clockwise: Boolean
    ): List<Point> {
        val ring = (0 until points).map { i ->
            val direction = if (clockwise) -1.0 else 1.0
            val angle = direction * (2.0 * PI * i) / points.toDouble()
            Point(
                x = centerLon + cos(angle) * radiusLon,
                y = centerLat + sin(angle) * radiusLat
            )
        }
        return ring + ring.first()
    }

    val outer = ellipseRing(
        centerLon = 14.6,
        centerLat = 49.9,
        radiusLon = 1.2,
        radiusLat = 0.8,
        clockwise = false
    )
    val holeA = ellipseRing(
        centerLon = 14.2,
        centerLat = 49.9,
        radiusLon = 0.25,
        radiusLat = 0.18,
        clockwise = true
    )
    val holeB = ellipseRing(
        centerLon = 15.0,
        centerLat = 49.75,
        radiusLon = 0.22,
        radiusLat = 0.15,
        clockwise = true
    )

    return mapFeatures {
        polygon(
            key = "polygon-multi-ring-test",
            rings = listOf(outer, holeA, holeB),
            style = BaseStyle(
                strokeColor = 0xFF6D4C41,
                fillColor = 0x666D4C41,
                strokeWidth = 2.0
            )
        )
    }
}
