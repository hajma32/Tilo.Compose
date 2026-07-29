package tilo.compose.draw

import tilo.compose.core.layers.LayerSink
import tilo.compose.core.layers.vector.FeatureLayer
import tilo.compose.core.layers.vector.VectorRenderStrategy
import tilo.compose.core.projection.Projection

/** Creates an immediate vector layer that displays the current draft from [state]. */
fun drawLayer(
    state: DrawState,
    id: String = "draw-layer",
    zIndex: Int = 20,
    projection: Projection? = null,
    opacity: Double = 1.0,
): FeatureLayer =
    createDrawLayer(
        state = state,
        id = id,
        zIndex = zIndex,
        opacity = opacity,
        projection = projection,
    )

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
