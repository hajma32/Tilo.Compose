package tilo.compose.core.layers.vector

import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureLayerApiCompatibilityTest {
    @Test
    fun opacityDoesNotChangeExistingPositionalArguments() {
        val layer =
            FeatureLayer(
                "features",
                3,
                true,
                0.5,
                2.0,
                null,
                emptyList(),
                emptyList(),
                VectorRenderStrategy.Immediate,
            )

        assertEquals(0.5, layer.minZoom)
        assertEquals(2.0, layer.maxZoom)
        assertEquals(1.0, layer.opacity)
    }
}
