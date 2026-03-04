package tilo.compose.core.feature

/**
 * Simple style holder for features. Extend later with richer style model.
 */
data class BaseStyle(
    val strokeColor: Long? = null,
    val fillColor: Long? = null,
    val strokeWidth: Double? = null
)

