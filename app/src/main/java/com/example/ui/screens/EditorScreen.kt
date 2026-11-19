package com.example.ui.screens

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.filesystem.FileNode
import com.example.data.filesystem.ProjectFileManager
import com.example.data.local.entity.ProjectEntity
import com.example.ui.components.FileBadge
import com.example.ui.components.NativeCodeEditor
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File

data class OpenTab(
    val filePath: String,
    val fileName: String,
    val isModified: Boolean = false,
    val extension: String = "",
    val parentDirName: String = ""
)

class WebAppInterface(
    val onContentChangedCallback: (String) -> Unit,
    val onCursorMovedCallback: (Int, Int) -> Unit,
    val onReadyCallback: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onContentChanged(content: String) {
        mainHandler.post {
            onContentChangedCallback(content)
        }
    }

    @JavascriptInterface
    fun onCursorMoved(line: Int, col: Int) {
        mainHandler.post {
            onCursorMovedCallback(line, col)
        }
    }

    @JavascriptInterface
    fun onEditorReady() {
        mainHandler.post {
            onReadyCallback()
        }
    }
}

private fun jsString(s: String): String = JSONObject.quote(s)

private fun languageFor(ext: String): String = when (ext.lowercase()) {
    "kt", "kts" -> "kotlin"
    "java" -> "java"
    "xml" -> "xml"
    "json" -> "json"
    "gradle" -> "groovy"
    "html", "htm" -> "html"
    "js", "ts" -> "javascript"
    "css" -> "css"
    "md", "markdown" -> "markdown"
    "py" -> "python"
    "php" -> "php"
    "sql" -> "sql"
    else -> "text"
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    project: ProjectEntity,
    fileManager: ProjectFileManager,
    onBack: () -> Unit,
    onQuickBuild: (ProjectEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    // Native (Compose) editor state
    var editorValue by remember { mutableStateOf(TextFieldValue("")) }
    var editorLanguage by remember { mutableStateOf("text") }
    val undoStack = remember { ArrayList<TextFieldValue>() }
    val redoStack = remember { ArrayList<TextFieldValue>() }

    // Editor settings
    var currentTheme by remember { mutableStateOf("dark") }
    var currentFontSize by remember { mutableIntStateOf(14) }
    var isWordWrap by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    // File Tree State
    var fileTree by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    var showFileExplorerDrawer by remember { mutableStateOf(false) }

    // Tabs State
    val openTabs = remember { mutableStateMapOf<String, OpenTab>() }
    var activeTabPath by remember { mutableStateOf<String?>(null) }
    var tabToCloseWithoutSaving by remember { mutableStateOf<OpenTab?>(null) }

    // Status bar state
    val cursorPosition = remember(editorValue.selection, editorValue.text) {
        val text = editorValue.text
        val end = editorValue.selection.end.coerceIn(0, text.length)
        var line = 1
        var lastBreak = -1
        for (i in 0 until end) {
            if (text[i] == '\n') { line++; lastBreak = i }
        }
        "Ln $line, Col ${end - lastBreak}"
    }

    // Find and Replace Bar
    var showFindBar by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }

    // Console drawer state
    var showConsole by remember { mutableStateOf(false) }
    var consoleLogs by remember { mutableStateOf("Nexa IDE Console Ready.\nInitialized project: ${project.name}\nReady for editing.\n") }

    // Dialogs for file explorer
    var folderForNewItem by remember { mutableStateOf<String?>(null) }
    var isCreatingFolder by remember { mutableStateOf(false) }
    var newItemName by remember { mutableStateOf("") }
    var itemToRename by remember { mutableStateOf<FileNode?>(null) }
    var itemToDelete by remember { mutableStateOf<FileNode?>(null) }

    val isWebappProject = project.language.equals("Webapp", ignoreCase = true) ||
            project.templateType.contains("HTML", ignoreCase = true)
    var showAllAndroidFiles by remember { mutableStateOf(!isWebappProject) }

    fun refreshTree() {
        val rootDir = File(project.rootPath)
        fileTree = if (isWebappProject && !showAllAndroidFiles) {
            fileManager.getWebappFileTree(rootDir)
        } else {
            fileManager.getFileTree(rootDir)
        }
    }

    fun markActiveTabModified() {
        activeTabPath?.let { path ->
            openTabs[path]?.let { tab ->
                if (!tab.isModified) openTabs[path] = tab.copy(isModified = true)
            }
        }
    }

    fun onEditorChange(newValue: TextFieldValue) {
        val old = editorValue
        if (newValue.text != old.text) {
            if (old.text.length < 400_000) {
                undoStack.add(old)
                if (undoStack.size > 100) undoStack.removeAt(0)
            }
            redoStack.clear()
            markActiveTabModified()
        }
        editorValue = newValue
    }

    fun loadFileIntoEditor(path: String) {
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            consoleLogs += "⚠ File not found: $path\n"
            return
        }
        val content = try {
            fileManager.readFileContent(path)
        } catch (e: Exception) {
            consoleLogs += "⚠ Could not read ${file.name}: ${e.message}\n"
            ""
        }
        undoStack.clear()
        redoStack.clear()
        editorLanguage = languageFor(file.extension)
        editorValue = TextFieldValue(content)
    }

    fun saveActiveFile(onComplete: (() -> Unit)? = null) {
        val path = activeTabPath
        if (path != null) {
            val tab = openTabs[path]
            if (tab != null && tab.isModified) {
                if (fileManager.writeFileContent(path, editorValue.text)) {
                    openTabs[path] = tab.copy(isModified = false)
                    consoleLogs += "✓ Saved: ${File(path).name}\n"
                } else {
                    consoleLogs += "⚠ Failed to save: ${File(path).name}\n"
                }
            }
        }
        onComplete?.invoke()
    }

    fun undoEdit() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(editorValue)
            editorValue = previous
            markActiveTabModified()
        }
    }

    fun redoEdit() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(editorValue)
            editorValue = next
            markActiveTabModified()
        }
    }

    fun selectAllText() {
        editorValue = editorValue.copy(selection = TextRange(0, editorValue.text.length))
    }

    fun formatCode() {
        val old = editorValue
        if (old.text.isBlank()) return
        val formatted = old.text.lines().joinToString("\n") { it.trimEnd().replace("\t", "    ") }
            .let { if (it.endsWith("\n")) it else it + "\n" }
        onEditorChange(TextFieldValue(formatted, TextRange(old.selection.end.coerceIn(0, formatted.length))))
    }

    fun findNextMatch(): Boolean {
        val query = findText
        if (query.isEmpty()) return false
        val text = editorValue.text
        val from = editorValue.selection.end.coerceIn(0, text.length)
        var index = text.indexOf(query, from, ignoreCase = true)
        if (index < 0) index = text.indexOf(query, 0, ignoreCase = true)
        if (index < 0) {
            consoleLogs += "No match found for: $query\n"
            return false
        }
        editorValue = editorValue.copy(selection = TextRange(index, index + query.length))
        return true
    }

    fun replaceCurrentMatch() {
        val query = findText
        if (query.isEmpty()) return
        val text = editorValue.text
        val sel = editorValue.selection
        val start = minOf(sel.start, sel.end).coerceIn(0, text.length)
        val end = maxOf(sel.start, sel.end).coerceIn(0, text.length)
        if (end > start && text.substring(start, end).equals(query, ignoreCase = true)) {
            val newText = text.replaceRange(start, end, replaceText)
            onEditorChange(TextFieldValue(newText, TextRange(start + replaceText.length)))
        }
        findNextMatch()
    }

    fun replaceAllMatches() {
        val query = findText
        if (query.isEmpty()) return
        val newText = editorValue.text.replace(query, replaceText, ignoreCase = true)
        if (newText != editorValue.text) {
            onEditorChange(TextFieldValue(newText, TextRange(0)))
        }
    }

    fun closeTab(path: String) {
        openTabs.remove(path)
        if (activeTabPath == path) {
            val nextPath = openTabs.keys.firstOrNull()
            activeTabPath = nextPath
            if (nextPath != null) {
                loadFileIntoEditor(nextPath)
            } else {
                undoStack.clear()
                redoStack.clear()
                editorValue = TextFieldValue("")
            }
        }
    }

    fun openFile(node: FileNode) {
        if (node.isDirectory) return
        val canonical = File(node.path).canonicalPath

        if (openTabs.containsKey(canonical)) {
            if (activeTabPath != canonical) {
                saveActiveFile {
                    activeTabPath = canonical
                    loadFileIntoEditor(canonical)
                }
            }
            return
        }

        saveActiveFile {
            val file = File(canonical)
            val parentName = file.parentFile?.name ?: ""
            val tab = OpenTab(
                filePath = canonical,
                fileName = file.name,
                extension = file.extension,
                parentDirName = parentName
            )
            openTabs[canonical] = tab
            activeTabPath = canonical
            loadFileIntoEditor(canonical)
        }
    }

    fun insertSymbol(symbol: String) {
        val v = editorValue
        val start = minOf(v.selection.start, v.selection.end).coerceIn(0, v.text.length)
        val end = maxOf(v.selection.start, v.selection.end).coerceIn(0, v.text.length)
        val newText = v.text.replaceRange(start, end, symbol)
        onEditorChange(TextFieldValue(newText, TextRange(start + symbol.length)))
    }

    // Load initial file tree and open default project file (index.html for webapps, MainActivity/build.gradle.kts for Android)
    LaunchedEffect(project.id) {
        val projectDir = File(project.rootPath)

        // Heavy disk work runs on a background thread so the UI never freezes
        val skipDirs = setOf("build", ".gradle", ".git", ".idea", "node_modules")
        val (tree, defaultFile) = withContext(Dispatchers.IO) {
            val t = if (isWebappProject && !showAllAndroidFiles) {
                fileManager.getWebappFileTree(projectDir)
            } else {
                fileManager.getFileTree(projectDir)
            }
            val df = if (isWebappProject) {
                val webDir = fileManager.getWebappDirectory(projectDir)
                File(webDir, "index.html").takeIf { it.exists() }
                    ?: webDir.walkTopDown().firstOrNull { it.isFile }
            } else {
                val walk = { projectDir.walkTopDown().onEnter { d -> d == projectDir || d.name !in skipDirs } }
                walk().firstOrNull {
                    it.isFile && (it.name == "MainActivity.kt" || it.name == "MainActivity.java" || it.name == "build.gradle.kts")
                } ?: walk().firstOrNull { it.isFile }
            }
            t to df
        }
        fileTree = tree

        openTabs.clear()

        // For Multi-File HTML Project, populate style.css and script.js in tabs too
        if (isWebappProject && project.templateType == "HTML Multi-File Project") {
            val webDir = fileManager.getWebappDirectory(projectDir)
            listOf("style.css", "script.js").forEach { name ->
                val f = File(webDir, name)
                if (f.exists()) {
                    val canonical = f.canonicalPath
                    openTabs[canonical] = OpenTab(
                        filePath = canonical,
                        fileName = f.name,
                        extension = f.extension,
                        parentDirName = f.parentFile?.name ?: ""
                    )
                }
            }
        }

        defaultFile?.let { file ->
            val canonical = file.canonicalPath
            val parentName = file.parentFile?.name ?: ""
            val tab = OpenTab(
                filePath = canonical,
                fileName = file.name,
                extension = file.extension,
                parentDirName = parentName
            )
            openTabs[canonical] = tab
            activeTabPath = canonical
            loadFileIntoEditor(canonical)
        }
    }

    // Android Hardware / Gesture Back Button Handling
    BackHandler {
        if (showFileExplorerDrawer) {
            showFileExplorerDrawer = false
        } else if (showFindBar) {
            showFindBar = false
        } else if (showConsole) {
            showConsole = false
        } else {
            saveActiveFile {
                onBack()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
            .navigationBarsPadding()
            .imePadding()
            .testTag("editor_screen")
    ) {
        // Top Toolbar
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    activeTabPath?.let { path ->
                        val currentTab = openTabs[path]
                        val displayName = if (currentTab != null && openTabs.values.count { it.fileName == currentTab.fileName } > 1 && currentTab.parentDirName.isNotEmpty()) {
                            "${currentTab.parentDirName}/${currentTab.fileName}"
                        } else {
                            File(path).name
                        }
                        Text(
                            text = displayName + if (currentTab?.isModified == true) " •" else "",
                            fontSize = 11.sp,
                            color = NeonCyan,
                            maxLines = 1
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = {
                    saveActiveFile {
                        onBack()
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { showFileExplorerDrawer = !showFileExplorerDrawer }) {
                    Icon(Icons.Default.Menu, contentDescription = "Toggle File Explorer", tint = NeonCyan)
                }
                IconButton(onClick = { showFindBar = !showFindBar }) {
                    Icon(Icons.Default.Search, contentDescription = "Find & Replace")
                }
                IconButton(onClick = { saveActiveFile() }) {
                    Icon(Icons.Default.Save, contentDescription = "Save File")
                }
                IconButton(onClick = {
                    saveActiveFile {
                        onQuickBuild(project)
                    }
                }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Build APK", tint = EmeraldGreen)
                }

                // Editor Options Menu
                Box {
                    IconButton(onClick = { showOptionsMenu = !showOptionsMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Editor Options", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Format Code") },
                            leadingIcon = { Icon(Icons.Default.FormatAlignLeft, contentDescription = null) },
                            onClick = {
                                showOptionsMenu = false
                                formatCode()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Select All") },
                            leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null) },
                            onClick = {
                                showOptionsMenu = false
                                selectAllText()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isWordWrap) "Disable Wrap" else "Enable Wrap") },
                            leadingIcon = { Icon(Icons.Default.WrapText, contentDescription = null) },
                            onClick = {
                                showOptionsMenu = false
                                isWordWrap = !isWordWrap
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Theme: ${currentTheme.replaceFirstChar { it.uppercase() }}") },
                            leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                            onClick = {
                                showOptionsMenu = false
                                currentTheme = when (currentTheme) {
                                    "dark" -> "dracula"
                                    "dracula" -> "light"
                                    else -> "dark"
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Font Size: ${currentFontSize}px") },
                            leadingIcon = { Icon(Icons.Default.FormatSize, contentDescription = null) },
                            onClick = {
                                showOptionsMenu = false
                                currentFontSize = if (currentFontSize >= 20) 12 else currentFontSize + 2
                            }
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Slate900,
                titleContentColor = Color.White
            )
        )

        // Find and Replace Header Bar
        AnimatedVisibility(visible = showFindBar) {
            Surface(
                color = Slate900,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Slate800)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = findText,
                            onValueChange = { findText = it },
                            placeholder = { Text("Find...", fontSize = 12.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = replaceText,
                            onValueChange = { replaceText = it },
                            placeholder = { Text("Replace...", fontSize = 12.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            singleLine = true
                        )
                        IconButton(
                            onClick = { showFindBar = false },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Find", modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            findNextMatch()
                        }) {
                            Text("Find Next", fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            replaceCurrentMatch()
                        }) {
                            Text("Replace", fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            replaceAllMatches()
                        }) {
                            Text("Replace All", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Multiple Editor Tabs Bar
        if (openTabs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate900)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                openTabs.values.forEach { tab ->
                    val isActive = tab.filePath == activeTabPath
                    val duplicateNameCount = openTabs.values.count { it.fileName == tab.fileName }
                    val label = if (duplicateNameCount > 1 && tab.parentDirName.isNotEmpty()) {
                        "${tab.parentDirName}/${tab.fileName}"
                    } else {
                        tab.fileName
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isActive) Slate800 else Color.Transparent)
                            .border(
                                1.dp,
                                if (isActive) NeonCyan.copy(alpha = 0.5f) else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                if (tab.filePath != activeTabPath) {
                                    saveActiveFile {
                                        activeTabPath = tab.filePath
                                        loadFileIntoEditor(tab.filePath)
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FileBadge(extension = tab.extension)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label + if (tab.isModified) " *" else "",
                            fontSize = 12.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isActive) Color.White else Color.Gray
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Tab",
                            modifier = Modifier
                                .size(14.dp)
                                .clickable {
                                    if (tab.isModified) {
                                        tabToCloseWithoutSaving = tab
                                    } else {
                                        closeTab(tab.filePath)
                                    }
                                },
                            tint = Color.Gray
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
        }

        // Central Code Editor (native Compose editor)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .heightIn(min = 200.dp)
        ) {
            if (activeTabPath == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0D1117)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Open a file from the file explorer (≡) to start editing",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            } else {
                NativeCodeEditor(
                    value = editorValue,
                    onValueChange = { onEditorChange(it) },
                    language = editorLanguage,
                    theme = currentTheme,
                    fontSize = currentFontSize,
                    wordWrap = isWordWrap,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("code_editor")
                )
            }
        }

        // Quick Keyboard Accessory Symbols Row
        Surface(
            color = Slate900,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SymbolKeyButton("Tab") { insertSymbol("    ") }
                SymbolKeyButton("{") { insertSymbol("{") }
                SymbolKeyButton("}") { insertSymbol("}") }
                SymbolKeyButton("(") { insertSymbol("(") }
                SymbolKeyButton(")") { insertSymbol(")") }
                SymbolKeyButton("[") { insertSymbol("[") }
                SymbolKeyButton("]") { insertSymbol("]") }
                SymbolKeyButton(";") { insertSymbol(";") }
                SymbolKeyButton("=") { insertSymbol(" = ") }
                SymbolKeyButton("\"") { insertSymbol("\"") }
                SymbolKeyButton(":") { insertSymbol(": ") }
                SymbolKeyButton(".") { insertSymbol(".") }
                SymbolKeyButton("<") { insertSymbol("<") }
                SymbolKeyButton(">") { insertSymbol(">") }
                SymbolKeyButton("/") { insertSymbol("/") }
                SymbolKeyButton("!") { insertSymbol("!") }

                // Undo, Redo & Format
                IconButton(
                    onClick = { undoEdit() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = NeonCyan, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = { redoEdit() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = NeonCyan, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = { formatCode() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.FormatAlignLeft, contentDescription = "Format", tint = NeonCyan, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Status Bar & Console Toggle Bar
        Surface(
            color = Slate950,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = cursorPosition,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    activeTabPath?.let {
                        Text(
                            text = File(it).extension.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showConsole = !showConsole }
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Console",
                        tint = if (showConsole) NeonCyan else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Build Console",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (showConsole) NeonCyan else Color.Gray
                    )
                    Icon(
                        imageVector = if (showConsole) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Bottom Console Drawer
        AnimatedVisibility(visible = showConsole) {
            Surface(
                color = Slate900,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .border(1.dp, Slate800)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Terminal Output",
                            style = MaterialTheme.typography.labelLarge,
                            color = NeonCyan
                        )
                        Row {
                            TextButton(onClick = { consoleLogs = "" }) {
                                Text("Clear", fontSize = 11.sp)
                            }
                            IconButton(
                                onClick = { showConsole = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Slate950)
                            .padding(8.dp)
                    ) {
                        LazyColumn {
                            item {
                                Text(
                                    text = consoleLogs,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.LightGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet: File Explorer
    if (showFileExplorerDrawer) {
        ModalBottomSheet(
            onDismissRequest = { showFileExplorerDrawer = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Slate900
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(16.dp)
            ) {
                val defaultFolder = if (isWebappProject && !showAllAndroidFiles) {
                    fileManager.getWebappDirectory(File(project.rootPath)).canonicalPath
                } else {
                    project.rootPath
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isWebappProject && !showAllAndroidFiles) "Web Files" else "Project Explorer",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (isWebappProject) {
                            Text(
                                text = if (project.templateType == "HTML Single File Project") "Single File (HTML+CSS+JS in index.html)" else "Multi-File (index.html, style.css, script.js)",
                                fontSize = 11.sp,
                                color = NeonCyan
                            )
                        }
                    }
                    Row {
                        IconButton(onClick = {
                            folderForNewItem = defaultFolder
                            isCreatingFolder = false
                        }) {
                            Icon(Icons.Default.NoteAdd, contentDescription = "New File", tint = NeonCyan)
                        }
                        IconButton(onClick = {
                            folderForNewItem = defaultFolder
                            isCreatingFolder = true
                        }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", tint = NeonCyan)
                        }
                    }
                }

                if (isWebappProject) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !showAllAndroidFiles,
                            onClick = {
                                showAllAndroidFiles = false
                                refreshTree()
                            },
                            label = { Text("Web Files Only", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                selectedLabelColor = NeonCyan
                            )
                        )
                        FilterChip(
                            selected = showAllAndroidFiles,
                            onClick = {
                                showAllAndroidFiles = true
                                refreshTree()
                            },
                            label = { Text("All Android Files", fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(fileTree) { node ->
                        FileTreeNodeItem(
                            node = node,
                            depth = 0,
                            onFileClick = { clickedFile ->
                                openFile(clickedFile)
                                showFileExplorerDrawer = false
                            },
                            onNewFileInFolder = { folderPath ->
                                folderForNewItem = folderPath
                                isCreatingFolder = false
                            },
                            onNewFolderInFolder = { folderPath ->
                                folderForNewItem = folderPath
                                isCreatingFolder = true
                            },
                            onRename = { itemToRename = it },
                            onDelete = { itemToDelete = it }
                        )
                    }
                }
            }
        }
    }

    // Dialog: Unsaved Changes before Closing Tab
    tabToCloseWithoutSaving?.let { tab ->
        AlertDialog(
            onDismissRequest = { tabToCloseWithoutSaving = null },
            title = { Text("Unsaved Changes") },
            text = { Text("File '${tab.fileName}' has unsaved changes. Do you want to save before closing?") },
            confirmButton = {
                Button(onClick = {
                    saveActiveFile {
                        closeTab(tab.filePath)
                        tabToCloseWithoutSaving = null
                    }
                }) {
                    Text("Save & Close")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        closeTab(tab.filePath)
                        tabToCloseWithoutSaving = null
                    }) {
                        Text("Discard", color = Color.Red)
                    }
                    TextButton(onClick = { tabToCloseWithoutSaving = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Dialog: New File / Folder
    folderForNewItem?.let { parentPath ->
        AlertDialog(
            onDismissRequest = { folderForNewItem = null },
            title = { Text(if (isCreatingFolder) "Create New Folder" else "Create New File") },
            text = {
                OutlinedTextField(
                    value = newItemName,
                    onValueChange = { newItemName = it },
                    label = { Text(if (isCreatingFolder) "Folder Name" else if (isWebappProject && !showAllAndroidFiles) "File Name (e.g. style.css, script.js, page.html)" else "File Name (e.g. Utils.kt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newItemName.isNotBlank()) {
                        if (isCreatingFolder) {
                            fileManager.createDirectory(parentPath, newItemName.trim())
                        } else {
                            val created = fileManager.createFile(parentPath, newItemName.trim())
                            if (created != null) {
                                openFile(
                                    FileNode(
                                        name = created.name,
                                        path = created.canonicalPath,
                                        isDirectory = false,
                                        extension = created.extension.lowercase()
                                    )
                                )
                            }
                        }
                        refreshTree()
                        newItemName = ""
                        folderForNewItem = null
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = {
                    newItemName = ""
                    folderForNewItem = null
                }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Delete File / Folder
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete ${if (item.isDirectory) "Folder" else "File"}?") },
            text = { Text("Are you sure you want to delete '${item.name}'? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        val canonical = File(item.path).canonicalPath
                        fileManager.deleteFile(canonical)
                        closeTab(canonical)
                        refreshTree()
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Rename File / Folder
    itemToRename?.let { item ->
        var renameInput by remember { mutableStateOf(item.name) }
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renameInput.isNotBlank()) {
                        val oldCanonical = File(item.path).canonicalPath
                        fileManager.renameFile(oldCanonical, renameInput.trim())
                        closeTab(oldCanonical)
                        refreshTree()
                        itemToRename = null
                    }
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FileTreeNodeItem(
    node: FileNode,
    depth: Int,
    onFileClick: (FileNode) -> Unit,
    onNewFileInFolder: (String) -> Unit,
    onNewFolderInFolder: (String) -> Unit,
    onRename: (FileNode) -> Unit,
    onDelete: (FileNode) -> Unit
) {
    var expanded by remember { mutableStateOf(depth < 2) }
    var menuOpen by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (node.isDirectory) {
                        expanded = !expanded
                    } else {
                        onFileClick(node)
                    }
                }
                .padding(start = (depth * 18).dp, top = 6.dp, bottom = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (node.isDirectory) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(20.dp))
                FileBadge(extension = node.extension)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (node.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                color = if (node.isDirectory) Color.White else Color.LightGray,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.Gray, modifier = Modifier.size(14.dp))
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    if (node.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("New File Here") },
                            leadingIcon = { Icon(Icons.Default.NoteAdd, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onNewFileInFolder(node.path)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("New Folder Here") },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onNewFolderInFolder(node.path)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRename(node)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color.Red) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                        onClick = {
                            menuOpen = false
                            onDelete(node)
                        }
                    )
                }
            }
        }

        if (node.isDirectory && expanded) {
            node.children.forEach { child ->
                FileTreeNodeItem(
                    node = child,
                    depth = depth + 1,
                    onFileClick = onFileClick,
                    onNewFileInFolder = onNewFileInFolder,
                    onNewFolderInFolder = onNewFolderInFolder,
                    onRename = onRename,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun SymbolKeyButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Slate800)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
