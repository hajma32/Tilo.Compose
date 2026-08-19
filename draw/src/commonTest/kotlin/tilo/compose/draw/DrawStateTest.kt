@file:OptIn(ExperimentalTiloDrawApi::class)

package tilo.compose.draw

import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.Point
import tilo.compose.core.layers.Layer
import tilo.compose.core.layers.LayerSink
import tilo.compose.core.layers.vector.FeatureLayer
import tilo.compose.core.map.MapState
import tilo.compose.core.map.Viewport
import tilo.compose.core.projection.IdentityProjection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrawStateTest {
    @Test
    fun drawingLifecycleIgnoresInactiveTapsAndKeepsStateExplicit() {
        val state = createDrawState()

        state.onMapTap(Point(1.0, 2.0))
        assertTrue(state.draftPoints.isEmpty())

        state.startDrawing()
        state.onMapTap(Point(1.0, 2.0))
        assertEquals(listOf(Point(1.0, 2.0)), state.draftPoints)
        assertTrue(state.canSave)

        state.stopDrawing(clearDraft = false)
        assertFalse(state.isDrawing)
        assertEquals(listOf(Point(1.0, 2.0)), state.draftPoints)

        state.stopDrawing(clearDraft = true)
        assertTrue(state.draftPoints.isEmpty())
    }

    @Test
    fun stoppingWithClearDropsHistoryEvenWhenTheCurrentDraftIsEmpty() {
        val state = createDrawState()
        state.startDrawing()
        state.onMapTap(Point(1.0, 2.0))
        state.clearDraft()
        state.stopDrawing(clearDraft = false)
        assertTrue(state.canUndo)

        state.stopDrawing(clearDraft = true)

        assertFalse(state.isDrawing)
        assertTrue(state.draftPoints.isEmpty())
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
        assertFalse(state.undo())
    }

    @Test
    fun lineHistoryAndSaveUseCallerOwnedKey() {
        val state = createDrawState(initialMode = DrawMode.Line)
        val first = Point(1.0, 2.0)
        val second = Point(3.0, 4.0)
        state.startDrawing()
        state.onMapTap(first)
        assertFalse(state.canSave)
        state.onMapTap(second)
        assertTrue(state.canSave)

        assertTrue(state.undo())
        assertFalse(state.canSave)
        assertTrue(state.redo())

        val saved = requireNotNull(state.save("route-42"))
        assertEquals("route-42", saved.key)
        assertEquals(LineString(listOf(first, second)), saved.geometry)
        assertTrue(state.draftPoints.isEmpty())
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
        assertFailsWith<IllegalArgumentException> { state.save(" ") }
    }

    @Test
    fun clearDraftParticipatesInUndoAndInvalidDraftDoesNotSave() {
        val state = createDrawState(initialMode = DrawMode.Polygon)
        state.startDrawing()
        state.onMapTap(Point(1.0, 1.0))
        state.onMapTap(Point(2.0, 2.0))
        assertNull(state.save("too-short"))

        state.clearDraft()
        assertTrue(state.draftPoints.isEmpty())
        assertTrue(state.undo())
        assertEquals(2, state.draftPoints.size)

        state.selectMode(DrawMode.Point)
        assertTrue(state.draftPoints.isEmpty())
        assertFalse(state.canUndo)
    }

    @Test
    fun layerSinkExtensionIsTheSingleLayerConstructionPath() {
        val state = createDrawState()
        val layers = mutableListOf<Layer>()
        val sink =
            object : LayerSink {
                override fun layer(layer: Layer) {
                    layers += layer
                }
            }
        state.startDrawing()
        state.onMapTap(Point(5.0, 6.0))

        sink.drawLayer(state = state, id = "draft", zIndex = 7, opacity = 0.5)

        val layer = assertIs<FeatureLayer>(layers.single())
        assertEquals("draft", layer.id)
        assertEquals(7, layer.zIndex)
        assertEquals(0.5, layer.opacity)
        val features =
            layer.source.getFeatures(
                MapState(
                    center = Point(5.0, 6.0),
                    zoom = 0.0,
                    viewport = Viewport(width = 256, height = 256),
                    projection = IdentityProjection,
                ),
            )
        assertEquals(listOf("draft-point-0", "draft-shape"), features.map { it.key }.sorted())
    }
}
