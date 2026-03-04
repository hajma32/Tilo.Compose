package tilo.compose.core.map

/** Settings controlling map behaviour and limits. */
data class MapSettings(
    val minZoom: Double = 0.0,
    val maxZoom: Double = 22.0,
    val wrapHorizontal: Boolean = true // whether to wrap longitude (slippy maps)
)

