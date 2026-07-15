@file:OptIn(ExperimentalTiloApi::class)

package tilo.samples

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.TiloMap
import tilo.compose.ui.DefaultZoomControls
import tilo.compose.ui.defaultAttributionContent
import tilo.compose.ui.defaultScaleBarContent

@Composable
internal fun BoxScope.OpenStreetMapSample() {
    val camera = rememberWebMercatorCamera(zoom = 12.0)

    TiloMap(
        cameraState = camera,
        modifier = Modifier.fillMaxSize(),
        attributionContent = defaultAttributionContent(),
        scaleBarContent = defaultScaleBarContent(),
        cameraControlsContent = { DefaultZoomControls(it) },
        layers = { openStreetMapLayer() },
    )

    SampleInfoCard(
        sample = Sample.OpenStreetMap,
        body = "One camera, one XYZ layer. Pan, pinch and zoom — the smallest useful Tilo map.",
        code = "xyzTileLayer(\"osm\", url)",
    )
}
