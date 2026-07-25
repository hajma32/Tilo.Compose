package tilo.compose.render

import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapState
import tilo.compose.core.projection.Projection
import kotlin.test.Test
import kotlin.test.assertSame

class FeatureProjectionTest {
    @Test
    fun returnsOriginalFeaturesForDifferentProjectionInstanceWithSameId() {
        val source = TestProjection(id = "test:shared", worldUnitsPerMapUnit = 2.0)
        val target = TestProjection(id = "test:shared", worldUnitsPerMapUnit = 2.0)
        val features = listOf(Feature(key = "point", geometry = Point(1.0, 2.0)))

        val transformed = transformFeaturesToMapProjection(features, source, MapState(projection = target))

        assertSame(features, transformed)
    }

    private data class TestProjection(
        override val id: String,
        override val worldUnitsPerMapUnit: Double,
    ) : Projection
}
