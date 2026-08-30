@file:OptIn(ExperimentalTiloDrawApi::class)

package tilo.compose.draw

import tilo.compose.core.layers.LayerSink
import tilo.compose.core.layers.vector.FeatureLayer
import tilo.compose.core.layers.vector.VectorRenderStrategy
import tilo.compose.core.projection.Projection

private fun createDrawLayer(
    state: DrawState,
    id: String,
    zIndex: Int,
    opacity: Double,
    projection: Projection?,
): FeatureLayer =
    FeatureLayer(
        id = id,
        zIndex = zIndex,
        opacity = opacity,
        projection = projection,
        features = if (state.isDrawing) state.draftFeatures else emptyList(),
        renderStrategy = VectorRenderStrategy.Immediate,
    )

/** Adds the current draft from [state] to this layer sink. */
@ExperimentalTiloDrawApi
fun LayerSink.drawLayer(
    state: DrawState,
    id: String = "draw-layer",
    zIndex: Int = 20,
    projection: Projection? = null,
    opacity: Double = 1.0,
) {
    layer(
        createDrawLayer(
            state = state,
            id = id,
            zIndex = zIndex,
            opacity = opacity,
            projection = projection,
        ),
    )
}
