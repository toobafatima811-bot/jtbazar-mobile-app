package com.example.data.supabase

import android.util.Log
import org.json.JSONObject

/**
 * Single source of truth for vendor-visible order statuses across JTBazar.
 * Reconciles `orders.order_status`, `orders.confirmation_status`,
 * `vendor_order_pickups.status`, and `order_items.vendor_fulfillment_status`
 * so the Android app never remains stuck on "Pending" when another status
 * is recorded in Supabase.
 */
enum class VendorOrderStatus(
  val key: String,
  val label: String,
  val progressionRank: Int
) {
  PENDING("pending", "Pending", 1),
  CONFIRMED("confirmed", "Confirmed", 2),
  PROCESSING("processing", "Processing", 3),
  READY_FOR_PICKUP("ready_for_pickup", "Ready for Pickup", 4),
  SHIPPED("shipped", "Collected by JTBazar", 5),
  DELIVERED("delivered", "Delivered", 6),
  NOT_DELIVERED("not_delivered", "Not Delivered", 7),
  CANCELLED("cancelled", "Cancelled", 8);

  val needsPreparation: Boolean
    get() = this == PENDING || this == CONFIRMED || this == PROCESSING

  val canMarkReadyForPickup: Boolean
    get() = this == PENDING || this == CONFIRMED || this == PROCESSING

  val isTerminal: Boolean
    get() = this == DELIVERED || this == NOT_DELIVERED || this == CANCELLED

  companion object {
    private const val TAG = "VendorOrderStatus"

    fun parse(raw: String?): VendorOrderStatus? {
      val normalized = raw
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
        ?.lowercase()
        ?.replace('-', '_')
        ?.replace(' ', '_')
        ?: return null

      return when (normalized) {
        "pending", "awaiting_vendor", "awaiting_confirmation", "unconfirmed" -> PENDING
        "confirmed", "customer_confirmed" -> CONFIRMED
        "processing", "preparing", "accepted", "packed", "in_progress" -> PROCESSING
        "ready_for_pickup", "ready", "ready_for_collection" -> READY_FOR_PICKUP
        "shipped", "picked_up", "in_transit", "out_for_delivery", "dispatched" -> SHIPPED
        "delivered", "completed" -> DELIVERED
        "not_delivered", "failed_delivery", "delivery_failed", "returned", "undelivered" -> NOT_DELIVERED
        "cancelled", "canceled", "rejected" -> CANCELLED
        else -> {
          Log.w(TAG, "Unrecognized raw order status encountered: $raw")
          null
        }
      }
    }

    fun formatLabel(raw: String?): String = parse(raw)?.label ?: "Pending"

    private fun JSONObject.safeText(key: String): String =
      optString(key, "").takeUnless { it.equals("null", ignoreCase = true) }.orEmpty().trim()

    fun extractOrderLevelStatus(row: JSONObject): VendorOrderStatus? {
      val nestedOrder = row.optJSONObject("orders")
      val directOrderStatus = parse(
        row.safeText("order_status")
          .ifBlank { nestedOrder?.safeText("order_status").orEmpty() }
          .ifBlank { row.safeText("vendor_order_status") }
      )
      val confirmationStatus = parse(
        row.safeText("confirmation_status")
          .ifBlank { nestedOrder?.safeText("confirmation_status").orEmpty() }
      )

      if (directOrderStatus == CANCELLED || confirmationStatus == CANCELLED) return CANCELLED
      if (directOrderStatus == NOT_DELIVERED) return NOT_DELIVERED
      if (directOrderStatus == DELIVERED) return DELIVERED
      if (directOrderStatus == SHIPPED) return SHIPPED

      return listOfNotNull(directOrderStatus, confirmationStatus)
        .maxByOrNull { it.progressionRank }
    }

    fun extractPickupStatus(row: JSONObject): VendorOrderStatus? {
      val nestedPickup = row.optJSONObject("vendor_order_pickups")
      val raw = row.safeText("pickup_status")
        .ifBlank { row.safeText("vendor_pickup_status") }
        .ifBlank { row.safeText("vendor_order_pickup_status") }
        .ifBlank { nestedPickup?.safeText("status").orEmpty() }
      return parse(raw)
    }

    fun extractFulfillmentStatus(row: JSONObject): VendorOrderStatus? {
      val fulfillment = parse(row.safeText("vendor_fulfillment_status"))
      val generic = parse(row.safeText("status"))
      return when {
        fulfillment == null -> generic
        generic == null -> fulfillment
        fulfillment == CANCELLED || generic == CANCELLED -> CANCELLED
        fulfillment == NOT_DELIVERED || generic == NOT_DELIVERED -> NOT_DELIVERED
        fulfillment == DELIVERED || generic == DELIVERED -> DELIVERED
        else -> maxOf(fulfillment, generic, compareBy { it.progressionRank })
      }
    }

    /**
     * Reconciles all available status signals for a single order item row.
     */
    fun resolveItemStatus(row: JSONObject): VendorOrderStatus {
      val orderLevel = extractOrderLevelStatus(row)
      val pickup = extractPickupStatus(row)
      val itemLevel = extractFulfillmentStatus(row)

      // Order-level cancellation/completion/dispatch always overrides stale item defaults.
      if (orderLevel != null && (orderLevel.isTerminal || orderLevel == SHIPPED)) {
        return orderLevel
      }
      if (pickup == CANCELLED || itemLevel == CANCELLED) return CANCELLED
      if (itemLevel == NOT_DELIVERED) return NOT_DELIVERED
      if (itemLevel == DELIVERED) return DELIVERED
      if (pickup == SHIPPED || itemLevel == SHIPPED) return SHIPPED
      if (pickup == READY_FOR_PICKUP || orderLevel == READY_FOR_PICKUP || itemLevel == READY_FOR_PICKUP) {
        return READY_FOR_PICKUP
      }

      return listOfNotNull(orderLevel, pickup, itemLevel)
        .maxByOrNull { it.progressionRank }
        ?: PENDING
    }

    /**
     * Reconciles effective status across all vendor items belonging to one order.
     */
    fun resolveGroupStatus(items: List<JSONObject>): VendorOrderStatus {
      if (items.isEmpty()) return PENDING

      // A cancelled product must not block readiness or make the whole vendor order look cancelled.
      val activeItems = items.filter { extractFulfillmentStatus(it) != CANCELLED }
      if (activeItems.isEmpty()) return CANCELLED

      val orderLevels = activeItems.mapNotNull { extractOrderLevelStatus(it) }
      val pickupLevels = activeItems.mapNotNull { extractPickupStatus(it) }
      val itemStatuses = activeItems.map { resolveItemStatus(it) }

      // Terminal or shipped order-level status applies to the active items in this group.
      orderLevels.firstOrNull { it == CANCELLED }?.let { return it }
      orderLevels.firstOrNull { it == NOT_DELIVERED }?.let { return it }
      orderLevels.firstOrNull { it == DELIVERED }?.let { return it }
      orderLevels.firstOrNull { it == SHIPPED }?.let { return it }

      // Pickup cancellation only closes the group when no active products remain.
      pickupLevels.firstOrNull { it == SHIPPED }?.let { return it }

      if (itemStatuses.all { it == NOT_DELIVERED }) return NOT_DELIVERED
      if (itemStatuses.all { it == DELIVERED }) return DELIVERED
      if (itemStatuses.all { it == SHIPPED }) return SHIPPED

      if (pickupLevels.any { it == READY_FOR_PICKUP } ||
        orderLevels.any { it == READY_FOR_PICKUP } ||
        itemStatuses.any { it == READY_FOR_PICKUP }
      ) {
        return READY_FOR_PICKUP
      }

      val highestOrder = orderLevels.maxByOrNull { it.progressionRank }
      val highestItem = itemStatuses.maxByOrNull { it.progressionRank }
      return listOfNotNull(highestOrder, highestItem)
        .maxByOrNull { it.progressionRank }
        ?: PENDING
    }
  }
}
