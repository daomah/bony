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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import social.bony.account.AccountRepository
import social.bony.account.signer.NostrSignerFactory
import social.bony.nostr.Event
import social.bony.nostr.EventKind
import social.bony.nostr.Filter
import social.bony.nostr.ProfileContent
import social.bony.nostr.UnsignedEvent
import social.bony.nostr.relay.RelayMessage
import social.bony.nostr.relay.RelayPool
import social.bony.profile.ProfileRepository
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pool: RelayPool,
    private val profileRepository: ProfileRepository,
    private val accountRepository: AccountRepository,
    private val signerFactory: NostrSignerFactory,
) : ViewModel() {

    val pubkey: String = checkNotNull(savedStateHandle["pubkey"])

    val profile: StateFlow<ProfileContent?> = profileRepository.profiles
        .map { it[pubkey] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), profileRepository.getProfile(pubkey))

    private val _notes = MutableStateFlow<List<Event>>(emptyList())
    val notes: StateFlow<List<Event>> = _notes.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val isActiveUserProfile: StateFlow<Boolean> = accountRepository.activeAccount
        .map { it?.pubkey == pubkey }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isFollowing: StateFlow<Boolean> = accountRepository.activeAccount
        .map { it?.follows?.contains(pubkey) == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _isFollowLoading = MutableStateFlow(false)
    val isFollowLoading: StateFlow<Boolean> = _isFollowLoading.asStateFlow()

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

        viewModelScope.launch {
            delay(10_000)
            _isLoading.update { false }
        }
    }

    fun follow() = toggleFollow(add = true)
    fun unfollow() = toggleFollow(add = false)

    private fun toggleFollow(add: Boolean) {
        viewModelScope.launch {
            _isFollowLoading.update { true }
            val account = accountRepository.activeAccount.first()
            val signer = signerFactory.forActiveAccount()
            if (account == null || signer == null) {
                _isFollowLoading.update { false }
                return@launch
            }

            val newFollows = if (add)
                (account.follows + pubkey).distinct()
            else
                account.follows.filter { it != pubkey }

            val unsigned = UnsignedEvent(
                pubkey = signer.pubkey,
                kind = EventKind.FOLLOW_LIST,
                content = "",
                tags = newFollows.map { p ->
                    buildJsonArray { add(JsonPrimitive("p")); add(JsonPrimitive(p)) }
                },
            )

            signer.signEvent(unsigned)
                .onSuccess { event ->
                    pool.publish(event)
                    accountRepository.updateAccount(account.copy(follows = newFollows))
                }
                .onFailure { e -> Timber.w(e, "toggleFollow failed") }

            _isFollowLoading.update { false }
        }
    }

    override fun onCleared() {
        notesSubId?.let { pool.unsubscribe(it) }
        metadataSubId?.let { pool.unsubscribe(it) }
    }
}
