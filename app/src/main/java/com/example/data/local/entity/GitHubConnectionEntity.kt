package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "github_connection")
data class GitHubConnectionEntity(
    @PrimaryKey val id: String = "primary_account",
    val username: String,
    val token: String,
    val avatarUrl: String? = null,
    val email: String? = null,
    val connectedAt: Long = System.currentTimeMillis(),
    val scopes: String = "repo,workflow",
    val activeRepo: String? = null,
    val isConnected: Boolean = true
)
