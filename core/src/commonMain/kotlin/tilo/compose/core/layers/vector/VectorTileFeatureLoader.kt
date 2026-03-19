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
    private val queryPlanner: VectorTileQueryPlanner = VectorTileQueryPlanner(),
    private val geometryDecoder: VectorTileGeometryDecoder = VectorTileGeometryDecoder(),
    private val renderPolicy: VectorTileRenderPolicy = DefaultVectorTileRenderPolicy
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

        plan.tiles.forEach { tile ->
            val vectorTile = source.loadTile(tile) ?: return@forEach

            vectorTile.layers.forEach { layer ->
                if (!renderPolicy.isLayerEnabled(layer.name, plan.requestedZoom)) return@forEach

                layer.features.forEachIndexed { featureIndex, feature ->
                    if (!renderPolicy.shouldIncludeFeature(layer.name, feature, plan.requestedZoom)) {
                        return@forEachIndexed
                    }

                    val geometry = geometryDecoder.decodeGeometry(
                        feature = feature,
                        extent = layer.extent,
                        tile = tile
                    ) ?: return@forEachIndexed

                    val simplifiedGeometry = renderPolicy.simplifyGeometry(layer.name, geometry)
                    if (bounds != null && geometryPoints(simplifiedGeometry).none(bounds::contains)) {
                        return@forEachIndexed
                    }

                    out += Feature(
                        geometry = simplifiedGeometry,
                        key = "${tile.z}/${tile.x}/${tile.y}:${layer.name}:$featureIndex:$featureCounter",
                        style = renderPolicy.styleFor(layer.name, feature.attributes, plan.requestedZoom),
                        label = renderPolicy.labelFor(layer.name, feature.attributes, plan.requestedZoom)
                    )
                    featureCounter++
                }
            }
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
