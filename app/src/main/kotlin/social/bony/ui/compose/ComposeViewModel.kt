package social.bony.ui.compose

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import social.bony.account.signer.NostrSignerFactory
import social.bony.db.EventRepository
import social.bony.nostr.Event
import social.bony.nostr.EventKind
import social.bony.nostr.Nip19
import social.bony.nostr.UnsignedEvent
import social.bony.nostr.relay.RelayPool
import social.bony.nostr.replyToPubkeys
import social.bony.nostr.rootEventId
import javax.inject.Inject

data class ComposeUiState(
    val isPublishing: Boolean = false,
    val error: String? = null,
    val replyToEvent: Event? = null,
    val quoteToEvent: Event? = null,
    val initialText: String = "",
)

@HiltViewModel
class ComposeViewModel @Inject constructor(
    private val pool: RelayPool,
    private val signerFactory: NostrSignerFactory,
    private val eventRepository: EventRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val replyToId: String? = savedStateHandle["replyToId"]
    private val quoteToId: String? = savedStateHandle["quoteToId"]

    private val _uiState = MutableStateFlow(ComposeUiState())
    val uiState: StateFlow<ComposeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            if (!replyToId.isNullOrEmpty()) {
                val event = eventRepository.getById(replyToId)
                _uiState.update { it.copy(replyToEvent = event) }
            }
            if (!quoteToId.isNullOrEmpty()) {
                val event = eventRepository.getById(quoteToId)
                if (event != null) {
                    val ref = "nostr:${Nip19.hexToNote(event.id)}"
                    _uiState.update { it.copy(quoteToEvent = event, initialText = ref) }
                }
            }
        }
    }

    fun publish(content: String, onSuccess: () -> Unit) {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isPublishing = true, error = null) }

            val signer = signerFactory.forActiveAccount()
            if (signer == null) {
                _uiState.update { it.copy(isPublishing = false, error = "No active account") }
                return@launch
            }

            val replyTo = _uiState.value.replyToEvent
            val quoteTo = _uiState.value.quoteToEvent

            val tags = buildList {
                if (replyTo != null) {
                    // NIP-10: root + reply e-tags with markers
                    val rootId = replyTo.parsedTags.rootEventId
                    if (rootId != null) {
                        add(buildJsonArray {
                            add(JsonPrimitive("e")); add(JsonPrimitive(rootId))
                            add(JsonPrimitive("")); add(JsonPrimitive("root"))
                        })
                        add(buildJsonArray {
                            add(JsonPrimitive("e")); add(JsonPrimitive(replyTo.id))
                            add(JsonPrimitive("")); add(JsonPrimitive("reply"))
                        })
                    } else {
                        add(buildJsonArray {
                            add(JsonPrimitive("e")); add(JsonPrimitive(replyTo.id))
                            add(JsonPrimitive("")); add(JsonPrimitive("reply"))
                        })
                    }
                    // p tags: author of the note + existing p-tagged participants
                    val mentions = (listOf(replyTo.pubkey) + replyTo.parsedTags.replyToPubkeys).distinct()
                    mentions.forEach { pubkey ->
                        add(buildJsonArray { add(JsonPrimitive("p")); add(JsonPrimitive(pubkey)) })
                    }
                }
                if (quoteTo != null) {
                    add(buildJsonArray { add(JsonPrimitive("q")); add(JsonPrimitive(quoteTo.id)) })
                    add(buildJsonArray { add(JsonPrimitive("p")); add(JsonPrimitive(quoteTo.pubkey)) })
                }
            }

            // For quote-reply, ensure nostr:note1… ref is in the content
            val noteRef = quoteTo?.let { "\n\nnostr:${Nip19.hexToNote(it.id)}" }
            val finalContent = if (noteRef != null && !trimmed.contains(noteRef.trim()))
                "$trimmed$noteRef"
            else
                trimmed

            val unsigned = UnsignedEvent(
                pubkey = signer.pubkey,
                kind = EventKind.TEXT_NOTE,
                content = finalContent,
                tags = tags,
            )

            signer.signEvent(unsigned)
                .onSuccess { event ->
                    pool.publish(event)
                    eventRepository.save(event, signer.pubkey)
                    _uiState.update { it.copy(isPublishing = false) }
                    onSuccess()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isPublishing = false, error = e.message) }
                }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
