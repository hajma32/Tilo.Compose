package tilo.compose.dsl

import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.FeatureLayerStyle
import tilo.compose.core.feature.PointStyle

internal fun validatePointIconReferences(
    layerId: String,
    features: List<Feature>,
    layerStyle: FeatureLayerStyle,
    registeredIconIds: Set<String>,
) {
    val referencedIconIds =
        buildSet {
            layerStyle.point
                ?.icon
                ?.id
                ?.let(::add)
            layerStyle.selectedPoint
                ?.icon
                ?.id
                ?.let(::add)
            features.forEach { feature ->
                (feature.style as? PointStyle)?.icon?.id?.let(::add)
                (feature.selectedStyle as? PointStyle)?.icon?.id?.let(::add)
            }
        }
    val missingIconIds = referencedIconIds - registeredIconIds
    require(missingIconIds.isEmpty()) {
        "Feature layer '$layerId' references unregistered point icon IDs: " +
            missingIconIds.sorted().joinToString() +
            ". Register each icon with pointIcon(id, painter)."
    }
}
