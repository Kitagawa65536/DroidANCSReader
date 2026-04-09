package com.bridgeip.ancsreader.data.store

import android.content.Context
import com.bridgeip.ancsreader.data.model.AncsNotification
import com.bridgeip.ancsreader.data.model.NotificationCategory
import com.bridgeip.ancsreader.data.model.NotificationEventFlags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class NotificationHistoryStore(
    context: Context,
    private val maxEntries: Int = 300,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _notifications = MutableStateFlow(load())
    val notifications: StateFlow<List<AncsNotification>> = _notifications.asStateFlow()

    fun upsert(notification: AncsNotification): Boolean {
        val updated = (_notifications.value.filterNot { it.notificationUid == notification.notificationUid } + notification)
            .sortedByDescending { it.lastUpdatedMillis }
            .take(maxEntries)
        return save(updated)
    }

    fun markRemoved(notificationUid: Long): Boolean {
        val updated = _notifications.value.map { notification ->
            if (notification.notificationUid == notificationUid) {
                notification.copy(
                    removedOnSource = true,
                    lastUpdatedMillis = System.currentTimeMillis(),
                )
            } else {
                notification
            }
        }
        return save(updated)
    }

    fun delete(notificationUid: Long): Boolean {
        return save(_notifications.value.filterNot { it.notificationUid == notificationUid })
    }

    fun clear(): Boolean {
        return save(emptyList())
    }

    private fun save(notifications: List<AncsNotification>): Boolean {
        _notifications.value = notifications
        return preferences.edit()
            .putString(KEY_HISTORY_JSON, JSONArray().apply {
                notifications.forEach { put(it.toJson()) }
            }.toString())
            .commit()
    }

    private fun load(): List<AncsNotification> {
        val raw = preferences.getString(KEY_HISTORY_JSON, null).orEmpty()
        if (raw.isBlank()) {
            return emptyList()
        }
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                add(array.getJSONObject(index).toAncsNotification())
            }
        }.sortedByDescending { it.lastUpdatedMillis }
    }

    private fun AncsNotification.toJson(): JSONObject = JSONObject()
        .put("notificationUid", notificationUid)
        .put("category", category.rawValue)
        .put("categoryCount", categoryCount)
        .put("title", title)
        .put("subtitle", subtitle)
        .put("message", message)
        .put("appIdentifier", appIdentifier)
        .put("dateText", dateText)
        .put("positiveActionLabel", positiveActionLabel)
        .put("negativeActionLabel", negativeActionLabel)
        .put("silent", flags.silent)
        .put("important", flags.important)
        .put("preExisting", flags.preExisting)
        .put("positiveAction", flags.positiveAction)
        .put("negativeAction", flags.negativeAction)
        .put("receivedAtMillis", receivedAtMillis)
        .put("lastUpdatedMillis", lastUpdatedMillis)
        .put("removedOnSource", removedOnSource)

    private fun JSONObject.toAncsNotification(): AncsNotification = AncsNotification(
        notificationUid = getLong("notificationUid"),
        category = NotificationCategory.fromRaw(getInt("category")),
        categoryCount = getInt("categoryCount"),
        title = optString("title"),
        subtitle = optString("subtitle"),
        message = optString("message"),
        appIdentifier = optString("appIdentifier"),
        dateText = optString("dateText"),
        positiveActionLabel = optString("positiveActionLabel").ifBlank { null },
        negativeActionLabel = optString("negativeActionLabel").ifBlank { null },
        flags = NotificationEventFlags(
            silent = optBoolean("silent"),
            important = optBoolean("important"),
            preExisting = optBoolean("preExisting"),
            positiveAction = optBoolean("positiveAction"),
            negativeAction = optBoolean("negativeAction"),
        ),
        receivedAtMillis = getLong("receivedAtMillis"),
        lastUpdatedMillis = getLong("lastUpdatedMillis"),
        removedOnSource = optBoolean("removedOnSource"),
    )

    private companion object {
        private const val PREFERENCES_NAME = "ancs_reader_notification_history"
        private const val KEY_HISTORY_JSON = "notification_history_json"
    }
}
