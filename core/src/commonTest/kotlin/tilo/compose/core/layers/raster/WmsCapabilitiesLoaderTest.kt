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

        val resolvedFormat =
            resolveWmsImageFormat(
                requested = null,
                advertised = capabilities.formats,
                requireTransparency = false,
            )

        assertEquals(WmsImageFormat.Jpeg, resolvedFormat)
        assertTrue("password" !in capabilities.toString())
        assertTrue("secret" !in capabilities.toString())
    }

    @Test
    fun transparentLayersPreferAnAdvertisedTransparentFormat() {
        fun capabilities(formats: List<String>) =
            WmsCapabilities(
                version = WmsVersion.V1_1_1,
                getMapUrl = "https://example.test/wms",
                formats = formats,
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

        val resolvedFormat =
            resolveWmsImageFormat(
                requested = null,
                advertised = listOf("image/jpeg", "image/png", "image/gif"),
                requireTransparency = true,
            )

        assertEquals(WmsImageFormat.Png, resolvedFormat)
        assertEquals(
            WmsImageFormat.Gif,
            resolveWmsImageFormat(
                requested = null,
                advertised = listOf("image/jpeg", "image/gif"),
                requireTransparency = true,
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            resolveWmsImageFormat(
                requested = null,
                advertised = listOf("image/jpeg"),
                requireTransparency = true,
            )
        }
        assertEquals(
            WmsImageFormat.Jpeg,
            resolveWmsImageFormat(
                requested = WmsImageFormat.Jpeg,
                advertised = listOf("image/png"),
                requireTransparency = true,
            ),
        )

        capabilities(listOf("image/jpeg", "image/png"))
            .createTileLayer(
                id = "base",
                layerNames = listOf("base"),
                projection = Epsg5514Projection,
                options = WmsLayerOptions(transparent = true),
            ).close()
        assertFailsWith<IllegalArgumentException> {
            capabilities(listOf("image/jpeg"))
                .createTileLayer(
                    id = "base",
                    layerNames = listOf("base"),
                    projection = Epsg5514Projection,
                    options = WmsLayerOptions(transparent = true),
                )
        }
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
     * Verifies WMS layer-property inheritance across multiple hierarchy levels.
     *
     * Input: a root CRS/bounds pair, a named intermediate group adding another CRS,
     * one child inheriting both, one child overriding a bound and adding a third CRS,
     * and a root sibling. Expected: descendants receive only ancestor metadata,
     * local bounds replace inherited bounds for the same CRS, and child metadata
     * never leaks back into its named parent.
     */
    @Test
    fun namedLayersInheritCrsAndBoundingBoxesFromAncestors() {
        val capabilities =
            WmsCapabilitiesLoader().parse(
                """
                <WMT_MS_Capabilities version="1.1.1">
                  <Capability>
                    <Layer>
                      <SRS>EPSG:4326</SRS>
                      <BoundingBox SRS="EPSG:4326"
                        minx="-180.0" miny="-90.0" maxx="180.0" maxy="90.0"/>
                      <Layer>
                        <Name>group</Name>
                        <SRS>EPSG:5514</SRS>
                        <BoundingBox SRS="EPSG:5514"
                          minx="-900000.0" miny="-1200000.0" maxx="-400000.0" maxy="-900000.0"/>
                        <Layer>
                          <Name>inherited</Name>
                        </Layer>
                        <Layer>
                          <Name>overridden</Name>
                          <SRS>EPSG:3857</SRS>
                          <BoundingBox SRS="EPSG:5514"
                            minx="-800000.0" miny="-1100000.0" maxx="-500000.0" maxy="-950000.0"/>
                          <BoundingBox SRS="EPSG:3857"
                            minx="-1000.0" miny="-2000.0" maxx="3000.0" maxy="4000.0"/>
                        </Layer>
                      </Layer>
                      <Layer>
                        <Name>sibling</Name>
                      </Layer>
                    </Layer>
                  </Capability>
                </WMT_MS_Capabilities>
                """.trimIndent(),
            )

        val rootBounds = BoundingBox.fromExtents(-180.0, 180.0, -90.0, 90.0)
        val groupBounds = BoundingBox.fromExtents(-900000.0, -400000.0, -1200000.0, -900000.0)
        val overriddenBounds = BoundingBox.fromExtents(-800000.0, -500000.0, -1100000.0, -950000.0)
        val childOnlyBounds = BoundingBox.fromExtents(-1000.0, 3000.0, -2000.0, 4000.0)
        val group = assertNotNull(capabilities.layer("group"))
        val inherited = assertNotNull(capabilities.layer("inherited"))
        val overridden = assertNotNull(capabilities.layer("overridden"))
        val sibling = assertNotNull(capabilities.layer("sibling"))

        assertEquals(
            listOf("inherited", "overridden", "group", "sibling"),
            capabilities.layers.map(WmsLayerCapabilities::name),
        )
        assertEquals(setOf("EPSG:4326", "EPSG:5514"), group.crs)
        assertEquals(setOf("EPSG:4326", "EPSG:5514"), group.boundingBoxes.keys)
        assertEquals(rootBounds, group.boundingBoxes["EPSG:4326"])
        assertEquals(groupBounds, group.boundingBoxes["EPSG:5514"])
        assertEquals(setOf("EPSG:4326", "EPSG:5514"), inherited.crs)
        assertEquals(rootBounds, inherited.boundingBoxes["EPSG:4326"])
        assertEquals(groupBounds, inherited.boundingBoxes["EPSG:5514"])
        assertEquals(setOf("EPSG:4326", "EPSG:5514", "EPSG:3857"), overridden.crs)
        assertEquals(setOf("EPSG:4326", "EPSG:5514", "EPSG:3857"), overridden.boundingBoxes.keys)
        assertEquals(rootBounds, overridden.boundingBoxes["EPSG:4326"])
        assertEquals(overriddenBounds, overridden.boundingBoxes["EPSG:5514"])
        assertEquals(childOnlyBounds, overridden.boundingBoxes["EPSG:3857"])
        assertEquals(setOf("EPSG:4326"), sibling.crs)
        assertEquals(setOf("EPSG:4326"), sibling.boundingBoxes.keys)
        assertEquals(rootBounds, sibling.boundingBoxes["EPSG:4326"])

        val inheritedGrid = capabilities.tileGridFor(listOf("inherited"), Epsg5514Projection)
        assertEquals(-900000.0, inheritedGrid.originX)
        assertEquals(-900000.0, inheritedGrid.originY)
        assertEquals(500000.0, inheritedGrid.worldWidth)
    }

    /** Verifies bounded traversal state for well-formed, deeply nested layer parser input. */
    @Test
    fun deeplyNestedLayerHierarchyIsParsedIteratively() {
        val nestingDepth = 4_096
        val xml =
            buildString {
                append("<WMT_MS_Capabilities version=\"1.1.1\"><Capability>")
                repeat(nestingDepth) { level ->
                    append("<Layer>")
                    if (level == nestingDepth - 1) append("<Name>deep</Name>")
                    append("<Title>Layer ")
                    append(level)
                    append("</Title><SRS>")
                    val crs = if (level == 0) "EPSG:4326" else "TEST:$level"
                    append(crs)
                    append("</SRS>")
                    if (level == 0) {
                        append("<LatLonBoundingBox minx=\"-10\" miny=\"-20\" maxx=\"30\" maxy=\"40\"/>")
                    }
                    append("<BoundingBox SRS=\"")
                    append(crs)
                    append("\" minx=\"-10\" miny=\"-20\" maxx=\"30\" maxy=\"40\"/>")
                }
                repeat(nestingDepth) { append("</Layer>") }
                append("</Capability></WMT_MS_Capabilities>")
            }

        val layer = assertNotNull(WmsCapabilitiesLoader().parse(xml).layer("deep"))

        assertEquals(nestingDepth, layer.crs.size)
        assertEquals(nestingDepth, layer.boundingBoxes.size)
        assertTrue("EPSG:4326" in layer.crs)
        assertTrue("TEST:${nestingDepth - 1}" in layer.crs)
        assertEquals(
            BoundingBox.fromExtents(-10.0, 30.0, -20.0, 40.0),
            layer.boundingBoxes["EPSG:4326"],
        )
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
        assertEquals(setOf("EPSG:4326", "EPSG:5514"), layer.crs)
        assertEquals(-907841.056021, grid.originX)
        assertEquals(-932111.729700, grid.originY)
        assertTrue(kotlin.math.abs(491149.385742 - grid.worldWidth) < 0.000001)
        assertEquals(-910000.0, compositeGrid.originX)
        assertEquals(-930000.0, compositeGrid.originY)
        assertTrue(kotlin.math.abs(493308.329721 - compositeGrid.worldWidth) < 0.000001)
    }
}
