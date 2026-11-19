package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "build_artifacts")
data class BuildArtifactEntity(
    @PrimaryKey val id: String,
    val buildId: String,
    val projectId: String,
    val projectName: String,
    val fileName: String,
    val fileType: String, // "APK", "AAB"
    val localFilePath: String,
    val fileSizeBytes: Long,
    val packageName: String,
    val versionName: String = "1.0",
    val versionCode: Int = 1,
    val downloadedAt: Long = System.currentTimeMillis(),
    val isVerified: Boolean = true
)
