package tilo.compose.core.render

import eu.tilo.compose.render.CommandBuilder
import eu.tilo.compose.render.RenderLineString
import eu.tilo.compose.render.RenderPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.Point
import tilo.compose.core.map.MapState
import tilo.compose.core.map.MapSettings
import tilo.compose.core.map.Viewport

class CommandBuilderTests {

    @Test
    fun buildsPointCommandWithStableId() {
        val state = MapState(
            center = Point(0.0, 0.0),
            zoom = 0.0,
            settings = MapSettings(),
            viewport = Viewport(100, 100)
        )
        val features = listOf(Feature(key = "p1", geometry = Point(10.0, 20.0)))

        val commands = CommandBuilder.build(state, features)

        assertEquals(1, commands.size)
        val p = commands.first() as RenderPoint
        assertEquals("p1:point", p.id)
    }

    @Test
    fun buildsLineStringCommand() {
        val state = MapState(
            center = Point(0.0, 0.0),
            zoom = 0.0,
            settings = MapSettings(),
            viewport = Viewport(100, 100)
        )
        val line = LineString(listOf(Point(0.0, 0.0), Point(10.0, 0.0)))
        val features = listOf(Feature(key = "l1", geometry = line))

        val commands = CommandBuilder.build(state, features)

        assertEquals(1, commands.size)
        assertTrue(commands.first() is RenderLineString)
        assertEquals("l1:line", commands.first().id)
    }
}

