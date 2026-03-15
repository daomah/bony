package social.bony.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import social.bony.account.Account

@Composable
fun AccountSwitcher(
    activeAccount: Account?,
    accounts: List<Account>,
    onSwitch: (pubkey: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { if (accounts.size > 1) expanded = true },
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Account",
                tint = MaterialTheme.colorScheme.onSurface,
            )
            if (activeAccount != null) {
                Text(
                    text = activeAccount.displayName
                        ?: activeAccount.pubkey.take(8) + "…",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = if (activeAccount.displayName == null) FontFamily.Monospace else null,
                    modifier = Modifier,
                )
            }
            if (accounts.size > 1) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Switch account",
                )
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = account.displayName ?: account.pubkey.take(8) + "…",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = if (account.displayName == null) FontFamily.Monospace else null,
                        )
                    },
                    onClick = {
                        onSwitch(account.pubkey)
                        expanded = false
                    },
                )
            }
        }
    }
}
