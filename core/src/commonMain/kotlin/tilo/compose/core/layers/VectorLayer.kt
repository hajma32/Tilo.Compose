package tilo.compose.core.layers

import tilo.compose.core.feature.source.FeatureSource

/**
 * A layer that provides vector features from a `FeatureSource`.
 *
 * The renderer queries `FeatureSource` for features to render. The layer keeps
 * an identity and lifecycle hooks.
 */
interface VectorLayer : Layer {
    /**
     * Source of vector features for this layer.
     */
    val source: FeatureSource
}
