package tilo.compose.render

import tilo.compose.core.layers.Attribution
import tilo.compose.core.layers.Layer
import tilo.compose.core.layers.LayerGroup

/** An ordered composite layer tree resolved once when layer declarations change. */
internal class ResolvedLayerTree private constructor(
    private val roots: List<ResolvedLayerNode>,
    val key: List<LayerTreeKey>,
) {
    /** Returns active renderable leaves and their opacity inherited from composite ancestors. */
    fun activeLayersAt(zoom: Double): ActiveLayerSet {
        val layers = mutableListOf<Layer>()
        val effectiveOpacities = mutableMapOf<String, Double>()

        fun appendActive(
            nodes: List<ResolvedLayerNode>,
            parentOpacity: Double,
        ) {
            nodes.forEach { node ->
                if (node.layer.isVisibleAt(zoom)) {
                    val effectiveOpacity = parentOpacity * node.layer.opacity
                    if (node.children == null) {
                        layers += node.layer
                        effectiveOpacities[node.layer.id] = effectiveOpacity
                    } else {
                        appendActive(node.children, effectiveOpacity)
                    }
                }
            }
        }

        appendActive(roots, parentOpacity = 1.0)
        return ActiveLayerSet(layers, effectiveOpacities)
    }

    /** Returns active renderable leaves without sorting or rebuilding the tree. */
    fun activeAt(zoom: Double): List<Layer> = activeLayersAt(zoom).layers

    /** Collects attribution from active composite nodes and leaves. */
    fun activeAttributions(zoom: Double): List<Attribution> =
        buildList {
            fun appendActive(nodes: List<ResolvedLayerNode>) {
                nodes.forEach { node ->
                    if (node.layer.isVisibleAt(zoom)) {
                        addAll(node.layer.attributions)
                        node.children?.let(::appendActive)
                    }
                }
            }
            appendActive(roots)
        }.distinctBy { attribution -> attribution.label to attribution.url }

    companion object {
        fun resolve(layers: List<Layer>): ResolvedLayerTree {
            val ids = mutableSetOf<String>()

            fun resolveNodes(siblings: List<Layer>): List<ResolvedLayerNode> =
                siblings
                    .sortedWith(compareBy(Layer::zIndex))
                    .map { layer ->
                        val minZoom = layer.minZoom
                        val maxZoom = layer.maxZoom
                        require(layer.id.isNotBlank()) { "Layer id must not be blank" }
                        require(layer.opacity in 0.0..1.0) {
                            "Layer '${layer.id}' opacity must be between 0.0 and 1.0"
                        }
                        require(minZoom == null || minZoom.isFinite()) {
                            "Layer '${layer.id}' minZoom must be finite"
                        }
                        require(maxZoom == null || maxZoom.isFinite()) {
                            "Layer '${layer.id}' maxZoom must be finite"
                        }
                        require(minZoom == null || maxZoom == null || minZoom <= maxZoom) {
                            "Layer '${layer.id}' minZoom must not be greater than maxZoom"
                        }
                        require(ids.add(layer.id)) {
                            "Duplicate layer id '${layer.id}'. Layer IDs must be unique within one map."
                        }
                        ResolvedLayerNode(
                            layer = layer,
                            children = if (layer is LayerGroup) resolveNodes(layer.children) else null,
                        )
                    }

            val roots = resolveNodes(layers)
            return ResolvedLayerTree(
                roots = roots,
                key = roots.map(ResolvedLayerNode::key),
            )
        }
    }
}

private data class ResolvedLayerNode(
    val layer: Layer,
    val children: List<ResolvedLayerNode>?,
) {
    val key: LayerTreeKey
        get() =
            LayerTreeKey(
                id = layer.id,
                zIndex = layer.zIndex,
                opacity = layer.opacity,
                children = children?.map(ResolvedLayerNode::key).orEmpty(),
            )
}

internal data class LayerTreeKey(
    val id: String,
    val zIndex: Int,
    val opacity: Double,
    val children: List<LayerTreeKey>,
)

internal data class ActiveLayerSet(
    val layers: List<Layer>,
    val effectiveOpacitiesByLayerId: Map<String, Double>,
)
