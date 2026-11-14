package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalShipping
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
import com.example.ui.theme.VendorCenterTheme
import com.example.ui.theme.VendorColors
import com.example.ui.theme.VendorShapes
import com.example.ui.theme.VendorSpacing
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

@Composable
fun VendorOverviewContent(
  uiState: VendorOverviewUiState,
  onRefresh: () -> Unit,
  onAddProduct: () -> Unit,
  onViewAllProducts: () -> Unit,
  onOpenProductDetails: (VendorProductUiModel) -> Unit,
  modifier: Modifier = Modifier
) {
  val numberFormatter = remember { NumberFormat.getIntegerInstance(Locale.US) }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(VendorColors.Background),
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
            label = "Total listings",
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
          )
          StatCard(
            icon = Icons.Rounded.ReceiptLong,
            value = numberFormatter.format(uiState.orderItemsCount),
            label = "Order items",
            iconTint = VendorColors.NavyHeader,
            iconBackground = VendorColors.NavyHeader.copy(alpha = 0.10f),
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
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
            label = "Items to prepare",
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
          )
          StatCard(
            icon = Icons.Rounded.Storefront,
            value = uiState.shopStatusLabel,
            label = "Shop status",
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

    // 3. Single "Add a new product" CTA Card
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
              text = "Add a new product",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = VendorColors.TextPrimary
            )
            Text(
              text = "Publish a new listing for JTBazar customers",
              style = MaterialTheme.typography.bodySmall,
              color = VendorColors.TextSecondary
            )
          }
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = "Add a new product",
            tint = VendorColors.PrimaryOrange,
            modifier = Modifier.size(24.dp)
          )
        }
      }
    }

    // 4. Recent listings header
    item {
      SectionHeader(
        title = "Recent listings",
        actionLabel = "View all",
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
            title = "Could not load listings",
            message = state.message,
            onRetry = onRefresh
          )
        }
      }

      VendorSectionState.Empty -> {
        item {
          EmptyState(
            icon = Icons.Rounded.Inventory2,
            title = "No products listed yet",
            description = "Your product listings will appear here once you add them.",
            actionLabel = "Add your first product",
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
  val displayName = shopName.ifBlank { "Your Shop" }
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
            text = "Welcome back",
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
              contentDescription = "Refresh overview",
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
            text = if (isSellerActive) "Seller account active" else "Seller review pending",
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
    product.moderationStatus == "rejected" -> Triple("Rejected", StatusTone.DANGER, Icons.Rounded.ErrorOutline)
    product.isHidden -> Triple("Hidden", StatusTone.NEUTRAL, Icons.Rounded.VisibilityOff)
    product.moderationStatus == "approved" -> Triple(
      product.publicationStatus.replace('_', ' ').replaceFirstChar { it.uppercase() },
      StatusTone.SUCCESS,
      Icons.Rounded.CheckCircle
    )
    else -> Triple("Pending", StatusTone.WARNING, Icons.Rounded.Schedule)
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
