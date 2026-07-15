package eu.tilo.compose.transit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import tilo.compose.core.geometry.Point

internal enum class TransitType {
    Service,
    Tram,
    Trolleybus,
    Bus,
    Boat,
    Train,
    Unknown,
}

internal data class TransitVehicle(
    val id: String,
    val actualType: TransitType,
    val plannedType: TransitType,
    val position: Point,
    val bearingDegrees: Double?,
    val lineId: Int?,
    val lineName: String?,
    val routeId: Int?,
    val course: String?,
    val lowFloor: Boolean?,
    val delayMinutes: Double?,
    val lastStopId: Int?,
    val finalStopId: Int?,
    val active: Boolean,
    val updatedAtMillis: Long?,
)

internal object ArcGisTransitDecoder {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    fun decode(message: String): List<TransitVehicle> =
        runCatching {
            json
                .parseToJsonElement(message)
                .featureCandidates()
                .mapNotNull(::decodeFeature)
        }.getOrDefault(emptyList())

    private fun decodeFeature(feature: JsonObject): TransitVehicle? {
        val attributes = (feature.valueIgnoringCase("attributes") as? JsonObject) ?: feature
        val fields = CaseInsensitiveFields(attributes)
        val geometry = feature.valueIgnoringCase("geometry") as? JsonObject
        val geometryFields = geometry?.let(::CaseInsensitiveFields)

        val id = fields.string("id")?.takeIf(String::isNotBlank) ?: return null
        val position = decodePosition(fields, geometryFields) ?: return null
        val inactive = fields.booleanLike("isinactive")
        return TransitVehicle(
            id = id,
            actualType = fields.int("vtype").toTransitType(),
            plannedType = fields.int("ltype").toTransitType(),
            position = position,
            bearingDegrees = fields.double("bearing"),
            lineId = fields.int("lineid"),
            lineName = fields.string("linename")?.takeIf(String::isNotBlank),
            routeId = fields.int("routeid"),
            course = fields.string("course")?.takeIf(String::isNotBlank),
            lowFloor = fields.booleanLike("lf"),
            delayMinutes = fields.double("delay"),
            lastStopId = fields.int("laststopid"),
            finalStopId = fields.int("finalstopid"),
            active = inactive != true,
            updatedAtMillis = fields.long("timeupdated", "lastupdate"),
        )
    }

    private fun decodePosition(
        fields: CaseInsensitiveFields,
        geometryFields: CaseInsensitiveFields?,
    ): Point? {
        val longitude =
            fields.double("lng", "lon", "longitude")
                ?: geometryFields?.double("x", "lng", "lon")
                ?: return null
        val latitude =
            fields.double("lat", "latitude")
                ?: geometryFields?.double("y", "lat")
                ?: return null
        if (longitude !in -180.0..180.0 || latitude !in -90.0..90.0) return null
        return Point(longitude, latitude)
    }
}

private class CaseInsensitiveFields(
    source: JsonObject,
) {
    private val values = source.entries.associate { (key, value) -> key.lowercase() to value }

    fun string(vararg names: String): String? = primitive(*names)?.content?.takeUnless { it == "null" }

    fun int(vararg names: String): Int? = primitive(*names)?.intOrNull ?: string(*names)?.toIntOrNull()

    fun long(vararg names: String): Long? = primitive(*names)?.longOrNull ?: string(*names)?.toLongOrNull()

    fun double(vararg names: String): Double? = primitive(*names)?.doubleOrNull ?: string(*names)?.toDoubleOrNull()

    fun booleanLike(vararg names: String): Boolean? {
        val primitive = primitive(*names) ?: return null
        primitive.booleanOrNull?.let { return it }
        return when (primitive.content.trim().lowercase()) {
            "1", "ano", "true", "yes", "y" -> true
            "0", "ne", "false", "no", "n" -> false
            else -> null
        }
    }

    private fun primitive(vararg names: String): JsonPrimitive? =
        names.firstNotNullOfOrNull { name -> values[name.lowercase()] as? JsonPrimitive }
}

private fun JsonElement.featureCandidates(): List<JsonObject> =
    when (this) {
        is JsonArray -> flatMap(JsonElement::featureCandidates)
        is JsonObject -> {
            val features = valueIgnoringCase("features")
            when (features) {
                is JsonArray -> features.flatMap(JsonElement::featureCandidates)
                is JsonObject -> listOf(features)
                else -> listOf(this)
            }
        }
        else -> emptyList()
    }

private fun JsonObject.valueIgnoringCase(name: String): JsonElement? =
    entries.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }?.value

private fun Int?.toTransitType(): TransitType =
    when (this) {
        0 -> TransitType.Service
        1 -> TransitType.Tram
        2 -> TransitType.Trolleybus
        3 -> TransitType.Bus
        4 -> TransitType.Boat
        5 -> TransitType.Train
        else -> TransitType.Unknown
    }
