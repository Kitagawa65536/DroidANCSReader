package com.bridgeip.ancsreader.data.repository

import com.bridgeip.ancsreader.data.model.AncsNotification
import com.bridgeip.ancsreader.data.model.AppSettings
import com.bridgeip.ancsreader.data.model.ConnectionStatus
import com.bridgeip.ancsreader.data.model.DebugLogEntry
import com.bridgeip.ancsreader.data.model.DiscoveredDevice
import com.bridgeip.ancsreader.data.model.GattServiceSummary
import com.bridgeip.ancsreader.data.model.NotificationAction
import com.bridgeip.ancsreader.data.model.NotificationPresentationCommand
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface AncsRepository {
    val bluetoothEnabled: StateFlow<Boolean>
    val isScanning: StateFlow<Boolean>
    val scanResults: StateFlow<List<DiscoveredDevice>>
    val connectionStatus: StateFlow<ConnectionStatus>
    val notifications: StateFlow<List<AncsNotification>>
    val gattServices: StateFlow<List<GattServiceSummary>>
    val debugLogs: StateFlow<List<DebugLogEntry>>
    val appSettings: StateFlow<AppSettings>
    val notificationPresentationCommands: SharedFlow<NotificationPresentationCommand>

    fun refreshBluetoothState()
    fun startScan()
    fun startInteractiveScan()
    fun stopScan()
    fun connect(address: String)
    fun disconnect()
    fun reconnectLastDevice()
    fun performAction(notificationUid: Long, action: NotificationAction)
    fun deleteNotification(notificationUid: Long)
    fun clearNotificationHistory()
    fun clearRemovedOnSourceNotifications()
    fun setForegroundServiceEnabled(enabled: Boolean)
}
