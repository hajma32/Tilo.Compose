package tilo.compose.core.layers

import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.vector.FeatureLayer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LayerVisibilityTest {
    @Test
    fun layerIsVisibleInsideInclusiveZoomRange() {
        val layer = featureLayer(minZoom = 10.0, maxZoom = 14.0)

        assertFalse(layer.isVisibleAt(9.99))
        assertTrue(layer.isVisibleAt(10.0))
        assertTrue(layer.isVisibleAt(12.0))
        assertTrue(layer.isVisibleAt(14.0))
        assertFalse(layer.isVisibleAt(14.01))
    }

    @Test
    fun hiddenLayerIsNeverVisible() {
        val layer = featureLayer(visible = false)

        assertFalse(layer.isVisibleAt(12.0))
    }

    private fun featureLayer(
        visible: Boolean = true,
        minZoom: Double? = null,
        maxZoom: Double? = null,
    ) = FeatureLayer(
        id = "test",
        visible = visible,
        minZoom = minZoom,
        maxZoom = maxZoom,
        features = listOf(Feature(key = "point", geometry = Point(0.0, 0.0))),
    )
}
