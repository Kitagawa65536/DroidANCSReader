package com.bridgeip.ancsreader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_history")
data class NotificationHistoryEntity(
    @PrimaryKey
    val notificationUid: Long,
    val categoryRawValue: Int,
    val categoryCount: Int,
    val title: String,
    val subtitle: String,
    val message: String,
    val appIdentifier: String,
    val dateText: String,
    val positiveActionLabel: String?,
    val negativeActionLabel: String?,
    val silent: Boolean,
    val important: Boolean,
    val preExisting: Boolean,
    val positiveAction: Boolean,
    val negativeAction: Boolean,
    val receivedAtMillis: Long,
    val lastUpdatedMillis: Long,
    val removedOnSource: Boolean,
)
