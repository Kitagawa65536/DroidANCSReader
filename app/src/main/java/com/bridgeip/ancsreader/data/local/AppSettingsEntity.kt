package com.bridgeip.ancsreader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    val foregroundServiceEnabled: Boolean,
    val lastConnectedDeviceAddress: String?,
    val lastConnectedDeviceName: String?,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
