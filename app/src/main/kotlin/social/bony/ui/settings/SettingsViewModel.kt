package social.bony.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.bony.account.Account
import social.bony.account.AccountRepository
import social.bony.logging.LogRepository
import social.bony.settings.AppSettings
import social.bony.settings.OrbotHelper
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    val logRepository: LogRepository,
    private val appSettings: AppSettings,
    @ApplicationContext context: Context,
) : ViewModel() {

    val accounts: StateFlow<List<Account>> = accountRepository.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeAccount: StateFlow<Account?> = accountRepository.activeAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val torEnabled: StateFlow<Boolean> = appSettings.torEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** True if Orbot is installed — checked once at construction, no async probe needed. */
    val orbotInstalled: Boolean = OrbotHelper.isInstalled(context)

    fun setTorEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettings.setTorEnabled(enabled) }
    }

    fun removeAccount(pubkey: String) {
        viewModelScope.launch { accountRepository.removeAccount(pubkey) }
    }
}
