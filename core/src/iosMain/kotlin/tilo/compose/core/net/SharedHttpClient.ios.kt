package tilo.compose.core.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout

private val sharedClient: HttpClient by lazy {
    HttpClient(Darwin) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 5_000
        }
    }
}

internal actual fun sharedHttpClient(): HttpClient = sharedClient
