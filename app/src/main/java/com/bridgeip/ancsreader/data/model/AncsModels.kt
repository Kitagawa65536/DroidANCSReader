package com.bridgeip.ancsreader.data.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class DiscoveredDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val isConnectable: Boolean,
)

data class DebugLogEntry(
    val timestampMillis: Long,
    val message: String,
) {
    val timestampLabel: String
        get() = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(timestampMillis))
}

enum class ConnectionStage {
    Idle,
    Scanning,
    Bonding,
    Connecting,
    DiscoveringServices,
    Subscribing,
    Ready,
    Disconnected,
    Error,
}

data class ConnectionStatus(
    val stage: ConnectionStage = ConnectionStage.Idle,
    val deviceAddress: String? = null,
    val message: String = "Idle",
) {
    val isConnected: Boolean = stage == ConnectionStage.Ready
}

data class GattServiceSummary(
    val uuid: String,
    val characteristics: List<String>,
)

enum class NotificationEventType(val rawValue: Int) {
    Added(0),
    Modified(1),
    Removed(2);

    companion object {
        fun fromRaw(value: Int): NotificationEventType? = entries.firstOrNull { it.rawValue == value }
    }
}

enum class NotificationCategory(val rawValue: Int, val label: String) {
    Other(0, "Other"),
    IncomingCall(1, "Incoming Call"),
    MissedCall(2, "Missed Call"),
    Voicemail(3, "Voicemail"),
    Social(4, "Social"),
    Schedule(5, "Schedule"),
    Email(6, "Email"),
    News(7, "News"),
    HealthAndFitness(8, "Health"),
    BusinessAndFinance(9, "Business"),
    Location(10, "Location"),
    Entertainment(11, "Entertainment");

    companion object {
        fun fromRaw(value: Int): NotificationCategory = entries.firstOrNull { it.rawValue == value } ?: Other
    }
}

data class NotificationEventFlags(
    val silent: Boolean,
    val important: Boolean,
    val preExisting: Boolean,
    val positiveAction: Boolean,
    val negativeAction: Boolean,
) {
    companion object {
        fun fromBitmask(bitmask: Int): NotificationEventFlags = NotificationEventFlags(
            silent = bitmask and 0x01 != 0,
            important = bitmask and 0x02 != 0,
            preExisting = bitmask and 0x04 != 0,
            positiveAction = bitmask and 0x08 != 0,
            negativeAction = bitmask and 0x10 != 0,
        )
    }
}

data class NotificationSourceEvent(
    val eventType: NotificationEventType,
    val flags: NotificationEventFlags,
    val category: NotificationCategory,
    val categoryCount: Int,
    val notificationUid: Long,
)

enum class NotificationAttributeId(val rawValue: Int, val maxLength: Int? = null) {
    AppIdentifier(0),
    Title(1, 64),
    Subtitle(2, 64),
    Message(3, 512),
    MessageSize(4),
    Date(5),
    PositiveActionLabel(6),
    NegativeActionLabel(7);

    companion object {
        fun fromRaw(value: Int): NotificationAttributeId? = entries.firstOrNull { it.rawValue == value }
    }
}

data class NotificationAttributeResponse(
    val notificationUid: Long,
    val attributes: Map<NotificationAttributeId, String>,
)

enum class NotificationAction(val rawValue: Int) {
    Positive(0),
    Negative(1),
}

data class AncsNotification(
    val notificationUid: Long,
    val category: NotificationCategory,
    val categoryCount: Int,
    val title: String = "",
    val subtitle: String = "",
    val message: String = "",
    val appIdentifier: String = "",
    val dateText: String = "",
    val positiveActionLabel: String? = null,
    val negativeActionLabel: String? = null,
    val flags: NotificationEventFlags,
    val receivedAtMillis: Long,
    val lastUpdatedMillis: Long,
    val removedOnSource: Boolean = false,
) {
    val supportsPositiveAction: Boolean = flags.positiveAction
    val supportsNegativeAction: Boolean = flags.negativeAction
    val isMissingDetails: Boolean = subtitle.isBlank() && message.isBlank()
    val displayTitle: String = title.ifBlank { appIdentifier.ifBlank { "Notification $notificationUid" } }
    val displayMessage: String = buildString {
        if (subtitle.isNotBlank()) {
            append(subtitle)
        }
        if (message.isNotBlank()) {
            if (isNotBlank()) {
                append("\n")
            }
            append(message)
        }
    }.ifBlank { "Waiting for details from ANCS…" }
}

data class AppSettings(
    val foregroundServiceEnabled: Boolean = false,
    val lastConnectedDeviceAddress: String? = null,
    val lastConnectedDeviceName: String? = null,
) {
    val lastConnectedDeviceLabel: String?
        get() = when {
            !lastConnectedDeviceName.isNullOrBlank() -> {
                if (!lastConnectedDeviceAddress.isNullOrBlank()) {
                    "$lastConnectedDeviceName ($lastConnectedDeviceAddress)"
                } else {
                    lastConnectedDeviceName
                }
            }

            !lastConnectedDeviceAddress.isNullOrBlank() -> lastConnectedDeviceAddress
            else -> null
        }
}

sealed interface NotificationPresentationCommand {
    data class Show(val notification: AncsNotification) : NotificationPresentationCommand
    data class Cancel(val notificationUid: Long) : NotificationPresentationCommand
    data object CancelAll : NotificationPresentationCommand
}

sealed interface AncsEvent {
    data class NotificationChanged(val event: NotificationSourceEvent) : AncsEvent
    data class NotificationAttributesReceived(val response: NotificationAttributeResponse) : AncsEvent
    data object SessionReady : AncsEvent
    data object SessionEnded : AncsEvent
    data class Error(val message: String) : AncsEvent
}
