package com.bridgeip.ancsreader.bluetooth

import com.bridgeip.ancsreader.data.model.AncsEvent
import com.bridgeip.ancsreader.data.model.NotificationAction
import com.bridgeip.ancsreader.data.model.NotificationAttributeId
import com.bridgeip.ancsreader.data.parser.NotificationAttributeResponseAssembler
import com.bridgeip.ancsreader.data.parser.NotificationSourceParser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AncsManager(
    private val logger: (String) -> Unit,
) {
    private val notificationSourceParser = NotificationSourceParser()
    private val attributeAssembler = NotificationAttributeResponseAssembler()

    private val _events = MutableSharedFlow<AncsEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<AncsEvent> = _events.asSharedFlow()

    private val pendingRequests = ArrayDeque<PendingAttributeRequest>()
    private var activeRequest: PendingAttributeRequest? = null
    private var controlPointWriter: ((ByteArray) -> Boolean)? = null

    fun attachControlPointWriter(writer: (ByteArray) -> Boolean) {
        controlPointWriter = writer
        _events.tryEmit(AncsEvent.SessionReady)
        drainRequestQueue()
    }

    fun resetSession() {
        controlPointWriter = null
        pendingRequests.clear()
        activeRequest = null
        attributeAssembler.reset()
        _events.tryEmit(AncsEvent.SessionEnded)
    }

    fun onNotificationSourceChanged(payload: ByteArray) {
        val event = notificationSourceParser.parse(payload)
        if (event == null) {
            logger("Unable to parse Notification Source payload (${payload.size} bytes)")
            return
        }
        logger("Notification Source: ${event.eventType} uid=${event.notificationUid} category=${event.category.label}")
        _events.tryEmit(AncsEvent.NotificationChanged(event))
    }

    fun onDataSourceChanged(payload: ByteArray) {
        val response = attributeAssembler.append(payload) ?: return
        logger("Data Source attributes received for uid=${response.notificationUid}")
        activeRequest = null
        _events.tryEmit(AncsEvent.NotificationAttributesReceived(response))
        drainRequestQueue()
    }

    fun requestNotificationDetails(
        notificationUid: Long,
        attributes: List<NotificationAttributeId> = AncsBluetoothConstants.defaultRequestedAttributes,
    ) {
        pendingRequests.addLast(PendingAttributeRequest(notificationUid, attributes))
        drainRequestQueue()
    }

    fun performAction(notificationUid: Long, action: NotificationAction) {
        val payload = buildPerformNotificationActionCommand(notificationUid, action)
        if (controlPointWriter?.invoke(payload) == true) {
            logger("Sent ${action.name} action for uid=$notificationUid")
        } else {
            logger("Skipped ${action.name} action for uid=$notificationUid because Control Point is unavailable")
        }
    }

    private fun drainRequestQueue() {
        if (activeRequest != null) {
            return
        }
        val writer = controlPointWriter ?: return
        val request = pendingRequests.removeFirstOrNull() ?: return
        val payload = buildGetNotificationAttributesCommand(
            notificationUid = request.notificationUid,
            requestedAttributes = request.attributes,
        )
        val started = writer(payload)
        if (!started) {
            logger("Control Point write failed for uid=${request.notificationUid}")
            _events.tryEmit(AncsEvent.Error("Failed to request notification attributes"))
            return
        }
        activeRequest = request
        attributeAssembler.start(request.notificationUid, request.attributes)
        logger("Requested attributes for uid=${request.notificationUid}")
    }

    private data class PendingAttributeRequest(
        val notificationUid: Long,
        val attributes: List<NotificationAttributeId>,
    )
}
