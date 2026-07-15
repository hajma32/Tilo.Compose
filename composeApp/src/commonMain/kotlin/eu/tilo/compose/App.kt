package eu.tilo.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.tilo.compose.transit.BrnoTransitFeed
import eu.tilo.compose.transit.TransitFeedState
import eu.tilo.compose.transit.TransitType
import eu.tilo.compose.transit.TransitVehicle
import eu.tilo.compose.transit.toTransitFeatures
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
import tilo.compose.draw.DrawState
import tilo.compose.draw.drawLayer
import tilo.compose.draw.rememberDrawState
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
import tilo.compose.ui.DefaultZoomControls
import tilo.compose.ui.defaultAttributionContent
import tilo.compose.ui.defaultScaleBarContent

private const val MAP_BACKGROUND_COLOR = 0xFFF2EEE3
private const val CUZK_ORTOFOTO_WMS_URL = "https://ags.cuzk.gov.cz/arcgis1/services/ORTOFOTO/MapServer/WMSServer"
private const val CUZK_ZTM_WMS_URL = "https://ags.cuzk.gov.cz/arcgis1/services/ZTM/MapServer/WMSServer"
private const val OSM_XYZ_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
private const val LIVE_TRANSIT_LAYER_ID = "live-transit"

private enum class MapDemo(
    val title: String,
    val subtitle: String,
) {
    Sjtks("S-JTSK / CUZK", "EPSG:5514 WMS + vectors"),
    WebMercatorXyz("Web Mercator / XYZ", "EPSG:3857 XYZ tiles"),
}

private enum class BasemapOption(
    val title: String,
) {
    CuzkOrtofoto("CUZK ortofoto"),
    CuzkZtm("CUZK basic map"),
}

private enum class DemoLayerOption(
    val title: String,
) {
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
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 64.dp),
        ) {
            DefaultZoomControls(
                onZoomBy = { delta -> cameraState.animateZoomBy(delta) },
            )
        }
    }

@Composable
private fun BoxScope.MapSearchBox(onMenuClick: () -> Unit) {
    Surface(
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(56.dp),
            ) {
                HamburgerIcon()
            }
            Text(
                text = "Kam jedete?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Preview
@Suppress("LongMethod") // The sample keeps its demo state and navigation wiring together for discoverability.
fun App() {
    val ortofotoLayer =
        rememberWMSLayer(
            id = "cuzk-ortofoto",
            capabilitiesUrl = CUZK_ORTOFOTO_WMS_URL,
            layerName = "0",
            projection = sjtsk(),
            format = "image/jpeg",
            attribution = attribution("ČÚZK Ortofoto, EPSG:5514"),
        )
    val ztmLayer =
        rememberWMSLayer(
            id = "cuzk-ztm",
            capabilitiesUrl = CUZK_ZTM_WMS_URL,
            layerName = "0",
            projection = sjtsk(),
            format = "image/png",
            attribution = attribution("ČÚZK Základní mapa, EPSG:5514"),
        )
    val mercatorPlaceFeatures = remember { buildMercatorPlaceFeatures() }
    val pragueDetailFeatures = remember { buildPragueDetailFeatures() }
    val transitFeed = remember { BrnoTransitFeed() }
    var transitState by remember { mutableStateOf(TransitFeedState.Idle) }
    var selectedTransitVehicleId by remember { mutableStateOf<String?>(null) }

    var savedDrawingFeatures by remember { mutableStateOf<List<Feature>>(emptyList()) }
    val drawState =
        rememberDrawState(
            onSave = { feature ->
                savedDrawingFeatures = savedDrawingFeatures + feature.withSavedDrawingStyle()
            },
        )

    var selectedDemo by remember { mutableStateOf(MapDemo.Sjtks) }
    var selectedBasemap by remember { mutableStateOf(BasemapOption.CuzkOrtofoto) }
    var selectedLayers by remember {
        mutableStateOf(
            setOf(
                DemoLayerOption.LiveTransit,
            ),
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

    val transitFeatures =
        remember(transitState.vehicles) {
            transitState.vehicles.toTransitFeatures()
        }
    val selectedTransitVehicle =
        transitState.vehicles.firstOrNull { vehicle ->
            vehicle.id == selectedTransitVehicleId
        }
    val selectedTransitFeatures =
        selectedTransitVehicleId?.let { vehicleId ->
            setOf(FeatureSelectionRef(layerId = LIVE_TRANSIT_LAYER_ID, featureKey = "transit-$vehicleId"))
        } ?: emptySet()

    val sjtskCameraState =
        rememberMapCameraState(
            initialCenter = Wgs84ToEpsg5514Transformation.sourceToTarget(Point(16.6068, 49.1951)),
            initialZoom = 11.5,
            config =
                MapConfig(minZoom = 0.0, maxZoom = 20.0)
                    .withTransformation(Wgs84ToEpsg5514Transformation)
                    .withTransformation(Epsg5514ToWgs84Transformation),
            projection = sjtsk(),
        )
    val mercatorCameraState =
        rememberMapCameraState(
            initialCenter = Wgs84ToWebMercatorTransformation.sourceToTarget(Point(14.4378, 50.0755)),
            initialZoom = 11.5,
            config =
                MapConfig(minZoom = 0.0, maxZoom = 20.0)
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
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                    )
                    MapDemo.entries.forEach { option ->
                        Row(
                            modifier =
                                Modifier
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
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                        )
                        BasemapOption.entries.forEach { option ->
                            Row(
                                modifier =
                                    Modifier
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
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                        )
                        DemoLayerOption.entries.forEach { option ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = option in selectedLayers,
                                    onCheckedChange = { checked ->
                                        selectedLayers =
                                            if (checked) {
                                                selectedLayers + option
                                            } else {
                                                selectedLayers - option
                                            }
                                    },
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
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
            },
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color(MAP_BACKGROUND_COLOR)),
            ) {
                when (selectedDemo) {
                    MapDemo.Sjtks -> {
                        SjtksShowcaseMap(
                            cameraState = sjtskCameraState,
                            selectedBasemap = selectedBasemap,
                            ortofotoLayer = ortofotoLayer,
                            ztmLayer = ztmLayer,
                            selectedLayers = selectedLayers,
                            transitFeatures = transitFeatures,
                            selectedTransitFeatures = selectedTransitFeatures,
                            onTransitSelect = { vehicle ->
                                selectedTransitVehicleId = vehicle?.id
                            },
                            savedDrawingFeatures = savedDrawingFeatures,
                            drawState = drawState,
                        )
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
                MapSearchBox(
                    onMenuClick = { coroutineScope.launch { drawerState.open() } },
                )
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
    transitFeatures: List<Feature>,
    selectedTransitFeatures: Set<FeatureSelectionRef>,
    onTransitSelect: (TransitVehicle?) -> Unit,
    savedDrawingFeatures: List<Feature>,
    drawState: DrawState,
) {
    TiloMap(
        cameraState = cameraState,
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(MAP_BACKGROUND_COLOR)),
        onTapWorld = drawState::onMapTap,
        onFeatureSelect = { selections ->
            onTransitSelect(
                selections.firstNotNullOfOrNull { selection ->
                    selection.feature.data?.payload as? TransitVehicle
                },
            )
        },
        selectedFeatures = selectedTransitFeatures,
        attributionContent = defaultAttributionContent(),
        scaleBarContent = defaultScaleBarContent(),
        cameraControlsContent = animatedZoomControlsContent(),
        layers = {
            when (selectedBasemap) {
                BasemapOption.CuzkOrtofoto -> wmsTileLayer(ortofotoLayer)
                BasemapOption.CuzkZtm -> wmsTileLayer(ztmLayer)
            }
            if (DemoLayerOption.LiveTransit in selectedLayers) {
                featureLayer(LIVE_TRANSIT_LAYER_ID, transitFeatures) {
                    zIndex = 5
                    projection = wgs84()
                    attribution =
                        attribution(
                            label = "KORDIS / data.brno.cz",
                            url = "https://data.brno.cz/items/e8aa121910df41bb9a28e4ca34a263c7",
                        )
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
@Suppress("LongMethod") // The example intentionally shows the complete vector-style DSL in one place.
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
            selectedPlace =
                selections.firstNotNullOfOrNull { selection ->
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
                attribution =
                    attribution(
                        label = "© OpenStreetMap contributors",
                        url = "https://www.openstreetmap.org/copyright",
                    ),
            )
            featureLayer("mercator-places", places) {
                zIndex = 1
                projection = wgs84()
                style =
                    featureLayerStyle {
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
                style =
                    featureLayerStyle {
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
            modifier =
                Modifier
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
private fun BoxScope.TransitVehicleCard(
    vehicle: TransitVehicle,
    onClose: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
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
            val details =
                buildList {
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

private fun Double.formatDelay(): String = if (this > 0.0) "+$this" else toString()

private fun Feature.withSavedDrawingStyle(): Feature =
    copy(
        style =
            when (geometry) {
                is Point ->
                    pointStyle {
                        shape = PointShape.Circle
                        size = 14.dp
                        fill(0xFFF97316)
                        stroke(0xFFFFFFFF, width = 3.75.dp)
                    }
                is LineString ->
                    lineStyle {
                        casing(0xFFFFFFFF, width = 7.dp) {
                            lineCap = LineCap.Round
                            lineJoin = LineJoin.Round
                        }
                        stroke(0xFFF97316, width = 3.75.dp) {
                            lineCap = LineCap.Round
                            lineJoin = LineJoin.Round
                        }
                    }
                is Polygon ->
                    polygonStyle {
                        fill(0x33F97316)
                        casing(0xFFFFFFFF, width = 7.dp) {
                            lineJoin = LineJoin.Round
                        }
                        stroke(0xFFF97316, width = 3.75.dp) {
                            lineJoin = LineJoin.Round
                        }
                    }
                else -> style
            },
    )

@Suppress("LongMethod") // This is declarative sample data, not branching application logic.
private fun buildMercatorPlaceFeatures(): List<Feature> =
    features {
        point("praha", 14.4378, 50.0755) {
            label = "Praha"
            labelStyle = extraLargeLabelStyle()
            data =
                Data(
                    PlaceDetails(
                        name = "Praha",
                        description = "Extra large label testovaci bod pro hlavni mesto.",
                    ),
                )
        }
        point("plzen", 13.3776, 49.7384) {
            label = "Plzeň"
            labelStyle = largeLabelStyle()
            data =
                Data(
                    PlaceDetails(
                        name = "Plzeň",
                        description = "Bodova feature pro ladeni large label stylu a selekce.",
                    ),
                )
        }
        point("strakonice", 13.9024, 49.2614) {
            label = "Strakonice"
            data =
                Data(
                    PlaceDetails(
                        name = "Strakonice",
                        description = "Bodova feature v EPSG:3857 XYZ ukazce, zadana ve WGS84 souradnicich.",
                    ),
                )
        }
        point("usti-nad-labem", 14.0407, 50.6607) {
            label = "Ústí nad Labem"
            labelStyle = largeLabelStyle()
            data =
                Data(
                    PlaceDetails(
                        name = "Ústí nad Labem",
                        description = "Large label testovaci bod v severnich Cechach.",
                    ),
                )
        }
        point("ceske-budejovice", 14.4743, 48.9757) {
            label = "České Budějovice"
            labelStyle = largeLabelStyle()
            data =
                Data(
                    PlaceDetails(
                        name = "České Budějovice",
                        description = "Large label testovaci bod v jiznich Cechach.",
                    ),
                )
        }
        point("tabor", 14.6578, 49.4144) {
            label = "Tábor"
            labelStyle = smallLabelStyle()
            data =
                Data(
                    PlaceDetails(
                        name = "Tábor",
                        description = "Small label testovaci bod mezi Prahou a Ceskymi Budejovicemi.",
                    ),
                )
        }
        point("benesov", 14.6869, 49.7816) {
            label = "Benešov"
            labelStyle = smallLabelStyle()
            data =
                Data(
                    PlaceDetails(
                        name = "Benešov",
                        description = "Small label testovaci bod jihovychodne od Prahy.",
                    ),
                )
        }
        line(
            key = "vltava",
            points =
                listOf(
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
            labelStyle =
                smallLabelStyle {
                    color(0xFF2563EB)
                    fontStyle = LabelFontStyle.Italic
                    offsetY(-2.dp)
                }
            data =
                Data(
                    PlaceDetails(
                        name = "Vltava",
                        description =
                            "Vltava je nejdelší řeka v Česku a levý přítok Labe. " +
                                "Protéká Šumavou, jižními Čechami a Prahou a u Mělníka se vlévá do Labe",
                        sourceLabel = "Wikipedie",
                        sourceUrl = "https://cs.wikipedia.org/wiki/Vltava",
                    ),
                )
        }
        line(
            key = "d1",
            points =
                listOf(
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
            style =
                lineStyle {
                    casing(0xFFFFFFFF, width = 7.dp) {
                        lineCap = LineCap.Round
                        lineJoin = LineJoin.Round
                    }
                    stroke(0xFFE53935, width = 4.dp) {
                        lineCap = LineCap.Round
                        lineJoin = LineJoin.Round
                    }
                }
            labelStyle =
                smallLabelStyle {
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
            data =
                Data(
                    PlaceDetails(
                        name = "D1",
                        description = "Priblizna liniova feature dalnice D1 s testovacim stitkovym labelem.",
                    ),
                )
        }
        polygon(
            key = "cesky-les",
            rings =
                listOf(
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
                    ),
                ),
        ) {
            label = "Český les"
            labelStyle =
                smallLabelStyle {
                    color(0xFF2E7D32)
                }
            style =
                polygonStyle {
                    fill(0x554CAF50)
                    casing(0xFFFFFFFF, width = 7.dp) {
                        lineJoin = LineJoin.Round
                    }
                    stroke(0xFF2E7D32, width = 3.75.dp) {
                        lineJoin = LineJoin.Round
                    }
                }
            data =
                Data(
                    PlaceDetails(
                        name = "Český les",
                        description = "Priblizny zeleny polygon kolem pohori Cesky les pro ladeni polygon stylu.",
                    ),
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
                data =
                    Data(
                        PlaceDetails(
                            name = name,
                            description = "Detailní ukázková feature v Praze viditelná od zoomu 13.0.",
                        ),
                    )
            }
        }
    }

@Composable
private fun HamburgerIcon() {
    Column(
        modifier = Modifier.size(width = 20.dp, height = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        repeat(3) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0xFF111827)),
            )
        }
    }
}
