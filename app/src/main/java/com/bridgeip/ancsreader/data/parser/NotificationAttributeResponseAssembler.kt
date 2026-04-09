package com.bridgeip.ancsreader.data.parser

import com.bridgeip.ancsreader.bluetooth.AncsBluetoothConstants
import com.bridgeip.ancsreader.data.model.NotificationAttributeId
import com.bridgeip.ancsreader.data.model.NotificationAttributeResponse
import com.bridgeip.ancsreader.util.readUInt16Le
import com.bridgeip.ancsreader.util.readUInt32Le
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

class NotificationAttributeResponseAssembler(
    private val charset: Charset = Charsets.UTF_8,
) {
    private var activeUid: Long? = null
    private var expectedAttributes: List<NotificationAttributeId> = emptyList()
    private val buffer = ByteArrayOutputStream()

    fun start(uid: Long, requestedAttributes: List<NotificationAttributeId>) {
        activeUid = uid
        expectedAttributes = requestedAttributes
        buffer.reset()
    }

    fun append(chunk: ByteArray): NotificationAttributeResponse? {
        val currentUid = activeUid ?: return null
        if (chunk.isEmpty()) {
            return null
        }

        buffer.write(chunk)
        val bytes = buffer.toByteArray()
        if (bytes.size < 5) {
            return null
        }
        if (bytes[0].toInt() and 0xFF != AncsBluetoothConstants.commandGetNotificationAttributes) {
            return null
        }
        if (bytes.readUInt32Le(1) != currentUid) {
            return null
        }

        var offset = 5
        val parsed = linkedMapOf<NotificationAttributeId, String>()
        for (expected in expectedAttributes) {
            if (offset + 3 > bytes.size) {
                return null
            }
            val attributeId = NotificationAttributeId.fromRaw(bytes[offset].toInt() and 0xFF) ?: return null
            val length = bytes.readUInt16Le(offset + 1)
            if (offset + 3 + length > bytes.size) {
                return null
            }
            val value = bytes.copyOfRange(offset + 3, offset + 3 + length).toString(charset)
            parsed[attributeId] = value
            offset += 3 + length
        }

        if (parsed.keys.containsAll(expectedAttributes)) {
            reset()
            return NotificationAttributeResponse(
                notificationUid = currentUid,
                attributes = parsed,
            )
        }
        return null
    }

    fun reset() {
        activeUid = null
        expectedAttributes = emptyList()
        buffer.reset()
    }
}

