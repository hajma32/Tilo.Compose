package tilo.compose.core.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import okhttp3.ConnectionPool
import java.util.concurrent.TimeUnit

private val sharedClient: HttpClient by lazy {
    val connectionPool = ConnectionPool(16, 5, TimeUnit.MINUTES)
    HttpClient(OkHttp) {
        engine {
            // OkHttpClient.Builder
            config {
                connectionPool(connectionPool)
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 5_000
        }
    }
}

actual fun sharedHttpClient(): HttpClient = sharedClient
