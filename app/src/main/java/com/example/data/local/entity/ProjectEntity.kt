package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val packageName: String,
    val templateType: String,
    val rootPath: String,
    val language: String, // "Kotlin", "Java"
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val gitRepoUrl: String? = null,
    val gitBranch: String = "main"
)
