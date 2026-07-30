package tilo.compose.core.geometry

/** MultiLineString is a collection of LineStrings. */
data class MultiLineString(
    val lines: List<LineString>,
) : Geometry
