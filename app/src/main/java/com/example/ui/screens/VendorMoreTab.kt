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
import com.example.ui.theme.VendorCenterTheme
import com.example.ui.theme.VendorColors
import com.example.ui.theme.VendorShapes
import com.example.ui.theme.VendorSpacing

@Composable
fun VendorMoreMenuContent(
  shopName: String,
  accountEmail: String,
  onOpenCasesSupport: () -> Unit,
  onOpenShopSettings: () -> Unit,
  onOpenAdvancedTools: () -> Unit,
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
              text = shopName.ifBlank { "Your Shop" },
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
            text = "Approved",
            tone = StatusTone.SUCCESS,
            icon = Icons.Rounded.Verified
          )
        }
      }
    }

    item {
      SectionHeader(
        title = "Shop tools & support",
        subtitle = "Manage customer disputes, shop details, variants and stock history."
      )
    }

    item {
      VendorMoreMenuCard(
        icon = Icons.Rounded.SupportAgent,
        title = "Cases & Support",
        subtitle = "Respond to customer cases and chat with JTBazar Support",
        onClick = onOpenCasesSupport,
        modifier = Modifier.testTag("vendor_more_cases_support")
      )
    }

    item {
      VendorMoreMenuCard(
        icon = Icons.Rounded.Storefront,
        title = "Shop Settings",
        subtitle = "Update public profile, shop image, map pin and verification docs",
        onClick = onOpenShopSettings,
        modifier = Modifier.testTag("vendor_more_shop_settings")
      )
    }

    item {
      VendorMoreMenuCard(
        icon = Icons.Rounded.Tune,
        title = "Variants & Inventory History",
        subtitle = "Manage product variants, photo uploads and stock movement logs",
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
  modifier: Modifier = Modifier
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
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = VendorColors.TextPrimary
        )
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
