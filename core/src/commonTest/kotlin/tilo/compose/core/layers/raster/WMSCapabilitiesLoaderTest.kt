package tilo.compose.core.layers.raster

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.projection.Epsg5514Projection

class WMSCapabilitiesLoaderTest {
    /**
     * Verifies normalization of WMS 1.3.0 EPSG:4326 metadata into internal XY coordinates.
     *
     * Input: capabilities XML whose bounding box is encoded in authoritative YX axis order.
     * Expected: stored bounds use longitude X extents `-10..20` and latitude Y extents `30..50`.
     */
    @Test
    fun normalizesWms130BoundingBoxToInternalXyOrder() {
        val capabilities = WMSCapabilitiesLoader().parse(
            """
            <WMS_Capabilities version="1.3.0">
              <Capability>
                <Layer>
                  <Layer>
                    <Name>world</Name>
                    <CRS>EPSG:4326</CRS>
                    <BoundingBox CRS="EPSG:4326"
                      minx="30.0" miny="-10.0" maxx="50.0" maxy="20.0"/>
                  </Layer>
                </Layer>
              </Capability>
            </WMS_Capabilities>
            """.trimIndent()
        )

        val bounds = assertNotNull(capabilities.layer("world")).boundingBoxes["EPSG:4326"]

        assertEquals(BoundingBox.fromExtents(-10.0, 20.0, 30.0, 50.0), bounds)
    }

    /**
     * Verifies parsing of service metadata and derivation of single/composite tile grids.
     *
     * Input: WMS 1.1.1 XML with GetMap URL, two formats, and two EPSG:5514 layers.
     * Expected: parsed endpoint/formats and grids matching individual and union bounding boxes.
     */
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
                  <Layer queryable="1">
                    <Name>buildings</Name>
                    <Title>Buildings</Title>
                    <BoundingBox SRS="EPSG:5514"
                      minx="-910000.0"
                      miny="-1235000.0"
                      maxx="-420000.0"
                      maxy="-930000.0"/>
                  </Layer>
                </Layer>
              </Capability>
            </WMT_MS_Capabilities>
            """.trimIndent()
        )

        val layer = assertNotNull(capabilities.layer("ortofoto"))
        val grid = capabilities.tileGridFor("ortofoto", Epsg5514Projection)
        val compositeGrid = capabilities.tileGridFor(
            listOf("ortofoto", "buildings"),
            Epsg5514Projection,
        )

        assertEquals("1.1.1", capabilities.version)
        assertEquals("https://example.test/wms?", capabilities.getMapUrl)
        assertEquals(listOf("image/png", "image/jpeg"), capabilities.formats)
        assertEquals("Ortofoto", layer.title)
        assertEquals(setOf("EPSG:5514"), layer.crs)
        assertEquals(-907841.056021, grid.originX)
        assertEquals(-932111.729700, grid.originY)
        assertTrue(kotlin.math.abs(491149.385742 - grid.worldWidth) < 0.000001)
        assertEquals(-910000.0, compositeGrid.originX)
        assertEquals(-930000.0, compositeGrid.originY)
        assertTrue(kotlin.math.abs(493308.329721 - compositeGrid.worldWidth) < 0.000001)
    }
}
