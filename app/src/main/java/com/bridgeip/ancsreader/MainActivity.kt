package com.bridgeip.ancsreader

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bridgeip.ancsreader.bluetooth.BluetoothPermissionResolver
import com.bridgeip.ancsreader.service.ConnectionForegroundService
import com.bridgeip.ancsreader.ui.AncsReaderApp
import com.bridgeip.ancsreader.ui.theme.ANCSReaderTheme
import com.bridgeip.ancsreader.viewmodel.AncsViewModel
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity

class MainActivity : ComponentActivity() {
    private val viewModel: AncsViewModel by viewModels {
        AncsViewModel.Factory(
            AppGraph.get(application).ancsRepository,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "0.1.0"
        setContent {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                viewModel.updatePermissionSnapshot(
                    grantedRequired = currentGrantedPermissions(BluetoothPermissionResolver.requiredBlePermissions()),
                    grantedOptional = currentGrantedPermissions(BluetoothPermissionResolver.optionalForegroundServicePermissions()),
                )
            }
            val optionalPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                viewModel.updatePermissionSnapshot(
                    grantedRequired = currentGrantedPermissions(BluetoothPermissionResolver.requiredBlePermissions()),
                    grantedOptional = currentGrantedPermissions(BluetoothPermissionResolver.optionalForegroundServicePermissions()),
                )
            }
            val bluetoothLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) {
                viewModel.refreshBluetoothState()
            }

            fun refreshEnvironment() {
                viewModel.updatePermissionSnapshot(
                    grantedRequired = currentGrantedPermissions(BluetoothPermissionResolver.requiredBlePermissions()),
                    grantedOptional = currentGrantedPermissions(BluetoothPermissionResolver.optionalForegroundServicePermissions()),
                )
                viewModel.refreshBluetoothState()
            }

            LaunchedEffect(Unit) {
                refreshEnvironment()
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        refreshEnvironment()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            ANCSReaderTheme {
                AncsReaderApp(
                    uiState = uiState,
                    appVersion = appVersion,
                    onRequestPermissions = {
                        permissionLauncher.launch(
                            BluetoothPermissionResolver.requiredBlePermissions().toTypedArray(),
                        )
                    },
                    onRequestOptionalPermissions = {
                        optionalPermissionLauncher.launch(
                            BluetoothPermissionResolver.optionalForegroundServicePermissions().toTypedArray(),
                        )
                    },
                    onEnableBluetooth = {
                        bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    },
                    onStartScan = viewModel::startInteractiveScan,
                    onStopScan = viewModel::stopScan,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                    onSetForegroundServiceEnabled = { enabled ->
                        viewModel.setForegroundServiceEnabled(enabled)
                        if (enabled) {
                            ConnectionForegroundService.start(this@MainActivity)
                        } else {
                            ConnectionForegroundService.stop(this@MainActivity)
                        }
                    },
                    onDeleteNotification = viewModel::deleteNotification,
                    onClearNotifications = viewModel::clearNotificationHistory,
                    onClearRemovedOnSourceNotifications = viewModel::clearRemovedOnSourceNotifications,
                    onRequestMissingNotificationDetails = viewModel::requestMissingNotificationDetails,
                    onOpenOssLicenses = {
                        OssLicensesMenuActivity.setActivityTitle(getString(R.string.oss_licenses_title))
                        startActivity(Intent(this@MainActivity, OssLicensesMenuActivity::class.java))
                    },
                )
            }
        }
    }

    private fun currentGrantedPermissions(permissions: List<String>): Set<String> = permissions
        .filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
        .toSet()
}
