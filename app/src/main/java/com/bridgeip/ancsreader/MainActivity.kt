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
import com.bridgeip.ancsreader.ui.AncsReaderApp
import com.bridgeip.ancsreader.ui.theme.ANCSReaderTheme
import com.bridgeip.ancsreader.viewmodel.AncsViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: AncsViewModel by viewModels {
        AncsViewModel.Factory(
            AppGraph.get(application).ancsRepository,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                    onRequestPermissions = {
                        permissionLauncher.launch(
                            BluetoothPermissionResolver.requiredBlePermissions().toTypedArray(),
                        )
                    },
                    onEnableBluetooth = {
                        bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    },
                    onStartScan = viewModel::startScan,
                    onStopScan = viewModel::stopScan,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                    onPositiveAction = viewModel::performPositiveAction,
                    onNegativeAction = viewModel::performNegativeAction,
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
