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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import tilo.compose.dsl.MapCameraState
import tilo.compose.dsl.TiloMap
import tilo.compose.dsl.WMSLayerState
import tilo.compose.dsl.attribution
import tilo.compose.dsl.cachedBitmap
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
import eu.tilo.compose.cuzk.ZabagedLayerState
import eu.tilo.compose.cuzk.rememberZabagedLayerState
import eu.tilo.compose.transit.BrnoTransitFeed
import eu.tilo.compose.transit.TransitConnectionStatus
import eu.tilo.compose.transit.TransitFeedState
import eu.tilo.compose.transit.TransitType
import eu.tilo.compose.transit.TransitVehicle
import eu.tilo.compose.transit.toTransitFeatures
import tilo.compose.core.feature.Data
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.LabelFontStyle
import tilo.compose.core.feature.LabelFontWeight as MapLabelFontWeight
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
import tilo.compose.ui.DefaultAttributionOverlay
import tilo.compose.ui.DefaultMapDebugOverlay
import tilo.compose.ui.defaultAttributionContent
import tilo.compose.ui.defaultScaleBarContent

private const val MAP_BACKGROUND_COLOR = 0xFFF2EEE3
private const val ZABAGED_OVERVIEW_BACKGROUND_COLOR = 0xFFEEF3E2
private const val CUZK_ORTOFOTO_WMS_URL = "https://ags.cuzk.gov.cz/arcgis1/services/ORTOFOTO/MapServer/WMSServer"
private const val CUZK_ZTM_WMS_URL = "https://ags.cuzk.gov.cz/arcgis1/services/ZTM/MapServer/WMSServer"
private const val OSM_XYZ_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
private const val LiveTransitLayerId = "live-transit"

private enum class MapDemo(val title: String, val subtitle: String) {
    Sjtks("S-JTSK / CUZK", "EPSG:5514 WMS + vectors"),
    WebMercatorXyz("Web Mercator / XYZ", "EPSG:3857 XYZ tiles"),
}

private enum class BasemapOption(val title: String) {
    CuzkOrtofoto("CUZK ortofoto"),
    CuzkZabaged("ZABAGED pastel map"),
    CuzkZtm("CUZK basic map"),
}

private enum class DemoLayerOption(val title: String) {
    Zabaged("ZABAGED transport + labels"),
    LiveTransit("Live public transit"),
}

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
        DefaultMapDebugOverlay(cameraState)
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
    val ztmOverviewLayer = rememberWMSLayer(
        id = "cuzk-ztm-zabaged-overview",
        capabilitiesUrl = CUZK_ZTM_WMS_URL,
        layerName = "0",
        projection = sjtsk(),
        format = "image/png",
        maxZoom = 9.999,
        attribution = attribution("ČÚZK Základní mapa, EPSG:5514"),
    )
    val mercatorPlaceFeatures = remember { buildMercatorPlaceFeatures() }
    val pragueDetailFeatures = remember { buildPragueDetailFeatures() }
    val transitFeed = remember { BrnoTransitFeed() }
    var transitState by remember { mutableStateOf(TransitFeedState.Idle) }
    var selectedTransitVehicleId by remember { mutableStateOf<String?>(null) }

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
                DemoLayerOption.Zabaged,
                DemoLayerOption.LiveTransit,
            )
        )
    }
    val drawerState = androidx.compose.material3.rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(transitFeed) {
        onDispose(transitFeed::close)
    }

    LaunchedEffect(selectedDemo, selectedLayers) {
        if (selectedDemo == MapDemo.Sjtks && DemoLayerOption.LiveTransit in selectedLayers) {
            transitFeed.states().collect { state -> transitState = state }
        } else {
            transitState = TransitFeedState.Idle
            selectedTransitVehicleId = null
        }
    }

    val transitFeatures = remember(transitState.vehicles) {
        transitState.vehicles.toTransitFeatures()
    }
    val selectedTransitVehicle = transitState.vehicles.firstOrNull { vehicle ->
        vehicle.id == selectedTransitVehicleId
    }
    val selectedTransitFeatures = selectedTransitVehicleId?.let { vehicleId ->
        setOf(FeatureSelectionRef(layerId = LiveTransitLayerId, featureKey = "transit-$vehicleId"))
    } ?: emptySet()

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
    val zabagedEnabled = selectedDemo == MapDemo.Sjtks && (
        selectedBasemap == BasemapOption.CuzkZabaged || DemoLayerOption.Zabaged in selectedLayers
    )
    val zabagedState = rememberZabagedLayerState(
        cameraState = sjtskCameraState,
        basemapEnabled = selectedDemo == MapDemo.Sjtks &&
            selectedBasemap == BasemapOption.CuzkZabaged,
        overlayEnabled = selectedDemo == MapDemo.Sjtks &&
            DemoLayerOption.Zabaged in selectedLayers,
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
                                ztmOverviewLayer = ztmOverviewLayer,
                                selectedLayers = selectedLayers,
                                zabagedState = zabagedState,
                                transitFeatures = transitFeatures,
                                selectedTransitFeatures = selectedTransitFeatures,
                                onTransitSelect = { vehicle ->
                                    selectedTransitVehicleId = vehicle?.id
                                },
                                savedDrawingFeatures = savedDrawingFeatures,
                                drawState = drawState,
                            )
                            DrawingControls(state = drawState)
                            if (DemoLayerOption.LiveTransit in selectedLayers) {
                                TransitStatusBadge(transitState)
                            }
                            if (zabagedEnabled && sjtskCameraState.zoom >= 10.0) {
                                ZabagedStatusBadge(
                                    state = zabagedState,
                                    belowTransitBadge = DemoLayerOption.LiveTransit in selectedLayers,
                                )
                            }
                            selectedTransitVehicle?.takeUnless { drawState.isDrawing }?.let { vehicle ->
                                TransitVehicleCard(
                                    vehicle = vehicle,
                                    onClose = { selectedTransitVehicleId = null },
                                )
                            }
                        }
                        MapDemo.WebMercatorXyz -> {
                            WebMercatorXyzExampleMap(
                                cameraState = mercatorCameraState,
                                places = mercatorPlaceFeatures,
                                pragueDetails = pragueDetailFeatures,
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
    ztmOverviewLayer: WMSLayerState,
    selectedLayers: Set<DemoLayerOption>,
    zabagedState: ZabagedLayerState,
    transitFeatures: List<Feature>,
    selectedTransitFeatures: Set<FeatureSelectionRef>,
    onTransitSelect: (TransitVehicle?) -> Unit,
    savedDrawingFeatures: List<Feature>,
    drawState: DrawState,
) {
    val backgroundColor = if (
        selectedBasemap == BasemapOption.CuzkZabaged && cameraState.zoom in 10.0..<12.0
    ) {
        ZABAGED_OVERVIEW_BACKGROUND_COLOR
    } else {
        MAP_BACKGROUND_COLOR
    }
    TiloMap(
        cameraState = cameraState,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(backgroundColor)),
        onTapWorld = drawState::onMapTap,
        onFeatureSelect = { selections ->
            onTransitSelect(
                selections.firstNotNullOfOrNull { selection ->
                    selection.feature.data?.payload as? TransitVehicle
                }
            )
        },
        selectedFeatures = selectedTransitFeatures,
        attributionContent = { attributions ->
            val transitAttributions = if (DemoLayerOption.LiveTransit in selectedLayers) {
                listOf(
                    attribution(
                        label = "KORDIS / data.brno.cz",
                        url = "https://data.brno.cz/items/e8aa121910df41bb9a28e4ca34a263c7",
                    )
                )
            } else {
                emptyList()
            }
            val zabagedAttributions = if (
                cameraState.zoom >= 10.0 &&
                selectedBasemap == BasemapOption.CuzkZabaged ||
                cameraState.zoom >= 10.0 && DemoLayerOption.Zabaged in selectedLayers
            ) {
                listOf(
                    attribution(
                        label = "ČÚZK ZABAGED®, CC BY 4.0",
                        url = "https://ags.cuzk.gov.cz/opendata/",
                    )
                )
            } else {
                emptyList()
            }
            DefaultAttributionOverlay(attributions + zabagedAttributions + transitAttributions)
        },
        scaleBarContent = defaultScaleBarContent(),
        cameraControlsContent = animatedZoomControlsContent(),
        layers = {
            when (selectedBasemap) {
                BasemapOption.CuzkOrtofoto -> wmsTileLayer(ortofotoLayer)
                BasemapOption.CuzkZabaged -> {
                    wmsTileLayer(ztmOverviewLayer)
                    featureLayer(
                        id = "zabaged-pastel-land",
                        features = zabagedState.landFeatures,
                    ) {
                        zIndex = 0
                        projection = sjtsk()
                        minZoom = 10.0
                        renderMode = cachedBitmap(
                            scale = 1.0,
                            paddingPx = 192,
                            invalidateOnZoomDelta = 0.3,
                        )
                    }
                    featureLayer(
                        id = "zabaged-pastel-buildings",
                        features = zabagedState.buildingFeatures,
                    ) {
                        zIndex = 0
                        projection = sjtsk()
                        minZoom = 14.0
                        renderMode = cachedBitmap(
                            scale = 1.0,
                            paddingPx = 128,
                            invalidateOnZoomDelta = 0.3,
                        )
                    }
                }
                BasemapOption.CuzkZtm -> wmsTileLayer(ztmLayer)
            }
            if (DemoLayerOption.Zabaged in selectedLayers) {
                featureLayer("zabaged-boundaries", zabagedState.boundaries) {
                    zIndex = 1
                    projection = sjtsk()
                    minZoom = 10.0
                    style = featureLayerStyle {
                        line {
                            stroke(0xFFFFD600, width = 2.dp, opacity = 0.48)
                        }
                    }
                }
                featureLayer("zabaged-roads", zabagedState.roads) {
                    zIndex = 2
                    projection = sjtsk()
                    minZoom = 10.0
                    style = featureLayerStyle {
                        line {
                            casing(0xFF475569, width = 4.dp)
                            stroke(0xFFFFFFFF, width = 2.5.dp)
                        }
                        label(0xFFFFFFFF) {
                            fontSize(11.sp)
                            fontWeight(MapLabelFontWeight.Bold)
                            noHalo()
                            background(
                                color = 0xFFBE123C,
                                cornerRadius = 0.dp,
                                paddingHorizontal = 5.dp,
                                paddingVertical = 2.dp,
                            )
                            offsetY(0.dp)
                        }
                    }
                }
                featureLayer("zabaged-streets", zabagedState.streets) {
                    zIndex = 3
                    projection = sjtsk()
                    minZoom = 10.0
                    style = featureLayerStyle {
                        line {
                            casing(0xFF64748B, width = 2.8.dp)
                            stroke(0xFFFFFFFF, width = 1.6.dp)
                        }
                        label(0xFF111827) {
                            fontSize(9.sp)
                            fontWeight(MapLabelFontWeight.Medium)
                            halo(0xFFFFFFFF, width = 2.5.dp)
                            offsetY(1.dp)
                        }
                    }
                }
                featureLayer("zabaged-municipalities", zabagedState.municipalities) {
                    zIndex = 4
                    projection = sjtsk()
                    minZoom = 10.0
                    style = featureLayerStyle {
                        point {
                            shape = PointShape.Circle
                            size = 0.dp
                            fill(0x00000000)
                            stroke(0x00000000, width = 0.dp)
                        }
                        label(0xFF111827) {
                            fontSize(11.sp)
                            fontWeight(MapLabelFontWeight.SemiBold)
                            halo(0xFFFFFFFF, width = 3.dp)
                            offsetY(0.dp)
                        }
                    }
                }
            }
            if (DemoLayerOption.LiveTransit in selectedLayers) {
                featureLayer(LiveTransitLayerId, transitFeatures) {
                    zIndex = 5
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
    pragueDetails: List<Feature>,
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
            featureLayer("prague-detail", pragueDetails) {
                zIndex = 2
                minZoom = 13.0
                projection = wgs84()
                style = featureLayerStyle {
                    point {
                        shape = PointShape.Circle
                        size = 12.dp
                        fill(0xFF7C3AED)
                        stroke(0xFFFFFFFF, width = 3.dp)
                    }
                    label(smallLabelStyle())
                    selectedPoint {
                        shape = PointShape.Circle
                        size = 18.dp
                        fill(0xFFFFD54F)
                        stroke(0xFF111827, width = 3.dp)
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
private fun BoxScope.TransitStatusBadge(state: TransitFeedState) {
    val text = when (state.status) {
        TransitConnectionStatus.Idle -> "Transit off"
        TransitConnectionStatus.Connecting -> "Connecting to KORDIS…"
        TransitConnectionStatus.Live -> "KORDIS live · ${state.vehicles.size} vehicles"
        TransitConnectionStatus.Reconnecting -> "Reconnecting · ${state.vehicles.size} vehicles"
    }
    val color = when (state.status) {
        TransitConnectionStatus.Live -> Color(0xFF166534)
        TransitConnectionStatus.Reconnecting -> Color(0xFF9A3412)
        TransitConnectionStatus.Idle, TransitConnectionStatus.Connecting -> Color(0xFF334155)
    }
    Surface(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.92f),
        contentColor = Color.White,
        shadowElevation = 4.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun BoxScope.ZabagedStatusBadge(
    state: ZabagedLayerState,
    belowTransitBadge: Boolean,
) {
    val error = state.errorMessage
    if (!state.isLoading && error == null) return

    val text = when {
        error != null -> "ZABAGED: $error"
        state.featureCount > 0 -> "Updating ZABAGED… · ${state.featureCount} features"
        else -> "Loading ZABAGED…"
    }
    Surface(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = if (belowTransitBadge) 56.dp else 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(if (error == null) 0xFF334155 else 0xFF991B1B).copy(alpha = 0.92f),
        contentColor = Color.White,
        shadowElevation = 4.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BoxScope.TransitVehicleCard(
    vehicle: TransitVehicle,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 16.dp, end = 96.dp, bottom = 76.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vehicle.lineName?.let { "Line $it" } ?: "Transit vehicle",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${vehicle.actualType.title()} · vehicle ${vehicle.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClose) {
                    Text("Close")
                }
            }
            val details = buildList {
                vehicle.delayMinutes?.let { delay -> add("delay ${delay.formatDelay()} min") }
                vehicle.course?.let { course -> add("course $course") }
                vehicle.bearingDegrees?.let { bearing -> add("bearing ${bearing.toInt()}°") }
                vehicle.lowFloor?.let { lowFloor -> add(if (lowFloor) "low-floor" else "high-floor") }
            }
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun TransitType.title(): String =
    when (this) {
        TransitType.Service -> "Service"
        TransitType.Tram -> "Tram"
        TransitType.Trolleybus -> "Trolleybus"
        TransitType.Bus -> "Bus"
        TransitType.Boat -> "Boat"
        TransitType.Train -> "Train"
        TransitType.Unknown -> "Unknown type"
    }

private fun Double.formatDelay(): String =
    if (this > 0.0) "+$this" else toString()

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

private fun buildPragueDetailFeatures(): List<Feature> =
    features {
        listOf(
            Triple("mala-strana", "Malá Strana", Point(14.4043, 50.0872)),
            Triple("stare-mesto", "Staré Město", Point(14.4208, 50.0870)),
            Triple("karlin", "Karlín", Point(14.4510, 50.0922)),
            Triple("vinohrady", "Vinohrady", Point(14.4418, 50.0765)),
            Triple("smichov", "Smíchov", Point(14.4030, 50.0715)),
            Triple("dejvice", "Dejvice", Point(14.3952, 50.1022)),
        ).forEach { (key, name, point) ->
            point(key, point.x, point.y) {
                label = name
                labelPriority = 80
                data = Data(
                    PlaceDetails(
                        name = name,
                        description = "Detailní ukázková feature v Praze viditelná od zoomu 13.0.",
                    )
                )
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
