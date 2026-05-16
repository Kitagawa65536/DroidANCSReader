package com.bridgeip.ancsreader.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bridgeip.ancsreader.R
import com.bridgeip.ancsreader.ui.screens.AboutScreen
import com.bridgeip.ancsreader.ui.screens.ConnectionScreen
import com.bridgeip.ancsreader.ui.screens.DebugScreen
import com.bridgeip.ancsreader.ui.screens.NotificationsScreen
import com.bridgeip.ancsreader.ui.state.AncsUiState
import com.bridgeip.ancsreader.ui.state.MainTab

private const val DebugRoute = "debug"
private const val SwipeThresholdPx = 96f

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
    onRequestMissingNotificationDetails: () -> Unit,
    onOpenOssLicenses: () -> Unit,
) {
    val visibleTabs = listOf(MainTab.Connection, MainTab.Notifications, MainTab.More)
    val navController = rememberNavController()

    fun navigateToTab(tab: MainTab) {
        navController.navigate(tab.route) {
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val currentDestination = navController.currentBackStackEntryAsState().value?.destination
            val currentRoute = currentDestination?.route
            val selectedVisibleTabIndex = visibleTabs.indexOfFirst { tab ->
                currentDestination?.hierarchy?.any { it.route == tab.route } == true
            }.takeIf { it >= 0 } ?: visibleTabs.indexOf(MainTab.More)

            TabRow(selectedTabIndex = selectedVisibleTabIndex) {
                visibleTabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedVisibleTabIndex == index,
                        onClick = {
                            if (currentRoute != tab.route) {
                                navigateToTab(tab)
                            }
                        },
                        text = { Text(stringResource(tab.titleResId)) },
                    )
                }
            }

            val contentModifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp)

            NavHost(
                navController = navController,
                startDestination = MainTab.Connection.route,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .pointerInput(selectedVisibleTabIndex) {
                        var dragAmount = 0f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragAmount = 0f
                            },
                            onHorizontalDrag = { _, dragDelta ->
                                dragAmount += dragDelta
                            },
                            onDragEnd = {
                                when {
                                    dragAmount <= -SwipeThresholdPx -> {
                                        visibleTabs.getOrNull(selectedVisibleTabIndex + 1)
                                            ?.let(::navigateToTab)
                                    }

                                    dragAmount >= SwipeThresholdPx -> {
                                        visibleTabs.getOrNull(selectedVisibleTabIndex - 1)
                                            ?.let(::navigateToTab)
                                    }
                                }
                            },
                            onDragCancel = {
                                dragAmount = 0f
                            },
                        )
                    },
            ) {
                composable(MainTab.Connection.route) {
                    ConnectionScreen(
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
                }

                composable(MainTab.Notifications.route) {
                    NotificationsScreen(
                        notifications = uiState.notifications,
                        onDeleteNotification = onDeleteNotification,
                        onClearNotifications = onClearNotifications,
                        onClearRemovedOnSourceNotifications = onClearRemovedOnSourceNotifications,
                        onRequestMissingNotificationDetails = onRequestMissingNotificationDetails,
                        hasRemovedOnSourceNotifications = uiState.notifications.any { it.removedOnSource },
                        modifier = contentModifier,
                    )
                }

                composable(MainTab.More.route) {
                    AboutScreen(
                        appVersion = appVersion,
                        onOpenOssLicenses = onOpenOssLicenses,
                        onOpenDebug = {
                            navController.navigate(DebugRoute)
                        },
                        modifier = contentModifier,
                    )
                }

                composable(DebugRoute) {
                    DebugScreen(
                        connectionStatus = uiState.connectionStatus,
                        gattServices = uiState.gattServices,
                        debugLogs = uiState.debugLogs,
                        onNavigateBack = {
                            navController.popBackStack()
                        },
                        modifier = contentModifier,
                    )
                }
            }
        }
    }
}
