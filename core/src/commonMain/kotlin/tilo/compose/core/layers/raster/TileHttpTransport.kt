package tilo.compose.core.layers.raster

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import tilo.compose.core.net.sharedHttpClient

/** Internal HTTP seam used by raster sources and deterministic network tests. */
internal fun interface TileHttpTransport {
    suspend fun readImage(url: String): ByteArray?
}

internal interface DiagnosticTileHttpTransport : TileHttpTransport {
    suspend fun readImageResult(url: String): TileReadResult
}

internal class KtorTileHttpTransport(
    private val http: HttpClient = sharedHttpClient(),
) : DiagnosticTileHttpTransport {
    override suspend fun readImage(url: String): ByteArray? = http.get(url).readTileImageBytesOrNull()

    @Suppress("TooGenericExceptionCaught")
    override suspend fun readImageResult(url: String): TileReadResult =
        try {
            http.get(url).readTileImageResult()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            TileReadResult.Failure(
                kind = RasterTileFailureKind.NetworkUnavailable,
                message = error.message ?: "Tile network request failed",
                cause = error,
            )
        }
}
