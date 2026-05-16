package com.bridgeip.ancsreader.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bridgeip.ancsreader.ui.screens.AboutScreen
import com.bridgeip.ancsreader.ui.screens.ConnectionScreen
import com.bridgeip.ancsreader.ui.screens.DebugScreen
import com.bridgeip.ancsreader.ui.screens.NotificationsScreen
import com.bridgeip.ancsreader.ui.state.AncsUiState
import com.bridgeip.ancsreader.ui.state.MainTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AncsReaderApp(
    uiState: AncsUiState,
    appVersion: String,
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
    onClearRemovedOnSourceNotifications: () -> Unit,
    onOpenOssLicenses: () -> Unit,
) {
    val visibleTabs = listOf(MainTab.Connection, MainTab.Notifications, MainTab.More)
    val pagerState = rememberPagerState(pageCount = { MainTab.entries.size })
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("DroidANCSReader") },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val currentTab = MainTab.entries[pagerState.currentPage]
            val selectedVisibleTabIndex = visibleTabs.indexOf(currentTab)
                .takeIf { it >= 0 }
                ?: visibleTabs.indexOf(MainTab.More)

            TabRow(selectedTabIndex = selectedVisibleTabIndex) {
                visibleTabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedVisibleTabIndex == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(MainTab.entries.indexOf(tab))
                            }
                        },
                        text = { Text(tab.title) },
                    )
                }
            }

            val contentModifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp)

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            ) { page ->
                when (MainTab.entries[page]) {
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
                        onClearRemovedOnSourceNotifications = onClearRemovedOnSourceNotifications,
                        hasRemovedOnSourceNotifications = uiState.notifications.any { it.removedOnSource },
                        modifier = contentModifier,
                    )

                    MainTab.Debug -> DebugScreen(
                        connectionStatus = uiState.connectionStatus,
                        gattServices = uiState.gattServices,
                        debugLogs = uiState.debugLogs,
                        onNavigateBack = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(MainTab.entries.indexOf(MainTab.More))
                            }
                        },
                        modifier = contentModifier,
                    )

                    MainTab.More -> AboutScreen(
                        appVersion = appVersion,
                        onOpenOssLicenses = onOpenOssLicenses,
                        onOpenDebug = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(MainTab.entries.indexOf(MainTab.Debug))
                            }
                        },
                        modifier = contentModifier,
                    )
                }
            }
        }
    }
}
