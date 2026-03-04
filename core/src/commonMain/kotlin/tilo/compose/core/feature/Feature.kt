package tilo.compose.core.feature

import tilo.compose.core.geometry.Geometry

/**
 * Small container for arbitrary feature-associated data. Can hold any platform-safe payload.
 */
data class Data(val payload: Any?)

/**
 * Feature composes geometry with presentation data (style, label, callout) and optional arbitrary data.
 */
data class Feature(
    val geometry: Geometry,
    val id: String? = null,
    val style: BaseStyle? = null,
    val label: String? = null,
    val callout: Callout? = null,
    val data: Data? = null
)
