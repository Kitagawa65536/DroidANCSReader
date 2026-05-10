package com.bridgeip.ancsreader.data.repository

import com.bridgeip.ancsreader.bluetooth.AncsEventController
import com.bridgeip.ancsreader.bluetooth.BleConnectionController
import com.bridgeip.ancsreader.bluetooth.BleScanner
import com.bridgeip.ancsreader.bluetooth.BluetoothStateMonitor
import com.bridgeip.ancsreader.bluetooth.ScanProfile
import com.bridgeip.ancsreader.data.model.AncsEvent
import com.bridgeip.ancsreader.data.model.AncsNotification
import com.bridgeip.ancsreader.data.model.AppSettings
import com.bridgeip.ancsreader.data.model.ConnectionStage
import com.bridgeip.ancsreader.data.model.ConnectionStatus
import com.bridgeip.ancsreader.data.model.DebugLogEntry
import com.bridgeip.ancsreader.data.model.DiscoveredDevice
import com.bridgeip.ancsreader.data.model.GattServiceSummary
import com.bridgeip.ancsreader.data.model.NotificationAction
import com.bridgeip.ancsreader.data.model.NotificationAttributeId
import com.bridgeip.ancsreader.data.model.NotificationEventType
import com.bridgeip.ancsreader.data.model.NotificationPresentationCommand
import com.bridgeip.ancsreader.data.store.AppPreferencesDataSource
import com.bridgeip.ancsreader.data.store.NotificationHistoryDataSource
import com.bridgeip.ancsreader.util.DebugLogSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

class DefaultAncsRepository(
    scope: CoroutineScope,
    private val scanner: BleScanner,
    private val connectionManager: BleConnectionController,
    private val bluetoothStateMonitor: BluetoothStateMonitor,
    ancsManager: AncsEventController,
    logStore: DebugLogSource,
    private val appPreferencesStore: AppPreferencesDataSource,
    private val notificationHistoryStore: NotificationHistoryDataSource,
) : AncsRepository {
    override val bluetoothEnabled: StateFlow<Boolean> = bluetoothStateMonitor.isBluetoothEnabled
    override val isScanning: StateFlow<Boolean> = scanner.isScanning
    override val scanResults: StateFlow<List<DiscoveredDevice>> = scanner.scanResults
    override val connectionStatus: StateFlow<ConnectionStatus> = connectionManager.connectionStatus
    override val gattServices: StateFlow<List<GattServiceSummary>> = connectionManager.services
    override val debugLogs: StateFlow<List<DebugLogEntry>> = logStore.entries
    override val appSettings: StateFlow<AppSettings> = appPreferencesStore.settings
    override val notifications: StateFlow<List<AncsNotification>> = notificationHistoryStore.notifications

    private val activeNotificationsByUid = linkedMapOf<Long, AncsNotification>()
    private val locallyDeletedNotificationUids = mutableSetOf<Long>()
    private val _notificationPresentationCommands = MutableSharedFlow<NotificationPresentationCommand>(extraBufferCapacity = 32)
    override val notificationPresentationCommands: SharedFlow<NotificationPresentationCommand> =
        _notificationPresentationCommands

    init {
        ancsManager.events.onEach { event ->
            when (event) {
                is AncsEvent.NotificationChanged -> handleNotificationEvent(ancsManager, event)
                is AncsEvent.NotificationAttributesReceived -> handleAttributes(event)
                AncsEvent.SessionEnded -> {
                    activeNotificationsByUid.clear()
                    locallyDeletedNotificationUids.clear()
                }
                AncsEvent.SessionReady -> Unit
                is AncsEvent.Error -> Unit
            }
        }.launchIn(scope)

        connectionManager.connectionStatus.onEach { status ->
            if (status.stage == ConnectionStage.Disconnected || status.stage == ConnectionStage.Error) {
                activeNotificationsByUid.clear()
                locallyDeletedNotificationUids.clear()
            }
        }.launchIn(scope)
    }

    override fun refreshBluetoothState() {
        bluetoothStateMonitor.refresh()
    }

    override fun startScan() {
        scanner.startScan(ScanProfile.LowPower)
    }

    override fun startInteractiveScan() {
        scanner.startScan(ScanProfile.Interactive)
    }

    override fun stopScan() {
        scanner.stopScan()
    }

    override fun connect(address: String) {
        scanner.stopScan()
        val name = scanResults.value.firstOrNull { it.address == address }?.name
        appPreferencesStore.setLastConnectedDevice(address, name)
        connectionManager.connect(address)
    }

    override fun disconnect() {
        connectionManager.disconnect()
    }

    override fun reconnectLastDevice() {
        val address = appSettings.value.lastConnectedDeviceAddress ?: return
        connectionManager.connect(address)
    }

    override fun performAction(notificationUid: Long, action: NotificationAction) {
        connectionManager.requestNotificationAction(notificationUid, action)
    }

    override fun deleteNotification(notificationUid: Long) {
        locallyDeletedNotificationUids += notificationUid
        activeNotificationsByUid.remove(notificationUid)
        notificationHistoryStore.delete(notificationUid)
        _notificationPresentationCommands.tryEmit(NotificationPresentationCommand.Cancel(notificationUid))
    }

    override fun clearNotificationHistory() {
        locallyDeletedNotificationUids += notificationHistoryStore.notifications.value.map { it.notificationUid }
        activeNotificationsByUid.clear()
        notificationHistoryStore.clear()
        _notificationPresentationCommands.tryEmit(NotificationPresentationCommand.CancelAll)
    }

    override fun clearRemovedOnSourceNotifications() {
        notificationHistoryStore.clearRemovedOnSourceNotifications()
    }

    override fun setForegroundServiceEnabled(enabled: Boolean) {
        appPreferencesStore.setForegroundServiceEnabled(enabled)
    }

    private fun handleNotificationEvent(
        ancsManager: AncsEventController,
        event: AncsEvent.NotificationChanged,
    ) {
        val source = event.event
        if (source.eventType == NotificationEventType.Removed) {
            locallyDeletedNotificationUids.remove(source.notificationUid)
            activeNotificationsByUid.remove(source.notificationUid)
            notificationHistoryStore.markRemoved(source.notificationUid)
            _notificationPresentationCommands.tryEmit(NotificationPresentationCommand.Cancel(source.notificationUid))
            return
        }

        if (source.notificationUid in locallyDeletedNotificationUids) {
            return
        }

        val now = System.currentTimeMillis()
        val existing = activeNotificationsByUid[source.notificationUid]
        val notification = AncsNotification(
            notificationUid = source.notificationUid,
            category = source.category,
            categoryCount = source.categoryCount,
            flags = source.flags,
            receivedAtMillis = existing?.receivedAtMillis ?: now,
            lastUpdatedMillis = now,
            title = existing?.title.orEmpty(),
            subtitle = existing?.subtitle.orEmpty(),
            message = existing?.message.orEmpty(),
            appIdentifier = existing?.appIdentifier.orEmpty(),
            dateText = existing?.dateText.orEmpty(),
            positiveActionLabel = existing?.positiveActionLabel,
            negativeActionLabel = existing?.negativeActionLabel,
            removedOnSource = false,
        )
        activeNotificationsByUid[source.notificationUid] = notification
        notificationHistoryStore.upsert(notification)
        ancsManager.requestNotificationDetails(
            notificationUid = source.notificationUid,
            attributes = listOf(
                NotificationAttributeId.AppIdentifier,
                NotificationAttributeId.Title,
                NotificationAttributeId.Subtitle,
                NotificationAttributeId.Message,
                NotificationAttributeId.Date,
                NotificationAttributeId.PositiveActionLabel,
                NotificationAttributeId.NegativeActionLabel,
            ),
        )
    }

    private fun handleAttributes(event: AncsEvent.NotificationAttributesReceived) {
        if (event.response.notificationUid in locallyDeletedNotificationUids) {
            return
        }
        val current = activeNotificationsByUid[event.response.notificationUid] ?: return
        val attributes = event.response.attributes
        val updated = current.copy(
            title = attributes[NotificationAttributeId.Title].orEmpty(),
            subtitle = attributes[NotificationAttributeId.Subtitle].orEmpty(),
            message = attributes[NotificationAttributeId.Message].orEmpty(),
            appIdentifier = attributes[NotificationAttributeId.AppIdentifier].orEmpty(),
            dateText = attributes[NotificationAttributeId.Date].orEmpty(),
            positiveActionLabel = attributes[NotificationAttributeId.PositiveActionLabel]?.ifBlank { null },
            negativeActionLabel = attributes[NotificationAttributeId.NegativeActionLabel]?.ifBlank { null },
            lastUpdatedMillis = System.currentTimeMillis(),
        )
        activeNotificationsByUid[event.response.notificationUid] = updated
        notificationHistoryStore.upsert(updated)
        _notificationPresentationCommands.tryEmit(NotificationPresentationCommand.Show(updated))
    }
}
