package social.bony.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.bony.account.Account
import social.bony.account.AccountRepository
import social.bony.account.SignerType
import social.bony.account.signer.AmberSignerBridge
import social.bony.account.signer.LocalKeySigner
import social.bony.nostr.Nip19
import javax.inject.Inject

data class OnboardingUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val amberBridge: AmberSignerBridge,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /** NIP-55: ask Amber for the user's public key. */
    fun addAccountWithAmber(callerPackage: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:")).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("type", "get_public_key")
                putExtra("package", callerPackage)
            }
            amberBridge.request(intent)
                .onSuccess { raw ->
                    val hexPubkey = Nip19.normalisePubkey(raw)
                    if (hexPubkey == null) {
                        _uiState.update { it.copy(isLoading = false, error = "Amber returned an unrecognised pubkey format: $raw") }
                        return@onSuccess
                    }
                    val account = Account(pubkey = hexPubkey, signerType = SignerType.AMBER)
                    accountRepository.addAccount(account)
                    _uiState.update { it.copy(isLoading = false, success = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    /** Last resort: generate a local secp256k1 keypair stored in Android Keystore. */
    fun addLocalKeyAccount() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val (signer, encryptedPrivkey) = LocalKeySigner.generate()
                val account = Account(
                    pubkey = signer.pubkey,
                    signerType = SignerType.LOCAL_KEY,
                )
                accountRepository.addAccount(account, encryptedPrivkey)
                _uiState.update { it.copy(isLoading = false, success = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
