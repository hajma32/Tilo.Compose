package tilo.compose.core.layers.raster

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

internal suspend fun HttpResponse.readTileImageBytesOrNull(): ByteArray? {
    if (!status.isSuccess()) return null
    val bytes = body<ByteArray>()
    return bytes.takeIf { it.isSupportedImageBytes() }
}

private fun ByteArray.isSupportedImageBytes(): Boolean =
    hasPrefix(0xFF, 0xD8, 0xFF) ||
        hasPrefix(0x89, 0x50, 0x4E, 0x47) ||
        hasPrefix(0x47, 0x49, 0x46, 0x38) ||
        (size >= 12 &&
            hasPrefix(0x52, 0x49, 0x46, 0x46) &&
            this[8] == 0x57.toByte() &&
            this[9] == 0x45.toByte() &&
            this[10] == 0x42.toByte() &&
            this[11] == 0x50.toByte())

private fun ByteArray.hasPrefix(vararg prefix: Int): Boolean =
    size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index].toByte() }
