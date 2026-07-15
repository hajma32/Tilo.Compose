package eu.tilo.publication.smoke

import tilo.compose.core.feature.Feature
import tilo.compose.draw.DrawState
import tilo.spatial.SpatialRect

/** References geocore and spatial-index APIs transitively through the published draw coordinate. */
val jvmPublishedApi: List<Any> =
    listOf(
        DrawState::class,
        Feature::class,
        SpatialRect::class,
    )
