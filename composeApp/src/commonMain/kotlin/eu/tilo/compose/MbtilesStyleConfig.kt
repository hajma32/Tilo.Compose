package eu.tilo.compose

import tilo.compose.core.layers.vector.DefaultVectorTileBasemapStyleConfig
import tilo.compose.core.layers.vector.VectorTileStyleConfigMap

/**
 * App-facing alias for the shared MBTiles basemap style config.
 *
 * Source of truth lives in `DefaultVectorTileBasemapStyleConfig` inside `core`.
 */
val MBTILES_STYLE_CONFIG: VectorTileStyleConfigMap = DefaultVectorTileBasemapStyleConfig.basemap
