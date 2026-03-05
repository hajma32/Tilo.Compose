package tilo.compose.core.feature.source

import tilo.compose.core.feature.Feature
import tilo.compose.core.map.Viewport

/**
 * Source of vector features for a `VectorLayer`.
 */
interface FeatureSource {
    /**
     * Return features that are relevant for the provided `viewport`.
     */
    fun getFeatures(viewport: Viewport): List<Feature>
}

