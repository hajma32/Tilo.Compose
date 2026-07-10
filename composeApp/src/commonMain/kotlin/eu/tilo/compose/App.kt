package eu.tilo.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.tilo.compose.map.mapFeatures
import eu.tilo.compose.render.Map
import eu.tilo.compose.render.MapLayerBuilder
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import tilo.compose.core.feature.ColorValue
import tilo.compose.core.feature.DashPattern
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.FillPattern
import tilo.compose.core.feature.FillStyle
import tilo.compose.core.feature.LineCap
import tilo.compose.core.feature.LineJoin
import tilo.compose.core.feature.LineStyle
import tilo.compose.core.feature.PointShape
import tilo.compose.core.feature.PointStyle
import tilo.compose.core.feature.PolygonStyle
import tilo.compose.core.feature.StrokeStyle
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.MultiLineString
import tilo.compose.core.geometry.MultiPolygon
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon
import tilo.compose.core.layers.raster.createOrtofotoTileLayer
import tilo.compose.core.layers.vector.FeatureLayer
import tilo.compose.core.layers.vector.VectorRenderStrategy
import tilo.compose.core.map.MapConfig
import tilo.compose.core.projection.Epsg5514Projection
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.transform.Epsg5514ToWgs84Transformation
import tilo.compose.core.transform.Wgs84ToEpsg5514Transformation
import tilo.compose.core.map.Map as MapState
import tilo.compose.draw.DrawLayer
import tilo.compose.draw.DrawMode
import tilo.compose.draw.DrawState

private const val MAP_BACKGROUND_COLOR = 0xFFF2EEE3

private fun color(argb: Long): ColorValue =
    ColorValue((argb and 0xFFFFFFFFL).toULong())

private enum class TestScreen(val title: String) {
    StylingTest("Styling"),
    MultipleLabelsTest("Multiple labels"),
    LineTest("Line"),
    PolygonTest("Polygon"),
    MultiLineStringTest("MultiLineString"),
    MultiPolygonTest("MultiPolygon"),
    PolygonMultiRingTest("Polygon (multi-ring)")
}

private enum class DemoLayerOption(val title: String) {
    CuzkOrtofoto("CUZK ortofoto"),
    DashedPolygons("Dashed polygons"),
    FullLines("Full lines"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Preview
fun App() {
    val ortofotoLayer = remember {
        createOrtofotoTileLayer(id = "cuzk-ortofoto")
    }
    val dashedPolygonsLayer = remember {
        FeatureLayer(
            id = "dashed-polygons",
            zIndex = 1,
            projection = Epsg4326Projection,
            features = buildDashedPolygonLayerFeatures(),
            renderStrategy = VectorRenderStrategy.CachedBitmap(
                scale = 1.5,
                paddingPx = 192,
                invalidateOnZoomDelta = 0.35,
            ),
        )
    }
    val fullLinesLayer = remember {
        FeatureLayer(
            id = "full-lines",
            zIndex = 2,
            projection = Epsg4326Projection,
            features = buildFullLineLayerFeatures(),
            renderStrategy = VectorRenderStrategy.Immediate,
        )
    }

    var savedDrawingFeatures by remember { mutableStateOf<List<Feature>>(emptyList()) }
    val drawState = remember {
        DrawState(
            onSave = { feature ->
                savedDrawingFeatures = savedDrawingFeatures + feature.withSavedDrawingStyle()
            },
        )
    }

    val savedDrawingsLayer = remember(savedDrawingFeatures) {
        FeatureLayer(
            id = "saved-drawings",
            zIndex = 10,
            projection = Epsg5514Projection,
            features = savedDrawingFeatures,
            renderStrategy = VectorRenderStrategy.Immediate,
        )
    }

    var selectedLayers by remember {
        mutableStateOf(
            setOf(
                DemoLayerOption.CuzkOrtofoto,
                DemoLayerOption.DashedPolygons,
                DemoLayerOption.FullLines,
            )
        )
    }
    val drawerState = androidx.compose.material3.rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val map = remember {
        MapState(
            center = Wgs84ToEpsg5514Transformation.sourceToTarget(Point(16.6068, 49.1951)),
            zoom = 11.5,
            config = MapConfig(minZoom = 0.0, maxZoom = 20.0)
                .withTransformation(Wgs84ToEpsg5514Transformation)
                .withTransformation(Epsg5514ToWgs84Transformation),
            projection = Epsg5514Projection
        )
    }

    fun MapLayerBuilder.CuzkOrtofoto() {
        layer(ortofotoLayer)
    }

    fun MapLayerBuilder.DashedPolygonsLayer() {
        layer(dashedPolygonsLayer)
    }

    fun MapLayerBuilder.FullLinesLayer() {
        layer(fullLinesLayer)
    }

    fun MapLayerBuilder.DrawLayer() {
        layer(DrawLayer(state = drawState, projection = Epsg5514Projection))
    }

    fun MapLayerBuilder.SavedDrawingsLayer() {
        layer(savedDrawingsLayer)
    }

    MaterialTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Text(
                        text = "Layers",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                    )
                    DemoLayerOption.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = option in selectedLayers,
                                onCheckedChange = { checked ->
                                    selectedLayers = if (checked) {
                                        selectedLayers + option
                                    } else {
                                        selectedLayers - option
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option.title)
                        }
                    }
                    NavigationDrawerItem(
                        label = { Text("Close") },
                        selected = false,
                        onClick = { coroutineScope.launch { drawerState.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Tilo.Compose") },
                        subtitle = { Text("Layer composition") },
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
                    Map(
                        state = map,
                        tileDecoder = ::decodeImageBitmap,
                        modifier = Modifier.fillMaxSize(),
                        onTapWorld = drawState::onMapTap,
                        invalidationKey = drawState.revision,
                        layers = {
                            if (DemoLayerOption.CuzkOrtofoto in selectedLayers) CuzkOrtofoto()
                            if (DemoLayerOption.DashedPolygons in selectedLayers) DashedPolygonsLayer()
                            if (DemoLayerOption.FullLines in selectedLayers) FullLinesLayer()
                            SavedDrawingsLayer()
                            DrawLayer()
                        },
                    )
                    DrawingControls(
                        state = drawState,
                    )
                }
            }
        }
    }
}

private fun buildTestFeatures(screen: TestScreen): List<Feature> = when (screen) {
    TestScreen.StylingTest -> buildStylingTestFeatures()
    TestScreen.MultipleLabelsTest -> buildMultipleLabelsTestFeatures()
    TestScreen.LineTest -> buildLineTestFeatures()
    TestScreen.PolygonTest -> buildPolygonTestFeatures()
    TestScreen.MultiLineStringTest -> buildMultiLineStringTestFeatures()
    TestScreen.MultiPolygonTest -> buildMultiPolygonTestFeatures()
    TestScreen.PolygonMultiRingTest -> buildPolygonMultiRingTestFeatures()
}

private fun TestScreen.vectorRenderStrategy(): VectorRenderStrategy =
    when (this) {
        TestScreen.StylingTest -> VectorRenderStrategy.CachedBitmap(
            scale = 1.5,
            paddingPx = 192,
            invalidateOnZoomDelta = 0.35,
        )
        else -> VectorRenderStrategy.Immediate
    }

@Composable
private fun BoxScope.DrawingControls(
    state: DrawState,
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.isDrawing) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DrawMode.entries.forEach { mode ->
                            if (mode == state.mode) {
                                Button(onClick = { state.selectMode(mode) }) {
                                    Text(mode.title())
                                }
                            } else {
                                OutlinedButton(onClick = { state.selectMode(mode) }) {
                                    Text(mode.title())
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { state.undo() },
                            enabled = state.canUndo,
                        ) {
                            Text("Undo")
                        }
                        OutlinedButton(
                            onClick = { state.redo() },
                            enabled = state.canRedo,
                        ) {
                            Text("Redo")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { state.save() },
                            enabled = state.canSave,
                        ) {
                            Text("Save")
                        }
                        OutlinedButton(onClick = { state.clear() }) {
                            Text("Clear")
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { state.toggleDrawing() }) {
            Text(if (state.isDrawing) "Done" else "Draw")
        }
    }
}

private fun DrawMode.title(): String =
    name.lowercase().replaceFirstChar { char -> char.uppercase() }

private fun Feature.withSavedDrawingStyle(): Feature =
    copy(
        style = when (geometry) {
            is Point -> PointStyle(
                shape = PointShape.Circle,
                size = 14.0,
                fill = FillStyle(color = color(0xFF43A047)),
                stroke = StrokeStyle(color = color(0xFF263238), width = 2.0),
            )
            is LineString -> LineStyle(
                stroke = StrokeStyle(
                    color = color(0xFF43A047),
                    width = 4.0,
                    lineCap = LineCap.Round,
                    lineJoin = LineJoin.Round,
                )
            )
            is Polygon -> PolygonStyle(
                fill = FillStyle(color = color(0x5543A047)),
                stroke = StrokeStyle(
                    color = color(0xFF2E7D32),
                    width = 3.0,
                    lineJoin = LineJoin.Round,
                )
            )
            else -> style
        }
    )

private fun buildDashedPolygonLayerFeatures(): List<Feature> {
    fun ring(cx: Double, cy: Double, rx: Double, ry: Double, n: Int = 72): List<Point> {
        val points = (0 until n).map { i ->
            val angle = 2.0 * PI * i / n
            Point(cx + cos(angle) * rx, cy + sin(angle) * ry)
        }
        return points + points.first()
    }

    return listOf(
        Feature(
            key = "dashed-polygon-west",
            geometry = Polygon(rings = listOf(ring(14.4, 49.75, 0.85, 0.45))),
            label = "Dashed polygon",
            style = PolygonStyle(
                fill = FillStyle(
                    color = color(0x3326A69A),
                    pattern = FillPattern.Hatch(
                        angleDegrees = 35.0,
                        spacing = 10.0,
                        stroke = StrokeStyle(color = color(0xFF00796B), width = 1.2)
                    )
                ),
                stroke = StrokeStyle(
                    color = color(0xFF004D40),
                    width = 3.0,
                    lineJoin = LineJoin.Round,
                    dash = DashPattern(listOf(18.0, 8.0)),
                )
            )
        ),
        Feature(
            key = "dashed-polygon-east",
            geometry = Polygon(rings = listOf(ring(16.1, 49.95, 0.75, 0.4))),
            label = "Pattern fill",
            style = PolygonStyle(
                fill = FillStyle(
                    color = color(0x33AB47BC),
                    pattern = FillPattern.Dots(
                        spacing = 12.0,
                        radius = 2.0,
                        color = color(0xFF8E24AA),
                    )
                ),
                stroke = StrokeStyle(
                    color = color(0xFF6A1B9A),
                    width = 2.5,
                    dash = DashPattern(listOf(12.0, 7.0)),
                )
            )
        ),
    )
}

private fun buildFullLineLayerFeatures(): List<Feature> {
    fun wave(key: String, baseLat: Double, strokeArgb: Long, phase: Double): Feature {
        val points = (0 until 120).map { i ->
            val t = i.toDouble() / 119.0
            Point(12.4 + 5.8 * t, baseLat + sin(t * PI * 2.5 + phase) * 0.28)
        }
        return Feature(
            key = key,
            geometry = LineString(points),
            label = key,
            style = LineStyle(
                stroke = StrokeStyle(
                    color = color(strokeArgb),
                    width = 4.0,
                    lineCap = LineCap.Round,
                    lineJoin = LineJoin.Round,
                )
            )
        )
    }

    return listOf(
        wave("Full line A", 50.35, 0xFFE53935, 0.0),
        wave("Full line B", 49.25, 0xFF1E88E5, PI / 2.0),
    )
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

private fun buildStylingTestFeatures(): List<Feature> {
    fun ring(cx: Double, cy: Double, rx: Double, ry: Double, n: Int = 72): List<Point> {
        val points = (0 until n).map { i ->
            val angle = 2.0 * PI * i / n
            Point(cx + cos(angle) * rx, cy + sin(angle) * ry)
        }
        return points + points.first()
    }

    val dashedLine = (0 until 80).map { i ->
        val t = i.toDouble() / 79.0
        Point(12.4 + 5.6 * t, 50.5 + sin(t * PI * 2.5) * 0.25)
    }

    val pointShapes = PointShape.entries.mapIndexed { index, shape ->
        Feature(
            key = "style-point-$shape",
            geometry = Point(13.0 + index * 1.0, 48.95),
            label = shape.name,
            style = PointStyle(
                shape = shape,
                size = 18.0,
                fill = FillStyle(color = color(0xFFFFC107)),
                stroke = StrokeStyle(color = color(0xFF263238), width = 2.0)
            )
        )
    }

    return listOf(
        Feature(
            key = "style-hatch-polygon",
            geometry = Polygon(rings = listOf(ring(14.0, 49.85, 1.0, 0.55))),
            label = "Hatch fill",
            style = PolygonStyle(
                fill = FillStyle(
                    color = color(0x3326A69A),
                    pattern = FillPattern.Hatch(
                        angleDegrees = 35.0,
                        spacing = 10.0,
                        stroke = StrokeStyle(color = color(0xFF00796B), width = 1.2)
                    )
                ),
                stroke = StrokeStyle(
                    color = color(0xFF004D40),
                    width = 3.0,
                    lineJoin = LineJoin.Round,
                )
            )
        ),
        Feature(
            key = "style-dots-polygon",
            geometry = Polygon(rings = listOf(ring(16.2, 49.7, 0.85, 0.45))),
            label = "Dots fill",
            style = PolygonStyle(
                fill = FillStyle(
                    color = color(0x33AB47BC),
                    pattern = FillPattern.Dots(
                        spacing = 12.0,
                        radius = 2.0,
                        color = color(0xFF8E24AA),
                    )
                ),
                stroke = StrokeStyle(
                    color = color(0xFF6A1B9A),
                    width = 2.0,
                    dash = DashPattern(listOf(14.0, 8.0)),
                )
            )
        ),
        Feature(
            key = "style-dashed-line",
            geometry = LineString(dashedLine),
            label = "Dashed round line",
            style = LineStyle(
                stroke = StrokeStyle(
                    color = color(0xFFE53935),
                    width = 5.0,
                    lineCap = LineCap.Round,
                    lineJoin = LineJoin.Round,
                    dash = DashPattern(listOf(18.0, 10.0, 4.0, 10.0)),
                )
            )
        )
    ) + pointShapes
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
                style = PointStyle(
                    size = 8.0,
                    fill = FillStyle(color = color(0xFFFF6D00)),
                    stroke = null,
                )
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
        lineString(
            key = "line-test",
            points = linePoints,
            style = LineStyle(stroke = StrokeStyle(color = color(0xFF00ACC1), width = 3.0))
        )
    }
}

private fun buildPolygonTestFeatures(): List<Feature> {
    val ring = (0 until 99).map { i ->
        val a = (2.0 * PI * i) / 99.0
        Point(14.8 + cos(a) * 1.2, 49.9 + sin(a) * 0.8)
    } + Point(14.8 + 1.2, 49.9)
    return mapFeatures {
        polygon(
            key = "polygon-test",
            rings = listOf(ring),
            style = PolygonStyle(
                fill = FillStyle(color = color(0x663949AB)),
                stroke = StrokeStyle(color = color(0xFF3949AB), width = 2.0)
            )
        )
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
        style = LineStyle(stroke = StrokeStyle(color = color(0xFF00897B), width = 2.5))
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
        style = PolygonStyle(
            fill = FillStyle(color = color(0x665E35B1)),
            stroke = StrokeStyle(color = color(0xFF5E35B1), width = 2.0)
        )
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
            style = PolygonStyle(
                fill = FillStyle(color = color(0x666D4C41)),
                stroke = StrokeStyle(color = color(0xFF6D4C41), width = 2.0)
            )
        )
    }
}
