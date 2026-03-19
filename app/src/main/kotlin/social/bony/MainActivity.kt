package social.bony

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import social.bony.account.signer.AmberSignerBridge
import social.bony.nostr.relay.RelayAuthManager
import social.bony.notifications.DeepLinkHandler
import social.bony.notifications.EXTRA_EVENT_ID
import social.bony.nostr.Nip19
import social.bony.ui.BonyNavHost
import social.bony.ui.theme.BonyTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var amberBridge: AmberSignerBridge
    @Inject lateinit var relayAuthManager: RelayAuthManager
    @Inject lateinit var deepLinkHandler: DeepLinkHandler

    private val amberLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        amberBridge.onResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Forward pending Amber sign requests to the system intent launcher
        lifecycleScope.launch {
            amberBridge.pendingRequest.collect { request ->
                request?.let { amberLauncher.launch(it.intent) }
            }
        }

        relayAuthManager.start(lifecycleScope)

        handleIntent(intent)

        setContent {
            BonyTheme {
                BonyNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // From in-app notification tap (EXTRA_EVENT_ID set by NotificationHelper)
        intent?.getStringExtra(EXTRA_EVENT_ID)?.let { eventId ->
            deepLinkHandler.navigateToThread(eventId)
            return
        }

        // From nostr: URI (Pokey, browsers, other Nostr apps)
        val data = intent?.data ?: return
        if (data.scheme != "nostr") return
        val entity = data.schemeSpecificPart ?: return
        when {
            entity.startsWith("note1") || entity.startsWith("nevent1") ->
                Nip19.nostrUriToEventId(entity)?.let { deepLinkHandler.navigateToThread(it) }
            entity.startsWith("npub1") ->
                Nip19.npubToHex(entity)?.let { deepLinkHandler.navigateToProfile(it) }
            entity.startsWith("nprofile1") ->
                Nip19.nprofileToHex(entity)?.let { deepLinkHandler.navigateToProfile(it) }
        }
    }
}
