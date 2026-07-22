@file:OptIn(
    tilo.compose.dsl.ExperimentalTiloApi::class,
    tilo.compose.render.ExperimentalTiloRenderingApi::class,
)

package eu.tilo.publication.smoke

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tilo.compose.core.feature.LabelTextAlign
import tilo.compose.core.transform.Epsg5514ToWgs84Transformation
import tilo.compose.draw.DrawState
import tilo.compose.dsl.TiloMap
import tilo.compose.dsl.featureLayerStyle
import tilo.compose.dsl.rememberMapCameraState
import tilo.compose.dsl.rememberRasterLayerState
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

/** Compiles the public zoom-style, label-alignment, and additional-width casing DSL. */
val publishedZoomStyle =
    featureLayerStyle {
        line {
            casing(0xFFFFFFFF, width = 2.dp)
            stroke(0xFF1E88E5, width = 6.dp)
        }
        label { textAlign = LabelTextAlign.Center }
        zoom(minZoom = 14.0) {
            line {
                casing(0xFFFFFFFF, width = 2.dp)
                stroke(0xFF1E88E5, width = 20.dp)
            }
            hideLabels()
        }
    }

/** Compiles the exact zero-configuration OSM path advertised as the main installation example. */
@Composable
fun PublishedOsmMap() {
    val camera =
        rememberMapCameraState(
            initialZoom = 2.0,
            projection = webMercator(),
        )
    val osmState = rememberRasterLayerState()

    TiloMap(
        cameraState = camera,
        modifier = Modifier.fillMaxSize(),
    ) {
        osmLayer(state = osmState)
    }
}
