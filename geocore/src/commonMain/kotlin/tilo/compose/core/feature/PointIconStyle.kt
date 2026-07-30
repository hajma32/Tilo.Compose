package tilo.compose.core.feature

/**
 * Reference to an icon registered on the containing feature layer.
 *
 * `size` uses logical display units (DIP/DP). A null `tint` preserves the
 * icon's original colors.
 */
data class PointIconStyle(
    val id: String,
    val size: Double = 14.0,
    val tint: ColorValue? = null,
    val opacity: Double = 1.0,
) {
    init {
        require(id.isNotBlank()) { "Point icon id must not be blank" }
        require(size > 0.0 && size.isFinite()) { "Point icon size must be finite and greater than zero" }
        require(opacity in 0.0..1.0) { "Point icon opacity must be between zero and one" }
    }
}
