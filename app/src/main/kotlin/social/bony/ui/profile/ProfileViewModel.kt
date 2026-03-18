package social.bony.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.bony.nostr.Event
import social.bony.nostr.EventKind
import social.bony.nostr.Filter
import social.bony.nostr.ProfileContent
import social.bony.nostr.relay.RelayMessage
import social.bony.nostr.relay.RelayPool
import social.bony.profile.ProfileRepository
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pool: RelayPool,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    val pubkey: String = checkNotNull(savedStateHandle["pubkey"])

    val profile: StateFlow<ProfileContent?> = profileRepository.profiles
        .map { it[pubkey] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), profileRepository.getProfile(pubkey))

    private val _notes = MutableStateFlow<List<Event>>(emptyList())
    val notes: StateFlow<List<Event>> = _notes.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var notesSubId: String? = null
    private var metadataSubId: String? = null

    init {
        notesSubId = pool.subscribe(listOf(
            Filter(authors = listOf(pubkey), kinds = listOf(EventKind.TEXT_NOTE), limit = 50)
        ))
        metadataSubId = pool.subscribe(listOf(
            Filter(authors = listOf(pubkey), kinds = listOf(EventKind.METADATA), limit = 1)
        ))

        viewModelScope.launch(Dispatchers.Default) {
            pool.messages.collect { (_, message) ->
                when (message) {
                    is RelayMessage.EventMessage -> {
                        val event = message.event
                        if (!event.verify()) return@collect
                        when (event.kind) {
                            EventKind.METADATA -> profileRepository.processEvent(event)
                            EventKind.TEXT_NOTE -> if (event.pubkey == pubkey) {
                                _notes.update { existing ->
                                    (existing + event)
                                        .distinctBy { it.id }
                                        .sortedByDescending { it.createdAt }
                                }
                            }
                        }
                    }
                    is RelayMessage.EndOfStoredEvents -> _isLoading.update { false }
                    else -> Unit
                }
            }
        }

        // Safety net: clear spinner after 10s
        viewModelScope.launch {
            delay(10_000)
            _isLoading.update { false }
        }
    }

    override fun onCleared() {
        notesSubId?.let { pool.unsubscribe(it) }
        metadataSubId?.let { pool.unsubscribe(it) }
    }
}
