package tilo.compose.core.render

import eu.tilo.compose.render.RenderCommand
import eu.tilo.compose.render.RenderPoint
import eu.tilo.compose.render.SceneDiff
import eu.tilo.compose.render.SceneOp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tilo.compose.core.geometry.Point

class SceneDiffTests {

    @Test
    fun diffDetectsAddUpdateRemove() {
        val prev = listOf(
            RenderPoint(id = "a", point = Point(0.0, 0.0)),
            RenderPoint(id = "b", point = Point(1.0, 1.0))
        )
        val next = listOf(
            RenderPoint(id = "a", point = Point(0.0, 0.0)),
            RenderPoint(id = "b", point = Point(2.0, 2.0)),
            RenderPoint(id = "c", point = Point(3.0, 3.0))
        )

        val ops = SceneDiff.diff(prev, next)
        assertEquals(2, ops.size)
        assertTrue(ops.any { it is SceneOp.Update && it.command.id == "b" })
        assertTrue(ops.any { it is SceneOp.Add && it.command.id == "c" })
    }

    @Test
    fun applyBuildsRetainedState() {
        val prev = mapOf("a" to RenderPoint(id = "a", point = Point(0.0, 0.0)) as RenderCommand)
        val ops = listOf<SceneOp>(
            SceneOp.Update(RenderPoint(id = "a", point = Point(1.0, 1.0))),
            SceneOp.Add(RenderPoint(id = "b", point = Point(2.0, 2.0)))
        )

        val result = SceneDiff.apply(prev, ops)
        assertEquals(2, result.size)
        assertEquals(Point(1.0, 1.0), (result.getValue("a") as RenderPoint).point)
    }
}

