package tilo.compose.render

import tilo.compose.core.feature.Feature
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.map.MapState
import tilo.compose.core.map.ScreenPoint
import tilo.compose.core.selection.FeatureHitTestFeature
import tilo.compose.core.selection.FeatureHitTestLayer
import tilo.compose.core.selection.FeatureSelection
import tilo.compose.core.selection.FeatureHitTester as CoreFeatureHitTester

internal class FeatureHitTester(
    private val projectionSnapshotStore: FeatureProjectionSnapshotStore? = null,
) {
    private val fallbackProjectionCache: FeatureProjectionCache? =
        if (projectionSnapshotStore == null) FeatureProjectionCache() else null

    fun hitTest(
        map: MapState,
        layers: List<VectorLayer>,
        screenPoint: ScreenPoint,
    ): List<FeatureSelection> {
        val worldPoint = map.screenToWorld(screenPoint)
        fallbackProjectionCache?.retainLayers(layers.mapTo(mutableSetOf()) { it.id })
        val hitTestLayers =
            layers
                .asReversed()
                .map { layer ->
                    FeatureHitTestLayer(
                        id = layer.id,
                        style = layer.style.resolveAtZoom(map.zoom),
                        features =
                            layer.hitTestFeatures(map).asReversed().map { (sourceFeature, projectedFeature) ->
                                FeatureHitTestFeature(
                                    feature = sourceFeature,
                                    geometry = projectedFeature.geometry,
                                )
                            },
                    )
                }

        return CoreFeatureHitTester(styleScale = map.viewport.pixelRatio).hitTest(
            layers = hitTestLayers,
            screenPoint = screenPoint,
            worldPoint = worldPoint,
            worldToScreen = map::worldToScreen,
        )
    }

    private fun VectorLayer.hitTestFeatures(map: MapState): List<Pair<Feature, Feature>> {
        val sourceProjection = projection
        if (
            sourceProjection == null ||
            (sourceProjection.id == map.projection.id && sourceProjection.definition == map.projection.definition)
        ) {
            return source.getFeatures(map).map { feature -> feature to feature }
        }

        projectionSnapshotStore?.let { store ->
            val snapshot =
                store.find(
                    layerId = id,
                    sourceIdentity = source,
                    sourceVersion = source.version,
                    source = sourceProjection,
                    map = map,
                ) ?: return emptyList()
            return snapshot.query(map).map { projected -> snapshot.sourceFeature(projected.key) to projected }
        }

        val sourceFeatures = source.getFeatures(map)
        val projected =
            requireNotNull(fallbackProjectionCache).transform(
                layerId = id,
                sourceIdentity = source,
                sourceVersion = source.version,
                features = sourceFeatures,
                source = sourceProjection,
                map = map,
            )
        val sourceFeaturesByKey = sourceFeatures.associateBy(Feature::key)
        return projected.map { feature -> sourceFeaturesByKey.getValue(feature.key) to feature }
    }
}
