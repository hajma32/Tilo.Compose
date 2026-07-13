package eu.tilo.compose.cuzk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import tilo.compose.core.feature.Data
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.ColorValue
import tilo.compose.core.feature.LabelFontWeight
import tilo.compose.core.feature.LabelFontStyle
import tilo.compose.core.feature.LabelStyle
import tilo.compose.core.feature.FillStyle
import tilo.compose.core.feature.LineCap
import tilo.compose.core.feature.LineJoin
import tilo.compose.core.feature.LineStyle
import tilo.compose.core.feature.PolygonStyle
import tilo.compose.core.feature.StrokeStyle
import tilo.compose.core.geometry.Geometry
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.MultiLineString
import tilo.compose.core.geometry.MultiPolygon
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon

internal object ZabagedGeoJsonDecoder {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(payload: String, layer: ZabagedLayer): List<Feature> {
        val root = json.parseToJsonElement(payload) as? JsonObject
            ?: error("ZABAGED response is not a GeoJSON object")
        root["error"]?.let { error("ZABAGED service returned $it") }
        val features = root["features"] as? JsonArray
            ?: error("ZABAGED response does not contain a features array")

        return features.mapNotNull { element ->
            decodeFeature(element as? JsonObject ?: return@mapNotNull null, layer)
        }
    }

    private fun decodeFeature(source: JsonObject, layer: ZabagedLayer): Feature? {
        val geometryObject = source["geometry"] as? JsonObject ?: return null
        val geometry = decodeGeometry(geometryObject) ?: return null
        val properties = source["properties"] as? JsonObject ?: JsonObject(emptyMap())
        val objectId = properties.text("OBJECTID")
            ?: source.text("id")
            ?: return null
        val roadTypeCode = properties.text("typsil_k")
        val population = properties.number("pocobyv")
        val label = when (layer) {
            ZabagedLayer.Watercourses -> properties.text("jmeno")
            ZabagedLayer.AdministrativeBoundaries -> null
            ZabagedLayer.Municipalities -> properties.text("nazevlau2")
            ZabagedLayer.Roads -> if (roadTypeCode?.startsWith("D") == true) {
                (properties.text("silnice") ?: properties.text("jmeno"))
                    ?.substringBefore(' ')
                    ?.trim()
            } else {
                null
            }
            ZabagedLayer.Streets -> properties.text("nazev")
            else -> null
        }?.takeIf(String::isNotBlank)
        val kind = when (layer) {
            ZabagedLayer.AdministrativeBoundaries -> properties.text("vyzn_zsh_p")
            ZabagedLayer.Municipalities -> properties.text("typobce")
            ZabagedLayer.Roads -> properties.text("typsil_p")
            ZabagedLayer.Streets -> properties.text("typulice_p")
            else -> null
        }

        return Feature(
            key = "${layer.keyPrefix}-$objectId",
            geometry = geometry,
            style = when (layer) {
                ZabagedLayer.ArableLand -> polygonFill(0xFFE9D9B8u)
                ZabagedLayer.Grassland -> polygonFill(0xFFDCE8B5u)
                ZabagedLayer.OrchardsAndGardens -> polygonFill(0xFFCDE2AEu)
                ZabagedLayer.MaintainedGreenery -> polygonFill(0xFFC2E3B4u)
                ZabagedLayer.Forest -> polygonFill(0xFFB7D5A6u)
                ZabagedLayer.CategorizedForest -> polygonFill(0xFFA9CC96u)
                ZabagedLayer.WaterAreas -> polygonFill(0xFFB9DDEBu)
                ZabagedLayer.SettlementAreas -> polygonFill(0xFFE5CBB8u)
                ZabagedLayer.Watercourses -> watercourseStyle()
                ZabagedLayer.Buildings -> polygonFill(0xFFE0DBD2u)
                ZabagedLayer.AdministrativeBoundaries -> boundaryStyle(kind)
                ZabagedLayer.Municipalities -> null
                ZabagedLayer.Roads -> roadStyle(roadTypeCode)
                ZabagedLayer.Streets -> null
            },
            label = label,
            labelPriority = when (layer) {
                ZabagedLayer.Watercourses -> 5
                ZabagedLayer.Roads -> 20
                ZabagedLayer.Streets -> 10
                ZabagedLayer.Municipalities -> municipalityPriority(population)
                ZabagedLayer.AdministrativeBoundaries -> null
                else -> null
            },
            labelStyle = when (layer) {
                ZabagedLayer.Municipalities -> municipalityLabelStyle(population)
                ZabagedLayer.Watercourses -> watercourseLabelStyle()
                else -> null
            },
            data = Data(ZabagedFeatureData(layer = layer, objectId = objectId, kind = kind)),
        )
    }

    private fun polygonFill(color: ULong): PolygonStyle =
        PolygonStyle(
            fill = FillStyle(color = ColorValue(color)),
            casing = null,
            stroke = null,
        )

    private fun watercourseStyle(): LineStyle =
        LineStyle(
            casing = null,
            stroke = StrokeStyle(
                color = ColorValue(0xFF75BBD1u),
                width = 1.6,
                lineCap = LineCap.Round,
                lineJoin = LineJoin.Round,
            ),
        )

    private fun watercourseLabelStyle(): LabelStyle =
        LabelStyle(
            color = ColorValue(0xFF3B91B2u),
            fontSize = 9.5,
            fontWeight = LabelFontWeight.Normal,
            fontStyle = LabelFontStyle.Italic,
            haloColor = ColorValue.White,
            haloWidth = 2.0,
            offsetY = -2.0,
        )

    private fun roadStyle(typeCode: String?): LineStyle {
        val family = typeCode?.takeWhile(Char::isLetter).orEmpty()
        val style = when (family) {
            "D" -> RoadStroke(
                color = 0xFFFCA5A5u,
                casingColor = 0xFF7F1D1Du,
                width = 6.5,
                casingWidth = 8.5,
                opacity = 1.0,
            )
            "M" -> RoadStroke(
                color = 0xFFFED7AAu,
                casingColor = 0xFF7C2D12u,
                width = 5.5,
                casingWidth = 7.2,
                opacity = 1.0,
            )
            "S" -> when {
                typeCode?.startsWith("S1") == true -> RoadStroke(
                    color = 0xFFFFF7D6u,
                    casingColor = 0xFF475569u,
                    width = 4.4,
                    casingWidth = 6.0,
                    opacity = 1.0,
                )
                typeCode?.startsWith("S2") == true -> RoadStroke(
                    color = 0xFFFFFFFFu,
                    casingColor = 0xFF475569u,
                    width = 3.0,
                    casingWidth = 4.4,
                    opacity = 1.0,
                )
                else -> RoadStroke(
                    color = 0xFFFFFFFFu,
                    casingColor = 0xFF64748Bu,
                    width = 1.8,
                    casingWidth = 3.0,
                    opacity = 1.0,
                )
            }
            else -> RoadStroke(
                color = 0xFFFFFFFFu,
                casingColor = 0xFF64748Bu,
                width = 1.5,
                casingWidth = 2.5,
                opacity = 1.0,
            )
        }
        return LineStyle(
            casing = StrokeStyle(
                color = ColorValue(style.casingColor),
                width = style.casingWidth,
                opacity = style.opacity,
                lineCap = LineCap.Round,
                lineJoin = LineJoin.Round,
            ),
            stroke = StrokeStyle(
                color = ColorValue(style.color),
                width = style.width,
                opacity = style.opacity,
                lineCap = LineCap.Round,
                lineJoin = LineJoin.Round,
            ),
        )
    }

    private fun boundaryStyle(kind: String?): LineStyle {
        val width = when {
            kind?.contains("Stát") == true -> 4.2
            kind?.startsWith("Oblast") == true || kind?.startsWith("Kraj") == true -> 3.4
            kind?.startsWith("Okres") == true -> 2.7
            else -> 1.9
        }
        return LineStyle(
            casing = null,
            stroke = StrokeStyle(
                color = ColorValue(0xFFFFD600u),
                width = width,
                opacity = 0.48,
                lineCap = LineCap.Butt,
                lineJoin = LineJoin.Round,
            ),
        )
    }

    private fun municipalityPriority(population: Double?): Int =
        when {
            population == null -> 30
            population >= 250_000 -> 300
            population >= 50_000 -> 200
            population >= 10_000 -> 100
            population >= 5_000 -> 60
            else -> 30
        }

    private fun municipalityLabelStyle(population: Double?): LabelStyle =
        LabelStyle(
            color = ColorValue(0xFF111827u),
            fontSize = when {
                population != null && population >= 250_000 -> 18.0
                population != null && population >= 50_000 -> 15.0
                population != null && population >= 10_000 -> 13.0
                else -> 11.0
            },
            fontWeight = if (population != null && population >= 10_000) {
                LabelFontWeight.Bold
            } else {
                LabelFontWeight.SemiBold
            },
            haloColor = ColorValue.White,
            haloWidth = 3.0,
            offsetY = 0.0,
        )

    private data class RoadStroke(
        val color: ULong,
        val casingColor: ULong,
        val width: Double,
        val casingWidth: Double,
        val opacity: Double,
    )

    private fun decodeGeometry(source: JsonObject): Geometry? {
        val coordinates = source["coordinates"] as? JsonArray ?: return null
        return when ((source["type"] as? JsonPrimitive)?.content) {
            "Point" -> coordinates.toPoint()
            "LineString" -> coordinates.toLineString()
            "MultiLineString" -> MultiLineString(
                coordinates.mapNotNull { (it as? JsonArray)?.toLineString() }
            ).takeIf { it.lines.isNotEmpty() }
            "Polygon" -> coordinates.toPolygon()
            "MultiPolygon" -> MultiPolygon(
                coordinates.mapNotNull { (it as? JsonArray)?.toPolygon() }
            ).takeIf { it.polygons.isNotEmpty() }
            else -> null
        }
    }

    private fun JsonArray.toPolygon(): Polygon? {
        val rings = mapNotNull { ring ->
            (ring as? JsonArray)?.toRing()
        }
        return rings.takeIf { it.isNotEmpty() }?.let(::Polygon)
    }

    private fun JsonArray.toRing(): List<Point>? {
        val points = mapNotNull { coordinate ->
            (coordinate as? JsonArray)?.toPoint()
        }.toMutableList()
        if (points.size < 3) return null
        if (points.first() != points.last()) points += points.first()
        return points.takeIf { it.size >= 4 }
    }

    private fun JsonArray.toLineString(): LineString? =
        mapNotNull { coordinate ->
            val values = coordinate as? JsonArray ?: return@mapNotNull null
            val x = (values.getOrNull(0) as? JsonPrimitive)?.doubleOrNull
            val y = (values.getOrNull(1) as? JsonPrimitive)?.doubleOrNull
            if (x != null && y != null && x.isFinite() && y.isFinite()) Point(x, y) else null
        }.takeIf { it.size >= 2 }?.let(::LineString)

    private fun JsonArray.toPoint(): Point? {
        val x = (getOrNull(0) as? JsonPrimitive)?.doubleOrNull
        val y = (getOrNull(1) as? JsonPrimitive)?.doubleOrNull
        return if (x != null && y != null && x.isFinite() && y.isFinite()) Point(x, y) else null
    }

    private fun JsonObject.text(name: String): String? {
        val primitive = entries.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
            ?.value as? JsonPrimitive
            ?: return null
        return primitive.content.takeUnless { it.isBlank() || it == "null" }
    }

    private fun JsonObject.number(name: String): Double? =
        entries.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
            ?.value
            ?.let { it as? JsonPrimitive }
            ?.doubleOrNull
}
