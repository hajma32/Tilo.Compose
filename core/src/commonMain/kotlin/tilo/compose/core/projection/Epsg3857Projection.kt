package tilo.compose.core.projection

/**
 * Web Mercator projected coordinates.
 */
object Epsg3857Projection : Projection {
    override val id: String = "EPSG:3857"
    override val worldUnitsPerMapUnit: Double = 111_319.49079327358
}
