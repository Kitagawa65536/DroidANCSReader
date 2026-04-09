package com.bridgeip.ancsreader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bridgeip.ancsreader.data.model.DiscoveredDevice
import com.bridgeip.ancsreader.ui.components.SectionCard
import com.bridgeip.ancsreader.ui.state.AncsUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectionScreen(
    uiState: AncsUiState,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(
                title = "Permissions",
                subtitle = "Android 12+ needs BLUETOOTH_SCAN and BLUETOOTH_CONNECT. Pre-Android 12 falls back to legacy Bluetooth and location permissions.",
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.requiredPermissions.forEach { permission ->
                        val granted = permission in uiState.grantedPermissions
                        AssistChip(
                            onClick = {},
                            label = { Text(if (granted) "$permission granted" else "$permission missing") },
                        )
                    }
                }
                if (uiState.missingPermissions.isNotEmpty()) {
                    Button(onClick = onRequestPermissions) {
                        Text("Request Bluetooth permissions")
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Bluetooth",
                subtitle = "Turn Bluetooth on before scanning for an iPhone.",
            ) {
                Text(
                    text = if (uiState.bluetoothEnabled) "Bluetooth is enabled." else "Bluetooth is disabled.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (!uiState.bluetoothEnabled) {
                    Button(onClick = onEnableBluetooth) {
                        Text("Enable Bluetooth")
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Connection",
                subtitle = uiState.connectionStatus.message,
            ) {
                Text(
                    text = "Status: ${uiState.connectionStatus.stage}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (uiState.isScanning) {
                        Button(onClick = onStopScan) {
                            Text("Stop scan")
                        }
                    } else {
                        Button(
                            onClick = onStartScan,
                            enabled = uiState.canStartScan,
                        ) {
                            Text("Start scan")
                        }
                    }
                    OutlinedButton(onClick = onDisconnect) {
                        Text("Disconnect")
                    }
                }
            }
        }

        item {
            Text(
                text = "Detected devices",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (uiState.scanResults.isEmpty()) {
            item {
                Text(
                    text = "No BLE devices yet. Start a scan and unlock the iPhone so it can accept pairing.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        items(uiState.scanResults, key = { it.address }) { device ->
            DeviceCard(device = device, onConnect = onConnect)
        }
    }
}

@Composable
private fun DeviceCard(
    device: DiscoveredDevice,
    onConnect: (String) -> Unit,
) {
    SectionCard(
        title = device.name ?: "Unnamed BLE device",
        subtitle = device.address,
    ) {
        Text("RSSI: ${device.rssi} dBm")
        Text("Connectable: ${if (device.isConnectable) "Yes" else "Unknown"}")
        Button(
            onClick = { onConnect(device.address) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Pair and connect")
        }
    }
}
