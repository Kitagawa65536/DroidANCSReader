package com.bridgeip.ancsreader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bridgeip.ancsreader.R
import com.bridgeip.ancsreader.data.model.ConnectionStage
import com.bridgeip.ancsreader.data.model.ConnectionStatus
import com.bridgeip.ancsreader.data.model.DebugLogEntry
import com.bridgeip.ancsreader.data.model.GattServiceSummary
import com.bridgeip.ancsreader.ui.components.SectionCard

@Composable
fun DebugScreen(
    connectionStatus: ConnectionStatus,
    gattServices: List<GattServiceSummary>,
    debugLogs: List<DebugLogEntry>,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Button(onClick = onNavigateBack) {
                Text(stringResource(R.string.debug_back_button))
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.gatt_state_title),
                subtitle = localizedConnectionMessage(connectionStatus),
            ) {
                Text(stringResource(R.string.debug_stage, stringResource(connectionStatus.stage.labelResId)))
                connectionStatus.deviceAddress?.let { Text(stringResource(R.string.debug_device, it)) }
            }
        }

        item {
            SectionCard(
                title = stringResource(R.string.services_title),
                subtitle = stringResource(R.string.services_description),
            ) {
                if (gattServices.isEmpty()) {
                    Text(stringResource(R.string.no_services_discovered))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        gattServices.forEach { service ->
                            Text(service.uuid, style = MaterialTheme.typography.bodyLarge)
                            service.characteristics.forEach { characteristic ->
                                Text(
                                    text = "• $characteristic",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.logs_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (debugLogs.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_logs),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        items(debugLogs.reversed(), key = { "${it.timestampMillis}-${it.message}" }) { log ->
            SectionCard(
                title = log.timestampLabel,
                subtitle = log.message,
            ) {}
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
