package com.bridgeip.ancsreader.bluetooth

import com.bridgeip.ancsreader.data.model.NotificationAction
import com.bridgeip.ancsreader.data.model.NotificationAttributeId
import com.bridgeip.ancsreader.util.toLeByteArray
import com.bridgeip.ancsreader.util.toLeShortByteArray
import java.util.UUID

object AncsBluetoothConstants {
    val ancsServiceUuid: UUID = UUID.fromString("7905F431-B5CE-4E99-A40F-4B1E122D00D0")
    val notificationSourceUuid: UUID = UUID.fromString("9FBF120D-6301-42D9-8C58-25E699A21DBD")
    val controlPointUuid: UUID = UUID.fromString("69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9")
    val dataSourceUuid: UUID = UUID.fromString("22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB")
    val clientCharacteristicConfigUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    const val commandGetNotificationAttributes = 0x00
    const val commandPerformNotificationAction = 0x02

    val defaultRequestedAttributes: List<NotificationAttributeId> = listOf(
        NotificationAttributeId.AppIdentifier,
        NotificationAttributeId.Title,
        NotificationAttributeId.Subtitle,
        NotificationAttributeId.Message,
        NotificationAttributeId.Date,
        NotificationAttributeId.PositiveActionLabel,
        NotificationAttributeId.NegativeActionLabel,
    )
}

fun buildGetNotificationAttributesCommand(
    notificationUid: Long,
    requestedAttributes: List<NotificationAttributeId> = AncsBluetoothConstants.defaultRequestedAttributes,
): ByteArray {
    val payload = ArrayList<Byte>()
    payload += AncsBluetoothConstants.commandGetNotificationAttributes.toByte()
    payload += notificationUid.toInt().toLeByteArray().toList()

    requestedAttributes.forEach { attribute ->
        payload += attribute.rawValue.toByte()
        attribute.maxLength?.let { maxLength ->
            payload += maxLength.toLeShortByteArray().toList()
        }
    }
    return payload.toByteArray()
}

fun buildPerformNotificationActionCommand(
    notificationUid: Long,
    action: NotificationAction,
): ByteArray = byteArrayOf(
    AncsBluetoothConstants.commandPerformNotificationAction.toByte(),
    *notificationUid.toInt().toLeByteArray(),
    action.rawValue.toByte(),
)

