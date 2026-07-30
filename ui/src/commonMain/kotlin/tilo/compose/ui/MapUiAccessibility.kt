package tilo.compose.ui

import androidx.compose.runtime.Immutable
import tilo.compose.core.layers.Attribution
import tilo.compose.core.scale.ScaleBar

/**
 * Optional localized accessibility text for Tilo's default map UI.
 *
 * Null values use strings from this artifact's Compose resources. Description factories make
 * dynamic scale and attribution text fully replaceable by applications.
 */
@Immutable
data class MapUiAccessibility(
    val zoomInDescription: String? = null,
    val zoomOutDescription: String? = null,
    val resetNorthDescription: String? = null,
    val scaleBarDescription: ((ScaleBar) -> String)? = null,
    val attributionClickLabel: ((Attribution) -> String)? = null,
) {
    init {
        require(zoomInDescription == null || zoomInDescription.isNotBlank()) {
            "zoomInDescription must be null or non-blank"
        }
        require(zoomOutDescription == null || zoomOutDescription.isNotBlank()) {
            "zoomOutDescription must be null or non-blank"
        }
        require(resetNorthDescription == null || resetNorthDescription.isNotBlank()) {
            "resetNorthDescription must be null or non-blank"
        }
    }
}

internal const val ZOOM_IN_TRAVERSAL_INDEX = 1.0f
internal const val ZOOM_OUT_TRAVERSAL_INDEX = 2.0f
internal const val COMPASS_TRAVERSAL_INDEX = 3.0f
internal const val ATTRIBUTION_TRAVERSAL_INDEX = 4.0f
