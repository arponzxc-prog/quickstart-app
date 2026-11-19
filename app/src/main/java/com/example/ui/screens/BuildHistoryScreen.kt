package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.BuildEntity
import com.example.ui.components.StatusChip
import com.example.ui.theme.CoralRed
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildHistoryScreen(
    builds: List<BuildEntity>,
    onBack: () -> Unit,
    onDeleteBuild: (String) -> Unit,
    onClearAllBuilds: () -> Unit,
    onInstallApk: (String) -> Unit,
    onShareApk: (String) -> Unit,
    onRebuild: (BuildEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedBuildForLogs by remember { mutableStateOf<BuildEntity?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm:ss", Locale.getDefault())

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("build_history_screen")
    ) {
        TopAppBar(
            title = { Text("Build History", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (builds.isNotEmpty()) {
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear History", tint = CoralRed)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        if (builds.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Build History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "When you build projects, full logs and artifacts will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(builds) { build ->
                    BuildHistoryCard(
                        build = build,
                        formattedDate = dateFormat.format(Date(build.startedAt)),
                        onViewLogs = { selectedBuildForLogs = build },
                        onDelete = { onDeleteBuild(build.id) },
                        onInstall = { build.localArtifactPath?.let(onInstallApk) },
                        onShare = { build.localArtifactPath?.let(onShareApk) },
                        onRebuild = { onRebuild(build) }
                    )
                }
            }
        }
    }

    // Modal: Full Log Viewer
    selectedBuildForLogs?.let { build ->
        AlertDialog(
            onDismissRequest = { selectedBuildForLogs = null },
            title = {
                Text(
                    text = "${build.projectName} #${build.buildNumber} Logs",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                        .background(Slate950, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    LazyColumn {
                        item {
                            Text(
                                text = build.logs.ifBlank { "No log output recorded." },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color.LightGray,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedBuildForLogs = null }) { Text("Close") }
            }
        )
    }

    // Modal: Confirm Clear History
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear All History?") },
            text = { Text("This will remove all past build records. Generated APK files in your Downloads folder will remain intact.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllBuilds()
                        showClearConfirm = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CoralRed)
                ) { Text("Clear All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BuildHistoryCard(
    build: BuildEntity,
    formattedDate: String,
    onViewLogs: () -> Unit,
    onDelete: () -> Unit,
    onInstall: () -> Unit,
    onShare: () -> Unit,
    onRebuild: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${build.projectName} #${build.buildNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                StatusChip(status = build.status)
            }

            Text(
                text = "Type: ${build.buildType} • Duration: ${build.durationSeconds}s • $formattedDate",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            build.artifactName?.let { name ->
                Text(
                    text = "Artifact: $name" + (build.artifactSize?.let { " (${it / 1024} KB)" } ?: ""),
                    fontSize = 11.sp,
                    color = EmeraldGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onViewLogs) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Logs", fontSize = 12.sp)
                    }
                    TextButton(onClick = onRebuild) {
                        Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rebuild", fontSize = 12.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (build.localArtifactPath != null) {
                        IconButton(onClick = onInstall, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Install", tint = EmeraldGreen)
                        }
                        IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = NeonCyan)
                        }
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                    }
                }
            }
        }
    }
}
