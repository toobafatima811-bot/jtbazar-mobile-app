package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.components.AppCard
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorState
import com.example.ui.components.PrimaryButton
import com.example.ui.components.ProductActionButton
import com.example.ui.components.StatusBadge
import com.example.ui.components.StatusTone
import com.example.ui.components.VendorAlertBanner
import com.example.ui.components.VendorSkeletonCard
import com.example.ui.components.formatPkrPrice
import com.example.ui.theme.VendorCenterTheme
import com.example.ui.theme.VendorColors
import com.example.ui.theme.VendorShapes
import com.example.ui.theme.VendorSpacing
import org.json.JSONObject

@Composable
fun VendorProductsContent(
  uiState: VendorProductsUiState,
  onRefresh: () -> Unit,
  onAddProduct: () -> Unit,
  onViewDetails: (VendorProductUiModel) -> Unit,
  onEditProduct: (VendorProductUiModel) -> Unit,
  onToggleVisibility: (VendorProductUiModel) -> Unit,
  onArchiveProduct: (VendorProductUiModel) -> Unit,
  onRequestDeletion: (VendorProductUiModel) -> Unit,
  onDismissAlert: () -> Unit,
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
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Your products",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = VendorColors.TextPrimary
          )
          val count = (uiState.productsState as? VendorSectionState.Content)?.data?.size
          if (count != null) {
            Text(
              text = "$count listing(s) in your catalog",
              style = MaterialTheme.typography.bodySmall,
              color = VendorColors.TextSecondary
            )
          }
        }
        PrimaryButton(
          text = "Add product",
          icon = Icons.Rounded.Add,
          onClick = onAddProduct
        )
      }
    }

    if (uiState.actionError.isNotBlank()) {
      item {
        VendorAlertBanner(
          message = uiState.actionError,
          isError = true,
          onDismiss = onDismissAlert
        )
      }
    }

    if (uiState.infoMessage.isNotBlank()) {
      item {
        VendorAlertBanner(
          message = uiState.infoMessage,
          isError = false,
          onDismiss = onDismissAlert
        )
      }
    }

    when (val state = uiState.productsState) {
      VendorSectionState.Loading -> {
        items(4) {
          VendorSkeletonCard()
        }
      }

      is VendorSectionState.Error -> {
        item {
          ErrorState(
            title = "Products could not load",
            message = state.message,
            onRetry = onRefresh
          )
        }
      }

      VendorSectionState.Empty -> {
        item {
          EmptyState(
            icon = Icons.Rounded.Inventory2,
            title = "No products yet",
            description = "Add your first product listing so customers on JTBazar can discover and order from your shop.",
            actionLabel = "Add product",
            actionIcon = Icons.Rounded.Add,
            onAction = onAddProduct
          )
        }
      }

      is VendorSectionState.Content -> {
        items(state.data, key = { it.id }) { product ->
          VendorProductCard(
            product = product,
            isMutating = uiState.isMutating,
            onViewDetails = { onViewDetails(product) },
            onEdit = { onEditProduct(product) },
            onToggleVisibility = { onToggleVisibility(product) },
            onArchive = { onArchiveProduct(product) },
            onDelete = { onRequestDeletion(product) }
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VendorProductCard(
  product: VendorProductUiModel,
  isMutating: Boolean,
  onViewDetails: () -> Unit,
  onEdit: () -> Unit,
  onToggleVisibility: () -> Unit,
  onArchive: () -> Unit,
  onDelete: () -> Unit
) {
  val isApproved = product.moderationStatus == "approved"
  val isRejected = product.moderationStatus == "rejected"
  val isDeletionPending = product.deletionStatus == "pending"

  val pubLabel = when {
    product.isHidden -> "Hidden"
    product.publicationStatus.equals("published", true) -> "Published"
    else -> product.publicationStatus.replace('_', ' ').replaceFirstChar { it.uppercase() }
  }
  val pubTone = when {
    product.isHidden -> StatusTone.NEUTRAL
    product.publicationStatus.equals("published", true) -> StatusTone.SUCCESS
    else -> StatusTone.NEUTRAL
  }
  val pubIcon = when {
    product.isHidden -> Icons.Rounded.VisibilityOff
    product.publicationStatus.equals("published", true) -> Icons.Rounded.Public
    else -> Icons.Rounded.Schedule
  }

  val modLabel = when {
    isRejected -> "Rejected"
    isApproved -> "Approved"
    else -> "Pending approval"
  }
  val modTone = when {
    isRejected -> StatusTone.DANGER
    isApproved -> StatusTone.SUCCESS
    else -> StatusTone.WARNING
  }
  val modIcon = when {
    isRejected -> Icons.Rounded.ErrorOutline
    isApproved -> Icons.Rounded.CheckCircle
    else -> Icons.Rounded.Schedule
  }

  AppCard(onClick = onViewDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm),
        verticalAlignment = Alignment.Top
      ) {
        if (!product.imageUrl.isNullOrBlank()) {
          AsyncImage(
            model = product.imageUrl,
            contentDescription = product.name,
            modifier = Modifier
              .size(72.dp)
              .clip(VendorShapes.iconBox),
            contentScale = ContentScale.Crop
          )
        } else {
          Box(
            modifier = Modifier
              .size(72.dp)
              .clip(VendorShapes.iconBox)
              .background(VendorColors.PrimaryOrangeTint),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Rounded.Inventory2,
              contentDescription = null,
              tint = VendorColors.PrimaryOrange,
              modifier = Modifier.size(28.dp)
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
            fontWeight = FontWeight.Bold,
            color = VendorColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = formatPkrPrice(product.priceRaw),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = VendorColors.PrimaryOrange
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(VendorSpacing.xxs)
          ) {
            StatusBadge(text = pubLabel, tone = pubTone, icon = pubIcon)
            StatusBadge(text = modLabel, tone = modTone, icon = modIcon)
            if (isDeletionPending) {
              StatusBadge(
                text = "Deletion pending",
                tone = StatusTone.WARNING,
                icon = Icons.Rounded.Schedule
              )
            }
          }
        }
      }

      if (isRejected && product.rejectionReason.isNotBlank()) {
        VendorAlertBanner(
          message = "Admin rejection reason: ${product.rejectionReason}",
          isError = true
        )
      }

      HorizontalDivider(color = VendorColors.Divider)

      // Equal 4-button action row: Edit, Hide, Archive, Delete (Delete distinct in red)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VendorSpacing.xs)
      ) {
        ProductActionButton(
          text = "Edit",
          icon = Icons.Rounded.Edit,
          enabled = !isMutating,
          onClick = onEdit,
          modifier = Modifier.weight(1f)
        )
        ProductActionButton(
          text = if (product.isHidden) "Show" else "Hide",
          icon = if (product.isHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
          enabled = !isMutating && isApproved,
          onClick = onToggleVisibility,
          modifier = Modifier.weight(1f)
        )
        ProductActionButton(
          text = "Archive",
          icon = Icons.Rounded.Archive,
          enabled = !isMutating,
          onClick = onArchive,
          modifier = Modifier.weight(1f)
        )
        ProductActionButton(
          text = "Delete",
          icon = Icons.Rounded.DeleteOutline,
          enabled = !isMutating && !isDeletionPending,
          tint = VendorColors.DangerRed,
          backgroundColor = VendorColors.DangerRedBg.copy(alpha = 0.7f),
          borderColor = VendorColors.DangerRed.copy(alpha = 0.28f),
          onClick = onDelete,
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun VendorProductsContentPreview() {
  VendorCenterTheme {
    VendorProductsContent(
      uiState = VendorProductsUiState(
        productsState = VendorSectionState.Content(
          listOf(
            VendorProductUiModel(
              id = "p1",
              name = "Multani Artisan Ceramic Dinner Plate Set",
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
              id = "p2",
              name = "Pure Blossom Honey 500g Jar",
              priceRaw = "950",
              publicationStatus = "draft",
              moderationStatus = "rejected",
              isHidden = true,
              rejectionReason = "Please upload clear front label image.",
              deletionStatus = "",
              imageUrl = null,
              rawJson = JSONObject()
            )
          )
        )
      ),
      onRefresh = {},
      onAddProduct = {},
      onViewDetails = {},
      onEditProduct = {},
      onToggleVisibility = {},
      onArchiveProduct = {},
      onRequestDeletion = {},
      onDismissAlert = {}
    )
  }
}
