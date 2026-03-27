package tilo.compose.core.layers.vector

import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Geometry
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.MultiLineString
import tilo.compose.core.geometry.MultiPoint
import tilo.compose.core.geometry.MultiPolygon
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon
import tilo.compose.core.tile.vector.VectorTileGeometryDecoder
import tilo.compose.core.tile.vector.VectorTileQueryPlanner
import tilo.compose.core.tile.vector.VectorTileSource

class VectorTileFeatureLoader(
    private val source: VectorTileSource,
    private val layerStyle: VectorTileLayerStyle,
    private val queryPlanner: VectorTileQueryPlanner = VectorTileQueryPlanner(),
    private val geometryDecoder: VectorTileGeometryDecoder = VectorTileGeometryDecoder(),
    private val decodedFeatureCache: VectorTileDecodedFeatureCache = VectorTileDecodedFeatureCache()
) {

    fun loadFeatures(center: Point, zoom: Double, tileCount: Int): List<Feature> {
        val metadata = source.metadata
        val plan = queryPlanner.plan(
            center = center,
            zoom = zoom,
            tileCount = tileCount,
            metadata = metadata
        ) ?: return emptyList()

        val bounds = metadata.bounds
        val out = mutableListOf<Feature>()
        var featureCounter = 0

        val decodedFeatures = decodedFeatureCache.getOrDecode(
            source = source,
            plan = plan,
            geometryDecoder = geometryDecoder
        )

        decodedFeatures.forEach { decoded ->
            if (!layerStyle.isLayerEnabled(decoded.layerName, plan.requestedZoom)) return@forEach
            if (!layerStyle.shouldIncludeFeature(decoded.layerName, decoded.feature, plan.requestedZoom)) return@forEach

            val simplifiedGeometry = layerStyle.simplifyGeometry(decoded.layerName, decoded.geometry)
            if (bounds != null && geometryPoints(simplifiedGeometry).none(bounds::contains)) return@forEach

            out += Feature(
                geometry = simplifiedGeometry,
                key = "${decoded.tile.z}/${decoded.tile.x}/${decoded.tile.y}:${decoded.layerName}:${layerStyle.id}:${decoded.featureIndex}:$featureCounter",
                style = layerStyle.styleFor(decoded.layerName, decoded.feature.attributes, plan.requestedZoom),
                label = layerStyle.labelFor(decoded.layerName, decoded.feature.attributes, plan.requestedZoom)
            )
            featureCounter++
        }

        return out
    }

    private fun geometryPoints(geometry: Geometry): List<Point> {
        return when (geometry) {
            is Point -> listOf(geometry)
            is MultiPoint -> geometry.points
            is LineString -> geometry.points
            is MultiLineString -> geometry.lines.flatMap { line -> line.points }
            is Polygon -> geometry.rings.flatten()
            is MultiPolygon -> geometry.polygons.flatMap { polygon -> polygon.rings.flatten() }
        }
    }
}
