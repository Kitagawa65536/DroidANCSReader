package com.bridgeip.ancsreader.data.parser

import com.bridgeip.ancsreader.data.model.NotificationCategory
import com.bridgeip.ancsreader.data.model.NotificationEventFlags
import com.bridgeip.ancsreader.data.model.NotificationEventType
import com.bridgeip.ancsreader.data.model.NotificationSourceEvent
import com.bridgeip.ancsreader.util.readUInt32Le
import com.bridgeip.ancsreader.util.readUInt8

class NotificationSourceParser {
    fun parse(packet: ByteArray): NotificationSourceEvent? {
        if (packet.size < 8) {
            return null
        }
        val eventType = NotificationEventType.fromRaw(packet.readUInt8(0)) ?: return null
        return NotificationSourceEvent(
            eventType = eventType,
            flags = NotificationEventFlags.fromBitmask(packet.readUInt8(1)),
            category = NotificationCategory.fromRaw(packet.readUInt8(2)),
            categoryCount = packet.readUInt8(3),
            notificationUid = packet.readUInt32Le(4),
        )
    }
}

