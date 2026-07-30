package tilo.compose.core.geometry

/** MultiPolygon is a collection of Polygons. */
data class MultiPolygon(
    val polygons: List<Polygon>,
) : Geometry
