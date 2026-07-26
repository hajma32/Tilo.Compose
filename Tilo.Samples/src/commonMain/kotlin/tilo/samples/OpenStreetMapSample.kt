@file:OptIn(ExperimentalTiloApi::class)

package tilo.samples

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.TiloMap
import tilo.compose.dsl.rememberMapDiagnosticsState
import tilo.compose.ui.DefaultMapDebugOverlay
import tilo.compose.ui.defaultAttributionContent
import tilo.compose.ui.defaultCameraControlsContent
import tilo.compose.ui.defaultScaleBarContent

@Composable
internal fun BoxScope.OpenStreetMapSample() {
    val camera =
        rememberWebMercatorCamera(
            zoom = 12.0,
            cameraBounds = webMercatorBounds(west = 14.2, south = 49.95, east = 14.7, north = 50.2),
        )
    val diagnostics = rememberMapDiagnosticsState()
    val defaultCameraControls = defaultCameraControlsContent()

    TiloMap(
        cameraState = camera,
        diagnosticsState = diagnostics,
        modifier = Modifier.fillMaxSize(),
        attributionContent = defaultAttributionContent(),
        scaleBarContent = defaultScaleBarContent(),
        cameraControlsContent = { state ->
            defaultCameraControls(state)
            DefaultMapDebugOverlay(
                cameraState = state,
                diagnosticsState = diagnostics,
                alignment = Alignment.CenterStart,
            )
        },
        layers = { openStreetMapLayer() },
    )

    SampleInfoCard(
        sample = Sample.OpenStreetMap,
        body = "One XYZ layer with bounded panning and a safe zoom range.",
        code = "MapConfig(minZoom, maxZoom, cameraBounds = area)",
    )
}
