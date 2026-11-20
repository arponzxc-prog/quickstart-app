package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.BuildArtifactDao
import com.example.data.local.dao.BuildDao
import com.example.data.local.dao.GitHubConnectionDao
import com.example.data.local.dao.NotificationDao
import com.example.data.local.dao.ProjectDao
import com.example.data.local.dao.RecentFileDao
import com.example.data.local.entity.BuildArtifactEntity
import com.example.data.local.entity.BuildEntity
import com.example.data.local.entity.GitHubConnectionEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.RecentFileEntity

@Database(
    entities = [
        ProjectEntity::class,
        BuildEntity::class,
        BuildArtifactEntity::class,
        GitHubConnectionEntity::class,
        NotificationEntity::class,
        RecentFileEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NexaDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun buildDao(): BuildDao
    abstract fun buildArtifactDao(): BuildArtifactDao
    abstract fun gitHubConnectionDao(): GitHubConnectionDao
    abstract fun notificationDao(): NotificationDao
    abstract fun recentFileDao(): RecentFileDao

    companion object {
        @Volatile
        private var INSTANCE: NexaDatabase? = null

        fun getInstance(context: Context): NexaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NexaDatabase::class.java,
                    "nexa_ide.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
