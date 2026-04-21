package com.bridgeip.ancsreader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat.BigTextStyle
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bridgeip.ancsreader.AppGraph
import com.bridgeip.ancsreader.MainActivity
import com.bridgeip.ancsreader.R
import com.bridgeip.ancsreader.data.model.AncsNotification
import com.bridgeip.ancsreader.data.model.ConnectionStage
import com.bridgeip.ancsreader.data.model.ConnectionStatus
import com.bridgeip.ancsreader.data.model.NotificationPresentationCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ConnectionForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val mirroredNotificationIds = linkedSetOf<Int>()
    private var reconnectJob: Job? = null
    private var reconnectAttemptCount = 0
    private val repository by lazy {
        AppGraph.get(application).ancsRepository
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createMirroredNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(repository.connectionStatus.value))
        serviceScope.launch {
            repository.connectionStatus.collectLatest { status ->
                notificationManager.notify(NOTIFICATION_ID, buildNotification(status))
            }
        }
        serviceScope.launch {
            combine(repository.connectionStatus, repository.appSettings) { status, settings ->
                status to settings
            }.collectLatest { (status, settings) ->
                handleReconnectPolicy(status, settings.lastConnectedDeviceAddress, settings.foregroundServiceEnabled)
            }
        }
        serviceScope.launch {
            repository.notificationPresentationCommands.collectLatest { command ->
                when (command) {
                    is NotificationPresentationCommand.Show -> showMirroredNotification(command.notification)
                    is NotificationPresentationCommand.Cancel -> cancelMirroredNotification(command.notificationUid.toInt())
                    NotificationPresentationCommand.CancelAll -> cancelAllMirroredNotifications()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            repository.setForegroundServiceEnabled(false)
            repository.disconnect()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        repository.setForegroundServiceEnabled(true)
        repository.reconnectLastDevice()
        return START_STICKY
    }

    override fun onDestroy() {
        reconnectJob?.cancel()
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
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ConnectionForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_status)
            .setContentTitle(getString(R.string.foreground_service_title))
            .setContentText(contentText(status))
            .setContentIntent(launchIntent)
            .setOngoing(true)
            .addAction(0, getString(R.string.foreground_service_stop), stopIntent)
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

    private fun handleReconnectPolicy(
        status: ConnectionStatus,
        lastConnectedDeviceAddress: String?,
        foregroundServiceEnabled: Boolean,
    ) {
        if (!foregroundServiceEnabled || lastConnectedDeviceAddress == null) {
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectAttemptCount = 0
            return
        }

        when (status.stage) {
            ConnectionStage.Idle,
            ConnectionStage.Disconnected -> scheduleReconnect(lastConnectedDeviceAddress)

            ConnectionStage.Bonding,
            ConnectionStage.Connecting,
            ConnectionStage.DiscoveringServices,
            ConnectionStage.Subscribing,
            ConnectionStage.Ready -> {
                reconnectJob?.cancel()
                reconnectJob = null
                reconnectAttemptCount = 0
            }

            ConnectionStage.Error -> {
                reconnectJob?.cancel()
                reconnectJob = null
            }

            ConnectionStage.Scanning -> Unit
        }
    }

    private fun scheduleReconnect(lastConnectedDeviceAddress: String) {
        if (reconnectJob?.isActive == true) {
            return
        }

        val delayMillis = reconnectDelayMillis(reconnectAttemptCount)
        reconnectJob = serviceScope.launch {
            delay(delayMillis)
            reconnectJob = null
            val settings = repository.appSettings.value
            val status = repository.connectionStatus.value
            if (!settings.foregroundServiceEnabled || settings.lastConnectedDeviceAddress != lastConnectedDeviceAddress) {
                reconnectAttemptCount = 0
                return@launch
            }
            if (status.stage == ConnectionStage.Idle || status.stage == ConnectionStage.Disconnected) {
                reconnectAttemptCount += 1
                repository.reconnectLastDevice()
            }
        }
    }

    private fun reconnectDelayMillis(attempt: Int): Long {
        // Back off quickly to avoid repeated BLE wakeups while still healing transient disconnects.
        return when (attempt) {
            0 -> 5_000L
            1 -> 15_000L
            2 -> 30_000L
            3 -> 60_000L
            4 -> 120_000L
            else -> 300_000L
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

    private fun createMirroredNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            MIRRORED_CHANNEL_ID,
            getString(R.string.mirrored_notifications_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.mirrored_notifications_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun showMirroredNotification(notification: AncsNotification) {
        val notificationId = notification.notificationUid.toInt()
        mirroredNotificationIds += notificationId
        val launchIntent = PendingIntent.getActivity(
            this,
            notificationId,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val mirrored = NotificationCompat.Builder(this, MIRRORED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_status)
            .setContentTitle(notification.displayTitle)
            .setContentText(notification.displayMessage.replace('\n', ' '))
            .setStyle(BigTextStyle().bigText(notification.displayMessage))
            .setSubText(notification.category.label)
            .setContentIntent(launchIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(notificationId, mirrored)
    }

    private fun cancelMirroredNotification(notificationId: Int) {
        mirroredNotificationIds -= notificationId
        notificationManager.cancel(notificationId)
    }

    private fun cancelAllMirroredNotifications() {
        mirroredNotificationIds.toList().forEach(::cancelMirroredNotification)
    }

    companion object {
        const val ACTION_STOP = "com.bridgeip.ancsreader.action.STOP_FOREGROUND"
        const val ACTION_START = "com.bridgeip.ancsreader.action.START_FOREGROUND"
        private const val CHANNEL_ID = "ancs_connection"
        private const val MIRRORED_CHANNEL_ID = "ancs_mirrored_notifications"
        private const val NOTIFICATION_ID = 1001

        fun start(service: Service) {
            ContextCompat.startForegroundService(
                service,
                Intent(service, ConnectionForegroundService::class.java).setAction(ACTION_START),
            )
        }

        fun start(context: android.content.Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ConnectionForegroundService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: android.content.Context) {
            context.startService(
                Intent(context, ConnectionForegroundService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
