package eu.tilo.compose.cuzk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import tilo.compose.core.geometry.LineString
import tilo.compose.core.geometry.MultiLineString
import tilo.compose.core.geometry.Point
import tilo.compose.core.geometry.Polygon
import tilo.compose.core.feature.ColorValue
import tilo.compose.core.feature.LabelFontStyle
import tilo.compose.core.feature.LineStyle
import tilo.compose.core.feature.PolygonStyle
import tilo.compose.core.feature.Feature

class ZabagedGeoJsonDecoderTest {
    @Test
    fun decodesStreetNamesAndMultiLineGeometry() {
        val features = ZabagedGeoJsonDecoder.decode(GEO_JSON, ZabagedLayer.Streets)

        assertEquals(2, features.size)
        assertEquals("street-101", features[0].key)
        assertEquals("Česká", features[0].label)
        assertEquals(2, assertIs<LineString>(features[0].geometry).points.size)
        assertEquals("street-102", features[1].key)
        assertNull(features[1].label)
        assertEquals(2, assertIs<MultiLineString>(features[1].geometry).lines.size)
    }

    @Test
    fun labelsOnlyMotorwaysAndStylesThemWiderThanLocalRoads() {
        val feature = ZabagedGeoJsonDecoder.decode(
            GEO_JSON,
            ZabagedLayer.Roads,
        ).first()
        val localRoad = ZabagedGeoJsonDecoder.decode(
            GEO_JSON
                .replace("\"silnice\": \"D1\"", "\"silnice\": \"III/1234\"")
                .replace("\"typsil_k\": \"D1p\"", "\"typsil_k\": \"S3\""),
            ZabagedLayer.Roads,
        ).first()

        assertEquals("D1", feature.label)
        assertNull(localRoad.label)
        val motorwayStyle = assertIs<LineStyle>(feature.style)
        val localStyle = assertIs<LineStyle>(localRoad.style)
        assertEquals(6.5, motorwayStyle.stroke.width)
        assertEquals(1.8, localStyle.stroke.width)
        assertEquals(1.0, motorwayStyle.stroke.opacity)
        assertEquals(1.0, localStyle.stroke.opacity)
    }

    @Test
    fun selectsBoundaryHierarchyByZoom() {
        assertEquals(ZabagedBoundaryDetail.Regions, ZabagedBoundaryDetail.forZoom(7.0))
        assertEquals(ZabagedBoundaryDetail.Districts, ZabagedBoundaryDetail.forZoom(10.5))
        assertEquals(ZabagedBoundaryDetail.Municipalities, ZabagedBoundaryDetail.forZoom(12.5))
    }

    @Test
    fun decodesRealMunicipalityNameAndPopulationPriority() {
        val feature = ZabagedGeoJsonDecoder.decode(MUNICIPALITY_GEO_JSON, ZabagedLayer.Municipalities)
            .single()

        assertEquals("municipality-147", feature.key)
        assertEquals("Brno", feature.label)
        assertEquals(300, feature.labelPriority)
        assertEquals(18.0, feature.labelStyle?.fontSize)
        assertEquals(Point(-597977.0, -1161136.0), feature.geometry)
    }

    @Test
    fun selectsMunicipalityDensityByZoom() {
        assertEquals(ZabagedMunicipalityDetail.MajorCities, ZabagedMunicipalityDetail.forZoom(9.0))
        assertEquals(ZabagedMunicipalityDetail.Towns, ZabagedMunicipalityDetail.forZoom(10.0))
        assertEquals(
            ZabagedMunicipalityDetail.AllMunicipalities,
            ZabagedMunicipalityDetail.forZoom(11.5),
        )
    }

    @Test
    fun decodesBasemapPolygonWithOpaqueBorderlessPastelFill() {
        val feature = ZabagedGeoJsonDecoder.decode(
            POLYGON_GEO_JSON,
            ZabagedLayer.ArableLand,
        ).single()

        val polygon = assertIs<Polygon>(feature.geometry)
        assertEquals(polygon.rings.single().first(), polygon.rings.single().last())
        val style = assertIs<PolygonStyle>(feature.style)
        assertEquals(ColorValue(0xFFE9D9B8u), style.fill?.color)
        assertEquals(1.0, style.fill?.opacity)
        assertNull(style.casing)
        assertNull(style.stroke)
    }

    @Test
    fun stylesSettlementAreaAsOpaqueWarmPastelPolygon() {
        val feature = ZabagedGeoJsonDecoder.decode(
            POLYGON_GEO_JSON,
            ZabagedLayer.SettlementAreas,
        ).single()

        val style = assertIs<PolygonStyle>(feature.style)
        assertEquals(ColorValue(0xFFE5CBB8u), style.fill?.color)
        assertEquals(1.0, style.fill?.opacity)
        assertNull(style.casing)
        assertNull(style.stroke)
    }

    @Test
    fun stylesWatercourseAndItsLabelInBlueItalic() {
        val feature = ZabagedGeoJsonDecoder.decode(
            GEO_JSON.replace("\"nazev\": \"Česká\"", "\"jmeno\": \"Svitava\""),
            ZabagedLayer.Watercourses,
        ).first()

        assertEquals("Svitava", feature.label)
        assertEquals(ColorValue(0xFF75BBD1u), assertIs<LineStyle>(feature.style).stroke.color)
        assertEquals(ColorValue(0xFF3B91B2u), feature.labelStyle?.color)
        assertEquals(LabelFontStyle.Italic, feature.labelStyle?.fontStyle)
    }

    @Test
    fun spatiallyThinsRepeatedLabelsWithoutRemovingGeometry() {
        val features = listOf(
            labeledLine("road-1", "D1", 0.0),
            labeledLine("road-2", "D1", 50.0),
            labeledLine("road-3", "D1", 250.0),
            labeledLine("road-4", "D2", 25.0),
        )

        val thinned = features.thinRepeatedLabels(minimumDistance = 100.0)

        assertEquals(listOf("D1", null, "D1", "D2"), thinned.map(Feature::label))
        assertEquals(features.map(Feature::geometry), thinned.map(Feature::geometry))
    }

    @Test
    fun showsMinorWatercourseNamesOnlyAtCloserZooms() {
        val watercourses = listOf(
            labeledLine("water-1", "Vltava", 0.0),
            labeledLine("water-2", "Svratka", 200.0),
            labeledLine("water-3", "Medlánecký potok", 400.0),
        )

        assertEquals(
            listOf("Vltava", "Svratka", null),
            watercourses
                .withWaterLabels(ZabagedWaterLabelDetail.forZoom(11.5))
                .map(Feature::label),
        )
        assertEquals(
            listOf("Vltava", "Svratka", "Medlánecký potok"),
            watercourses
                .withWaterLabels(ZabagedWaterLabelDetail.forZoom(13.0))
                .map(Feature::label),
        )
    }

    @Test
    fun stylesBuildingsAsOpaqueBorderlessWarmGrayPolygons() {
        val feature = ZabagedGeoJsonDecoder.decode(
            POLYGON_GEO_JSON,
            ZabagedLayer.Buildings,
        ).single()

        val style = assertIs<PolygonStyle>(feature.style)
        assertEquals(ColorValue(0xFFE0DBD2u), style.fill?.color)
        assertEquals(1.0, style.fill?.opacity)
        assertNull(style.casing)
        assertNull(style.stroke)
    }

    @Test
    fun generalizesRoadNetworkByZoomAndScalesDistantRoadWidths() {
        assertEquals(ZabagedRoadDetail.Overview, ZabagedRoadDetail.forZoom(10.5))
        assertEquals(ZabagedRoadDetail.Network, ZabagedRoadDetail.forZoom(12.0))
        assertEquals(ZabagedRoadDetail.Detailed, ZabagedRoadDetail.forZoom(14.0))
        assertEquals(false, ZabagedRoadDetail.Overview.whereClause.contains("III. třídy"))
        assertEquals(false, ZabagedRoadDetail.Overview.whereClause.contains("%paprsek%"))
        assertEquals(true, ZabagedRoadDetail.Overview.whereClause.contains("I. třídy%'"))
        assertEquals(true, ZabagedRoadDetail.Network.whereClause.contains("NOT LIKE '%větev%'"))

        val motorway = ZabagedGeoJsonDecoder.decode(GEO_JSON, ZabagedLayer.Roads).first()
        val original = assertIs<LineStyle>(motorway.style)
        val overview = assertIs<LineStyle>(
            listOf(motorway).withRoadWidths(ZabagedRoadDetail.Overview).single().style
        )
        assertEquals(original.stroke.width * 0.45, overview.stroke.width)
        assertEquals(original.casing!!.width * 0.45, overview.casing!!.width)
    }
}

private fun labeledLine(key: String, label: String, x: Double): Feature =
    Feature(
        key = key,
        label = label,
        geometry = LineString(listOf(Point(x, 0.0), Point(x + 10.0, 0.0))),
    )

private val POLYGON_GEO_JSON =
    """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "properties": {"OBJECTID": 501},
              "geometry": {
                "type": "Polygon",
                "coordinates": [[
                  [-599000.0, -1160000.0],
                  [-598900.0, -1160000.0],
                  [-598900.0, -1159900.0],
                  [-599000.0, -1159900.0]
                ]]
              }
            }
          ]
        }
    """.trimIndent()

private val MUNICIPALITY_GEO_JSON =
    """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "properties": {
                "OBJECTID": 147,
                "nazevlau2": "Brno",
                "pocobyv": 404296,
                "typobce": "Statutární město",
                "typdbobc_p": "Hlavní"
              },
              "geometry": {
                "type": "Point",
                "coordinates": [-597977.0, -1161136.0]
              }
            }
          ]
        }
    """.trimIndent()

private val GEO_JSON =
    """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "properties": {
                "OBJECTID": 101,
                "nazev": "Česká",
                "silnice": "D1     1",
                "typsil_k": "D1p",
                "typsil_p": "dálnice I.třídy paprsek"
              },
              "geometry": {
                "type": "LineString",
                "coordinates": [[-599000.0, -1160000.0], [-598990.0, -1159990.0]]
              }
            },
            {
              "type": "Feature",
              "properties": {"OBJECTID": "102", "nazev": null},
              "geometry": {
                "type": "MultiLineString",
                "coordinates": [
                  [[-599000.0, -1160000.0], [-598990.0, -1159990.0]],
                  [[-598980.0, -1159980.0], [-598970.0, -1159970.0]]
                ]
              }
            }
          ]
        }
    """.trimIndent()
