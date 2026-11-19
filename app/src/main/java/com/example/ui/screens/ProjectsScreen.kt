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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ProjectEntity
import com.example.ui.theme.AmberYellow
import com.example.ui.theme.CoralRed
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    projects: List<ProjectEntity>,
    onOpenProject: (ProjectEntity) -> Unit,
    onCreateProject: (name: String, packageName: String, templateType: String, language: String) -> Unit,
    onDuplicateProject: (ProjectEntity, newName: String) -> Unit,
    onRenameProject: (ProjectEntity, newName: String) -> Unit,
    onDeleteProject: (ProjectEntity) -> Unit,
    onExportProject: (ProjectEntity) -> Unit,
    onImportProjectClick: () -> Unit,
    onQuickBuild: (ProjectEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var projectToRename by remember { mutableStateOf<ProjectEntity?>(null) }
    var projectToDelete by remember { mutableStateOf<ProjectEntity?>(null) }
    var projectToDuplicate by remember { mutableStateOf<ProjectEntity?>(null) }

    val filteredProjects = projects.filter { project ->
        val matchesQuery = project.name.contains(searchQuery, ignoreCase = true) ||
                project.packageName.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (selectedFilter) {
            "Kotlin" -> project.language.equals("Kotlin", ignoreCase = true)
            "Java" -> project.language.equals("Java", ignoreCase = true)
            "Webapp" -> project.language.equals("Webapp", ignoreCase = true) || project.templateType.contains("HTML", ignoreCase = true)
            else -> true
        }
        matchesQuery && matchesFilter
    }

    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    Box(modifier = modifier.fillMaxSize().testTag("projects_screen")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("project_search_input"),
                placeholder = { Text("Search projects or packages...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips & Import Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Kotlin", "Java", "Webapp").forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter) }
                        )
                    }
                }

                TextButton(
                    onClick = onImportProjectClick,
                    modifier = Modifier.testTag("import_zip_button")
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import ZIP", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Projects List
            if (filteredProjects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching projects found" else "No projects yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap the + button to create a new project",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredProjects) { project ->
                        ProjectItemCard(
                            project = project,
                            dateStr = dateFormat.format(Date(project.lastModifiedAt)),
                            onOpen = { onOpenProject(project) },
                            onBuild = { onQuickBuild(project) },
                            onRename = { projectToRename = project },
                            onDuplicate = { projectToDuplicate = project },
                            onExport = { onExportProject(project) },
                            onDelete = { projectToDelete = project }
                        )
                    }
                }
            }
        }

        // FAB: New Project
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("create_project_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create New Project")
        }
    }

    // Dialog: Create Project
    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, pkg, template, lang ->
                onCreateProject(name, pkg, template, lang)
                showCreateDialog = false
            }
        )
    }

    // Dialog: Rename Project
    projectToRename?.let { project ->
        var newName by remember { mutableStateOf(project.name) }
        AlertDialog(
            onDismissRequest = { projectToRename = null },
            title = { Text("Rename Project") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onRenameProject(project, newName.trim())
                            projectToRename = null
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { projectToRename = null }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Duplicate Project
    projectToDuplicate?.let { project ->
        var duplicateName by remember { mutableStateOf("${project.name} Copy") }
        AlertDialog(
            onDismissRequest = { projectToDuplicate = null },
            title = { Text("Duplicate Project") },
            text = {
                OutlinedTextField(
                    value = duplicateName,
                    onValueChange = { duplicateName = it },
                    label = { Text("New Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (duplicateName.isNotBlank()) {
                            onDuplicateProject(project, duplicateName.trim())
                            projectToDuplicate = null
                        }
                    }
                ) { Text("Duplicate") }
            },
            dismissButton = {
                TextButton(onClick = { projectToDuplicate = null }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Delete Project
    projectToDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete Project?") },
            text = {
                Text("Are you sure you want to delete '${project.name}'? All local files will be permanently removed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProject(project)
                        projectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProjectItemCard(
    project: ProjectEntity,
    dateStr: String,
    onOpen: () -> Unit,
    onBuild: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isKotlin = project.language.equals("Kotlin", ignoreCase = true)
    val isWebapp = project.language.equals("Webapp", ignoreCase = true) || project.templateType.contains("HTML", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("project_card_${project.id}")
            .clickable { onOpen() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isKotlin) NeonPurple.copy(alpha = 0.2f)
                        else if (isWebapp) NeonCyan.copy(alpha = 0.2f)
                        else AmberYellow.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isKotlin) "KT" else if (isWebapp) "WEB" else "JV",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (isKotlin) NeonPurple else if (isWebapp) NeonCyan else AmberYellow
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = project.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = project.templateType,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = " • $dateStr",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onBuild) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Build APK", tint = EmeraldGreen)
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open in Editor") },
                        leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onOpen()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDuplicate()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export ZIP") },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onExport()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = CoralRed) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = CoralRed) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, packageName: String, templateType: String, language: String) -> Unit
) {
    var name by remember { mutableStateOf("MyApplication") }
    var packageName by remember { mutableStateOf("com.example.myapplication") }
    var language by remember { mutableStateOf("Kotlin") }

    val kotlinTemplates = listOf(
        "Kotlin Android Project",
        "Empty Activity",
        "Basic Activity",
        "WebView Android Project"
    )
    val javaTemplates = listOf(
        "Java Android Project",
        "Empty Android Project",
        "Basic Activity",
        "WebView Android Project"
    )
    val webappTemplates = listOf(
        "HTML Single File Project",
        "HTML Multi-File Project"
    )

    var selectedTemplate by remember { mutableStateOf(kotlinTemplates[0]) }

    val currentTemplates = when (language) {
        "Webapp" -> webappTemplates
        "Java" -> javaTemplates
        else -> kotlinTemplates
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Android Project", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        packageName = "com.example.${it.lowercase().replace(" ", "")}"
                    },
                    label = { Text("App Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_project_name_input")
                )

                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Package Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_project_package_input")
                )

                // Language / Project Type selection
                Text("Project Type / Language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = language == "Kotlin",
                            onClick = {
                                language = "Kotlin"
                                selectedTemplate = kotlinTemplates[0]
                            }
                        )
                        Text("Kotlin", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = language == "Java",
                            onClick = {
                                language = "Java"
                                selectedTemplate = javaTemplates[0]
                            }
                        )
                        Text("Java", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = language == "Webapp",
                            onClick = {
                                language = "Webapp"
                                selectedTemplate = webappTemplates[0]
                            }
                        )
                        Text("Webapp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                    }
                }

                // Template selection
                Text(
                    text = if (language == "Webapp") "HTML Webapp Format" else "Project Template",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    currentTemplates.forEach { template ->
                        val isSelected = selectedTemplate == template
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedTemplate = template },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedTemplate = template }
                                )
                                Column(modifier = Modifier.padding(start = 4.dp)) {
                                    Text(
                                        text = template,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (template == "HTML Single File Project") {
                                        Text(
                                            text = "1. Single File: HTML, CSS & JS combined in index.html",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else if (template == "HTML Multi-File Project") {
                                        Text(
                                            text = "2. Multi-File: Separate index.html, style.css, script.js",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && packageName.isNotBlank()) {
                        onConfirm(name.trim(), packageName.trim(), selectedTemplate, language)
                    }
                },
                modifier = Modifier.testTag("confirm_create_project_button")
            ) {
                Text("Create Project")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
