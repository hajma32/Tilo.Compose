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
import tilo.compose.dsl.extraLargeLabelStyle
import tilo.compose.dsl.featureLayerStyle
import tilo.compose.dsl.features
import tilo.compose.dsl.largeLabelStyle
import tilo.compose.dsl.lineStyle
import tilo.compose.dsl.mediumLabelStyle
import tilo.compose.dsl.pointStyle
import tilo.compose.dsl.polygonStyle
import tilo.compose.dsl.rememberMapCameraState
import tilo.compose.dsl.rememberWMSLayer
import tilo.compose.dsl.sjtsk
import tilo.compose.dsl.smallLabelStyle
import tilo.compose.dsl.webMercator
import tilo.compose.dsl.wgs84
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import tilo.compose.core.feature.Data
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.LabelFontStyle
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
import tilo.compose.ui.defaultAttributionContent
import tilo.compose.ui.defaultScaleBarContent
import tilo.compose.ui.defaultZoomControlsContent

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
                    Surface(shadowElevation = 4.dp) {
                        TopAppBar(
                            title = { Text("Tilo.Compose") },
                            navigationIcon = {
                                IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                    HamburgerIcon()
                                }
                            }
                        )
                    }
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
        attributionContent = defaultAttributionContent(),
        scaleBarContent = defaultScaleBarContent(),
        cameraControlsContent = defaultZoomControlsContent(),
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
        attributionContent = defaultAttributionContent(),
        scaleBarContent = defaultScaleBarContent(),
        cameraControlsContent = defaultZoomControlsContent(),
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
                        size = 18.dp
                        fill(0xFF2563EB)
                        stroke(0xFFFFFFFF, width = 3.75.dp)
                    }
                    line {
                        casing(0xFFFFFFFF, width = 7.dp) {
                            lineCap = LineCap.Round
                            lineJoin = LineJoin.Round
                        }
                        stroke(0xFF2563EB, width = 3.75.dp) {
                            lineCap = LineCap.Round
                            lineJoin = LineJoin.Round
                        }
                    }
                    polygon {
                        fill(0x332563EB)
                        casing(0xFFFFFFFF, width = 7.dp) {
                            lineJoin = LineJoin.Round
                        }
                        stroke(0xFF2563EB, width = 3.75.dp) {
                            lineJoin = LineJoin.Round
                        }
                    }
                    label(mediumLabelStyle())
                    selectedPoint {
                        shape = PointShape.Circle
                        size = 26.dp
                        fill(0xFFFFD54F)
                        stroke(0xFF111827, width = 4.dp)
                    }
                    selectedLine {
                        casing(0xFFFFFFFF, width = 10.dp) {
                            lineCap = LineCap.Round
                            lineJoin = LineJoin.Round
                        }
                        stroke(0xFFFFD54F, width = 7.dp) {
                            lineCap = LineCap.Round
                            lineJoin = LineJoin.Round
                        }
                    }
                    selectedPolygon {
                        fill(0x55FFD54F)
                        casing(0xFFFFFFFF, width = 10.dp) {
                            lineJoin = LineJoin.Round
                        }
                        stroke(0xFFFFD54F, width = 7.dp) {
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
                size = 14.dp
                fill(0xFF43A047)
                stroke(0xFF263238, width = 2.dp)
            }
            is LineString -> lineStyle {
                noCasing()
                stroke(0xFF43A047, width = 4.dp) {
                    lineCap = LineCap.Round
                    lineJoin = LineJoin.Round
                }
            }
            is Polygon -> polygonStyle {
                fill(0x5543A047)
                noCasing()
                stroke(0xFF2E7D32, width = 3.dp) {
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
                        spacing = 10.dp,
                        strokeColor = 0xFF00796B,
                        strokeWidth = 1.2.dp,
                    )
                }
                noCasing()
                stroke(0xFF004D40, width = 3.dp) {
                    lineJoin = LineJoin.Round
                    dash(18.dp, 8.dp)
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
                        spacing = 12.dp,
                        radius = 2.dp,
                        color = 0xFF8E24AA,
                    )
                }
                noCasing()
                stroke(0xFF6A1B9A, width = 2.5.dp) {
                    dash(12.dp, 7.dp)
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
            noCasing()
            stroke(strokeArgb, width = 4.dp) {
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
        point("praha", 14.4378, 50.0755) {
            label = "Praha"
            labelStyle = extraLargeLabelStyle()
            data = Data(
                PlaceDetails(
                    name = "Praha",
                    description = "Extra large label testovaci bod pro hlavni mesto.",
                )
            )
        }
        point("plzen", 13.3776, 49.7384) {
            label = "Plzeň"
            labelStyle = largeLabelStyle()
            data = Data(
                PlaceDetails(
                    name = "Plzeň",
                    description = "Bodova feature pro ladeni large label stylu a selekce.",
                )
            )
        }
        point("strakonice", 13.9024, 49.2614) {
            label = "Strakonice"
            data = Data(
                PlaceDetails(
                    name = "Strakonice",
                    description = "Bodova feature v EPSG:3857 XYZ ukazce, zadana ve WGS84 souradnicich.",
                )
            )
        }
        point("usti-nad-labem", 14.0407, 50.6607) {
            label = "Ústí nad Labem"
            labelStyle = largeLabelStyle()
            data = Data(
                PlaceDetails(
                    name = "Ústí nad Labem",
                    description = "Large label testovaci bod v severnich Cechach.",
                )
            )
        }
        point("ceske-budejovice", 14.4743, 48.9757) {
            label = "České Budějovice"
            labelStyle = largeLabelStyle()
            data = Data(
                PlaceDetails(
                    name = "České Budějovice",
                    description = "Large label testovaci bod v jiznich Cechach.",
                )
            )
        }
        point("tabor", 14.6578, 49.4144) {
            label = "Tábor"
            labelStyle = smallLabelStyle()
            data = Data(
                PlaceDetails(
                    name = "Tábor",
                    description = "Small label testovaci bod mezi Prahou a Ceskymi Budejovicemi.",
                )
            )
        }
        point("benesov", 14.6869, 49.7816) {
            label = "Benešov"
            labelStyle = smallLabelStyle()
            data = Data(
                PlaceDetails(
                    name = "Benešov",
                    description = "Small label testovaci bod jihovychodne od Prahy.",
                )
            )
        }
        line(
            key = "vltava",
            points = listOf(
                Point(14.3150, 48.8120),
                Point(14.4750, 48.9740),
                Point(14.4200, 49.2240),
                Point(14.1650, 49.5050),
                Point(14.3950, 49.8150),
                Point(14.4208, 50.0880),
                Point(14.3120, 50.2400),
                Point(14.4740, 50.3510),
            ),
        ) {
            label = "Vltava"
            labelStyle = smallLabelStyle {
                color(0xFF2563EB)
                fontStyle = LabelFontStyle.Italic
                offsetY(-2.dp)
            }
            data = Data(
                PlaceDetails(
                    name = "Vltava",
                    description = "Priblizna liniova feature sledujici tok Vltavy pres jizni a stredni Cechy.",
                )
            )
        }
        line(
            key = "d1",
            points = listOf(
                Point(14.4300, 50.0520),
                Point(14.7200, 49.8550),
                Point(15.2350, 49.6840),
                Point(15.5900, 49.6050),
                Point(16.6070, 49.1950),
                Point(17.1150, 49.2140),
                Point(17.6700, 49.6600),
                Point(18.2620, 49.8200),
            ),
        ) {
            label = "D1"
            style = lineStyle {
                casing(0xFFFFFFFF, width = 7.dp) {
                    lineCap = LineCap.Round
                    lineJoin = LineJoin.Round
                }
                stroke(0xFFE53935, width = 4.dp) {
                    lineCap = LineCap.Round
                    lineJoin = LineJoin.Round
                }
            }
            labelStyle = smallLabelStyle {
                color(0xFFFFFFFF)
                noHalo()
                background(
                    color = 0xFFE53935,
                    cornerRadius = 4.dp,
                    paddingHorizontal = 6.dp,
                    paddingVertical = 2.dp,
                )
                offsetY(4.dp)
            }
            data = Data(
                PlaceDetails(
                    name = "D1",
                    description = "Priblizna liniova feature dalnice D1 s testovacim stitkovym labelem.",
                )
            )
        }
        polygon(
            key = "cesky-les",
            rings = listOf(
                listOf(
                    Point(12.5100, 49.9200),
                    Point(12.7800, 49.8600),
                    Point(12.8700, 49.5600),
                    Point(12.7600, 49.3000),
                    Point(12.6100, 49.0600),
                    Point(12.4100, 49.1800),
                    Point(12.3600, 49.5200),
                    Point(12.4100, 49.7600),
                    Point(12.5100, 49.9200),
                )
            ),
        ) {
            label = "Český les"
            labelStyle = smallLabelStyle {
                color(0xFF2E7D32)
            }
            style = polygonStyle {
                fill(0x554CAF50)
                casing(0xFFFFFFFF, width = 7.dp) {
                    lineJoin = LineJoin.Round
                }
                stroke(0xFF2E7D32, width = 3.75.dp) {
                    lineJoin = LineJoin.Round
                }
            }
            data = Data(
                PlaceDetails(
                    name = "Český les",
                    description = "Priblizny zeleny polygon kolem pohori Cesky les pro ladeni polygon stylu.",
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
