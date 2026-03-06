package tilo.compose.core.tile.utils

/**
 * Tile addressing mode used when mapping logical tile coordinates to source requests.
 */
enum class AddressingStrategy {
    /** Standard slippy-map XYZ addressing (y grows from north to south). */
    XYZ,

    /** TMS addressing (y is flipped against XYZ for the same zoom level). */
    TMS,

    /** WMS tile grid addressing (uses XYZ tile indices with WMS BBOX requests). */
    WMS
}

