package tilo.compose.core.feature

import tilo.compose.core.geometry.Geometry

/**
 * Small container for arbitrary feature-associated data. Can hold any platform-safe payload.
 */
data class Data(val payload: Any?)

/**
 * Feature composes geometry with presentation data (style, label, callout) and optional arbitrary data.
 *
 * Important: `key` is required and used as a stable identifier for diffing/rendering. Provide a stable
 * value across updates to allow efficient diffs (avoid using array indexes or ephemeral values).
 */
data class Feature(
    val geometry: Geometry,
    val key: String,
    val style: BaseStyle? = null,
    val label: String? = null,
    val callout: Callout? = null,
    val data: Data? = null
)
