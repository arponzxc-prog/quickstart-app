package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.builder.BuildProgressState
import com.example.data.local.entity.BuildArtifactEntity
import com.example.data.local.entity.BuildEntity
import com.example.data.local.entity.GitHubConnectionEntity
import com.example.data.local.entity.ProjectEntity
import com.example.ui.components.StatusChip
import com.example.ui.theme.AmberYellow
import com.example.ui.theme.CoralRed
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

@Composable
fun BuildScreen(
    projects: List<ProjectEntity>,
    selectedProject: ProjectEntity?,
    onSelectProject: (ProjectEntity) -> Unit,
    buildState: BuildProgressState,
    latestBuild: BuildEntity?,
    latestArtifact: BuildArtifactEntity?,
    githubConnection: GitHubConnectionEntity?,
    onStartBuild: (project: ProjectEntity, buildType: String, jdk: String, gradle: String, compileSdk: String, clean: Boolean) -> Unit,
    onCancelBuild: () -> Unit,
    onInstallApk: (String) -> Unit,
    onShareApk: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToGitHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var selectedBuildType by remember { mutableStateOf("DEBUG") } // DEBUG, RELEASE, BUNDLE
    var selectedJdk by remember { mutableStateOf("17") }
    var selectedGradle by remember { mutableStateOf("8.7") }
    var selectedCompileSdk by remember { mutableStateOf("35") }
    var cleanBuild by remember { mutableStateOf(true) }

    var projectDropdownExpanded by remember { mutableStateOf(false) }
    var showMissingGitHubDialog by remember { mutableStateOf(false) }

    val logListState = rememberLazyListState()

    val hasGitHubConnected = githubConnection != null &&
            githubConnection.token.isNotBlank() &&
            !githubConnection.activeRepo.isNullOrBlank()

    // Auto-scroll logs when updated
    val activeState = buildState as? BuildProgressState.Active
    LaunchedEffect(activeState?.liveLogs) {
        if (activeState?.liveLogs?.isNotEmpty() == true) {
            logListState.animateScrollToItem((logListState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("build_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Build Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Cloud APK/AAB Builder",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Powered by GitHub Actions CI/CD",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = onNavigateToHistory) {
                            Icon(Icons.Default.History, contentDescription = "Build History", tint = NeonCyan)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Project Selector Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { projectDropdownExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("select_project_dropdown"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedProject?.name ?: "Select Project to Build",
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        DropdownMenu(
                            expanded = projectDropdownExpanded,
                            onDismissRequest = { projectDropdownExpanded = false }
                        ) {
                            projects.forEach { proj ->
                                DropdownMenuItem(
                                    text = { Text("${proj.name} (${proj.packageName})") },
                                    onClick = {
                                        onSelectProject(proj)
                                        projectDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // GitHub Connection Requirement Banner (if not configured)
        if (!hasGitHubConnected) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AmberYellow.copy(alpha = 0.12f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberYellow.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = AmberYellow,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GitHub Token & Repository Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AmberYellow
                            )
                        }

                        Text(
                            text = "Nexa IDE compiles real Android APKs using GitHub Actions runners. Without adding your GitHub Personal Access Token (PAT) and selecting an active repository, APK builds cannot be created.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )

                        Button(
                            onClick = onNavigateToGitHub,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberYellow, contentColor = Color.Black),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Connect GitHub Account", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Build Configuration Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Build Variant",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedBuildType == "DEBUG",
                            onClick = { selectedBuildType = "DEBUG" },
                            label = { Text("Debug APK") }
                        )
                        FilterChip(
                            selected = selectedBuildType == "RELEASE",
                            onClick = { selectedBuildType = "RELEASE" },
                            label = { Text("Release APK") }
                        )
                        FilterChip(
                            selected = selectedBuildType == "BUNDLE",
                            onClick = { selectedBuildType = "BUNDLE" },
                            label = { Text("Android Bundle (AAB)") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Clean Project Before Build",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = cleanBuild,
                            onCheckedChange = { cleanBuild = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("JDK Version", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("OpenJDK $selectedJdk", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Gradle", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("v$selectedGradle", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Compile SDK", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("API $selectedCompileSdk", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }

                    // Build Trigger Action
                    val isBuilding = activeState != null
                    Button(
                        onClick = {
                            if (!hasGitHubConnected) {
                                showMissingGitHubDialog = true
                            } else {
                                selectedProject?.let { proj ->
                                    onStartBuild(proj, selectedBuildType, selectedJdk, selectedGradle, selectedCompileSdk, cleanBuild)
                                }
                            }
                        },
                        enabled = selectedProject != null && !isBuilding,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("start_build_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasGitHubConnected) MaterialTheme.colorScheme.primary else AmberYellow,
                            contentColor = if (hasGitHubConnected) MaterialTheme.colorScheme.onPrimary else Color.Black
                        )
                    ) {
                        if (isBuilding) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Building on GitHub Actions...")
                        } else if (!hasGitHubConnected) {
                            Icon(Icons.Default.Key, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connect GitHub Token to Build", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("START $selectedBuildType BUILD", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Active Build Progress Card (if running)
        activeState?.let { active ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Building: ${active.projectName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = active.currentStep,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NeonCyan
                                )
                            }
                            IconButton(onClick = onCancelBuild) {
                                Icon(Icons.Default.Cancel, contentDescription = "Cancel Build", tint = CoralRed)
                            }
                        }

                        LinearProgressIndicator(
                            progress = { active.progressPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = NeonCyan,
                            trackColor = Slate800,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Status: ${active.status}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = "${(active.progressPercent * 100).toInt()}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                        }
                    }
                }
            }
        }

        // Completed Artifact Card (if available)
        if (latestArtifact != null && activeState == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = EmeraldGreen.copy(alpha = 0.1f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Real Artifact Ready!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldGreen
                                )
                                Text(
                                    text = "${latestArtifact.fileName} (${String.format(java.util.Locale.US, "%.1f MB", latestArtifact.fileSizeBytes / (1024.0 * 1024.0))})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusChip(status = "VERIFIED")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onInstallApk(latestArtifact.localFilePath) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("install_apk_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Install APK", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { onShareApk(latestArtifact.localFilePath) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share APK")
                            }
                        }
                    }
                }
            }
        }

        // Failure Assistance Card (if last build failed)
        if (latestBuild?.status == "FAILED" && activeState == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CoralRed.copy(alpha = 0.12f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CoralRed.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Cancel, contentDescription = null, tint = CoralRed, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Build Failed on GitHub Actions",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = CoralRed
                                )
                                Text(
                                    text = latestBuild.errorMessage ?: "Check compiler logs on GitHub to see exact errors.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val githubUrl = latestBuild.githubRunId?.let { "https://github.com/${githubConnection?.activeRepo}/actions/runs/$it" }
                                ?: (if (!githubConnection?.activeRepo.isNullOrBlank()) "https://github.com/${githubConnection?.activeRepo}/actions" else null)
                            if (githubUrl != null) {
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Cannot open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open GitHub Logs", fontSize = 12.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = onNavigateToGitHub,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("GitHub Settings", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Live Build Console Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate950),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Build Console",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val runUrl = activeState?.runHtmlUrl ?: (if (!githubConnection?.activeRepo.isNullOrBlank()) "https://github.com/${githubConnection?.activeRepo}/actions" else null)
                            if (runUrl != null) {
                                IconButton(onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(runUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = "View GitHub Actions", tint = NeonCyan, modifier = Modifier.size(18.dp))
                                }
                            }

                            val logs = activeState?.liveLogs ?: latestBuild?.logs ?: "No build executed yet."
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(logs))
                                Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Logs", tint = Color.Gray, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Slate900, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        val displayLogs = activeState?.liveLogs ?: latestBuild?.logs ?: "Waiting for build trigger...\nReady."
                        LazyColumn(state = logListState) {
                            item {
                                Text(
                                    text = displayLogs,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color.LightGray,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Missing GitHub Token / Repository Alert
    if (showMissingGitHubDialog) {
        AlertDialog(
            onDismissRequest = { showMissingGitHubDialog = false },
            icon = { Icon(Icons.Default.Key, contentDescription = null, tint = AmberYellow) },
            title = { Text("GitHub Token Required", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Nexa IDE does not generate fake or demo apps. Real APK and AAB packages are compiled via GitHub Actions runners in the cloud.\n\n" +
                    "To build your app:\n" +
                    "1. Connect your GitHub Personal Access Token (PAT) with 'repo' and 'workflow' scopes.\n" +
                    "2. Select the repository where your project code is synced.\n" +
                    "3. Tap 'START BUILD' to trigger real GitHub Actions CI.",
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMissingGitHubDialog = false
                        onNavigateToGitHub()
                    }
                ) {
                    Text("Go to GitHub Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMissingGitHubDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
