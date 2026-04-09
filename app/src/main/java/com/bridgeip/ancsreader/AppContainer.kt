package com.bridgeip.ancsreader

import android.app.Application
import com.bridgeip.ancsreader.bluetooth.AncsManager
import com.bridgeip.ancsreader.bluetooth.AndroidBleScanner
import com.bridgeip.ancsreader.bluetooth.AndroidBluetoothStateMonitor
import com.bridgeip.ancsreader.bluetooth.BleConnectionManager
import com.bridgeip.ancsreader.data.repository.AncsRepository
import com.bridgeip.ancsreader.data.repository.DefaultAncsRepository
import com.bridgeip.ancsreader.data.store.AppPreferencesStore
import com.bridgeip.ancsreader.data.store.NotificationHistoryStore
import com.bridgeip.ancsreader.util.DebugLogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

interface AppContainer {
    val ancsRepository: AncsRepository
}

object AppGraph {
    @Volatile
    private var container: AppContainer? = null

    fun get(application: Application): AppContainer {
        return container ?: synchronized(this) {
            container ?: DefaultAppContainer(application).also { created ->
                container = created
            }
        }
    }
}

class DefaultAppContainer(
    application: Application,
) : AppContainer {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logStore = DebugLogStore()
    private val appPreferencesStore = AppPreferencesStore(application)
    private val notificationHistoryStore = NotificationHistoryStore(application)
    private val ancsManager = AncsManager(logStore::append)
    private val bleScanner = AndroidBleScanner(
        context = application,
        logger = logStore::append,
    )
    private val bluetoothStateMonitor = AndroidBluetoothStateMonitor(
        context = application,
        logger = logStore::append,
    )
    private val bleConnectionManager = BleConnectionManager(
        context = application,
        ancsManager = ancsManager,
        logger = logStore::append,
    )

    override val ancsRepository: AncsRepository = DefaultAncsRepository(
        scope = applicationScope,
        scanner = bleScanner,
        connectionManager = bleConnectionManager,
        bluetoothStateMonitor = bluetoothStateMonitor,
        ancsManager = ancsManager,
        logStore = logStore,
        appPreferencesStore = appPreferencesStore,
        notificationHistoryStore = notificationHistoryStore,
    )
}
