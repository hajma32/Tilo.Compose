package eu.tilo.compose.cuzk

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tilo.compose.core.feature.Feature
import tilo.compose.core.feature.LineStyle
import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.bounds

internal class ZabagedService(
    private val client: HttpClient = createCuzkHttpClient(),
    private val baseUrl: String = DefaultBaseUrl,
) {
    suspend fun query(
        layer: ZabagedLayer,
        bounds: BoundingBox,
        maximumOffset: Double,
        whereClause: String = "1=1",
        waterLabelDetail: ZabagedWaterLabelDetail? = null,
        roadDetail: ZabagedRoadDetail? = null,
    ): List<Feature> {
        val result = mutableListOf<Feature>()
        var offset = 0

        do {
            val payload: String = client.get("$baseUrl/${layer.serviceId}/query") {
                parameter("where", whereClause)
                parameter("geometry", "${bounds.minX},${bounds.minY},${bounds.maxX},${bounds.maxY}")
                parameter("geometryType", "esriGeometryEnvelope")
                parameter("spatialRel", "esriSpatialRelIntersects")
                parameter("inSR", 5514)
                parameter("outSR", 5514)
                parameter("outFields", layer.outFields)
                parameter("returnGeometry", true)
                parameter("returnZ", false)
                parameter("returnM", false)
                parameter("orderByFields", "OBJECTID")
                parameter("resultOffset", offset)
                parameter("resultRecordCount", PageSize)
                parameter("maxAllowableOffset", maximumOffset.coerceAtLeast(0.01))
                parameter("f", "geojson")
            }.body()
            val page = ZabagedGeoJsonDecoder.decode(payload, layer)
            result += page
            offset += page.size
        } while (page.size == PageSize && offset < MaximumFeatureCount)

        return when (layer) {
            ZabagedLayer.Roads -> result
                .withRoadWidths(roadDetail ?: ZabagedRoadDetail.Detailed)
                .thinRepeatedLabels(minimumDistance = maximumOffset * 340.0)
            ZabagedLayer.Watercourses -> result
                .withWaterLabels(waterLabelDetail ?: ZabagedWaterLabelDetail.AllNamedWatercourses)
                .thinRepeatedLabels(minimumDistance = maximumOffset * 220.0)
            else -> result
        }
    }

    fun close() {
        client.close()
    }

    private companion object {
        const val DefaultBaseUrl =
            "https://ags.cuzk.gov.cz/arcgis/rest/services/ZABAGED_POLOHOPIS/MapServer"
        const val PageSize = 2_000
        const val MaximumFeatureCount = 16_000
    }
}

internal fun List<Feature>.withRoadWidths(detail: ZabagedRoadDetail): List<Feature> {
    if (detail.widthScale == 1.0) return this
    return map { feature ->
        val style = feature.style as? LineStyle ?: return@map feature
        val casing = style.casing
        feature.copy(
            style = style.copy(
                casing = casing?.copy(width = casing.width * detail.widthScale),
                stroke = style.stroke.copy(width = style.stroke.width * detail.widthScale),
            )
        )
    }
}

internal fun List<Feature>.withWaterLabels(
    detail: ZabagedWaterLabelDetail,
): List<Feature> {
    if (detail == ZabagedWaterLabelDetail.AllNamedWatercourses) return this
    return map { feature ->
        if (feature.label?.substringBefore(" (") in MajorRiverNames) feature
        else feature.copy(label = null)
    }
}

internal fun List<Feature>.thinRepeatedLabels(minimumDistance: Double): List<Feature> {
    if (minimumDistance <= 0.0) return this
    val acceptedAnchors = mutableMapOf<String, MutableList<Point>>()
    val minimumDistanceSquared = minimumDistance * minimumDistance

    return map { feature ->
        val label = feature.label ?: return@map feature
        val bounds = feature.geometry.bounds()
        val anchor = Point(
            x = (bounds.minX + bounds.maxX) / 2.0,
            y = (bounds.minY + bounds.maxY) / 2.0,
        )
        val anchors = acceptedAnchors.getOrPut(label) { mutableListOf() }
        val isTooClose = anchors.any { accepted ->
            val dx = anchor.x - accepted.x
            val dy = anchor.y - accepted.y
            dx * dx + dy * dy < minimumDistanceSquared
        }
        if (isTooClose) {
            feature.copy(label = null)
        } else {
            anchors += anchor
            feature
        }
    }
}
