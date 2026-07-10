package tilo.compose.core.layers.vector

import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.source.FeatureListSource
import tilo.compose.core.projection.Projection

/**
 * A [VectorLayer] backed by a static in-memory list of features.
 *
 * @param id Unique layer identifier.
 * @param zIndex Draw order relative to other layers (lower = drawn first).
 * @param projection Source CRS of the provided features, or null if features are already in the
 *   map CRS.
 * @param features Features to display.
 */
class FeatureLayer(
    override val id: String,
    override val zIndex: Int = 0,
    override val projection: Projection? = null,
    features: List<Feature>
) : VectorLayer {
    override val source = FeatureListSource(features, projection)
}
