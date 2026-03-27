package tilo.compose.core.layers.vector

import tilo.compose.core.feature.BaseStyle

/**
 * Map-based style config for vector rendering.
 *
 * Top-level map order defines render order (one entry = one vector layer = one batch).
 * Missing entries are not rendered.
 */
typealias VectorTileStyleConfigMap = LinkedHashMap<String, VectorTileLayerConfig>

data class VectorTileLayerConfig(
    val layerNameMatches: (layerName: String) -> Boolean,
    val subLayers: LinkedHashMap<String, VectorTileSubLayerConfig>
)

data class VectorTileSubLayerConfig(
    val visibility: (zoomLevel: Int) -> Boolean,
    val matches: (zoomLevel: Int, layerName: String, attributes: Map<String, String>) -> Boolean,
    val style: (zoomLevel: Int, attributes: Map<String, String>) -> BaseStyle,
    val label: (zoomLevel: Int, attributes: Map<String, String>) -> String?
)

object VectorTileStyleMapCompiler {

    fun compile(styleMap: VectorTileStyleConfigMap): List<VectorTileLayerStyle> {
        if (styleMap.isEmpty()) return emptyList()

        return styleMap.entries.map { (layerId, layerConfig) ->
            require(layerConfig.subLayers.isNotEmpty()) {
                "Layer '$layerId' must define at least one sub-layer."
            }
            val orderedSubLayers = layerConfig.subLayers.entries.toList()

            VectorTileLayerStyle(
                id = layerId,
                layerEnabled = { layerName, _ -> layerConfig.layerNameMatches(layerName) },
                featureIncluded = { layerName, feature, zoomLevel ->
                    selectSubLayer(
                        orderedSubLayers = orderedSubLayers,
                        zoomLevel = zoomLevel,
                        layerName = layerName,
                        attributes = feature.attributes
                    ) != null
                },
                labelProvider = { layerName, attributes, zoomLevel ->
                    val subLayer = selectSubLayer(
                        orderedSubLayers = orderedSubLayers,
                        zoomLevel = zoomLevel,
                        layerName = layerName,
                        attributes = attributes
                    ) ?: return@VectorTileLayerStyle null
                    subLayer.label(zoomLevel, attributes)
                },
                styleProvider = { layerName, attributes, zoomLevel ->
                    val subLayer = selectSubLayer(
                        orderedSubLayers = orderedSubLayers,
                        zoomLevel = zoomLevel,
                        layerName = layerName,
                        attributes = attributes
                    ) ?: error(
                        "No matching sub-layer for layer '$layerId' and source layer '$layerName'."
                    )
                    subLayer.style(zoomLevel, attributes)
                }
            )
        }
    }

    private fun selectSubLayer(
        orderedSubLayers: List<Map.Entry<String, VectorTileSubLayerConfig>>,
        zoomLevel: Int,
        layerName: String,
        attributes: Map<String, String>
    ): VectorTileSubLayerConfig? {
        return orderedSubLayers.firstOrNull { (_, config) ->
            config.visibility(zoomLevel) && config.matches(zoomLevel, layerName, attributes)
        }?.value
    }
}
