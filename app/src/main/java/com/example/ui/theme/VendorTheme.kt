package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

object VendorColors {
  val PrimaryOrange = Color(0xFFC2410C)
  val PrimaryOrangeTint = Color(0x1AC2410C) // 10% tint
  val PrimaryOrangeSoft = Color(0xFFFFF4EE)
  val PrimaryOrangeBorder = Color(0xFFFED7AA)

  val NavyHeader = Color(0xFF10284B)
  val NavyHeaderMuted = Color(0xFF94A3B8)
  val NavyHeaderSubtitle = Color(0xFFCBD5E1)

  val Background = Color(0xFFF4F6FA)
  val CardWhite = Color(0xFFFFFFFF)
  val BorderSubtle = Color(0xFFE6EAF2)
  val Divider = Color(0xFFEEF2F6)

  val TextPrimary = Color(0xFF0F172A)
  val TextSecondary = Color(0xFF5B6475)
  val TextMuted = Color(0xFF64748B)

  val SuccessGreen = Color(0xFF166534)
  val SuccessGreenBg = Color(0xFFDCFCE7)

  val WarningAmber = Color(0xFF92400E)
  val WarningAmberAccent = Color(0xFFD97706)
  val WarningAmberBg = Color(0xFFFEF3C7)

  val DangerRed = Color(0xFFB91C1C)
  val DangerRedBg = Color(0xFFFEE2E2)

  val NeutralBadgeText = Color(0xFF475569)
  val NeutralBadgeBg = Color(0xFFF1F5F9)
}

object VendorSpacing {
  val xxs = 4.dp
  val xs = 8.dp
  val sm = 12.dp
  val md = 16.dp
  val lg = 20.dp
  val xl = 24.dp
  val touchMin = 48.dp
}

object VendorShapes {
  val card = RoundedCornerShape(16.dp)
  val button = RoundedCornerShape(12.dp)
  val input = RoundedCornerShape(12.dp)
  val badge = RoundedCornerShape(8.dp)
  val iconBox = RoundedCornerShape(12.dp)
  val pill = RoundedCornerShape(50)
}

val PlusJakartaSansFontFamily = FontFamily(
  Font(R.font.plus_jakarta_sans, FontWeight.Normal),
  Font(R.font.plus_jakarta_sans, FontWeight.Medium),
  Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
  Font(R.font.plus_jakarta_sans, FontWeight.Bold),
  Font(R.font.plus_jakarta_sans, FontWeight.ExtraBold)
)

val VendorTypography = Typography(
  headlineMedium = TextStyle(
    fontFamily = PlusJakartaSansFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 30.sp,
    color = VendorColors.TextPrimary
  ),
  headlineSmall = TextStyle(
    fontFamily = PlusJakartaSansFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 26.sp,
    color = VendorColors.TextPrimary
  ),
  titleLarge = TextStyle(
    fontFamily = PlusJakartaSansFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    color = VendorColors.TextPrimary
  ),
  titleMedium = TextStyle(
    fontFamily = PlusJakartaSansFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    color = VendorColors.TextPrimary
  ),
  titleSmall = TextStyle(
    fontFamily = PlusJakartaSansFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    color = VendorColors.TextPrimary
  ),
  bodyLarge = TextStyle(
    fontFamily = PlusJakartaSansFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 22.sp,
    color = VendorColors.TextPrimary
  ),
  bodyMedium = TextStyle(
    fontFamily = PlusJakartaSansFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    color = VendorColors.TextPrimary
  ),
  bodySmall = TextStyle(
    fontFamily = PlusJakartaSansFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    color = VendorColors.TextSecondary
  ),
  labelLarge = TextStyle(
    fontFamily = PlusJakartaSansFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 18.sp,
    color = VendorColors.TextPrimary
  ),
  labelMedium = TextStyle(
    fontFamily = PlusJakartaSansFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    color = VendorColors.TextSecondary
  ),
  labelSmall = TextStyle(
    fontFamily = PlusJakartaSansFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    color = VendorColors.TextSecondary
  )
)

private val VendorLightColorScheme = lightColorScheme(
  primary = VendorColors.PrimaryOrange,
  onPrimary = Color.White,
  primaryContainer = VendorColors.PrimaryOrangeSoft,
  onPrimaryContainer = VendorColors.PrimaryOrange,
  secondary = VendorColors.NavyHeader,
  onSecondary = Color.White,
  background = VendorColors.Background,
  onBackground = VendorColors.TextPrimary,
  surface = VendorColors.CardWhite,
  onSurface = VendorColors.TextPrimary,
  surfaceVariant = VendorColors.Background,
  onSurfaceVariant = VendorColors.TextSecondary,
  outline = VendorColors.BorderSubtle,
  outlineVariant = VendorColors.BorderSubtle,
  error = VendorColors.DangerRed,
  onError = Color.White,
  errorContainer = VendorColors.DangerRedBg,
  onErrorContainer = VendorColors.DangerRed
)

@Composable
fun VendorCenterTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = VendorLightColorScheme,
    typography = VendorTypography,
    content = content
  )
}
