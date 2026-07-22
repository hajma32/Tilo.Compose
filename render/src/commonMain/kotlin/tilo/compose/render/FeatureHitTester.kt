package tilo.compose.render

import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.vector.VectorLayer
import tilo.compose.core.map.MapState
import tilo.compose.core.selection.FeatureHitTestFeature
import tilo.compose.core.selection.FeatureHitTestLayer
import tilo.compose.core.selection.FeatureSelection
import tilo.compose.core.selection.FeatureHitTester as CoreFeatureHitTester

internal class FeatureHitTester {
    fun hitTest(
        map: MapState,
        layers: List<VectorLayer>,
        screenPoint: Point,
    ): List<FeatureSelection> {
        val worldPoint = map.screenToWorld(screenPoint)
        val hitTestLayers =
            layers
                .asReversed()
                .map { layer ->
                    FeatureHitTestLayer(
                        id = layer.id,
                        style = layer.style.resolveAtZoom(map.zoom),
                        features =
                            layer.source
                                .getFeatures(map)
                                .asReversed()
                                .map { feature ->
                                    FeatureHitTestFeature(
                                        feature = feature,
                                        geometry = feature.geometryInMapProjection(layer, map),
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

    private fun Feature.geometryInMapProjection(
        layer: VectorLayer,
        map: MapState,
    ) = transformFeaturesToMapProjection(listOf(this), layer.projection, map).first().geometry
}
