package social.bony.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.bony.nostr.EventKind
import social.bony.nostr.Filter
import social.bony.nostr.Nip19
import social.bony.nostr.ProfileContent
import social.bony.nostr.relay.RelayMessage
import social.bony.nostr.relay.RelayPool
import social.bony.profile.ProfileRepository
import javax.inject.Inject

data class ProfileMatch(val pubkey: String, val profile: ProfileContent?)

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data class NpubInput(val pubkey: String, val profile: ProfileContent?) : SearchUiState
    data class HashtagInput(val tag: String) : SearchUiState
    data object Searching : SearchUiState
    data class Results(val profiles: List<ProfileMatch>) : SearchUiState
}

private const val SEARCH_RELAY = "wss://relay.nostr.band"

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val pool: RelayPool,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _query.debounce(300).collectLatest { q -> processQuery(q.trim()) }
        }
    }

    fun onQueryChange(q: String) = _query.update { q }

    private suspend fun processQuery(q: String) {
        when {
            q.isEmpty() -> _uiState.update { SearchUiState.Idle }

            q.startsWith("npub1") && q.length > 20 -> {
                val hex = Nip19.npubToHex(q)
                if (hex != null) {
                    _uiState.update { SearchUiState.NpubInput(hex, profileRepository.getProfile(hex)) }
                } else {
                    _uiState.update { SearchUiState.Idle }
                }
            }

            q.startsWith("#") && q.length > 1 -> {
                val tag = q.removePrefix("#").lowercase().trim()
                if (tag.isNotEmpty()) _uiState.update { SearchUiState.HashtagInput(tag) }
            }

            q.length >= 2 -> searchProfiles(q)

            else -> _uiState.update { SearchUiState.Idle }
        }
    }

    private suspend fun searchProfiles(query: String) {
        // Seed with local cache matches immediately
        val local = profileRepository.profiles.value
            .filter { (_, p) ->
                p.bestName?.contains(query, ignoreCase = true) == true ||
                p.nip05?.contains(query, ignoreCase = true) == true
            }
            .map { (pubkey, p) -> ProfileMatch(pubkey, p) }
            .sortedBy { it.profile?.bestName }
            .take(20)

        _uiState.update { SearchUiState.Results(local) }

        // NIP-50 query to search relay
        pool.addRelay(SEARCH_RELAY)
        val subId = pool.subscribe(listOf(
            Filter(kinds = listOf(EventKind.METADATA), search = query, limit = 20)
        ))

        try {
            pool.messages.collect { (_, msg) ->
                when (msg) {
                    is RelayMessage.EventMessage -> {
                        if (msg.subscriptionId == subId && msg.event.verify()) {
                            profileRepository.processEvent(msg.event)
                            val pubkey = msg.event.pubkey
                            val current = (_uiState.value as? SearchUiState.Results)?.profiles ?: local
                            if (current.none { it.pubkey == pubkey }) {
                                val updated = (current + ProfileMatch(pubkey, profileRepository.getProfile(pubkey)))
                                    .sortedBy { it.profile?.bestName }
                                _uiState.update { SearchUiState.Results(updated) }
                            }
                        }
                    }
                    is RelayMessage.EndOfStoredEvents -> {
                        if (msg.subscriptionId == subId) {
                            pool.unsubscribe(subId)
                            currentCoroutineContext().cancel()
                        }
                    }
                    else -> Unit
                }
            }
        } finally {
            pool.unsubscribe(subId)
        }
    }
}
