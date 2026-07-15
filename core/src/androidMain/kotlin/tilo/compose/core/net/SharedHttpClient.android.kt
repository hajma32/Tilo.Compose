package tilo.compose.core.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import java.io.File
import java.util.concurrent.TimeUnit

private val sharedClient: HttpClient by lazy {
    val connectionPool = ConnectionPool(16, 5, TimeUnit.MINUTES)
    val dispatcher =
        Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        }
    val cacheDirectory =
        File(
            requireNotNull(System.getProperty("java.io.tmpdir")) {
                "Android HTTP cache directory is unavailable"
            },
            "tilo-http-cache",
        )
    val cache = Cache(cacheDirectory, 64L * 1024L * 1024L)
    HttpClient(OkHttp) {
        engine {
            config {
                connectionPool(connectionPool)
                dispatcher(dispatcher)
                cache(cache)
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 5_000
        }
    }
}

internal actual fun sharedHttpClient(): HttpClient = sharedClient
