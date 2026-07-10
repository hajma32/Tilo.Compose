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
import eu.tilo.compose.render.TiloMap
import eu.tilo.compose.render.cachedBitmap
import eu.tilo.compose.render.lineStyle
import eu.tilo.compose.render.pointStyle
import eu.tilo.compose.render.polygonStyle
import eu.tilo.compose.render.rememberMapCameraState
import eu.tilo.compose.render.rememberWMSTileLayer
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.LineCap
import tilo.compose.core.feature.LineJoin
import tilo.compose.core.feature.PointShape
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon
import tilo.compose.core.map.MapConfig
import tilo.compose.core.projection.Epsg5514Projection
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.transform.Epsg5514ToWgs84Transformation
import tilo.compose.core.transform.Wgs84ToEpsg5514Transformation
import tilo.compose.draw.DrawMode
import tilo.compose.draw.DrawState
import tilo.compose.draw.drawLayer
import tilo.compose.draw.rememberDrawState

private const val MAP_BACKGROUND_COLOR = 0xFFF2EEE3
private const val CUZK_ORTOFOTO_WMS_URL = "https://ags.cuzk.gov.cz/arcgis1/services/ORTOFOTO/MapServer/WMSServer"
private const val CUZK_ZTM_WMS_URL = "https://ags.cuzk.gov.cz/arcgis1/services/ZTM/MapServer/WMSServer"

private enum class BasemapOption(val title: String) {
    CuzkOrtofoto("CUZK ortofoto"),
    CuzkZtm("CUZK basic map"),
}

private enum class DemoLayerOption(val title: String) {
    DashedPolygons("Dashed polygons"),
    FullLines("Full lines"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Preview
fun App() {
    val ortofotoLayer = rememberWMSTileLayer(
        id = "cuzk-ortofoto",
        capabilitiesUrl = CUZK_ORTOFOTO_WMS_URL,
        layerName = "0",
        projection = Epsg5514Projection,
        format = "image/jpeg",
    )
    val ztmLayer = rememberWMSTileLayer(
        id = "cuzk-ztm",
        capabilitiesUrl = CUZK_ZTM_WMS_URL,
        layerName = "0",
        projection = Epsg5514Projection,
        format = "image/png",
    )
    val dashedPolygonFeatures = remember { buildDashedPolygonLayerFeatures() }
    val fullLineFeatures = remember { buildFullLineLayerFeatures() }

    var savedDrawingFeatures by remember { mutableStateOf<List<Feature>>(emptyList()) }
    val drawState = rememberDrawState(
        onSave = { feature ->
            savedDrawingFeatures = savedDrawingFeatures + feature.withSavedDrawingStyle()
        },
    )

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

    val cameraState = rememberMapCameraState(
        center = Wgs84ToEpsg5514Transformation.sourceToTarget(Point(16.6068, 49.1951)),
        zoom = 11.5,
        config = MapConfig(minZoom = 0.0, maxZoom = 20.0)
            .withTransformation(Wgs84ToEpsg5514Transformation)
            .withTransformation(Epsg5514ToWgs84Transformation),
        projection = Epsg5514Projection,
    )

    MaterialTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
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
                    TiloMap(
                        cameraState = cameraState,
                        tileDecoder = ::decodeImageBitmap,
                        modifier = Modifier.fillMaxSize(),
                        onTapWorld = drawState::onMapTap,
                        invalidationKey = drawState.revision,
                        layers = {
                            when (selectedBasemap) {
                                BasemapOption.CuzkOrtofoto -> tileLayer(ortofotoLayer)
                                BasemapOption.CuzkZtm -> tileLayer(ztmLayer)
                            }
                            if (DemoLayerOption.DashedPolygons in selectedLayers) {
                                featureLayer("dashed-polygons", dashedPolygonFeatures) {
                                    zIndex = 1
                                    projection = Epsg4326Projection
                                    renderStrategy = cachedBitmap(
                                        scale = 1.5,
                                        paddingPx = 192,
                                        invalidateOnZoomDelta = 0.35,
                                    )
                                }
                            }
                            if (DemoLayerOption.FullLines in selectedLayers) {
                                featureLayer("full-lines", fullLineFeatures) {
                                    zIndex = 2
                                    projection = Epsg4326Projection
                                }
                            }
                            featureLayer("saved-drawings", savedDrawingFeatures) {
                                zIndex = 10
                                projection = Epsg5514Projection
                            }
                            +drawLayer(state = drawState, projection = Epsg5514Projection)
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

    return listOf(
        Feature(
            key = "dashed-polygon-west",
            geometry = Polygon(rings = listOf(ring(14.4, 49.75, 0.85, 0.45))),
            label = "Dashed polygon",
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
        ),
        Feature(
            key = "dashed-polygon-east",
            geometry = Polygon(rings = listOf(ring(16.1, 49.95, 0.75, 0.4))),
            label = "Pattern fill",
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
            style = lineStyle {
                stroke(strokeArgb, width = 4.0) {
                    lineCap = LineCap.Round
                    lineJoin = LineJoin.Round
                }
            }
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
