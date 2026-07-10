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
import tilo.compose.core.layers.Layer
import tilo.compose.core.layers.raster.createOrtofotoTileLayer
import tilo.compose.core.layers.vector.FeatureLayer
import tilo.compose.core.map.MapConfig
import tilo.compose.core.map.Map
import tilo.compose.core.projection.Epsg5514Projection
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.transform.Wgs84ToEpsg5514Transformation

private const val MAP_BACKGROUND_COLOR = 0xFFF2EEE3

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
    val tileLayer = remember {
        createOrtofotoTileLayer(id = "cuzk-ortofoto")
    }

    var selectedScreen by remember { mutableStateOf(TestScreen.MultipleLabelsTest) }
    val drawerState = androidx.compose.material3.rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val map = remember(selectedScreen) {
        Map(
            center = Wgs84ToEpsg5514Transformation.sourceToTarget(Point(16.6068, 49.1951)),
            zoom = 11.5,
            config = MapConfig(minZoom = 0.0, maxZoom = 20.0),
            projection = Epsg5514Projection
        )
    }

    val layers: List<Layer> = remember(selectedScreen) {
        listOf(
            tileLayer,
            FeatureLayer(
                id = "test-features",
                zIndex = 1,
                projection = Epsg4326Projection,
                features = buildTestFeatures(selectedScreen)
            )
        )
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
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .background(Color(MAP_BACKGROUND_COLOR))
                ) {
                    MapRenderer(
                        map = map,
                        layers = layers,
                        tileDecoder = ::decodeImageBitmap,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private fun buildTestFeatures(screen: TestScreen): List<Feature> = when (screen) {
    TestScreen.MultipleLabelsTest -> buildMultipleLabelsTestFeatures()
    TestScreen.LineTest -> buildLineTestFeatures()
    TestScreen.PolygonTest -> buildPolygonTestFeatures()
    TestScreen.MultiLineStringTest -> buildMultiLineStringTestFeatures()
    TestScreen.MultiPolygonTest -> buildMultiPolygonTestFeatures()
    TestScreen.PolygonMultiRingTest -> buildPolygonMultiRingTestFeatures()
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
    val lonMin = 12.0; val lonMax = 19.0; val latMin = 48.5; val latMax = 51.2
    fun lerp(min: Double, max: Double, t: Double) = min + (max - min) * t
    return mapFeatures {
        repeat(100) { i ->
            val t = (i + 1).toDouble() / 101.0
            val s = ((i * 7) % 100 + 1).toDouble() / 101.0
            point(
                key = "pt-$i",
                x = lerp(lonMin, lonMax, t),
                y = lerp(latMin, latMax, s),
                label = "Point ${i + 1}",
                style = BaseStyle(fillColor = 0xFFFF6D00, strokeWidth = 8.0)
            )
        }
    }
}

private fun buildLineTestFeatures(): List<Feature> {
    val linePoints = (0 until 100).map { i ->
        val t = i.toDouble() / 99.0
        Point(12.0 + 7.0 * t, 49.6 + sin(t * PI * 3.0) * 0.9)
    }
    return mapFeatures {
        lineString(key = "line-test", points = linePoints, style = BaseStyle(strokeColor = 0xFF00ACC1, strokeWidth = 3.0))
    }
}

private fun buildPolygonTestFeatures(): List<Feature> {
    val ring = (0 until 99).map { i ->
        val a = (2.0 * PI * i) / 99.0
        Point(14.8 + cos(a) * 1.2, 49.9 + sin(a) * 0.8)
    } + Point(14.8 + 1.2, 49.9)
    return mapFeatures {
        polygon(key = "polygon-test", rings = listOf(ring), style = BaseStyle(strokeColor = 0xFF3949AB, fillColor = 0x663949AB, strokeWidth = 2.0))
    }
}

private fun buildMultiLineStringTestFeatures(): List<Feature> {
    fun wave(baseLat: Double, phase: Double) = (0 until 100).map { i ->
        val t = i.toDouble() / 99.0
        Point(12.0 + 7.0 * t, baseLat + sin(t * PI * 2.0 + phase) * 0.45)
    }
    return listOf(Feature(
        key = "multiline-test",
        geometry = MultiLineString(lines = listOf(LineString(wave(49.2, 0.0)), LineString(wave(49.9, PI / 2)), LineString(wave(50.6, PI)))),
        style = BaseStyle(strokeColor = 0xFF00897B, strokeWidth = 2.5)
    ))
}

private fun buildMultiPolygonTestFeatures(): List<Feature> {
    fun ellipse(cx: Double, cy: Double, rx: Double, ry: Double): Polygon {
        val ring = (0 until 99).map { i ->
            val a = (2.0 * PI * i) / 99.0
            Point(cx + cos(a) * rx, cy + sin(a) * ry)
        } + Point(cx + rx, cy)
        return Polygon(rings = listOf(ring))
    }
    return listOf(Feature(
        key = "multipolygon-test",
        geometry = MultiPolygon(polygons = listOf(ellipse(14.2, 50.1, 0.55, 0.35), ellipse(15.4, 49.7, 0.5, 0.3), ellipse(13.2, 49.4, 0.45, 0.28))),
        style = BaseStyle(strokeColor = 0xFF5E35B1, fillColor = 0x665E35B1, strokeWidth = 2.0)
    ))
}

private fun buildPolygonMultiRingTestFeatures(): List<Feature> {
    fun ring(cx: Double, cy: Double, rx: Double, ry: Double, cw: Boolean, n: Int = 96): List<Point> {
        val dir = if (cw) -1.0 else 1.0
        val pts = (0 until n).map { i -> Point(cx + cos(dir * 2.0 * PI * i / n) * rx, cy + sin(dir * 2.0 * PI * i / n) * ry) }
        return pts + pts.first()
    }
    return mapFeatures {
        polygon(
            key = "polygon-multiring-test",
            rings = listOf(
                ring(14.6, 49.9, 1.2, 0.8, false),
                ring(14.2, 49.9, 0.25, 0.18, true),
                ring(15.0, 49.75, 0.22, 0.15, true)
            ),
            style = BaseStyle(strokeColor = 0xFF6D4C41, fillColor = 0x666D4C41, strokeWidth = 2.0)
        )
    }
}
