package tilo.compose.core.layers.vector

import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.source.FeatureSource
import tilo.compose.core.map.Map
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.tile.vector.VectorTileSource

/**
 * A [VectorLayer] that loads features from a vector tile source via [VectorTileFeatureLoader].
 *
 * One [style] maps to one [VectorTileLayer], so rendering naturally runs as one style = one
 * layer = one batch.
 *
 * Features are decoded in WGS-84 (EPSG:4326), so [projection] is set accordingly and the renderer
 * will reproject them to the map CRS before drawing.
 *
 * @param id Unique layer identifier.
 * @param zIndex Draw order relative to other layers (lower = drawn first).
 * @param source Source backing vector tile access.
 * @param style Style/filter contract applied to this layer.
 * @param tileCount Number of tiles to load around the map center per frame.
 */
class VectorTileLayer(
    override val id: String,
    override val zIndex: Int = 0,
    source: VectorTileSource,
    style: VectorTileLayerStyle,
    tileCount: Int = 9,
    decodedFeatureCache: VectorTileDecodedFeatureCache = VectorTileDecodedFeatureCache()
) : VectorLayer {

    override val projection = Epsg4326Projection

    private val loader = VectorTileFeatureLoader(
        source = source,
        layerStyle = style,
        decodedFeatureCache = decodedFeatureCache
    )

    override val source: FeatureSource = object : FeatureSource {
        override fun getFeatures(map: Map): List<Feature> {
            val center = map.transformSourceToTarget(map.center, map.projection, Epsg4326Projection)
            return loader.loadFeatures(center = center, zoom = map.zoom, tileCount = tileCount)
        }
    }
}
