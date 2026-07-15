@file:OptIn(ExperimentalTiloApi::class)

package tilo.samples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.TiloMap
import tilo.compose.dsl.attribution
import tilo.compose.dsl.rememberWMSLayer
import tilo.compose.dsl.sjtsk
import tilo.compose.ui.DefaultZoomControls
import tilo.compose.ui.defaultAttributionContent
import tilo.compose.ui.defaultScaleBarContent

@Composable
internal fun BoxScope.NonMercatorSample() {
    val camera = rememberSjtskCamera()
    val ortofoto =
        rememberWMSLayer(
            id = "cuzk-ortofoto-5514",
            capabilitiesUrl = CUZK_ORTHOPHOTO_URL,
            layerName = "0",
            projection = sjtsk(),
            format = "image/jpeg",
            attribution = attribution("ČÚZK Ortofoto · EPSG:5514"),
        )

    TiloMap(
        cameraState = camera,
        modifier = Modifier.fillMaxSize().background(Color(0xFFDBDED3)),
        attributionContent = defaultAttributionContent(),
        scaleBarContent = defaultScaleBarContent(),
        cameraControlsContent = { DefaultZoomControls(it) },
        layers = { wmsTileLayer(ortofoto) },
    )

    SampleInfoCard(
        sample = Sample.NonMercator,
        body = "The map, WMS grid and camera all run directly in S‑JTSK. No Web Mercator detour required.",
        code = "projection = sjtsk() // EPSG:5514",
    )
    MapPill(
        when {
            ortofoto.isLoading -> "Reading ČÚZK WMS capabilities…"
            ortofoto.error != null -> "ČÚZK WMS is unavailable"
            else -> "Live ČÚZK ortofoto · EPSG:5514"
        },
    )
}
