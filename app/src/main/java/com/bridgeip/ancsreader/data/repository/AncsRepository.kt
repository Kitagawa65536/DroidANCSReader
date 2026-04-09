package com.bridgeip.ancsreader.data.repository

import com.bridgeip.ancsreader.data.model.AncsNotification
import com.bridgeip.ancsreader.data.model.ConnectionStatus
import com.bridgeip.ancsreader.data.model.DebugLogEntry
import com.bridgeip.ancsreader.data.model.DiscoveredDevice
import com.bridgeip.ancsreader.data.model.GattServiceSummary
import com.bridgeip.ancsreader.data.model.NotificationAction
import kotlinx.coroutines.flow.StateFlow

interface AncsRepository {
    val bluetoothEnabled: StateFlow<Boolean>
    val isScanning: StateFlow<Boolean>
    val scanResults: StateFlow<List<DiscoveredDevice>>
    val connectionStatus: StateFlow<ConnectionStatus>
    val notifications: StateFlow<List<AncsNotification>>
    val gattServices: StateFlow<List<GattServiceSummary>>
    val debugLogs: StateFlow<List<DebugLogEntry>>

    fun refreshBluetoothState()
    fun startScan()
    fun stopScan()
    fun connect(address: String)
    fun disconnect()
    fun performAction(notificationUid: Long, action: NotificationAction)
}

