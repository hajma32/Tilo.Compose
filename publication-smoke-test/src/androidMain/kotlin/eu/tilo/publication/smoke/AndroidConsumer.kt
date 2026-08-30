@file:OptIn(
    tilo.compose.dsl.ExperimentalTiloApi::class,
    tilo.compose.draw.ExperimentalTiloDrawApi::class,
    tilo.compose.render.ExperimentalTiloRenderingApi::class,
)

package eu.tilo.publication.smoke

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.LabelTextAlign
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.CameraPosition
import tilo.compose.core.transform.Epsg5514ToWgs84Transformation
import tilo.compose.draw.DrawState
import tilo.compose.draw.drawLayer
import tilo.compose.draw.rememberDrawState
import tilo.compose.dsl.TiloMap
import tilo.compose.dsl.MapRenderMetrics
import tilo.compose.dsl.featureLayerStyle
import tilo.compose.dsl.rememberMapCameraState
import tilo.compose.dsl.rememberMapDiagnosticsState
import tilo.compose.dsl.rememberRasterLayerState
import tilo.compose.dsl.webMercator
import tilo.compose.render.RenderPoint
import tilo.compose.ui.MapDebugMetrics
import tilo.spatial.SpatialRect

/** Compiles the explicit lifecycle and save-key contract of the published Draw API. */
val publishedDrawLifecycle: (DrawState) -> Feature? = { state ->
    state.startDrawing()
    state.onMapTap(Point(14.0, 50.0))
    val saved = state.save(key = "publication-smoke-drawing")
    state.stopDrawing()
    saved
}

/** References APIs resolved transitively from the published main library and optional draw plugin. */
val androidPublishedApi: List<Any> =
    listOf(
        MapDebugMetrics::class,
        MapRenderMetrics::class,
        RenderPoint::class,
        Epsg5514ToWgs84Transformation,
        DrawState::class,
        publishedDrawLifecycle,
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
            labelsVisible = false
        }
    }

/** Compiles the exact zero-configuration OSM path advertised as the main installation example. */
@Composable
fun PublishedOsmMap() {
    val camera =
        rememberMapCameraState(
            initialPosition = CameraPosition(center = Point(0.0, 0.0), zoom = 2.0),
            projection = webMercator(),
        )
    val osmState = rememberRasterLayerState()
    val diagnostics = rememberMapDiagnosticsState()
    val drawState = rememberDrawState()

    TiloMap(
        cameraState = camera,
        diagnosticsState = diagnostics,
        modifier = Modifier.fillMaxSize(),
    ) {
        osmLayer {
            state = osmState
        }
        drawLayer(state = drawState, projection = webMercator())
    }
}
