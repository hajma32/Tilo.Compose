@file:OptIn(ExperimentalTiloApi::class)

package tilo.samples

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.LineCap
import tilo.compose.core.feature.LineJoin
import tilo.compose.core.feature.PointShape
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon
import tilo.compose.draw.DrawMode
import tilo.compose.draw.DrawState
import tilo.compose.draw.drawLayer
import tilo.compose.draw.rememberDrawState
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.TiloMap
import tilo.compose.dsl.lineStyle
import tilo.compose.dsl.pointStyle
import tilo.compose.dsl.polygonStyle
import tilo.compose.dsl.webMercator
import tilo.compose.ui.DefaultZoomControls
import tilo.compose.ui.defaultAttributionContent
import tilo.compose.ui.defaultScaleBarContent

@Composable
internal fun BoxScope.DrawingSample() {
    val camera = rememberWebMercatorCamera(center = Point(14.42, 50.083), zoom = 13.0)
    var savedFeatures by remember { mutableStateOf<List<Feature>>(emptyList()) }
    val drawState =
        rememberDrawState(
            onSave = { feature -> savedFeatures = savedFeatures + feature.asSavedDrawing() },
        )

    TiloMap(
        cameraState = camera,
        modifier = Modifier.fillMaxSize(),
        onTapWorld = drawState::onMapTap,
        attributionContent = defaultAttributionContent(),
        scaleBarContent = defaultScaleBarContent(),
        cameraControlsContent = { DefaultZoomControls(it) },
        invalidationKey = drawState.revision to savedFeatures.size,
        layers = {
            openStreetMapLayer()
            featureLayer("saved-drawings", savedFeatures) {
                zIndex = 8
                projection = webMercator()
            }
            drawLayer(state = drawState, projection = webMercator())
        },
    )

    SampleInfoCard(
        sample = Sample.Drawing,
        body = "Start drawing, choose a geometry and tap the map. Draft state, history and saving stay explicit.",
        code = "drawLayer(state = drawState)",
    )
    DrawingControls(
        state = drawState,
        savedCount = savedFeatures.size,
        onClearSaved = { savedFeatures = emptyList() },
    )
}

@Composable
private fun BoxScope.DrawingControls(
    state: DrawState,
    savedCount: Int,
    onClearSaved: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 84.dp),
        color = Ink.copy(alpha = .97f),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color(0xFF526159)),
        shape = RoundedCornerShape(3.dp),
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SampleAction(
                    text = if (state.isDrawing) "Finish" else "Start drawing",
                    active = state.isDrawing,
                    onClick = state::toggleDrawing,
                )
                if (state.isDrawing) {
                    DrawMode.entries.forEach { mode ->
                        SampleAction(
                            text = mode.title,
                            active = state.mode == mode,
                            onClick = { state.selectMode(mode) },
                        )
                    }
                }
            }
            when {
                state.isDrawing -> DrawingActions(state)
                savedCount > 0 -> SavedDrawingActions(savedCount, onClearSaved)
            }
        }
    }
}

@Composable
private fun DrawingActions(state: DrawState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SampleAction("Undo", enabled = state.canUndo, onClick = state::undo)
        SampleAction("Redo", enabled = state.canRedo, onClick = state::redo)
        SampleAction("Clear", enabled = state.draftPoints.isNotEmpty(), onClick = state::clear)
        SampleAction("Save", active = true, enabled = state.canSave, onClick = state::save)
    }
}

@Composable
private fun SavedDrawingActions(
    savedCount: Int,
    onClearSaved: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$savedCount saved",
            color = Green,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        Spacer(Modifier.width(10.dp))
        SampleAction("Clear saved", onClick = onClearSaved)
    }
}

@Composable
private fun SampleAction(
    text: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val contentColor =
        when {
            !enabled -> Color(0xFF65716A)
            active -> Color.White
            else -> Color(0xFFE7ECE9)
        }
    Surface(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        color = if (active) Orange else Color.Transparent,
        contentColor = contentColor,
        border = BorderStroke(1.dp, if (active) Orange else Color(0xFF526159)),
        shape = RoundedCornerShape(3.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

private val DrawMode.title: String
    get() =
        when (this) {
            DrawMode.Point -> "Point"
            DrawMode.Line -> "Line"
            DrawMode.Polygon -> "Polygon"
        }

private fun Feature.asSavedDrawing(): Feature =
    copy(
        style =
            when (geometry) {
                is Point ->
                    pointStyle {
                        shape = PointShape.Circle
                        size = 15.dp
                        fill(0xFFF2663B)
                        stroke(0xFFFFFFFF, width = 3.5.dp)
                    }
                is LineString ->
                    lineStyle {
                        casing(0xFFFFFFFF, width = 8.dp) {
                            lineCap = LineCap.Round
                            lineJoin = LineJoin.Round
                        }
                        stroke(0xFFF2663B, width = 4.5.dp) {
                            lineCap = LineCap.Round
                            lineJoin = LineJoin.Round
                        }
                    }
                is Polygon ->
                    polygonStyle {
                        fill(0x55F2663B)
                        casing(0xFFFFFFFF, width = 8.dp) { lineJoin = LineJoin.Round }
                        stroke(0xFFF2663B, width = 4.5.dp) { lineJoin = LineJoin.Round }
                    }
                else -> style
            },
    )
