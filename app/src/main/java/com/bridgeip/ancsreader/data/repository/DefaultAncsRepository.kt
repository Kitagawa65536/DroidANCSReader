package com.bridgeip.ancsreader.data.repository

import com.bridgeip.ancsreader.bluetooth.AncsManager
import com.bridgeip.ancsreader.bluetooth.BleConnectionManager
import com.bridgeip.ancsreader.bluetooth.BleScanner
import com.bridgeip.ancsreader.bluetooth.BluetoothStateMonitor
import com.bridgeip.ancsreader.data.model.AncsEvent
import com.bridgeip.ancsreader.data.model.AncsNotification
import com.bridgeip.ancsreader.data.model.ConnectionStage
import com.bridgeip.ancsreader.data.model.ConnectionStatus
import com.bridgeip.ancsreader.data.model.DebugLogEntry
import com.bridgeip.ancsreader.data.model.DiscoveredDevice
import com.bridgeip.ancsreader.data.model.GattServiceSummary
import com.bridgeip.ancsreader.data.model.NotificationAction
import com.bridgeip.ancsreader.data.model.NotificationAttributeId
import com.bridgeip.ancsreader.data.model.NotificationEventType
import com.bridgeip.ancsreader.util.DebugLogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

class DefaultAncsRepository(
    scope: CoroutineScope,
    private val scanner: BleScanner,
    private val connectionManager: BleConnectionManager,
    private val bluetoothStateMonitor: BluetoothStateMonitor,
    ancsManager: AncsManager,
    logStore: DebugLogStore,
) : AncsRepository {
    override val bluetoothEnabled: StateFlow<Boolean> = bluetoothStateMonitor.isBluetoothEnabled
    override val isScanning: StateFlow<Boolean> = scanner.isScanning
    override val scanResults: StateFlow<List<DiscoveredDevice>> = scanner.scanResults
    override val connectionStatus: StateFlow<ConnectionStatus> = connectionManager.connectionStatus
    override val gattServices: StateFlow<List<GattServiceSummary>> = connectionManager.services
    override val debugLogs: StateFlow<List<DebugLogEntry>> = logStore.entries

    private val notificationsByUid = MutableStateFlow<Map<Long, AncsNotification>>(emptyMap())
    override val notifications: StateFlow<List<AncsNotification>> = notificationsByUid
        .map { notifications -> notifications.values.sortedByDescending { it.lastUpdatedMillis } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        ancsManager.events.onEach { event ->
            when (event) {
                is AncsEvent.NotificationChanged -> handleNotificationEvent(ancsManager, event)
                is AncsEvent.NotificationAttributesReceived -> handleAttributes(event)
                AncsEvent.SessionEnded -> notificationsByUid.value = emptyMap()
                AncsEvent.SessionReady -> Unit
                is AncsEvent.Error -> Unit
            }
        }.launchIn(scope)

        connectionManager.connectionStatus.onEach { status ->
            if (status.stage == ConnectionStage.Disconnected || status.stage == ConnectionStage.Error) {
                notificationsByUid.value = emptyMap()
            }
        }.launchIn(scope)
    }

    override fun refreshBluetoothState() {
        bluetoothStateMonitor.refresh()
    }

    override fun startScan() {
        scanner.startScan()
    }

    override fun stopScan() {
        scanner.stopScan()
    }

    override fun connect(address: String) {
        scanner.stopScan()
        connectionManager.connect(address)
    }

    override fun disconnect() {
        connectionManager.disconnect()
    }

    override fun performAction(notificationUid: Long, action: NotificationAction) {
        connectionManager.requestNotificationAction(notificationUid, action)
    }

    private fun handleNotificationEvent(
        ancsManager: AncsManager,
        event: AncsEvent.NotificationChanged,
    ) {
        val source = event.event
        if (source.eventType == NotificationEventType.Removed) {
            notificationsByUid.value = notificationsByUid.value - source.notificationUid
            return
        }

        val now = System.currentTimeMillis()
        val existing = notificationsByUid.value[source.notificationUid]
        notificationsByUid.value = notificationsByUid.value + (
            source.notificationUid to AncsNotification(
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
            )
        )
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
        val current = notificationsByUid.value[event.response.notificationUid] ?: return
        val attributes = event.response.attributes
        notificationsByUid.value = notificationsByUid.value + (
            event.response.notificationUid to current.copy(
                title = attributes[NotificationAttributeId.Title].orEmpty(),
                subtitle = attributes[NotificationAttributeId.Subtitle].orEmpty(),
                message = attributes[NotificationAttributeId.Message].orEmpty(),
                appIdentifier = attributes[NotificationAttributeId.AppIdentifier].orEmpty(),
                dateText = attributes[NotificationAttributeId.Date].orEmpty(),
                positiveActionLabel = attributes[NotificationAttributeId.PositiveActionLabel]?.ifBlank { null },
                negativeActionLabel = attributes[NotificationAttributeId.NegativeActionLabel]?.ifBlank { null },
                lastUpdatedMillis = System.currentTimeMillis(),
            )
        )
    }
}

