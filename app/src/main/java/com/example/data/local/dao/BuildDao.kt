package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.BuildEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildDao {
    @Query("SELECT * FROM builds ORDER BY startedAt DESC")
    fun getAllBuilds(): Flow<List<BuildEntity>>

    @Query("SELECT * FROM builds WHERE projectId = :projectId ORDER BY startedAt DESC")
    fun getBuildsForProject(projectId: String): Flow<List<BuildEntity>>

    @Query("SELECT * FROM builds WHERE id = :id LIMIT 1")
    suspend fun getBuildById(id: String): BuildEntity?

    @Query("SELECT * FROM builds WHERE id = :id LIMIT 1")
    fun getBuildByIdFlow(id: String): Flow<BuildEntity?>

    @Query("SELECT COUNT(*) FROM builds")
    fun getTotalBuildsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM builds WHERE status = 'SUCCESSFUL'")
    fun getSuccessfulBuildsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM builds WHERE status = 'FAILED'")
    fun getFailedBuildsCount(): Flow<Int>

    @Query("SELECT MAX(buildNumber) FROM builds")
    suspend fun getMaxBuildNumber(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuild(build: BuildEntity)

    @Update
    suspend fun updateBuild(build: BuildEntity)

    @Query("DELETE FROM builds WHERE id = :id")
    suspend fun deleteBuildById(id: String)

    @Query("DELETE FROM builds")
    suspend fun clearAllBuilds()
}
