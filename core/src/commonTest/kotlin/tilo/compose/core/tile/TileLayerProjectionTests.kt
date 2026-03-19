package tilo.compose.core.tile

import kotlin.test.Test
import kotlin.test.assertFailsWith
import tilo.compose.core.layers.raster.WMSTileLayer
import tilo.compose.core.layers.raster.XYZTileLayer
import tilo.compose.core.map.Map
import tilo.compose.core.projection.Epsg3857Projection
import tilo.compose.core.projection.Epsg4326Projection

class TileLayerProjectionTests {

    @Test
    fun xyzTileLayerFailsFastWhenProjectionDoesNotMatchMap() {
        val map = Map(projection = Epsg3857Projection)
        val layer = XYZTileLayer(
            id = "xyz",
            grid = TileGrid(),
            projection = Epsg4326Projection,
            urlTemplate = "https://tiles/{z}/{x}/{y}.png"
        )

        assertFailsWith<IllegalArgumentException> {
            layer.validateProjection(map)
        }
    }

    @Test
    fun wmsTileLayerRejectsMismatchedCrsParameter() {
        assertFailsWith<IllegalArgumentException> {
            WMSTileLayer(
                id = "wms",
                projection = Epsg4326Projection,
                baseUrl = "https://example.test/wms",
                layers = "demo",
                crs = Epsg3857Projection.id
            )
        }
    }
}
