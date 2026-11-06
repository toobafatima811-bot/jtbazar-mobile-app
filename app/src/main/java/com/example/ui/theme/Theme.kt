package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
  primary = JTOrange,
  onPrimary = Color.White,
  primaryContainer = JTOrangeContainer,
  onPrimaryContainer = JTOrangeDark,
  secondary = JTNavyPrimary,
  onSecondary = Color.White,
  secondaryContainer = JTNavyContainer,
  onSecondaryContainer = JTNavyPrimary,
  tertiary = JTEmerald,
  background = Color.White,
  surface = Color.White,
  onBackground = JTTextPrimary,
  onSurface = JTTextPrimary,
  surfaceVariant = JTSurfaceVariant,
  outline = JTBorder
)

// Ensure pure white background even if device has dark mode enabled
private val CleanColorScheme = lightColorScheme(
  primary = JTOrange,
  onPrimary = Color.White,
  primaryContainer = JTOrangeContainer,
  onPrimaryContainer = JTOrangeDark,
  secondary = JTNavyPrimary,
  onSecondary = Color.White,
  secondaryContainer = JTNavyContainer,
  onSecondaryContainer = JTNavyPrimary,
  tertiary = JTEmerald,
  background = Color.White,
  surface = Color.White,
  onBackground = JTTextPrimary,
  onSurface = JTTextPrimary,
  surfaceVariant = JTSurfaceVariant,
  outline = JTBorder
)

@Composable
fun JTBazarTheme(
  darkTheme: Boolean = false, // Always pure white background as explicitly requested
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = CleanColorScheme,
    typography = Typography,
    content = content
  )
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  JTBazarTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
