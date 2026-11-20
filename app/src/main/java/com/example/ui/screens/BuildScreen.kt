package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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

    // Build Configuration States
    var selectedBuildType by remember { mutableStateOf("DEBUG") } // DEBUG, RELEASE, BUNDLE
    var selectedModule by remember { mutableStateOf(":app") }
    var selectedJdk by remember { mutableStateOf("17") }
    var selectedGradle by remember { mutableStateOf("8.10.2") }
    var selectedCompileSdk by remember { mutableStateOf("35") }
    var cleanBuild by remember { mutableStateOf(true) }

    // Editable project configuration for build
    var customPackageName by remember(selectedProject) { mutableStateOf(selectedProject?.packageName ?: "com.example.app") }
    var customVersionName by remember { mutableStateOf("1.0.0") }
    var customVersionCode by remember { mutableStateOf("1") }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showSigningSecretDialog by remember { mutableStateOf(false) }

    // Log Filter
    var logFilter by remember { mutableStateOf("ALL") } // ALL, ERRORS, TASKS
    var logSearchQuery by remember { mutableStateOf("") }

    var projectDropdownExpanded by remember { mutableStateOf(false) }
    var showMissingGitHubDialog by remember { mutableStateOf(false) }

    val logListState = rememberLazyListState()

    val hasGitHubConnected = githubConnection != null &&
            githubConnection.token.isNotBlank() &&
            !githubConnection.activeRepo.isNullOrBlank()

    val activeState = buildState as? BuildProgressState.Active
    val isBuilding = activeState != null

    // Auto-scroll logs when updated
    LaunchedEffect(activeState?.liveLogs) {
        if (activeState?.liveLogs?.isNotEmpty() == true) {
            logListState.animateScrollToItem((logListState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
            .testTag("build_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Build Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
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
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Production CI/CD powered by GitHub Actions",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonCyan
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
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedProject?.let { "${it.name} (${it.language})" } ?: "Select Project to Build",
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        DropdownMenu(
                            expanded = projectDropdownExpanded,
                            onDismissRequest = { projectDropdownExpanded = false }
                        ) {
                            projects.forEach { proj ->
                                DropdownMenuItem(
                                    text = { Text("${proj.name} • ${proj.packageName}") },
                                    onClick = {
                                        onSelectProject(proj)
                                        customPackageName = proj.packageName
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
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AmberYellow, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GitHub Token & Repository Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AmberYellow
                            )
                        }

                        Text(
                            text = "Nexa IDE compiles real Android APKs using GitHub Actions runners. Without connecting your GitHub Personal Access Token (PAT) and selecting an active repository, APK builds cannot be created.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
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
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Build Configuration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        TextButton(onClick = { showConfigDialog = true }) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp), tint = NeonCyan)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Customize", fontSize = 12.sp, color = NeonCyan)
                        }
                    }

                    // Build Variant selection chips
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
                            label = { Text("App Bundle (AAB)") }
                        )
                    }

                    // Package metadata summary pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = Slate800,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Module", fontSize = 10.sp, color = Color.Gray)
                                Text(selectedModule, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        Surface(
                            color = Slate800,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Package ID", fontSize = 10.sp, color = Color.Gray)
                                Text(customPackageName.takeLast(16), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonCyan, maxLines = 1)
                            }
                        }
                        Surface(
                            color = Slate800,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Version", fontSize = 10.sp, color = Color.Gray)
                                Text("v$customVersionName ($customVersionCode)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                            }
                        }
                    }

                    // SDK & Environment Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("JDK Runtime", fontSize = 11.sp, color = Color.Gray)
                            Text("OpenJDK $selectedJdk", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White)
                        }
                        Column {
                            Text("Gradle Engine", fontSize = 11.sp, color = Color.Gray)
                            Text("v$selectedGradle", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White)
                        }
                        Column {
                            Text("Compile SDK", fontSize = 11.sp, color = Color.Gray)
                            Text("API $selectedCompileSdk", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White)
                        }
                    }

                    // Signing status & explanation
                    Surface(
                        color = Slate950,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSigningSecretDialog = true }
                            .padding(vertical = 4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (selectedBuildType == "DEBUG") Icons.Default.VerifiedUser else Icons.Default.Key,
                                contentDescription = null,
                                tint = if (selectedBuildType == "DEBUG") EmeraldGreen else AmberYellow,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (selectedBuildType == "DEBUG") "Signed with Debug Keystore (Ready for device testing)"
                                    else "Release Signing: Uses repo secrets or local fallback key",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Tap to view production signing secret instructions",
                                    fontSize = 10.sp,
                                    color = NeonCyan
                                )
                            }
                        }
                    }

                    // Clean Project Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Clean project before build (Recommended)",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                        Switch(
                            checked = cleanBuild,
                            onCheckedChange = { cleanBuild = it }
                        )
                    }

                    // Build Trigger Action
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
                            containerColor = if (hasGitHubConnected) NeonPurple else AmberYellow,
                            contentColor = if (hasGitHubConnected) Color.White else Color.Black
                        )
                    ) {
                        if (isBuilding) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Building on GitHub Actions...")
                        } else if (!hasGitHubConnected) {
                            Icon(Icons.Default.Key, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connect GitHub Account to Build", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("START $selectedBuildType BUILD", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Active Build Progress Card (Real-time Timeline & Actions)
        activeState?.let { active ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                    text = "Status: ${active.status}",
                                    fontSize = 11.sp,
                                    color = NeonCyan
                                )
                            }

                            Row {
                                active.runHtmlUrl?.let { url ->
                                    IconButton(onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        context.startActivity(intent)
                                    }) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = "View Run", tint = NeonCyan)
                                    }
                                }
                                IconButton(onClick = onCancelBuild) {
                                    Icon(Icons.Default.Cancel, contentDescription = "Cancel Build", tint = CoralRed)
                                }
                            }
                        }

                        // Granular Step Indicator
                        Text(
                            text = "Current Step: ${active.currentStep}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )

                        LinearProgressIndicator(
                            progress = { active.progressPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = NeonCyan,
                            trackColor = Slate800
                        )

                        Text(
                            text = "${(active.progressPercent * 100).toInt()}% completed",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }

        // Live Compiler Logs Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compiler Logs", fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = {
                                val logsToCopy = activeState?.liveLogs ?: latestBuild?.logs ?: ""
                                clipboardManager.setText(AnnotatedString(logsToCopy))
                                Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Logs", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val rawLogs = activeState?.liveLogs ?: latestBuild?.logs ?: "No recent build logs available."
                    val filteredLogs = remember(rawLogs, logFilter) {
                        val lines = rawLogs.lines()
                        when (logFilter) {
                            "ERRORS" -> lines.filter { it.contains("error", ignoreCase = true) || it.contains("FAILED") || it.contains("e: ") }.joinToString("\n")
                            "TASKS" -> lines.filter { it.contains("> Task") || it.contains("BUILD") }.joinToString("\n")
                            else -> rawLogs
                        }
                    }

                    // Filter chips
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = logFilter == "ALL",
                            onClick = { logFilter = "ALL" },
                            label = { Text("All", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = logFilter == "ERRORS",
                            onClick = { logFilter = "ERRORS" },
                            label = { Text("Errors Only", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = logFilter == "TASKS",
                            onClick = { logFilter = "TASKS" },
                            label = { Text("Gradle Tasks", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        color = Color(0xFF0d1117),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    ) {
                        val horizScroll = rememberScrollState()
                        LazyColumn(
                            state = logListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(horizScroll)
                                .padding(10.dp)
                        ) {
                            item {
                                Text(
                                    text = filteredLogs.ifBlank { "No logs match the current filter." },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFFc9d1d9),
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Latest Verified Artifact Card (with Integrity Check & Signing Status)
        latestArtifact?.let { artifact ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Verified Artifact Ready", fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(
                                        text = "${artifact.fileName} (${String.format(Locale.US, "%.2f MB", artifact.fileSizeBytes / (1024.0 * 1024.0))})",
                                        fontSize = 11.sp,
                                        color = EmeraldGreen
                                    )
                                }
                            }
                            Surface(
                                color = EmeraldGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "ZIP VERIFIED",
                                    color = EmeraldGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = "Package: ${artifact.packageName} • v${artifact.versionName}",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onInstallApk(artifact.localFilePath) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Install APK", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { onShareApk(artifact.localFilePath) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share")
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog: Customize Build Config (Package Name, Version Name, Code)
    if (showConfigDialog) {
        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = { Text("Customize Build Properties", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customPackageName,
                        onValueChange = { customPackageName = it.trim() },
                        label = { Text("Package ID / Application ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customVersionName,
                        onValueChange = { customVersionName = it.trim() },
                        label = { Text("Version Name (e.g. 1.0.0)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customVersionCode,
                        onValueChange = { customVersionCode = it.filter { c -> c.isDigit() } },
                        label = { Text("Version Code (e.g. 1)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showConfigDialog = false }) { Text("Done") }
            }
        )
    }

    // Modal Dialog: Signing Secrets Explanation
    if (showSigningSecretDialog) {
        AlertDialog(
            onDismissRequest = { showSigningSecretDialog = false },
            title = { Text("Play Store Release Signing", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "To produce production-ready APKs signed with your permanent key instead of a development key, configure these secrets in your GitHub Repository settings (Settings > Secrets and variables > Actions):",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                    Surface(
                        color = Color(0xFF0d1117),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "• RELEASE_KEYSTORE_BASE64: base64 of your .jks\n" +
                                    "• STORE_PASSWORD: keystore password\n" +
                                    "• KEY_ALIAS: alias name (e.g. upload)\n" +
                                    "• KEY_PASSWORD: key password",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = NeonCyan,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Text(
                        text = "If these secrets are not configured, Nexa IDE automatically signs using an authenticated local keystore so your builds always succeed for testing.",
                        fontSize = 11.sp,
                        color = EmeraldGreen
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showSigningSecretDialog = false }) { Text("Got It") }
            }
        )
    }
}
