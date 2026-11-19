package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey val id: String, // composite or uuid
    val projectId: String,
    val filePath: String,
    val fileName: String,
    val lastOpenedAt: Long = System.currentTimeMillis()
)
