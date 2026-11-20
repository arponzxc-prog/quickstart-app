package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.github.*
import com.example.data.local.entity.GitHubConnectionEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.security.SecureTokenManager
import com.example.ui.components.FileBadge
import com.example.ui.components.GitConflictDialog
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class GitHubTab {
    REPOSITORIES,
    FILES,
    COMMITS,
    BRANCHES,
    PULL_REQUESTS,
    ACTIONS
}

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

    // Bumped whenever a new token is saved so the token is re-read from secure storage instead of
    // being served from a value cached by remember() for an unchanged `connection` object.
    var tokenRevision by remember { mutableStateOf(0) }
    val secureToken = remember(connection, tokenRevision) {
        SecureTokenManager.getGitHubToken(context)
            ?: connection?.token
                ?.let { GitHubTokenSanitizer.clean(it) }
                ?.takeIf { it.isNotBlank() && !GitHubTokenSanitizer.isPlaceholder(it) }
    }
    val authManager = remember { GitHubAuthManager(GitHubService.create()) }
    // Result of a real GET /user with the token in use. null = not (yet) checked.
    var authCheck by remember { mutableStateOf<GitHubAuthCheck?>(null) }
    val hasCredentials = (connection?.isConnected == true) || !secureToken.isNullOrBlank()
    // "Connected" is only ever shown after GitHub itself accepted the token.
    val isConnected = !secureToken.isNullOrBlank() && authCheck is GitHubAuthCheck.Authenticated
    val authenticatedLogin = (authCheck as? GitHubAuthCheck.Authenticated)?.user?.login

    var selectedTab by remember { mutableStateOf(GitHubTab.REPOSITORIES) }
    var activeRepoName by remember { mutableStateOf(connection?.activeRepo ?: "") }

    // Dialogs
    var showTokenDialog by remember { mutableStateOf(false) }
    var inputToken by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var tokenDialogError by remember { mutableStateOf<String?>(null) }

    // 1. Repositories state
    var reposList by remember { mutableStateOf<List<GitHubRepo>>(emptyList()) }
    var isLoadingRepos by remember { mutableStateOf(false) }
    var repoSearchQuery by remember { mutableStateOf("") }
    var showCreateRepoDialog by remember { mutableStateOf(false) }
    var newRepoName by remember { mutableStateOf("") }
    var isNewRepoPrivate by remember { mutableStateOf(false) }
    var createRepoError by remember { mutableStateOf<String?>(null) }
    var isCreatingRepo by remember { mutableStateOf(false) }

    // 2. Files state
    var currentDirPath by remember { mutableStateOf("") }
    var dirContents by remember { mutableStateOf<List<GitHubContentItem>>(emptyList()) }
    var isLoadingFiles by remember { mutableStateOf(false) }
    var viewingFileContent by remember { mutableStateOf<Pair<String, String>?>(null) } // (filename, content)
    var fileToDelete by remember { mutableStateOf<GitHubContentItem?>(null) }

    // 3. Commits state
    var commitsList by remember { mutableStateOf<List<GitHubCommitItem>>(emptyList()) }
    var isLoadingCommits by remember { mutableStateOf(false) }
    var commitDetail by remember { mutableStateOf<GitHubCommitDetail?>(null) }

    // 4. Branches state
    var branchesList by remember { mutableStateOf<List<GitHubBranch>>(emptyList()) }
    var isLoadingBranches by remember { mutableStateOf(false) }
    var currentBranch by remember { mutableStateOf(currentProject?.gitBranch ?: "main") }
    var showCreateBranchDialog by remember { mutableStateOf(false) }
    var newBranchName by remember { mutableStateOf("") }
    var showMergeBranchDialog by remember { mutableStateOf(false) }
    var branchToMerge by remember { mutableStateOf("") }

    // 5. Pull Requests state
    var pullRequestsList by remember { mutableStateOf<List<GitHubPullRequest>>(emptyList()) }
    var isLoadingPRs by remember { mutableStateOf(false) }
    var showCreatePrDialog by remember { mutableStateOf(false) }
    var prTitle by remember { mutableStateOf("") }
    var prBody by remember { mutableStateOf("") }
    var prHeadBranch by remember { mutableStateOf("") }
    var prBaseBranch by remember { mutableStateOf("main") }

    // 6. Actions state
    var workflowsList by remember { mutableStateOf<List<GitHubWorkflow>>(emptyList()) }
    var runsList by remember { mutableStateOf<List<GitHubWorkflowRun>>(emptyList()) }
    var isLoadingActions by remember { mutableStateOf(false) }

    // Push & Commit Dialog State
    var showCommitDialog by remember { mutableStateOf(false) }
    var commitMessage by remember { mutableStateOf("Update via Nexa IDE") }
    var isPushing by remember { mutableStateOf(false) }

    // Conflict Resolution State
    var conflictData by remember { mutableStateOf<Triple<String, String, String>?>(null) } // (filename, local, remote)

    // Never fall back to connection.token: in Room it is only the "SECURE_KEYSTORE_ENCRYPTED"
    // placeholder, and sending that to GitHub as a Bearer token always yields HTTP 401.
    val effectiveToken = secureToken ?: ""
    val authHeader = GitHubTokenSanitizer.bearer(effectiveToken)

    fun getRepoParts(): Pair<String, String>? {
        val repo = activeRepoName.trim()
        if (repo.isBlank() || !repo.contains("/")) return null
        val parts = repo.split("/")
        return Pair(parts[0], parts[1])
    }

    fun fetchRepositories() {
        if (effectiveToken.isBlank()) return
        coroutineScope.launch {
            try {
                isLoadingRepos = true
                val service = GitHubService.create()
                val repos = withContext(Dispatchers.IO) {
                    service.getUserRepositories(authHeader)
                }
                reposList = repos
                if (activeRepoName.isBlank() && repos.isNotEmpty()) {
                    activeRepoName = repos.first().fullName
                    connection?.let {
                        onSaveConnection(it.username, effectiveToken, repos.first().fullName)
                    }
                }
            } catch (e: retrofit2.HttpException) {
                val err = GitHubErrors.fromHttpException(e)
                if (err.httpCode == 401) authCheck = GitHubAuthCheck.Failed(err)
                Toast.makeText(
                    context,
                    GitHubErrors.explain(GitHubOperation.LIST_REPOS, err),
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Repositories error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingRepos = false
            }
        }
    }

    fun fetchDirectoryContents(path: String) {
        val parts = getRepoParts() ?: return
        coroutineScope.launch {
            try {
                isLoadingFiles = true
                val service = GitHubService.create()
                val items = withContext(Dispatchers.IO) {
                    service.getDirectoryContents(authHeader, parts.first, parts.second, path, currentBranch)
                }
                dirContents = items.sortedWith(compareBy({ it.type != "dir" }, { it.name }))
                currentDirPath = path
            } catch (e: Exception) {
                Toast.makeText(context, "Could not load files: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingFiles = false
            }
        }
    }

    fun viewRemoteFile(item: GitHubContentItem) {
        val parts = getRepoParts() ?: return
        coroutineScope.launch {
            try {
                isLoadingFiles = true
                val service = GitHubService.create()
                val resp = withContext(Dispatchers.IO) {
                    service.getFileContent(authHeader, parts.first, parts.second, item.path, currentBranch)
                }
                if (resp.isSuccessful) {
                    val fileBody = resp.body()
                    val decoded = if (fileBody?.content != null) {
                        try {
                            val cleanBase64 = fileBody.content.replace("\n", "").replace("\r", "")
                            String(android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT))
                        } catch (e: Exception) {
                            "Could not decode file content."
                        }
                    } else {
                        "File is empty or binary."
                    }
                    viewingFileContent = Pair(item.name, decoded)
                } else {
                    Toast.makeText(context, "Failed to fetch file content (HTTP ${resp.code()})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingFiles = false
            }
        }
    }

    fun fetchCommits() {
        val parts = getRepoParts() ?: return
        coroutineScope.launch {
            try {
                isLoadingCommits = true
                val service = GitHubService.create()
                val list = withContext(Dispatchers.IO) {
                    service.listCommits(authHeader, parts.first, parts.second, currentBranch, 30)
                }
                commitsList = list
            } catch (e: Exception) {
                Toast.makeText(context, "Commits error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingCommits = false
            }
        }
    }

    fun fetchBranches() {
        val parts = getRepoParts() ?: return
        coroutineScope.launch {
            try {
                isLoadingBranches = true
                val service = GitHubService.create()
                val list = withContext(Dispatchers.IO) {
                    service.listBranches(authHeader, parts.first, parts.second)
                }
                branchesList = list
            } catch (e: Exception) {
                Toast.makeText(context, "Branches error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingBranches = false
            }
        }
    }

    fun fetchPullRequests() {
        val parts = getRepoParts() ?: return
        coroutineScope.launch {
            try {
                isLoadingPRs = true
                val service = GitHubService.create()
                val list = withContext(Dispatchers.IO) {
                    service.listPullRequests(authHeader, parts.first, parts.second, "all", 30)
                }
                pullRequestsList = list
            } catch (e: Exception) {
                Toast.makeText(context, "PR error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingPRs = false
            }
        }
    }

    fun fetchActions() {
        val parts = getRepoParts() ?: return
        coroutineScope.launch {
            try {
                isLoadingActions = true
                val service = GitHubService.create()
                val wf = withContext(Dispatchers.IO) {
                    try { service.listWorkflows(authHeader, parts.first, parts.second).workflows } catch (e: Exception) { emptyList() }
                }
                val runs = withContext(Dispatchers.IO) {
                    try { service.listWorkflowRuns(authHeader, parts.first, parts.second, 20).workflowRuns } catch (e: Exception) { emptyList() }
                }
                workflowsList = wf
                runsList = runs
            } catch (e: Exception) {
                Toast.makeText(context, "Actions error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingActions = false
            }
        }
    }

    fun pullRemoteFilesWithConflictDetection() {
        val parts = getRepoParts()
        if (parts == null || currentProject == null) {
            Toast.makeText(context, "Select an active repository and project first", Toast.LENGTH_SHORT).show()
            return
        }
        val rootDir = File(currentProject.rootPath)
        coroutineScope.launch {
            try {
                val service = GitHubService.create()
                // Inspect remote files in repo root
                val remoteItems = withContext(Dispatchers.IO) {
                    service.getDirectoryContents(authHeader, parts.first, parts.second, "", currentBranch)
                }
                for (item in remoteItems.filter { it.type == "file" }) {
                    val localFile = File(rootDir, item.name)
                    if (localFile.exists()) {
                        val localText = localFile.readText()
                        val remoteResp = withContext(Dispatchers.IO) {
                            service.getFileContent(authHeader, parts.first, parts.second, item.path, currentBranch)
                        }
                        if (remoteResp.isSuccessful && remoteResp.body()?.content != null) {
                            val cleanBase64 = remoteResp.body()!!.content!!.replace("\n", "").replace("\r", "")
                            val remoteText = String(android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT))
                            if (localText != remoteText) {
                                // Conflict detected! Trigger conflict dialog
                                conflictData = Triple(item.name, localText, remoteText)
                                return@launch
                            }
                        }
                    }
                }
                Toast.makeText(context, "✓ Branch '$currentBranch' is in sync with local project", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Pull notice: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Verify the token actually in use against GET /user whenever it changes.
    LaunchedEffect(effectiveToken) {
        if (effectiveToken.isBlank()) {
            authCheck = null
        } else {
            authCheck = null
            val result = withContext(Dispatchers.IO) { authManager.verifyToken(effectiveToken) }
            authCheck = result
            if (result is GitHubAuthCheck.Authenticated &&
                connection != null &&
                connection.username != result.user.login
            ) {
                // Keep stored metadata in sync with the real authenticated account.
                onSaveConnection(result.user.login, effectiveToken, connection.activeRepo)
            }
        }
    }

    LaunchedEffect(effectiveToken, isConnected) {
        if (effectiveToken.isNotBlank() && isConnected) {
            fetchRepositories()
        }
    }

    LaunchedEffect(selectedTab, activeRepoName, currentBranch, isConnected) {
        if (activeRepoName.isNotBlank() && effectiveToken.isNotBlank() && isConnected) {
            when (selectedTab) {
                GitHubTab.REPOSITORIES -> fetchRepositories()
                GitHubTab.FILES -> fetchDirectoryContents(currentDirPath)
                GitHubTab.COMMITS -> fetchCommits()
                GitHubTab.BRANCHES -> fetchBranches()
                GitHubTab.PULL_REQUESTS -> fetchPullRequests()
                GitHubTab.ACTIONS -> fetchActions()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
            .testTag("github_screen")
    ) {
        TopAppBar(
            title = {
                Column {
                    Text("GitHub Advanced Studio", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (activeRepoName.isNotBlank()) {
                        Text(
                            text = "$activeRepoName • branch: $currentBranch",
                            fontSize = 11.sp,
                            color = NeonCyan
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = {
                    when (selectedTab) {
                        GitHubTab.REPOSITORIES -> fetchRepositories()
                        GitHubTab.FILES -> fetchDirectoryContents(currentDirPath)
                        GitHubTab.COMMITS -> fetchCommits()
                        GitHubTab.BRANCHES -> fetchBranches()
                        GitHubTab.PULL_REQUESTS -> fetchPullRequests()
                        GitHubTab.ACTIONS -> fetchActions()
                    }
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NeonCyan)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate900, titleContentColor = Color.White)
        )

        // Connection Summary Bar
        Surface(
            color = Slate900,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isConnected -> EmeraldGreen
                                    effectiveToken.isNotBlank() && authCheck == null -> AmberYellow
                                    else -> CoralRed
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isConnected -> "@${authenticatedLogin ?: connection?.username}"
                            effectiveToken.isNotBlank() && authCheck == null -> "Verifying…"
                            authCheck is GitHubAuthCheck.Failed -> "Authentication failed"
                            authCheck is GitHubAuthCheck.NetworkError -> "Unverified"
                            else -> "Disconnected"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = SecureTokenManager.maskToken(effectiveToken),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (hasCredentials) {
                        TextButton(
                            onClick = {
                                inputToken = ""
                                tokenDialogError = null
                                showTokenDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Change Token", fontSize = 11.sp, color = NeonCyan)
                        }
                        TextButton(
                            onClick = onDisconnect,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Disconnect", fontSize = 11.sp, color = CoralRed)
                        }
                    } else {
                        Button(
                            onClick = {
                                tokenDialogError = null
                                showTokenDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Connect", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        GitHubAuthBanner(
            check = authCheck,
            hasToken = effectiveToken.isNotBlank(),
            onChangeToken = {
                inputToken = ""
                tokenDialogError = null
                showTokenDialog = true
            },
            onRetry = {
                coroutineScope.launch {
                    authCheck = null
                    authCheck = withContext(Dispatchers.IO) { authManager.verifyToken(effectiveToken) }
                }
            }
        )

        // Tab Navigation Bar
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Slate900,
            contentColor = Color.White,
            edgePadding = 8.dp
        ) {
            GitHubTab.values().forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            text = when (tab) {
                                GitHubTab.REPOSITORIES -> "Repos"
                                GitHubTab.FILES -> "Files"
                                GitHubTab.COMMITS -> "Commits"
                                GitHubTab.BRANCHES -> "Branches"
                                GitHubTab.PULL_REQUESTS -> "PRs"
                                GitHubTab.ACTIONS -> "CI/CD"
                            },
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == tab) NeonCyan else Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                )
            }
        }

        // Tab Content Panels
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            when (selectedTab) {
                // TAB 1: REPOSITORIES
                GitHubTab.REPOSITORIES -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = repoSearchQuery,
                                onValueChange = { repoSearchQuery = it },
                                placeholder = { Text("Search repositories...", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                singleLine = true
                            )
                            Button(
                                onClick = { showCreateRepoDialog = true },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val filteredRepos = reposList.filter {
                            it.fullName.contains(repoSearchQuery, ignoreCase = true) ||
                                    it.name.contains(repoSearchQuery, ignoreCase = true)
                        }

                        if (isLoadingRepos) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = NeonCyan)
                            }
                        } else if (filteredRepos.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No repositories found", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredRepos) { repo ->
                                    val isSelected = repo.fullName.equals(activeRepoName, ignoreCase = true)
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                activeRepoName = repo.fullName
                                                connection?.let {
                                                    onSaveConnection(it.username, effectiveToken, repo.fullName)
                                                }
                                                Toast.makeText(context, "Active repo: ${repo.fullName}", Toast.LENGTH_SHORT).show()
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = if (isSelected) Slate800 else Slate900),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) NeonCyan else Slate800
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = if (repo.private) Icons.Default.Lock else Icons.Default.Public,
                                                        contentDescription = null,
                                                        tint = if (repo.private) AmberYellow else NeonCyan,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = repo.fullName,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = Color.White
                                                    )
                                                }
                                                if (isSelected) {
                                                    Surface(
                                                        color = EmeraldGreen.copy(alpha = 0.2f),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "Active",
                                                            color = EmeraldGreen,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            repo.description?.let { desc ->
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = desc,
                                                    fontSize = 12.sp,
                                                    color = Color.LightGray,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("★ ${repo.stargazersCount}", fontSize = 11.sp, color = AmberYellow)
                                                Text("⑂ ${repo.forksCount}", fontSize = 11.sp, color = Color.Gray)
                                                Text("branch: ${repo.defaultBranch}", fontSize = 11.sp, color = NeonCyan)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 2: REMOTE FILE EXPLORER
                GitHubTab.FILES -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (currentDirPath.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            val parent = File(currentDirPath).parent?.replace("\\", "/") ?: ""
                                            fetchDirectoryContents(parent)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up", tint = NeonCyan, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Text(
                                    text = "/${currentDirPath.ifEmpty { " (repo root)" }}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { showCommitDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Push", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = { pullRemoteFilesWithConflictDetection() },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pull", fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isLoadingFiles) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = NeonCyan)
                            }
                        } else if (dirContents.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No files found in this directory", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(dirContents) { item ->
                                    Surface(
                                        color = Slate900,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (item.type == "dir") {
                                                    fetchDirectoryContents(item.path)
                                                } else {
                                                    viewRemoteFile(item)
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (item.type == "dir") {
                                                Icon(Icons.Default.Folder, contentDescription = null, tint = AmberYellow, modifier = Modifier.size(20.dp))
                                            } else {
                                                FileBadge(extension = File(item.name).extension)
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                if (item.type != "dir") {
                                                    Text("${item.size / 1024} KB", color = Color.Gray, fontSize = 11.sp)
                                                }
                                            }

                                            if (item.type != "dir") {
                                                IconButton(
                                                    onClick = { fileToDelete = item },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CoralRed, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 3: COMMITS & HISTORY
                GitHubTab.COMMITS -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Commit History ($currentBranch)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (isLoadingCommits) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = NeonCyan)
                            }
                        } else if (commitsList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No commits recorded yet", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(commitsList) { commitItem ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val parts = getRepoParts() ?: return@clickable
                                                coroutineScope.launch {
                                                    try {
                                                        val service = GitHubService.create()
                                                        val detail = withContext(Dispatchers.IO) {
                                                            service.getCommitDetail(authHeader, parts.first, parts.second, commitItem.sha)
                                                        }
                                                        commitDetail = detail
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = Slate900)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = commitItem.commit.message,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = commitItem.commit.author?.name ?: "Unknown",
                                                    fontSize = 11.sp,
                                                    color = NeonCyan
                                                )
                                                Text(
                                                    text = commitItem.sha.take(7),
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = AmberYellow
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 4: BRANCHES & MERGE
                GitHubTab.BRANCHES -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Branches (${branchesList.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Button(
                                onClick = { showCreateBranchDialog = true },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Branch", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isLoadingBranches) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = NeonCyan)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(branchesList) { branch ->
                                    val isCurrent = branch.name == currentBranch
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = if (isCurrent) Slate800 else Slate900),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isCurrent) NeonCyan else Slate800)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.CallSplit, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(branch.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                if (!isCurrent) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            currentBranch = branch.name
                                                            Toast.makeText(context, "Switched to branch '${branch.name}'", Toast.LENGTH_SHORT).show()
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("Switch", fontSize = 11.sp)
                                                    }
                                                    Button(
                                                        onClick = {
                                                            branchToMerge = branch.name
                                                            showMergeBranchDialog = true
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("Merge", fontSize = 11.sp)
                                                    }
                                                } else {
                                                    Surface(color = EmeraldGreen.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                                        Text("Active", color = EmeraldGreen, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
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

                // TAB 5: PULL REQUESTS
                GitHubTab.PULL_REQUESTS -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pull Requests",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Button(
                                onClick = {
                                    prHeadBranch = currentBranch
                                    showCreatePrDialog = true
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New PR", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isLoadingPRs) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = NeonCyan)
                            }
                        } else if (pullRequestsList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No open pull requests found", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(pullRequestsList) { pr ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = Slate900)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "#${pr.number} ${pr.title}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color.White,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Surface(
                                                    color = if (pr.state == "open") EmeraldGreen.copy(alpha = 0.2f) else CoralRed.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = pr.state.uppercase(),
                                                        color = if (pr.state == "open") EmeraldGreen else CoralRed,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${pr.head?.ref ?: "head"} -> ${pr.base?.ref ?: "main"}",
                                                fontSize = 11.sp,
                                                color = NeonCyan
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 6: ACTIONS & CI/CD
                GitHubTab.ACTIONS -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "GitHub Actions Runs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (isLoadingActions) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = NeonCyan)
                            }
                        } else if (runsList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No workflow runs found", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(runsList) { run ->
                                    val isSuccess = run.conclusion == "success"
                                    val isFailed = run.conclusion == "failure"
                                    val isRunning = run.status == "in_progress" || run.status == "queued"
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = Slate900)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = run.name ?: "Android Build",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "#${run.id} • ${run.headBranch ?: "main"} • ${run.status}",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }

                                            Surface(
                                                color = when {
                                                    isSuccess -> EmeraldGreen.copy(alpha = 0.2f)
                                                    isFailed -> CoralRed.copy(alpha = 0.2f)
                                                    isRunning -> NeonCyan.copy(alpha = 0.2f)
                                                    else -> Slate800
                                                },
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = (run.conclusion ?: run.status).uppercase(),
                                                    color = when {
                                                        isSuccess -> EmeraldGreen
                                                        isFailed -> CoralRed
                                                        isRunning -> NeonCyan
                                                        else -> Color.White
                                                    },
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
    }

    // Modal Dialog: View Remote File Content
    viewingFileContent?.let { (fileName, content) ->
        AlertDialog(
            onDismissRequest = { viewingFileContent = null },
            title = { Text(fileName, fontWeight = FontWeight.Bold) },
            text = {
                Surface(
                    color = Color(0xFF0d1117),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    LazyColumn(modifier = Modifier.padding(8.dp)) {
                        item {
                            Text(
                                text = content,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewingFileContent = null }) { Text("Close") }
            }
        )
    }

    // Modal Dialog: Commit Details
    commitDetail?.let { detail ->
        AlertDialog(
            onDismissRequest = { commitDetail = null },
            title = { Text("Commit Details: ${detail.sha.take(7)}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(detail.commit.message, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Author: ${detail.commit.author?.name} (${detail.commit.author?.date})", fontSize = 11.sp, color = NeonCyan)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Changed Files (${detail.files?.size ?: 0}):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(detail.files ?: emptyList()) { file ->
                            Text("• ${file.filename} (+${file.additions} -${file.deletions})", fontSize = 11.sp, color = Color.LightGray)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { commitDetail = null }) { Text("Close") }
            }
        )
    }

    // Modal Dialog: Git Conflict Resolution
    conflictData?.let { (fileName, local, remote) ->
        GitConflictDialog(
            fileName = fileName,
            localContent = local,
            remoteContent = remote,
            onKeepLocal = {
                conflictData = null
                Toast.makeText(context, "Keeping local version of $fileName", Toast.LENGTH_SHORT).show()
            },
            onAcceptRemote = {
                currentProject?.let { proj ->
                    File(proj.rootPath, fileName).writeText(remote)
                }
                conflictData = null
                Toast.makeText(context, "Applied remote version to $fileName", Toast.LENGTH_SHORT).show()
            },
            onCancel = { conflictData = null }
        )
    }

    // Dialog: Delete Remote File
    fileToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete File on GitHub?") },
            text = { Text("Are you sure you want to delete '${item.path}' from branch '$currentBranch'?") },
            confirmButton = {
                Button(
                    onClick = {
                        val parts = getRepoParts()
                        if (parts != null) {
                            coroutineScope.launch {
                                try {
                                    val service = GitHubService.create()
                                    withContext(Dispatchers.IO) {
                                        service.deleteFile(
                                            auth = authHeader,
                                            owner = parts.first,
                                            repo = parts.second,
                                            path = item.path,
                                            body = DeleteFilePayload(
                                                message = "Delete ${item.name} via Nexa IDE",
                                                sha = item.sha,
                                                branch = currentBranch
                                            )
                                        )
                                    }
                                    Toast.makeText(context, "Deleted ${item.name}", Toast.LENGTH_SHORT).show()
                                    fetchDirectoryContents(currentDirPath)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Deletion failed: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    fileToDelete = null
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Create Branch
    if (showCreateBranchDialog) {
        AlertDialog(
            onDismissRequest = { showCreateBranchDialog = false },
            title = { Text("Create New Branch", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Creating new branch based on '$currentBranch':", fontSize = 12.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = newBranchName,
                        onValueChange = { newBranchName = it.replace(" ", "-") },
                        label = { Text("Branch Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parts = getRepoParts()
                        if (parts != null && newBranchName.isNotBlank()) {
                            coroutineScope.launch {
                                try {
                                    val service = GitHubService.create()
                                    val ref = withContext(Dispatchers.IO) {
                                        service.getGitRef(authHeader, parts.first, parts.second, currentBranch)
                                    }
                                    val headSha = ref.obj.sha
                                    withContext(Dispatchers.IO) {
                                        service.createGitRef(
                                            auth = authHeader,
                                            owner = parts.first,
                                            repo = parts.second,
                                            request = CreateGitRefRequest(
                                                ref = "refs/heads/${newBranchName.trim()}",
                                                sha = headSha
                                            )
                                        )
                                    }
                                    currentBranch = newBranchName.trim()
                                    fetchBranches()
                                    showCreateBranchDialog = false
                                    Toast.makeText(context, "Created & switched to '$newBranchName'", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Create branch failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                ) { Text("Create Branch") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateBranchDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Merge Branch
    if (showMergeBranchDialog) {
        AlertDialog(
            onDismissRequest = { showMergeBranchDialog = false },
            title = { Text("Merge Branch", fontWeight = FontWeight.Bold) },
            text = {
                Text("Merge changes from '$branchToMerge' into active branch '$currentBranch'?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parts = getRepoParts()
                        if (parts != null) {
                            coroutineScope.launch {
                                try {
                                    val service = GitHubService.create()
                                    val resp = withContext(Dispatchers.IO) {
                                        service.mergeBranch(
                                            auth = authHeader,
                                            owner = parts.first,
                                            repo = parts.second,
                                            request = MergeBranchRequest(
                                                base = currentBranch,
                                                head = branchToMerge,
                                                commitMessage = "Merge branch '$branchToMerge' into $currentBranch via Nexa IDE"
                                            )
                                        )
                                    }
                                    if (resp.isSuccessful) {
                                        Toast.makeText(context, "Merged successfully!", Toast.LENGTH_SHORT).show()
                                        fetchCommits()
                                    } else {
                                        Toast.makeText(context, "Merge response: HTTP ${resp.code()} (Check for conflicts)", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Merge error: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    showMergeBranchDialog = false
                                }
                            }
                        }
                    }
                ) { Text("Confirm Merge") }
            },
            dismissButton = {
                TextButton(onClick = { showMergeBranchDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Create Pull Request
    if (showCreatePrDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePrDialog = false },
            title = { Text("Create Pull Request", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Base branch: $prBaseBranch  <--  Head branch: $prHeadBranch", fontSize = 12.sp, color = NeonCyan)
                    OutlinedTextField(
                        value = prTitle,
                        onValueChange = { prTitle = it },
                        label = { Text("PR Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = prBody,
                        onValueChange = { prBody = it },
                        label = { Text("Description") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parts = getRepoParts()
                        if (parts != null && prTitle.isNotBlank()) {
                            coroutineScope.launch {
                                try {
                                    val service = GitHubService.create()
                                    withContext(Dispatchers.IO) {
                                        service.createPullRequest(
                                            auth = authHeader,
                                            owner = parts.first,
                                            repo = parts.second,
                                            payload = CreatePullRequestPayload(
                                                title = prTitle.trim(),
                                                head = prHeadBranch,
                                                base = prBaseBranch,
                                                body = prBody.trim()
                                            )
                                        )
                                    }
                                    showCreatePrDialog = false
                                    fetchPullRequests()
                                    Toast.makeText(context, "Pull request created successfully!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "PR creation failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                ) { Text("Create PR") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePrDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Git Commit & Push
    if (showCommitDialog) {
        AlertDialog(
            onDismissRequest = { showCommitDialog = false },
            title = { Text("Git Commit & Push", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Target: $activeRepoName ($currentBranch)", fontSize = 12.sp, color = NeonCyan)
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
                        val parts = getRepoParts()
                        if (parts != null && currentProject != null) {
                            isPushing = true
                            coroutineScope.launch {
                                try {
                                    val service = GitHubService.create()
                                    val rootDir = File(currentProject.rootPath)
                                    val files = rootDir.walkTopDown().filter { it.isFile }.filter {
                                        val rel = it.relativeTo(rootDir).path
                                        !rel.startsWith(".gradle") &&
                                                !rel.startsWith("build") &&
                                                !rel.startsWith("app/build") &&
                                                !rel.endsWith(".apk") &&
                                                !rel.endsWith(".aab") &&
                                                !rel.endsWith(".zip")
                                    }.take(40).toList()

                                    for (file in files) {
                                        val relPath = file.relativeTo(rootDir).path.replace('\\', '/')
                                        var sha: String? = null
                                        try {
                                            val resp = withContext(Dispatchers.IO) {
                                                service.getFileContent(authHeader, parts.first, parts.second, relPath, currentBranch)
                                            }
                                            if (resp.isSuccessful) sha = resp.body()?.sha
                                        } catch (e: Exception) {
                                            // ignore
                                        }

                                        val b64 = android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
                                        withContext(Dispatchers.IO) {
                                            service.createOrUpdateFile(
                                                auth = authHeader,
                                                owner = parts.first,
                                                repo = parts.second,
                                                path = relPath,
                                                body = CreateOrUpdateFilePayload(
                                                    message = commitMessage.ifBlank { "Update $relPath via Nexa IDE" },
                                                    content = b64,
                                                    branch = currentBranch,
                                                    sha = sha
                                                )
                                            )
                                        }
                                    }
                                    Toast.makeText(context, "Pushed files successfully!", Toast.LENGTH_SHORT).show()
                                    fetchCommits()
                                    showCommitDialog = false
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Push failed: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isPushing = false
                                }
                            }
                        }
                    },
                    enabled = !isPushing
                ) {
                    if (isPushing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
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

    // Dialog: Create Repo
    if (showCreateRepoDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCreatingRepo) { showCreateRepoDialog = false; createRepoError = null } },
            title = { Text("Create Repository", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newRepoName,
                        onValueChange = { newRepoName = it.replace(" ", "-"); createRepoError = null },
                        label = { Text("Repository Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isNewRepoPrivate, onCheckedChange = { isNewRepoPrivate = it })
                        Text("Private Repository", fontSize = 12.sp)
                    }
                    createRepoError?.let { msg ->
                        Text(
                            text = msg,
                            fontSize = 12.sp,
                            color = CoralRed,
                            modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 180.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isCreatingRepo,
                    onClick = {
                        val validation = GitHubRepoValidator.validateName(newRepoName)
                        if (!validation.ok) {
                            createRepoError = validation.error
                        } else {
                        isCreatingRepo = true
                        createRepoError = null
                        coroutineScope.launch {
                            // Re-read the token right now; never reuse a value captured earlier.
                            val liveToken = SecureTokenManager.getGitHubToken(context) ?: effectiveToken
                            val result = withContext(Dispatchers.IO) {
                                authManager.createRepository(
                                    rawToken = liveToken,
                                    rawName = validation.name,
                                    description = null,
                                    isPrivate = isNewRepoPrivate
                                )
                            }
                            when (result) {
                                is GitHubCreateRepoResult.Success -> {
                                    val created = result.repo
                                    activeRepoName = created.fullName
                                    connection?.let {
                                        onSaveConnection(it.username, liveToken, created.fullName)
                                    }
                                    fetchRepositories()
                                    showCreateRepoDialog = false
                                    newRepoName = ""
                                    Toast.makeText(context, "Created ${created.fullName}", Toast.LENGTH_SHORT).show()
                                }
                                is GitHubCreateRepoResult.Failure -> {
                                    if (result.error.httpCode == 401) {
                                        authCheck = GitHubAuthCheck.Failed(result.error)
                                    }
                                    createRepoError = result.explanation
                                }
                                is GitHubCreateRepoResult.NetworkError ->
                                    createRepoError = "Network error: ${result.message}"
                                is GitHubCreateRepoResult.InvalidInput ->
                                    createRepoError = result.message
                            }
                            isCreatingRepo = false
                        }
                        }
                    }
                ) {
                    if (isCreatingRepo) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Create")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isCreatingRepo,
                    onClick = { showCreateRepoDialog = false; createRepoError = null }
                ) { Text("Cancel") }
            }
        )
    }

    // Dialog: Connect Token
    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("Connect GitHub Account", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a GitHub Personal Access Token (classic) with 'repo' and 'workflow' scopes.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    OutlinedTextField(
                        value = inputToken,
                        onValueChange = { inputToken = it; tokenDialogError = null },
                        label = { Text("Personal Access Token (ghp_...)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    tokenDialogError?.let { msg ->
                        Text(
                            text = msg,
                            fontSize = 12.sp,
                            color = CoralRed,
                            modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 180.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isVerifying,
                    onClick = {
                        // Strip whitespace/newlines/"Bearer " that often come with pasted tokens.
                        val cleaned = GitHubTokenSanitizer.clean(inputToken)
                        when {
                            cleaned.isEmpty() -> tokenDialogError = "Paste your GitHub personal access token."
                            !GitHubTokenSanitizer.isWellFormed(cleaned) ->
                                tokenDialogError = "That does not look like a GitHub token " +
                                    "(expected letters, digits, '_' or '-', at least 20 characters)."
                            else -> {
                                isVerifying = true
                                tokenDialogError = null
                                coroutineScope.launch {
                                    val result = withContext(Dispatchers.IO) { authManager.verifyToken(cleaned) }
                                    when (result) {
                                        is GitHubAuthCheck.Authenticated -> {
                                            // Save only after GitHub accepted the token.
                                            onSaveConnection(result.user.login, cleaned, connection?.takeIf { it.username == result.user.login }?.activeRepo)
                                            tokenRevision += 1
                                            authCheck = result
                                            inputToken = ""
                                            showTokenDialog = false
                                            Toast.makeText(context, "Authenticated as @${result.user.login}", Toast.LENGTH_SHORT).show()
                                        }
                                        is GitHubAuthCheck.Failed ->
                                            tokenDialogError = GitHubErrors.explain(GitHubOperation.VERIFY_TOKEN, result.error)
                                        is GitHubAuthCheck.NetworkError ->
                                            tokenDialogError = "Could not reach GitHub: ${result.message}"
                                        is GitHubAuthCheck.MalformedToken ->
                                            tokenDialogError = result.reason
                                        GitHubAuthCheck.NoToken ->
                                            tokenDialogError = "Paste your GitHub personal access token."
                                    }
                                    isVerifying = false
                                }
                            }
                        }
                    }
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
}


/**
 * Shows the real outcome of GET /user. Authentication and repository-creation permission are
 * reported separately; a failed check never shows a "connected" state.
 */
@Composable
private fun GitHubAuthBanner(
    check: GitHubAuthCheck?,
    hasToken: Boolean,
    onChangeToken: () -> Unit,
    onRetry: () -> Unit
) {
    if (!hasToken && check == null) return

    val (color, title, body) = when (check) {
        null -> Triple(AmberYellow, "Verifying token with GitHub…", null)
        is GitHubAuthCheck.Authenticated -> {
            val scopeText = check.scopes?.takeIf { it.isNotEmpty() }?.joinToString(", ")
                ?: if (check.scopes == null) "not reported (fine-grained token)" else "none"
            val perm = when (check.repoCreation) {
                RepoCreationCapability.ALLOWED -> "Repository creation: allowed (repo scope)."
                RepoCreationCapability.PUBLIC_ONLY -> "Repository creation: public repositories only (public_repo scope)."
                RepoCreationCapability.MISSING_SCOPE -> "Repository creation: NOT permitted – token lacks the repo/public_repo scope."
                RepoCreationCapability.UNKNOWN -> "Repository creation: cannot be known in advance for this token type; GitHub decides when you create."
            }
            val c = if (check.repoCreation == RepoCreationCapability.MISSING_SCOPE) AmberYellow else EmeraldGreen
            Triple(c, "Authenticated as @${check.user.login}", "Scopes: $scopeText\n$perm")
        }
        is GitHubAuthCheck.Failed -> Triple(
            CoralRed,
            if (check.error.httpCode == 401) "GitHub authentication failed" else "GitHub request failed (HTTP ${check.error.httpCode})",
            GitHubErrors.explain(GitHubOperation.VERIFY_TOKEN, check.error)
        )
        is GitHubAuthCheck.NetworkError -> Triple(AmberYellow, "Could not reach GitHub", check.message)
        is GitHubAuthCheck.MalformedToken -> Triple(CoralRed, "Stored token unusable", check.reason)
        GitHubAuthCheck.NoToken -> return
    }

    Surface(
        color = color.copy(alpha = 0.12f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = color)
            if (body != null) Text(body, fontSize = 11.sp, color = Color.White)
            val failed = check is GitHubAuthCheck.Failed || check is GitHubAuthCheck.MalformedToken
            if (failed) {
                Row {
                    TextButton(onClick = onChangeToken) { Text("Change Token", fontSize = 11.sp, color = NeonCyan) }
                    if (check is GitHubAuthCheck.Failed && check.error.httpCode != 401) {
                        TextButton(onClick = onRetry) { Text("Retry", fontSize = 11.sp, color = NeonCyan) }
                    }
                }
            } else if (check is GitHubAuthCheck.NetworkError) {
                TextButton(onClick = onRetry) { Text("Retry", fontSize = 11.sp, color = NeonCyan) }
            }
        }
    }
}
