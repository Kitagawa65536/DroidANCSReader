package com.bridgeip.ancsreader.data.repository

import com.bridgeip.ancsreader.bluetooth.AncsEventController
import com.bridgeip.ancsreader.bluetooth.BleConnectionController
import com.bridgeip.ancsreader.bluetooth.BleScanner
import com.bridgeip.ancsreader.bluetooth.BluetoothStateMonitor
import com.bridgeip.ancsreader.data.model.AncsNotification
import com.bridgeip.ancsreader.data.model.AncsEvent
import com.bridgeip.ancsreader.data.model.AppSettings
import com.bridgeip.ancsreader.data.model.ConnectionStatus
import com.bridgeip.ancsreader.data.model.DebugLogEntry
import com.bridgeip.ancsreader.data.model.DiscoveredDevice
import com.bridgeip.ancsreader.data.model.GattServiceSummary
import com.bridgeip.ancsreader.data.model.NotificationCategory
import com.bridgeip.ancsreader.data.model.NotificationEventFlags
import com.bridgeip.ancsreader.data.model.NotificationAction
import com.bridgeip.ancsreader.data.model.NotificationAttributeId
import com.bridgeip.ancsreader.data.store.AppPreferencesDataSource
import com.bridgeip.ancsreader.data.store.NotificationHistoryDataSource
import com.bridgeip.ancsreader.util.DebugLogSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultAncsRepositoryTest {
    @Test
    fun requestMissingNotificationDetails_requestsOnlyActiveNotificationsWithoutDetails() {
        val scanner = FakeBleScanner()
        val bluetoothStateMonitor = FakeBluetoothStateMonitor()
        val missingDetailsNotification = testNotification(
            notificationUid = 1L,
            removedOnSource = false,
        )
        val fetchedDetailsNotification = testNotification(
            notificationUid = 2L,
            removedOnSource = false,
            message = "Already fetched",
        )
        val removedMissingDetailsNotification = testNotification(
            notificationUid = 3L,
            removedOnSource = true,
        )
        val notificationsFlow = MutableStateFlow(
            listOf(
                missingDetailsNotification,
                fetchedDetailsNotification,
                removedMissingDetailsNotification,
            ),
        )
        val ancsManager = FakeAncsEventController()
        val connectionManager = FakeBleConnectionController()
        val appPreferencesStore = FakeAppPreferencesDataSource()
        val notificationHistoryStore = FakeNotificationHistoryDataSource(notificationsFlow)
        val logStore = FakeDebugLogSource()

        val repository = DefaultAncsRepository(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            scanner = scanner,
            connectionManager = connectionManager,
            bluetoothStateMonitor = bluetoothStateMonitor,
            ancsManager = ancsManager,
            logStore = logStore,
            appPreferencesStore = appPreferencesStore,
            notificationHistoryStore = notificationHistoryStore,
        )

        repository.requestMissingNotificationDetails()

        assertEquals(
            listOf(
                RequestedNotificationDetails(
                    notificationUid = 1L,
                    attributes = listOf(
                        NotificationAttributeId.AppIdentifier,
                        NotificationAttributeId.Title,
                        NotificationAttributeId.Subtitle,
                        NotificationAttributeId.Message,
                        NotificationAttributeId.Date,
                        NotificationAttributeId.PositiveActionLabel,
                        NotificationAttributeId.NegativeActionLabel,
                    ),
                ),
            ),
            ancsManager.requestedNotificationDetails,
        )
    }

    @Test
    fun clearRemovedOnSourceNotifications_deletesOnlyRemovedFromHistoryList() {
        val scanner = FakeBleScanner()
        val bluetoothStateMonitor = FakeBluetoothStateMonitor()
        val activeNotification = testNotification(
            notificationUid = 1L,
            removedOnSource = false,
        )
        val removedNotification = testNotification(
            notificationUid = 2L,
            removedOnSource = true,
        )
        val notificationsFlow = MutableStateFlow(
            listOf(activeNotification, removedNotification),
        )
        val ancsManager = FakeAncsEventController()
        val connectionManager = FakeBleConnectionController()
        val appPreferencesStore = FakeAppPreferencesDataSource()
        val notificationHistoryStore = FakeNotificationHistoryDataSource(notificationsFlow)
        val logStore = FakeDebugLogSource()

        val repository = DefaultAncsRepository(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            scanner = scanner,
            connectionManager = connectionManager,
            bluetoothStateMonitor = bluetoothStateMonitor,
            ancsManager = ancsManager,
            logStore = logStore,
            appPreferencesStore = appPreferencesStore,
            notificationHistoryStore = notificationHistoryStore,
        )

        assertEquals(listOf(activeNotification, removedNotification), repository.notifications.value)

        repository.clearRemovedOnSourceNotifications()

        assertEquals(1, notificationHistoryStore.clearRemovedOnSourceCalls)
        assertEquals(listOf(activeNotification), repository.notifications.value)
    }

    private fun testNotification(
        notificationUid: Long,
        removedOnSource: Boolean,
        message: String = "",
    ): AncsNotification = AncsNotification(
        notificationUid = notificationUid,
        category = NotificationCategory.Other,
        categoryCount = 1,
        flags = NotificationEventFlags(
            silent = false,
            important = false,
            preExisting = false,
            positiveAction = false,
            negativeAction = false,
        ),
        receivedAtMillis = 1_000L + notificationUid,
        lastUpdatedMillis = 2_000L + notificationUid,
        removedOnSource = removedOnSource,
        title = "Title $notificationUid",
        message = message,
    )

    private class FakeBleScanner : BleScanner {
        override val scanResults = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
        override val isScanning = MutableStateFlow(false)

        override fun startScan(profile: com.bridgeip.ancsreader.bluetooth.ScanProfile) = Unit

        override fun stopScan() = Unit
    }

    private class FakeBluetoothStateMonitor : BluetoothStateMonitor {
        override val isBluetoothEnabled = MutableStateFlow(true)

        override fun refresh() = Unit
    }

    private class FakeBleConnectionController : BleConnectionController {
        override val connectionStatus = MutableStateFlow(ConnectionStatus())
        override val services = MutableStateFlow<List<GattServiceSummary>>(emptyList())

        override fun connect(address: String) = Unit

        override fun disconnect() = Unit

        override fun requestNotificationAction(notificationUid: Long, action: NotificationAction) = Unit
    }

    private class FakeAncsEventController : AncsEventController {
        private val _events = MutableSharedFlow<AncsEvent>()
        override val events: SharedFlow<AncsEvent> = _events
        val requestedNotificationDetails = mutableListOf<RequestedNotificationDetails>()

        override fun requestNotificationDetails(
            notificationUid: Long,
            attributes: List<NotificationAttributeId>,
        ) {
            requestedNotificationDetails += RequestedNotificationDetails(
                notificationUid = notificationUid,
                attributes = attributes,
            )
        }
    }

    private data class RequestedNotificationDetails(
        val notificationUid: Long,
        val attributes: List<NotificationAttributeId>,
    )

    private class FakeAppPreferencesDataSource : AppPreferencesDataSource {
        override val settings = MutableStateFlow(AppSettings())

        override fun setForegroundServiceEnabled(enabled: Boolean) = Unit

        override fun setLastConnectedDevice(address: String, name: String?) = Unit
    }

    private class FakeNotificationHistoryDataSource(
        override val notifications: MutableStateFlow<List<AncsNotification>>,
    ) : NotificationHistoryDataSource {
        var clearRemovedOnSourceCalls: Int = 0

        override fun upsert(notification: AncsNotification) = Unit

        override fun markRemoved(notificationUid: Long) = Unit

        override fun delete(notificationUid: Long) = Unit

        override fun clearRemovedOnSourceNotifications() {
            clearRemovedOnSourceCalls += 1
            notifications.value = notifications.value.filterNot { it.removedOnSource }
        }

        override fun clear() = Unit
    }

    private class FakeDebugLogSource : DebugLogSource {
        override val entries = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    }
}
