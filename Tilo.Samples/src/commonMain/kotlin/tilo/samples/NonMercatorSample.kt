@file:OptIn(ExperimentalTiloApi::class)

package tilo.samples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.PointShape
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.raster.WmsImageFormat
import tilo.compose.core.transform.Wgs84ToEpsg5514Transformation
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.RasterLayerAvailability
import tilo.compose.dsl.RasterLayerStatus
import tilo.compose.dsl.TiloMap
import tilo.compose.dsl.attribution
import tilo.compose.dsl.epsg5514
import tilo.compose.dsl.features
import tilo.compose.dsl.mediumLabelStyle
import tilo.compose.dsl.pointStyle
import tilo.compose.dsl.rememberRasterLayerState
import tilo.compose.ui.defaultAttributionContent
import tilo.compose.ui.defaultCameraControlsContent
import tilo.compose.ui.defaultScaleBarContent

@Composable
internal fun BoxScope.NonMercatorSample() {
    val camera = rememberSjtskCamera()
    val wmsState = rememberRasterLayerState()
    val pragueReference = remember { transformedPragueReference() }

    TiloMap(
        cameraState = camera,
        modifier = Modifier.fillMaxSize().background(Color(0xFFDBDED3)),
        attributionContent = defaultAttributionContent(),
        scaleBarContent = defaultScaleBarContent(),
        cameraControlsContent = defaultCameraControlsContent(),
        layers = {
            wmsTileLayer(
                id = "cuzk-ortofoto-5514",
                capabilitiesUrl = CUZK_ORTHOPHOTO_URL,
                layerNames = listOf("0"),
                projection = epsg5514(),
            ) {
                format = WmsImageFormat.Jpeg
                attributions =
                    listOf(
                        attribution(
                            "© Český úřad zeměměřický a katastrální · Ortofoto České republiky · " +
                                "WMS služba v souřadnicovém systému S‑JTSK / Křovák East North (EPSG:5514)",
                        ),
                    )
                state = wmsState
            }
            featureLayer("prague-wgs84-reference", pragueReference) {
                zIndex = 10
                projection = epsg5514()
            }
        },
    )

    SampleInfoCard(
        sample = Sample.NonMercator,
        body =
            "The map and WMS grid run directly in S‑JTSK. The camera stays within Czechia " +
                "between zoom levels 9 and 16.",
        code = "MapConfig(minZoom = 9.0, maxZoom = 16.0, cameraBounds = czechia)",
    )
    MapPill(
        when (wmsState.status) {
            RasterLayerStatus.Idle -> "Preparing ČÚZK WMS…"
            RasterLayerStatus.Loading -> "Reading ČÚZK WMS capabilities…"
            RasterLayerStatus.Ready ->
                when (wmsState.availability) {
                    RasterLayerAvailability.Offline -> "ČÚZK WMS is offline · retry available"
                    RasterLayerAvailability.Degraded -> "ČÚZK WMS has partial tile failures"
                    else -> "Live ČÚZK ortofoto · EPSG:5514"
                }
            is RasterLayerStatus.Failed -> "ČÚZK WMS is unavailable"
        },
    )
}

private fun transformedPragueReference(): List<Feature> {
    val pragueWgs84 = Point(x = 14.4378, y = 50.0755)
    val pragueSjtsk = Wgs84ToEpsg5514Transformation.sourceToTarget(pragueWgs84)

    return features {
        point(key = "prague", point = pragueSjtsk) {
            label = "Praha · WGS84 → EPSG:5514"
            style =
                pointStyle {
                    shape = PointShape.Circle
                    size = 22.dp
                    fill(0xFFF2663B)
                    stroke(0xFFFFFFFF, width = 4.dp)
                }
            labelStyle = mediumLabelStyle { color(0xFF17201C) }
        }
    }
}
