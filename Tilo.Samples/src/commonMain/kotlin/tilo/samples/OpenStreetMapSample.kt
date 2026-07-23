@file:OptIn(ExperimentalTiloApi::class)

package tilo.samples

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.TiloMap
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

    TiloMap(
        cameraState = camera,
        modifier = Modifier.fillMaxSize(),
        attributionContent = defaultAttributionContent(),
        scaleBarContent = defaultScaleBarContent(),
        cameraControlsContent = defaultCameraControlsContent(),
        layers = { openStreetMapLayer() },
    )

    SampleInfoCard(
        sample = Sample.OpenStreetMap,
        body = "One XYZ layer with bounded panning and a safe zoom range.",
        code = "MapConfig(minZoom, maxZoom, cameraBounds = area)",
    )
}
