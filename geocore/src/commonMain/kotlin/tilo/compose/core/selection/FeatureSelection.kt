package tilo.compose.core.selection

import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.ScreenPoint

/**
 * One selectable vector feature hit by a map tap.
 *
 * A single tap can hit multiple features. Selection callbacks return candidates
 * ordered from top-most to bottom-most so applications can choose whether to
 * pick the first item, show a chooser, or apply their own priority rules.
 *
 * `worldPoint` is expressed in the map projection. `screenPoint` is expressed
 * in physical pixels from the top-left corner of the map viewport.
 */
data class FeatureSelection(
    val layerId: String,
    val feature: Feature,
    val worldPoint: Point,
    val screenPoint: ScreenPoint,
) {
    val ref: FeatureSelectionRef
        get() = FeatureSelectionRef(layerId = layerId, featureKey = feature.key)
}

/**
 * Stable reference to a feature in a layer, suitable for storing selection state.
 */
data class FeatureSelectionRef(
    val layerId: String,
    val featureKey: String,
)
