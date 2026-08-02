package tilo.compose.core.feature.source

import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import tilo.compose.core.projection.Projection
import tilo.compose.core.transform.Transformation
import tilo.compose.core.transform.TransformationProvider
import tilo.compose.core.transform.TransformationRegistry
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

    @Test
    fun reprojectedSourceReturnsConservativeCandidatesWithoutPerQueryTransforms() {
        val sourceProjection = TestProjection("source")
        val mapProjection = TestProjection("map")
        var transformedPoints = 0
        val transformation =
            object : Transformation<Projection, Projection> {
                override val source = sourceProjection
                override val target = mapProjection

                override fun sourceToTarget(point: Point): Point {
                    transformedPoints += 1
                    return Point(point.x, point.y - point.x.centerBump())
                }

                override fun targetToSource(point: Point): Point = Point(point.x, point.y + point.x.centerBump())
            }
        val registry = TransformationRegistry(listOf(TransformationProvider { _, _ -> transformation }))
        val visibleAfterProjection = Feature(key = "curved", geometry = Point(0.0, 100.0))
        val outside = Feature(key = "outside", geometry = Point(1_000.0, 1_000.0))
        val source = FeatureListSource(listOf(visibleAfterProjection, outside), projection = sourceProjection)
        val map =
            MapState(
                projection = mapProjection,
                transformationRegistry = registry,
                viewport = Viewport(width = 100, height = 100),
            )

        val first = source.getFeatures(map)
        val second = source.getFeatures(map)

        assertEquals(listOf(visibleAfterProjection, outside), first)
        assertEquals(first, second)
        assertEquals(0, transformedPoints)
    }

    private fun Double.centerBump(): Double = if (this == 0.0) 100.0 else 0.0

    private data class TestProjection(
        override val id: String,
    ) : Projection
}
