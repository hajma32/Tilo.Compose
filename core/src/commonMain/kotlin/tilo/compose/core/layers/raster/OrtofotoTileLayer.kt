package tilo.compose.core.layers.raster

import tilo.compose.core.projection.Epsg5514Projection
import tilo.compose.core.tile.TileGrid

/**
 * Factory for CUZK Ortofoto WMS layer (EPSG:5514).
 *
 * Uses defaults discovered in GetCapabilities:
 * - baseUrl: https://ags.cuzk.gov.cz/arcgis1/services/ORTOFOTO/MapServer/WMSServer
 * - default layer name: "0"
 * - supported CRS: EPSG:5514 (S-JTSK / Krovak East North)
 * - recommended WMS CRS parameter name: "CRS" (WMS 1.3.0)
 *
 * The TileGrid is initialized from the service bounding box reported in GetCapabilities
 * so that zoom=0 yields a single tile covering the whole dataset extent. Caller may
 * override any parameter if desired.
 */
fun createOrtofotoTileLayer(
    id: String = "ortofoto-cuzk",
    baseUrl: String = "https://ags.cuzk.gov.cz/arcgis1/services/ORTOFOTO/MapServer/WMSServer",
    layers: String = "0",
    styles: String = "default",
    format: String = "image/png",
    crsParamName: String = "SRS",
    grid: TileGrid = TileGrid(
        // Bounding box (EPSG:5514) taken from GetCapabilities (minx, miny, maxx, maxy)
        // minX = -907841.056021, maxX = -416691.670279
        // minY = -1230916.869000, maxY = -932111.729700
        originX = -907841.056021,
        originY = -932111.729700,
        worldWidth = (-416691.670279) - (-907841.056021),
        nTilesX0 = 1,
        nTilesY0 = 1,
        tileSize = 256
    )
) = WMSTileLayer(
    id = id,
    projection = Epsg5514Projection,
    grid = grid,
    baseUrl = baseUrl,
    layers = layers,
    crs = Epsg5514Projection.id,
    styles = styles,
    format = format,
    crsParamName = crsParamName
)
