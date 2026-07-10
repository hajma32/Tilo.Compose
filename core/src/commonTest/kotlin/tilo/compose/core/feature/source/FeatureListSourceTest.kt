package tilo.compose.core.feature.source

import kotlin.test.Test
import kotlin.test.assertEquals
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.Map
import tilo.compose.core.map.Viewport

class FeatureListSourceTest {
    @Test
    fun returnsOnlyFeaturesIntersectingVisibleMapBounds() {
        val visible = Feature(
            key = "visible",
            geometry = Point(0.0, 0.0)
        )
        val outside = Feature(
            key = "outside",
            geometry = Point(1_000.0, 1_000.0)
        )
        val map = Map(
            center = Point(0.0, 0.0),
            zoom = 0.0,
            viewport = Viewport(width = 256, height = 256)
        )

        val features = FeatureListSource(listOf(visible, outside)).getFeatures(map)

        assertEquals(listOf("visible"), features.map { it.key })
    }
}
