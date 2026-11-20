package com.example.ui.screens

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.filesystem.FileNode
import com.example.data.filesystem.ProjectFileManager
import com.example.data.local.entity.ProjectEntity
import com.example.ui.components.FileBadge
import com.example.ui.editor.CodeSyntaxHighlighter
import com.example.ui.theme.*
import java.io.File

data class OpenTab(
    val filePath: String,
    val fileName: String,
    val isModified: Boolean = false,
    val extension: String = "",
    val parentDirName: String = ""
)

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
    val context = LocalContext.current

    // Editor settings
    var currentTheme by remember { mutableStateOf("dark") }
    var currentFontSize by remember { mutableIntStateOf(14) }
    var isWordWrap by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    // File Tree State
    var fileTree by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    var showFileExplorerDrawer by remember { mutableStateOf(false) }

    // Tabs & Content State
    val openTabs = remember { mutableStateMapOf<String, OpenTab>() }
    var activeTabPath by remember { mutableStateOf<String?>(null) }
    val fileContents = remember { mutableStateMapOf<String, String>() }
    var editorText by remember { mutableStateOf("") }
    var tabToCloseWithoutSaving by remember { mutableStateOf<OpenTab?>(null) }

    // Undo & Redo History Stacks (up to 50 states)
    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }

    // Find and Replace Bar
    var showFindBar by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }

    // Go to Line Dialog
    var showGoToLineDialog by remember { mutableStateOf(false) }
    var goToLineInput by remember { mutableStateOf("") }

    // Nexa AI Assistant Modal
    var showAiAssistant by remember { mutableStateOf(false) }

    // Live Preview Studio Modal
    var showLivePreview by remember { mutableStateOf(false) }

    // Console drawer state
    var showConsole by remember { mutableStateOf(false) }
    var consoleLogs by remember { mutableStateOf("Nexa IDE Native Editor Ready.\nProject: ${project.name}\nReady for editing.\n") }

    // Dialogs for file explorer
    var folderForNewItem by remember { mutableStateOf<String?>(null) }
    var isCreatingFolder by remember { mutableStateOf(false) }
    var newItemName by remember { mutableStateOf("") }
    var itemToRename by remember { mutableStateOf<FileNode?>(null) }
    var itemToDelete by remember { mutableStateOf<FileNode?>(null) }

    val isWebappProject = project.language.equals("Webapp", ignoreCase = true) ||
            project.templateType.contains("HTML", ignoreCase = true)
    var showAllAndroidFiles by remember { mutableStateOf(!isWebappProject) }

    val activeExtension = remember(activeTabPath) {
        activeTabPath?.let { File(it).extension.lowercase() } ?: ""
    }

    val activeSyntaxHighlighter = remember(activeExtension, currentTheme) {
        CodeSyntaxHighlighter(extension = activeExtension, theme = currentTheme)
    }

    // Bracket balance checker for syntax feedback
    val bracketStatus = remember(editorText) {
        var braces = 0
        var parens = 0
        var brackets = 0
        for (c in editorText) {
            when (c) {
                '{' -> braces++
                '}' -> braces--
                '(' -> parens++
                ')' -> parens--
                '[' -> brackets++
                ']' -> brackets--
            }
        }
        if (braces == 0 && parens == 0 && brackets == 0) {
            "✓ Syntax Balanced" to EmeraldGreen
        } else {
            val errors = mutableListOf<String>()
            if (braces != 0) errors.add(if (braces > 0) "Missing $braces '}'" else "Extra ${-braces} '}'")
            if (parens != 0) errors.add(if (parens > 0) "Missing $parens ')'" else "Extra ${-parens} ')'")
            if (brackets != 0) errors.add(if (brackets > 0) "Missing $brackets ']'" else "Extra ${-brackets} ']'")
            ("⚠️ " + errors.joinToString(", ")) to AmberYellow
        }
    }

    fun updateEditorText(newVal: String, pushUndo: Boolean = true) {
        if (pushUndo && newVal != editorText) {
            if (undoStack.size >= 50) undoStack.removeAt(0)
            undoStack.add(editorText)
            redoStack.clear()
        }
        editorText = newVal
        activeTabPath?.let { path ->
            fileContents[path] = newVal
            openTabs[path]?.let { tab ->
                if (!tab.isModified) {
                    openTabs[path] = tab.copy(isModified = true)
                }
            }
        }
    }

    fun performUndo() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(editorText)
            editorText = previous
            activeTabPath?.let { path ->
                fileContents[path] = previous
            }
        }
    }

    fun performRedo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(editorText)
            editorText = next
            activeTabPath?.let { path ->
                fileContents[path] = next
            }
        }
    }

    fun refreshTree() {
        val rootDir = File(project.rootPath)
        fileTree = if (isWebappProject && !showAllAndroidFiles) {
            fileManager.getWebappFileTree(rootDir)
        } else {
            fileManager.getFileTree(rootDir)
        }
    }

    fun loadFileContent(path: String) {
        val file = File(path)
        if (file.exists() && file.isFile) {
            val content = fileContents[path] ?: fileManager.readFileContent(path).also { fileContents[path] = it }
            editorText = content
            undoStack.clear()
            redoStack.clear()
        } else {
            editorText = ""
        }
    }

    fun saveActiveFile(onComplete: (() -> Unit)? = null) {
        val path = activeTabPath
        if (path == null) {
            onComplete?.invoke()
            return
        }
        fileManager.writeFileContent(path, editorText)
        fileContents[path] = editorText
        openTabs[path]?.let { currentTab ->
            openTabs[path] = currentTab.copy(isModified = false)
        }
        consoleLogs += "✓ Saved: ${File(path).name}\n"
        Toast.makeText(context, "Saved ${File(path).name}", Toast.LENGTH_SHORT).show()
        onComplete?.invoke()
    }

    fun closeTab(path: String) {
        fileContents.remove(path)
        openTabs.remove(path)
        if (activeTabPath == path) {
            val nextPath = openTabs.keys.firstOrNull()
            activeTabPath = nextPath
            if (nextPath != null) {
                loadFileContent(nextPath)
            } else {
                editorText = ""
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
                    loadFileContent(canonical)
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
            loadFileContent(canonical)
        }
    }

    fun insertSymbol(symbol: String) {
        // Smart auto-closing bracket pairs
        val insertion = when (symbol) {
            "{" -> "{}"
            "(" -> "()"
            "[" -> "[]"
            "\"" -> "\"\""
            "'" -> "''"
            else -> symbol
        }
        updateEditorText(editorText + insertion)
    }

    fun formatCode() {
        val lines = editorText.lines()
        val formatted = lines.joinToString("\n") { it.trimEnd() }
        if (formatted != editorText) {
            updateEditorText(formatted)
            consoleLogs += "✓ Formatted: Cleaned line whitespace\n"
            Toast.makeText(context, "Code formatted", Toast.LENGTH_SHORT).show()
        }
    }

    // Initial load
    LaunchedEffect(project.id) {
        refreshTree()
        val projectDir = File(project.rootPath)
        val defaultFile = if (isWebappProject) {
            val webDir = fileManager.getWebappDirectory(projectDir)
            File(webDir, "index.html").takeIf { it.exists() }
                ?: webDir.walkTopDown().firstOrNull { it.isFile }
        } else {
            projectDir.walkTopDown()
                .filter { !it.absolutePath.contains("/.github") && !it.absolutePath.contains("/.gradle") && !it.absolutePath.contains("/build/") }
                .firstOrNull { it.isFile && (it.name == "MainActivity.kt" || it.name == "MainActivity.java") }
                ?: projectDir.walkTopDown()
                    .filter { !it.absolutePath.contains("/.github") && !it.absolutePath.contains("/.gradle") && !it.absolutePath.contains("/build/") }
                    .firstOrNull { it.isFile && it.name == "build.gradle.kts" }
                ?: projectDir.walkTopDown()
                    .filter { !it.absolutePath.contains("/.github") && !it.absolutePath.contains("/.gradle") && !it.absolutePath.contains("/build/") }
                    .firstOrNull { it.isFile }
        }

        openTabs.clear()
        fileContents.clear()

        if (isWebappProject && project.templateType == "HTML Multi-File Project") {
            val webDir = fileManager.getWebappDirectory(projectDir)
            listOf("style.css", "script.js").forEach { name ->
                val f = File(webDir, name)
                if (f.exists()) {
                    val canonical = f.canonicalPath
                    fileContents[canonical] = fileManager.readFileContent(canonical)
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
            loadFileContent(canonical)
        }
    }

    BackHandler {
        if (showLivePreview) {
            showLivePreview = false
        } else if (showAiAssistant) {
            showAiAssistant = false
        } else if (showFileExplorerDrawer) {
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

    // Theme colors
    val bgColor = when (currentTheme) {
        "dracula" -> Color(0xFF282a36)
        "light" -> Color.White
        else -> Color(0xFF0d1117)
    }
    val textColor = when (currentTheme) {
        "dracula" -> Color(0xFFf8f8f2)
        "light" -> Color(0xFF24292f)
        else -> Color(0xFFc9d1d9)
    }
    val gutterBg = when (currentTheme) {
        "dracula" -> Color(0xFF21222c)
        "light" -> Color(0xFFf6f8fa)
        else -> Color(0xFF161b22)
    }
    val gutterText = Color(0xFF8b949e)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
            .imePadding()
            .testTag("editor_screen")
    ) {
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
                // Nexa AI Coding Assistant Button
                IconButton(
                    onClick = { showAiAssistant = true },
                    modifier = Modifier.testTag("nexa_ai_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Nexa AI Assistant",
                        tint = NeonPurple
                    )
                }

                // Undo Button
                IconButton(
                    onClick = { performUndo() },
                    enabled = undoStack.isNotEmpty()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (undoStack.isNotEmpty()) Color.White else Color.Gray.copy(alpha = 0.5f)
                    )
                }

                // Redo Button
                IconButton(
                    onClick = { performRedo() },
                    enabled = redoStack.isNotEmpty()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (redoStack.isNotEmpty()) Color.White else Color.Gray.copy(alpha = 0.5f)
                    )
                }

                // File Explorer Toggle
                IconButton(onClick = { showFileExplorerDrawer = !showFileExplorerDrawer }) {
                    Icon(Icons.Default.Menu, contentDescription = "Toggle File Explorer", tint = NeonCyan)
                }

                // Find & Replace Toggle
                IconButton(onClick = { showFindBar = !showFindBar }) {
                    Icon(Icons.Default.Search, contentDescription = "Find & Replace")
                }

                // Save Active File
                IconButton(onClick = { saveActiveFile() }) {
                    Icon(Icons.Default.Save, contentDescription = "Save File")
                }

                // Live Preview Studio (HTML / CSS / JS / Webapp)
                IconButton(
                    onClick = {
                        saveActiveFile {
                            showLivePreview = true
                        }
                    },
                    modifier = Modifier.testTag("live_preview_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Live Preview Studio",
                        tint = NeonCyan
                    )
                }

                // Quick Build APK
                IconButton(onClick = {
                    saveActiveFile {
                        onQuickBuild(project)
                    }
                }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Build APK", tint = EmeraldGreen)
                }

                // Overflow Options Menu
                Box {
                    IconButton(onClick = { showOptionsMenu = !showOptionsMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Editor Options", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Live Preview Studio") },
                            leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null, tint = NeonCyan) },
                            onClick = {
                                showOptionsMenu = false
                                saveActiveFile {
                                    showLivePreview = true
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Nexa AI Assistant") },
                            leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NeonPurple) },
                            onClick = {
                                showOptionsMenu = false
                                showAiAssistant = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Go to Line...") },
                            leadingIcon = { Icon(Icons.Default.Navigation, contentDescription = null) },
                            onClick = {
                                showOptionsMenu = false
                                showGoToLineDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Format Code") },
                            leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null) },
                            onClick = {
                                showOptionsMenu = false
                                formatCode()
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
                                currentFontSize = if (currentFontSize >= 22) 12 else currentFontSize + 2
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

        // Find and Replace Header Bar with Real Replace & Replace All
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
                            placeholder = { Text("Replace with...", fontSize = 12.sp) },
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

                    if (findText.isNotBlank()) {
                        val matchCount = editorText.split(findText).size - 1
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$matchCount match(es) found",
                                fontSize = 11.sp,
                                color = NeonCyan
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(
                                    onClick = {
                                        if (matchCount > 0) {
                                            val replaced = editorText.replaceFirst(findText, replaceText)
                                            updateEditorText(replaced)
                                        }
                                    },
                                    enabled = matchCount > 0
                                ) {
                                    Text("Replace", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        if (matchCount > 0) {
                                            val replaced = editorText.replace(findText, replaceText)
                                            updateEditorText(replaced)
                                            Toast.makeText(context, "Replaced $matchCount occurrences", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = matchCount > 0
                                ) {
                                    Text("Replace All", fontSize = 11.sp)
                                }
                            }
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
                                        loadFileContent(tab.filePath)
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

        // Central Native Compose Code Editor with Syntax Highlighting for 15+ Languages
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(bgColor)
        ) {
            val verticalScrollState = rememberScrollState()
            val horizontalScrollState = rememberScrollState()

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
                    .then(if (!isWordWrap) Modifier.horizontalScroll(horizontalScrollState) else Modifier)
            ) {
                // Line Number Gutter
                val lineCount = maxOf(1, editorText.lines().size)
                Column(
                    modifier = Modifier
                        .background(gutterBg)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    for (i in 1..lineCount) {
                        Text(
                            text = i.toString(),
                            fontSize = currentFontSize.sp,
                            fontFamily = FontFamily.Monospace,
                            color = gutterText,
                            lineHeight = (currentFontSize * 1.4).sp
                        )
                    }
                }

                // Native Compose Code Editor with Real-Time Syntax Highlighting
                BasicTextField(
                    value = editorText,
                    onValueChange = { newVal ->
                        updateEditorText(newVal)
                    },
                    visualTransformation = activeSyntaxHighlighter,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = currentFontSize.sp,
                        color = textColor,
                        lineHeight = (currentFontSize * 1.4).sp
                    ),
                    modifier = Modifier
                        .then(
                            if (isWordWrap) Modifier.weight(1f).fillMaxHeight()
                            else Modifier.fillMaxHeight().defaultMinSize(minWidth = 800.dp)
                        )
                        .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
                        .testTag("native_code_input"),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (editorText.isEmpty()) {
                                Text(
                                    text = if (activeTabPath != null) "File is empty. Start typing code here..." else "No file selected. Open the file tree (☰) to select or create a file.",
                                    color = Color.Gray.copy(alpha = 0.5f),
                                    fontSize = currentFontSize.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }

        // Quick Keyboard Accessory Symbols Row with Smart Bracket Insertion
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
                SymbolKeyButton("{ }") { insertSymbol("{") }
                SymbolKeyButton("( )") { insertSymbol("(") }
                SymbolKeyButton("[ ]") { insertSymbol("[") }
                SymbolKeyButton("\" \"") { insertSymbol("\"") }
                SymbolKeyButton("' '") { insertSymbol("'") }
                SymbolKeyButton(";") { insertSymbol(";") }
                SymbolKeyButton("=") { insertSymbol(" = ") }
                SymbolKeyButton(":") { insertSymbol(": ") }
                SymbolKeyButton(".") { insertSymbol(".") }
                SymbolKeyButton("<") { insertSymbol("<") }
                SymbolKeyButton(">") { insertSymbol(">") }
                SymbolKeyButton("/") { insertSymbol("/") }
                SymbolKeyButton("!") { insertSymbol("!") }
                SymbolKeyButton("&") { insertSymbol("&") }
                SymbolKeyButton("|") { insertSymbol("|") }
                SymbolKeyButton("$") { insertSymbol("$") }
                SymbolKeyButton("->") { insertSymbol(" -> ") }
            }
        }

        // Status Bar with Bracket Balance & File Stats
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
                        text = "Ln ${editorText.lines().size}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = bracketStatus.first,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = bracketStatus.second
                    )
                    Spacer(modifier = Modifier.width(10.dp))
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
                        text = "Console",
                        fontSize = 11.sp,
                        color = if (showConsole) NeonCyan else Color.Gray
                    )
                    Spacer(modifier = Modifier.width(4.dp))
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

    // Modal Bottom Sheet: Nexa AI Assistant
    if (showAiAssistant) {
        Dialog(
            onDismissRequest = { showAiAssistant = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AiAssistantScreen(
                currentFileName = activeTabPath?.let { File(it).name },
                currentFileContent = editorText,
                onApplyCodeToEditor = { newCode ->
                    updateEditorText(newCode)
                    saveActiveFile()
                    showAiAssistant = false
                },
                onBack = { showAiAssistant = false }
            )
        }
    }

    // Modal Fullscreen Dialog: Live Preview Studio
    if (showLivePreview) {
        Dialog(
            onDismissRequest = { showLivePreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            PreviewScreen(
                project = project,
                fileManager = fileManager,
                onBack = { showLivePreview = false },
                onNavigateToBuild = {
                    showLivePreview = false
                    onQuickBuild(it)
                }
            )
        }
    }

    // Dialog: Go to Line
    if (showGoToLineDialog) {
        AlertDialog(
            onDismissRequest = { showGoToLineDialog = false },
            title = { Text("Go to Line", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = goToLineInput,
                    onValueChange = { goToLineInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Line number (1 - ${editorText.lines().size})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val lineNum = goToLineInput.toIntOrNull()
                        if (lineNum != null && lineNum in 1..editorText.lines().size) {
                            showGoToLineDialog = false
                            Toast.makeText(context, "Jumped to line $lineNum", Toast.LENGTH_SHORT).show()
                            goToLineInput = ""
                        } else {
                            Toast.makeText(context, "Invalid line number", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("Go") }
            },
            dismissButton = {
                TextButton(onClick = { showGoToLineDialog = false }) { Text("Cancel") }
            }
        )
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
                TextButton(onClick = { folderForNewItem = null }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Rename
    itemToRename?.let { node ->
        var newName by remember { mutableStateOf(node.name) }
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        fileManager.renameFile(node.path, newName.trim())
                        refreshTree()
                        itemToRename = null
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Delete
    itemToDelete?.let { node ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete ${if (node.isDirectory) "Folder" else "File"}?") },
            text = { Text("Are you sure you want to delete '${node.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        fileManager.deleteFile(node.path)
                        refreshTree()
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Cancel") }
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
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
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
                .padding(start = (depth * 16).dp)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (node.isDirectory) {
                Icon(
                    imageVector = if (expanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = null,
                    tint = AmberYellow,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                FileBadge(extension = node.extension)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = node.name,
                fontSize = 13.sp,
                color = Color.White,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (node.isDirectory) {
                IconButton(
                    onClick = { onNewFileInFolder(node.path) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.NoteAdd, contentDescription = "New File", tint = Color.Gray, modifier = Modifier.size(14.dp))
                }
            }

            IconButton(
                onClick = { onRename(node) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = Color.Gray, modifier = Modifier.size(14.dp))
            }

            IconButton(
                onClick = { onDelete(node) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(14.dp))
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
    Surface(
        onClick = onClick,
        color = Slate800,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = symbol, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color.White)
        }
    }
}
