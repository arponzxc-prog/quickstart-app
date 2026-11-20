package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.AiChatMessage
import com.example.data.ai.AiCodeProposal
import com.example.data.ai.GeminiAiService
import com.example.data.ai.GeminiConnectionStatus
import com.example.data.security.SecureTokenManager
import com.example.ui.components.AiDiffDialog
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    currentFileName: String? = null,
    currentFileContent: String? = null,
    onApplyCodeToEditor: ((newCode: String) -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val aiService = remember { GeminiAiService(context) }

    var apiKeyDialog by remember { mutableStateOf(false) }
    var inputApiKey by remember { mutableStateOf("") }
    // "hasValidKey" only means a key is stored; readiness comes from connectionStatus below.
    var hasValidKey by remember { mutableStateOf(!aiService.getEffectiveApiKey().isNullOrBlank()) }
    var keyRevision by remember { mutableStateOf(0) }
    var connectionStatus by remember {
        mutableStateOf<GeminiConnectionStatus>(
            if (hasValidKey) GeminiConnectionStatus.Checking else GeminiConnectionStatus.NoKey
        )
    }

    fun runConnectionCheck() {
        coroutineScope.launch {
            connectionStatus = GeminiConnectionStatus.Checking
            connectionStatus = aiService.checkConnection()
        }
    }

    // Real API call (not just "a key exists") decides what the header shows.
    LaunchedEffect(hasValidKey, keyRevision) {
        if (hasValidKey) {
            connectionStatus = GeminiConnectionStatus.Checking
            connectionStatus = aiService.checkConnection()
        } else {
            connectionStatus = GeminiConnectionStatus.NoKey
        }
    }

    var messages by remember {
        mutableStateOf(
            listOf(
                AiChatMessage(
                    isUser = false,
                    text = "Hello! I am Nexa AI, your coding assistant. I can explain code, fix errors, refactor, generate tests, or help you with your Android & Web projects. How can I help you today?"
                )
            )
        )
    }

    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var activeProposalForDiff by remember { mutableStateOf<AiCodeProposal?>(null) }
    val listState = rememberLazyListState()

    fun sendPrompt(prompt: String, isCodeAction: Boolean = false, codeActionType: String = "") {
        if (prompt.isBlank()) return
        if (!hasValidKey) {
            apiKeyDialog = true
            return
        }

        val userMessage = AiChatMessage(isUser = true, text = prompt)
        messages = messages + userMessage
        inputText = ""
        isLoading = true

        coroutineScope.launch {
            try {
                if (isCodeAction && !currentFileContent.isNullOrBlank()) {
                    val fileName = currentFileName ?: "ActiveFile"
                    when (codeActionType) {
                        "FIX" -> {
                            val proposal = aiService.fixErrors(currentFileContent, prompt, fileName)
                            messages = messages + AiChatMessage(
                                isUser = false,
                                text = "Here is the proposed fix for '${fileName}':\n\n${proposal.summary}",
                                proposal = proposal
                            )
                        }
                        "REFACTOR" -> {
                            val proposal = aiService.refactorCode(currentFileContent, prompt, fileName)
                            messages = messages + AiChatMessage(
                                isUser = false,
                                text = "Here is the refactored code for '${fileName}':\n\n${proposal.summary}",
                                proposal = proposal
                            )
                        }
                        "EXPLAIN" -> {
                            val explanation = aiService.explainCode(currentFileContent, fileName)
                            messages = messages + AiChatMessage(isUser = false, text = explanation)
                        }
                        "REVIEW" -> {
                            val review = aiService.codeReview(currentFileContent, fileName)
                            messages = messages + AiChatMessage(isUser = false, text = review)
                        }
                        else -> {
                            val response = aiService.sendMessage(
                                prompt = prompt,
                                history = messages,
                                currentFileName = currentFileName,
                                currentFileContent = currentFileContent
                            )
                            messages = messages + AiChatMessage(isUser = false, text = response)
                        }
                    }
                } else {
                    val response = aiService.sendMessage(
                        prompt = prompt,
                        history = messages,
                        currentFileName = currentFileName,
                        currentFileContent = currentFileContent
                    )
                    messages = messages + AiChatMessage(isUser = false, text = response)
                }

                listState.animateScrollToItem(messages.size - 1)
            } catch (e: Exception) {
                messages = messages + AiChatMessage(
                    isUser = false,
                    text = "⚠️ Nexa AI Error: ${e.message}"
                )
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
            .imePadding()
            .testTag("ai_assistant_screen")
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(NeonPurple.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.clickable(enabled = hasValidKey) { runConnectionCheck() }) {
                        Text("Nexa AI Assistant", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        val status = connectionStatus
                        Text(
                            text = when (status) {
                                is GeminiConnectionStatus.Ready -> "${status.modelId} • Ready"
                                GeminiConnectionStatus.Checking -> "Checking Gemini connection…"
                                is GeminiConnectionStatus.Failed -> "Connection failed • tap to retry"
                                GeminiConnectionStatus.NoKey -> "API Key Required"
                            },
                            fontSize = 11.sp,
                            color = when (status) {
                                is GeminiConnectionStatus.Ready -> EmeraldGreen
                                is GeminiConnectionStatus.Failed -> CoralRed
                                else -> AmberYellow
                            }
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
                IconButton(onClick = { apiKeyDialog = true }) {
                    Icon(Icons.Default.Key, contentDescription = "Configure Gemini Key", tint = if (connectionStatus is GeminiConnectionStatus.Ready) EmeraldGreen else AmberYellow)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate900, titleContentColor = Color.White)
        )

        // Real connection failure (shows the actual, key-redacted API error)
        (connectionStatus as? GeminiConnectionStatus.Failed)?.let { failed ->
            Surface(
                color = CoralRed.copy(alpha = 0.15f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { runConnectionCheck() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CoralRed.copy(alpha = 0.5f))
            ) {
                Text(
                    text = failed.message,
                    fontSize = 12.sp,
                    color = CoralRed,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        // API Key missing banner
        if (!hasValidKey) {
            Surface(
                color = AmberYellow.copy(alpha = 0.15f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { apiKeyDialog = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AmberYellow.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = AmberYellow, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Enter your Google Gemini API key to activate AI chat and code actions. Tap to configure.",
                        fontSize = 12.sp,
                        color = AmberYellow,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Active File Context Indicator
        if (!currentFileName.isNullOrBlank()) {
            Surface(
                color = Slate900,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Slate800)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Context: $currentFileName",
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Quick Code Action Chips Bar
        if (!currentFileContent.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate900)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionChip("Explain Code", Icons.Default.Info) {
                    sendPrompt("Explain this code", isCodeAction = true, codeActionType = "EXPLAIN")
                }
                ActionChip("Fix Errors", Icons.Default.BugReport) {
                    sendPrompt("Find and fix any errors in this file", isCodeAction = true, codeActionType = "FIX")
                }
                ActionChip("Refactor & Clean", Icons.Default.AutoFixHigh) {
                    sendPrompt("Refactor this code for readability and performance", isCodeAction = true, codeActionType = "REFACTOR")
                }
                ActionChip("Code Review", Icons.Default.RateReview) {
                    sendPrompt("Perform a comprehensive code review", isCodeAction = true, codeActionType = "REVIEW")
                }
            }
        }

        // Message List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { msg ->
                MessageBubble(
                    message = msg,
                    onCopy = { text ->
                        clipboardManager.setText(AnnotatedString(text))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    onOpenDiff = { proposal ->
                        activeProposalForDiff = proposal
                    }
                )
            }

            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = NeonCyan)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Nexa AI is thinking...", fontSize = 12.sp, color = NeonCyan)
                    }
                }
            }
        }

        // Input Bar
        Surface(
            color = Slate900,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask Nexa AI a question or request a change...", fontSize = 13.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = Slate700
                    )
                )

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isLoading) {
                            sendPrompt(inputText)
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (inputText.isNotBlank() && !isLoading) NeonCyan else Slate800)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank() && !isLoading) Slate950 else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    // Safe Diff Dialog
    activeProposalForDiff?.let { proposal ->
        AiDiffDialog(
            proposal = proposal,
            aiService = aiService,
            onAccept = { newCode ->
                onApplyCodeToEditor?.invoke(newCode)
                Toast.makeText(context, "Applied changes to editor!", Toast.LENGTH_SHORT).show()
                activeProposalForDiff = null
            },
            onDismiss = { activeProposalForDiff = null }
        )
    }

    // Gemini API Key Config Dialog
    if (apiKeyDialog) {
        AlertDialog(
            onDismissRequest = { apiKeyDialog = false },
            title = { Text("Google Gemini API Key", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter your Google AI Studio Gemini API key. Keys are encrypted using Android Keystore and never stored in plaintext.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = inputApiKey,
                        onValueChange = { inputApiKey = it },
                        label = { Text("Gemini API Key (AIzaSy...)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (hasValidKey) {
                        Text(
                            text = "✓ Key configured in secure storage",
                            fontSize = 11.sp,
                            color = EmeraldGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputApiKey.isNotBlank()) {
                            SecureTokenManager.saveGeminiApiKey(context, inputApiKey)
                            hasValidKey = !aiService.getEffectiveApiKey().isNullOrBlank()
                            keyRevision += 1
                            apiKeyDialog = false
                            Toast.makeText(context, "Gemini API Key saved. Checking connection…", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("Save Key") }
            },
            dismissButton = {
                Row {
                    if (hasValidKey) {
                        TextButton(
                            onClick = {
                                SecureTokenManager.clearGeminiApiKey(context)
                                hasValidKey = !aiService.getEffectiveApiKey().isNullOrBlank()
                                keyRevision += 1
                                apiKeyDialog = false
                                Toast.makeText(context, "Gemini API Key cleared", Toast.LENGTH_SHORT).show()
                            }
                        ) { Text("Remove Key", color = CoralRed) }
                    }
                    TextButton(onClick = { apiKeyDialog = false }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun ActionChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Slate800,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate700)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text, fontSize = 11.sp, color = Color.White)
        }
    }
}

@Composable
private fun MessageBubble(
    message: AiChatMessage,
    onCopy: (String) -> Unit,
    onOpenDiff: (AiCodeProposal) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (message.isUser) 14.dp else 2.dp,
                bottomEnd = if (message.isUser) 2.dp else 14.dp
            ),
            color = if (message.isUser) NeonCyan.copy(alpha = 0.2f) else Slate900,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (message.isUser) NeonCyan.copy(alpha = 0.4f) else Slate800
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.text,
                    fontSize = 13.sp,
                    color = Color.White,
                    lineHeight = 18.sp
                )

                // If proposal is attached, display the Safe Diff Compare Button
                message.proposal?.let { proposal ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onOpenDiff(proposal) },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Compare Diff & Apply", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = { onCopy(message.proposal?.proposedCode ?: message.text) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
