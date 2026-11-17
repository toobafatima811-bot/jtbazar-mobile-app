package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.data.supabase.VendorOrderStatus
import com.example.ui.components.AppCard
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorState
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.components.VendorAlertBanner
import com.example.ui.components.VendorOutlinedTextField
import com.example.ui.components.VendorSkeletonCard
import com.example.ui.components.vendorOrderStatusBadgeSpec
import com.example.ui.theme.VendorCenterTheme
import com.example.ui.theme.VendorColors
import com.example.ui.theme.VendorShapes
import com.example.ui.theme.VendorSpacing
import org.json.JSONObject

@Composable
fun VendorOrdersContent(
  uiState: VendorOrdersUiState,
  onSearchChange: (String) -> Unit,
  onSearchSubmit: () -> Unit,
  onStatusSelect: (String) -> Unit,
  onPrevPage: () -> Unit,
  onNextPage: () -> Unit,
  onPrintPackingSlip: (VendorOrderGroupUiModel) -> Unit,
  onMarkReady: (VendorOrderGroupUiModel) -> Unit,
  onRefresh: () -> Unit,
  onDismissAlert: () -> Unit,
  onOpenOrderDetails: (VendorOrderGroupUiModel) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val hasPrev = uiState.page > 0
  val hasNext = (uiState.page + 1) * uiState.pageSize < uiState.totalOrders
  val filterOptions = remember(uiState.totalAllOrders, uiState.statusCounts) {
    buildList {
      add("all" to "All (${uiState.totalAllOrders})")
      VendorOrderStatus.entries.forEach { status ->
        val count = uiState.statusCounts[status] ?: 0
        add(status.key to "${status.label} ($count)")
      }
    }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(VendorColors.Background)
      .testTag("vendor_orders_list"),
    contentPadding = PaddingValues(VendorSpacing.md),
    verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
  ) {
    item {
      SectionHeader(
        title = "Vendor orders",
        subtitle = "Track live order statuses and prepare items for courier pickup.",
        actionLabel = "Refresh",
        actionIcon = Icons.Rounded.Refresh,
        onActionClick = onRefresh
      )
    }

    item {
      Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.xs)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(VendorSpacing.xs),
          verticalAlignment = Alignment.CenterVertically
        ) {
          VendorOutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchChange,
            label = "Search order # or product",
            leadingIcon = Icons.Rounded.Search,
            trailingIcon = if (uiState.searchQuery.isNotBlank()) {
              {
                IconButton(
                  onClick = {
                    onSearchChange("")
                    onSearchSubmit()
                  },
                  modifier = Modifier.size(VendorSpacing.touchMin)
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Clear search",
                    tint = VendorColors.TextSecondary
                  )
                }
              }
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
            modifier = Modifier
              .weight(1f)
              .testTag("vendor_orders_search_input")
          )
          PrimaryButton(
            text = "Search",
            icon = Icons.Rounded.Search,
            enabled = !uiState.isBusy,
            onClick = onSearchSubmit,
            modifier = Modifier.testTag("vendor_orders_search_button")
          )
        }

        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(VendorSpacing.xs),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("vendor_orders_filter_row")
        ) {
          items(filterOptions, key = { it.first }) { (key, label) ->
            val selected = uiState.selectedStatus == key
            FilterChip(
              selected = selected,
              onClick = { onStatusSelect(key) },
              enabled = !uiState.isBusy,
              label = {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.SemiBold
                )
              },
              shape = VendorShapes.pill,
              colors = FilterChipDefaults.filterChipColors(
                containerColor = VendorColors.CardWhite,
                labelColor = VendorColors.TextSecondary,
                selectedContainerColor = VendorColors.PrimaryOrange,
                selectedLabelColor = Color.White
              ),
              border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = VendorColors.BorderSubtle,
                selectedBorderColor = VendorColors.PrimaryOrange
              ),
              modifier = Modifier
                .heightIn(min = 40.dp)
                .testTag("vendor_orders_filter_$key")
            )
          }
        }
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

    if (uiState.totalOrders > 0 || hasPrev || hasNext) {
      item {
        AppCard(contentPadding = PaddingValues(horizontal = VendorSpacing.md, vertical = VendorSpacing.xs)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = "Page ${uiState.page + 1} • ${uiState.totalOrders} order(s)",
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.SemiBold,
              color = VendorColors.TextSecondary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              TextButton(
                onClick = onPrevPage,
                enabled = !uiState.isBusy && hasPrev,
                modifier = Modifier.heightIn(min = VendorSpacing.touchMin)
              ) {
                Text(
                  text = "Previous",
                  color = if (!uiState.isBusy && hasPrev) VendorColors.PrimaryOrange else VendorColors.TextMuted,
                  fontWeight = FontWeight.SemiBold
                )
              }
              TextButton(
                onClick = onNextPage,
                enabled = !uiState.isBusy && hasNext,
                modifier = Modifier.heightIn(min = VendorSpacing.touchMin)
              ) {
                Text(
                  text = "Next",
                  color = if (!uiState.isBusy && hasNext) VendorColors.PrimaryOrange else VendorColors.TextMuted,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }
          }
        }
      }
    }

    when (val state = uiState.ordersState) {
      VendorSectionState.Loading -> {
        items(3) {
          VendorSkeletonCard()
        }
      }

      is VendorSectionState.Error -> {
        item {
          ErrorState(
            title = "Orders could not load",
            message = state.message,
            onRetry = onRefresh
          )
        }
      }

      VendorSectionState.Empty -> {
        item {
          val selectedCanonical = VendorOrderStatus.parse(uiState.selectedStatus)
          EmptyState(
            icon = Icons.Rounded.LocalShipping,
            title = if (selectedCanonical != null) {
              "No ${selectedCanonical.label} orders"
            } else {
              "No orders yet"
            },
            description = if (selectedCanonical != null) {
              "There are currently no orders in ${selectedCanonical.label} status."
            } else {
              "Customer contact, payment aur delivery JTBazar sambhalta hai. Aap sirf apne items pickup ke liye taiyar karte hain."
            },
            actionLabel = if (selectedCanonical != null || uiState.searchQuery.isNotBlank()) "Show all orders" else null,
            onAction = if (selectedCanonical != null || uiState.searchQuery.isNotBlank()) {
              {
                if (uiState.searchQuery.isNotBlank()) onSearchChange("")
                onStatusSelect("all")
              }
            } else null
          )
        }
      }

      is VendorSectionState.Content -> {
        items(state.data, key = { it.orderId.ifBlank { it.orderNumber } }) { group ->
          VendorOrderGroupCard(
            group = group,
            isBusy = uiState.isBusy,
            onOpenDetails = { onOpenOrderDetails(group) },
            onPrintPackingSlip = { onPrintPackingSlip(group) },
            onMarkReady = { onMarkReady(group) }
          )
        }
      }
    }
  }
}

@Composable
private fun VendorOrderGroupCard(
  group: VendorOrderGroupUiModel,
  isBusy: Boolean,
  onOpenDetails: () -> Unit,
  onPrintPackingSlip: () -> Unit,
  onMarkReady: () -> Unit
) {
  val badgeSpec = vendorOrderStatusBadgeSpec(group.canonicalStatus)

  AppCard(
    onClick = onOpenDetails,
    modifier = Modifier.testTag("vendor_order_card_${group.orderId}")
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
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
            imageVector = Icons.Rounded.LocalShipping,
            contentDescription = null,
            tint = VendorColors.PrimaryOrange,
            modifier = Modifier.size(20.dp)
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Order ${group.orderNumber}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = VendorColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          val meta = buildString {
            append("${group.items.size} item(s)")
            if (group.createdAt.isNotBlank()) {
              append(" • ${group.createdAt.take(10)}")
            }
          }
          Text(
            text = meta,
            style = MaterialTheme.typography.bodySmall,
            color = VendorColors.TextSecondary
          )
        }
        StatusBadge(
          text = badgeSpec.label,
          tone = badgeSpec.tone,
          icon = badgeSpec.icon,
          modifier = Modifier.testTag("vendor_order_status_badge_${group.orderId}")
        )
      }

      HorizontalDivider(color = VendorColors.Divider)

      Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.xs)) {
        group.items.forEach { item ->
          Surface(
            shape = VendorShapes.input,
            color = VendorColors.Background,
            border = BorderStroke(1.dp, VendorColors.BorderSubtle),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(VendorSpacing.sm),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
            ) {
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
              ) {
                Text(
                  text = item.productName,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = VendorColors.TextPrimary,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = "SKU: ${item.sku} • Variant: ${item.variantSummary}",
                  style = MaterialTheme.typography.bodySmall,
                  color = VendorColors.TextSecondary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Surface(
                shape = VendorShapes.badge,
                color = VendorColors.CardWhite,
                border = BorderStroke(1.dp, VendorColors.BorderSubtle)
              ) {
                Text(
                  text = "×${item.quantity}",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = VendorColors.NavyHeader,
                  modifier = Modifier.padding(horizontal = VendorSpacing.xs, vertical = VendorSpacing.xxs)
                )
              }
            }
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.xs)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(VendorSpacing.xs)
        ) {
          SecondaryButton(
            text = "Order details",
            icon = Icons.Rounded.Visibility,
            enabled = group.orderId.isNotBlank(),
            onClick = onOpenDetails,
            modifier = Modifier
              .weight(1f)
              .testTag("vendor_order_details_${group.orderId}")
          )
          SecondaryButton(
            text = "Packing slip",
            icon = Icons.Rounded.ReceiptLong,
            enabled = group.orderId.isNotBlank(),
            onClick = onPrintPackingSlip,
            modifier = Modifier.weight(1f)
          )
        }
        if (group.canonicalStatus.canMarkReadyForPickup) {
          PrimaryButton(
            text = "Mark ready for pickup",
            icon = Icons.Rounded.CheckCircle,
            enabled = !isBusy && group.orderId.isNotBlank(),
            loading = isBusy,
            onClick = onMarkReady,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("vendor_mark_ready_${group.orderId}")
          )
        }
      }
    }
  }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun VendorOrdersContentPreview() {
  VendorCenterTheme {
    VendorOrdersContent(
      uiState = VendorOrdersUiState(
        totalOrders = 1,
        totalAllOrders = 1,
        statusCounts = mapOf(VendorOrderStatus.PROCESSING to 1),
        ordersState = VendorSectionState.Content(
          listOf(
            VendorOrderGroupUiModel(
              orderId = "ord-1",
              orderNumber = "#JT-94821",
              createdAt = "2026-11-12T10:30:00Z",
              status = VendorOrderStatus.PROCESSING.key,
              canonicalStatus = VendorOrderStatus.PROCESSING,
              items = listOf(
                VendorOrderLineItemUiModel(
                  productName = "Multani Hand-Glazed Ceramic Plate Set",
                  quantity = "2",
                  sku = "MC-PLT-01",
                  variantSummary = "Size: 10.5 inch",
                  rawJson = JSONObject()
                )
              ),
              rawItems = emptyList()
            )
          )
        )
      ),
      onSearchChange = {},
      onSearchSubmit = {},
      onStatusSelect = {},
      onPrevPage = {},
      onNextPage = {},
      onPrintPackingSlip = {},
      onMarkReady = {},
      onRefresh = {},
      onDismissAlert = {}
    )
  }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun VendorOrdersEmptyPreview() {
  VendorCenterTheme {
    VendorOrdersContent(
      uiState = VendorOrdersUiState(ordersState = VendorSectionState.Empty),
      onSearchChange = {},
      onSearchSubmit = {},
      onStatusSelect = {},
      onPrevPage = {},
      onNextPage = {},
      onPrintPackingSlip = {},
      onMarkReady = {},
      onRefresh = {},
      onDismissAlert = {}
    )
  }
}
