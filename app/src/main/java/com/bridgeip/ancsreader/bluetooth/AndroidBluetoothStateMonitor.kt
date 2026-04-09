package com.bridgeip.ancsreader.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidBluetoothStateMonitor(
    private val context: Context,
    private val logger: (String) -> Unit,
) : BluetoothStateMonitor {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)

    private val _isBluetoothEnabled = MutableStateFlow(bluetoothManager?.adapter?.isEnabled == true)
    override val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    init {
        context.registerReceiver(
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
        )
    }

    override fun refresh() {
        val enabled = bluetoothManager?.adapter?.isEnabled == true
        _isBluetoothEnabled.value = enabled
        logger("Bluetooth adapter state: ${if (enabled) "enabled" else "disabled"}")
    }
}

