package tilo.compose.core.layers

/** Receiver that accepts layers from reusable layer declarations and plugins. */
interface LayerSink {
    fun layer(layer: Layer)
}
