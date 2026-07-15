package eu.tilo.compose.transit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArcGisTransitDecoderTest {
    @Test
    fun decodesCurrentUppercaseStreamSchema() {
        val vehicle = ArcGisTransitDecoder.decode(UPPERCASE_STREAM_MESSAGE).single()

        assertEquals("7012", vehicle.id)
        assertEquals(TransitType.Tram, vehicle.actualType)
        assertEquals(TransitType.Tram, vehicle.plannedType)
        assertEquals(16.6087, vehicle.position.x)
        assertEquals(49.1952, vehicle.position.y)
        assertEquals(273.4, vehicle.bearingDegrees)
        assertEquals("12", vehicle.lineName)
        assertEquals(2.5, vehicle.delayMinutes)
        assertEquals(1_720_000_000_000L, vehicle.updatedAtMillis)
        assertTrue(vehicle.lowFloor == true)
        assertTrue(vehicle.active)
    }

    @Test
    fun decodesLowercaseHistorySchemaAndFeatureCollection() {
        val vehicle = ArcGisTransitDecoder.decode(LOWERCASE_FEATURE_COLLECTION).single()

        assertEquals("bus-42", vehicle.id)
        assertEquals(TransitType.Bus, vehicle.actualType)
        assertEquals(16.61, vehicle.position.x)
        assertEquals(49.20, vehicle.position.y)
        assertFalse(vehicle.active)
        assertFalse(vehicle.lowFloor ?: true)
        assertEquals(1_710_000_000_000L, vehicle.updatedAtMillis)
    }

    @Test
    fun ignoresControlAndInvalidMessages() {
        assertTrue(ArcGisTransitDecoder.decode("{\"type\":\"connected\"}").isEmpty())
        assertTrue(ArcGisTransitDecoder.decode("not json").isEmpty())
        assertTrue(
            ArcGisTransitDecoder
                .decode(
                    "{\"attributes\":{\"ID\":1,\"Lat\":999,\"Lng\":16}}",
                ).isEmpty(),
        )
    }

    @Test
    fun keepsUnknownOptionalValuesNullable() {
        val vehicle =
            ArcGisTransitDecoder
                .decode(
                    """
                    {
                      "attributes": {"ID": "x", "Lat": 49.1, "Lng": 16.5},
                      "geometry": {"x": 16.5, "y": 49.1}
                    }
                    """.trimIndent(),
                ).single()

        assertEquals(TransitType.Unknown, vehicle.actualType)
        assertNull(vehicle.lineName)
        assertNull(vehicle.delayMinutes)
        assertNull(vehicle.lowFloor)
    }
}

private val UPPERCASE_STREAM_MESSAGE =
    """
    {
      "geometry": {"x": 16.6087, "y": 49.1952, "spatialReference": {"wkid": 4326}},
      "attributes": {
        "ID": 7012,
        "VType": 1,
        "LType": 1,
        "Lat": 49.1952,
        "Lng": 16.6087,
        "Bearing": 273.4,
        "LineID": 12,
        "LineName": "12",
        "RouteID": 1201,
        "Course": "12007",
        "LF": "true",
        "Delay": 2.5,
        "LastStopID": 1001,
        "FinalStopID": 1099,
        "IsInactive": "false",
        "TimeUpdated": 1720000000000
      }
    }
    """.trimIndent()

private val LOWERCASE_FEATURE_COLLECTION =
    """
    {
      "features": [
        {
          "geometry": {"x": 16.61, "y": 49.20},
          "attributes": {
            "id": "bus-42",
            "vtype": 3,
            "ltype": 3,
            "lat": 49.20,
            "lng": 16.61,
            "lf": "0",
            "isinactive": "1",
            "lastupdate": 1710000000000
          }
        }
      ]
    }
    """.trimIndent()
