package com.bridgeip.ancsreader.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bridgeip.ancsreader.ui.screens.ConnectionScreen
import com.bridgeip.ancsreader.ui.screens.DebugScreen
import com.bridgeip.ancsreader.ui.screens.NotificationsScreen
import com.bridgeip.ancsreader.ui.state.AncsUiState
import com.bridgeip.ancsreader.ui.state.MainTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AncsReaderApp(
    uiState: AncsUiState,
    onRequestPermissions: () -> Unit,
    onRequestOptionalPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onSetForegroundServiceEnabled: (Boolean) -> Unit,
    onDeleteNotification: (Long) -> Unit,
    onClearNotifications: () -> Unit,
) {
    var currentTab by rememberSaveable { mutableStateOf(MainTab.Connection) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("ANCS Reader") },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { innerPadding ->
        TabRow(
            selectedTabIndex = currentTab.ordinal,
            modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
        ) {
            MainTab.entries.forEach { tab ->
                Tab(
                    selected = currentTab == tab,
                    onClick = { currentTab = tab },
                    text = { Text(tab.title) },
                )
            }
        }

        val contentModifier = Modifier
            .fillMaxSize()
            .padding(top = innerPadding.calculateTopPadding() + 48.dp)
            .padding(horizontal = 16.dp, vertical = 16.dp)

        when (currentTab) {
            MainTab.Connection -> ConnectionScreen(
                uiState = uiState,
                onRequestPermissions = onRequestPermissions,
                onRequestOptionalPermissions = onRequestOptionalPermissions,
                onEnableBluetooth = onEnableBluetooth,
                onStartScan = onStartScan,
                onStopScan = onStopScan,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                onSetForegroundServiceEnabled = onSetForegroundServiceEnabled,
                modifier = contentModifier,
            )

            MainTab.Notifications -> NotificationsScreen(
                notifications = uiState.notifications,
                onDeleteNotification = onDeleteNotification,
                onClearNotifications = onClearNotifications,
                modifier = contentModifier,
            )

            MainTab.Debug -> DebugScreen(
                connectionStatus = uiState.connectionStatus,
                gattServices = uiState.gattServices,
                debugLogs = uiState.debugLogs,
                modifier = contentModifier,
            )
        }
    }
}
