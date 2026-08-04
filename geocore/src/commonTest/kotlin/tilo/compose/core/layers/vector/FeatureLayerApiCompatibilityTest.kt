package tilo.compose.core.layers.vector

import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FeatureLayerApiCompatibilityTest {
    @Test
    fun blankIdDuplicateFeatureKeysAndNonFiniteZoomsAreRejected() {
        val feature = Feature(key = "station", geometry = Point(0.0, 0.0))

        assertFailsWith<IllegalArgumentException> { FeatureLayer(id = " ", features = emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            FeatureLayer(id = "features", features = listOf(feature, feature.copy(geometry = Point(1.0, 1.0))))
        }
        assertFailsWith<IllegalArgumentException> {
            FeatureLayer(id = "features", minZoom = Double.NEGATIVE_INFINITY, features = emptyList())
        }
    }

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
