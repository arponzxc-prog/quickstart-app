package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.example.ui.components.HtmlBuildCustomizeDialog
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.builder.BuildEngine
import com.example.builder.BuildProgressState
import com.example.data.filesystem.ProjectFileManager
import com.example.data.local.NexaDatabase
import com.example.data.local.entity.BuildArtifactEntity
import com.example.data.local.entity.BuildEntity
import com.example.data.local.entity.GitHubConnectionEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.preferences.NexaPreferences
import com.example.data.preferences.NexaPreferencesRepository
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.BuildHistoryScreen
import com.example.ui.screens.BuildScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.GitHubScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.PreviewScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NexaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen {
    HOME,
    PROJECTS,
    BUILD,
    EDITOR,
    BUILD_HISTORY,
    GITHUB,
    DOWNLOADS,
    NOTIFICATIONS,
    SETTINGS,
    ABOUT,
    AI_ASSISTANT,
    PREVIEW
}

class MainActivity : ComponentActivity() {

    private lateinit var database: NexaDatabase
    private lateinit var preferencesRepo: NexaPreferencesRepository
    private lateinit var fileManager: ProjectFileManager
    private lateinit var buildEngine: BuildEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        database = NexaDatabase.getInstance(this)
        preferencesRepo = NexaPreferencesRepository(this)
        fileManager = ProjectFileManager(this)
        buildEngine = BuildEngine(this, database, fileManager)

        // Security: Disable WebView debugging in release builds, enable only in debug builds
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            android.webkit.WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        }

        setContent {
            val preferences by preferencesRepo.preferencesFlow.collectAsState(initial = NexaPreferences())
            val isDark = when (preferences.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> true
            }

            NexaTheme(darkTheme = isDark) {
                NexaMainApp(
                    database = database,
                    preferencesRepo = preferencesRepo,
                    fileManager = fileManager,
                    buildEngine = buildEngine,
                    preferences = preferences
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexaMainApp(
    database: NexaDatabase,
    preferencesRepo: NexaPreferencesRepository,
    fileManager: ProjectFileManager,
    buildEngine: BuildEngine,
    preferences: NexaPreferences
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var activeProject by remember { mutableStateOf<ProjectEntity?>(null) }

    // Live Database flows
    val projects by database.projectDao().getAllProjects().collectAsState(initial = emptyList())
    val builds by database.buildDao().getAllBuilds().collectAsState(initial = emptyList())
    val artifacts by database.buildArtifactDao().getAllArtifacts().collectAsState(initial = emptyList())
    val githubConnection by database.gitHubConnectionDao().getConnectionFlow().collectAsState(initial = null)
    val notifications by database.notificationDao().getAllNotifications().collectAsState(initial = emptyList())
    val unreadNotificationsCount by database.notificationDao().getUnreadCount().collectAsState(initial = 0)
    val successfulBuildsCount by database.buildDao().getSuccessfulBuildsCount().collectAsState(initial = 0)
    val failedBuildsCount by database.buildDao().getFailedBuildsCount().collectAsState(initial = 0)

    val buildState by buildEngine.buildState.collectAsState(initial = BuildProgressState.Idle)

    // Storage usage
    var storageUsageBytes by remember { mutableStateOf(0L) }
    fun refreshStorage() {
        storageUsageBytes = fileManager.getTotalStorageUsageBytes()
    }

    // Auto-seed initial project on first launch and migrate any legacy plain tokens
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            refreshStorage()
            // Keystore Token Security Migration: remove plaintext token from Room
            val conn = database.gitHubConnectionDao().getConnection()
            if (conn != null && conn.token.isNotBlank() && !conn.token.startsWith("SECURE_") && !conn.token.contains("••••")) {
                com.example.data.security.SecureTokenManager.migrateLegacyTokenIfNeeded(context, conn.token)
                database.gitHubConnectionDao().saveConnection(conn.copy(token = "SECURE_KEYSTORE_ENCRYPTED"))
            }
        }
    }

    LaunchedEffect(projects) {
        if (projects.isEmpty()) {
            withContext(Dispatchers.IO) {
                val starterProject = fileManager.createProject(
                    name = "QuickStart App",
                    packageName = "com.example.quickstart",
                    templateType = "Kotlin Android Project",
                    language = "Kotlin"
                )
                database.projectDao().insertProject(starterProject)
                database.notificationDao().insertNotification(
                    NotificationEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        title = "Welcome to Nexa IDE!",
                        message = "Created initial QuickStart App template for you. Open it in the editor to start coding.",
                        type = "SYSTEM"
                    )
                )
            }
        }
        if (activeProject == null && projects.isNotEmpty()) {
            activeProject = projects.first()
        }
    }

    // ZIP Import File Picker
    val importZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                        val imported = fileManager.importProjectFromZip(inputStream, "ImportedApp_${System.currentTimeMillis() % 1000}")
                        if (imported != null) {
                            database.projectDao().insertProject(imported)
                            withContext(Dispatchers.Main) {
                                activeProject = imported
                                Toast.makeText(context, "Project imported successfully!", Toast.LENGTH_SHORT).show()
                                currentScreen = Screen.PROJECTS
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to import ZIP: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Proper Android Back-Button Handling
    BackHandler(enabled = drawerState.isOpen || currentScreen != Screen.HOME) {
        if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        } else if (currentScreen != Screen.HOME) {
            currentScreen = Screen.HOME
        }
    }

    var htmlProjectToConfigure by remember { mutableStateOf<ProjectEntity?>(null) }
    val handleQuickBuild = { proj: ProjectEntity ->
        if (proj.language.equals("Webapp", ignoreCase = true) || proj.templateType.contains("HTML", ignoreCase = true)) {
            htmlProjectToConfigure = proj
        } else {
            activeProject = proj
            currentScreen = Screen.BUILD
        }
    }

    // If on full-screen Editor, show dedicated editor without bottom bar or drawer
    if (currentScreen == Screen.EDITOR && activeProject != null) {
        EditorScreen(
            project = activeProject!!,
            fileManager = fileManager,
            onBack = { currentScreen = Screen.PROJECTS },
            onQuickBuild = { proj -> handleQuickBuild(proj) }
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                // Drawer Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = NeonCyan)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Nexa IDE",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Mobile IDE & APK Builder",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Navigation Drawer Items
                DrawerMenuItem(
                    label = "My Projects",
                    icon = Icons.Default.Folder,
                    selected = currentScreen == Screen.PROJECTS,
                    onClick = {
                        currentScreen = Screen.PROJECTS
                        coroutineScope.launch { drawerState.close() }
                    }
                )

                DrawerMenuItem(
                    label = "Code Editor",
                    icon = Icons.Default.Code,
                    selected = currentScreen == Screen.EDITOR,
                    onClick = {
                        if (activeProject != null) {
                            currentScreen = Screen.EDITOR
                        } else if (projects.isNotEmpty()) {
                            activeProject = projects.first()
                            currentScreen = Screen.EDITOR
                        } else {
                            Toast.makeText(context, "Please create a project first", Toast.LENGTH_SHORT).show()
                        }
                        coroutineScope.launch { drawerState.close() }
                    }
                )

                DrawerMenuItem(
                    label = "Build History",
                    icon = Icons.Default.History,
                    selected = currentScreen == Screen.BUILD_HISTORY,
                    onClick = {
                        currentScreen = Screen.BUILD_HISTORY
                        coroutineScope.launch { drawerState.close() }
                    }
                )

                DrawerMenuItem(
                    label = "GitHub",
                    icon = Icons.Default.CloudDone,
                    selected = currentScreen == Screen.GITHUB,
                    onClick = {
                        currentScreen = Screen.GITHUB
                        coroutineScope.launch { drawerState.close() }
                    }
                )

                DrawerMenuItem(
                    label = "Downloads",
                    icon = Icons.Default.Storage,
                    selected = currentScreen == Screen.DOWNLOADS,
                    onClick = {
                        currentScreen = Screen.DOWNLOADS
                        coroutineScope.launch { drawerState.close() }
                    }
                )

                DrawerMenuItem(
                    label = "Notifications",
                    icon = Icons.Default.Notifications,
                    badgeCount = unreadNotificationsCount,
                    selected = currentScreen == Screen.NOTIFICATIONS,
                    onClick = {
                        currentScreen = Screen.NOTIFICATIONS
                        coroutineScope.launch { drawerState.close() }
                    }
                )

                DrawerMenuItem(
                    label = "Settings",
                    icon = Icons.Default.Settings,
                    selected = currentScreen == Screen.SETTINGS,
                    onClick = {
                        currentScreen = Screen.SETTINGS
                        coroutineScope.launch { drawerState.close() }
                    }
                )

                DrawerMenuItem(
                    label = "Live Preview Studio",
                    icon = Icons.Default.Preview,
                    selected = currentScreen == Screen.PREVIEW,
                    onClick = {
                        currentScreen = Screen.PREVIEW
                        coroutineScope.launch { drawerState.close() }
                    }
                )

                DrawerMenuItem(
                    label = "Nexa AI Assistant",
                    icon = Icons.Default.AutoAwesome,
                    selected = currentScreen == Screen.AI_ASSISTANT,
                    onClick = {
                        currentScreen = Screen.AI_ASSISTANT
                        coroutineScope.launch { drawerState.close() }
                    }
                )

                DrawerMenuItem(
                    label = "About",
                    icon = Icons.Default.Info,
                    selected = currentScreen == Screen.ABOUT,
                    onClick = {
                        currentScreen = Screen.ABOUT
                        coroutineScope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (currentScreen in listOf(Screen.HOME, Screen.PROJECTS, Screen.BUILD)) {
                    TopAppBar(
                        title = {
                            Text(
                                text = when (currentScreen) {
                                    Screen.HOME -> "Nexa IDE"
                                    Screen.PROJECTS -> "Projects"
                                    Screen.BUILD -> "Build APK / AAB"
                                    else -> "Nexa IDE"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            IconButton(onClick = { currentScreen = Screen.NOTIFICATIONS }) {
                                BadgedBox(
                                    badge = {
                                        if (unreadNotificationsCount > 0) {
                                            Badge { Text("$unreadNotificationsCount") }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            },
            bottomBar = {
                if (currentScreen in listOf(Screen.HOME, Screen.PROJECTS, Screen.BUILD, Screen.SETTINGS)) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        NavigationBarItem(
                            selected = currentScreen == Screen.HOME,
                            onClick = { currentScreen = Screen.HOME },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home") },
                            modifier = Modifier.testTag("nav_home")
                        )
                        NavigationBarItem(
                            selected = currentScreen == Screen.PROJECTS,
                            onClick = { currentScreen = Screen.PROJECTS },
                            icon = { Icon(Icons.Default.Folder, contentDescription = "Projects") },
                            label = { Text("Projects") },
                            modifier = Modifier.testTag("nav_projects")
                        )
                        NavigationBarItem(
                            selected = currentScreen == Screen.BUILD,
                            onClick = { currentScreen = Screen.BUILD },
                            icon = { Icon(Icons.Default.Build, contentDescription = "Build") },
                            label = { Text("Build") },
                            modifier = Modifier.testTag("nav_build")
                        )
                        NavigationBarItem(
                            selected = currentScreen == Screen.SETTINGS,
                            onClick = { currentScreen = Screen.SETTINGS },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") },
                            modifier = Modifier.testTag("nav_settings")
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentScreen) {
                    Screen.HOME -> HomeScreen(
                        projects = projects,
                        builds = builds,
                        successfulBuildsCount = successfulBuildsCount,
                        failedBuildsCount = failedBuildsCount,
                        storageUsageBytes = storageUsageBytes,
                        githubConnection = githubConnection,
                        onOpenProject = { proj ->
                            activeProject = proj
                            currentScreen = Screen.EDITOR
                        },
                        onNewProjectClick = { currentScreen = Screen.PROJECTS },
                        onImportProjectClick = { importZipLauncher.launch(arrayOf("application/zip")) },
                        onQuickBuildClick = { proj -> handleQuickBuild(proj) },
                        onNavigateToProjects = { currentScreen = Screen.PROJECTS },
                        onNavigateToBuilds = { currentScreen = Screen.BUILD_HISTORY },
                        onNavigateToGitHub = { currentScreen = Screen.GITHUB }
                    )

                    Screen.PROJECTS -> ProjectsScreen(
                        projects = projects,
                        onOpenProject = { proj ->
                            activeProject = proj
                            currentScreen = Screen.EDITOR
                        },
                        onCreateProject = { name, pkg, template, lang ->
                            coroutineScope.launch(Dispatchers.IO) {
                                val newProj = fileManager.createProject(name, pkg, template, lang)
                                database.projectDao().insertProject(newProj)
                                refreshStorage()
                                withContext(Dispatchers.Main) {
                                    activeProject = newProj
                                    Toast.makeText(context, "Created ${newProj.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDuplicateProject = { proj, newName ->
                            coroutineScope.launch(Dispatchers.IO) {
                                val dup = fileManager.duplicateProject(proj, newName)
                                if (dup != null) {
                                    database.projectDao().insertProject(dup)
                                    refreshStorage()
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Duplicated to $newName", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        onRenameProject = { proj, newName ->
                            coroutineScope.launch(Dispatchers.IO) {
                                database.projectDao().updateProject(proj.copy(name = newName))
                            }
                        },
                        onDeleteProject = { proj ->
                            coroutineScope.launch(Dispatchers.IO) {
                                fileManager.deleteFile(proj.rootPath)
                                database.projectDao().deleteProject(proj)
                                refreshStorage()
                                if (activeProject?.id == proj.id) {
                                    activeProject = projects.firstOrNull { it.id != proj.id }
                                }
                            }
                        },
                        onExportProject = { proj ->
                            coroutineScope.launch(Dispatchers.IO) {
                                val zip = fileManager.exportProjectToZip(proj)
                                withContext(Dispatchers.Main) {
                                    if (zip != null) {
                                        Toast.makeText(context, "Exported: ${zip.name}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        onImportProjectClick = { importZipLauncher.launch(arrayOf("application/zip")) },
                        onQuickBuild = { proj -> handleQuickBuild(proj) }
                    )

                    Screen.BUILD -> BuildScreen(
                        projects = projects,
                        selectedProject = activeProject,
                        onSelectProject = { activeProject = it },
                        buildState = buildState,
                        latestBuild = builds.firstOrNull(),
                        latestArtifact = artifacts.firstOrNull(),
                        githubConnection = githubConnection,
                        onStartBuild = { proj, bType, jdk, gradle, compileSdk, clean ->
                            coroutineScope.launch {
                                buildEngine.triggerBuild(proj, bType, jdk, gradle, compileSdk, clean)
                                refreshStorage()
                            }
                        },
                        onCancelBuild = {
                            coroutineScope.launch { buildEngine.cancelCurrentBuild() }
                        },
                        onInstallApk = { buildEngine.installApk(it) },
                        onShareApk = { buildEngine.shareApk(it) },
                        onNavigateToHistory = { currentScreen = Screen.BUILD_HISTORY },
                        onNavigateToGitHub = { currentScreen = Screen.GITHUB }
                    )

                    Screen.BUILD_HISTORY -> BuildHistoryScreen(
                        builds = builds,
                        onBack = { currentScreen = Screen.HOME },
                        onDeleteBuild = { id ->
                            coroutineScope.launch(Dispatchers.IO) {
                                database.buildDao().deleteBuildById(id)
                            }
                        },
                        onClearAllBuilds = {
                            coroutineScope.launch(Dispatchers.IO) {
                                database.buildDao().clearAllBuilds()
                            }
                        },
                        onInstallApk = { buildEngine.installApk(it) },
                        onShareApk = { buildEngine.shareApk(it) },
                        onRebuild = { build ->
                            val proj = projects.find { it.id == build.projectId } ?: activeProject
                            if (proj != null) {
                                activeProject = proj
                                currentScreen = Screen.BUILD
                                coroutineScope.launch {
                                    buildEngine.triggerBuild(proj, build.buildType)
                                    refreshStorage()
                                }
                            }
                        }
                    )

                    Screen.GITHUB -> GitHubScreen(
                        connection = githubConnection,
                        currentProject = activeProject,
                        onSaveConnection = { username, token, activeRepo ->
                            // Securely store token in Keystore and store non-sensitive metadata in Room
                            com.example.data.security.SecureTokenManager.saveGitHubToken(context, token)
                            coroutineScope.launch(Dispatchers.IO) {
                                database.gitHubConnectionDao().saveConnection(
                                    GitHubConnectionEntity(
                                        username = username,
                                        token = "SECURE_KEYSTORE_ENCRYPTED",
                                        activeRepo = activeRepo
                                    )
                                )
                            }
                        },
                        onDisconnect = {
                            com.example.data.security.SecureTokenManager.clearGitHubToken(context)
                            coroutineScope.launch(Dispatchers.IO) {
                                database.gitHubConnectionDao().clearConnection()
                            }
                        },
                        onBack = { currentScreen = Screen.HOME }
                    )

                    Screen.DOWNLOADS -> DownloadsScreen(
                        artifacts = artifacts,
                        onBack = { currentScreen = Screen.HOME },
                        onInstallApk = { buildEngine.installApk(it) },
                        onShareApk = { buildEngine.shareApk(it) },
                        onDeleteArtifact = { art ->
                            coroutineScope.launch(Dispatchers.IO) {
                                File(art.localFilePath).delete()
                                database.buildArtifactDao().deleteArtifact(art)
                                refreshStorage()
                            }
                        }
                    )

                    Screen.NOTIFICATIONS -> NotificationsScreen(
                        notifications = notifications,
                        onBack = { currentScreen = Screen.HOME },
                        onMarkAsRead = { id ->
                            coroutineScope.launch(Dispatchers.IO) {
                                database.notificationDao().markAsRead(id)
                            }
                        },
                        onMarkAllAsRead = {
                            coroutineScope.launch(Dispatchers.IO) {
                                database.notificationDao().markAllAsRead()
                            }
                        },
                        onDeleteNotification = { id ->
                            coroutineScope.launch(Dispatchers.IO) {
                                database.notificationDao().deleteNotificationById(id)
                            }
                        },
                        onClearAll = {
                            coroutineScope.launch(Dispatchers.IO) {
                                database.notificationDao().clearAllNotifications()
                            }
                        }
                    )

                    Screen.SETTINGS -> SettingsScreen(
                        preferences = preferences,
                        storageUsageBytes = storageUsageBytes,
                        onSetThemeMode = { mode ->
                            coroutineScope.launch { preferencesRepo.setThemeMode(mode) }
                        },
                        onSetFontSize = { size ->
                            coroutineScope.launch { preferencesRepo.setEditorFontSize(size) }
                        },
                        onSetTabSize = { size ->
                            coroutineScope.launch { preferencesRepo.setEditorTabSize(size) }
                        },
                        onSetAutoSave = { autoSave ->
                            coroutineScope.launch { preferencesRepo.setEditorAutoSave(autoSave) }
                        },
                        onSetEditorTheme = { theme ->
                            coroutineScope.launch { preferencesRepo.setEditorTheme(theme) }
                        },
                        onClearCache = {
                            coroutineScope.launch(Dispatchers.IO) {
                                context.cacheDir.deleteRecursively()
                                refreshStorage()
                            }
                        },
                        onBack = { currentScreen = Screen.HOME }
                    )

                    Screen.ABOUT -> AboutScreen(
                        onBack = { currentScreen = Screen.HOME }
                    )

                    Screen.AI_ASSISTANT -> AiAssistantScreen(
                        currentFileName = activeProject?.name,
                        currentFileContent = null,
                        onBack = { currentScreen = Screen.HOME }
                    )

                    Screen.PREVIEW -> {
                        val proj = activeProject ?: projects.firstOrNull()
                        if (proj != null) {
                            PreviewScreen(
                                project = proj,
                                fileManager = fileManager,
                                onBack = { currentScreen = Screen.HOME },
                                onNavigateToBuild = {
                                    activeProject = it
                                    currentScreen = Screen.BUILD
                                }
                            )
                        } else {
                            Toast.makeText(context, "No project available to preview", Toast.LENGTH_SHORT).show()
                            currentScreen = Screen.HOME
                        }
                    }

                    Screen.EDITOR -> { /* Handled in dedicated condition above */ }
                }
            }
        }
    }

    htmlProjectToConfigure?.let { proj ->
        HtmlBuildCustomizeDialog(
            project = proj,
            onDismiss = { htmlProjectToConfigure = null },
            onBuildWithConfig = { updatedName, updatedPackage, enableJs, enableDom, enableZoom, showSplash, splashTitle ->
                coroutineScope.launch(Dispatchers.IO) {
                    val updatedProj = fileManager.updateHtmlProjectConfig(
                        proj, updatedName, updatedPackage, enableJs, enableDom, enableZoom, showSplash, splashTitle
                    )
                    database.projectDao().updateProject(updatedProj)
                    refreshStorage()
                    withContext(Dispatchers.Main) {
                        activeProject = updatedProj
                        htmlProjectToConfigure = null
                        currentScreen = Screen.BUILD
                    }
                }
            }
        )
    }
}

@Composable
private fun DrawerMenuItem(
    label: String,
    icon: ImageVector,
    badgeCount: Int = 0,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                if (badgeCount > 0) {
                    Spacer(modifier = Modifier.weight(1f))
                    Badge { Text("$badgeCount") }
                }
            }
        },
        icon = { Icon(icon, contentDescription = label) },
        selected = selected,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
    )
}
