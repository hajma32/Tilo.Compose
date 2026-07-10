package tilo.compose.core.layers.raster

import tilo.compose.core.projection.Epsg5514Projection
import tilo.compose.core.tile.TileGrid

/**
 * Factory for CUZK Ortofoto WMS layer (EPSG:5514).
 */
fun createOrtofotoTileLayer(
    id: String = "ortofoto-cuzk",
    baseUrl: String = "https://ags.cuzk.gov.cz/arcgis1/services/ORTOFOTO/MapServer/WMSServer",
    layers: String = "0",
    styles: String = "",
    format: String = "image/jpeg",
    crsParamName: String = "SRS",
    grid: TileGrid =
        TileGrid(
            // Bounding box (EPSG:5514) taken from GetCapabilities (minx, miny, maxx, maxy)
            // minX = -907841.056021, maxX = -416691.670279
            // minY = -1230916.869000, maxY = -932111.729700
            originX = -907841.056021,
            originY = -932111.729700,
            worldWidth = (-416691.670279) - (-907841.056021),
            nTilesX0 = 1,
            nTilesY0 = 1,
            tileSize = 256,
        ),
): TileLayer =
    WMSTileLayer(
        id = id,
        projection = Epsg5514Projection,
        grid = grid,
        baseUrl = baseUrl,
        layers = layers,
        crs = Epsg5514Projection.id,
        styles = styles,
        format = format,
        crsParamName = crsParamName,
    )
