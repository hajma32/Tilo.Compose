package tilo.compose.core.transform

import tilo.compose.core.projection.Wgs84WebMercatorProjection
import kotlin.test.Test
import kotlin.test.assertEquals

class Wgs84WebMercatorProjectionTests {

    @Test
    fun exposesStableId() {
        assertEquals("EPSG:4326", Wgs84WebMercatorProjection.id)
    }
}
