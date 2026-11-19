package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberYellow
import com.example.ui.theme.CoralRed
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple

@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier) {
    val (bgColor, textColor, dotColor) = when (status.uppercase()) {
        "SUCCESSFUL", "DOWNLOADED" -> Triple(
            EmeraldGreen.copy(alpha = 0.15f),
            EmeraldGreen,
            EmeraldGreen
        )
        "FAILED", "ERROR" -> Triple(
            CoralRed.copy(alpha = 0.15f),
            CoralRed,
            CoralRed
        )
        "RUNNING", "DOWNLOADING" -> Triple(
            NeonCyan.copy(alpha = 0.15f),
            NeonCyan,
            NeonCyan
        )
        "QUEUED", "PENDING" -> Triple(
            AmberYellow.copy(alpha = 0.15f),
            AmberYellow,
            AmberYellow
        )
        "CANCELLED" -> Triple(
            Color.Gray.copy(alpha = 0.15f),
            Color.LightGray,
            Color.Gray
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Color.Gray
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, textColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = status,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun FileBadge(extension: String, modifier: Modifier = Modifier) {
    val (badgeColor, textColor) = when (extension.lowercase()) {
        "kt" -> Pair(NeonPurple.copy(alpha = 0.2f), NeonPurple)
        "java" -> Pair(AmberYellow.copy(alpha = 0.2f), AmberYellow)
        "xml" -> Pair(EmeraldGreen.copy(alpha = 0.2f), EmeraldGreen)
        "kts", "gradle" -> Pair(ElectricBlue.copy(alpha = 0.2f), ElectricBlue)
        "json" -> Pair(AmberYellow.copy(alpha = 0.2f), AmberYellow)
        "md" -> Pair(Color.Cyan.copy(alpha = 0.2f), Color.Cyan)
        "html", "htm" -> Pair(CoralRed.copy(alpha = 0.2f), CoralRed)
        "css" -> Pair(NeonCyan.copy(alpha = 0.2f), NeonCyan)
        "js" -> Pair(AmberYellow.copy(alpha = 0.2f), AmberYellow)
        else -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(badgeColor)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = extension.uppercase(),
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
