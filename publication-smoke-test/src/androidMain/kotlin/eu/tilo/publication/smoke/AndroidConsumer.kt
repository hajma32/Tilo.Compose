package eu.tilo.publication.smoke

import tilo.compose.core.transform.Epsg5514ToWgs84Transformation
import tilo.compose.draw.DrawState
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
