package com.bridgeip.ancsreader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.bridgeip.ancsreader.AppGraph
import com.bridgeip.ancsreader.MainActivity
import com.bridgeip.ancsreader.R
import com.bridgeip.ancsreader.data.model.ConnectionStage
import com.bridgeip.ancsreader.data.model.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ConnectionForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val repository by lazy {
        AppGraph.get(application).ancsRepository
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(repository.connectionStatus.value))
        serviceScope.launch {
            repository.connectionStatus.collectLatest { status ->
                notificationManager.notify(NOTIFICATION_ID, buildNotification(status))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(status: ConnectionStatus): Notification {
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.foreground_service_title))
            .setContentText(contentText(status))
            .setContentIntent(launchIntent)
            .setOngoing(true)
            .build()
    }

    private fun contentText(status: ConnectionStatus): String {
        val address = status.deviceAddress ?: "iPhone"
        return when (status.stage) {
            ConnectionStage.Ready -> getString(R.string.foreground_service_text_ready, address)
            ConnectionStage.Connecting,
            ConnectionStage.Bonding,
            ConnectionStage.DiscoveringServices,
            ConnectionStage.Subscribing -> getString(R.string.foreground_service_text_connecting, address)
            else -> getString(R.string.foreground_service_text_idle)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.foreground_service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.foreground_service_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_STOP = "com.bridgeip.ancsreader.action.STOP_FOREGROUND"
        private const val CHANNEL_ID = "ancs_connection"
        private const val NOTIFICATION_ID = 1001
    }
}
