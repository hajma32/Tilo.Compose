package tilo.compose.core.layers.raster

import io.ktor.http.Url
import tilo.compose.core.geometry.BoundingBox
import tilo.compose.core.projection.Epsg5514Projection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WmsCapabilitiesLoaderTest {
    @Test
    fun capabilitiesResponsePreservesCharsetAndHttpStatus() {
        val xml =
            """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <WMT_MS_Capabilities version="1.1.1">
              <Capability><Layer><Layer><Name>café</Name></Layer></Layer></Capability>
            </WMT_MS_Capabilities>
            """.trimIndent()
        val latin1 = xml.map { character -> character.code.toByte() }.toByteArray()
        val response =
            RasterHttpResponse(
                statusCode = 200,
                headers = mapOf("content-type" to listOf("text/xml; charset=ISO-8859-1")),
                body = latin1,
            )

        val capabilities = WmsCapabilitiesLoader().parse(response.requireWmsCapabilitiesText())
        assertNotNull(capabilities.layer("café"))

        val failure =
            assertFailsWith<RasterHttpStatusException> {
                RasterHttpResponse(
                    statusCode = 401,
                    headers = mapOf("WWW-Authenticate" to listOf("Bearer")),
                ).requireWmsCapabilitiesText()
            }
        assertEquals(401, failure.statusCode)
        assertEquals("Bearer", failure.header("www-authenticate"))
    }

    @Test
    fun capabilitiesUrlReplacesWmsParametersWithoutCorruptingFragment() {
        val url = Url(capabilitiesUrl("https://example.test/wms?token=a%2Bb&ReQuEsT=old#metadata"))

        assertEquals("a+b", url.parameters["token"])
        assertEquals("WMS", url.parameters["SERVICE"])
        assertEquals("GetCapabilities", url.parameters["REQUEST"])
        assertEquals("metadata", url.fragment)
    }

    @Test
    fun capabilitiesResponseDetectsUtf16AndRejectsInvalidHttpCharset() {
        val xml = "<WMT_MS_Capabilities version=\"1.1.1\"/>"
        val utf16Le =
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
                xml.flatMap { character -> listOf(character.code.toByte(), 0) }.toByteArray()
        val decoded = RasterHttpResponse(statusCode = 200, body = utf16Le).requireWmsCapabilitiesText()

        assertTrue("WMT_MS_Capabilities" in decoded)
        assertFailsWith<IllegalArgumentException> {
            RasterHttpResponse(
                statusCode = 200,
                headers = mapOf("Content-Type" to listOf("text/xml; charset=not-a-real-charset")),
                body = xml.encodeToByteArray(),
            ).requireWmsCapabilitiesText()
        }
    }

    @Test
    fun capabilitiesDoNotForwardHeadersToAnUntrustedGetMapOrigin() {
        val capabilities =
            WmsCapabilities(
                version = WmsVersion.V1_3_0,
                getMapUrl = "https://tiles.attacker.test/wms",
                formats = listOf("image/png"),
                layers =
                    listOf(
                        WmsLayerCapabilities(
                            name = "base",
                            boundingBoxes =
                                mapOf(
                                    Epsg5514Projection.id to
                                        BoundingBox.fromExtents(-10.0, 10.0, -10.0, 10.0),
                                ),
                        ),
                    ),
                sourceOrigin = wmsHttpOrigin("https://trusted.test/capabilities"),
            )
        val http = RasterHttpConfig(headers = mapOf("Authorization" to "Bearer secret"))

        assertFailsWith<IllegalArgumentException> {
            capabilities.createTileLayer(
                id = "base",
                layerNames = listOf("base"),
                projection = Epsg5514Projection,
                options = WmsLayerOptions(http = http),
            )
        }

        capabilities
            .createTileLayer(
                id = "base",
                layerNames = listOf("base"),
                projection = Epsg5514Projection,
                options =
                    WmsLayerOptions(
                        http =
                            RasterHttpConfig(
                                headers = http.headers,
                                allowCrossOriginHeaders = true,
                            ),
                    ),
            ).close()
    }

    @Test
    fun unsupportedWmsProtocolVersionIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            WmsCapabilitiesLoader().parse("<WMT_MS_Capabilities version=\"1.0.0\"/>")
        }
        assertFailsWith<IllegalArgumentException> {
            WmsCapabilitiesLoader().parse("<WMT_MS_Capabilities/>")
        }
    }

    @Test
    fun capabilitiesChooseSupportedRasterFormatAndDoNotPrintEndpointSecrets() {
        val capabilities =
            WmsCapabilities(
                version = WmsVersion.V1_1_1,
                getMapUrl = "https://user:password@example.test/wms?token=secret",
                formats = listOf("image/svg+xml", "image/jpeg"),
                layers =
                    listOf(
                        WmsLayerCapabilities(
                            name = "base",
                            boundingBoxes =
                                mapOf(
                                    Epsg5514Projection.id to
                                        BoundingBox.fromExtents(-10.0, 10.0, -10.0, 10.0),
                                ),
                        ),
                    ),
            )

        val resolvedFormat = resolveWmsImageFormat(requested = null, advertised = capabilities.formats)

        assertEquals(WmsImageFormat.Jpeg, resolvedFormat)
        assertTrue("password" !in capabilities.toString())
        assertTrue("secret" !in capabilities.toString())
    }

    @Test
    fun capabilitiesMetadataSnapshotsCallerCollections() {
        val crs = mutableSetOf(Epsg5514Projection.id)
        val boundingBoxes =
            mutableMapOf(
                Epsg5514Projection.id to BoundingBox.fromExtents(-10.0, 10.0, -10.0, 10.0),
            )
        val layer = WmsLayerCapabilities("base", crs = crs, boundingBoxes = boundingBoxes)
        val formats = mutableListOf("image/png")
        val layers = mutableListOf(layer)
        val capabilities = WmsCapabilities(WmsVersion.V1_1_1, null, formats, layers)

        crs.clear()
        boundingBoxes.clear()
        formats.clear()
        layers.clear()

        assertEquals(setOf(Epsg5514Projection.id), layer.crs)
        assertEquals(1, layer.boundingBoxes.size)
        assertEquals(listOf("image/png"), capabilities.formats)
        assertEquals(listOf(layer), capabilities.layers)
    }

    /**
     * Verifies normalization of WMS 1.3.0 EPSG:4326 metadata into internal XY coordinates.
     *
     * Input: capabilities XML whose bounding box is encoded in authoritative YX axis order.
     * Expected: stored bounds use longitude X extents `-10..20` and latitude Y extents `30..50`.
     */
    @Test
    fun normalizesWms130BoundingBoxToInternalXyOrder() {
        val capabilities =
            WmsCapabilitiesLoader().parse(
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
                """.trimIndent(),
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
        val capabilities =
            WmsCapabilitiesLoader().parse(
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
                """.trimIndent(),
            )

        val layer = assertNotNull(capabilities.layer("ortofoto"))
        val grid = capabilities.tileGridFor(listOf("ortofoto"), Epsg5514Projection)
        val compositeGrid =
            capabilities.tileGridFor(
                listOf("ortofoto", "buildings"),
                Epsg5514Projection,
            )

        assertEquals(WmsVersion.V1_1_1, capabilities.version)
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
