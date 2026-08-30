package tilo.compose.draw

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point

/**
 * Observable owner of an interactive drawing draft and its undo/redo history.
 *
 * Feed map taps to [onMapTap] while [isDrawing] is true and render [draftFeatures] through
 * `drawLayer`. [save] returns the finished feature and clears the current draft.
 */
@Stable
@ExperimentalTiloDrawApi
class DrawState internal constructor(
    initialMode: DrawMode = DrawMode.Point,
    private val featureFactory: DrawingFeatureFactory,
) {
    var isDrawing: Boolean by mutableStateOf(false)
        private set

    var mode: DrawMode by mutableStateOf(initialMode)
        private set

    var draftPoints: List<Point> by mutableStateOf(emptyList())
        private set

    private var undoStack: List<List<Point>> by mutableStateOf(emptyList())
    private var redoStack: List<List<Point>> by mutableStateOf(emptyList())

    val canSave: Boolean
        get() =
            when (mode) {
                DrawMode.Point -> draftPoints.isNotEmpty()
                DrawMode.Line -> draftPoints.size >= 2
                DrawMode.Polygon -> draftPoints.size >= 3
            }

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    val draftFeatures: List<Feature>
        get() = featureFactory.draftFeatures(mode, draftPoints)

    fun startDrawing() {
        if (!isDrawing) {
            isDrawing = true
        }
    }

    fun stopDrawing(clearDraft: Boolean = true) {
        isDrawing = false
        if (clearDraft) {
            clearDraftHistory()
        }
    }

    fun selectMode(mode: DrawMode) {
        if (this.mode == mode) return
        this.mode = mode
        clearDraftHistory()
    }

    fun onMapTap(point: Point) {
        if (!isDrawing) return
        pushUndo()
        draftPoints =
            when (mode) {
                DrawMode.Point -> listOf(point)
                DrawMode.Line,
                DrawMode.Polygon,
                -> draftPoints + point
            }
        redoStack = emptyList()
    }

    fun clearDraft() {
        if (draftPoints.isEmpty()) return
        pushUndo()
        draftPoints = emptyList()
        redoStack = emptyList()
    }

    fun save(key: String): Feature? {
        require(key.isNotBlank()) { "Saved drawing key must not be blank" }
        val feature = featureFactory.drawingFeature(key = key, mode = mode, points = draftPoints) ?: return null
        clearDraftHistory()
        return feature
    }

    fun undo(): Boolean {
        val previous = undoStack.lastOrNull() ?: return false
        undoStack = undoStack.dropLast(1)
        redoStack = redoStack + listOf(draftPoints)
        draftPoints = previous
        return true
    }

    fun redo(): Boolean {
        val next = redoStack.lastOrNull() ?: return false
        redoStack = redoStack.dropLast(1)
        undoStack = undoStack + listOf(draftPoints)
        draftPoints = next
        return true
    }

    private fun pushUndo() {
        undoStack = undoStack + listOf(draftPoints)
    }

    private fun clearDraftHistory() {
        draftPoints = emptyList()
        undoStack = emptyList()
        redoStack = emptyList()
    }
}

/**
 * Creates drawing state outside composition.
 *
 * The returned state is independent of Compose lifecycle management; the caller owns and
 * retains it for as long as the drawing session should survive.
 */
@ExperimentalTiloDrawApi
fun createDrawState(
    initialMode: DrawMode = DrawMode.Point,
    style: DrawStyle = DefaultDrawStyle(),
): DrawState =
    DrawState(
        initialMode = initialMode,
        featureFactory = DrawingFeatureFactory(style),
    )

/**
 * Remembers drawing state for [initialMode] and [style] in the current composition.
 *
 * Changing the initial mode or style intentionally creates a fresh state.
 */
@Composable
@ExperimentalTiloDrawApi
fun rememberDrawState(
    initialMode: DrawMode = DrawMode.Point,
    style: DrawStyle = DefaultDrawStyle(),
): DrawState =
    remember(initialMode, style) {
        DrawState(
            initialMode = initialMode,
            featureFactory = DrawingFeatureFactory(style),
        )
    }
