package tilo.compose.core.layers.raster

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import tilo.compose.core.projection.Epsg5514Projection

class WMSCapabilitiesLoaderTest {
    @Test
    fun parsesGetMapEndpointAndLayerBoundingBox() {
        val capabilities = WMSCapabilitiesLoader().parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <WMT_MS_Capabilities version="1.1.1">
              <Capability>
                <Request>
                  <GetMap>
                    <Format>image/png</Format>
                    <Format>image/jpeg</Format>
                    <DCPType>
                      <HTTP>
                        <Get>
                          <OnlineResource xlink:href="https://example.test/wms?"/>
                        </Get>
                      </HTTP>
                    </DCPType>
                  </GetMap>
                </Request>
                <Layer>
                  <Title>Root</Title>
                  <SRS>EPSG:4326 EPSG:5514</SRS>
                  <Layer queryable="1">
                    <Name>ortofoto</Name>
                    <Title>Ortofoto</Title>
                    <BoundingBox SRS="EPSG:5514"
                      minx="-907841.056021"
                      miny="-1230916.869000"
                      maxx="-416691.670279"
                      maxy="-932111.729700"/>
                  </Layer>
                </Layer>
              </Capability>
            </WMT_MS_Capabilities>
            """.trimIndent()
        )

        val layer = assertNotNull(capabilities.layer("ortofoto"))
        val grid = capabilities.tileGridFor("ortofoto", Epsg5514Projection)

        assertEquals("1.1.1", capabilities.version)
        assertEquals("https://example.test/wms?", capabilities.getMapUrl)
        assertEquals(listOf("image/png", "image/jpeg"), capabilities.formats)
        assertEquals("Ortofoto", layer.title)
        assertEquals(setOf("EPSG:5514"), layer.crs)
        assertEquals(-907841.056021, grid.originX)
        assertEquals(-932111.729700, grid.originY)
        assertTrue(kotlin.math.abs(491149.385742 - grid.worldWidth) < 0.000001)
    }
}
