package com.bridgeip.ancsreader.data.store

import android.content.Context
import com.bridgeip.ancsreader.data.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppPreferencesStore(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setForegroundServiceEnabled(enabled: Boolean) {
        update(_settings.value.copy(foregroundServiceEnabled = enabled))
    }

    fun setLastConnectedDevice(
        address: String,
        name: String?,
    ) {
        update(
            _settings.value.copy(
                lastConnectedDeviceAddress = address,
                lastConnectedDeviceName = name,
            ),
        )
    }

    private fun update(settings: AppSettings) {
        _settings.value = settings
        preferences.edit()
            .putBoolean(KEY_FOREGROUND_SERVICE_ENABLED, settings.foregroundServiceEnabled)
            .putString(KEY_LAST_CONNECTED_DEVICE_ADDRESS, settings.lastConnectedDeviceAddress)
            .putString(KEY_LAST_CONNECTED_DEVICE_NAME, settings.lastConnectedDeviceName)
            .apply()
    }

    private fun load(): AppSettings = AppSettings(
        foregroundServiceEnabled = preferences.getBoolean(KEY_FOREGROUND_SERVICE_ENABLED, false),
        lastConnectedDeviceAddress = preferences.getString(KEY_LAST_CONNECTED_DEVICE_ADDRESS, null),
        lastConnectedDeviceName = preferences.getString(KEY_LAST_CONNECTED_DEVICE_NAME, null),
    )

    private companion object {
        private const val PREFERENCES_NAME = "ancs_reader_settings"
        private const val KEY_FOREGROUND_SERVICE_ENABLED = "foreground_service_enabled"
        private const val KEY_LAST_CONNECTED_DEVICE_ADDRESS = "last_connected_device_address"
        private const val KEY_LAST_CONNECTED_DEVICE_NAME = "last_connected_device_name"
    }
}

