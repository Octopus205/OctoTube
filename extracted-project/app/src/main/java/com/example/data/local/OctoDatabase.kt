package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SubscriptionEntity::class,
        WatchHistoryEntity::class,
        DownloadedMediaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class OctoDatabase : RoomDatabase() {
    abstract fun octoDao(): OctoDao

    companion object {
        @Volatile
        private var instance: OctoDatabase? = null

        fun getInstance(context: Context): OctoDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OctoDatabase::class.java,
                    "octopipe_local.db"
                ).build().also { instance = it }
            }
        }
    }
}
