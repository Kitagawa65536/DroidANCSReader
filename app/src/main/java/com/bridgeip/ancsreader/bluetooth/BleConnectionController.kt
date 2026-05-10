package com.bridgeip.ancsreader.bluetooth

import com.bridgeip.ancsreader.data.model.ConnectionStatus
import com.bridgeip.ancsreader.data.model.GattServiceSummary
import com.bridgeip.ancsreader.data.model.NotificationAction
import kotlinx.coroutines.flow.StateFlow

interface BleConnectionController {
    val connectionStatus: StateFlow<ConnectionStatus>
    val services: StateFlow<List<GattServiceSummary>>

    fun connect(address: String)
    fun disconnect()
    fun requestNotificationAction(notificationUid: Long, action: NotificationAction)
}
