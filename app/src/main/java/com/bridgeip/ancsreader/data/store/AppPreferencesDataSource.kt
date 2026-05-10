package com.bridgeip.ancsreader.data.store

import com.bridgeip.ancsreader.data.model.AppSettings
import kotlinx.coroutines.flow.StateFlow

interface AppPreferencesDataSource {
    val settings: StateFlow<AppSettings>

    fun setForegroundServiceEnabled(enabled: Boolean)

    fun setLastConnectedDevice(
        address: String,
        name: String?,
    )
}
