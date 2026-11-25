package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.supabase.VendorOrderStatus
import com.example.localization.AppLanguageManager
import com.example.localization.appText
import com.example.ui.components.AppCard
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorState
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.StatusTone
import com.example.ui.components.VendorSkeletonCard
import com.example.ui.components.VendorStatGridSkeleton
import com.example.ui.components.formatPkrPrice
import com.example.ui.components.vendorOrderStatusBadgeSpec
import com.example.ui.theme.VendorCenterTheme
import com.example.ui.theme.VendorColors
import com.example.ui.theme.VendorShapes
import com.example.ui.theme.VendorSpacing
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VendorOverviewContent(
  uiState: VendorOverviewUiState,
  onRefresh: () -> Unit,
  onAddProduct: () -> Unit,
  onViewAllProducts: () -> Unit,
  onOpenProductDetails: (VendorProductUiModel) -> Unit,
  onSelectOrderStatus: (VendorOrderStatus) -> Unit = {},
  onOpenOrders: () -> Unit = {},
  onOpenOrderDetails: (VendorOrderGroupUiModel) -> Unit = {},
  onOpenNotifications: () -> Unit = {},
  onOpenGuide: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val numberFormatter = remember { NumberFormat.getIntegerInstance(Locale.US) }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(VendorColors.Background)
      .testTag("vendor_overview_list"),
    contentPadding = PaddingValues(VendorSpacing.md),
    verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
  ) {
    // 1. Navy Hero Card
    item {
      VendorHeroCard(
        shopName = uiState.shopName,
        isSellerActive = uiState.isSellerActive,
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh
      )
    }

    item {
      AppCard(
        onClick = onOpenGuide,
        containerColor = VendorColors.CardWhite,
        borderColor = VendorColors.Border.copy(alpha = 0.75f),
        modifier = Modifier.testTag("vendor_dashboard_guide")
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
        ) {
          Icon(Icons.Rounded.Info, contentDescription = appText("Vendor guide", "وینڈر رہنمائی"), tint = VendorColors.NavyHeader, modifier = Modifier.size(22.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(appText("Vendor guide & product help", "وینڈر رہنمائی اور پروڈکٹ مدد"), fontWeight = FontWeight.Bold, color = VendorColors.TextPrimary)
            Text(appText("Step-by-step instructions for every dashboard section and product field.", "ڈیش بورڈ کے ہر حصے اور پروڈکٹ فیلڈ کی مرحلہ وار ہدایات۔"), style = MaterialTheme.typography.bodySmall, color = VendorColors.TextSecondary)
          }
          Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = appText("Open guide", "رہنمائی کھولیں"), tint = VendorColors.TextSecondary)
        }
      }
    }

    // Optional live unread notifications banner
    if (uiState.unreadNotificationsCount > 0) {
      item {
        AppCard(
          onClick = onOpenNotifications,
          containerColor = VendorColors.WarningAmberBg,
          borderColor = VendorColors.WarningAmberAccent.copy(alpha = 0.35f),
          modifier = Modifier.testTag("vendor_dashboard_notifications_banner")
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
          ) {
            Icon(
              imageVector = Icons.Rounded.NotificationsActive,
              contentDescription = appText("Unread notifications", "غیر پڑھے ہوئے نوٹیفیکیشنز"),
              tint = VendorColors.WarningAmber,
              modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = appText(
                  "${uiState.unreadNotificationsCount} unread notification(s)",
                  "${uiState.unreadNotificationsCount} غیر پڑھے ہوئے نوٹیفیکیشن"
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = VendorColors.WarningAmber
              )
              Text(
                text = appText("Tap to view live order & store updates", "لائیو آرڈر اور اسٹور اپڈیٹس دیکھنے کے لیے ٹیپ کریں"),
                style = MaterialTheme.typography.bodySmall,
                color = VendorColors.TextPrimary
              )
            }
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
              contentDescription = appText("Open notifications", "نوٹیفیکیشنز کھولیں"),
              tint = VendorColors.WarningAmber
            )
          }
        }
      }
    }

    // 2. 2x2 Stat Cards (equal height rows)
    item {
      Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
          horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
        ) {
          StatCard(
            icon = Icons.Rounded.ShoppingBag,
            value = numberFormatter.format(uiState.totalListings),
            label = appText("Total listings", "کل مصنوعات"),
            supportingText = appText("Products in catalog", "کیٹلاگ میں مصنوعات"),
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .clickable(onClick = onViewAllProducts)
              .testTag("vendor_dashboard_total_listings")
          )
          StatCard(
            icon = Icons.Rounded.ReceiptLong,
            value = numberFormatter.format(uiState.totalOrdersCount),
            label = appText("Total orders", "کل آرڈرز"),
            supportingText = appText(
              "${numberFormatter.format(uiState.orderItemsCount)} order item(s)",
              "${numberFormatter.format(uiState.orderItemsCount)} آرڈر آئٹمز"
            ),
            iconTint = VendorColors.NavyHeader,
            iconBackground = VendorColors.NavyHeader.copy(alpha = 0.10f),
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .clickable(onClick = onOpenOrders)
              .testTag("vendor_dashboard_total_orders")
          )
        }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
          horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
        ) {
          StatCard(
            icon = Icons.Rounded.LocalShipping,
            value = numberFormatter.format(uiState.itemsToPrepareCount),
            label = appText("Needs prep", "تیاری درکار"),
            supportingText = appText("Pending, confirmed & active", "زیر التواء، تصدیق شدہ اور فعال"),
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .clickable(onClick = onOpenOrders)
              .testTag("vendor_dashboard_needs_prep")
          )
          StatCard(
            icon = Icons.Rounded.Storefront,
            value = if (uiState.shopStatusLabel.equals("Active", ignoreCase = true)) appText("Active", "فعال") else uiState.shopStatusLabel,
            label = appText("Shop status", "دکان کی حیثیت"),
            supportingText = if (uiState.isSellerActive) appText("Live on JTBazar", "جے ٹی بازار پر لائیو") else appText("Under review", "جائزہ جاری"),
            iconTint = VendorColors.SuccessGreen,
            iconBackground = VendorColors.SuccessGreenBg,
            valueColor = VendorColors.SuccessGreen,
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
          )
        }
      }
    }

    // 3. Live Order Status Badges Breakdown
    item {
      AppCard(modifier = Modifier.testTag("vendor_dashboard_status_breakdown")) {
        Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = appText("Order status overview", "آرڈرز کی صورتحال کا جائزہ"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VendorColors.TextPrimary
              )
              Text(
                text = appText("Live Supabase counts across all order stages", "تمام مراحل کے لائیو آرڈر شمار"),
                style = MaterialTheme.typography.bodySmall,
                color = VendorColors.TextSecondary
              )
            }
          }

          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VendorSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(VendorSpacing.xs),
            maxItemsInEachRow = 2
          ) {
            VendorOrderStatus.entries.forEach { status ->
              val count = uiState.statusCounts[status] ?: 0
              val spec = vendorOrderStatusBadgeSpec(status)
              val (bg, fg) = when (spec.tone) {
                StatusTone.SUCCESS -> VendorColors.SuccessGreenBg to VendorColors.SuccessGreen
                StatusTone.WARNING -> VendorColors.WarningAmberBg to VendorColors.WarningAmber
                StatusTone.DANGER -> VendorColors.DangerRedBg to VendorColors.DangerRed
                StatusTone.PRIMARY -> VendorColors.PrimaryOrangeTint to VendorColors.PrimaryOrange
                StatusTone.NEUTRAL -> VendorColors.NeutralBadgeBg to VendorColors.NeutralBadgeText
              }

              Surface(
                onClick = { onSelectOrderStatus(status) },
                shape = VendorShapes.input,
                color = VendorColors.Background,
                border = BorderStroke(
                  width = 1.dp,
                  color = if (count > 0) fg.copy(alpha = 0.28f) else VendorColors.BorderSubtle
                ),
                modifier = Modifier
                  .weight(1f)
                  .testTag("vendor_dashboard_status_${status.key}")
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VendorSpacing.sm, vertical = VendorSpacing.xs),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                  ) {
                    Box(
                      modifier = Modifier
                        .size(28.dp)
                        .clip(VendorShapes.badge)
                        .background(bg),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = spec.icon,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(16.dp)
                      )
                    }
                    Text(
                      text = spec.label,
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.SemiBold,
                      color = VendorColors.TextPrimary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                  Spacer(modifier = Modifier.width(VendorSpacing.xxs))
                  Surface(
                    shape = VendorShapes.pill,
                    color = if (count > 0) bg else VendorColors.NeutralBadgeBg
                  ) {
                    Text(
                      text = numberFormatter.format(count),
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.Bold,
                      color = if (count > 0) fg else VendorColors.TextSecondary,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    // 4. Recent Orders Quick Preview
    if (uiState.recentOrders.isNotEmpty()) {
      item {
        SectionHeader(
          title = appText("Recent orders", "حالیہ آرڈرز"),
          actionLabel = appText("View all", "سب دیکھیں"),
          onActionClick = onOpenOrders
        )
      }
      items(
        items = uiState.recentOrders.take(3),
        key = { "overview_order_${it.orderId.ifBlank { it.orderNumber }}" }
      ) { group ->
        val badge = vendorOrderStatusBadgeSpec(group.canonicalStatus)
        AppCard(
          onClick = { onOpenOrderDetails(group) },
          modifier = Modifier.testTag("vendor_dashboard_recent_order_${group.orderId}")
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(VendorShapes.iconBox)
                .background(VendorColors.PrimaryOrangeTint),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Rounded.ReceiptLong,
                contentDescription = null,
                tint = VendorColors.PrimaryOrange,
                modifier = Modifier.size(20.dp)
              )
            }
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
              Text(
                text = appText("Order ${group.orderNumber}", "آرڈر ${group.orderNumber}"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = VendorColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = appText("${group.items.size} item(s)", "${group.items.size} آئٹمز") +
                  if (group.createdAt.isNotBlank()) " • ${group.createdAt.take(10)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = VendorColors.TextSecondary
              )
            }
            StatusBadge(
              text = badge.label,
              tone = badge.tone,
              icon = badge.icon
            )
          }
        }
      }
    }

    // 5. Single "Add a new product" CTA Card
    item {
      AppCard(
        onClick = onAddProduct,
        containerColor = VendorColors.PrimaryOrangeSoft,
        borderColor = VendorColors.PrimaryOrangeBorder
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
              .background(VendorColors.PrimaryOrange),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Rounded.AddCircle,
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
              text = appText("Add a new product", "نئی پروڈکٹ شامل کریں"),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = VendorColors.TextPrimary
            )
            Text(
              text = appText("Publish a new listing for JTBazar customers", "جے ٹی بازار کے صارفین کے لیے نئی پروڈکٹ شائع کریں"),
              style = MaterialTheme.typography.bodySmall,
              color = VendorColors.TextSecondary
            )
          }
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = appText("Add a new product", "نئی پروڈکٹ شامل کریں"),
            tint = VendorColors.PrimaryOrange,
            modifier = Modifier.size(24.dp)
          )
        }
      }
    }

    // 6. Recent listings header
    item {
      SectionHeader(
        title = appText("Recent listings", "حالیہ مصنوعات"),
        actionLabel = appText("View all", "سب دیکھیں"),
        onActionClick = onViewAllProducts
      )
    }

    when (val state = uiState.recentProductsState) {
      VendorSectionState.Loading -> {
        items(3) {
          VendorSkeletonCard()
        }
      }

      is VendorSectionState.Error -> {
        item {
          ErrorState(
            title = appText("Could not load listings", "مصنوعات لوڈ نہیں ہو سکیں"),
            message = state.message,
            onRetry = onRefresh
          )
        }
      }

      VendorSectionState.Empty -> {
        item {
          EmptyState(
            icon = Icons.Rounded.Inventory2,
            title = appText("No products listed yet", "ابھی تک کوئی پروڈکٹ شامل نہیں"),
            description = appText("Your product listings will appear here once you add them.", "پروڈکٹس شامل کرنے کے بعد وہ یہاں نظر آئیں گی۔"),
            actionLabel = appText("Add your first product", "پہلی پروڈکٹ شامل کریں"),
            actionIcon = Icons.Rounded.Add,
            onAction = onAddProduct
          )
        }
      }

      is VendorSectionState.Content -> {
        items(state.data, key = { it.id }) { product ->
          VendorRecentListingCard(
            product = product,
            onClick = { onOpenProductDetails(product) }
          )
        }
      }
    }
  }
}

@Composable
private fun VendorHeroCard(
  shopName: String,
  isSellerActive: Boolean,
  isRefreshing: Boolean,
  onRefresh: () -> Unit
) {
  val displayName = shopName.ifBlank { appText("Your Shop", "آپ کی دکان") }
  val avatarChar = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "J"

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = VendorShapes.card,
    colors = CardDefaults.cardColors(containerColor = VendorColors.NavyHeader),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(VendorSpacing.md),
      verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
      ) {
        // Shop Avatar (First letter)
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f)),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = avatarChar,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          Text(
            text = appText("Welcome back", "خوش آمدید"),
            style = MaterialTheme.typography.bodySmall,
            color = VendorColors.NavyHeaderSubtitle
          )
          Text(
            text = displayName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        IconButton(
          onClick = onRefresh,
          enabled = !isRefreshing,
          modifier = Modifier.size(VendorSpacing.touchMin)
        ) {
          if (isRefreshing) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              color = Color.White,
              strokeWidth = 2.dp
            )
          } else {
            Icon(
              imageVector = Icons.Rounded.Refresh,
              contentDescription = appText("Refresh overview", "جائزہ ریفریش کریں"),
              tint = Color.White,
              modifier = Modifier.size(22.dp)
            )
          }
        }
      }

      Surface(
        shape = VendorShapes.pill,
        color = if (isSellerActive) VendorColors.SuccessGreenBg else VendorColors.WarningAmberBg
      ) {
        Row(
          modifier = Modifier.padding(horizontal = VendorSpacing.sm, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = if (isSellerActive) Icons.Rounded.Verified else Icons.Rounded.Schedule,
            contentDescription = null,
            tint = if (isSellerActive) VendorColors.SuccessGreen else VendorColors.WarningAmber,
            modifier = Modifier.size(16.dp)
          )
          Text(
            text = if (isSellerActive) appText("Seller account active", "فروش اکاؤنٹ فعال ہے")
            else appText("Seller review pending", "فروش کا جائزہ زیر التواء ہے"),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isSellerActive) VendorColors.SuccessGreen else VendorColors.WarningAmber
          )
        }
      }
    }
  }
}

@Composable
private fun VendorRecentListingCard(
  product: VendorProductUiModel,
  onClick: () -> Unit
) {
  val (statusText, statusTone, statusIcon) = when {
    product.moderationStatus == "rejected" -> Triple(appText("Rejected", "مسترد"), StatusTone.DANGER, Icons.Rounded.ErrorOutline)
    product.isHidden -> Triple(appText("Hidden", "مخفی"), StatusTone.NEUTRAL, Icons.Rounded.VisibilityOff)
    product.moderationStatus == "approved" -> Triple(
      when (product.publicationStatus.lowercase()) {
        "published" -> appText("Published", "شائع شدہ")
        "draft" -> appText("Draft", "مسودہ")
        "archived" -> appText("Archived", "آرکائیو شدہ")
        "hidden" -> appText("Hidden", "مخفی")
        "pending_review", "pending" -> appText("Pending", "زیر التواء")
        "suspended" -> appText("Suspended", "معطل")
        else -> product.publicationStatus.replace('_', ' ').replaceFirstChar { it.uppercase() }
      },
      StatusTone.SUCCESS,
      Icons.Rounded.CheckCircle
    )
    else -> Triple(appText("Pending", "زیر التواء"), StatusTone.WARNING, Icons.Rounded.Schedule)
  }

  AppCard(onClick = onClick) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
    ) {
      if (!product.imageUrl.isNullOrBlank()) {
        AsyncImage(
          model = product.imageUrl,
          contentDescription = product.name,
          modifier = Modifier
            .size(56.dp)
            .clip(VendorShapes.iconBox),
          contentScale = ContentScale.Crop
        )
      } else {
        Box(
          modifier = Modifier
            .size(56.dp)
            .clip(VendorShapes.iconBox)
            .background(VendorColors.PrimaryOrangeTint),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Rounded.Inventory2,
            contentDescription = null,
            tint = VendorColors.PrimaryOrange,
            modifier = Modifier.size(24.dp)
          )
        }
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(VendorSpacing.xxs)
      ) {
        Text(
          text = product.name,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = VendorColors.TextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = formatPkrPrice(product.priceRaw),
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Bold,
          color = VendorColors.PrimaryOrange
        )
      }

      StatusBadge(
        text = statusText,
        tone = statusTone,
        icon = statusIcon
      )
    }
  }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun VendorOverviewContentPreview() {
  VendorCenterTheme {
    VendorOverviewContent(
      uiState = VendorOverviewUiState(
        shopName = "Multan Craft Heritage",
        isSellerActive = true,
        totalListings = 18,
        orderItemsCount = 42,
        itemsToPrepareCount = 5,
        shopStatusLabel = "Active",
        recentProductsState = VendorSectionState.Content(
          listOf(
            VendorProductUiModel(
              id = "1",
              name = "Hand-Glazed Ceramic Dinner Plate",
              priceRaw = "2200",
              publicationStatus = "published",
              moderationStatus = "approved",
              isHidden = false,
              rejectionReason = "",
              deletionStatus = "",
              imageUrl = null,
              rawJson = JSONObject()
            ),
            VendorProductUiModel(
              id = "2",
              name = "Kashigari Blue Pottery Bowl",
              priceRaw = "1450",
              publicationStatus = "draft",
              moderationStatus = "pending",
              isHidden = false,
              rejectionReason = "",
              deletionStatus = "",
              imageUrl = null,
              rawJson = JSONObject()
            )
          )
        )
      ),
      onRefresh = {},
      onAddProduct = {},
      onViewAllProducts = {},
      onOpenProductDetails = {}
    )
  }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun VendorOverviewLoadingPreview() {
  VendorCenterTheme {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(VendorColors.Background)
        .padding(VendorSpacing.md)
    ) {
      VendorStatGridSkeleton()
    }
  }
}
