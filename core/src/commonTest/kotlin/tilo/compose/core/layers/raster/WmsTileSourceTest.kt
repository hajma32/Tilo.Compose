package tilo.compose.core.layers.raster

import io.ktor.http.Url
import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.tile.TileBounds
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.TileRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WmsTileSourceTest {
    @Test
    fun imageFormatRequiresConcreteMimeType() {
        assertFailsWith<IllegalArgumentException> { WmsImageFormat("png") }
        assertFailsWith<IllegalArgumentException> { WmsImageFormat("image/*") }
        assertFailsWith<IllegalArgumentException> { WmsImageFormat("text/plain") }
    }

    private val request =
        TileRequest(
            coordinate = TileCoordinate(z = 0, x = 0, y = 0),
            bounds =
                TileBounds(
                    topLeft = Point(-10.0, 50.0),
                    bottomRight = Point(20.0, 30.0),
                ),
        )

    /**
     * Verifies WMS 1.1.1 request naming and longitude/latitude BBOX serialization.
     *
     * Input: EPSG:4326 bounds west `-10`, south `30`, east `20`, north `50`.
     * Expected: `SRS=EPSG:4326` and BBOX `-10,30,20,50`.
     */
    @Test
    fun wms111UsesSrsAndXyBboxForEpsg4326() {
        val parameters = Url(source(version = WmsVersion.V1_1_1).cacheKey(request)).parameters

        assertEquals("EPSG:4326", parameters["SRS"])
        assertEquals("-10.0,30.0,20.0,50.0", parameters["BBOX"])
    }

    /**
     * Verifies authoritative latitude/longitude axis order for EPSG:4326 in WMS 1.3.0.
     *
     * Input: the same geographic bounds serialized using WMS version `1.3.0`.
     * Expected: `CRS=EPSG:4326` and YX BBOX `30,-10,50,20`.
     */
    @Test
    fun wms130UsesCrsAndAuthoritativeAxisOrderForEpsg4326() {
        val parameters = Url(source(version = WmsVersion.V1_3_0).cacheKey(request)).parameters

        assertEquals("EPSG:4326", parameters["CRS"])
        assertEquals("30.0,-10.0,50.0,20.0", parameters["BBOX"])
    }

    /**
     * Verifies that Web Mercator retains XY axis order under WMS 1.3.0.
     *
     * Input: EPSG:3857 source with bounds `(-10, 30, 20, 50)`.
     * Expected: `CRS=EPSG:3857` and unchanged XY BBOX ordering.
     */
    @Test
    fun wms130KeepsXyOrderForWebMercator() {
        val source =
            WmsTileSource(
                projection = Epsg3857Projection,
                baseUrl = "https://example.com/wms",
                layerNames = listOf("base"),
                version = WmsVersion.V1_3_0,
            )

        val parameters = Url(source.cacheKey(request)).parameters

        assertEquals("EPSG:3857", parameters["CRS"])
        assertEquals("-10.0,30.0,20.0,50.0", parameters["BBOX"])
    }

    /**
     * Verifies explicit axis-order override for non-standard server behavior.
     *
     * Input: WMS 1.3.0 EPSG:4326 source forced to `WmsAxisOrder.XY`.
     * Expected: BBOX is serialized as `-10,30,20,50` instead of the default YX order.
     */
    @Test
    fun explicitAxisOrderSupportsCustomServerCrsRules() {
        val parameters =
            Url(
                source(
                    version = WmsVersion.V1_3_0,
                    axisOrder = WmsAxisOrder.XY,
                ).cacheKey(request),
            ).parameters

        assertEquals("-10.0,30.0,20.0,50.0", parameters["BBOX"])
    }

    @Test
    fun wmsValuesAreEncodedAndExistingEndpointParametersArePreserved() {
        val url =
            Url(
                WmsTileSource(
                    projection = Epsg4326Projection,
                    baseUrl = "https://example.com/wms?api_key=a%2Bb&request=stale&srs=stale#section",
                    layerNames = listOf("roads & rivers"),
                    styles = listOf("night=blue"),
                    format = WmsImageFormat("image/png; mode=8bit"),
                ).cacheKey(request),
            )

        assertEquals("a+b", url.parameters["api_key"])
        assertEquals("GetMap", url.parameters["REQUEST"])
        assertEquals("roads & rivers", url.parameters["LAYERS"])
        assertEquals("night=blue", url.parameters["STYLES"])
        assertEquals("image/png; mode=8bit", url.parameters["FORMAT"])
        assertEquals(Epsg4326Projection.id, url.parameters["SRS"])
        assertEquals(1, url.parameters.names().count { it.equals("SRS", ignoreCase = true) })
        assertEquals(false, url.parameters.names().any { it.equals("CRS", ignoreCase = true) })
        assertEquals("section", url.fragment)
    }

    private fun source(
        version: WmsVersion,
        axisOrder: WmsAxisOrder = WmsAxisOrder.forCrs(Epsg4326Projection.id),
    ): WmsTileSource =
        WmsTileSource(
            projection = Epsg4326Projection,
            baseUrl = "https://example.com/wms",
            layerNames = listOf("base"),
            version = version,
            axisOrder = axisOrder,
        )
}
