package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.RecentFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {
    @Query("SELECT * FROM recent_files WHERE projectId = :projectId ORDER BY lastOpenedAt DESC LIMIT 10")
    fun getRecentFilesForProject(projectId: String): Flow<List<RecentFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentFile(recentFile: RecentFileEntity)

    @Query("DELETE FROM recent_files WHERE projectId = :projectId")
    suspend fun clearRecentFilesForProject(projectId: String)
}
