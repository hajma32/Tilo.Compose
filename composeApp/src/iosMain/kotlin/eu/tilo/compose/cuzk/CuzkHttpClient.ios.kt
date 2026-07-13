package eu.tilo.compose.cuzk

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

internal actual fun createCuzkHttpClient(): HttpClient = HttpClient(Darwin)
