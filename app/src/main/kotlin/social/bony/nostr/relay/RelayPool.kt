package social.bony.nostr.relay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.bony.nostr.Filter
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class RelayStatus { CONNECTING, CONNECTED, DISCONNECTED }

private const val RECONNECT_DELAY_MS = 5_000L
private const val MAX_RECONNECT_DELAY_MS = 60_000L

/**
 * Manages a set of [RelayConnection]s and multiplexes their messages into a
 * single [messages] flow.
 *
 * Subscriptions opened via [subscribe] are automatically re-sent to a relay
 * when it reconnects. Call [unsubscribe] or cancel the returned [Job] to stop.
 */
class RelayPool(
    private val scope: CoroutineScope,
    private var connectionFactory: (url: String) -> RelayConnection,
) {
    private val connections = ConcurrentHashMap<String, RelayEntry>()
    private val activeSubscriptions = ConcurrentHashMap<String, Subscription>()

    private val _messages = MutableSharedFlow<PoolMessage>(extraBufferCapacity = 256)
    val messages: SharedFlow<PoolMessage> = _messages.asSharedFlow()

    private val _relayStatuses = MutableStateFlow<Map<String, RelayStatus>>(emptyMap())
    val relayStatuses: StateFlow<Map<String, RelayStatus>> = _relayStatuses.asStateFlow()

    // ── Relay management ──────────────────────────────────────────────────────

    fun addRelay(url: String) {
        if (connections.containsKey(url)) return
        val connection = connectionFactory(url)
        val job = scope.launch { connectWithRetry(url, connection) }
        connections[url] = RelayEntry(connection, job)
        _relayStatuses.update { it + (url to RelayStatus.CONNECTING) }
        Timber.d("Added relay: $url")
    }

    fun removeRelay(url: String) {
        connections.remove(url)?.job?.cancel()
        _relayStatuses.update { it - url }
        Timber.d("Removed relay: $url")
    }

    /**
     * Swap the transport (e.g. plain vs. Tor SOCKS proxy) and reconnect all
     * existing relays so the new client takes effect immediately.
     */
    fun updateTransport(client: okhttp3.OkHttpClient) {
        connectionFactory = { url -> RelayConnection(url, client) }
        val urls = connections.keys.toList()
        urls.forEach { removeRelay(it) }
        urls.forEach { addRelay(it) }
        Timber.d("Transport updated, reconnecting ${urls.size} relay(s)")
    }

    fun relayUrls(): Set<String> = connections.keys.toSet()

    // ── Subscriptions ─────────────────────────────────────────────────────────

    /**
     * Opens a subscription on all current (and future) relays.
     * Returns the subscription ID so callers can filter [messages] by it.
     */
    fun subscribe(filters: List<Filter>, id: String = newSubId()): String {
        val sub = Subscription(id, filters)
        activeSubscriptions[id] = sub
        val req = ClientMessage.Req(id, filters)
        connections.values.forEach { it.connection.send(req) }
        Timber.d("Subscribed: $id with ${filters.size} filter(s)")
        return id
    }

    /** Broadcasts an event to all connected relays. */
    fun publish(event: social.bony.nostr.Event) {
        val msg = ClientMessage.Publish(event)
        connections.values.forEach { it.connection.send(msg) }
        Timber.d("Published event ${event.id.take(8)}… to ${connections.size} relay(s)")
    }

    /** Sends a message to a specific relay by URL. Returns false if not connected. */
    fun send(relayUrl: String, message: ClientMessage): Boolean =
        connections[relayUrl]?.connection?.send(message) ?: false

    fun unsubscribe(subscriptionId: String) {
        activeSubscriptions.remove(subscriptionId) ?: return
        val close = ClientMessage.Close(subscriptionId)
        connections.values.forEach { it.connection.send(close) }
        Timber.d("Unsubscribed: $subscriptionId")
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private suspend fun connectWithRetry(url: String, connection: RelayConnection) {
        var delayMs = RECONNECT_DELAY_MS
        while (true) {
            Timber.d("Connecting: $url")
            try {
                connection.messages.collect { message ->
                    when (message) {
                        is RelayMessage.Connected -> {
                            delayMs = RECONNECT_DELAY_MS
                            _relayStatuses.update { it + (url to RelayStatus.CONNECTED) }
                            activeSubscriptions.values.forEach { sub ->
                                connection.send(ClientMessage.Req(sub.id, sub.filters))
                            }
                            Timber.d("Replayed ${activeSubscriptions.size} subscription(s) to $url")
                        }
                        else -> {
                            delayMs = RECONNECT_DELAY_MS
                            _messages.emit(PoolMessage(url, message))
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Collection error on $url")
            }

            if (!connections.containsKey(url)) break

            _relayStatuses.update { it + (url to RelayStatus.DISCONNECTED) }
            Timber.d("Reconnecting $url in ${delayMs}ms")
            delay(delayMs)
            delayMs = (delayMs * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
        }
    }

    private fun newSubId(): String = UUID.randomUUID().toString().take(8)

    // ── Data classes ──────────────────────────────────────────────────────────

    private data class RelayEntry(val connection: RelayConnection, val job: Job)
    private data class Subscription(val id: String, val filters: List<Filter>)
}

/**
 * A relay message tagged with the relay URL it arrived from.
 */
data class PoolMessage(
    val relayUrl: String,
    val message: RelayMessage,
)
