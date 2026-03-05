package tilo.compose.core.layers.impl

import tilo.compose.core.feature.source.FeatureSource
import tilo.compose.core.layers.VectorLayer

/**
 * Simple concrete VectorLayer example.
 */
class SimpleVectorLayer(
    override val id: String,
    override val source: FeatureSource
) : VectorLayer {
    override fun update() {
        // no-op
    }

    override fun dispose() {
        // no-op
    }
}
