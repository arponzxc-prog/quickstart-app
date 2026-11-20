package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.GeminiAiService
import com.example.data.ai.GeminiConfig
import com.example.data.ai.GeminiConnectionStatus
import com.example.ui.theme.NeonCyan
import kotlinx.coroutines.launch

/**
 * Settings card to choose, add and remove Gemini model names, and to test the selected one
 * with a real API call. Stored in GeminiConfig (not secret), so no other file needs changing
 * when a new model is released.
 */
@Composable
fun AiModelSettingsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var models by remember { mutableStateOf(GeminiConfig.getModels(context)) }
    var selected by remember { mutableStateOf(GeminiConfig.modelId(context)) }
    var newModel by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier.fillMaxWidth().testTag("ai_model_settings"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NeonCyan)
                Spacer(modifier = Modifier.padding(4.dp))
                Text("Nexa AI Model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                "Pick the Gemini model used by Nexa AI, or add a new model name when Google releases one.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            models.forEach { id ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = id == selected,
                        onClick = {
                            GeminiConfig.setModelOverride(context, if (id == GeminiConfig.DEFAULT_MODEL_ID) null else id)
                            selected = GeminiConfig.modelId(context)
                            testResult = null
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(id, fontSize = 14.sp, fontWeight = if (id == selected) FontWeight.Bold else FontWeight.Normal)
                        if (id == GeminiConfig.DEFAULT_MODEL_ID) {
                            Text("Default", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (id != GeminiConfig.DEFAULT_MODEL_ID) {
                        IconButton(onClick = {
                            GeminiConfig.removeModel(context, id)
                            models = GeminiConfig.getModels(context)
                            selected = GeminiConfig.modelId(context)
                            testResult = null
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove $id", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = newModel,
                onValueChange = { newModel = it; error = null },
                label = { Text("Add model name (e.g. ${GeminiConfig.DEFAULT_MODEL_ID})") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { msg -> { Text(msg) } },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val err = GeminiConfig.addModel(context, newModel)
                    if (err == null) {
                        // A newly added model becomes the selected one so it can be tested right away.
                        GeminiConfig.setModelOverride(context, GeminiConfig.normalizeModelId(newModel))
                        models = GeminiConfig.getModels(context)
                        selected = GeminiConfig.modelId(context)
                        newModel = ""
                        testResult = null
                    } else {
                        error = err
                    }
                }) { Text("Add & Select") }

                OutlinedButton(
                    enabled = !testing,
                    onClick = {
                        scope.launch {
                            testing = true
                            testResult = null
                            val status = GeminiAiService(context).checkConnection()
                            testResult = when (status) {
                                is GeminiConnectionStatus.Ready -> "Working: ${status.modelId} answered a real test request."
                                is GeminiConnectionStatus.Failed -> status.message
                                GeminiConnectionStatus.NoKey -> "No Gemini API key saved. Add it from the key icon in Nexa AI Assistant."
                                GeminiConnectionStatus.Checking -> ""
                            }
                            testing = false
                        }
                    }
                ) {
                    if (testing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Test selected")
                    }
                }
            }

            testResult?.let { Text(it, fontSize = 12.sp) }
        }
    }
}
