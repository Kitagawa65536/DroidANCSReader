package com.bridgeip.ancsreader.bluetooth

import kotlinx.coroutines.flow.StateFlow

interface BluetoothStateMonitor {
    val isBluetoothEnabled: StateFlow<Boolean>
    fun refresh()
}

