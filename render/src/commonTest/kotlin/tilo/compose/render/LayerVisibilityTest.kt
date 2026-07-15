package tilo.compose.render

import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.Layer
import tilo.compose.core.layers.vector.FeatureLayer
import kotlin.test.Test
import kotlin.test.assertEquals

class LayerVisibilityTest {
    @Test
    fun activeAtFiltersHiddenAndOutOfRangeLayersBeforeRendering() {
        val layers: List<Layer> =
            listOf(
                layer(id = "always"),
                layer(id = "hidden", visible = false),
                layer(id = "detail", minZoom = 12.0),
            )

        assertEquals(listOf("always"), layers.activeAt(11.0).map(Layer::id))
        assertEquals(listOf("always", "detail"), layers.activeAt(12.0).map(Layer::id))
    }

    private fun layer(
        id: String,
        visible: Boolean = true,
        minZoom: Double? = null,
    ) = FeatureLayer(
        id = id,
        visible = visible,
        minZoom = minZoom,
        features = listOf(Feature(key = id, geometry = Point(0.0, 0.0))),
    )
}
