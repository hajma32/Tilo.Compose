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
import androidx.compose.material3.RadioButton
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
import tilo.compose.dsl.MapCameraState
import tilo.compose.dsl.TiloMap
import tilo.compose.dsl.WMSLayerState
import tilo.compose.dsl.attribution
import tilo.compose.dsl.cachedBitmap
import tilo.compose.dsl.FeatureOptions
import tilo.compose.dsl.featureLayerStyle
import tilo.compose.dsl.features
import tilo.compose.dsl.lineStyle
import tilo.compose.dsl.pointStyle
import tilo.compose.dsl.polygonStyle
import tilo.compose.dsl.rememberMapCameraState
import tilo.compose.dsl.rememberWMSLayer
import tilo.compose.dsl.sjtsk
import tilo.compose.dsl.webMercator
import tilo.compose.dsl.wgs84
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import tilo.compose.core.feature.Data
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.LineCap
import tilo.compose.core.feature.LineJoin
import tilo.compose.core.feature.PointShape
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon
import tilo.compose.core.map.MapConfig
import tilo.compose.core.selection.FeatureSelectionRef
import tilo.compose.core.transform.Epsg5514ToWgs84Transformation
import tilo.compose.core.transform.WebMercatorToWgs84Transformation
import tilo.compose.core.transform.Wgs84ToEpsg5514Transformation
import tilo.compose.core.transform.Wgs84ToWebMercatorTransformation
import tilo.compose.draw.DrawMode
import tilo.compose.draw.DrawState
import tilo.compose.draw.drawLayer
import tilo.compose.draw.rememberDrawState

private const val MAP_BACKGROUND_COLOR = 0xFFF2EEE3
private const val CUZK_ORTOFOTO_WMS_URL = "https://ags.cuzk.gov.cz/arcgis1/services/ORTOFOTO/MapServer/WMSServer"
private const val CUZK_ZTM_WMS_URL = "https://ags.cuzk.gov.cz/arcgis1/services/ZTM/MapServer/WMSServer"
private const val OSM_XYZ_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"

private enum class MapDemo(val title: String, val subtitle: String) {
    Sjtks("S-JTSK / CUZK", "EPSG:5514 WMS + vectors"),
    WebMercatorXyz("Web Mercator / XYZ", "EPSG:3857 XYZ tiles"),
}

private enum class BasemapOption(val title: String) {
    CuzkOrtofoto("CUZK ortofoto"),
    CuzkZtm("CUZK basic map"),
}

private enum class DemoLayerOption(val title: String) {
    DashedPolygons("Dashed polygons"),
    FullLines("Full lines"),
}

private data class PlaceDetails(
    val name: String,
    val description: String,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Preview
fun App() {
    val ortofotoLayer = rememberWMSLayer(
        id = "cuzk-ortofoto",
        capabilitiesUrl = CUZK_ORTOFOTO_WMS_URL,
        layerName = "0",
        projection = sjtsk(),
        format = "image/jpeg",
    )
    val ztmLayer = rememberWMSLayer(
        id = "cuzk-ztm",
        capabilitiesUrl = CUZK_ZTM_WMS_URL,
        layerName = "0",
        projection = sjtsk(),
        format = "image/png",
    )
    val dashedPolygonFeatures = remember { buildDashedPolygonLayerFeatures() }
    val fullLineFeatures = remember { buildFullLineLayerFeatures() }
    val mercatorPlaceFeatures = remember { buildMercatorPlaceFeatures() }

    var savedDrawingFeatures by remember { mutableStateOf<List<Feature>>(emptyList()) }
    val drawState = rememberDrawState(
        onSave = { feature ->
            savedDrawingFeatures = savedDrawingFeatures + feature.withSavedDrawingStyle()
        },
    )

    var selectedDemo by remember { mutableStateOf(MapDemo.Sjtks) }
    var selectedBasemap by remember { mutableStateOf(BasemapOption.CuzkOrtofoto) }
    var selectedLayers by remember {
        mutableStateOf(
            setOf(
                DemoLayerOption.DashedPolygons,
                DemoLayerOption.FullLines,
            )
        )
    }
    val drawerState = androidx.compose.material3.rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val sjtskCameraState = rememberMapCameraState(
        center = Wgs84ToEpsg5514Transformation.sourceToTarget(Point(16.6068, 49.1951)),
        zoom = 11.5,
        config = MapConfig(minZoom = 0.0, maxZoom = 20.0)
            .withTransformation(Wgs84ToEpsg5514Transformation)
            .withTransformation(Epsg5514ToWgs84Transformation),
        projection = sjtsk(),
    )
    val mercatorCameraState = rememberMapCameraState(
        center = Wgs84ToWebMercatorTransformation.sourceToTarget(Point(14.4378, 50.0755)),
        zoom = 11.5,
        config = MapConfig(minZoom = 0.0, maxZoom = 20.0)
            .withTransformation(Wgs84ToWebMercatorTransformation)
            .withTransformation(WebMercatorToWgs84Transformation),
        projection = webMercator(),
    )

    MaterialTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Text(
                        text = "Demo",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                    )
                    MapDemo.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedDemo == option,
                                onClick = { selectedDemo = option },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(option.title)
                                Text(
                                    text = option.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (selectedDemo == MapDemo.Sjtks) {
                        Text(
                            text = "Basemap",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                        )
                        BasemapOption.entries.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selectedBasemap == option,
                                    onClick = { selectedBasemap = option },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(option.title)
                            }
                        }
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
                        subtitle = { Text(selectedDemo.subtitle) },
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
                    when (selectedDemo) {
                        MapDemo.Sjtks -> {
                            SjtksShowcaseMap(
                                cameraState = sjtskCameraState,
                                selectedBasemap = selectedBasemap,
                                ortofotoLayer = ortofotoLayer,
                                ztmLayer = ztmLayer,
                                selectedLayers = selectedLayers,
                                dashedPolygonFeatures = dashedPolygonFeatures,
                                fullLineFeatures = fullLineFeatures,
                                savedDrawingFeatures = savedDrawingFeatures,
                                drawState = drawState,
                            )
                            DrawingControls(state = drawState)
                        }
                        MapDemo.WebMercatorXyz -> {
                            WebMercatorXyzExampleMap(
                                cameraState = mercatorCameraState,
                                places = mercatorPlaceFeatures,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SjtksShowcaseMap(
    cameraState: MapCameraState,
    selectedBasemap: BasemapOption,
    ortofotoLayer: WMSLayerState,
    ztmLayer: WMSLayerState,
    selectedLayers: Set<DemoLayerOption>,
    dashedPolygonFeatures: List<Feature>,
    fullLineFeatures: List<Feature>,
    savedDrawingFeatures: List<Feature>,
    drawState: DrawState,
) {
    TiloMap(
        cameraState = cameraState,
        modifier = Modifier.fillMaxSize(),
        onTapWorld = drawState::onMapTap,
        invalidationKey = drawState.revision,
        layers = {
            when (selectedBasemap) {
                BasemapOption.CuzkOrtofoto -> wmsTileLayer(ortofotoLayer)
                BasemapOption.CuzkZtm -> wmsTileLayer(ztmLayer)
            }
            if (DemoLayerOption.DashedPolygons in selectedLayers) {
                featureLayer("dashed-polygons", dashedPolygonFeatures) {
                    zIndex = 1
                    projection = wgs84()
                    renderMode = cachedBitmap(
                        scale = 1.5,
                        paddingPx = 192,
                        invalidateOnZoomDelta = 0.35,
                    )
                }
            }
            if (DemoLayerOption.FullLines in selectedLayers) {
                featureLayer("full-lines", fullLineFeatures) {
                    zIndex = 2
                    projection = wgs84()
                }
            }
            featureLayer("saved-drawings", savedDrawingFeatures) {
                zIndex = 10
                projection = sjtsk()
            }
            drawLayer(state = drawState, projection = sjtsk())
        },
    )
}

@Composable
private fun BoxScope.WebMercatorXyzExampleMap(
    cameraState: MapCameraState,
    places: List<Feature>,
) {
    var selectedPlace by remember { mutableStateOf<PlaceDetails?>(null) }
    var selectedPlaceRefs by remember { mutableStateOf<Set<FeatureSelectionRef>>(emptySet()) }

    TiloMap(
        cameraState = cameraState,
        modifier = Modifier.fillMaxSize(),
        onFeatureSelect = { selections ->
            selectedPlaceRefs = selections.map { it.ref }.toSet()
            selectedPlace = selections.firstNotNullOfOrNull { selection ->
                selection.feature.data?.payload as? PlaceDetails
            }
        },
        selectedFeatures = selectedPlaceRefs,
        layers = {
            xyzTileLayer(
                id = "osm-standard",
                urlTemplate = OSM_XYZ_URL,
                projection = webMercator(),
                maxVisibleTiles = 9,
                prefetchMargin = 1,
                attribution = attribution(
                    label = "© OpenStreetMap contributors",
                    url = "https://www.openstreetmap.org/copyright",
                ),
            )
            featureLayer("mercator-places", places) {
                zIndex = 1
                projection = wgs84()
                style = featureLayerStyle {
                    point {
                        shape = PointShape.Circle
                        size = 16.0
                        fill(0xFFE53935)
                        stroke(0xFFFFFFFF, width = 3.0)
                    }
                    line {
                        stroke(0xFF7E57C2, width = 5.0) {
                            lineCap = LineCap.Round
                            lineJoin = LineJoin.Round
                        }
                    }
                    polygon {
                        fill(0x3343A047)
                        stroke(0xFF2E7D32, width = 3.0) {
                            lineJoin = LineJoin.Round
                        }
                    }
                    label(0xFF111827)
                    selectedPoint {
                        shape = PointShape.Circle
                        size = 26.0
                        fill(0xFFFFD54F)
                        stroke(0xFF111827, width = 4.0)
                    }
                    selectedLine {
                        stroke(0xFFFFD54F, width = 9.0) {
                            lineCap = LineCap.Round
                            lineJoin = LineJoin.Round
                        }
                    }
                    selectedPolygon {
                        fill(0x55FFD54F)
                        stroke(0xFFFFD54F, width = 6.0) {
                            lineJoin = LineJoin.Round
                        }
                    }
                    selectedLabel(0xFF111827)
                }
            }
        },
    )
    selectedPlace?.let { place ->
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = place.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    Surface(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(16.dp),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("XYZ / Web Mercator")
            Text(
                text = "EPSG:3857 camera, OSM XYZ tiles, WGS84 features",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
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
            is Point -> pointStyle {
                shape = PointShape.Circle
                size = 14.0
                fill(0xFF43A047)
                stroke(0xFF263238, width = 2.0)
            }
            is LineString -> lineStyle {
                stroke(0xFF43A047, width = 4.0) {
                    lineCap = LineCap.Round
                    lineJoin = LineJoin.Round
                }
            }
            is Polygon -> polygonStyle {
                fill(0x5543A047)
                stroke(0xFF2E7D32, width = 3.0) {
                    lineJoin = LineJoin.Round
                }
            }
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

    return features {
        polygon(
            key = "dashed-polygon-west",
            rings = listOf(ring(14.4, 49.75, 0.85, 0.45)),
        ) {
            label = "Dashed polygon"
            style = polygonStyle {
                fill(0x3326A69A) {
                    hatch(
                        angleDegrees = 35.0,
                        spacing = 10.0,
                        strokeColor = 0xFF00796B,
                        strokeWidth = 1.2,
                    )
                }
                stroke(0xFF004D40, width = 3.0) {
                    lineJoin = LineJoin.Round
                    dash(18.0, 8.0)
                }
            }
        }
        polygon(
            key = "dashed-polygon-east",
            rings = listOf(ring(16.1, 49.95, 0.75, 0.4)),
        ) {
            label = "Pattern fill"
            style = polygonStyle {
                fill(0x33AB47BC) {
                    dots(
                        spacing = 12.0,
                        radius = 2.0,
                        color = 0xFF8E24AA,
                    )
                }
                stroke(0xFF6A1B9A, width = 2.5) {
                    dash(12.0, 7.0)
                }
            }
        }
    }
}

private fun buildFullLineLayerFeatures(): List<Feature> {
    fun wavePoints(baseLat: Double, phase: Double): List<Point> =
        (0 until 120).map { i ->
            val t = i.toDouble() / 119.0
            Point(12.4 + 5.8 * t, baseLat + sin(t * PI * 2.5 + phase) * 0.28)
        }

    fun FeatureOptions.wave(key: String, strokeArgb: Long) {
        label = key
        style = lineStyle {
            stroke(strokeArgb, width = 4.0) {
                lineCap = LineCap.Round
                lineJoin = LineJoin.Round
            }
        }
    }

    return features {
        line("Full line A", wavePoints(50.35, 0.0)) {
            wave(key = "Full line A", strokeArgb = 0xFFE53935)
        }
        line("Full line B", wavePoints(49.25, PI / 2.0)) {
            wave(key = "Full line B", strokeArgb = 0xFF1E88E5)
        }
    }
}

private fun buildMercatorPlaceFeatures(): List<Feature> =
    features {
        point("prague", 14.4378, 50.0755) {
            label = "Prague"
            data = Data(
                PlaceDetails(
                    name = "Praha",
                    description = "Hlavni mesto Ceska a vychozi bod XYZ/Web Mercator ukazky.",
                )
            )
            style = pointStyle {
                shape = PointShape.Circle
                size = 16.0
                fill(0xFFE53935)
                stroke(0xFFFFFFFF, width = 3.0)
            }
        }
        point("brno", 16.6068, 49.1951) {
            label = "Brno"
            data = Data(
                PlaceDetails(
                    name = "Brno",
                    description = "Moravske centrum a prakticky test vyberu feature mimo stred mapy.",
                )
            )
            style = pointStyle {
                shape = PointShape.Square
                size = 14.0
                fill(0xFF1E88E5)
                stroke(0xFFFFFFFF, width = 2.5)
            }
        }
        point("ostrava", 18.2625, 49.8209) {
            label = "Ostrava"
            data = Data(
                PlaceDetails(
                    name = "Ostrava",
                    description = "Treti ukazkove mesto s vlastnim payloadem v Feature.data.",
                )
            )
            style = pointStyle {
                shape = PointShape.Diamond
                size = 14.0
                fill(0xFF43A047)
                stroke(0xFFFFFFFF, width = 2.5)
            }
        }
        line(
            key = "prague-selection-line",
            points = listOf(
                Point(14.3880, 50.0920),
                Point(14.4170, 50.1060),
                Point(14.4610, 50.0960),
                Point(14.4900, 50.0740),
            ),
        ) {
            label = "Selection line"
            data = Data(
                PlaceDetails(
                    name = "Praha test line",
                    description = "Kliknutelna linie v XYZ/Web Mercator ukazce pro overeni line hit-testu.",
                )
            )
        }
        polygon(
            key = "prague-selection-polygon",
            rings = listOf(
                listOf(
                    Point(14.4050, 50.0600),
                    Point(14.4550, 50.0580),
                    Point(14.4720, 50.0370),
                    Point(14.4250, 50.0280),
                    Point(14.3920, 50.0420),
                    Point(14.4050, 50.0600),
                )
            ),
        ) {
            label = "Selection polygon"
            data = Data(
                PlaceDetails(
                    name = "Praha test polygon",
                    description = "Kliknutelny polygon s vlastnim selected stylem pro overeni polygon selekce.",
                )
            )
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
