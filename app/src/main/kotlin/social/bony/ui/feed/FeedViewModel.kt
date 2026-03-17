package social.bony.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.bony.account.Account
import social.bony.account.AccountRepository
import social.bony.nostr.Event
import social.bony.nostr.EventKind
import social.bony.nostr.Filter
import social.bony.nostr.ProfileContent
import social.bony.nostr.pubkeys
import social.bony.nostr.relay.RelayMessage
import social.bony.nostr.relay.RelayPool
import social.bony.profile.ProfileRepository
import javax.inject.Inject

data class FeedUiState(
    val events: List<Event> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val pool: RelayPool,
    private val accountRepository: AccountRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    val profiles: StateFlow<Map<String, ProfileContent>> = profileRepository.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val activeAccount: StateFlow<Account?> = accountRepository.activeAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val accounts = accountRepository.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var collectJob: Job? = null
    private var feedSubId: String? = null
    private var followSubId: String? = null
    private var metadataSubId: String? = null

    init {
        viewModelScope.launch {
            accountRepository.activeAccount.collect { account ->
                if (account != null) loadFeed(account) else clearFeed()
            }
        }
    }

    fun switchAccount(pubkey: String) {
        viewModelScope.launch { accountRepository.setActiveAccount(pubkey) }
    }

    // ── Feed loading ──────────────────────────────────────────────────────────

    private fun loadFeed(account: Account) {
        collectJob?.cancel()
        feedSubId?.let { pool.unsubscribe(it) }
        followSubId?.let { pool.unsubscribe(it) }
        metadataSubId?.let { pool.unsubscribe(it) }

        _uiState.update { it.copy(events = emptyList(), isLoading = true, error = null) }

        val relays = account.relays.ifEmpty { DEFAULT_RELAYS }
        relays.forEach { pool.addRelay(it) }

        // Show own notes immediately while the follow list loads
        feedSubId = pool.subscribe(listOf(
            Filter(
                authors = listOf(account.pubkey),
                kinds = listOf(EventKind.TEXT_NOTE, EventKind.REPOST),
                limit = 50,
            )
        ))

        // Fetch follow list (kind-3) and own profile (kind-0) in parallel
        followSubId = pool.subscribe(listOf(
            Filter(authors = listOf(account.pubkey), kinds = listOf(EventKind.FOLLOW_LIST), limit = 1)
        ))
        metadataSubId = pool.subscribe(listOf(
            Filter(authors = listOf(account.pubkey), kinds = listOf(EventKind.METADATA), limit = 1)
        ))

        collectJob = viewModelScope.launch { collectMessages() }

        // Safety net: clear spinner after 15s regardless
        viewModelScope.launch {
            delay(15_000)
            _uiState.update { if (it.isLoading) it.copy(isLoading = false) else it }
        }
    }

    private suspend fun collectMessages() {
        pool.messages.collect { poolMessage ->
            when (val msg = poolMessage.message) {
                is RelayMessage.EventMessage -> {
                    if (msg.event.verify()) handleEvent(msg.event)
                }
                is RelayMessage.EndOfStoredEvents -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
                else -> Unit
            }
        }
    }

    private fun handleEvent(event: Event) {
        when (event.kind) {
            EventKind.FOLLOW_LIST -> expandFeedToFollows(event)
            EventKind.METADATA    -> profileRepository.processEvent(event)
            EventKind.TEXT_NOTE,
            EventKind.REPOST      -> addToFeed(event)
        }
    }

    /**
     * Called when the follow list (kind-3) arrives.
     * Replaces the narrow own-notes subscription with a full home feed
     * and kicks off a metadata fetch for all followed pubkeys.
     */
    private fun expandFeedToFollows(event: Event) {
        val followed = event.parsedTags.pubkeys
        if (followed.isEmpty()) return

        // We have what we need — close the follow list subscription
        followSubId?.let { pool.unsubscribe(it) }
        followSubId = null

        val selfPubkey = activeAccount.value?.pubkey ?: return
        val allPubkeys = (followed + selfPubkey).distinct()

        // Expand the feed subscription to all followed pubkeys
        feedSubId?.let { pool.unsubscribe(it) }
        feedSubId = pool.subscribe(listOf(
            Filter(
                authors = allPubkeys,
                kinds = listOf(EventKind.TEXT_NOTE, EventKind.REPOST),
                limit = 200,
            )
        ))

        // Fetch profiles for everyone in one shot
        metadataSubId?.let { pool.unsubscribe(it) }
        metadataSubId = pool.subscribe(listOf(
            Filter(authors = allPubkeys, kinds = listOf(EventKind.METADATA))
        ))
    }

    private fun addToFeed(event: Event) {
        _uiState.update { state ->
            val updated = (state.events + event)
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt }
            state.copy(events = updated)
        }
    }

    private fun clearFeed() {
        collectJob?.cancel()
        feedSubId?.let { pool.unsubscribe(it) }
        followSubId?.let { pool.unsubscribe(it) }
        metadataSubId?.let { pool.unsubscribe(it) }
        _uiState.update { FeedUiState(isLoading = false) }
    }

    companion object {
        val DEFAULT_RELAYS = listOf(
            "wss://relay.damus.io",
            "wss://relay.nostr.band",
            "wss://nos.lol",
        )
    }
}
