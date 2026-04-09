package com.bridgeip.ancsreader.ui.state

import com.bridgeip.ancsreader.bluetooth.BluetoothPermissionResolver
import com.bridgeip.ancsreader.data.model.AncsNotification
import com.bridgeip.ancsreader.data.model.ConnectionStatus
import com.bridgeip.ancsreader.data.model.DebugLogEntry
import com.bridgeip.ancsreader.data.model.DiscoveredDevice
import com.bridgeip.ancsreader.data.model.GattServiceSummary

data class AncsUiState(
    val bluetoothEnabled: Boolean = false,
    val requiredPermissions: List<String> = BluetoothPermissionResolver.requiredBlePermissions(),
    val grantedPermissions: Set<String> = emptySet(),
    val optionalPermissions: List<String> = BluetoothPermissionResolver.optionalForegroundServicePermissions(),
    val grantedOptionalPermissions: Set<String> = emptySet(),
    val isScanning: Boolean = false,
    val scanResults: List<DiscoveredDevice> = emptyList(),
    val connectionStatus: ConnectionStatus = ConnectionStatus(),
    val notifications: List<AncsNotification> = emptyList(),
    val gattServices: List<GattServiceSummary> = emptyList(),
    val debugLogs: List<DebugLogEntry> = emptyList(),
) {
    val missingPermissions: List<String> = requiredPermissions.filterNot(grantedPermissions::contains)
    val missingOptionalPermissions: List<String> = optionalPermissions.filterNot(grantedOptionalPermissions::contains)
    val canStartScan: Boolean = bluetoothEnabled && missingPermissions.isEmpty()
}

enum class MainTab(val title: String) {
    Connection("Connect"),
    Notifications("Notifications"),
    Debug("Debug"),
}

