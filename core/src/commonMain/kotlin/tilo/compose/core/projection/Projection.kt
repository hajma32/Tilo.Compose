package tilo.compose.core.projection

/**
 * Coordinate system identity used by map/layer metadata and transformation registry.
 *
 * Screen/world conversion stays in viewport math, but projections can normalize
 * their raw CRS units to shared map zoom units via [worldUnitsPerMapUnit].
 */
interface Projection {
    val id: String

    /**
     * Raw CRS units represented by one map world unit used in screen/zoom math.
     * Geographic lon/lat uses 1.0; projected meter-based systems normalize to
     * meters-per-degree-at-equator so zoom semantics stay consistent.
     */
    val worldUnitsPerMapUnit: Double
        get() = 1.0
}
