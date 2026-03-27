package tilo.compose.core.feature.source

import tilo.compose.core.feature.Feature
import tilo.compose.core.map.Map

/**
 * Simple in-memory list-backed feature source.
 */
class FeatureListSource(
    private val features: List<Feature>
) : FeatureSource {
    override fun getFeatures(map: Map): List<Feature> = features
}
