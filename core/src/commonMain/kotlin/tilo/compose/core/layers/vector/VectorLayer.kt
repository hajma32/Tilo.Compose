package tilo.compose.core.layers.vector

import tilo.compose.core.feature.source.FeatureSource
import tilo.compose.core.layers.Layer

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
