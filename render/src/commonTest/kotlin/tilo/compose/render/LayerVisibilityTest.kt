package tilo.compose.render

import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.Attribution
import tilo.compose.core.layers.Layer
import tilo.compose.core.layers.LayerGroup
import tilo.compose.core.layers.vector.FeatureLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LayerVisibilityTest {
    @Test
    fun activeAtFiltersHiddenAndOutOfRangeLayersBeforeRendering() {
        val layers: List<Layer> =
            listOf(
                layer(id = "always"),
                layer(id = "hidden", visible = false),
                layer(id = "detail", minZoom = 12.0),
            )

        val tree = ResolvedLayerTree.resolve(layers)
        assertEquals(listOf("always"), tree.activeAt(11.0).map(Layer::id))
        assertEquals(listOf("always", "detail"), tree.activeAt(12.0).map(Layer::id))
    }

    @Test
    fun groupIsAtomicAndChildZIndicesAreLocal() {
        val layers: List<Layer> =
            listOf(
                layer(id = "overlay", zIndex = 20),
                LayerGroup(
                    id = "transport",
                    zIndex = 10,
                    children =
                        listOf(
                            layer(id = "labels", zIndex = 100),
                            layer(id = "roads", zIndex = -100),
                        ),
                ),
                layer(id = "background", zIndex = 0),
            )

        assertEquals(
            listOf("background", "roads", "labels", "overlay"),
            ResolvedLayerTree.resolve(layers).activeAt(12.0).map(Layer::id),
        )
    }

    @Test
    fun ancestorVisibilityAndZoomRangeConstrainNestedChildren() {
        val layers =
            listOf(
                LayerGroup(
                    id = "group",
                    minZoom = 10.0,
                    maxZoom = 15.0,
                    children = listOf(layer(id = "detail", minZoom = 12.0)),
                ),
            )

        val tree = ResolvedLayerTree.resolve(layers)
        assertEquals(emptyList(), tree.activeAt(11.0))
        assertEquals(listOf("detail"), tree.activeAt(12.0).map(Layer::id))
        assertEquals(emptyList(), tree.activeAt(16.0))
    }

    @Test
    fun activeAttributionsIncludeGroupsAndActiveDescendantsOnlyOnce() {
        val shared = Attribution("Shared")
        val layers =
            listOf(
                LayerGroup(
                    id = "group",
                    minZoom = 10.0,
                    attributions = listOf(Attribution("Group"), shared),
                    children =
                        listOf(
                            layer(id = "visible", attribution = shared),
                            layer(id = "detail", minZoom = 15.0, attribution = Attribution("Detail")),
                        ),
                ),
            )

        assertEquals(
            listOf("Group", "Shared"),
            ResolvedLayerTree.resolve(layers).activeAttributions(12.0).map(Attribution::label),
        )
    }

    @Test
    fun duplicateIdsInDirectLayerTreesAreRejected() {
        val layers =
            listOf(
                layer(id = "duplicate"),
                LayerGroup(id = "group", children = listOf(layer(id = "duplicate"))),
            )

        assertFailsWith<IllegalArgumentException> {
            ResolvedLayerTree.resolve(layers)
        }
    }

    @Test
    fun zoomFilteringDoesNotReadZIndicesAfterTreeResolution() {
        var zIndexReads = 0

        fun countedLayer(
            id: String,
            order: Int,
        ): Layer =
            object : Layer {
                override val id = id
                override val zIndex: Int
                    get() {
                        zIndexReads += 1
                        return order
                    }
            }
        val tree = ResolvedLayerTree.resolve(listOf(countedLayer("upper", 10), countedLayer("lower", 0)))
        val readsAfterResolution = zIndexReads

        tree.activeAt(10.0)
        tree.activeAt(11.0)

        assertEquals(readsAfterResolution, zIndexReads)
    }

    private fun layer(
        id: String,
        visible: Boolean = true,
        minZoom: Double? = null,
        zIndex: Int = 0,
        attribution: Attribution? = null,
    ) = FeatureLayer(
        id = id,
        zIndex = zIndex,
        visible = visible,
        minZoom = minZoom,
        attributions = listOfNotNull(attribution),
        features = listOf(Feature(key = id, geometry = Point(0.0, 0.0))),
    )
}
