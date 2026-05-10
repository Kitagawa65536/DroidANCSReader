package com.bridgeip.ancsreader.data.store

import android.content.Context
import com.bridgeip.ancsreader.data.local.AncsDatabase
import com.bridgeip.ancsreader.data.local.AppSettingsEntity
import com.bridgeip.ancsreader.data.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AppPreferencesStore(
    context: Context,
) : AppPreferencesDataSource {
    private val dao = AncsDatabase.getInstance(context).appSettingsDao()
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _settings = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        scope.launch {
            migrateLegacyPreferencesIfNeeded()
            dao.observeSettings().collectLatest { entity ->
                _settings.value = entity?.toModel() ?: AppSettings()
            }
        }
    }

    override fun setForegroundServiceEnabled(enabled: Boolean) {
        val updated = _settings.value.copy(foregroundServiceEnabled = enabled)
        _settings.value = updated
        scope.launch {
            dao.upsert(updated.toEntity())
        }
    }

    override fun setLastConnectedDevice(
        address: String,
        name: String?,
    ) {
        val updated = _settings.value.copy(
            lastConnectedDeviceAddress = address,
            lastConnectedDeviceName = name,
        )
        _settings.value = updated
        scope.launch {
            dao.upsert(updated.toEntity())
        }
    }

    private suspend fun migrateLegacyPreferencesIfNeeded() {
        if (dao.getSettings() != null) {
            return
        }
        dao.upsert(loadLegacySettings().toEntity())
        preferences.edit().clear().apply()
    }

    private fun loadLegacySettings(): AppSettings = AppSettings(
        foregroundServiceEnabled = preferences.getBoolean(KEY_FOREGROUND_SERVICE_ENABLED, false),
        lastConnectedDeviceAddress = preferences.getString(KEY_LAST_CONNECTED_DEVICE_ADDRESS, null),
        lastConnectedDeviceName = preferences.getString(KEY_LAST_CONNECTED_DEVICE_NAME, null),
    )

    private fun AppSettings.toEntity(): AppSettingsEntity = AppSettingsEntity(
        foregroundServiceEnabled = foregroundServiceEnabled,
        lastConnectedDeviceAddress = lastConnectedDeviceAddress,
        lastConnectedDeviceName = lastConnectedDeviceName,
    )

    private fun AppSettingsEntity.toModel(): AppSettings = AppSettings(
        foregroundServiceEnabled = foregroundServiceEnabled,
        lastConnectedDeviceAddress = lastConnectedDeviceAddress,
        lastConnectedDeviceName = lastConnectedDeviceName,
    )

    private companion object {
        private const val PREFERENCES_NAME = "ancs_reader_settings"
        private const val KEY_FOREGROUND_SERVICE_ENABLED = "foreground_service_enabled"
        private const val KEY_LAST_CONNECTED_DEVICE_ADDRESS = "last_connected_device_address"
        private const val KEY_LAST_CONNECTED_DEVICE_NAME = "last_connected_device_name"
    }
}
