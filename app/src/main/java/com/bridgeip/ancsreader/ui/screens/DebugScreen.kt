package com.bridgeip.ancsreader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bridgeip.ancsreader.data.model.ConnectionStatus
import com.bridgeip.ancsreader.data.model.DebugLogEntry
import com.bridgeip.ancsreader.data.model.GattServiceSummary
import com.bridgeip.ancsreader.ui.components.SectionCard

@Composable
fun DebugScreen(
    connectionStatus: ConnectionStatus,
    gattServices: List<GattServiceSummary>,
    debugLogs: List<DebugLogEntry>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(
                title = "GATT state",
                subtitle = connectionStatus.message,
            ) {
                Text("Stage: ${connectionStatus.stage}")
                connectionStatus.deviceAddress?.let { Text("Device: $it") }
            }
        }

        item {
            SectionCard(
                title = "Services",
                subtitle = "Discovered services and characteristics from the current GATT session.",
            ) {
                if (gattServices.isEmpty()) {
                    Text("No services discovered yet.")
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
                text = "Logs",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (debugLogs.isEmpty()) {
            item {
                Text(
                    text = "No logs yet.",
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

