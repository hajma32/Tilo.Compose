package eu.tilo.compose.cuzk

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun createCuzkHttpClient(): HttpClient = HttpClient(OkHttp)
