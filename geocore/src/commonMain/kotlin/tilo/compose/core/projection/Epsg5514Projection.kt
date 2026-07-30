package tilo.compose.core.projection

/**
 * S-JTSK / Krovak East North.
 *
 * This iteration adds explicit CRS identity so maps, layers and features can
 * consistently declare `EPSG:5514`. Geometry reprojection hooks are wired
 * through the shared transformation registry.
 */
object Epsg5514Projection : Projection {
    override val id: String = "EPSG:5514"
    override val worldUnitsPerMapUnit: Double = 111_319.49079327358
}
