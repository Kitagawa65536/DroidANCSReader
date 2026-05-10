package com.bridgeip.ancsreader.bluetooth

import com.bridgeip.ancsreader.data.model.AncsEvent
import com.bridgeip.ancsreader.data.model.NotificationAttributeId
import kotlinx.coroutines.flow.SharedFlow

interface AncsEventController {
    val events: SharedFlow<AncsEvent>

    fun requestNotificationDetails(
        notificationUid: Long,
        attributes: List<NotificationAttributeId>,
    )
}
