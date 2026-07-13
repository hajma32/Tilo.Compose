package eu.tilo.compose.transit

import io.ktor.client.HttpClient

internal expect fun createTransitHttpClient(): HttpClient
