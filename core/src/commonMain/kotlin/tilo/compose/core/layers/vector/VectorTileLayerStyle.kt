package tilo.compose.core.layers.vector

import tilo.compose.core.feature.BaseStyle
import tilo.compose.core.geometry.Geometry
import tilo.compose.core.tile.vector.VectorTileFeature

/**
 * Runtime style contract used by the renderer after style config compilation.
 */
class VectorTileLayerStyle(
    val id: String,
    internal val layerEnabled: (layerName: String, renderZoom: Int) -> Boolean,
    internal val featureIncluded: (layerName: String, feature: VectorTileFeature, renderZoom: Int) -> Boolean = { _, _, _ -> true },
    internal val labelProvider: (layerName: String, attributes: Map<String, String>, renderZoom: Int) -> String? = { _, _, _ -> null },
    internal val styleProvider: (layerName: String, attributes: Map<String, String>, renderZoom: Int) -> BaseStyle,
    internal val geometrySimplifier: (layerName: String, geometry: Geometry) -> Geometry = { _, geometry -> geometry }
) {
    fun isLayerEnabled(layerName: String, renderZoom: Int): Boolean = layerEnabled(layerName, renderZoom)

    fun shouldIncludeFeature(layerName: String, feature: VectorTileFeature, renderZoom: Int): Boolean =
        featureIncluded(layerName, feature, renderZoom)

    fun labelFor(layerName: String, attributes: Map<String, String>, renderZoom: Int): String? =
        labelProvider(layerName, attributes, renderZoom)

    fun styleFor(layerName: String, attributes: Map<String, String>, renderZoom: Int): BaseStyle =
        styleProvider(layerName, attributes, renderZoom)

    fun simplifyGeometry(layerName: String, geometry: Geometry): Geometry =
        geometrySimplifier(layerName, geometry)
}
