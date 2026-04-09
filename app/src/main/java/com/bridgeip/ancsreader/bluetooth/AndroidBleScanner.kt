package com.bridgeip.ancsreader.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import com.bridgeip.ancsreader.data.model.DiscoveredDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidBleScanner(
    context: Context,
    private val logger: (String) -> Unit,
) : BleScanner {
    private companion object {
        private const val SCAN_REPORT_DELAY_MILLIS = 1_500L
    }

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val _scanResults = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val scanResults: StateFlow<List<DiscoveredDevice>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val resultMap = linkedMapOf<String, DiscoveredDevice>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            upsertResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::upsertResult)
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            logger("BLE scan failed: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    override fun startScan(profile: ScanProfile) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            logger("BLE scan skipped because Bluetooth is disabled")
            return
        }
        if (_isScanning.value) {
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            logger("BLE scanner is unavailable on this device")
            return
        }

        try {
            resultMap.clear()
            _scanResults.value = emptyList()
            val settings = ScanSettings.Builder()
                .setScanMode(
                    when (profile) {
                        ScanProfile.LowPower -> ScanSettings.SCAN_MODE_LOW_POWER
                        ScanProfile.Interactive -> ScanSettings.SCAN_MODE_LOW_LATENCY
                    },
                )
                .setReportDelay(
                    when (profile) {
                        ScanProfile.LowPower -> SCAN_REPORT_DELAY_MILLIS
                        ScanProfile.Interactive -> 0L
                    },
                )
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        setMatchMode(ScanSettings.MATCH_MODE_STICKY)
                        setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                    }
                }
                .build()
            scanner.startScan(
                null,
                settings,
                scanCallback,
            )
            _isScanning.value = true
            logger(
                when (profile) {
                    ScanProfile.LowPower -> "BLE scan started in low-power mode"
                    ScanProfile.Interactive -> "BLE scan started in interactive low-latency mode"
                },
            )
        } catch (_: SecurityException) {
            logger("BLE scan failed because a Bluetooth permission is missing")
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopScan() {
        if (!_isScanning.value) {
            return
        }
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
            logger("BLE scan stop failed because a Bluetooth permission is missing")
        } finally {
            _isScanning.value = false
            logger("BLE scan stopped")
        }
    }

    @SuppressLint("MissingPermission")
    private fun upsertResult(result: ScanResult) {
        val address = result.device.address ?: return
        resultMap[address] = DiscoveredDevice(
            address = address,
            name = result.device.name?.takeIf { it.isNotBlank() }
                ?: result.scanRecord?.deviceName?.takeIf { it.isNotBlank() },
            rssi = result.rssi,
            isConnectable = result.isConnectable,
        )
        _scanResults.value = resultMap.values.sortedByDescending { it.rssi }
    }
}
