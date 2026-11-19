package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.GitHubConnectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GitHubConnectionDao {
    @Query("SELECT * FROM github_connection WHERE id = 'primary_account' LIMIT 1")
    fun getConnectionFlow(): Flow<GitHubConnectionEntity?>

    @Query("SELECT * FROM github_connection WHERE id = 'primary_account' LIMIT 1")
    suspend fun getConnection(): GitHubConnectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConnection(connection: GitHubConnectionEntity)

    @Query("DELETE FROM github_connection WHERE id = 'primary_account'")
    suspend fun clearConnection()
}
