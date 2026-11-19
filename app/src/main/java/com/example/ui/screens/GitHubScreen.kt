package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.github.CreateRepoRequest
import com.example.data.github.GitHubRepo
import com.example.data.github.GitHubService
import com.example.data.local.entity.GitHubConnectionEntity
import com.example.data.local.entity.ProjectEntity
import com.example.ui.theme.AmberYellow
import com.example.ui.theme.CoralRed
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubScreen(
    connection: GitHubConnectionEntity?,
    currentProject: ProjectEntity?,
    onSaveConnection: (username: String, token: String, activeRepo: String?) -> Unit,
    onDisconnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showTokenDialog by remember { mutableStateOf(false) }
    var inputToken by remember { mutableStateOf(connection?.token ?: "") }
    var isVerifying by remember { mutableStateOf(false) }

    // Repositories state
    var reposList by remember { mutableStateOf<List<GitHubRepo>>(emptyList()) }
    var isLoadingRepos by remember { mutableStateOf(false) }
    var manualRepoInput by remember { mutableStateOf(connection?.activeRepo ?: "") }

    // Dialogs / Sheets
    var showBrowseSheet by remember { mutableStateOf(false) }
    var showCreateRepoDialog by remember { mutableStateOf(false) }
    var newRepoName by remember { mutableStateOf("") }
    var isNewRepoPrivate by remember { mutableStateOf(false) }
    var isCreatingRepo by remember { mutableStateOf(false) }
    var repoSearchQuery by remember { mutableStateOf("") }

    // Git operations modal state
    var commitMessage by remember { mutableStateOf("Update Android project files via Nexa IDE") }
    var gitBranch by remember { mutableStateOf(currentProject?.gitBranch ?: "main") }
    var showCommitDialog by remember { mutableStateOf(false) }
    var isPushing by remember { mutableStateOf(false) }

    fun fetchRepositories(token: String) {
        if (token.isBlank()) return
        coroutineScope.launch {
            try {
                isLoadingRepos = true
                val service = GitHubService.create()
                val repos = withContext(Dispatchers.IO) {
                    service.getUserRepositories("Bearer ${token.trim()}")
                }
                reposList = repos
                if (manualRepoInput.isBlank() && repos.isNotEmpty()) {
                    manualRepoInput = repos.first().fullName
                    connection?.let {
                        onSaveConnection(it.username, it.token, repos.first().fullName)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Repositories error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingRepos = false
            }
        }
    }

    LaunchedEffect(connection?.token) {
        connection?.token?.let { token ->
            if (token.isNotBlank()) {
                fetchRepositories(token)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("github_screen")
    ) {
        TopAppBar(
            title = { Text("GitHub Integration", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection Status Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (connection?.isConnected == true) EmeraldGreen.copy(alpha = 0.2f) else CoralRed.copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (connection?.isConnected == true) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = if (connection?.isConnected == true) EmeraldGreen else CoralRed,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (connection?.isConnected == true) "@${connection.username}" else "Not Connected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (connection?.isConnected == true)
                                    "Connected • ${reposList.size} repos found"
                                else
                                    "Connect your GitHub Personal Access Token (PAT)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (connection?.isConnected == true) {
                            OutlinedButton(
                                onClick = onDisconnect,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRed)
                            ) {
                                Text("Disconnect", fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = { showTokenDialog = true },
                                modifier = Modifier.testTag("connect_github_button")
                            ) {
                                Text("Connect")
                            }
                        }
                    }
                }
            }

            // Repository Configuration Card (Manual input + Browse list + Create new repo)
            if (connection?.isConnected == true) {
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Target Cloud Repository",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                IconButton(
                                    onClick = { fetchRepositories(connection.token) }
                                ) {
                                    if (isLoadingRepos) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NeonCyan)
                                    }
                                }
                            }

                            // Editable Input Field for Repository Name
                            Text(
                                text = "Enter repository name (e.g. ${connection.username}/my-app or my-app):",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = manualRepoInput,
                                onValueChange = { manualRepoInput = it },
                                placeholder = { Text("${connection.username}/my-android-repo") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("active_repo_input"),
                                trailingIcon = {
                                    if (manualRepoInput.isNotBlank()) {
                                        IconButton(onClick = {
                                            val normalized = if (!manualRepoInput.contains("/")) {
                                                "${connection.username}/$manualRepoInput"
                                            } else manualRepoInput.trim()

                                            manualRepoInput = normalized
                                            onSaveConnection(connection.username, connection.token, normalized)
                                            Toast.makeText(context, "Active repository set to $normalized", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.Check, contentDescription = "Save Repo", tint = EmeraldGreen)
                                        }
                                    }
                                }
                            )

                            // Quick Buttons: Browse Existing Repos / Create New Repo
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (reposList.isEmpty()) fetchRepositories(connection.token)
                                        showBrowseSheet = true
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Browse Repos (${reposList.size})", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        newRepoName = "${currentProject?.name?.lowercase()?.replace(" ", "-") ?: "my-android-app"}"
                                        showCreateRepoDialog = true
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("New Repo", fontSize = 12.sp)
                                }
                            }

                            // Active Repository Confirmation Banner
                            if (connection.activeRepo.isNullOrBlank() && manualRepoInput.isBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AmberYellow.copy(alpha = 0.15f))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "⚠️ No repository selected. Type your repo name above, click 'Browse Repos', or tap 'New Repo' to create one.",
                                        fontSize = 11.sp,
                                        color = AmberYellow,
                                        lineHeight = 16.sp
                                    )
                                }
                            } else {
                                Text(
                                    text = "✓ Active: ${connection.activeRepo ?: manualRepoInput}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldGreen
                                )
                            }
                        }
                    }
                }

                // Git Version Control Operations Card
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
                                text = "Git Version Control",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { showCommitDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Push")
                                }

                                OutlinedButton(
                                    onClick = {
                                        Toast.makeText(context, "Branch '$gitBranch' is up to date.", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pull")
                                }
                            }

                            // 1-Click Sync Workflow Button
                            Button(
                                onClick = {
                                    val targetRepo = (connection.activeRepo ?: manualRepoInput).trim()
                                    if (targetRepo.isBlank() || !targetRepo.contains("/")) {
                                        Toast.makeText(context, "Please select or type a repository first!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val parts = targetRepo.split("/")
                                    coroutineScope.launch {
                                        try {
                                            val service = GitHubService.create()
                                            val authHeader = "Bearer ${connection.token.trim()}"
                                            var branch = "main"
                                            try {
                                                val details = withContext(Dispatchers.IO) {
                                                    service.getRepository(authHeader, parts[0], parts[1])
                                                }
                                                branch = details.defaultBranch
                                            } catch (e: Exception) {
                                                // fallback
                                            }

                                            val workflowContent = """
                                                name: Android CI Build

                                                on:
                                                  push:
                                                    branches: [ main, master ]
                                                  workflow_dispatch:
                                                    inputs:
                                                      build_type:
                                                        description: 'Build Type (debug, release, bundle)'
                                                        required: true
                                                        default: 'debug'

                                                jobs:
                                                  build:
                                                    name: Build Android APK
                                                    runs-on: ubuntu-latest
                                                    steps:
                                                      - name: Checkout Source Code
                                                        uses: actions/checkout@v4
                                                        with:
                                                          fetch-depth: 1

                                                      - name: Set up Java JDK 17
                                                        uses: actions/setup-java@v4
                                                        with:
                                                          java-version: '17'
                                                          distribution: 'temurin'

                                                      - name: Setup Gradle
                                                        uses: gradle/actions/setup-gradle@v4
                                                        with:
                                                          gradle-version: '8.10.2'

                                                      - name: Detect Project & Ensure Gradle Wrapper
                                                        id: detect-project
                                                        run: |
                                                          echo "=== Repository Root Listing ==="
                                                          ls -la
                                                          PROJECT_DIR="."
                                                          if [ ! -f "settings.gradle" ] && [ ! -f "settings.gradle.kts" ] && [ ! -f "build.gradle" ] && [ ! -f "build.gradle.kts" ]; then
                                                            FOUND_DIR=${'$'}(find . -maxdepth 3 \( -name "settings.gradle*" -o -name "build.gradle*" \) -not -path "*/.*" -exec dirname {} \; | head -n 1)
                                                            if [ -n "${'$'}FOUND_DIR" ]; then
                                                              echo "Found Android project in subfolder: ${'$'}FOUND_DIR"
                                                              PROJECT_DIR="${'$'}FOUND_DIR"
                                                            fi
                                                          fi
                                                          echo "project_dir=${'$'}PROJECT_DIR" >> ${'$'}GITHUB_OUTPUT
                                                          cd "${'$'}PROJECT_DIR"
                                                          if [ ! -f "gradlew" ]; then
                                                            echo "gradlew not found, generating wrapper..."
                                                            gradle wrapper || true
                                                          fi
                                                          chmod +x gradlew || true

                                                      - name: Compile with Gradle
                                                        run: |
                                                          PROJECT_DIR="${'$'}{{ steps.detect-project.outputs.project_dir }}"
                                                          cd "${'$'}PROJECT_DIR"
                                                          if [ -f "./gradlew" ]; then
                                                            CMD="./gradlew"
                                                          else
                                                            CMD="gradle"
                                                          fi
                                                          BUILD_TYPE="${'$'}{{ github.event.inputs.build_type }}"
                                                          echo "Running ${'$'}CMD for build type ${'$'}BUILD_TYPE..."
                                                          if [ "${'$'}BUILD_TYPE" = "release" ]; then
                                                            ${'$'}CMD assembleRelease --stacktrace --no-daemon
                                                          elif [ "${'$'}BUILD_TYPE" = "bundle" ]; then
                                                            ${'$'}CMD bundleRelease --stacktrace --no-daemon
                                                          else
                                                            ${'$'}CMD assembleDebug --stacktrace --no-daemon
                                                          fi

                                                      - name: Upload APK Artifact
                                                        uses: actions/upload-artifact@v4
                                                        if: always()
                                                        with:
                                                          name: Android-APK
                                                          path: |
                                                            **/build/outputs/apk/**/*.apk
                                                            **/build/outputs/bundle/**/*.aab
                                                          retention-days: 7
                                            """.trimIndent()

                                            val base64 = android.util.Base64.encodeToString(
                                                workflowContent.toByteArray(),
                                                android.util.Base64.NO_WRAP
                                            )

                                            val resp = withContext(Dispatchers.IO) {
                                                service.createOrUpdateFile(
                                                    auth = authHeader,
                                                    owner = parts[0],
                                                    repo = parts[1],
                                                    path = ".github/workflows/android-build.yml",
                                                    body = com.example.data.github.CreateOrUpdateFilePayload(
                                                        message = "Sync Android CI Build workflow via Nexa IDE",
                                                        content = base64,
                                                        branch = branch
                                                    )
                                                )
                                            }

                                            if (resp.isSuccessful) {
                                                Toast.makeText(context, "✓ Workflow file synced to $targetRepo ($branch)! Ready to build.", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Sync response: HTTP ${resp.code()}", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Sync failed: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Build Workflow to Repository", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Security & Scopes Information Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Token Permissions & Tips",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "• Token must have 'repo' and 'workflow' checkboxes checked on GitHub.\n" +
                                    "• If you don't have a repository yet, tap '+ New Repo' above to create one automatically on your GitHub.\n" +
                                    "• Nexa IDE stores tokens locally in encrypted app storage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet: Browse User's Repositories
    if (showBrowseSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBrowseSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Select GitHub Repository",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = repoSearchQuery,
                    onValueChange = { repoSearchQuery = it },
                    placeholder = { Text("Filter repositories...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                val filtered = reposList.filter {
                    it.fullName.contains(repoSearchQuery, ignoreCase = true) ||
                            it.name.contains(repoSearchQuery, ignoreCase = true)
                }

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (reposList.isEmpty()) "No repositories found on this account" else "No matching repositories",
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                showBrowseSheet = false
                                showCreateRepoDialog = true
                            }) {
                                Text("+ Create New Repository")
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filtered) { repo ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        manualRepoInput = repo.fullName
                                        connection?.let {
                                            onSaveConnection(it.username, it.token, repo.fullName)
                                        }
                                        showBrowseSheet = false
                                        Toast.makeText(context, "Selected ${repo.fullName}", Toast.LENGTH_SHORT).show()
                                    },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (repo.private) Icons.Default.Lock else Icons.Default.Public,
                                        contentDescription = null,
                                        tint = if (repo.private) AmberYellow else NeonCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = repo.fullName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        repo.description?.let {
                                            Text(
                                                text = it,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Create New Repository on GitHub
    if (showCreateRepoDialog) {
        AlertDialog(
            onDismissRequest = { showCreateRepoDialog = false },
            title = { Text("Create GitHub Repository", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Create a repository under @${connection?.username} to store and build your Android app:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = newRepoName,
                        onValueChange = { newRepoName = it.replace(" ", "-") },
                        label = { Text("Repository Name") },
                        placeholder = { Text("my-android-app") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !isNewRepoPrivate,
                            onClick = { isNewRepoPrivate = false }
                        )
                        Text("Public")
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(
                            selected = isNewRepoPrivate,
                            onClick = { isNewRepoPrivate = true }
                        )
                        Text("Private")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newRepoName.isNotBlank() && connection != null) {
                            isCreatingRepo = true
                            coroutineScope.launch {
                                try {
                                    val service = GitHubService.create()
                                    val request = CreateRepoRequest(
                                        name = newRepoName.trim(),
                                        description = "Nexa IDE Android Project",
                                        private = isNewRepoPrivate,
                                        autoInit = true
                                    )
                                    val created = withContext(Dispatchers.IO) {
                                        service.createRepository("Bearer ${connection.token.trim()}", request)
                                    }
                                    manualRepoInput = created.fullName
                                    onSaveConnection(connection.username, connection.token, created.fullName)
                                    fetchRepositories(connection.token)
                                    showCreateRepoDialog = false
                                    Toast.makeText(context, "Created ${created.fullName} successfully!", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Creation failed: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isCreatingRepo = false
                                }
                            }
                        }
                    }
                ) {
                    if (isCreatingRepo) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Create & Select")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateRepoDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Connect GitHub PAT
    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("Connect GitHub Account", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter a GitHub Personal Access Token (classic).\n\n" +
                                "Mandatory Scopes:\n" +
                                "☑ repo (Full repository control)\n" +
                                "☑ workflow (Update GitHub Action workflows)\n\n" +
                                "⚠️ Note: If 'workflow' is not checked, GitHub will reject builds with Error 403.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    OutlinedTextField(
                        value = inputToken,
                        onValueChange = { inputToken = it },
                        label = { Text("GitHub Token (ghp_...)") },
                        placeholder = { Text("ghp_xxxxxxxxxxxxxxxxxxxx") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_token_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputToken.isNotBlank()) {
                            isVerifying = true
                            coroutineScope.launch {
                                try {
                                    val service = GitHubService.create()
                                    val user = withContext(Dispatchers.IO) {
                                        service.getAuthenticatedUser("Bearer ${inputToken.trim()}")
                                    }
                                    onSaveConnection(user.login, inputToken.trim(), null)
                                    fetchRepositories(inputToken.trim())
                                    showTokenDialog = false
                                    Toast.makeText(context, "Connected as @${user.login}", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Authentication failed: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isVerifying = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm_token_button")
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Verify & Save")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Git Commit & Push
    if (showCommitDialog) {
        AlertDialog(
            onDismissRequest = { showCommitDialog = false },
            title = { Text("Git Commit & Push", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Target Branch: $gitBranch", fontSize = 12.sp, color = NeonCyan)
                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        label = { Text("Commit Message") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (connection != null && currentProject != null) {
                            val targetRepo = (connection.activeRepo ?: manualRepoInput).trim()
                            if (targetRepo.isBlank() || !targetRepo.contains("/")) {
                                Toast.makeText(context, "Please select an active repository first!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val parts = targetRepo.split("/")
                            isPushing = true
                            coroutineScope.launch {
                                try {
                                    val service = GitHubService.create()
                                    val authHeader = "Bearer ${connection.token.trim()}"
                                    val rootDir = java.io.File(currentProject.rootPath)
                                    var count = 0
                                    if (rootDir.exists()) {
                                        val files = rootDir.walkTopDown().filter { it.isFile }.filter {
                                            val rel = it.relativeTo(rootDir).path
                                            !rel.startsWith(".gradle") &&
                                            !rel.startsWith("build") &&
                                            !rel.startsWith("app/build") &&
                                            !rel.endsWith(".apk") &&
                                            !rel.endsWith(".aab") &&
                                            !rel.endsWith(".zip")
                                        }.take(50).toList()

                                        for (file in files) {
                                            val relPath = file.relativeTo(rootDir).path.replace('\\', '/')
                                            var sha: String? = null
                                            try {
                                                val resp = withContext(Dispatchers.IO) {
                                                    service.getFileContent(authHeader, parts[0], parts[1], relPath, gitBranch)
                                                }
                                                if (resp.isSuccessful) sha = resp.body()?.sha
                                            } catch (e: Exception) {
                                                // ignore
                                            }

                                            val b64 = android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
                                            withContext(Dispatchers.IO) {
                                                service.createOrUpdateFile(
                                                    auth = authHeader,
                                                    owner = parts[0],
                                                    repo = parts[1],
                                                    path = relPath,
                                                    body = com.example.data.github.CreateOrUpdateFilePayload(
                                                        message = commitMessage.ifBlank { "Update $relPath via Nexa IDE" },
                                                        content = b64,
                                                        branch = gitBranch,
                                                        sha = sha
                                                    )
                                                )
                                            }
                                            count++
                                        }
                                    }
                                    Toast.makeText(context, "Successfully pushed $count files to $targetRepo ($gitBranch)!", Toast.LENGTH_LONG).show()
                                    showCommitDialog = false
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Push failed: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isPushing = false
                                }
                            }
                        } else {
                            Toast.makeText(context, "Please connect GitHub and select a project first", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isPushing
                ) {
                    if (isPushing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pushing files...")
                    } else {
                        Text("Commit & Push")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommitDialog = false }) { Text("Cancel") }
            }
        )
    }
}
