package eu.tilo.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tilo.compose.dsl.MapCameraState
import tilo.compose.dsl.TiloMap
import tilo.compose.dsl.WMSLayerState
import tilo.compose.dsl.attribution
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
import tilo.compose.ui.DefaultZoomControls
import tilo.compose.ui.defaultAttributionContent
import tilo.compose.ui.defaultScaleBarContent

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
    CityLabels("City labels"),
}

private data class DemoCity(
    val key: String,
    val name: String,
    val lon: Double,
    val lat: Double,
    val population: Int,
)

private data class PlaceDetails(
    val name: String,
    val description: String,
    val sourceLabel: String? = null,
    val sourceUrl: String? = null,
)

private fun animatedZoomControlsContent(): @Composable BoxScope.(MapCameraState) -> Unit =
    { cameraState ->
        DefaultZoomControls(
            onZoomBy = { delta -> cameraState.animateZoomBy(delta) },
        )
    }

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
        attribution = attribution("ČÚZK Ortofoto, EPSG:5514"),
    )
    val ztmLayer = rememberWMSLayer(
        id = "cuzk-ztm",
        capabilitiesUrl = CUZK_ZTM_WMS_URL,
        layerName = "0",
        projection = sjtsk(),
        format = "image/png",
        attribution = attribution("ČÚZK Základní mapa, EPSG:5514"),
    )
    val sjtskReferenceFeatures = remember { buildSjtksReferenceFeatures() }
    val mercatorPlaceFeatures = remember { buildMercatorPlaceFeatures() }

    var savedDrawingFeatures by remember { mutableStateOf<List<Feature>>(emptyList()) }
    val drawState = rememberDrawState(
        onSave = { feature ->
            savedDrawingFeatures = savedDrawingFeatures + feature.withSavedDrawingStyle()
        },
    )

    var selectedDemo by remember { mutableStateOf(MapDemo.WebMercatorXyz) }
    var selectedBasemap by remember { mutableStateOf(BasemapOption.CuzkOrtofoto) }
    var selectedLayers by remember {
        mutableStateOf(
            setOf(
                DemoLayerOption.CityLabels,
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
                                referenceFeatures = sjtskReferenceFeatures,
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
    referenceFeatures: List<Feature>,
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
        cameraControlsContent = animatedZoomControlsContent(),
        layers = {
            when (selectedBasemap) {
                BasemapOption.CuzkOrtofoto -> wmsTileLayer(ortofotoLayer)
                BasemapOption.CuzkZtm -> wmsTileLayer(ztmLayer)
            }
            if (DemoLayerOption.CityLabels in selectedLayers) {
                featureLayer("city-labels", referenceFeatures) {
                    zIndex = 1
                    projection = wgs84()
                    style = featureLayerStyle {
                        point {
                            shape = PointShape.Circle
                            size = 0.dp
                            fill(0x00000000)
                            stroke(0x00000000, width = 0.dp)
                        }
                        label(mediumLabelStyle {
                            offsetY(0.dp)
                        })
                    }
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
    val uriHandler = LocalUriHandler.current

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
        cameraControlsContent = animatedZoomControlsContent(),
        layers = {
            xyzTileLayer(
                id = "osm-standard",
                urlTemplate = OSM_XYZ_URL,
                projection = webMercator(),
                maxVisibleTiles = 9,
                prefetchMargin = 0,
                overviewZoomOffset = 0,
                maxOverviewTiles = 0,
                overviewPrefetchMargin = 0,
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
                .padding(start = 12.dp, end = 12.dp, bottom = 50.dp),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${place.description}...",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (place.sourceLabel != null && place.sourceUrl != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Zdroj:",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = place.sourceLabel,
                            modifier = Modifier.clickable { uriHandler.openUri(place.sourceUrl) },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.DrawingControls(
    state: DrawState,
) {
    if (state.isDrawing) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 96.dp, bottom = 76.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DrawMode.entries.forEach { mode ->
                        DrawingModeChip(
                            mode = mode,
                            selected = mode == state.mode,
                            onClick = { state.selectMode(mode) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DrawingActionButton("Undo", enabled = state.canUndo, onClick = state::undo)
                    DrawingActionButton("Redo", enabled = state.canRedo, onClick = state::redo)
                    Spacer(Modifier.weight(1f))
                    DrawingActionButton("Clear", onClick = state::clear)
                    Button(
                        onClick = { state.save() },
                        enabled = state.canSave,
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
    FloatingActionButton(
        onClick = { state.toggleDrawing() },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = 76.dp),
        shape = CircleShape,
    ) {
        DrawFabIcon(active = state.isDrawing)
    }
}

@Composable
private fun DrawingModeChip(
    mode: DrawMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier
            .height(36.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = mode.title(),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DrawingActionButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = ButtonDefaults.TextButtonContentPadding,
    ) {
        Text(text)
    }
}

@Composable
private fun DrawFabIcon(active: Boolean) {
    val color = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Icon(
        imageVector = DrawEditIcon,
        contentDescription = if (active) "Finish drawing" else "Start drawing",
        modifier = Modifier.size(26.dp),
        tint = color,
    )
}

private val DrawEditIcon: ImageVector =
    ImageVector.Builder(
        name = "Edit",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 17.25f)
            verticalLineTo(21f)
            horizontalLineTo(6.75f)
            lineTo(17.81f, 9.94f)
            lineTo(14.06f, 6.19f)
            lineTo(3f, 17.25f)
            close()
            moveTo(20.71f, 7.04f)
            curveTo(21.1f, 6.65f, 21.1f, 6.02f, 20.71f, 5.63f)
            lineTo(18.37f, 3.29f)
            curveTo(17.98f, 2.9f, 17.35f, 2.9f, 16.96f, 3.29f)
            lineTo(15.13f, 5.12f)
            lineTo(18.88f, 8.87f)
            lineTo(20.71f, 7.04f)
            close()
        }
    }.build()

private fun DrawMode.title(): String =
    name.lowercase().replaceFirstChar { char -> char.uppercase() }

private fun Feature.withSavedDrawingStyle(): Feature =
    copy(
        style = when (geometry) {
            is Point -> pointStyle {
                shape = PointShape.Circle
                size = 14.dp
                fill(0xFFF97316)
                stroke(0xFFFFFFFF, width = 3.75.dp)
            }
            is LineString -> lineStyle {
                casing(0xFFFFFFFF, width = 7.dp) {
                    lineCap = LineCap.Round
                    lineJoin = LineJoin.Round
                }
                stroke(0xFFF97316, width = 3.75.dp) {
                    lineCap = LineCap.Round
                    lineJoin = LineJoin.Round
                }
            }
            is Polygon -> polygonStyle {
                fill(0x33F97316)
                casing(0xFFFFFFFF, width = 7.dp) {
                    lineJoin = LineJoin.Round
                }
                stroke(0xFFF97316, width = 3.75.dp) {
                    lineJoin = LineJoin.Round
                }
            }
            else -> style
        }
    )

private fun buildSjtksReferenceFeatures(): List<Feature> =
    features {
        demoCitiesOver50k().forEach { city ->
            point(city.key, city.lon, city.lat) {
                label = city.name
                labelPriority = when {
                    city.population >= 500_000 -> 300
                    city.population >= 100_000 -> 200
                    else -> 100
                }
                labelStyle = when {
                    city.population >= 500_000 -> extraLargeLabelStyle {
                        offsetY(0.dp)
                    }
                    city.population >= 100_000 -> largeLabelStyle {
                        offsetY(0.dp)
                    }
                    else -> mediumLabelStyle {
                        offsetY(0.dp)
                    }
                }
                data = Data(
                    PlaceDetails(
                        name = city.name,
                        description = "Orientacni bod pro mesto nad 50 tisic obyvatel.",
                    )
                )
            }
        }
        brnoAreaMunicipalitiesOver1k().forEach { city ->
            point(city.key, city.lon, city.lat) {
                label = city.name
                labelPriority = 20
                labelStyle = smallLabelStyle {
                    offsetY(0.dp)
                }
                data = Data(
                    PlaceDetails(
                        name = city.name,
                        description = "Orientacni bod pro obec nad 1000 obyvatel v okoli Brna.",
                    )
                )
            }
        }
    }

private fun demoCitiesOver50k(): List<DemoCity> =
    listOf(
        DemoCity("praha", "Praha", 14.4378, 50.0755, 1_390_000),
        DemoCity("brno", "Brno", 16.6068, 49.1951, 400_000),
        DemoCity("ostrava", "Ostrava", 18.2625, 49.8209, 280_000),
        DemoCity("plzen", "Plzeň", 13.3776, 49.7384, 185_000),
        DemoCity("liberec", "Liberec", 15.0562, 50.7663, 107_000),
        DemoCity("olomouc", "Olomouc", 17.2518, 49.5938, 102_000),
        DemoCity("ceske-budejovice", "České Budějovice", 14.4743, 48.9757, 97_000),
        DemoCity("hradec-kralove", "Hradec Králové", 15.8328, 50.2092, 93_000),
        DemoCity("pardubice", "Pardubice", 15.7791, 50.0343, 92_000),
        DemoCity("usti-nad-labem", "Ústí nad Labem", 14.0407, 50.6607, 91_000),
        DemoCity("zlin", "Zlín", 17.6660, 49.2244, 74_000),
        DemoCity("havirov", "Havířov", 18.4369, 49.7798, 70_000),
        DemoCity("kladno", "Kladno", 14.1038, 50.1473, 69_000),
        DemoCity("most", "Most", 13.6362, 50.5030, 63_000),
        DemoCity("opava", "Opava", 17.9026, 49.9387, 55_000),
        DemoCity("frydek-mistek", "Frýdek-Místek", 18.3500, 49.6819, 55_000),
        DemoCity("jihlava", "Jihlava", 15.5906, 49.3961, 53_000),
        DemoCity("karvina", "Karviná", 18.5417, 49.8540, 51_000),
        DemoCity("teplice", "Teplice", 13.8245, 50.6404, 50_000),
    )

private fun brnoAreaMunicipalitiesOver1k(): List<DemoCity> =
    listOf(
        DemoCity("brno-area-blansko", "Blansko", 16.6444, 49.3630, 20_000),
        DemoCity("brno-area-boskovice", "Boskovice", 16.6599, 49.4875, 12_000),
        DemoCity("brno-area-letovice", "Letovice", 16.5736, 49.5471, 6_700),
        DemoCity("brno-area-adamov", "Adamov", 16.6525, 49.3016, 4_400),
        DemoCity("brno-area-rajec-jestrebi", "Rájec-Jestřebí", 16.6381, 49.4109, 3_600),
        DemoCity("brno-area-lipuvka", "Lipůvka", 16.5536, 49.3395, 1_400),
        DemoCity("brno-area-cerna-hora", "Černá Hora", 16.5814, 49.4136, 2_100),
        DemoCity("brno-area-jedovnice", "Jedovnice", 16.7551, 49.3446, 2_900),
        DemoCity("brno-area-ostrov-u-macochy", "Ostrov u Macochy", 16.7624, 49.3838, 1_100),
        DemoCity("brno-area-risty", "Ráječko", 16.6440, 49.3935, 1_400),
        DemoCity("brno-area-kurim", "Kuřim", 16.5314, 49.2985, 11_000),
        DemoCity("brno-area-tisnov", "Tišnov", 16.4244, 49.3489, 9_300),
        DemoCity("brno-area-veverska-bityska", "Veverská Bítýška", 16.4369, 49.2759, 3_300),
        DemoCity("brno-area-drásov", "Drásov", 16.4778, 49.3318, 2_200),
        DemoCity("brno-area-celistice", "Čebín", 16.4770, 49.3132, 1_800),
        DemoCity("brno-area-sentice", "Sentice", 16.4579, 49.3162, 1_100),
        DemoCity("brno-area-hradcany", "Hradčany", 16.4552, 49.3614, 1_100),
        DemoCity("brno-area-zastavka", "Zastávka", 16.3631, 49.1880, 2_500),
        DemoCity("brno-area-rosice", "Rosice", 16.3879, 49.1823, 6_700),
        DemoCity("brno-area-ivancice", "Ivančice", 16.3775, 49.1014, 9_900),
        DemoCity("brno-area-oslavany", "Oslavany", 16.3365, 49.1236, 4_700),
        DemoCity("brno-area-dolni-kounice", "Dolní Kounice", 16.4649, 49.0701, 2_400),
        DemoCity("brno-area-tetcice", "Tetčice", 16.4057, 49.1701, 1_300),
        DemoCity("brno-area-strelice", "Střelice", 16.5039, 49.1533, 3_200),
        DemoCity("brno-area-troubsko", "Troubsko", 16.5107, 49.1699, 2_300),
        DemoCity("brno-area-popuvky", "Popůvky", 16.4866, 49.1779, 1_800),
        DemoCity("brno-area-omice", "Omice", 16.4513, 49.1702, 1_000),
        DemoCity("brno-area-ostopovice", "Ostopovice", 16.5430, 49.1612, 1_800),
        DemoCity("brno-area-moravany", "Moravany", 16.5795, 49.1479, 3_400),
        DemoCity("brno-area-modrice", "Modřice", 16.6046, 49.1283, 5_600),
        DemoCity("brno-area-rajhrad", "Rajhrad", 16.6039, 49.0902, 3_900),
        DemoCity("brno-area-rajhradice", "Rajhradice", 16.6295, 49.0914, 1_600),
        DemoCity("brno-area-zidlochovice", "Židlochovice", 16.6188, 49.0395, 3_800),
        DemoCity("brno-area-hustopece", "Hustopeče", 16.7376, 48.9409, 6_000),
        DemoCity("brno-area-pohorelice", "Pohořelice", 16.5245, 48.9812, 5_500),
        DemoCity("brno-area-orechov", "Ořechov", 16.5237, 49.1112, 2_800),
        DemoCity("brno-area-slapnice", "Šlapanice", 16.7273, 49.1686, 7_900),
        DemoCity("brno-area-jirikovice", "Jiříkovice", 16.7567, 49.1669, 1_100),
        DemoCity("brno-area-blazovice", "Blažovice", 16.7861, 49.1655, 1_200),
        DemoCity("brno-area-prace", "Prace", 16.7655, 49.1408, 1_000),
        DemoCity("brno-area-ponetovice", "Ponětovice", 16.7502, 49.1528, 1_000),
        DemoCity("brno-area-sokolnice", "Sokolnice", 16.7216, 49.1138, 2_400),
        DemoCity("brno-area-telnice", "Telnice", 16.7177, 49.1019, 1_600),
        DemoCity("brno-area-ujezd-u-brna", "Újezd u Brna", 16.7570, 49.1053, 3_400),
        DemoCity("brno-area-slavkov-u-brna", "Slavkov u Brna", 16.8765, 49.1533, 7_000),
        DemoCity("brno-area-rousinov", "Rousínov", 16.8822, 49.2013, 5_700),
        DemoCity("brno-area-vyskov", "Vyškov", 16.9989, 49.2775, 20_000),
        DemoCity("brno-area-bucovice", "Bučovice", 17.0019, 49.1489, 6_400),
        DemoCity("brno-area-bilovice", "Bílovice nad Svitavou", 16.6729, 49.2470, 3_700),
        DemoCity("brno-area-ricany", "Řícmanice", 16.6827, 49.2577, 1_000),
        DemoCity("brno-area-babice", "Babice nad Svitavou", 16.6968, 49.2817, 1_300),
        DemoCity("brno-area-kanice", "Kanice", 16.7142, 49.2634, 1_100),
        DemoCity("brno-area-ochoz", "Ochoz u Brna", 16.7447, 49.2550, 1_400),
        DemoCity("brno-area-mokra-horakov", "Mokrá-Horákov", 16.7510, 49.2228, 2_800),
        DemoCity("brno-area-pozorice", "Pozořice", 16.7907, 49.2098, 2_300),
        DemoCity("brno-area-vinicne-sumice", "Viničné Šumice", 16.8258, 49.2137, 1_300),
        DemoCity("brno-area-tvarozna", "Tvarožná", 16.7702, 49.1918, 1_300),
        DemoCity("brno-area-sivice", "Sivice", 16.7822, 49.2040, 1_100),
        DemoCity("brno-area-holubice", "Holubice", 16.8122, 49.1775, 1_500),
    )

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
                    description = "Vltava je nejdelší řeka v Česku a levý přítok Labe. Protéká Šumavou, jižními Čechami a Prahou a u Mělníka se vlévá do Labe",
                    sourceLabel = "Wikipedie",
                    sourceUrl = "https://cs.wikipedia.org/wiki/Vltava",
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
