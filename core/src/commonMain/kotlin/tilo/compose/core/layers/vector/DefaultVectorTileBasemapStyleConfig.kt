package tilo.compose.core.layers.vector

import tilo.compose.core.feature.BaseStyle

object DefaultVectorTileBasemapStyleConfig {
    private const val WATER_FILL = 0xFF8FC2E8L
    private const val WATER_STROKE = 0xFF4F88B5L
    private const val LANDUSE_GREEN = 0xFFC7DBAE
    private const val LANDUSE_BROWN = 0xFFD7C19BL
    private const val BUILDING_FILL = 0xFF9B7453L
    private const val BUILDING_STROKE = 0xFF6E5038L
    private const val ROAD_PRIMARY = 0xFF374151L
    private const val ROAD_SECONDARY = 0xFF4B5563L
    private const val ROAD_PATH = 0xFF6B7280L
    private const val BOUNDARY_MAJOR = 0xFF6D6370L
    private const val BOUNDARY_MINOR = 0xFFA59DA7L

    private val roadTokens = listOf("road", "transport", "street", "highway", "path", "rail")
    private val buildingTokens = listOf("building")
    private val boundaryTokens = listOf("boundary", "admin")
    private val waterTokens = listOf("water", "waterway", "river", "lake")
    private val landuseTokens = listOf("landuse", "landcover", "park", "forest", "wood", "farmland", "field", "natural")

    val basemap: VectorTileStyleConfigMap by lazy {
        linkedMapOf(
            "landuse" to createLanduseLayerConfig(),
            "water" to createWaterLayerConfig(),
            "buildings" to createBuildingLayerConfig(),
            "boundaries" to createBoundaryLayerConfig(),
            "roads" to createRoadLayerConfig()
        )
    }

    val defaultOrder: List<String>
        get() = basemap.keys.toList()

    fun resolvePresetOrder(ids: List<String>): List<VectorTileLayerStyle> =
        VectorTileStyleMapCompiler.compile(subset(ids))

    private fun subset(ids: List<String>): VectorTileStyleConfigMap {
        val selected = linkedMapOf<String, VectorTileLayerConfig>()
        ids.forEach { id ->
            val key = id.trim().lowercase()
            selected[key] = basemap[key] ?: error("Unknown vector style preset: '$id'")
        }
        return selected
    }

    private fun createLanduseLayerConfig() = VectorTileLayerConfig(
        layerNameMatches = { it.containsAnyToken(landuseTokens) },
        subLayers = linkedMapOf(
            "green" to VectorTileSubLayerConfig(
                visibility = { true },
                matches = { _, _, attributes -> isGreenLanduse(attributes) },
                style = { _, _ -> BaseStyle(fillColor = LANDUSE_GREEN) },
                label = { _, _ -> null }
            ),
            "brown" to VectorTileSubLayerConfig(
                visibility = { true },
                matches = { _, _, attributes -> !isGreenLanduse(attributes) },
                style = { _, _ -> BaseStyle(fillColor = LANDUSE_BROWN) },
                label = { _, _ -> null }
            )
        )
    )

    private fun createWaterLayerConfig() = VectorTileLayerConfig(
        layerNameMatches = { it.containsAnyToken(waterTokens) },
        subLayers = linkedMapOf(
            "line" to VectorTileSubLayerConfig(
                visibility = { it >= 8 },
                matches = { _, _, attributes -> isLinearWater(attributes) },
                style = { zoom, _ ->
                    BaseStyle(
                        strokeColor = WATER_STROKE,
                        strokeWidth = if (zoom >= 12) 1.8 else 1.2
                    )
                },
                label = { _, _ -> null }
            ),
            "fill" to VectorTileSubLayerConfig(
                visibility = { it >= 8 },
                matches = { _, _, _ -> true },
                style = { _, _ ->
                    BaseStyle(
                        fillColor = WATER_FILL,
                        strokeColor = WATER_STROKE,
                        strokeWidth = 1.0
                    )
                },
                label = { _, _ -> null }
            )
        )
    )

    private fun createBuildingLayerConfig() = VectorTileLayerConfig(
        layerNameMatches = { it.containsAnyToken(buildingTokens) },
        subLayers = linkedMapOf(
            "default" to VectorTileSubLayerConfig(
                visibility = { it >= 12 },
                matches = { _, _, _ -> true },
                style = { zoom, _ ->
                    BaseStyle(
                        fillColor = BUILDING_FILL,
                        strokeColor = BUILDING_STROKE,
                        strokeWidth = if (zoom >= 17) 0.9 else 0.7
                    )
                },
                label = { _, _ -> null }
            )
        )
    )

    private fun createBoundaryLayerConfig() = VectorTileLayerConfig(
        layerNameMatches = { it.containsAnyToken(boundaryTokens) },
        subLayers = linkedMapOf(
            "major_admin" to VectorTileSubLayerConfig(
                visibility = { it >= 7 },
                matches = { _, _, attributes ->
                    val adminLevel = attributes["admin_level"]?.toIntOrNull()
                    isAdministrativeBoundary(attributes) && adminLevel != null && adminLevel <= 4
                },
                style = { zoom, _ ->
                    BaseStyle(
                        strokeColor = BOUNDARY_MAJOR,
                        strokeWidth = if (zoom >= 12) 2.0 else 1.5
                    )
                },
                label = { _, _ -> null }
            ),
            "minor_admin" to VectorTileSubLayerConfig(
                visibility = { it >= 10 },
                matches = { _, _, attributes -> isAdministrativeBoundary(attributes) },
                style = { zoom, _ ->
                    BaseStyle(
                        strokeColor = BOUNDARY_MINOR,
                        strokeWidth = if (zoom >= 12) 1.4 else 1.0
                    )
                },
                label = { _, _ -> null }
            )
        )
    )

    private fun createRoadLayerConfig() = VectorTileLayerConfig(
        layerNameMatches = { it.containsAnyToken(roadTokens) },
        subLayers = linkedMapOf(
            "primary" to VectorTileSubLayerConfig(
                visibility = { it >= 9 },
                matches = { _, _, attributes -> isPrimaryRoad(attributes) },
                style = { zoom, _ ->
                    BaseStyle(
                        strokeColor = ROAD_PRIMARY,
                        strokeWidth = when {
                            zoom >= 15 -> 3.8
                            zoom >= 12 -> 3.0
                            else -> 2.2
                        }
                    )
                },
                label = { _, _ -> null }
            ),
            "secondary" to VectorTileSubLayerConfig(
                visibility = { it >= 11 },
                matches = { _, _, attributes -> isSecondaryRoad(attributes) },
                style = { zoom, _ ->
                    BaseStyle(
                        strokeColor = ROAD_SECONDARY,
                        strokeWidth = when {
                            zoom >= 15 -> 2.4
                            zoom >= 12 -> 2.0
                            else -> 1.5
                        }
                    )
                },
                label = { _, _ -> null }
            ),
            "path" to VectorTileSubLayerConfig(
                visibility = { it >= 13 },
                matches = { _, _, attributes -> isPath(attributes) },
                style = { zoom, _ ->
                    BaseStyle(
                        strokeColor = ROAD_PATH,
                        strokeWidth = if (zoom >= 15) 1.6 else 1.2
                    )
                },
                label = { _, _ -> null }
            )
        )
    )

    private fun isGreenLanduse(attributes: Map<String, String>): Boolean {
        val kind = featureKind(attributes)
        return kind.contains("forest") ||
            kind.contains("wood") ||
            kind.contains("park") ||
            kind.contains("grass") ||
            kind.contains("meadow") ||
            kind.contains("orchard") ||
            kind.contains("vineyard")
    }

    private fun isLinearWater(attributes: Map<String, String>): Boolean {
        val kind = featureKind(attributes)
        return kind.contains("river") || kind.contains("stream") || kind.contains("canal") || kind.contains("ditch")
    }

    private fun isAdministrativeBoundary(attributes: Map<String, String>): Boolean {
        val boundary = attributes["boundary"]?.lowercase()
        return boundary == "administrative" || attributes["admin_level"] != null
    }

    private fun isPrimaryRoad(attributes: Map<String, String>): Boolean {
        val kind = featureKind(attributes)
        return kind.contains("motorway") || kind.contains("trunk") || kind.contains("primary")
    }

    private fun isSecondaryRoad(attributes: Map<String, String>): Boolean {
        val kind = featureKind(attributes)
        return kind.contains("secondary") || kind.contains("tertiary") || kind.contains("residential")
    }

    private fun isPath(attributes: Map<String, String>): Boolean {
        val kind = featureKind(attributes)
        return kind.contains("path") || kind.contains("track") || kind.contains("footway") || kind.contains("cycleway")
    }

    private fun featureKind(attributes: Map<String, String>): String =
        (attributes["class"] ?: attributes["type"] ?: attributes["landuse"] ?: attributes["subclass"] ?: "")
            .lowercase()

    private fun String.containsAnyToken(tokens: List<String>): Boolean {
        val lower = lowercase()
        return tokens.any(lower::contains)
    }
}
