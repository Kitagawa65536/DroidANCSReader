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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bridgeip.ancsreader.R
import com.bridgeip.ancsreader.data.model.ConnectionStage
import com.bridgeip.ancsreader.data.model.ConnectionStatus
import com.bridgeip.ancsreader.data.model.DiscoveredDevice
import com.bridgeip.ancsreader.ui.components.SectionCard
import com.bridgeip.ancsreader.ui.state.AncsUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectionScreen(
    uiState: AncsUiState,
    onRequestPermissions: () -> Unit,
    onRequestOptionalPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onSetForegroundServiceEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(
                title = stringResource(R.string.permissions_title),
                subtitle = stringResource(R.string.permissions_description),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.requiredPermissions.forEach { permission ->
                        val granted = permission in uiState.grantedPermissions
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    stringResource(
                                        if (granted) R.string.permission_granted else R.string.permission_missing,
                                        permission,
                                    ),
                                )
                            },
                        )
                    }
                }
                if (uiState.missingPermissions.isNotEmpty()) {
                    Button(onClick = onRequestPermissions) {
                        Text(stringResource(R.string.request_bluetooth_permissions))
                    }
                }
                if (uiState.optionalPermissions.isNotEmpty() && uiState.missingOptionalPermissions.isNotEmpty()) {
                    OutlinedButton(onClick = onRequestOptionalPermissions) {
                        Text(stringResource(R.string.allow_android_notifications))
                    }
                }
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.bluetooth_title),
                subtitle = stringResource(R.string.bluetooth_description),
            ) {
                Text(
                    text = stringResource(
                        if (uiState.bluetoothEnabled) R.string.bluetooth_enabled else R.string.bluetooth_disabled,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (!uiState.bluetoothEnabled) {
                    Button(onClick = onEnableBluetooth) {
                        Text(stringResource(R.string.enable_bluetooth))
                    }
                }
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.connection_title),
                subtitle = localizedConnectionMessage(uiState.connectionStatus),
            ) {
                Text(
                    text = stringResource(
                        R.string.connection_status,
                        stringResource(uiState.connectionStatus.stage.labelResId),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (uiState.isScanning) {
                        Button(onClick = onStopScan) {
                            Text(stringResource(R.string.stop_scan))
                        }
                    } else {
                        Button(
                            onClick = onStartScan,
                            enabled = uiState.canStartScan,
                        ) {
                            Text(stringResource(R.string.start_scan))
                        }
                    }
                    OutlinedButton(onClick = onDisconnect) {
                        Text(stringResource(R.string.disconnect))
                    }
                }
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.background_service_title),
                subtitle = stringResource(R.string.background_service_description),
            ) {
                Text(
                    text = stringResource(
                        if (uiState.appSettings.foregroundServiceEnabled) {
                            R.string.foreground_service_enabled
                        } else {
                            R.string.foreground_service_disabled
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                uiState.appSettings.lastConnectedDeviceLabel?.let { deviceLabel ->
                    Text(
                        text = stringResource(R.string.paired_target, deviceLabel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onSetForegroundServiceEnabled(true) },
                        enabled = uiState.appSettings.lastConnectedDeviceAddress != null && !uiState.appSettings.foregroundServiceEnabled,
                    ) {
                        Text(stringResource(R.string.start_service))
                    }
                    OutlinedButton(
                        onClick = { onSetForegroundServiceEnabled(false) },
                        enabled = uiState.appSettings.foregroundServiceEnabled,
                    ) {
                        Text(stringResource(R.string.stop_service))
                    }
                }
                if (uiState.appSettings.lastConnectedDeviceAddress == null) {
                    Text(
                        text = stringResource(R.string.background_target_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.detected_devices_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (uiState.scanResults.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_ble_devices),
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
        title = device.name ?: stringResource(R.string.unnamed_ble_device),
        subtitle = device.address,
    ) {
        Text(stringResource(R.string.rssi_value, device.rssi))
        Text(
            stringResource(
                R.string.connectable_value,
                stringResource(if (device.isConnectable) R.string.yes else R.string.unknown),
            ),
        )
        Button(
            onClick = { onConnect(device.address) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.pair_and_connect))
        }
    }
}

private val ConnectionStage.labelResId: Int
    get() = when (this) {
        ConnectionStage.Idle -> R.string.connection_stage_idle
        ConnectionStage.Scanning -> R.string.connection_stage_scanning
        ConnectionStage.Bonding -> R.string.connection_stage_bonding
        ConnectionStage.Connecting -> R.string.connection_stage_connecting
        ConnectionStage.DiscoveringServices -> R.string.connection_stage_discovering_services
        ConnectionStage.Subscribing -> R.string.connection_stage_subscribing
        ConnectionStage.Ready -> R.string.connection_stage_ready
        ConnectionStage.Disconnected -> R.string.connection_stage_disconnected
        ConnectionStage.Error -> R.string.connection_stage_error
    }

@Composable
private fun localizedConnectionMessage(status: ConnectionStatus): String {
    val device = status.deviceAddress ?: "iPhone"
    return when (status.stage) {
        ConnectionStage.Idle -> stringResource(R.string.connection_message_idle)
        ConnectionStage.Scanning -> stringResource(R.string.connection_message_scanning)
        ConnectionStage.Bonding -> stringResource(R.string.connection_message_bonding)
        ConnectionStage.Connecting -> stringResource(R.string.connection_message_connecting, device)
        ConnectionStage.DiscoveringServices -> stringResource(R.string.connection_message_discovering_services)
        ConnectionStage.Subscribing -> stringResource(R.string.connection_message_subscribing)
        ConnectionStage.Ready -> stringResource(R.string.connection_message_ready)
        ConnectionStage.Disconnected -> stringResource(R.string.connection_message_disconnected)
        ConnectionStage.Error -> stringResource(R.string.connection_message_error)
    }
}
