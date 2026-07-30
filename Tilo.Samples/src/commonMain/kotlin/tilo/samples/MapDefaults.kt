@file:OptIn(ExperimentalTiloApi::class)

package tilo.samples

import androidx.compose.runtime.Composable
import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapConfig
import tilo.compose.core.transform.Epsg5514ToWgs84Transformation
import tilo.compose.core.transform.WebMercatorToWgs84Transformation
import tilo.compose.core.transform.Wgs84ToEpsg5514Transformation
import tilo.compose.core.transform.Wgs84ToWebMercatorTransformation
import tilo.compose.dsl.ExperimentalTiloApi
import tilo.compose.dsl.MapCameraState
import tilo.compose.dsl.MapLayerBuilder
import tilo.compose.dsl.rememberMapCameraState
import tilo.compose.dsl.sjtsk
import tilo.compose.dsl.webMercator

internal const val CUZK_ORTHOPHOTO_URL =
    "https://ags.cuzk.gov.cz/arcgis1/services/ORTOFOTO/MapServer/WMSServer"

private val PRAGUE = Point(14.4378, 50.0755)
private val DEFAULT_MAP_CONFIG = MapConfig(minZoom = 1.0, maxZoom = 20.0)
private val WEB_MERCATOR_MAP_CONFIG =
    DEFAULT_MAP_CONFIG
        .withTransformation(Wgs84ToWebMercatorTransformation)
        .withTransformation(WebMercatorToWgs84Transformation)

// WGS84 extent published in the ČÚZK ZTM 100 metadata for Czechia.
private val CZECH_REPUBLIC_SJTSK_BOUNDS =
    transformedBounds(
        west = 12.09,
        south = 48.55,
        east = 18.86,
        north = 51.06,
        transform = Wgs84ToEpsg5514Transformation::sourceToTarget,
    )
private val SJTSK_MAP_CONFIG =
    MapConfig(
        minZoom = 9.0,
        maxZoom = 16.0,
        cameraBounds = CZECH_REPUBLIC_SJTSK_BOUNDS,
    ).withTransformation(Wgs84ToEpsg5514Transformation)
        .withTransformation(Epsg5514ToWgs84Transformation)

@Composable
internal fun rememberWebMercatorCamera(
    center: Point = PRAGUE,
    zoom: Double,
    cameraBounds: BoundingBox? = null,
): MapCameraState =
    rememberMapCameraState(
        initialCenter = Wgs84ToWebMercatorTransformation.sourceToTarget(center),
        initialZoom = zoom,
        projection = webMercator(),
        config = WEB_MERCATOR_MAP_CONFIG.copy(cameraBounds = cameraBounds),
    )

internal fun webMercatorBounds(
    west: Double,
    south: Double,
    east: Double,
    north: Double,
): BoundingBox = transformedBounds(west, south, east, north, Wgs84ToWebMercatorTransformation::sourceToTarget)

@Composable
internal fun rememberSjtskCamera(): MapCameraState =
    rememberMapCameraState(
        initialCenter = Wgs84ToEpsg5514Transformation.sourceToTarget(PRAGUE),
        initialZoom = 12.2,
        projection = sjtsk(),
        config = SJTSK_MAP_CONFIG,
    )

private fun transformedBounds(
    west: Double,
    south: Double,
    east: Double,
    north: Double,
    transform: (Point) -> Point,
): BoundingBox =
    BoundingBox.fromPoints(
        listOf(
            transform(Point(west, south)),
            transform(Point(west, north)),
            transform(Point(east, north)),
            transform(Point(east, south)),
        ),
    )

internal fun MapLayerBuilder.openStreetMapLayer(opacity: Double = 1.0) {
    osmLayer(id = "osm-standard", opacity = opacity)
}
