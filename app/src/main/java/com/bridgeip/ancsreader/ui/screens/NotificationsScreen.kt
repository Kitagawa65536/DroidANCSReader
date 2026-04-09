package com.bridgeip.ancsreader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bridgeip.ancsreader.data.model.AncsNotification
import com.bridgeip.ancsreader.ui.components.SectionCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationsScreen(
    notifications: List<AncsNotification>,
    onPositiveAction: (Long) -> Unit,
    onNegativeAction: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (notifications.isEmpty()) {
        Column(modifier = modifier.padding(24.dp)) {
            Text(
                text = "No ANCS notifications yet.",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "After ANCS is ready, incoming iPhone notifications will appear here with add/update/remove reflected in real time.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(notifications, key = { it.notificationUid }) { notification ->
            SectionCard(
                title = notification.displayTitle,
                subtitle = "${notification.category.label} • ${formatTimestamp(notification.receivedAtMillis)}",
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(onClick = {}, label = { Text(notification.category.label) })
                    if (notification.flags.important) {
                        AssistChip(onClick = {}, label = { Text("Important") })
                    }
                    if (notification.flags.preExisting) {
                        AssistChip(onClick = {}, label = { Text("Pre-existing") })
                    }
                }
                Text(
                    text = notification.displayMessage,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (notification.dateText.isNotBlank()) {
                    Text(
                        text = "ANCS date: ${notification.dateText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (notification.supportsPositiveAction) {
                        OutlinedButton(
                            onClick = { onPositiveAction(notification.notificationUid) },
                        ) {
                            Text(notification.positiveActionLabel ?: "Positive")
                        }
                    }
                    if (notification.supportsNegativeAction) {
                        OutlinedButton(
                            onClick = { onNegativeAction(notification.notificationUid) },
                        ) {
                            Text(notification.negativeActionLabel ?: "Negative")
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestampMillis: Long): String = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(timestampMillis))
