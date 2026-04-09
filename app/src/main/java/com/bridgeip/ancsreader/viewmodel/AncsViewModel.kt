package com.bridgeip.ancsreader.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bridgeip.ancsreader.bluetooth.BluetoothPermissionResolver
import com.bridgeip.ancsreader.data.model.AncsNotification
import com.bridgeip.ancsreader.data.model.AppSettings
import com.bridgeip.ancsreader.data.model.ConnectionStatus
import com.bridgeip.ancsreader.data.model.DebugLogEntry
import com.bridgeip.ancsreader.data.model.DiscoveredDevice
import com.bridgeip.ancsreader.data.model.GattServiceSummary
import com.bridgeip.ancsreader.data.model.NotificationAction
import com.bridgeip.ancsreader.data.repository.AncsRepository
import com.bridgeip.ancsreader.ui.state.AncsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class AncsViewModel(
    private val repository: AncsRepository,
) : ViewModel() {
    private val grantedRequiredPermissions = MutableStateFlow<Set<String>>(emptySet())
    private val grantedOptionalPermissions = MutableStateFlow<Set<String>>(emptySet())

    private val repositoryState = repository.bluetoothEnabled
        .combine(repository.isScanning) { bluetoothEnabled, isScanning ->
            PartialRepositorySnapshot(
                bluetoothEnabled = bluetoothEnabled,
                isScanning = isScanning,
            )
        }
        .combine(repository.scanResults) { partial, scanResults ->
            partial.copy(scanResults = scanResults)
        }
        .combine(repository.connectionStatus) { partial, connectionStatus ->
            partial.copy(connectionStatus = connectionStatus)
        }
        .combine(repository.notifications) { partial, notifications ->
            partial.copy(notifications = notifications)
        }
        .combine(repository.gattServices) { partial, gattServices ->
            partial.copy(gattServices = gattServices)
        }
        .combine(repository.debugLogs) { partial, debugLogs ->
            partial.copy(debugLogs = debugLogs)
        }
        .combine(repository.appSettings) { partial, appSettings ->
            RepositorySnapshot(
                bluetoothEnabled = partial.bluetoothEnabled,
                isScanning = partial.isScanning,
                scanResults = partial.scanResults,
                connectionStatus = partial.connectionStatus,
                notifications = partial.notifications,
                gattServices = partial.gattServices,
                debugLogs = partial.debugLogs,
                appSettings = appSettings,
            )
        }

    val uiState: StateFlow<AncsUiState> = combine(
        repositoryState,
        grantedRequiredPermissions,
        grantedOptionalPermissions,
    ) { snapshot, required, optional ->
        AncsUiState(
            bluetoothEnabled = snapshot.bluetoothEnabled,
            requiredPermissions = BluetoothPermissionResolver.requiredBlePermissions(),
            grantedPermissions = required,
            optionalPermissions = BluetoothPermissionResolver.optionalForegroundServicePermissions(),
            grantedOptionalPermissions = optional,
            isScanning = snapshot.isScanning,
            scanResults = snapshot.scanResults,
            connectionStatus = snapshot.connectionStatus,
            notifications = snapshot.notifications,
            gattServices = snapshot.gattServices,
            debugLogs = snapshot.debugLogs,
            appSettings = snapshot.appSettings,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AncsUiState(),
    )

    fun refreshBluetoothState() {
        repository.refreshBluetoothState()
    }

    fun updatePermissionSnapshot(
        grantedRequired: Set<String>,
        grantedOptional: Set<String>,
    ) {
        grantedRequiredPermissions.value = grantedRequired
        grantedOptionalPermissions.value = grantedOptional
    }

    fun startScan() {
        repository.startScan()
    }

    fun stopScan() {
        repository.stopScan()
    }

    fun connect(address: String) {
        repository.connect(address)
    }

    fun disconnect() {
        repository.disconnect()
    }

    fun performPositiveAction(notificationUid: Long) {
        repository.performAction(notificationUid, NotificationAction.Positive)
    }

    fun performNegativeAction(notificationUid: Long) {
        repository.performAction(notificationUid, NotificationAction.Negative)
    }

    fun deleteNotification(notificationUid: Long) {
        repository.deleteNotification(notificationUid)
    }

    fun clearNotificationHistory() {
        repository.clearNotificationHistory()
    }

    fun setForegroundServiceEnabled(enabled: Boolean) {
        repository.setForegroundServiceEnabled(enabled)
    }

    class Factory(
        private val repository: AncsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AncsViewModel(repository) as T
        }
    }

    private data class RepositorySnapshot(
        val bluetoothEnabled: Boolean,
        val isScanning: Boolean,
        val scanResults: List<DiscoveredDevice>,
        val connectionStatus: ConnectionStatus,
        val notifications: List<AncsNotification>,
        val gattServices: List<GattServiceSummary>,
        val debugLogs: List<DebugLogEntry>,
        val appSettings: AppSettings,
    )

    private data class PartialRepositorySnapshot(
        val bluetoothEnabled: Boolean,
        val isScanning: Boolean,
        val scanResults: List<DiscoveredDevice> = emptyList(),
        val connectionStatus: ConnectionStatus = ConnectionStatus(),
        val notifications: List<AncsNotification> = emptyList(),
        val gattServices: List<GattServiceSummary> = emptyList(),
        val debugLogs: List<DebugLogEntry> = emptyList(),
    )
}
