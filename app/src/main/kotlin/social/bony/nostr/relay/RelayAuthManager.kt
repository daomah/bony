package social.bony.nostr.relay

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import social.bony.account.signer.NostrSignerFactory
import social.bony.nostr.EventKind
import social.bony.nostr.UnsignedEvent
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RelayAuthManager"

/**
 * NIP-42: responds to AUTH challenges from relays.
 *
 * Observes [RelayPool.messages] for [RelayMessage.Auth] and replies with
 * a signed kind-22242 event containing the relay URL and challenge string.
 *
 * Call [start] once from MainActivity. AUTH requires signing, so the scope
 * should be tied to the activity lifetime (lifecycleScope).
 */
@Singleton
class RelayAuthManager @Inject constructor(
    private val pool: RelayPool,
    private val signerFactory: NostrSignerFactory,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            pool.messages.collect { (relayUrl, message) ->
                if (message is RelayMessage.Auth) {
                    handleAuth(scope, relayUrl, message.challenge)
                }
            }
        }
    }

    private fun handleAuth(scope: CoroutineScope, relayUrl: String, challenge: String) {
        scope.launch {
            Log.d(TAG, "AUTH challenge from $relayUrl: $challenge")
            val signer = signerFactory.forActiveAccount() ?: run {
                Log.w(TAG, "No active account — cannot respond to AUTH from $relayUrl")
                return@launch
            }

            val unsigned = UnsignedEvent(
                pubkey = signer.pubkey,
                kind = EventKind.AUTH,
                content = "",
                tags = listOf(
                    buildJsonArray { add(JsonPrimitive("relay")); add(JsonPrimitive(relayUrl)) },
                    buildJsonArray { add(JsonPrimitive("challenge")); add(JsonPrimitive(challenge)) },
                ),
            )

            signer.signEvent(unsigned)
                .onSuccess { signed ->
                    pool.send(relayUrl, ClientMessage.Auth(signed))
                    Log.d(TAG, "AUTH sent to $relayUrl")
                }
                .onFailure { e ->
                    Log.w(TAG, "AUTH signing failed for $relayUrl: ${e.message}")
                }
        }
    }
}
