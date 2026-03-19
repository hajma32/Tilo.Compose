package tilo.compose.data.mbtiles

import okio.Buffer
import okio.GzipSource
import okio.buffer

internal object MbtilesCompression {
    fun ungzipIfNeeded(bytes: ByteArray): ByteArray {
        val isGzip = bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
        if (!isGzip) return bytes

        val source = GzipSource(Buffer().write(bytes)).buffer()
        return try {
            source.readByteArray()
        } finally {
            source.close()
        }
    }
}
