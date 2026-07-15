package tilo.compose.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TileImageDecoderIosTest {
    /**
     * Verifies the real Skia image decoder used by the iOS target.
     *
     * Input: a valid 1 × 1 PNG fixture and a three-byte corrupt payload.
     * Expected: a 1 × 1 image for PNG and `null` for corrupt bytes.
     */
    @Test
    fun skiaDecoderAcceptsRealPngAndRejectsCorruptBytes() {
        val image = decodeTileImageBitmap(onePixelPng)

        assertNotNull(image)
        assertEquals(1, image.width)
        assertEquals(1, image.height)
        assertNull(decodeTileImageBitmap(byteArrayOf(0x01, 0x02, 0x03)))
    }

    private companion object {
        val onePixelPng =
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
                0x00,
                0x00,
                0x00,
                0x0D,
                0x49,
                0x48,
                0x44,
                0x52,
                0x00,
                0x00,
                0x00,
                0x01,
                0x00,
                0x00,
                0x00,
                0x01,
                0x08,
                0x04,
                0x00,
                0x00,
                0x00,
                0xB5.toByte(),
                0x1C,
                0x0C,
                0x02,
                0x00,
                0x00,
                0x00,
                0x0B,
                0x49,
                0x44,
                0x41,
                0x54,
                0x78,
                0xDA.toByte(),
                0x63,
                0x64,
                0xF8.toByte(),
                0x0F,
                0x00,
                0x01,
                0x05,
                0x01,
                0x01,
                0x27,
                0x18,
                0xE3.toByte(),
                0x66,
                0x00,
                0x00,
                0x00,
                0x00,
                0x49,
                0x45,
                0x4E,
                0x44,
                0xAE.toByte(),
                0x42,
                0x60,
                0x82.toByte(),
            )
    }
}
