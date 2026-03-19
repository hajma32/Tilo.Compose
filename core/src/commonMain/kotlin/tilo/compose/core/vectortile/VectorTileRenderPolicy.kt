package tilo.compose.core.vectortile

import tilo.compose.core.feature.BaseStyle
import tilo.compose.core.geometry.Geometry
import tilo.compose.core.geometry.MultiPolygon
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon

interface VectorTileRenderPolicy {
    fun isLayerEnabled(layerName: String, renderZoom: Int): Boolean

    fun shouldIncludeFeature(layerName: String, feature: VectorTileFeature, renderZoom: Int): Boolean

    fun labelFor(layerName: String, attributes: Map<String, String>, renderZoom: Int): String?

    fun styleFor(layerName: String, attributes: Map<String, String>, renderZoom: Int): BaseStyle

    fun simplifyGeometry(layerName: String, geometry: Geometry): Geometry
}

object DefaultVectorTileRenderPolicy : VectorTileRenderPolicy {
    private const val BUILDING_MAX_VERTICES = 5

    override fun isLayerEnabled(layerName: String, renderZoom: Int): Boolean {
        val lower = layerName.lowercase()
        if (lower.contains("building")) return renderZoom >= 18

        val isLabelLayer = lower.contains("place") ||
            lower.contains("settlement") ||
            lower.contains("locality") ||
            lower.contains("municip") ||
            lower.contains("admin")
        if (isLabelLayer) return true

        return when {
            renderZoom >= 13 -> lower.contains("road") ||
                lower.contains("water") ||
                lower.contains("landuse") ||
                lower.contains("boundary")
            renderZoom >= 10 -> lower.contains("road") ||
                lower.contains("water") ||
                lower.contains("boundary")
            else -> lower.contains("road") || lower.contains("water")
        }
    }

    override fun shouldIncludeFeature(layerName: String, feature: VectorTileFeature, renderZoom: Int): Boolean {
        if (feature.geometryType != VectorTileGeometryType.POINT) return true
        return isMunicipalityPointFeature(layerName, feature.attributes)
    }

    override fun labelFor(layerName: String, attributes: Map<String, String>, renderZoom: Int): String? {
        val name = featureName(attributes) ?: return null
        if (renderZoom < 13) return null
        if (isMajorCity(attributes)) return name

        val lowerLayer = layerName.lowercase()
        val settlementKinds = setOf("city", "town", "village", "hamlet", "suburb", "municipality")
        val placeClass = attributes["place"]?.lowercase()
        val clazz = attributes["class"]?.lowercase()
        val type = attributes["type"]?.lowercase()

        val isSettlement = placeClass in settlementKinds || clazz in settlementKinds || type in settlementKinds
        if (isSettlement) return name

        val isPlaceLayer = lowerLayer.contains("place") ||
            lowerLayer.contains("settlement") ||
            lowerLayer.contains("locality") ||
            lowerLayer.contains("municip")
        if (isPlaceLayer) return name

        val isAdminBoundary =
            (lowerLayer.contains("boundary") || lowerLayer.contains("admin")) &&
                (attributes["admin_level"] != null || attributes["boundary"]?.lowercase() == "administrative")
        if (isAdminBoundary) return name

        return null
    }

    override fun styleFor(layerName: String, attributes: Map<String, String>, renderZoom: Int): BaseStyle {
        val lower = layerName.lowercase()

        if (
            lower.contains("place") ||
            lower.contains("settlement") ||
            lower.contains("locality") ||
            lower.contains("municip") ||
            lower.contains("admin")
        ) {
            return BaseStyle(strokeColor = 0xFF111827, fillColor = 0x00000000L, strokeWidth = 0.0)
        }

        if (lower.contains("road")) {
            val roadKind = (attributes["class"] ?: attributes["type"] ?: "").lowercase()
            val major = roadKind.contains("motorway") || roadKind.contains("trunk") || roadKind.contains("primary")
            val strokeColor = if (major) 0xFF4B5563 else 0xFF6B7280
            val strokeWidth = when {
                renderZoom >= 15 && major -> 3.8
                renderZoom >= 15 -> 2.8
                renderZoom >= 12 && major -> 3.0
                renderZoom >= 12 -> 2.2
                renderZoom >= 9 -> 1.8
                else -> 1.4
            }
            return BaseStyle(strokeColor = strokeColor, strokeWidth = strokeWidth)
        }

        if (lower.contains("water")) {
            return BaseStyle(strokeColor = 0xFF60A5FA, fillColor = 0x3360A5FA, strokeWidth = 1.5)
        }

        if (lower.contains("building")) {
            return BaseStyle(strokeColor = 0xFF9CA3AF, fillColor = 0x55D1D5DB, strokeWidth = 1.0)
        }

        if (lower.contains("boundary")) {
            return BaseStyle(strokeColor = 0xFF9CA3AF, strokeWidth = 1.2)
        }

        if (lower.contains("landuse")) {
            return BaseStyle(strokeColor = 0xFF84CC16, fillColor = 0x2284CC16, strokeWidth = 0.8)
        }

        return BaseStyle(strokeColor = 0xFF4B5563, fillColor = 0x224B5563, strokeWidth = 1.0)
    }

    override fun simplifyGeometry(layerName: String, geometry: Geometry): Geometry {
        val isBuildingLayer = layerName.lowercase().contains("building")
        if (!isBuildingLayer) return geometry

        return when (geometry) {
            is Polygon -> Polygon(rings = geometry.rings.map(::simplifyBuildingRing))
            is MultiPolygon -> MultiPolygon(
                polygons = geometry.polygons.map { polygon ->
                    Polygon(rings = polygon.rings.map(::simplifyBuildingRing))
                }
            )
            else -> geometry
        }
    }

    private fun isMunicipalityPointFeature(layerName: String, attributes: Map<String, String>): Boolean {
        val settlementKinds = setOf("city", "town", "village", "hamlet", "suburb", "municipality")
        val lowerLayer = layerName.lowercase()

        val place = attributes["place"]?.lowercase()
        val clazz = attributes["class"]?.lowercase()
        val type = attributes["type"]?.lowercase()
        if (place in settlementKinds || clazz in settlementKinds || type in settlementKinds) {
            return true
        }

        val name = featureName(attributes)
        val isPlaceLayer = lowerLayer.contains("place") ||
            lowerLayer.contains("settlement") ||
            lowerLayer.contains("locality") ||
            lowerLayer.contains("municip")
        if (isPlaceLayer && !name.isNullOrBlank()) {
            return true
        }

        val isAdminLayer = lowerLayer.contains("admin") || lowerLayer.contains("boundary")
        val hasAdminHint = attributes["admin_level"] != null ||
            attributes["boundary"]?.lowercase() == "administrative"
        return isAdminLayer && hasAdminHint && !name.isNullOrBlank()
    }

    private fun featureName(attributes: Map<String, String>): String? {
        return attributes["name:cs"]
            ?: attributes["name"]
            ?: attributes["name:en"]
            ?: attributes["official_name"]
            ?: attributes["name_int"]
            ?: attributes["name:latin"]
    }

    private fun isMajorCity(attributes: Map<String, String>): Boolean {
        val place = attributes["place"]?.lowercase()
        val clazz = attributes["class"]?.lowercase()
        val type = attributes["type"]?.lowercase()
        val isCityKind = place == "city" || clazz == "city" || type == "city"
        val isCapital = attributes["capital"]?.lowercase() in setOf("yes", "true", "1")
        val population = settlementPopulation(attributes)
        return isCapital || (isCityKind && population >= 80_000L)
    }

    private fun settlementPopulation(attributes: Map<String, String>): Long {
        val raw = attributes["population"] ?: return 0L
        return raw.filter { character -> character.isDigit() }.toLongOrNull() ?: 0L
    }

    private fun simplifyBuildingRing(points: List<Point>): List<Point> {
        if (points.size <= BUILDING_MAX_VERTICES + 1) return points

        val unique = if (points.firstOrNull() == points.lastOrNull()) points.dropLast(1) else points
        if (unique.size <= BUILDING_MAX_VERTICES) return unique + unique.first()

        val step = unique.size.toDouble() / BUILDING_MAX_VERTICES.toDouble()
        val sampled = (0 until BUILDING_MAX_VERTICES).map { index ->
            val sampledIndex = (index * step).toInt().coerceIn(0, unique.lastIndex)
            unique[sampledIndex]
        }.distinct()

        if (sampled.size < 3) return points
        return sampled + sampled.first()
    }
}

