package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ui.components.AppCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.components.StatusTone
import com.example.localization.appText
import com.example.ui.theme.VendorCenterTheme
import com.example.ui.theme.VendorColors
import com.example.ui.theme.VendorShapes
import com.example.ui.theme.VendorSpacing

@Composable
fun VendorMoreMenuContent(
  shopName: String,
  accountEmail: String,
  unreadNotificationsCount: Int = 0,
  onOpenCasesSupport: () -> Unit,
  onOpenShopSettings: () -> Unit,
  onOpenAdvancedTools: () -> Unit,
  onOpenNotifications: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(VendorColors.Background),
    contentPadding = PaddingValues(VendorSpacing.md),
    verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
  ) {
    item {
      AppCard(
        containerColor = VendorColors.NavyHeader,
        borderColor = VendorColors.NavyHeader
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
        ) {
          Box(
            modifier = Modifier
              .size(48.dp)
              .clip(VendorShapes.iconBox)
              .background(Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Rounded.Storefront,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(24.dp)
            )
          }
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            Text(
              text = shopName.ifBlank { appText("Your Shop", "آپ کی دکان") },
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            if (accountEmail.isNotBlank()) {
              Text(
                text = accountEmail,
                style = MaterialTheme.typography.bodySmall,
                color = VendorColors.NavyHeaderSubtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
          StatusBadge(
            text = appText("Approved", "منظور شدہ"),
            tone = StatusTone.SUCCESS,
            icon = Icons.Rounded.Verified
          )
        }
      }
    }

    item {
      SectionHeader(
        title = appText("Shop tools & support", "دکان کے ٹولز اور سپورٹ"),
        subtitle = appText(
          "Manage customer disputes, shop details, variants and stock history.",
          "کسٹمر شکایات، دکان کی تفصیلات، ویرینٹس اور اسٹاک ہسٹری منظم کریں۔"
        )
      )
    }

    item {
      VendorMoreMenuCard(
        icon = Icons.Rounded.Notifications,
        title = appText("Notifications", "اطلاعات"),
        subtitle = if (unreadNotificationsCount > 0) {
          appText(
            "$unreadNotificationsCount unread order or store alert(s)",
            "$unreadNotificationsCount غیر پڑھے گئے آرڈر یا اسٹور الرٹس"
          )
        } else {
          appText(
            "View order status updates and store alerts",
            "آرڈر اسٹیٹس اپڈیٹس اور اسٹور الرٹس دیکھیں"
          )
        },
        badgeText = if (unreadNotificationsCount > 0) {
          appText("$unreadNotificationsCount unread", "$unreadNotificationsCount غیر پڑھے")
        } else null,
        onClick = onOpenNotifications,
        modifier = Modifier.testTag("vendor_more_notifications")
      )
    }

    item {
      VendorMoreMenuCard(
        icon = Icons.Rounded.SupportAgent,
        title = appText("Cases & Support", "کیسز اور سپورٹ"),
        subtitle = appText(
          "Respond to customer cases and chat with JTBazar Support",
          "کسٹمر کیسز کا جواب دیں اور جے ٹی بازار سپورٹ سے بات کریں"
        ),
        onClick = onOpenCasesSupport,
        modifier = Modifier.testTag("vendor_more_cases_support")
      )
    }

    item {
      VendorMoreMenuCard(
        icon = Icons.Rounded.Storefront,
        title = appText("Shop Settings", "دکان کی ترتیبات"),
        subtitle = appText(
          "Update profile, weekly opening hours, emergency close, map pin and docs",
          "پروفائل، ہفتہ وار کھلنے کے اوقات، ہنگامی بندش، میپ پن اور دستاویزات اپڈیٹ کریں"
        ),
        onClick = onOpenShopSettings,
        modifier = Modifier.testTag("vendor_more_shop_settings")
      )
    }

    item {
      VendorMoreMenuCard(
        icon = Icons.Rounded.Tune,
        title = appText("Variants & Inventory History", "ویرینٹس اور انوینٹری ہسٹری"),
        subtitle = appText(
          "Manage product variants, photo uploads and stock movement logs",
          "پروڈکٹ ویرینٹس، تصاویر اور اسٹاک نقل و حرکت لاگز منظم کریں"
        ),
        onClick = onOpenAdvancedTools,
        modifier = Modifier.testTag("vendor_more_advanced_tools")
      )
    }
  }
}

@Composable
private fun VendorMoreMenuCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  badgeText: String? = null
) {
  AppCard(
    onClick = onClick,
    modifier = modifier
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
    ) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(VendorShapes.iconBox)
          .background(VendorColors.PrimaryOrangeTint),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = VendorColors.PrimaryOrange,
          modifier = Modifier.size(22.dp)
        )
      }
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(VendorSpacing.xs)
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = VendorColors.TextPrimary
          )
          if (!badgeText.isNullOrBlank()) {
            StatusBadge(
              text = badgeText,
              tone = StatusTone.WARNING
            )
          }
        }
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = VendorColors.TextSecondary
        )
      }
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = title,
        tint = VendorColors.TextSecondary,
        modifier = Modifier.size(24.dp)
      )
    }
  }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun VendorMoreMenuContentPreview() {
  VendorCenterTheme {
    VendorMoreMenuContent(
      shopName = "Multan Craft Heritage",
      accountEmail = "seller@jtbazar.pk",
      onOpenCasesSupport = {},
      onOpenShopSettings = {},
      onOpenAdvancedTools = {}
    )
  }
}
