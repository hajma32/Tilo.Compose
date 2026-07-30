package tilo.compose.core.layers

/**
 * A composite layer that keeps multiple child layers in one ordered map-layer slot.
 *
 * `zIndex` orders the complete group among its siblings. Child layer z-indices are local to this
 * group, so layers outside the group never interleave with its children. Group visibility and zoom
 * limits apply to every descendant in addition to each descendant's own constraints. Group opacity
 * is multiplied with each descendant's opacity.
 *
 * Child layer IDs remain globally unique within a map. Grouping a layer does not change its ID.
 */
class LayerGroup(
    override val id: String,
    children: List<Layer>,
    override val zIndex: Int = 0,
    override val visible: Boolean = true,
    override val minZoom: Double? = null,
    override val maxZoom: Double? = null,
    override val attributions: List<Attribution> = emptyList(),
    override val opacity: Double = 1.0,
) : Layer {
    /** Immutable snapshot of the layers contained by this group. */
    val children: List<Layer> = children.toList()

    init {
        require(opacity in 0.0..1.0) { "opacity must be between 0.0 and 1.0" }
        require(minZoom == null || maxZoom == null || minZoom <= maxZoom) {
            "minZoom must not be greater than maxZoom"
        }
    }
}
