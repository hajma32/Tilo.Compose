package tilo.compose.core.layers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LayerGroupTest {
    @Test
    fun childrenAreSnapshottedAtConstruction() {
        val children = mutableListOf<Layer>(testLayer("first"))
        val group = LayerGroup(id = "group", children = children)

        children += testLayer("second")

        assertEquals(listOf("first"), group.children.map(Layer::id))
    }

    @Test
    fun invalidZoomRangeIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            LayerGroup(
                id = "group",
                children = emptyList(),
                minZoom = 12.0,
                maxZoom = 11.0,
            )
        }
    }

    @Test
    fun opacityOutsideUnitRangeIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            LayerGroup(id = "group", children = emptyList(), opacity = -0.1)
        }
        assertFailsWith<IllegalArgumentException> {
            LayerGroup(id = "group", children = emptyList(), opacity = 1.1)
        }
    }

    @Test
    fun opacityDoesNotChangeExistingPositionalArguments() {
        val group = LayerGroup("group", emptyList(), 3, true, 0.5, 2.0, emptyList())

        assertEquals(0.5, group.minZoom)
        assertEquals(2.0, group.maxZoom)
        assertEquals(1.0, group.opacity)
    }

    private fun testLayer(id: String): Layer =
        object : Layer {
            override val id = id
        }
}
