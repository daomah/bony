package social.bony.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.bony.account.Account
import social.bony.account.AccountRepository
import social.bony.nostr.relay.RelayPool
import social.bony.nostr.relay.RelayStatus
import javax.inject.Inject

@HiltViewModel
class RelayManagementViewModel @Inject constructor(
    private val pool: RelayPool,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    val relayStatuses: StateFlow<Map<String, RelayStatus>> = pool.relayStatuses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val activeAccount: StateFlow<Account?> = accountRepository.activeAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addRelay(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        pool.addRelay(trimmed)
        persistRelays { relays -> (relays + trimmed).distinct() }
    }

    fun removeRelay(url: String) {
        pool.removeRelay(url)
        persistRelays { relays -> relays - url }
    }

    private fun persistRelays(transform: (List<String>) -> List<String>) {
        viewModelScope.launch {
            val account = activeAccount.value ?: return@launch
            accountRepository.updateAccount(account.copy(relays = transform(account.relays)))
        }
    }
}
