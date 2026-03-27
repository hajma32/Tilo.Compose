package tilo.compose.core.layers.vector

/**
 * Simple text config for vector style batch order.
 *
 * Supported keys:
 * - order: comma-separated preset ids in draw order
 * - <preset>.enabled: true/false
 */
object VectorTileStyleConfig {
    @Suppress("unused")
    const val DEFAULT_TEXT: String = """
        # Draw order (first is rendered first)
        order = landuse, water, buildings, boundaries, roads

        # Optional per-preset switches
        landuse.enabled = true
        water.enabled = true
        buildings.enabled = true
        boundaries.enabled = true
        roads.enabled = true
    """

    fun parsePresetOrder(configText: String): List<String> {
        val rows = configText
            .lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .toList()

        if (rows.isEmpty()) {
            return DefaultVectorTileBasemapStyleConfig.defaultOrder
        }

        val values = mutableMapOf<String, String>()
        rows.forEach { row ->
            val idx = row.indexOf('=')
            if (idx <= 0 || idx == row.lastIndex) {
                error("Invalid config row: '$row'. Expected key = value.")
            }
            val key = row.substring(0, idx).trim().lowercase()
            val value = row.substring(idx + 1).trim()
            values[key] = value
        }

        val order = values["order"]
            ?.split(',')
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            ?.ifEmpty { null }
            ?: DefaultVectorTileBasemapStyleConfig.defaultOrder

        val filtered = order.filter { presetId ->
            val enabled = values["$presetId.enabled"]?.lowercase()
            enabled == null || enabled == "true" || enabled == "1" || enabled == "yes"
        }

        return filtered.ifEmpty { DefaultVectorTileBasemapStyleConfig.defaultOrder }
    }

    fun parseStyles(configText: String): List<VectorTileLayerStyle> =
        DefaultVectorTileBasemapStyleConfig.resolvePresetOrder(parsePresetOrder(configText))
}
