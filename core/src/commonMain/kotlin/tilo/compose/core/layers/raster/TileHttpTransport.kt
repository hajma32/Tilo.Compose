package tilo.compose.core.layers.raster

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import tilo.compose.core.net.sharedHttpClient

/** Internal HTTP seam used by raster sources and deterministic network tests. */
internal fun interface TileHttpTransport {
    suspend fun readImage(url: String): ByteArray?
}

internal class KtorTileHttpTransport(
    private val http: HttpClient = sharedHttpClient(),
) : TileHttpTransport {
    override suspend fun readImage(url: String): ByteArray? = http.get(url).readTileImageBytesOrNull()
}
