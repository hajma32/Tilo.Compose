package tilo.compose.core.layers

/**
 * Represents a map layer responsible for rendering and updating map elements.
 *
 * Implementations should be platform-agnostic and lightweight. Platform-specific
 * behavior should be placed behind `expect/actual` or small adapters when needed.
 */
interface Layer {
    /**
     * Unique identifier for the layer.
     */
    val id: String

    /**
     * Update the layer's state (for example, when features or the map state change).
     * Implementations may be a no-op if no update is required.
     */
    fun update()

    /**
     * Release resources held by the layer. After calling this, the layer should not be used.
     */
    fun dispose()
}
