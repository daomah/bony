package social.bony.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.bony.account.Account
import social.bony.account.AccountRepository
import social.bony.nostr.Event
import social.bony.nostr.EventKind
import social.bony.nostr.Filter
import social.bony.nostr.relay.PoolMessage
import social.bony.nostr.relay.RelayMessage
import social.bony.nostr.relay.RelayPool
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    val activeAccount: StateFlow<Account?> = accountRepository.activeAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val accounts = accountRepository.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var currentSubId: String? = null

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

    private fun loadFeed(account: Account) {
        // Unsubscribe from previous feed
        currentSubId?.let { pool.unsubscribe(it) }

        _uiState.update { it.copy(events = emptyList(), isLoading = true, error = null) }

        val relays = account.relays.ifEmpty { DEFAULT_RELAYS }
        relays.forEach { pool.addRelay(it) }

        // Subscribe: own posts for now; expands to follow list once NIP-02 is fetched
        val filter = Filter(
            authors = listOf(account.pubkey),
            kinds = listOf(EventKind.TEXT_NOTE, EventKind.REPOST),
            limit = 50,
        )
        currentSubId = pool.subscribe(listOf(filter))

        viewModelScope.launch { collectMessages() }

        // Safety net: clear spinner after 15s even if EOSE never arrives
        viewModelScope.launch {
            kotlinx.coroutines.delay(15_000)
            _uiState.update { if (it.isLoading) it.copy(isLoading = false) else it }
        }
    }

    private suspend fun collectMessages() {
        pool.messages.collect { poolMessage ->
            when (val msg = poolMessage.message) {
                is RelayMessage.EventMessage -> {
                    if (msg.event.verify()) {
                        _uiState.update { state ->
                            val updated = (state.events + msg.event)
                                .distinctBy { it.id }
                                .sortedByDescending { it.createdAt }
                            state.copy(events = updated)
                        }
                    }
                }
                is RelayMessage.EndOfStoredEvents -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
                is RelayMessage.Notice -> {
                    // Relay notices are informational — surface if needed
                }
                else -> Unit
            }
        }
    }

    private fun clearFeed() {
        currentSubId?.let { pool.unsubscribe(it) }
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
