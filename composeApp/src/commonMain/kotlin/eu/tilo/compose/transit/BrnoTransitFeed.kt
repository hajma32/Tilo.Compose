package eu.tilo.compose.transit

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal enum class TransitConnectionStatus {
    Idle,
    Connecting,
    Live,
    Reconnecting,
}

internal data class TransitFeedState(
    val status: TransitConnectionStatus,
    val vehicles: List<TransitVehicle> = emptyList(),
    val errorMessage: String? = null,
) {
    companion object {
        val Idle = TransitFeedState(status = TransitConnectionStatus.Idle)
    }
}

internal class BrnoTransitFeed(
    private val client: HttpClient = createTransitHttpClient(),
) {
    fun close() {
        client.close()
    }

    fun states(): Flow<TransitFeedState> =
        channelFlow {
            val output = this
            val mutex = Mutex()
            val tracked = mutableMapOf<String, TrackedVehicle>()
            var dirty = false
            var reconnectDelayMillis = 1_000L
            var firstConnection = true

            suspend fun snapshot(
                status: TransitConnectionStatus,
                errorMessage: String? = null,
                pruneStale: Boolean = false,
            ): TransitFeedState =
                mutex.withLock {
                    if (pruneStale) {
                        tracked.entries.removeAll { (_, trackedVehicle) ->
                            trackedVehicle.lastSeen.elapsedNow() >= VehicleStaleAfter
                        }
                    }
                    TransitFeedState(
                        status = status,
                        vehicles = tracked.values.map(TrackedVehicle::vehicle),
                        errorMessage = errorMessage,
                    )
                }

            while (currentCoroutineContext().isActive) {
                val connectingStatus = if (firstConnection) {
                    TransitConnectionStatus.Connecting
                } else {
                    TransitConnectionStatus.Reconnecting
                }
                output.send(snapshot(connectingStatus))

                var reconnectStateEmitted = false
                try {
                    client.webSocket(urlString = BrnoTransitWebSocketUrl) {
                        firstConnection = false
                        output.send(snapshot(TransitConnectionStatus.Live))
                        val publisher = launch {
                            while (isActive) {
                                delay(PublishIntervalMillis)
                                val shouldPublish = mutex.withLock {
                                    val beforePrune = tracked.size
                                    tracked.entries.removeAll { (_, trackedVehicle) ->
                                        trackedVehicle.lastSeen.elapsedNow() >= VehicleStaleAfter
                                    }
                                    val changed = dirty || tracked.size != beforePrune
                                    dirty = false
                                    changed
                                }
                                if (shouldPublish) {
                                    output.send(snapshot(TransitConnectionStatus.Live))
                                }
                            }
                        }

                        try {
                            for (frame in incoming) {
                                val message = (frame as? Frame.Text)?.readText() ?: continue
                                val updates = ArcGisTransitDecoder.decode(message)
                                if (updates.isEmpty()) continue
                                reconnectDelayMillis = 1_000L
                                mutex.withLock {
                                    updates.forEach { vehicle ->
                                        if (vehicle.active) {
                                            tracked[vehicle.id] = TrackedVehicle(
                                                vehicle = vehicle,
                                                lastSeen = TimeSource.Monotonic.markNow(),
                                            )
                                        } else {
                                            tracked.remove(vehicle.id)
                                        }
                                    }
                                    dirty = true
                                }
                            }
                        } finally {
                            publisher.cancelAndJoin()
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    reconnectStateEmitted = true
                    output.send(
                        snapshot(
                            status = TransitConnectionStatus.Reconnecting,
                            errorMessage = error.message ?: error::class.simpleName,
                            pruneStale = true,
                        )
                    )
                }

                if (!reconnectStateEmitted) {
                    output.send(
                        snapshot(
                            status = TransitConnectionStatus.Reconnecting,
                            errorMessage = "Stream connection closed",
                            pruneStale = true,
                        )
                    )
                }

                delay(reconnectDelayMillis)
                reconnectDelayMillis = (reconnectDelayMillis * 2).coerceAtMost(MaxReconnectDelayMillis)
            }
        }
}

private data class TrackedVehicle(
    val vehicle: TransitVehicle,
    val lastSeen: TimeMark,
)

private const val BrnoTransitWebSocketUrl =
    "wss://gis.brno.cz/geoevent/ws/services/stream_kordis_26/StreamServer/subscribe?outSR=4326"
private const val PublishIntervalMillis = 500L
private const val MaxReconnectDelayMillis = 15_000L
private val VehicleStaleAfter = 45.seconds
