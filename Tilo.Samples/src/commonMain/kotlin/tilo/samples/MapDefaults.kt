@file:OptIn(ExperimentalTiloApi::class)

package tilo.samples

import androidx.compose.runtime.Composable
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapConfig
import tilo.compose.core.transform.Epsg5514ToWgs84Transformation
import tilo.compose.core.transform.WebMercatorToWgs84Transformation
import tilo.compose.core.transform.Wgs84ToEpsg5514Transformation
import tilo.compose.core.transform.Wgs84ToWebMercatorTransformation
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.MapCameraState
import tilo.compose.dsl.MapLayerBuilder
import tilo.compose.dsl.attribution
import tilo.compose.dsl.rememberMapCameraState
import tilo.compose.dsl.sjtsk
import tilo.compose.dsl.webMercator

internal const val CUZK_ORTHOPHOTO_URL =
    "https://ags.cuzk.gov.cz/arcgis1/services/ORTOFOTO/MapServer/WMSServer"

private const val OPEN_STREET_MAP_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
private val PRAGUE = Point(14.4378, 50.0755)
private val DEFAULT_MAP_CONFIG = MapConfig(minZoom = 1.0, maxZoom = 20.0)

@Composable
internal fun rememberWebMercatorCamera(
    center: Point = PRAGUE,
    zoom: Double,
): MapCameraState =
    rememberMapCameraState(
        center = Wgs84ToWebMercatorTransformation.sourceToTarget(center),
        zoom = zoom,
        projection = webMercator(),
        config =
            DEFAULT_MAP_CONFIG
                .withTransformation(Wgs84ToWebMercatorTransformation)
                .withTransformation(WebMercatorToWgs84Transformation),
    )

@Composable
internal fun rememberSjtskCamera(): MapCameraState =
    rememberMapCameraState(
        center = Wgs84ToEpsg5514Transformation.sourceToTarget(PRAGUE),
        zoom = 12.2,
        projection = sjtsk(),
        config =
            DEFAULT_MAP_CONFIG
                .withTransformation(Wgs84ToEpsg5514Transformation)
                .withTransformation(Epsg5514ToWgs84Transformation),
    )

internal fun MapLayerBuilder.openStreetMapLayer() {
    xyzTileLayer(
        id = "osm-standard",
        urlTemplate = OPEN_STREET_MAP_URL,
        projection = webMercator(),
        prefetchMargin = 1,
        attribution =
            attribution(
                label = "© OpenStreetMap contributors",
                url = "https://www.openstreetmap.org/copyright",
            ),
    )
}
