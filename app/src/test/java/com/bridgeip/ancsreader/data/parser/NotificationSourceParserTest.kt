package com.bridgeip.ancsreader.data.parser

import com.bridgeip.ancsreader.data.model.NotificationCategory
import com.bridgeip.ancsreader.data.model.NotificationEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSourceParserTest {
    private val parser = NotificationSourceParser()

    @Test
    fun parse_addedNotification_returnsExpectedFields() {
        val packet = byteArrayOf(
            0x00,
            0x0A,
            0x06,
            0x02,
            0x78,
            0x56,
            0x34,
            0x12,
        )

        val event = parser.parse(packet)

        requireNotNull(event)
        assertEquals(NotificationEventType.Added, event.eventType)
        assertEquals(NotificationCategory.Email, event.category)
        assertEquals(2, event.categoryCount)
        assertEquals(0x12345678L, event.notificationUid)
        assertTrue(event.flags.important)
        assertTrue(event.flags.positiveAction)
    }
}

