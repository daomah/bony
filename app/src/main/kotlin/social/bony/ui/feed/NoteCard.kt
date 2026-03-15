package social.bony.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import social.bony.nostr.Event
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NoteCard(event: Event, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = event.pubkey.abbreviate(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(
                text = remember(event.createdAt) { event.createdAt.formatRelative() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = event.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            overflow = TextOverflow.Ellipsis,
            maxLines = 12,
        )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

// ── Formatting helpers ────────────────────────────────────────────────────────

private fun String.abbreviate(): String =
    if (length > 16) "${take(8)}…${takeLast(8)}" else this

private fun Long.formatRelative(): String {
    val now = System.currentTimeMillis() / 1000
    val delta = now - this
    return when {
        delta < 60 -> "now"
        delta < 3600 -> "${delta / 60}m"
        delta < 86400 -> "${delta / 3600}h"
        delta < 604800 -> "${delta / 86400}d"
        else -> Instant.ofEpochSecond(this)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d"))
    }
}
