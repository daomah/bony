package social.bony.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun OnboardingScreen(
    onAccountAdded: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Navigate away as soon as an account is successfully added
    LaunchedEffect(uiState.success) {
        if (uiState.success) onAccountAdded()
    }

    // Surface errors via snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "🦴",
                style = MaterialTheme.typography.displayLarge,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "bony",
                style = MaterialTheme.typography.headlineLarge,
            )

            Text(
                text = "A bare-bones Nostr client.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(48.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            } else {
                Button(
                    onClick = { viewModel.addAccountWithAmber(context.packageName) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sign in with Amber")
                }

                Spacer(Modifier.height(12.dp))

                // nsecBunker: coming soon — requires relay URL + bunker pubkey input
                OutlinedButton(
                    onClick = { /* TODO: nsecBunker onboarding */ },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                ) {
                    Text("Sign in with nsecBunker (coming soon)")
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "No signer app? Generate a local key.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { viewModel.addLocalKeyAccount() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Create local key (advanced)")
                }
            }
        }
    }
}
