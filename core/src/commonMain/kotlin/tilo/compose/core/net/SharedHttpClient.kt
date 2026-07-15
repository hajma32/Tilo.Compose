package tilo.compose.core.net

import io.ktor.client.HttpClient

/**
 * Platform-specific shared HttpClient singleton.
 */
internal expect fun sharedHttpClient(): HttpClient
