package com.bridgeip.ancsreader.bluetooth

import com.bridgeip.ancsreader.data.model.DiscoveredDevice
import kotlinx.coroutines.flow.StateFlow

enum class ScanProfile {
    LowPower,
    Interactive,
}

interface BleScanner {
    val scanResults: StateFlow<List<DiscoveredDevice>>
    val isScanning: StateFlow<Boolean>

    fun startScan(profile: ScanProfile = ScanProfile.LowPower)
    fun stopScan()
}
