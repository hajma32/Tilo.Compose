package tilo.compose.core.feature

import tilo.compose.core.geometry.Geometry

/**
 * Opaque application-owned payload associated with a feature.
 *
 * Tilo never interprets or serializes this value. Applications are responsible
 * for using a payload that is valid on every platform where the feature travels.
 */
data class Data(
    val payload: Any?,
)

/**
 * Feature composes geometry with presentation data (style and label) and optional arbitrary data.
 *
 * Important: `key` is required and used as a stable identifier for diffing/rendering. Provide a stable
 * value across updates to allow efficient diffs (avoid using array indexes or ephemeral values).
 */
data class Feature(
    val geometry: Geometry,
    val key: String,
    val style: GeometryStyle? = null,
    val selectedStyle: GeometryStyle? = null,
    val label: String? = null,
    val labelPriority: Int? = null,
    val labelStyle: LabelStyle? = null,
    val selectedLabelStyle: LabelStyle? = null,
    val data: Data? = null,
)
