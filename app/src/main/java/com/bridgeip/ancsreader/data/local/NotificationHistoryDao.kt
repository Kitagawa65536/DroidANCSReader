package com.bridgeip.ancsreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationHistoryDao {
    @Query("SELECT * FROM notification_history ORDER BY lastUpdatedMillis DESC")
    fun observeNotifications(): Flow<List<NotificationHistoryEntity>>

    @Query("SELECT COUNT(*) FROM notification_history")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(notification: NotificationHistoryEntity)

    @Query("UPDATE notification_history SET removedOnSource = 1, lastUpdatedMillis = :updatedAtMillis WHERE notificationUid = :notificationUid")
    suspend fun markRemoved(notificationUid: Long, updatedAtMillis: Long)

    @Query("DELETE FROM notification_history WHERE notificationUid = :notificationUid")
    suspend fun delete(notificationUid: Long)

    @Query("DELETE FROM notification_history")
    suspend fun clear()
}
