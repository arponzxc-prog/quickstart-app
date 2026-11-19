package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "builds")
data class BuildEntity(
    @PrimaryKey val id: String, // UUID
    val buildNumber: Int,
    val projectId: String,
    val projectName: String,
    val buildType: String, // "DEBUG", "RELEASE", "BUNDLE"
    val status: String, // "PENDING", "QUEUED", "RUNNING", "SUCCESSFUL", "FAILED", "CANCELLED", "DOWNLOADING", "DOWNLOADED"
    val logs: String = "",
    val durationSeconds: Long = 0L,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val artifactName: String? = null,
    val artifactSize: Long? = null,
    val localArtifactPath: String? = null,
    val errorMessage: String? = null,
    val gitCommitHash: String? = null,
    val githubRunId: Long? = null
)
