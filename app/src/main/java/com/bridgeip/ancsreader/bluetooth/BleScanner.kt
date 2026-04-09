package com.bridgeip.ancsreader.bluetooth

import com.bridgeip.ancsreader.data.model.DiscoveredDevice
import kotlinx.coroutines.flow.StateFlow

interface BleScanner {
    val scanResults: StateFlow<List<DiscoveredDevice>>
    val isScanning: StateFlow<Boolean>

    fun startScan()
    fun stopScan()
}

