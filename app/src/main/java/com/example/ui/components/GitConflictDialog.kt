package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
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
import com.example.data.ai.DiffLine
import com.example.data.ai.DiffLineType
import com.example.ui.theme.*

/**
 * Nexa IDE Git Conflict Resolution Dialog
 *
 * Displays a clear comparison between Local file content and Remote GitHub file content.
 * Guarantees that neither version is silently overwritten.
 */
@Composable
fun GitConflictDialog(
    fileName: String,
    localContent: String,
    remoteContent: String,
    onKeepLocal: () -> Unit,
    onAcceptRemote: () -> Unit,
    onCancel: () -> Unit
) {
    val diffLines = remember(localContent, remoteContent) {
        val oldLines = localContent.lines()
        val newLines = remoteContent.lines()
        val list = mutableListOf<DiffLine>()
        var o = 0
        var n = 0
        while (o < oldLines.size || n < newLines.size) {
            val oldL = oldLines.getOrNull(o)
            val newL = newLines.getOrNull(n)
            if (oldL == newL && oldL != null) {
                list.add(DiffLine(DiffLineType.UNCHANGED, oldL, o + 1, n + 1))
                o++
                n++
            } else if (oldL != null && (newL == null || !newLines.contains(oldL))) {
                list.add(DiffLine(DiffLineType.REMOVED, oldL, o + 1, null))
                o++
            } else if (newL != null) {
                list.add(DiffLine(DiffLineType.ADDED, newL, null, n + 1))
                n++
            } else {
                break
            }
        }
        list
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(16.dp),
            color = Slate900,
            border = androidx.compose.foundation.BorderStroke(1.dp, AmberYellow)
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
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AmberYellow,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Git Conflict Detected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "File: $fileName",
                                fontSize = 12.sp,
                                color = AmberYellow
                            )
                        }
                    }

                    IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "This file has conflicting modifications locally and on GitHub. Review the differences below before deciding which version to keep.",
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Diff viewer
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
                                DiffLineType.ADDED -> Color(0x3346c46e) // Remote added
                                DiffLineType.REMOVED -> Color(0x33f85149) // Local removed
                                DiffLineType.UNCHANGED -> Color.Transparent
                            }
                            val prefix = when (line.type) {
                                DiffLineType.ADDED -> "+ (Remote) "
                                DiffLineType.REMOVED -> "- (Local)  "
                                DiffLineType.UNCHANGED -> "           "
                            }
                            val prefixColor = when (line.type) {
                                DiffLineType.ADDED -> EmeraldGreen
                                DiffLineType.REMOVED -> CoralRed
                                DiffLineType.UNCHANGED -> Color.Gray
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
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = line.text,
                                    color = if (line.type == DiffLineType.UNCHANGED) Color(0xFF8b949e) else Color.White,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
                    ) {
                        Text("Cancel", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onKeepLocal,
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                    ) {
                        Text("Keep Local Version", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onAcceptRemote,
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                    ) {
                        Text("Accept Remote", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
