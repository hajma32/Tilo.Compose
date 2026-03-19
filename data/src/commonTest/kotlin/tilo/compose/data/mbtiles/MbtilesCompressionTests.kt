package tilo.compose.data.mbtiles

import kotlin.test.Test
import kotlin.test.assertContentEquals
import okio.Buffer
import okio.GzipSink
import okio.buffer

class MbtilesCompressionTests {

    @Test
    fun ungzipIfNeededReturnsOriginalBytesForPlainPayload() {
        val bytes = "plain-tile".encodeToByteArray()

        assertContentEquals(bytes, MbtilesCompression.ungzipIfNeeded(bytes))
    }

    @Test
    fun ungzipIfNeededDecompressesGzipPayload() {
        val original = "vector-tile".encodeToByteArray()
        val target = Buffer()
        val sink = GzipSink(target).buffer()
        try {
            sink.write(original)
        } finally {
            sink.close()
        }
        val gzipped = target.readByteArray()

        assertContentEquals(original, MbtilesCompression.ungzipIfNeeded(gzipped))
    }
}
