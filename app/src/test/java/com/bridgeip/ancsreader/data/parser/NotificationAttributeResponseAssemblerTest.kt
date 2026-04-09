package com.bridgeip.ancsreader.data.parser

import com.bridgeip.ancsreader.data.model.NotificationAttributeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationAttributeResponseAssemblerTest {
    private val assembler = NotificationAttributeResponseAssembler()

    @Test
    fun append_fragments_reassemblesNotificationAttributes() {
        assembler.start(
            uid = 0x12345678,
            requestedAttributes = listOf(
                NotificationAttributeId.Title,
                NotificationAttributeId.Message,
            ),
        )

        val firstChunk = byteArrayOf(
            0x00,
            0x78,
            0x56,
            0x34,
            0x12,
            0x01,
            0x05,
            0x00,
            'H'.code.toByte(),
            'e'.code.toByte(),
        )
        val secondChunk = byteArrayOf(
            'l'.code.toByte(),
            'l'.code.toByte(),
            'o'.code.toByte(),
            0x03,
            0x05,
            0x00,
            'W'.code.toByte(),
            'o'.code.toByte(),
            'r'.code.toByte(),
            'l'.code.toByte(),
            'd'.code.toByte(),
        )

        assertNull(assembler.append(firstChunk))
        val response = assembler.append(secondChunk)

        requireNotNull(response)
        assertEquals("Hello", response.attributes[NotificationAttributeId.Title])
        assertEquals("World", response.attributes[NotificationAttributeId.Message])
    }
}

