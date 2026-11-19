package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ProjectEntity
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

@Composable
fun HtmlBuildCustomizeDialog(
    project: ProjectEntity,
    onDismiss: () -> Unit,
    onBuildWithConfig: (
        updatedName: String,
        updatedPackage: String,
        enableJs: Boolean,
        enableDom: Boolean,
        enableZoom: Boolean,
        showSplash: Boolean,
        splashTitle: String
    ) -> Unit
) {
    var appName by remember { mutableStateOf(project.name) }
    var packageName by remember { mutableStateOf(project.packageName) }
    var enableJs by remember { mutableStateOf(true) }
    var enableDom by remember { mutableStateOf(true) }
    var enableZoom by remember { mutableStateOf(true) }
    var showSplash by remember { mutableStateOf(true) }
    var splashTitle by remember { mutableStateOf(project.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "HTML APK Build Configuration",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = NeonCyan
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Customize your app identity, features, and startup splash screen before compiling the APK.",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )

                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("App Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Package Name (e.g. com.my.htmlapp)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Splash Screen (Opening Screen)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showSplash,
                        onCheckedChange = { showSplash = it }
                    )
                    Text("Show opening splash screen image/title", fontSize = 13.sp)
                }

                if (showSplash) {
                    OutlinedTextField(
                        value = splashTitle,
                        onValueChange = { splashTitle = it },
                        label = { Text("Splash Screen Title / Logo Text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "App Features & WebView Characteristics",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = enableJs,
                        onCheckedChange = { enableJs = it }
                    )
                    Text("Enable JavaScript", fontSize = 13.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = enableDom,
                        onCheckedChange = { enableDom = it }
                    )
                    Text("Enable DOM Storage (localStorage / IndexedDB)", fontSize = 13.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = enableZoom,
                        onCheckedChange = { enableZoom = it }
                    )
                    Text("Enable Pinch Zoom Controls", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (appName.isNotBlank() && packageName.isNotBlank()) {
                        onBuildWithConfig(
                            appName.trim(),
                            packageName.trim(),
                            enableJs,
                            enableDom,
                            enableZoom,
                            showSplash,
                            splashTitle.trim().ifEmpty { appName }
                        )
                    }
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Text("Build APK Now", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
