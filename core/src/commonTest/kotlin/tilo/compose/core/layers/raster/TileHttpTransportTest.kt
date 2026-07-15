package tilo.compose.core.layers.raster

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import tilo.compose.core.geometry.Point
import tilo.compose.core.tile.TileBounds
import tilo.compose.core.tile.TileCoordinate
import tilo.compose.core.tile.TileRequest

class TileHttpTransportTest {

    /**
     * Verifies recognition of all encoded image formats supported by tile transport.
     *
     * Input: successful HTTP responses with PNG, JPEG, GIF, and WebP signatures.
     * Expected: each byte array is returned unchanged.
     */
    @Test
    fun successfulSupportedImagesAreAccepted() = runTest {
        supportedImages.forEach { expected ->
            val client = clientResponding(HttpStatusCode.OK, expected)
            try {
                assertContentEquals(expected, KtorTileHttpTransport(client).readImage("https://tiles.test/0/0/0"))
            } finally {
                client.close()
            }
        }
    }

    /**
     * Verifies rejection of HTTP failures and payloads that are not supported images.
     *
     * Input: 404, 500, HTML-with-200, and empty-with-200 responses.
     * Expected: every response is represented as an unavailable tile (`null`).
     */
    @Test
    fun errorStatusesAndNonImagesAreRejected() = runTest {
        listOf(
            HttpStatusCode.NotFound to png,
            HttpStatusCode.InternalServerError to png,
            HttpStatusCode.OK to "<html>not a tile</html>".encodeToByteArray(),
            HttpStatusCode.OK to byteArrayOf(),
        ).forEach { (status, body) ->
            val client = clientResponding(status, body)
            try {
                assertNull(KtorTileHttpTransport(client).readImage("https://tiles.test/tile"))
            } finally {
                client.close()
            }
        }
    }

    /**
     * Verifies that coroutine cancellation is not converted into a missing tile.
     *
     * Input: a mock HTTP engine throwing `CancellationException`.
     * Expected: the same cancellation propagates to the caller.
     */
    @Test
    fun cancellationIsNeverConvertedToMissingTile() = runTest {
        val client = HttpClient(
            MockEngine {
                throw CancellationException("obsolete viewport")
            },
        )
        try {
            assertFailsWith<CancellationException> {
                KtorTileHttpTransport(client).readImage("https://tiles.test/tile")
            }
        } finally {
            client.close()
        }
    }

    /**
     * Verifies recovery after a transient transport exception.
     *
     * Input: one failed HTTP attempt followed by a successful PNG response for the same URL.
     * Expected: the first result is `null`, the second contains PNG bytes, and two attempts occur.
     */
    @Test
    fun transientNetworkFailureReturnsMissingTileAndNextRequestCanRecover() = runTest {
        var attempts = 0
        val client = HttpClient(
            MockEngine {
                attempts += 1
                if (attempts == 1) error("temporary transport failure")
                respond(content = png, status = HttpStatusCode.OK)
            },
        )
        val transport = KtorTileHttpTransport(client)
        try {
            assertNull(transport.readImage("https://tiles.test/tile"))
            assertContentEquals(png, transport.readImage("https://tiles.test/tile"))
            assertEquals(2, attempts)
        } finally {
            client.close()
        }
    }

    /**
     * Verifies XYZ URL expansion through the injected transport seam.
     *
     * Input: template `{z}/{x}/{y}` and coordinate `(3, 4, 5)`.
     * Expected: one request to `https://tiles.test/3/4/5.png` and the injected PNG result.
     */
    @Test
    fun xyzSourceUsesInjectedTransportAndBuildsAddressOnce() = runTest {
        val urls = mutableListOf<String>()
        val source = XYZTileSource(
            urlTemplate = "https://tiles.test/{z}/{x}/{y}.png",
            transport = TileHttpTransport { url ->
                urls += url
                png
            },
        )

        assertContentEquals(png, source.readTile(request(z = 3, x = 4, y = 5)))
        assertEquals(listOf("https://tiles.test/3/4/5.png"), urls)
    }

    /**
     * Verifies that WMS reads use the injected transport and preserve request parameters.
     *
     * Input: a WMS source for layer `roads` and one tile request.
     * Expected: one URL containing `REQUEST=GetMap` and `LAYERS=roads`, returning PNG bytes.
     */
    @Test
    fun wmsSourceUsesInjectedTransport() = runTest {
        val urls = mutableListOf<String>()
        val source = WMSTileSource(
            baseUrl = "https://maps.test/wms",
            layers = "roads",
            transport = TileHttpTransport { url ->
                urls += url
                png
            },
        )

        assertContentEquals(png, source.readTile(request(z = 0, x = 0, y = 0)))
        assertEquals(1, urls.size)
        assertEquals(true, "REQUEST=GetMap" in urls.single())
        assertEquals(true, "LAYERS=roads" in urls.single())
    }

    private fun clientResponding(status: HttpStatusCode, bytes: ByteArray): HttpClient =
        HttpClient(
            MockEngine {
                respond(content = bytes, status = status)
            },
        )

    private fun request(z: Int, x: Int, y: Int): TileRequest =
        TileRequest(
            coordinate = TileCoordinate(z = z, x = x, y = y),
            bounds = TileBounds(
                topLeft = Point(0.0, 1.0),
                bottomRight = Point(1.0, 0.0),
            ),
        )

    private companion object {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x00)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)
        val gif = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x00)
        val webp = byteArrayOf(
            0x52,
            0x49,
            0x46,
            0x46,
            0x00,
            0x00,
            0x00,
            0x00,
            0x57,
            0x45,
            0x42,
            0x50,
        )
        val supportedImages = listOf(png, jpeg, gif, webp)
    }
}
