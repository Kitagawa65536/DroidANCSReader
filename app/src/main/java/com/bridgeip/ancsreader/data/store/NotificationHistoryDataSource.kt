package com.bridgeip.ancsreader.data.store

import com.bridgeip.ancsreader.data.model.AncsNotification
import kotlinx.coroutines.flow.StateFlow

interface NotificationHistoryDataSource {
    val notifications: StateFlow<List<AncsNotification>>

    fun upsert(notification: AncsNotification)
    fun markRemoved(notificationUid: Long)
    fun delete(notificationUid: Long)
    fun clearRemovedOnSourceNotifications()
    fun clear()
}
