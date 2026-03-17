package social.bony.profile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import social.bony.nostr.Event
import social.bony.nostr.EventKind
import social.bony.nostr.ProfileContent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cache of kind-0 profile metadata keyed by hex pubkey.
 *
 * Profiles are updated only when a newer event arrives for the same pubkey.
 * This will be backed by Room in a future pass for persistence across sessions.
 */
@Singleton
class ProfileRepository @Inject constructor() {

    private val _profiles = MutableStateFlow<Map<String, ProfileContent>>(emptyMap())
    val profiles: StateFlow<Map<String, ProfileContent>> = _profiles.asStateFlow()

    // Tracks the createdAt of the newest event processed per pubkey
    private val latestTimestamp = mutableMapOf<String, Long>()

    fun processEvent(event: Event) {
        if (event.kind != EventKind.METADATA) return
        val content = ProfileContent.parse(event.content) ?: return

        synchronized(latestTimestamp) {
            val existing = latestTimestamp[event.pubkey] ?: -1L
            if (event.createdAt <= existing) return
            latestTimestamp[event.pubkey] = event.createdAt
        }

        _profiles.update { it + (event.pubkey to content) }
    }

    fun getProfile(pubkey: String): ProfileContent? = _profiles.value[pubkey]
}
