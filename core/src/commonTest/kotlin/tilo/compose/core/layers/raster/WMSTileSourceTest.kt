package tilo.compose.core.layers.raster

import kotlin.test.Test
import kotlin.test.assertContains
import tilo.compose.core.geometry.Point
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.Epsg4326Projection
import tilo.compose.core.tile.TileBounds
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.TileRequest

class WMSTileSourceTest {
    private val request =
        TileRequest(
            coordinate = TileCoordinate(z = 0, x = 0, y = 0),
            bounds = TileBounds(
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
        val url = source(version = "1.1.1").cacheKey(request)

        assertContains(url, "&SRS=EPSG:4326")
        assertContains(url, "&BBOX=-10.0,30.0,20.0,50.0")
    }

    /**
     * Verifies authoritative latitude/longitude axis order for EPSG:4326 in WMS 1.3.0.
     *
     * Input: the same geographic bounds serialized using WMS version `1.3.0`.
     * Expected: `CRS=EPSG:4326` and YX BBOX `30,-10,50,20`.
     */
    @Test
    fun wms130UsesCrsAndAuthoritativeAxisOrderForEpsg4326() {
        val url = source(version = "1.3.0").cacheKey(request)

        assertContains(url, "&CRS=EPSG:4326")
        assertContains(url, "&BBOX=30.0,-10.0,50.0,20.0")
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
            WMSTileSource(
                projection = Epsg3857Projection,
                baseUrl = "https://example.com/wms",
                layers = "base",
                version = "1.3.0",
            )

        val url = source.cacheKey(request)

        assertContains(url, "&CRS=EPSG:3857")
        assertContains(url, "&BBOX=-10.0,30.0,20.0,50.0")
    }

    /**
     * Verifies explicit axis-order override for non-standard server behavior.
     *
     * Input: WMS 1.3.0 EPSG:4326 source forced to `WMSAxisOrder.XY`.
     * Expected: BBOX is serialized as `-10,30,20,50` instead of the default YX order.
     */
    @Test
    fun explicitAxisOrderSupportsCustomServerCrsRules() {
        val url =
            source(
                version = "1.3.0",
                axisOrder = WMSAxisOrder.XY,
            ).cacheKey(request)

        assertContains(url, "&BBOX=-10.0,30.0,20.0,50.0")
    }

    private fun source(
        version: String,
        axisOrder: WMSAxisOrder = WMSAxisOrder.forCrs(Epsg4326Projection.id),
    ): WMSTileSource =
        WMSTileSource(
            projection = Epsg4326Projection,
            baseUrl = "https://example.com/wms",
            layers = "base",
            version = version,
            axisOrder = axisOrder,
        )
}
