package tilo.compose.draw

import tilo.compose.core.layers.vector.FeatureLayer
import tilo.compose.core.layers.vector.VectorRenderStrategy
import tilo.compose.core.projection.Projection

fun DrawLayer(
    state: DrawState,
    id: String = "draw-layer",
    zIndex: Int = 20,
    projection: Projection? = null,
): FeatureLayer =
    FeatureLayer(
        id = id,
        zIndex = zIndex,
        projection = projection,
        features = if (state.isDrawing) state.draftFeatures else emptyList(),
        renderStrategy = VectorRenderStrategy.Immediate,
    )
