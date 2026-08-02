package tilo.compose.core.map

/**
 * Minimal camera control contract for reusable map UI.
 */
interface MapCameraController {
    fun zoomIn(step: Double)

    fun zoomOut(step: Double)

    fun zoomBy(
        delta: Double,
        focus: ScreenPoint?,
    )
}
