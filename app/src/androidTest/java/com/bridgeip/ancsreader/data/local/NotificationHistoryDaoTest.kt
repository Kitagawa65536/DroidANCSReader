package com.bridgeip.ancsreader.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationHistoryDaoTest {
    private lateinit var database: AncsDatabase
    private lateinit var dao: NotificationHistoryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AncsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.notificationHistoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deleteRemovedOnSource_removesOnlyMarkedRows() = runBlocking {
        dao.upsert(entity(notificationUid = 1L, removedOnSource = false, lastUpdatedMillis = 1_000L))
        dao.upsert(entity(notificationUid = 2L, removedOnSource = true, lastUpdatedMillis = 2_000L))
        dao.upsert(entity(notificationUid = 3L, removedOnSource = true, lastUpdatedMillis = 3_000L))

        dao.deleteRemovedOnSource()

        val notifications = dao.observeNotifications().first()
        assertEquals(1, notifications.size)
        assertEquals(1L, notifications.single().notificationUid)
        assertFalse(notifications.single().removedOnSource)
    }

    @Test
    fun markRemoved_setsRemovedFlagWithoutDeletingRow() = runBlocking {
        dao.upsert(entity(notificationUid = 10L, removedOnSource = false, lastUpdatedMillis = 1_000L))

        dao.markRemoved(notificationUid = 10L, updatedAtMillis = 5_000L)

        val notifications = dao.observeNotifications().first()
        assertEquals(1, notifications.size)
        assertTrue(notifications.single().removedOnSource)
        assertEquals(5_000L, notifications.single().lastUpdatedMillis)
    }

    private fun entity(
        notificationUid: Long,
        removedOnSource: Boolean,
        lastUpdatedMillis: Long,
    ): NotificationHistoryEntity = NotificationHistoryEntity(
        notificationUid = notificationUid,
        categoryRawValue = 0,
        categoryCount = 1,
        title = "Title $notificationUid",
        subtitle = "",
        message = "",
        appIdentifier = "app.test",
        dateText = "",
        positiveActionLabel = null,
        negativeActionLabel = null,
        silent = false,
        important = false,
        preExisting = false,
        positiveAction = false,
        negativeAction = false,
        receivedAtMillis = lastUpdatedMillis - 100L,
        lastUpdatedMillis = lastUpdatedMillis,
        removedOnSource = removedOnSource,
    )
}
