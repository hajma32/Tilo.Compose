package tilo.compose.draw

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import tilo.compose.core.feature.Feature
import tilo.compose.core.geometry.Point

class DrawState(
    initialMode: DrawMode = DrawMode.Point,
    private val featureFactory: DrawingFeatureFactory = DrawingFeatureFactory(),
    private val onSave: (Feature) -> Unit = {},
    private val onChange: (DrawState) -> Unit = {},
) {
    var isDrawing: Boolean by mutableStateOf(false)
        private set

    var mode: DrawMode by mutableStateOf(initialMode)
        private set

    var draftPoints: List<Point> by mutableStateOf(emptyList())
        private set

    var revision: Int by mutableStateOf(0)
        private set

    private var undoStack: List<List<Point>> by mutableStateOf(emptyList())
    private var redoStack: List<List<Point>> by mutableStateOf(emptyList())

    val canSave: Boolean
        get() = savedFeature() != null

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    val draftFeatures: List<Feature>
        get() = featureFactory.draftFeatures(mode, draftPoints)

    fun toggleDrawing() {
        isDrawing = !isDrawing
        if (!isDrawing) {
            clearDraftHistory()
        }
        invalidate()
    }

    fun startDrawing() {
        if (!isDrawing) {
            isDrawing = true
            invalidate()
        }
    }

    fun stopDrawing(clearDraft: Boolean = true) {
        if (isDrawing || clearDraft && draftPoints.isNotEmpty()) {
            isDrawing = false
            if (clearDraft) {
                clearDraftHistory()
            }
            invalidate()
        }
    }

    fun selectMode(mode: DrawMode) {
        if (this.mode == mode) return
        this.mode = mode
        clearDraftHistory()
        invalidate()
    }

    fun onMapTap(point: Point) {
        if (!isDrawing) return
        pushUndo()
        draftPoints = when (mode) {
            DrawMode.Point -> listOf(point)
            DrawMode.Line,
            DrawMode.Polygon -> draftPoints + point
        }
        redoStack = emptyList()
        invalidate()
    }

    fun clear() {
        if (draftPoints.isEmpty()) return
        pushUndo()
        draftPoints = emptyList()
        redoStack = emptyList()
        invalidate()
    }

    fun save(): Feature? {
        val feature = savedFeature() ?: return null
        onSave(feature)
        clearDraftHistory()
        invalidate()
        return feature
    }

    fun undo(): Boolean {
        val previous = undoStack.lastOrNull() ?: return false
        undoStack = undoStack.dropLast(1)
        redoStack = redoStack + listOf(draftPoints)
        draftPoints = previous
        invalidate()
        return true
    }

    fun redo(): Boolean {
        val next = redoStack.lastOrNull() ?: return false
        redoStack = redoStack.dropLast(1)
        undoStack = undoStack + listOf(draftPoints)
        draftPoints = next
        invalidate()
        return true
    }

    private fun savedFeature(): Feature? =
        featureFactory.drawingFeature(
            key = "drawing-${revision + 1}",
            mode = mode,
            points = draftPoints,
        )

    private fun invalidate() {
        revision++
        onChange(this)
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

fun createDrawState(
    initialMode: DrawMode = DrawMode.Point,
    style: DrawStyle = DefaultDrawStyle(),
    onSave: (Feature) -> Unit = {},
    onChange: (DrawState) -> Unit = {},
): DrawState =
    DrawState(
        initialMode = initialMode,
        featureFactory = DrawingFeatureFactory(style),
        onSave = onSave,
        onChange = onChange,
    )
