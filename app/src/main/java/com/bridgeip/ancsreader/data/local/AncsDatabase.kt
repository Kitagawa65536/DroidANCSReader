package com.bridgeip.ancsreader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AppSettingsEntity::class,
        NotificationHistoryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AncsDatabase : RoomDatabase() {
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun notificationHistoryDao(): NotificationHistoryDao

    companion object {
        @Volatile
        private var instance: AncsDatabase? = null

        fun getInstance(context: Context): AncsDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AncsDatabase::class.java,
                    "ancs_reader.db",
                ).build().also { created ->
                    instance = created
                }
            }
        }
    }
}
