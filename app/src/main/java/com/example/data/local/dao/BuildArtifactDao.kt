package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.BuildArtifactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildArtifactDao {
    @Query("SELECT * FROM build_artifacts ORDER BY downloadedAt DESC")
    fun getAllArtifacts(): Flow<List<BuildArtifactEntity>>

    @Query("SELECT * FROM build_artifacts WHERE id = :id LIMIT 1")
    suspend fun getArtifactById(id: String): BuildArtifactEntity?

    @Query("SELECT * FROM build_artifacts WHERE buildId = :buildId LIMIT 1")
    suspend fun getArtifactByBuildId(buildId: String): BuildArtifactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtifact(artifact: BuildArtifactEntity)

    @Delete
    suspend fun deleteArtifact(artifact: BuildArtifactEntity)

    @Query("DELETE FROM build_artifacts WHERE id = :id")
    suspend fun deleteArtifactById(id: String)
}
