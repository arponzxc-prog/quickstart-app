package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val message: String,
    val type: String, // "BUILD_STARTED", "BUILD_SUCCESSFUL", "BUILD_FAILED", "DOWNLOAD_SUCCESSFUL", "GITHUB_STATUS", "STORAGE_WARNING"
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val relatedId: String? = null
)
