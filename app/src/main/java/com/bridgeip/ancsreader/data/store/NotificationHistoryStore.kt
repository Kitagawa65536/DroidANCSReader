package com.bridgeip.ancsreader.data.store

import android.content.Context
import com.bridgeip.ancsreader.data.local.AncsDatabase
import com.bridgeip.ancsreader.data.local.NotificationHistoryEntity
import com.bridgeip.ancsreader.data.model.AncsNotification
import com.bridgeip.ancsreader.data.model.NotificationCategory
import com.bridgeip.ancsreader.data.model.NotificationEventFlags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class NotificationHistoryStore(
    context: Context,
    private val maxEntries: Int = 300,
) {
    private val dao = AncsDatabase.getInstance(context).notificationHistoryDao()
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _notifications = MutableStateFlow(emptyList<AncsNotification>())
    val notifications: StateFlow<List<AncsNotification>> = _notifications.asStateFlow()

    init {
        scope.launch {
            migrateLegacyPreferencesIfNeeded()
            dao.observeNotifications().collectLatest { entities ->
                _notifications.value = entities.map { it.toModel() }
            }
        }
    }

    fun upsert(notification: AncsNotification) {
        val allNotifications = (_notifications.value.filterNot { it.notificationUid == notification.notificationUid } + notification)
            .sortedByDescending { it.lastUpdatedMillis }
        val updated = allNotifications.take(maxEntries)
        _notifications.value = updated
        scope.launch {
            dao.upsert(notification.toEntity())
            trimToMaxEntries(allNotifications)
        }
    }

    fun markRemoved(notificationUid: Long) {
        val updatedAtMillis = System.currentTimeMillis()
        _notifications.value = _notifications.value.map { notification ->
            if (notification.notificationUid == notificationUid) {
                notification.copy(
                    removedOnSource = true,
                    lastUpdatedMillis = updatedAtMillis,
                )
            } else {
                notification
            }
        }
        scope.launch {
            dao.markRemoved(notificationUid, updatedAtMillis)
        }
    }

    fun delete(notificationUid: Long) {
        _notifications.value = _notifications.value.filterNot { it.notificationUid == notificationUid }
        scope.launch {
            dao.delete(notificationUid)
        }
    }

    fun clear() {
        _notifications.value = emptyList()
        scope.launch {
            dao.clear()
        }
    }

    private suspend fun migrateLegacyPreferencesIfNeeded() {
        if (dao.count() > 0) {
            return
        }
        loadLegacyNotifications()
            .sortedByDescending { it.lastUpdatedMillis }
            .take(maxEntries)
            .forEach { notification ->
                dao.upsert(notification.toEntity())
            }
        preferences.edit().clear().apply()
    }

    private suspend fun trimToMaxEntries(notifications: List<AncsNotification>) {
        notifications
            .drop(maxEntries)
            .forEach { notification ->
                dao.delete(notification.notificationUid)
            }
    }

    private fun loadLegacyNotifications(): List<AncsNotification> {
        val raw = preferences.getString(KEY_HISTORY_JSON, null).orEmpty()
        if (raw.isBlank()) {
            return emptyList()
        }
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                add(array.getJSONObject(index).toAncsNotification())
            }
        }
    }

    private fun AncsNotification.toEntity(): NotificationHistoryEntity = NotificationHistoryEntity(
        notificationUid = notificationUid,
        categoryRawValue = category.rawValue,
        categoryCount = categoryCount,
        title = title,
        subtitle = subtitle,
        message = message,
        appIdentifier = appIdentifier,
        dateText = dateText,
        positiveActionLabel = positiveActionLabel,
        negativeActionLabel = negativeActionLabel,
        silent = flags.silent,
        important = flags.important,
        preExisting = flags.preExisting,
        positiveAction = flags.positiveAction,
        negativeAction = flags.negativeAction,
        receivedAtMillis = receivedAtMillis,
        lastUpdatedMillis = lastUpdatedMillis,
        removedOnSource = removedOnSource,
    )

    private fun NotificationHistoryEntity.toModel(): AncsNotification = AncsNotification(
        notificationUid = notificationUid,
        category = NotificationCategory.fromRaw(categoryRawValue),
        categoryCount = categoryCount,
        title = title,
        subtitle = subtitle,
        message = message,
        appIdentifier = appIdentifier,
        dateText = dateText,
        positiveActionLabel = positiveActionLabel,
        negativeActionLabel = negativeActionLabel,
        flags = NotificationEventFlags(
            silent = silent,
            important = important,
            preExisting = preExisting,
            positiveAction = positiveAction,
            negativeAction = negativeAction,
        ),
        receivedAtMillis = receivedAtMillis,
        lastUpdatedMillis = lastUpdatedMillis,
        removedOnSource = removedOnSource,
    )

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
