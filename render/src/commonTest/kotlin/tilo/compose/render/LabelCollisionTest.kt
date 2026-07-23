@file:OptIn(ExperimentalTiloRenderingApi::class)

package tilo.compose.render

import androidx.compose.ui.geometry.Offset
import tilo.compose.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertEquals

class LabelCollisionTest {
    /**
     * Verifies that selection has the highest collision priority for labels.
     *
     * Input: overlapping selected and unselected labels, where the unselected label has priority `100`.
     * Expected: only the selected label is accepted.
     */
    @Test
    fun selectedLabelWinsCollisionEvenAgainstHigherPriority() {
        val priority = candidate(id = "priority", selected = false, priority = 100, order = 0)
        val selected = candidate(id = "selected", selected = true, priority = null, order = 1)

        val accepted = selectLabelCollisionCandidates(listOf(priority, selected), collisionPadding = 0f)

        assertEquals(listOf("selected"), accepted.map { it.command.id })
    }

    /**
     * Verifies deterministic collision priority before size and input order.
     *
     * Input: three overlapping labels with different explicit priority and areas.
     * Expected: the explicitly prioritized label is the sole accepted candidate.
     */
    @Test
    fun priorityThenAreaThenInputOrderResolveCollisionsDeterministically() {
        val small = candidate(id = "small", priority = null, width = 10, height = 10, order = 0)
        val large = candidate(id = "large", priority = null, width = 20, height = 20, order = 1)
        val priority = candidate(id = "priority", priority = 1, width = 5, height = 5, order = 2)

        assertEquals(
            listOf("priority"),
            selectLabelCollisionCandidates(listOf(small, large, priority), 0f).map { it.command.id },
        )
    }

    /**
     * Verifies that non-overlapping labels are all accepted in collision-priority order.
     *
     * Input: two spatially separated labels with priorities one and two.
     * Expected: both survive, with the higher-priority candidate returned first.
     */
    @Test
    fun nonCollidingLabelsAreAcceptedInCollisionPriorityOrder() {
        val first = candidate(id = "first", priority = 1, order = 0, left = 0f)
        val second = candidate(id = "second", priority = 2, order = 1, left = 100f)

        val accepted = selectLabelCollisionCandidates(listOf(first, second), collisionPadding = 2f)

        assertEquals(listOf("second", "first"), accepted.map { it.command.id })
        assertEquals(listOf(1, 0), accepted.map { it.order })
    }

    /** A later scene layer wins when selection and explicit label priority are otherwise equal. */
    @Test
    fun higherLayerOrderWinsCollisionAtEqualPriority() {
        val lower = candidate(id = "lower", width = 40, height = 20, order = 0, layerOrder = 2)
        val higher = candidate(id = "higher", width = 10, height = 10, order = 1, layerOrder = 3)

        val accepted = selectLabelCollisionCandidates(listOf(lower, higher), collisionPadding = 0f)

        assertEquals(listOf("higher"), accepted.map { it.command.id })
    }

    /** Explicit label priority remains stronger than scene layer order. */
    @Test
    fun explicitPriorityWinsCollisionAcrossLayerOrder() {
        val priority = candidate(id = "priority", priority = 10, order = 0, layerOrder = 0)
        val higherLayer = candidate(id = "higher-layer", priority = 5, order = 1, layerOrder = 100)

        val accepted = selectLabelCollisionCandidates(listOf(priority, higherLayer), collisionPadding = 0f)

        assertEquals(listOf("priority"), accepted.map { it.command.id })
    }

    private fun candidate(
        id: String,
        selected: Boolean = false,
        priority: Int? = null,
        width: Int = 20,
        height: Int = 10,
        order: Int,
        left: Float = 0f,
        layerOrder: Int = 0,
    ): LabelCollisionCandidate =
        LabelCollisionCandidate(
            command =
                RenderLabel(
                    id = id,
                    text = id,
                    anchor = Point(0.0, 0.0),
                    labelPriority = priority,
                    selected = selected,
                ),
            center = Offset(left + width / 2f, height / 2f),
            topLeft = Offset(left, 0f),
            width = width,
            height = height,
            bounds = ScreenBounds(left, 0f, left + width, height.toFloat()),
            order = order,
            layerOrder = layerOrder,
        )
}
