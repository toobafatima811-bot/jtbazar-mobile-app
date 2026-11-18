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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.data.supabase.VendorOrderStatus
import com.example.ui.components.AppCard
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.StatusBadge
import com.example.ui.components.VendorAlertBanner
import com.example.ui.components.VendorOutlinedTextField
import com.example.ui.components.formatPkrPrice
import com.example.ui.components.vendorOrderStatusBadgeSpec
import com.example.ui.theme.VendorCenterTheme
import com.example.ui.theme.VendorColors
import com.example.ui.theme.VendorShapes
import com.example.ui.theme.VendorSpacing
import org.json.JSONObject

private data class OrderTimelineStep(
  val status: VendorOrderStatus,
  val title: String,
  val subtitle: String,
  val icon: ImageVector
)

private val standardFulfillmentSteps = listOf(
  OrderTimelineStep(
    VendorOrderStatus.PENDING,
    "Order Received",
    "Order placed and awaiting customer/admin confirmation",
    Icons.Rounded.Schedule
  ),
  OrderTimelineStep(
    VendorOrderStatus.CONFIRMED,
    "Confirmed",
    "Customer confirmation completed — prepare items",
    Icons.Rounded.Verified
  ),
  OrderTimelineStep(
    VendorOrderStatus.PROCESSING,
    "Processing",
    "Order is currently being packed and prepared",
    Icons.Rounded.Autorenew
  ),
  OrderTimelineStep(
    VendorOrderStatus.READY_FOR_PICKUP,
    "Ready for Pickup",
    "Marked ready for JTBazar courier pickup",
    Icons.Rounded.Inventory2
  ),
  OrderTimelineStep(
    VendorOrderStatus.SHIPPED,
    "Shipped",
    "Handed over to JTBazar courier for delivery",
    Icons.Rounded.LocalShipping
  ),
  OrderTimelineStep(
    VendorOrderStatus.DELIVERED,
    "Delivered",
    "Successfully delivered to the customer",
    Icons.Rounded.CheckCircle
  )
)

@Composable
fun VendorOrderDetailsScreen(
  group: VendorOrderGroupUiModel,
  isBusy: Boolean,
  actionError: String,
  infoMessage: String,
  onBack: () -> Unit,
  onRefresh: () -> Unit,
  onPrintPackingSlip: () -> Unit,
  onMarkReady: (String) -> Unit,
  onDismissAlert: () -> Unit,
  modifier: Modifier = Modifier
) {
  var vendorNote by rememberSaveable(group.orderId) { mutableStateOf(group.vendorNote) }
  val badgeSpec = vendorOrderStatusBadgeSpec(group.canonicalStatus)

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(VendorColors.Background)
      .testTag("vendor_order_details_screen"),
    contentPadding = PaddingValues(VendorSpacing.md),
    verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
      ) {
        SecondaryButton(
          text = "All orders",
          icon = Icons.AutoMirrored.Rounded.ArrowBack,
          onClick = onBack,
          modifier = Modifier
            .weight(1f)
            .testTag("vendor_order_details_back")
        )
        SecondaryButton(
          text = "Refresh status",
          icon = Icons.Rounded.Refresh,
          enabled = !isBusy,
          onClick = onRefresh,
          modifier = Modifier
            .weight(1f)
            .testTag("vendor_order_details_refresh")
        )
      }
    }

    if (actionError.isNotBlank()) {
      item {
        VendorAlertBanner(
          message = actionError,
          isError = true,
          onDismiss = onDismissAlert
        )
      }
    }

    if (infoMessage.isNotBlank()) {
      item {
        VendorAlertBanner(
          message = infoMessage,
          isError = false,
          onDismiss = onDismissAlert
        )
      }
    }

    // Header Card
    item {
      AppCard(
        containerColor = VendorColors.NavyHeader,
        borderColor = VendorColors.NavyHeader
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Order ${group.orderNumber}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              if (group.createdAt.isNotBlank()) {
                Text(
                  text = "Placed ${group.createdAt.take(16).replace('T', ' ')}",
                  style = MaterialTheme.typography.bodySmall,
                  color = VendorColors.NavyHeaderSubtitle
                )
              }
            }
            Spacer(modifier = Modifier.width(VendorSpacing.xs))
            StatusBadge(
              text = badgeSpec.label,
              tone = badgeSpec.tone,
              icon = badgeSpec.icon,
              modifier = Modifier.testTag("vendor_order_details_status_badge")
            )
          }

          HorizontalDivider(color = Color.White.copy(alpha = 0.14f))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            OrderHeaderMetric(label = "Items", value = "${group.items.size}")
            OrderHeaderMetric(
              label = "Order stage",
              value = group.orderStatusLabel.ifBlank { badgeSpec.label }
            )
            if (group.pickupStatusLabel.isNotBlank()) {
              OrderHeaderMetric(label = "Pickup", value = group.pickupStatusLabel)
            }
          }
        }
      }
    }

    // Status Timeline Card
    item {
      AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
          Text(
            text = "Live fulfillment timeline",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = VendorColors.TextPrimary
          )

          if (group.canonicalStatus == VendorOrderStatus.CANCELLED ||
            group.canonicalStatus == VendorOrderStatus.NOT_DELIVERED
          ) {
            val isCancelled = group.canonicalStatus == VendorOrderStatus.CANCELLED
            Surface(
              shape = VendorShapes.input,
              color = VendorColors.DangerRedBg,
              border = BorderStroke(1.dp, VendorColors.DangerRed.copy(alpha = 0.25f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(VendorSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
              ) {
                Icon(
                  imageVector = if (isCancelled) Icons.Rounded.Cancel else Icons.Rounded.ErrorOutline,
                  contentDescription = null,
                  tint = VendorColors.DangerRed,
                  modifier = Modifier.size(22.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Text(
                    text = badgeSpec.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = VendorColors.DangerRed
                  )
                  Text(
                    text = if (isCancelled) {
                      "This order was cancelled and does not need preparation or pickup."
                    } else {
                      "Courier marked this delivery as unsuccessful. Contact JTBazar support if needed."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = VendorColors.TextPrimary
                  )
                }
              }
            }
          } else {
            standardFulfillmentSteps.forEachIndexed { index, step ->
              val currentRank = group.canonicalStatus.progressionRank
              val reached = currentRank >= step.status.progressionRank
              val isCurrent = group.canonicalStatus == step.status

              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
              ) {
                Box(
                  modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                      when {
                        isCurrent -> VendorColors.PrimaryOrangeTint
                        reached -> VendorColors.SuccessGreenBg
                        else -> VendorColors.NeutralBadgeBg
                      }
                    ),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = step.icon,
                    contentDescription = null,
                    tint = when {
                      isCurrent -> VendorColors.PrimaryOrange
                      reached -> VendorColors.SuccessGreen
                      else -> VendorColors.TextMuted
                    },
                    modifier = Modifier.size(18.dp)
                  )
                }
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                  Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (reached) VendorColors.TextPrimary else VendorColors.TextMuted
                  )
                  Text(
                    text = step.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (reached) VendorColors.TextSecondary else VendorColors.TextMuted
                  )
                }
              }
              if (index < standardFulfillmentSteps.lastIndex) {
                Spacer(modifier = Modifier.height(2.dp))
              }
            }
          }
        }
      }
    }

    // Metadata Card
    if (group.confirmationStatusLabel.isNotBlank() ||
      group.trackingNumber.isNotBlank() ||
      group.pickupReadyAt.isNotBlank() ||
      group.updatedAt.isNotBlank() ||
      group.vendorNote.isNotBlank()
    ) {
      item {
        AppCard {
          Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.xs)) {
            Text(
              text = "Status synchronization details",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = VendorColors.TextPrimary
            )
            if (group.confirmationStatusLabel.isNotBlank()) {
              MetadataLine("Customer confirmation", group.confirmationStatusLabel)
            }
            if (group.pickupStatusLabel.isNotBlank()) {
              MetadataLine("Courier pickup status", group.pickupStatusLabel)
            }
            if (group.pickupReadyAt.isNotBlank()) {
              MetadataLine("Marked ready at", group.pickupReadyAt.take(16).replace('T', ' '))
            }
            if (group.trackingNumber.isNotBlank()) {
              MetadataLine("Tracking number", group.trackingNumber)
            }
            if (group.vendorNote.isNotBlank()) {
              MetadataLine("Vendor note", group.vendorNote)
            }
            if (group.updatedAt.isNotBlank()) {
              MetadataLine("Last updated", group.updatedAt.take(16).replace('T', ' '))
            }
          }
        }
      }
    }

    // Line items header
    item {
      Text(
        text = "Items in this order (${group.items.size})",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = VendorColors.TextPrimary,
        modifier = Modifier.padding(top = VendorSpacing.xxs)
      )
    }

    items(group.items, key = { it.id.ifBlank { "${it.productName}:${it.sku}:${it.variantSummary}" } }) { item ->
      val itemBadge = vendorOrderStatusBadgeSpec(item.canonicalStatus)
      AppCard {
        Column(
          modifier = Modifier.background(if (item.canonicalStatus == VendorOrderStatus.CANCELLED) Color(0xFFF3F4F6) else Color.Transparent, RoundedCornerShape(12.dp)).padding(VendorSpacing.sm),
          verticalArrangement = Arrangement.spacedBy(VendorSpacing.xs)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
              Text(
                text = item.productName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (item.canonicalStatus == VendorOrderStatus.CANCELLED) VendorColors.TextSecondary else VendorColors.TextPrimary
              )
              if (item.canonicalStatus == VendorOrderStatus.CANCELLED) {
                Text(
                  text = "Cancelled by admin",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = VendorColors.TextSecondary
                )
              }
              Text(
                text = "SKU: ${item.sku} • Variant: ${item.variantSummary}",
                style = MaterialTheme.typography.bodySmall,
                color = VendorColors.TextSecondary
              )
            }
            Spacer(modifier = Modifier.width(VendorSpacing.xs))
            StatusBadge(
              text = itemBadge.label,
              tone = itemBadge.tone,
              icon = itemBadge.icon
            )
          }

          HorizontalDivider(color = VendorColors.Divider)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Quantity: ×${item.quantity}",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = if (item.canonicalStatus == VendorOrderStatus.CANCELLED) VendorColors.TextSecondary else VendorColors.NavyHeader
            )
            val priceDisplay = item.lineTotal.ifBlank { item.unitPrice }
            if (priceDisplay.isNotBlank()) {
              Text(
                text = formatPkrPrice(priceDisplay),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (item.canonicalStatus == VendorOrderStatus.CANCELLED) VendorColors.TextSecondary else VendorColors.PrimaryOrange
              )
            }
          }
        }
      }
    }

    // Actions Card
    item {
      AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
          Text(
            text = "Fulfillment actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = VendorColors.TextPrimary
          )
          Text(
            text = "Customer contact, payment and final delivery are managed by JTBazar. You only prepare your shop items for courier pickup.",
            style = MaterialTheme.typography.bodySmall,
            color = VendorColors.TextSecondary
          )

          SecondaryButton(
            text = "Print packing slip",
            icon = Icons.Rounded.ReceiptLong,
            enabled = group.orderId.isNotBlank(),
            onClick = onPrintPackingSlip,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("vendor_order_details_packing_slip")
          )

          if (group.canonicalStatus.canMarkReadyForPickup) {
            VendorOutlinedTextField(
              value = vendorNote,
              onValueChange = { vendorNote = it.take(300) },
              label = "Pickup note for courier (optional)",
              leadingIcon = Icons.Rounded.Notes,
              minLines = 2,
              enabled = !isBusy
            )
            PrimaryButton(
              text = "Mark ready for pickup",
              icon = Icons.Rounded.CheckCircle,
              enabled = !isBusy && group.orderId.isNotBlank(),
              loading = isBusy,
              onClick = { onMarkReady(vendorNote.trim()) },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("vendor_order_details_mark_ready")
            )
          } else {
            Surface(
              shape = VendorShapes.input,
              color = VendorColors.Background,
              border = BorderStroke(1.dp, VendorColors.BorderSubtle),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(VendorSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VendorSpacing.xs)
              ) {
                Icon(
                  imageVector = badgeSpec.icon,
                  contentDescription = null,
                  tint = VendorColors.TextSecondary,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = "Current order status: ${badgeSpec.label}",
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.SemiBold,
                  color = VendorColors.TextSecondary
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun OrderHeaderMetric(label: String, value: String) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = VendorColors.NavyHeaderSubtitle
    )
    Text(
      text = value,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = Color.White,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun MetadataLine(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = VendorColors.TextSecondary
    )
    Spacer(modifier = Modifier.width(VendorSpacing.sm))
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall,
      fontWeight = FontWeight.SemiBold,
      color = VendorColors.TextPrimary
    )
  }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun VendorOrderDetailsScreenPreview() {
  VendorCenterTheme {
    VendorOrderDetailsScreen(
      group = VendorOrderGroupUiModel(
        orderId = "ord-1",
        orderNumber = "JT-94821",
        createdAt = "2026-11-12T10:30:00Z",
        status = VendorOrderStatus.PROCESSING.key,
        canonicalStatus = VendorOrderStatus.PROCESSING,
        orderStatusLabel = "Processing",
        confirmationStatusLabel = "Confirmed",
        pickupStatusLabel = "Pending",
        items = listOf(
          VendorOrderLineItemUiModel(
            id = "item-1",
            productName = "Multani Hand-Glazed Ceramic Plate Set",
            quantity = "2",
            sku = "MC-PLT-01",
            variantSummary = "Size: 10.5 inch",
            unitPrice = "2200",
            lineTotal = "4400",
            canonicalStatus = VendorOrderStatus.PROCESSING,
            rawJson = JSONObject()
          )
        ),
        rawItems = emptyList()
      ),
      isBusy = false,
      actionError = "",
      infoMessage = "",
      onBack = {},
      onRefresh = {},
      onPrintPackingSlip = {},
      onMarkReady = {},
      onDismissAlert = {}
    )
  }
}
