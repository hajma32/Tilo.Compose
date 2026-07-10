package tilo.compose.draw

import tilo.compose.core.layers.LayerSink
import tilo.compose.core.layers.vector.FeatureLayer
import tilo.compose.core.layers.vector.VectorRenderStrategy
import tilo.compose.core.projection.Projection

fun drawLayer(
    state: DrawState,
    id: String = "draw-layer",
    zIndex: Int = 20,
    projection: Projection? = null,
): FeatureLayer =
    createDrawLayer(
        state = state,
        id = id,
        zIndex = zIndex,
        projection = projection,
    )

private fun createDrawLayer(
    state: DrawState,
    id: String,
    zIndex: Int,
    projection: Projection?,
): FeatureLayer =
    FeatureLayer(
        id = id,
        zIndex = zIndex,
        projection = projection,
        features = if (state.isDrawing) state.draftFeatures else emptyList(),
        renderStrategy = VectorRenderStrategy.Immediate,
    )

fun LayerSink.drawLayer(
    state: DrawState,
    id: String = "draw-layer",
    zIndex: Int = 20,
    projection: Projection? = null,
) {
    layer(
        createDrawLayer(
            state = state,
            id = id,
            zIndex = zIndex,
            projection = projection,
        )
    )
}
