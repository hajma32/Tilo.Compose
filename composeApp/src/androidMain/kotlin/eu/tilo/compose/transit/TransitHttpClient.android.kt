package eu.tilo.compose.transit

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets

internal actual fun createTransitHttpClient(): HttpClient =
    HttpClient(OkHttp) {
        install(WebSockets)
    }
