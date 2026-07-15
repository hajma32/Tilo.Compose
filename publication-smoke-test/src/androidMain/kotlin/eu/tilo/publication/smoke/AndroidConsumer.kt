@file:OptIn(
    tilo.compose.dsl.ExperimentalTiloApi::class,
    tilo.compose.render.ExperimentalTiloRenderingApi::class,
)

package eu.tilo.publication.smoke

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tilo.compose.core.transform.Epsg5514ToWgs84Transformation
import tilo.compose.draw.DrawState
import tilo.compose.dsl.TiloMap
import tilo.compose.dsl.rememberMapCameraState
import tilo.compose.dsl.webMercator
import tilo.compose.render.RenderPoint
import tilo.compose.ui.MapDebugMetrics
import tilo.spatial.SpatialRect

/** References APIs resolved transitively from the published main library and optional draw plugin. */
val androidPublishedApi: List<Any> =
    listOf(
        MapDebugMetrics::class,
        RenderPoint::class,
        Epsg5514ToWgs84Transformation,
        DrawState::class,
        SpatialRect::class,
    )

/** Compiles the exact zero-configuration OSM path advertised as the main installation example. */
@Composable
fun PublishedOsmMap() {
    val camera =
        rememberMapCameraState(
            initialZoom = 2.0,
            projection = webMercator(),
        )

    TiloMap(
        cameraState = camera,
        modifier = Modifier.fillMaxSize(),
    ) {
        osmLayer()
    }
}
