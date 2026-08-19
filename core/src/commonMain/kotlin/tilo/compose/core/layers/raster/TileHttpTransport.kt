package tilo.compose.core.layers.raster

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.CancellationException
import tilo.compose.core.net.sharedHttpClient

/** One HTTP GET issued by a remote raster source. */
class RasterHttpRequest(
    val url: String,
    headers: Map<String, String> = emptyMap(),
) {
    val headers: Map<String, String> = normalizedRasterHeaders(headers)

    /** Returns a request header value, matching [name] case-insensitively. */
    fun header(name: String): String? = headers[canonicalRasterHeaderName(name)]

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is RasterHttpRequest &&
            url == other.url &&
            headers == other.headers

    override fun hashCode(): Int = 31 * url.hashCode() + headers.hashCode()

    override fun toString(): String =
        "RasterHttpRequest(url=${url.redactedForDiagnostics()}, headerNames=${headers.keys})"
}

/** HTTP response returned by an application-provided raster transport. */
class RasterHttpResponse(
    val statusCode: Int,
    headers: Map<String, List<String>> = emptyMap(),
    body: ByteArray = byteArrayOf(),
) {
    init {
        require(statusCode in 100..599) { "HTTP status code must be between 100 and 599." }
    }

    val headers: Map<String, List<String>> = normalizedRasterResponseHeaders(headers)
    private val responseBody: ByteArray = body.copyOf()

    /** Immutable snapshot of the response body. */
    val body: ByteArray
        get() = responseBody.copyOf()

    internal val bodyBytes: ByteArray
        get() = responseBody

    /** Returns the first value for [name], matching the HTTP name case-insensitively. */
    fun header(name: String): String? = headers[canonicalRasterHeaderName(name)]?.firstOrNull()

    val isSuccess: Boolean
        get() = statusCode in 200..299

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is RasterHttpResponse &&
            statusCode == other.statusCode &&
            headers == other.headers &&
            responseBody.contentEquals(other.responseBody)

    override fun hashCode(): Int {
        var result = statusCode
        result = 31 * result + headers.hashCode()
        result = 31 * result + responseBody.contentHashCode()
        return result
    }

    override fun toString(): String =
        "RasterHttpResponse(statusCode=$statusCode, headerNames=${headers.keys}, bodySize=${responseBody.size})"
}

/** HTTP status failure encountered while initializing a remote raster source. */
class RasterHttpStatusException(
    val statusCode: Int,
    message: String,
    headers: Map<String, List<String>> = emptyMap(),
) : IllegalStateException(message) {
    val headers: Map<String, List<String>> = normalizedRasterResponseHeaders(headers)

    /** Returns the first response header value, matching [name] case-insensitively. */
    fun header(name: String): String? = headers[canonicalRasterHeaderName(name)]?.firstOrNull()
}

/**
 * Application-provided HTTP transport for WMS capabilities and remote raster tiles.
 *
 * Return the complete response, throw when the request cannot be completed, and
 * propagate coroutine cancellation. The application owns the transport and any
 * client or other resources behind it.
 */
fun interface RasterHttpTransport {
    suspend fun get(request: RasterHttpRequest): RasterHttpResponse
}

/**
 * Headers and optional custom transport shared by one remote raster source.
 *
 * `transportKey` is the stable semantic identity used by managed Compose layers.
 * Inline transport lambdas are safe across recomposition because the default key
 * is stable. Change the key when a new transport must recreate the raster runtime;
 * otherwise the existing runtime keeps using its original, equivalent transport.
 * WMS workflows do not forward `headers` to a GetMap endpoint on another origin
 * unless `allowCrossOriginHeaders` is explicitly enabled.
 */
class RasterHttpConfig(
    headers: Map<String, String> = emptyMap(),
    val transport: RasterHttpTransport? = null,
    transportKey: Any = Unit,
    val allowCrossOriginHeaders: Boolean = false,
) {
    val headers: Map<String, String> = normalizedRasterHeaders(headers)
    val transportKey: Any? = transport?.let { transportKey }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is RasterHttpConfig &&
            headers == other.headers &&
            transportKey == other.transportKey &&
            allowCrossOriginHeaders == other.allowCrossOriginHeaders

    override fun hashCode(): Int {
        var result = 31 * headers.hashCode() + (transportKey?.hashCode() ?: 0)
        result = 31 * result + allowCrossOriginHeaders.hashCode()
        return result
    }

    override fun toString(): String =
        "RasterHttpConfig(headerNames=${headers.keys}, customTransport=${transport != null})"
}

/** Internal HTTP seam used by raster sources and deterministic network tests. */
internal fun interface TileHttpTransport {
    suspend fun readImage(url: String): ByteArray?
}

internal interface DiagnosticTileHttpTransport : TileHttpTransport {
    suspend fun readImageResult(url: String): TileReadResult
}

internal class KtorTileHttpTransport(
    private val http: HttpClient = sharedHttpClient(),
    private val headers: Map<String, String> = emptyMap(),
) : DiagnosticTileHttpTransport {
    override suspend fun readImage(url: String): ByteArray? = request(url).readTileImageBytesOrNull()

    @Suppress("TooGenericExceptionCaught")
    override suspend fun readImageResult(url: String): TileReadResult =
        try {
            request(url).readTileImageResult()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            TileReadResult.Failure(
                kind = RasterTileFailureKind.NetworkUnavailable,
                message = error.message ?: "Tile network request failed",
                cause = error,
            )
        }

    private suspend fun request(url: String): RasterHttpResponse = http.getRaster(url, headers)
}

internal fun RasterHttpConfig.tileHttpTransport(): TileHttpTransport =
    transport?.let { customTransport ->
        object : DiagnosticTileHttpTransport {
            override suspend fun readImage(url: String): ByteArray? = request(url).readTileImageBytesOrNull()

            @Suppress("TooGenericExceptionCaught")
            override suspend fun readImageResult(url: String): TileReadResult =
                try {
                    request(url).readTileImageResult()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    TileReadResult.Failure(
                        kind = RasterTileFailureKind.NetworkUnavailable,
                        message = error.message ?: "Raster HTTP transport failed",
                        cause = error,
                    )
                }

            private suspend fun request(url: String): RasterHttpResponse =
                customTransport.get(RasterHttpRequest(url = url, headers = headers))
        }
    } ?: KtorTileHttpTransport(headers = headers)

internal suspend fun RasterHttpConfig.readResponse(url: String): RasterHttpResponse =
    transport?.get(RasterHttpRequest(url = url, headers = headers))
        ?: sharedHttpClient().getRaster(url, headers)

private suspend fun HttpClient.getRaster(
    url: String,
    requestHeaders: Map<String, String>,
): RasterHttpResponse {
    val response =
        get(url) {
            headers {
                requestHeaders.forEach { (name, value) -> append(name, value) }
            }
        }
    return RasterHttpResponse(
        statusCode = response.status.value,
        headers = response.headers.entries().associate { (name, values) -> name to values.toList() },
        body = response.bodyAsBytes(),
    )
}

private fun normalizedRasterHeaders(headers: Map<String, String>): Map<String, String> =
    buildMap {
        headers.forEach { (name, value) ->
            require(name.isValidRasterHeaderName()) { "Invalid HTTP header name." }
            require('\r' !in value && '\n' !in value) { "HTTP header values must not contain CR or LF." }
            put(canonicalRasterHeaderName(name), value)
        }
    }

private fun normalizedRasterResponseHeaders(headers: Map<String, List<String>>): Map<String, List<String>> =
    buildMap {
        headers.forEach { (name, values) ->
            require(name.isValidRasterHeaderName()) { "Invalid HTTP header name." }
            val canonicalName = canonicalRasterHeaderName(name)
            put(canonicalName, get(canonicalName).orEmpty() + values)
        }
    }

private fun String.isValidRasterHeaderName(): Boolean =
    isNotEmpty() &&
        all { character ->
            character in 'a'..'z' ||
                character in 'A'..'Z' ||
                character in '0'..'9' ||
                character in "!#$%&'*+-.^_`|~"
        }

private fun canonicalRasterHeaderName(name: String): String =
    name.lowercase().split('-').joinToString("-") { part ->
        part.replaceFirstChar { character -> character.uppercaseChar() }
    }

private fun String.redactedForDiagnostics(): String {
    val firstSecretDelimiter =
        listOf(indexOf('?'), indexOf('#'))
            .filter { it >= 0 }
            .minOrNull()
            ?: length
    val urlPrefix = substring(0, firstSecretDelimiter)
    val schemeDelimiter = urlPrefix.indexOf("://")
    if (schemeDelimiter < 0) return "…"
    val authorityStart = schemeDelimiter + 3
    val authorityEnd =
        urlPrefix.indexOf('/', startIndex = authorityStart).takeIf { it >= 0 }
            ?: urlPrefix.length
    val authority = urlPrefix.substring(authorityStart, authorityEnd).substringAfterLast('@')
    val safePrefix = urlPrefix.substring(0, authorityStart) + authority
    val pathMarker = if (authorityEnd < urlPrefix.length) "/…" else ""
    val suffix =
        when {
            indexOf('?') == firstSecretDelimiter -> "?…"
            indexOf('#') == firstSecretDelimiter -> "#…"
            else -> ""
        }
    return safePrefix + pathMarker + suffix
}
