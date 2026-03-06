package tilo.compose.core.projection

/**
 * Coordinate system identity used by map/layer metadata and transformation registry.
 *
 * Screen/world conversion belongs to viewport math, not CRS descriptors.
 */
interface Projection {
    val id: String
}
