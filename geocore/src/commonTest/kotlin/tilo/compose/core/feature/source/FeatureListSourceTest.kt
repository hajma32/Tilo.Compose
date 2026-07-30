package tilo.compose.core.feature.source

import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FeatureListSourceTest {
    /**
     * Input: two sources with equal feature snapshots and one source with different content.
     * Expected output: equivalent sources share equality/hash identity while changed content does not.
     */
    @Test
    fun equivalentFeatureSnapshotsShareCacheIdentity() {
        val feature = Feature(key = "place", geometry = Point(14.4, 50.1))
        val first = FeatureListSource(listOf(feature))
        val equivalent = FeatureListSource(listOf(feature.copy()))
        val changed = FeatureListSource(listOf(feature.copy(key = "other-place")))

        assertEquals(first, equivalent)
        assertEquals(first.hashCode(), equivalent.hashCode())
        assertNotEquals(first, changed)
    }

    /**
     * Input: one feature inside and one feature outside the map viewport.
     * Expected output: the source returns only the key of the visible feature.
     */
    @Test
    fun returnsOnlyFeaturesIntersectingVisibleMapBounds() {
        val visible =
            Feature(
                key = "visible",
                geometry = Point(0.0, 0.0),
            )
        val outside =
            Feature(
                key = "outside",
                geometry = Point(1_000.0, 1_000.0),
            )
        val map =
            MapState(
                center = Point(0.0, 0.0),
                zoom = 0.0,
                viewport = Viewport(width = 256, height = 256),
            )

        val features = FeatureListSource(listOf(visible, outside)).getFeatures(map)

        assertEquals(listOf("visible"), features.map { it.key })
    }

    @Test
    fun rotatedViewportQueriesItsFourCornerEnvelope() {
        val cornerFeature = Feature(key = "rotated-corner", geometry = Point(65.0, 0.0))
        val source = FeatureListSource(listOf(cornerFeature))

        val unrotated = source.getFeatures(MapState(viewport = Viewport(width = 100, height = 100)))
        val rotated =
            source.getFeatures(
                MapState(bearing = 45.0, viewport = Viewport(width = 100, height = 100)),
            )

        assertEquals(emptyList(), unrotated)
        assertEquals(listOf(cornerFeature), rotated)
    }
}
