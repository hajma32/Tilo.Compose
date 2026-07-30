package tilo.compose.core.layers.vector

import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.FeatureLayerStyle
import tilo.compose.core.feature.source.FeatureListSource
import tilo.compose.core.layers.Attribution
import tilo.compose.core.projection.Projection

/**
 * A [VectorLayer] backed by a static in-memory list of features.
 *
 * @param id Unique layer identifier.
 * @param zIndex Draw order relative to other layers (lower = drawn first).
 * @param opacity Final compositing opacity in the inclusive range `0.0..1.0`.
 * @param projection Source CRS of the provided features, or null if features are already in the
 *   map CRS.
 * @param features Features to display.
 * @param renderStrategy Rendering strategy preferred by this layer.
 */
class FeatureLayer(
    override val id: String,
    override val zIndex: Int = 0,
    override val visible: Boolean = true,
    override val minZoom: Double? = null,
    override val maxZoom: Double? = null,
    override val projection: Projection? = null,
    override val attributions: List<Attribution> = emptyList(),
    features: List<Feature>,
    override val renderStrategy: VectorRenderStrategy = VectorRenderStrategy.Immediate,
    override val style: FeatureLayerStyle = FeatureLayerStyle(),
    override val opacity: Double = 1.0,
) : VectorLayer {
    init {
        require(opacity in 0.0..1.0) { "opacity must be between 0.0 and 1.0" }
        require(minZoom == null || maxZoom == null || minZoom <= maxZoom) {
            "minZoom must not be greater than maxZoom"
        }
    }

    override val source = FeatureListSource(features, projection)
}
