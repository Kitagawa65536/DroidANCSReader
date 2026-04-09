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
    onDeleteNotification: (Long) -> Unit,
    onClearNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (notifications.isEmpty()) {
        Column(modifier = modifier.padding(24.dp)) {
            Text(
                text = "No saved ANCS notifications yet.",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Received iPhone notifications will be stored here so you can review them later.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Saved notification log",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onClearNotifications) {
                    Text("Clear all")
                }
            }
        }

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
                    if (notification.removedOnSource) {
                        AssistChip(onClick = {}, label = { Text("Removed on iPhone") })
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
                    OutlinedButton(
                        onClick = { onDeleteNotification(notification.notificationUid) },
                    ) {
                        Text("Delete")
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
