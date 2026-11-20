package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ai.AiCodeProposal
import com.example.data.ai.DiffLineType
import com.example.data.ai.GeminiAiService
import com.example.ui.theme.*

/**
 * Nexa AI Safe Diff Comparison Dialog
 *
 * Provides a clear visual diff comparing Original vs Proposed code.
 * User must explicitly click "Accept & Apply" to write changes to their file,
 * preventing any unwanted overwrites.
 */
@Composable
fun AiDiffDialog(
    proposal: AiCodeProposal,
    aiService: GeminiAiService,
    onAccept: (proposedCode: String) -> Unit,
    onDismiss: () -> Unit
) {
    val diffLines = remember(proposal) {
        aiService.computeDiff(proposal.originalCode, proposal.proposedCode)
    }

    val additionsCount = diffLines.count { it.type == DiffLineType.ADDED }
    val deletionsCount = diffLines.count { it.type == DiffLineType.REMOVED }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(16.dp),
            color = Slate900,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CompareArrows,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Nexa AI Code Proposal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = proposal.targetFileName ?: "Current File",
                                fontSize = 12.sp,
                                color = NeonCyan
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = EmeraldGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "+$additionsCount",
                                color = EmeraldGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            color = CoralRed.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "-$deletionsCount",
                                color = CoralRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Summary / Rationale card
                if (proposal.summary.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Slate950,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
                    ) {
                        Text(
                            text = proposal.summary,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Diff viewer list
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    color = Color(0xFF0d1117),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
                ) {
                    val horizontalScroll = rememberScrollState()

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScroll)
                            .padding(vertical = 4.dp)
                    ) {
                        items(diffLines) { line ->
                            val lineBg = when (line.type) {
                                DiffLineType.ADDED -> Color(0x3346c46e)
                                DiffLineType.REMOVED -> Color(0x33f85149)
                                DiffLineType.UNCHANGED -> Color.Transparent
                            }
                            val prefix = when (line.type) {
                                DiffLineType.ADDED -> "+"
                                DiffLineType.REMOVED -> "-"
                                DiffLineType.UNCHANGED -> " "
                            }
                            val prefixColor = when (line.type) {
                                DiffLineType.ADDED -> EmeraldGreen
                                DiffLineType.REMOVED -> CoralRed
                                DiffLineType.UNCHANGED -> Color.Gray
                            }
                            val textColor = when (line.type) {
                                DiffLineType.ADDED -> Color(0xFFe6edf3)
                                DiffLineType.REMOVED -> Color(0xFFffa198)
                                DiffLineType.UNCHANGED -> Color(0xFF8b949e)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(lineBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = prefix,
                                    color = prefixColor,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(16.dp)
                                )
                                Text(
                                    text = line.text,
                                    color = textColor,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reject Proposal")
                    }

                    Button(
                        onClick = {
                            onAccept(proposal.proposedCode)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Accept & Apply", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
