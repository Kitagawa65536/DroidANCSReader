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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bridgeip.ancsreader.R
import com.bridgeip.ancsreader.data.model.AncsNotification
import com.bridgeip.ancsreader.data.model.NotificationCategory
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
    onClearRemovedOnSourceNotifications: () -> Unit,
    onRequestMissingNotificationDetails: () -> Unit,
    hasRemovedOnSourceNotifications: Boolean,
    modifier: Modifier = Modifier,
) {
    if (notifications.isEmpty()) {
        Column(modifier = modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.notifications_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.notifications_empty_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val hasMissingDetailsNotifications = notifications.any { notification ->
        notification.isMissingDetails && !notification.removedOnSource
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.saved_notification_log_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onRequestMissingNotificationDetails,
                        enabled = hasMissingDetailsNotifications,
                    ) {
                        Text(stringResource(R.string.request_missing_details))
                    }
                    OutlinedButton(
                        onClick = onClearRemovedOnSourceNotifications,
                        enabled = hasRemovedOnSourceNotifications,
                    ) {
                        Text(stringResource(R.string.remove_deleted_on_iphone))
                    }
                    OutlinedButton(onClick = onClearNotifications) {
                        Text(stringResource(R.string.clear_all))
                    }
                }
            }
        }

        items(notifications, key = { it.notificationUid }) { notification ->
            val categoryLabel = stringResource(notification.category.labelResId)
            SectionCard(
                title = notification.displayTitleLocalized(),
                subtitle = "$categoryLabel • ${formatTimestamp(notification.receivedAtMillis)}",
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(onClick = {}, label = { Text(categoryLabel) })
                    if (notification.flags.important) {
                        AssistChip(onClick = {}, label = { Text(stringResource(R.string.notification_flag_important)) })
                    }
                    if (notification.flags.preExisting) {
                        AssistChip(onClick = {}, label = { Text(stringResource(R.string.notification_flag_pre_existing)) })
                    }
                    if (notification.removedOnSource) {
                        AssistChip(onClick = {}, label = { Text(stringResource(R.string.notification_flag_removed_on_iphone)) })
                    }
                }
                Text(
                    text = notification.displayMessageLocalized(),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (notification.dateText.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.ancs_date, notification.dateText),
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
                        Text(stringResource(R.string.delete))
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

private val NotificationCategory.labelResId: Int
    get() = when (this) {
        NotificationCategory.Other -> R.string.category_other
        NotificationCategory.IncomingCall -> R.string.category_incoming_call
        NotificationCategory.MissedCall -> R.string.category_missed_call
        NotificationCategory.Voicemail -> R.string.category_voicemail
        NotificationCategory.Social -> R.string.category_social
        NotificationCategory.Schedule -> R.string.category_schedule
        NotificationCategory.Email -> R.string.category_email
        NotificationCategory.News -> R.string.category_news
        NotificationCategory.HealthAndFitness -> R.string.category_health
        NotificationCategory.BusinessAndFinance -> R.string.category_business
        NotificationCategory.Location -> R.string.category_location
        NotificationCategory.Entertainment -> R.string.category_entertainment
    }

@Composable
private fun AncsNotification.displayTitleLocalized(): String =
    title.ifBlank {
        appIdentifier.ifBlank {
            stringResource(R.string.notification_fallback_title, notificationUid)
        }
    }

@Composable
private fun AncsNotification.displayMessageLocalized(): String = buildString {
    if (subtitle.isNotBlank()) {
        append(subtitle)
    }
    if (message.isNotBlank()) {
        if (isNotBlank()) {
            append("\n")
        }
        append(message)
    }
}.ifBlank { stringResource(R.string.notification_waiting_for_details) }
