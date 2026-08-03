package tilo.compose.core.projection

/**
 * S-JTSK / Krovak East North.
 *
 * Maps, layers and features can consistently declare `EPSG:5514`; the platform
 * CRS provider discovers the concrete coordinate operation at runtime.
 */
object Epsg5514Projection : Projection {
    override val id: String = "EPSG:5514"
    override val worldUnitsPerMapUnit: Double = 111_319.49079327358
}
