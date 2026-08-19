package tilo.compose.core.layers.raster

internal fun RasterHttpResponse.readTileImageBytesOrNull(): ByteArray? =
    (readTileImageResult() as? TileReadResult.Success)?.bytes

internal fun RasterHttpResponse.readTileImageResult(): TileReadResult {
    if (statusCode == 404 || statusCode == 410 || statusCode == 204) {
        return TileReadResult.Missing
    }
    if (!isSuccess) {
        return TileReadResult.Failure(
            kind = RasterTileFailureKind.HttpStatus,
            message = "Tile request returned HTTP $statusCode",
            httpStatus = statusCode,
        )
    }
    val bytes = bodyBytes
    return if (bytes.isSupportedImageBytes()) {
        TileReadResult.Success(bytes)
    } else {
        TileReadResult.Failure(
            kind = RasterTileFailureKind.InvalidPayload,
            message = "Tile response is not a supported image",
        )
    }
}

private fun ByteArray.isSupportedImageBytes(): Boolean =
    hasPrefix(0xFF, 0xD8, 0xFF) ||
        hasPrefix(0x89, 0x50, 0x4E, 0x47) ||
        hasPrefix(0x47, 0x49, 0x46, 0x38) ||
        (
            size >= 12 &&
                hasPrefix(0x52, 0x49, 0x46, 0x46) &&
                this[8] == 0x57.toByte() &&
                this[9] == 0x45.toByte() &&
                this[10] == 0x42.toByte() &&
                this[11] == 0x50.toByte()
        )

private fun ByteArray.hasPrefix(vararg prefix: Int): Boolean =
    size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index].toByte() }
