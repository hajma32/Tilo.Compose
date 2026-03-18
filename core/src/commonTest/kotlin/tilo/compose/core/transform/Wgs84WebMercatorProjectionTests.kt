@file:Suppress("DEPRECATION")

package tilo.compose.core.transform

import kotlin.test.Test
import kotlin.test.assertEquals
import tilo.compose.core.projection.Wgs84WebMercatorProjection

class Wgs84WebMercatorProjectionTests {

    @Test
    fun exposesStableId() {
        assertEquals("EPSG:3857", Wgs84WebMercatorProjection.id)
    }
}
