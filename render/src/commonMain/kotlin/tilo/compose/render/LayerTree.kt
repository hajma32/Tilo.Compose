package tilo.compose.render

import tilo.compose.core.layers.Attribution
import tilo.compose.core.layers.Layer
import tilo.compose.core.layers.LayerGroup

/** An ordered composite layer tree resolved once when layer declarations change. */
internal class ResolvedLayerTree private constructor(
    private val roots: List<ResolvedLayerNode>,
    val key: List<LayerTreeKey>,
) {
    /** Returns active renderable leaves without sorting or rebuilding the tree. */
    fun activeAt(zoom: Double): List<Layer> =
        buildList {
            fun appendActive(nodes: List<ResolvedLayerNode>) {
                nodes.forEach { node ->
                    if (node.layer.isVisibleAt(zoom)) {
                        if (node.children == null) {
                            add(node.layer)
                        } else {
                            appendActive(node.children)
                        }
                    }
                }
            }
            appendActive(roots)
        }

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
                children = children?.map(ResolvedLayerNode::key).orEmpty(),
            )
}

internal data class LayerTreeKey(
    val id: String,
    val zIndex: Int,
    val children: List<LayerTreeKey>,
)
