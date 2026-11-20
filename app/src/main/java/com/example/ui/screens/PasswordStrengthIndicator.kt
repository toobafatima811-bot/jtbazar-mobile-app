package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localization.appText

@Composable
fun PasswordStrengthIndicator(password: String) {
    if (password.isEmpty()) return
    val checks = listOf(
        password.length >= 8,
        password.any { it.isUpperCase() },
        password.any { it.isLowerCase() },
        password.any { it.isDigit() },
        password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
    )
    val score = checks.count { it }
    val label = when {
        score <= 1 -> appText("Weak", "کمزور")
        score <= 3 -> appText("Fair", "درمیانہ")
        score == 4 -> appText("Good", "اچھا")
        else -> appText("Strong", "مضبوط")
    }
    val tint = when {
        score <= 1 -> Color(0xFFB42318)
        score <= 3 -> Color(0xFFD97706)
        score == 4 -> Color(0xFFB89B22)
        else -> Color(0xFF16803C)
    }
    val labels = listOf(
        appText("At least 8 characters", "کم از کم 8 حروف"),
        appText("One uppercase letter", "ایک بڑا حرف"),
        appText("One lowercase letter", "ایک چھوٹا حرف"),
        appText("One number", "ایک نمبر"),
        appText("One special symbol", "ایک خاص علامت")
    )
    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(appText("Password strength", "پاس ورڈ کی مضبوطی"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tint)
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { score / 5f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = tint,
            trackColor = Color(0xFFE8E7E2)
        )
        Spacer(modifier = Modifier.height(7.dp))
        labels.forEachIndexed { index, text ->
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(if (checks[index]) "✓" else "○", color = if (checks[index]) Color(0xFF16803C) else Color.Gray, fontSize = 11.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text, color = if (checks[index]) Color(0xFF16803C) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}
