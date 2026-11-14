package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.VendorColors
import com.example.ui.theme.VendorShapes
import com.example.ui.theme.VendorSpacing
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

enum class StatusTone {
  SUCCESS,
  WARNING,
  DANGER,
  PRIMARY,
  NEUTRAL
}

fun formatPkrPrice(raw: String?): String {
  val cleaned = raw?.trim().orEmpty()
  if (cleaned.isBlank() || cleaned == "null") return "PKR 0"
  val numeric = cleaned.replace(",", "").toDoubleOrNull() ?: return "PKR $cleaned"
  val formatter = NumberFormat.getNumberInstance(Locale.US)
  return if (numeric % 1.0 == 0.0) {
    formatter.maximumFractionDigits = 0
    "PKR ${formatter.format(numeric.roundToLong())}"
  } else {
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    "PKR ${formatter.format(numeric)}"
  }
}

fun formatPkrPrice(amount: Int): String {
  val formatter = NumberFormat.getIntegerInstance(Locale.US)
  return "PKR ${formatter.format(amount)}"
}

@Composable
fun AppCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  containerColor: Color = VendorColors.CardWhite,
  borderColor: Color = VendorColors.BorderSubtle,
  contentPadding: PaddingValues = PaddingValues(VendorSpacing.md),
  content: @Composable ColumnScope.() -> Unit
) {
  val cardModifier = if (onClick != null) {
    modifier.fillMaxWidth().clip(VendorShapes.card).clickable(onClick = onClick)
  } else {
    modifier.fillMaxWidth()
  }
  Card(
    modifier = cardModifier,
    shape = VendorShapes.card,
    colors = CardDefaults.cardColors(containerColor = containerColor),
    border = BorderStroke(1.dp, borderColor),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(contentPadding),
      content = content
    )
  }
}

@Composable
fun StatCard(
  icon: ImageVector,
  value: String,
  label: String,
  modifier: Modifier = Modifier,
  supportingText: String? = null,
  iconTint: Color = VendorColors.PrimaryOrange,
  iconBackground: Color = VendorColors.PrimaryOrangeTint,
  valueColor: Color = VendorColors.TextPrimary
) {
  AppCard(
    modifier = modifier,
    contentPadding = PaddingValues(VendorSpacing.md)
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(VendorShapes.iconBox)
        .background(iconBackground),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(22.dp)
      )
    }
    Spacer(modifier = Modifier.height(VendorSpacing.sm))
    Text(
      text = value,
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
      color = valueColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    Spacer(modifier = Modifier.height(VendorSpacing.xxs))
    Text(
      text = label,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
      color = VendorColors.TextPrimary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    if (!supportingText.isNullOrBlank()) {
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = supportingText,
        style = MaterialTheme.typography.bodySmall,
        color = VendorColors.TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
fun StatusBadge(
  text: String,
  tone: StatusTone,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null
) {
  val (bg, fg) = when (tone) {
    StatusTone.SUCCESS -> VendorColors.SuccessGreenBg to VendorColors.SuccessGreen
    StatusTone.WARNING -> VendorColors.WarningAmberBg to VendorColors.WarningAmber
    StatusTone.DANGER -> VendorColors.DangerRedBg to VendorColors.DangerRed
    StatusTone.PRIMARY -> VendorColors.PrimaryOrangeTint to VendorColors.PrimaryOrange
    StatusTone.NEUTRAL -> VendorColors.NeutralBadgeBg to VendorColors.NeutralBadgeText
  }
  Surface(
    modifier = modifier,
    shape = VendorShapes.badge,
    color = bg
  ) {
    Row(
      modifier = Modifier.padding(horizontal = VendorSpacing.xs, vertical = VendorSpacing.xxs),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(VendorSpacing.xxs)
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = fg,
          modifier = Modifier.size(14.dp)
        )
      }
      Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
fun PrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  enabled: Boolean = true,
  loading: Boolean = false,
  containerColor: Color = VendorColors.PrimaryOrange
) {
  Button(
    onClick = onClick,
    enabled = enabled && !loading,
    modifier = modifier.heightIn(min = VendorSpacing.touchMin),
    shape = VendorShapes.button,
    colors = ButtonDefaults.buttonColors(
      containerColor = containerColor,
      contentColor = Color.White,
      disabledContainerColor = containerColor.copy(alpha = 0.45f),
      disabledContentColor = Color.White.copy(alpha = 0.85f)
    ),
    contentPadding = PaddingValues(horizontal = VendorSpacing.md, vertical = VendorSpacing.sm)
  ) {
    if (loading) {
      CircularProgressIndicator(
        modifier = Modifier.size(18.dp),
        color = Color.White,
        strokeWidth = 2.dp
      )
      Spacer(modifier = Modifier.width(VendorSpacing.xs))
    } else if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(VendorSpacing.xs))
    }
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
fun SecondaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  enabled: Boolean = true,
  contentColor: Color = VendorColors.TextPrimary,
  borderColor: Color = VendorColors.BorderSubtle
) {
  OutlinedButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.heightIn(min = VendorSpacing.touchMin),
    shape = VendorShapes.button,
    border = BorderStroke(1.dp, if (enabled) borderColor else borderColor.copy(alpha = 0.5f)),
    colors = ButtonDefaults.outlinedButtonColors(
      containerColor = VendorColors.CardWhite,
      contentColor = contentColor,
      disabledContentColor = VendorColors.TextSecondary.copy(alpha = 0.5f)
    ),
    contentPadding = PaddingValues(horizontal = VendorSpacing.md, vertical = VendorSpacing.sm)
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(VendorSpacing.xs))
    }
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
fun ProductActionButton(
  text: String,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  tint: Color = VendorColors.TextPrimary,
  backgroundColor: Color = VendorColors.Background,
  borderColor: Color = VendorColors.BorderSubtle
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.heightIn(min = VendorSpacing.touchMin),
    shape = VendorShapes.button,
    color = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.5f),
    border = BorderStroke(1.dp, if (enabled) borderColor else borderColor.copy(alpha = 0.4f))
  ) {
    Column(
      modifier = Modifier.padding(horizontal = VendorSpacing.xxs, vertical = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = text,
        tint = if (enabled) tint else tint.copy(alpha = 0.45f),
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (enabled) tint else tint.copy(alpha = 0.45f),
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
fun SectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  actionLabel: String? = null,
  actionIcon: ImageVector? = null,
  onActionClick: (() -> Unit)? = null
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = VendorColors.TextPrimary
      )
      if (!subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = VendorColors.TextSecondary
        )
      }
    }
    if (!actionLabel.isNullOrBlank() && onActionClick != null) {
      TextButton(
        onClick = onActionClick,
        modifier = Modifier.heightIn(min = VendorSpacing.touchMin)
      ) {
        if (actionIcon != null) {
          Icon(
            imageVector = actionIcon,
            contentDescription = null,
            tint = VendorColors.PrimaryOrange,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(VendorSpacing.xxs))
        }
        Text(
          text = actionLabel,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = VendorColors.PrimaryOrange
        )
      }
    }
  }
}

@Composable
fun EmptyState(
  icon: ImageVector,
  title: String,
  description: String,
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  actionIcon: ImageVector? = null,
  onAction: (() -> Unit)? = null
) {
  AppCard(
    modifier = modifier,
    contentPadding = PaddingValues(VendorSpacing.xl)
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
    ) {
      Box(
        modifier = Modifier
          .size(56.dp)
          .clip(VendorShapes.iconBox)
          .background(VendorColors.PrimaryOrangeTint),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = VendorColors.PrimaryOrange,
          modifier = Modifier.size(28.dp)
        )
      }
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = VendorColors.TextPrimary,
        textAlign = TextAlign.Center
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = VendorColors.TextSecondary,
        textAlign = TextAlign.Center
      )
      if (!actionLabel.isNullOrBlank() && onAction != null) {
        Spacer(modifier = Modifier.height(VendorSpacing.xxs))
        PrimaryButton(
          text = actionLabel,
          icon = actionIcon,
          onClick = onAction
        )
      }
    }
  }
}

@Composable
fun ErrorState(
  message: String,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
  title: String = "Unable to load data",
  icon: ImageVector = Icons.Rounded.CloudOff,
  retryLabel: String = "Retry"
) {
  AppCard(
    modifier = modifier,
    borderColor = VendorColors.DangerRed.copy(alpha = 0.25f),
    contentPadding = PaddingValues(VendorSpacing.xl)
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
    ) {
      Box(
        modifier = Modifier
          .size(56.dp)
          .clip(VendorShapes.iconBox)
          .background(VendorColors.DangerRedBg),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = VendorColors.DangerRed,
          modifier = Modifier.size(28.dp)
        )
      }
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = VendorColors.TextPrimary,
        textAlign = TextAlign.Center
      )
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = VendorColors.TextSecondary,
        textAlign = TextAlign.Center
      )
      Spacer(modifier = Modifier.height(VendorSpacing.xxs))
      PrimaryButton(
        text = retryLabel,
        icon = Icons.Rounded.Refresh,
        onClick = onRetry
      )
    }
  }
}

@Composable
fun VendorAlertBanner(
  message: String,
  isError: Boolean,
  modifier: Modifier = Modifier,
  onDismiss: (() -> Unit)? = null
) {
  if (message.isBlank()) return
  val bg = if (isError) VendorColors.DangerRedBg else VendorColors.SuccessGreenBg
  val fg = if (isError) VendorColors.DangerRed else VendorColors.SuccessGreen
  val icon = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = VendorShapes.input,
    color = bg,
    border = BorderStroke(1.dp, fg.copy(alpha = 0.25f))
  ) {
    Row(
      modifier = Modifier.padding(horizontal = VendorSpacing.sm, vertical = VendorSpacing.xs),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(VendorSpacing.xs)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = fg,
        modifier = Modifier.size(20.dp)
      )
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = fg,
        modifier = Modifier.weight(1f)
      )
      if (onDismiss != null) {
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Dismiss message",
            tint = fg,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
fun VendorOutlinedTextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  leadingIcon: ImageVector? = null,
  trailingIcon: @Composable (() -> Unit)? = null,
  placeholder: String? = null,
  supportingText: String? = null,
  readOnly: Boolean = false,
  enabled: Boolean = true,
  minLines: Int = 1,
  singleLine: Boolean = minLines == 1,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label, style = MaterialTheme.typography.bodySmall) },
    placeholder = placeholder?.let { { Text(it, color = VendorColors.TextSecondary) } },
    supportingText = supportingText?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
    leadingIcon = leadingIcon?.let {
      {
        Icon(
          imageVector = it,
          contentDescription = null,
          tint = VendorColors.TextSecondary,
          modifier = Modifier.size(20.dp)
        )
      }
    },
    trailingIcon = trailingIcon,
    readOnly = readOnly,
    enabled = enabled,
    minLines = minLines,
    singleLine = singleLine,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
    shape = VendorShapes.input,
    textStyle = MaterialTheme.typography.bodyMedium,
    colors = OutlinedTextFieldDefaults.colors(
      focusedBorderColor = VendorColors.PrimaryOrange,
      focusedLabelColor = VendorColors.PrimaryOrange,
      unfocusedBorderColor = VendorColors.BorderSubtle,
      unfocusedLabelColor = VendorColors.TextSecondary,
      focusedContainerColor = VendorColors.CardWhite,
      unfocusedContainerColor = VendorColors.CardWhite,
      disabledContainerColor = VendorColors.Background,
      disabledBorderColor = VendorColors.BorderSubtle
    ),
    modifier = modifier.fillMaxWidth()
  )
}

@Composable
fun rememberShimmerBrush(): Brush {
  val transition = rememberInfiniteTransition(label = "vendor_shimmer")
  val translateAnim by transition.animateFloat(
    initialValue = 0f,
    targetValue = 900f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1100, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "shimmer_translate"
  )
  return Brush.linearGradient(
    colors = listOf(
      Color(0xFFE9EEF5),
      Color(0xFFF6F8FC),
      Color(0xFFE9EEF5)
    ),
    start = Offset(translateAnim - 300f, 0f),
    end = Offset(translateAnim, 200f)
  )
}

@Composable
fun VendorShimmerBox(
  modifier: Modifier = Modifier,
  height: Dp = 16.dp,
  cornerRadius: Dp = 8.dp
) {
  val brush = rememberShimmerBrush()
  Box(
    modifier = modifier
      .height(height)
      .clip(RoundedCornerShape(cornerRadius))
      .background(brush)
  )
}

@Composable
fun VendorSkeletonCard(modifier: Modifier = Modifier) {
  AppCard(modifier = modifier) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm),
      verticalAlignment = Alignment.CenterVertically
    ) {
      VendorShimmerBox(
        modifier = Modifier.size(64.dp),
        height = 64.dp,
        cornerRadius = 12.dp
      )
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(VendorSpacing.xs)
      ) {
        VendorShimmerBox(modifier = Modifier.fillMaxWidth(0.75f), height = 16.dp)
        VendorShimmerBox(modifier = Modifier.fillMaxWidth(0.45f), height = 14.dp)
        VendorShimmerBox(modifier = Modifier.fillMaxWidth(0.35f), height = 20.dp, cornerRadius = 8.dp)
      }
    }
  }
}

@Composable
fun VendorStatGridSkeleton(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
  ) {
    VendorShimmerBox(
      modifier = Modifier.fillMaxWidth(),
      height = 144.dp,
      cornerRadius = 16.dp
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
    ) {
      repeat(2) {
        AppCard(modifier = Modifier.weight(1f)) {
          VendorShimmerBox(modifier = Modifier.size(40.dp), height = 40.dp, cornerRadius = 12.dp)
          Spacer(modifier = Modifier.height(VendorSpacing.sm))
          VendorShimmerBox(modifier = Modifier.fillMaxWidth(0.45f), height = 24.dp)
          Spacer(modifier = Modifier.height(VendorSpacing.xs))
          VendorShimmerBox(modifier = Modifier.fillMaxWidth(0.7f), height = 14.dp)
        }
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
    ) {
      repeat(2) {
        AppCard(modifier = Modifier.weight(1f)) {
          VendorShimmerBox(modifier = Modifier.size(40.dp), height = 40.dp, cornerRadius = 12.dp)
          Spacer(modifier = Modifier.height(VendorSpacing.sm))
          VendorShimmerBox(modifier = Modifier.fillMaxWidth(0.45f), height = 24.dp)
          Spacer(modifier = Modifier.height(VendorSpacing.xs))
          VendorShimmerBox(modifier = Modifier.fillMaxWidth(0.7f), height = 14.dp)
        }
      }
    }
  }
}
