package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.filesystem.ProjectFileManager
import com.example.data.local.entity.ProjectEntity
import com.example.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ViewportPreset(val label: String, val widthDp: Int, val heightDp: Int) {
    MOBILE("Mobile (375x667)", 375, 667),
    TABLET("Tablet (768x1024)", 768, 1024),
    DESKTOP("Desktop (Responsive)", -1, -1),
    CUSTOM("Custom", 412, 850)
}

data class ConsoleLogItem(
    val level: String, // LOG, WARN, ERROR
    val message: String,
    val sourceId: String?,
    val lineNumber: Int,
    val time: String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
)

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    project: ProjectEntity,
    fileManager: ProjectFileManager,
    onBack: () -> Unit,
    onNavigateToBuild: (ProjectEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var selectedViewport by remember { mutableStateOf(ViewportPreset.MOBILE) }
    var customWidth by remember { mutableIntStateOf(375) }
    var customHeight by remember { mutableIntStateOf(667) }

    var reloadTrigger by remember { mutableIntStateOf(0) }
    var autoRefresh by remember { mutableStateOf(true) }

    // DevTools & Console State
    var showDevTools by remember { mutableStateOf(true) }
    var selectedDevToolTab by remember { mutableStateOf("CONSOLE") } // CONSOLE, SOURCE, ERRORS
    val consoleLogs = remember { mutableStateListOf<ConsoleLogItem>() }
    var pageLoadError by remember { mutableStateOf<String?>(null) }
    var renderedHtmlSource by remember { mutableStateOf("") }

    val webDir = remember(project.rootPath) {
        val root = File(project.rootPath)
        fileManager.getWebappDirectory(root)
    }

    val htmlContent = remember(reloadTrigger, webDir) {
        val indexFile = File(webDir, "index.html")
        if (indexFile.exists()) {
            val content = fileManager.readFileContent(indexFile.canonicalPath)
            renderedHtmlSource = content
            content
        } else {
            val fallback = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>${project.name}</title>
                    <style>
                        body { font-family: sans-serif; background: #0d1117; color: white; padding: 20px; text-align: center; }
                        h1 { color: #00d2ff; }
                    </style>
                </head>
                <body>
                    <h1>Nexa IDE Live Preview</h1>
                    <p>No <code>index.html</code> found in web directory (${webDir.name}).</p>
                    <p>Create an <code>index.html</code> file in the editor to see live web preview.</p>
                </body>
                </html>
            """.trimIndent()
            renderedHtmlSource = fallback
            fallback
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
            .testTag("preview_screen")
    ) {
        TopAppBar(
            title = {
                Column {
                    Text("Live Preview Studio", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${project.name} • ${selectedViewport.label}",
                        fontSize = 11.sp,
                        color = NeonCyan
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                // Refresh Preview Button
                IconButton(onClick = {
                    consoleLogs.clear()
                    pageLoadError = null
                    reloadTrigger++
                    Toast.makeText(context, "Preview refreshed", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NeonCyan)
                }

                // Build Real APK Button
                Button(
                    onClick = { onNavigateToBuild(project) },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Build APK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate900, titleContentColor = Color.White)
        )

        // Viewport Switcher Toolbar
        Surface(
            color = Slate900,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedViewport == ViewportPreset.MOBILE,
                    onClick = { selectedViewport = ViewportPreset.MOBILE },
                    label = { Text("Mobile", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )
                FilterChip(
                    selected = selectedViewport == ViewportPreset.TABLET,
                    onClick = { selectedViewport = ViewportPreset.TABLET },
                    label = { Text("Tablet", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Tablet, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )
                FilterChip(
                    selected = selectedViewport == ViewportPreset.DESKTOP,
                    onClick = { selectedViewport = ViewportPreset.DESKTOP },
                    label = { Text("Desktop", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.DesktopWindows, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = autoRefresh,
                        onCheckedChange = { autoRefresh = it },
                        modifier = Modifier.size(28.dp)
                    )
                    Text("Auto-refresh", fontSize = 11.sp, color = Color.LightGray)
                }
            }
        }

        // Preview Accuracy Notice Banner
        Surface(
            color = Slate950,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Web Runtime Preview (HTML/CSS/JS). Native Kotlin/Compose code requires building the APK to test on device.",
                    fontSize = 11.sp,
                    color = Color.LightGray
                )
            }
        }

        // Central Responsive Preview Stage
        Box(
            modifier = Modifier
                .weight(if (showDevTools) 0.60f else 1f)
                .fillMaxWidth()
                .background(Color(0xFF070a0e)),
            contentAlignment = Alignment.Center
        ) {
            val frameModifier = when (selectedViewport) {
                ViewportPreset.MOBILE -> Modifier
                    .width(375.dp)
                    .fillMaxHeight(0.96f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(4.dp, Slate700, RoundedCornerShape(20.dp))

                ViewportPreset.TABLET -> Modifier
                    .width(600.dp)
                    .fillMaxHeight(0.96f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(6.dp, Slate700, RoundedCornerShape(16.dp))

                ViewportPreset.DESKTOP -> Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Slate800, RoundedCornerShape(8.dp))

                ViewportPreset.CUSTOM -> Modifier
                    .width(customWidth.dp)
                    .height(customHeight.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Slate700, RoundedCornerShape(12.dp))
            }

            AndroidView(
                modifier = frameModifier.background(Color.White),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true

                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                consoleMessage?.let {
                                    val level = when (it.messageLevel()) {
                                        ConsoleMessage.MessageLevel.ERROR -> "ERROR"
                                        ConsoleMessage.MessageLevel.WARNING -> "WARN"
                                        else -> "LOG"
                                    }
                                    consoleLogs.add(
                                        ConsoleLogItem(
                                            level = level,
                                            message = it.message(),
                                            sourceId = it.sourceId(),
                                            lineNumber = it.lineNumber()
                                        )
                                    )
                                }
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                pageLoadError = error?.description?.toString() ?: "Failed to load resource"
                            }
                        }

                        val baseUrl = "file://${webDir.absolutePath}/"
                        loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                    val baseUrl = "file://${webDir.absolutePath}/"
                    webView.loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null)
                }
            )
        }

        // Developer Tools & Console Panel
        Surface(
            color = Slate900,
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (showDevTools) 0.40f else 0.05f)
                .border(1.dp, Slate800)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // DevTools Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                showDevTools = true
                                selectedDevToolTab = "CONSOLE"
                            }
                        ) {
                            Text(
                                "Console (${consoleLogs.size})",
                                fontSize = 11.sp,
                                fontWeight = if (selectedDevToolTab == "CONSOLE") FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedDevToolTab == "CONSOLE") NeonCyan else Color.Gray
                            )
                        }
                        TextButton(
                            onClick = {
                                showDevTools = true
                                selectedDevToolTab = "SOURCE"
                            }
                        ) {
                            Text(
                                "Source",
                                fontSize = 11.sp,
                                fontWeight = if (selectedDevToolTab == "SOURCE") FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedDevToolTab == "SOURCE") NeonCyan else Color.Gray
                            )
                        }
                        if (pageLoadError != null) {
                            TextButton(
                                onClick = {
                                    showDevTools = true
                                    selectedDevToolTab = "ERRORS"
                                }
                            ) {
                                Text(
                                    "Errors (!)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CoralRed
                                )
                            }
                        }
                    }

                    Row {
                        IconButton(onClick = { consoleLogs.clear() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.ClearAll, contentDescription = "Clear Console", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { showDevTools = !showDevTools }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = if (showDevTools) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = "Toggle DevTools",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // DevTools Body
                if (showDevTools) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF0d1117))
                            .padding(8.dp)
                    ) {
                        when (selectedDevToolTab) {
                            "CONSOLE" -> {
                                if (consoleLogs.isEmpty()) {
                                    Text("Console output is empty. JavaScript console.log() calls will appear here.", color = Color.Gray, fontSize = 11.sp)
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(consoleLogs) { log ->
                                            val levelColor = when (log.level) {
                                                "ERROR" -> CoralRed
                                                "WARN" -> AmberYellow
                                                else -> Color(0xFFc9d1d9)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "[${log.time}] [${log.level}] ${log.message}",
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = levelColor,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (log.lineNumber > 0) {
                                                    Text(
                                                        text = "Ln ${log.lineNumber}",
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = Color.Gray
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            "SOURCE" -> {
                                val scroll = rememberScrollState()
                                LazyColumn(modifier = Modifier.fillMaxSize().horizontalScroll(scroll)) {
                                    item {
                                        Text(
                                            text = renderedHtmlSource,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Color(0xFFc9d1d9)
                                        )
                                    }
                                }
                            }
                            "ERRORS" -> {
                                Text(
                                    text = pageLoadError ?: "No page load errors detected.",
                                    color = if (pageLoadError != null) CoralRed else EmeraldGreen,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
