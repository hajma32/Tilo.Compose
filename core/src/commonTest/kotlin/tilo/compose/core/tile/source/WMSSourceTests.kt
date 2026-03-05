package tilo.compose.core.tile.source

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.transform.WmsBbox

class WMSSourceTests {

    @Test
    fun usesConfiguredCrsInWmsRequest() = runBlocking {
        val source = WMSSource(
            wmsBaseUrl = "https://example.com/wms",
            layers = "demo",
            crs = "EPSG:5514",
            crsParameterName = "CRS"
        )

        val tile = source.getTiles(
            listOf(
                WmsTileRequest(
                    coordinate = TileCoordinate(z = 3, x = 0, y = 0),
                    bbox = WmsBbox(minX = 10.0, minY = 20.0, maxX = 30.0, maxY = 40.0),
                    width = 512,
                    height = 512
                )
            )
        ).first()

        assertTrue(tile.url.contains("CRS=EPSG:5514"))
        assertTrue(tile.url.contains("BBOX=10.0,20.0,30.0,40.0"))
        assertTrue(tile.url.contains("WIDTH=512"))
        assertTrue(tile.url.contains("HEIGHT=512"))
    }
}
