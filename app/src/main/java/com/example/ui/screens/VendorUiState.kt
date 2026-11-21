package com.example.ui.screens

import com.example.data.supabase.VendorApplication
import com.example.data.supabase.VendorOrderStatus
import com.example.data.supabase.WeeklyShopHours
import com.example.localization.AppLanguageManager
import org.json.JSONArray
import org.json.JSONObject

enum class VendorBottomTab(val routeKey: String, val label: String, val urduLabel: String) {
  OVERVIEW("overview", "Overview", "جائزہ"),
  PRODUCTS("listings", "Products", "مصنوعات"),
  INVENTORY("inventory", "Inventory", "اسٹاک"),
  ORDERS("fulfilment", "Orders", "آرڈرز"),
  MORE("more", "More", "مزید");

  fun localizedLabel(): String = AppLanguageManager.tr(label, urduLabel)
}

enum class VendorMoreSection {
  MENU,
  CASES_SUPPORT,
  SHOP_SETTINGS,
  ADVANCED_TOOLS
}

sealed interface VendorSectionState<out T> {
  data object Loading : VendorSectionState<Nothing>
  data class Error(val message: String) : VendorSectionState<Nothing>
  data object Empty : VendorSectionState<Nothing>
  data class Content<T>(val data: T) : VendorSectionState<T>
}

data class VendorProductUiModel(
  val id: String,
  val name: String,
  val priceRaw: String,
  val publicationStatus: String,
  val moderationStatus: String,
  val isHidden: Boolean,
  val rejectionReason: String,
  val deletionStatus: String,
  val imageUrl: String?,
  val rawJson: JSONObject
)

data class VendorOverviewUiState(
  val shopName: String = "",
  val isSellerActive: Boolean = true,
  val totalListings: Int = 0,
  val totalOrdersCount: Int = 0,
  val orderItemsCount: Int = 0,
  val itemsToPrepareCount: Int = 0,
  val statusCounts: Map<VendorOrderStatus, Int> = emptyMap(),
  val unreadNotificationsCount: Int = 0,
  val recentOrders: List<VendorOrderGroupUiModel> = emptyList(),
  val shopStatusLabel: String = "Active",
  val recentProductsState: VendorSectionState<List<VendorProductUiModel>> = VendorSectionState.Loading,
  val isRefreshing: Boolean = false
)

data class VendorProductsUiState(
  val productsState: VendorSectionState<List<VendorProductUiModel>> = VendorSectionState.Loading,
  val isMutating: Boolean = false,
  val actionError: String = "",
  val infoMessage: String = ""
)

data class VendorInventoryRowUiModel(
  val key: String,
  val productId: String,
  val variantId: String?,
  val variantLabel: String,
  val quantity: Int,
  val lowStockThreshold: Int
)

data class VendorInventoryGroupUiModel(
  val productId: String,
  val productName: String,
  val rows: List<VendorInventoryRowUiModel>
)

data class VendorInventoryUiState(
  val stockRecordsCount: Int = 0,
  val totalUnitsCount: Int = 0,
  val contentState: VendorSectionState<List<VendorInventoryGroupUiModel>> = VendorSectionState.Loading,
  val savingKey: String? = null,
  val actionError: String = "",
  val infoMessage: String = ""
)

data class VendorOrderLineItemUiModel(
  val productName: String,
  val quantity: String,
  val sku: String,
  val variantSummary: String,
  val rawJson: JSONObject,
  val id: String = "",
  val unitPrice: String = "",
  val lineTotal: String = "",
  val canonicalStatus: VendorOrderStatus = VendorOrderStatus.PENDING,
  val vendorNote: String = "",
  val trackingNumber: String = "",
  val trackingUrl: String = ""
)

data class VendorOrderGroupUiModel(
  val orderId: String,
  val orderNumber: String,
  val createdAt: String,
  val status: String,
  val items: List<VendorOrderLineItemUiModel>,
  val rawItems: List<JSONObject>,
  val canonicalStatus: VendorOrderStatus = VendorOrderStatus.parse(status) ?: VendorOrderStatus.PENDING,
  val orderStatusLabel: String = "",
  val confirmationStatusLabel: String = "",
  val pickupStatusLabel: String = "",
  val updatedAt: String = "",
  val pickupReadyAt: String = "",
  val trackingNumber: String = "",
  val vendorNote: String = ""
)

data class VendorOrdersUiState(
  val searchQuery: String = "",
  val selectedStatus: String = "all",
  val page: Int = 0,
  val totalOrders: Int = 0,
  val totalAllOrders: Int = 0,
  val statusCounts: Map<VendorOrderStatus, Int> = emptyMap(),
  val pageSize: Int = 12,
  val ordersState: VendorSectionState<List<VendorOrderGroupUiModel>> = VendorSectionState.Loading,
  val isBusy: Boolean = false,
  val actionError: String = "",
  val infoMessage: String = ""
)

data class VendorOrdersFilterResult(
  val statusCounts: Map<VendorOrderStatus, Int>,
  val totalAllOrders: Int,
  val totalOrderItemsCount: Int,
  val itemsToPrepareCount: Int,
  val filteredOrders: List<VendorOrderGroupUiModel>,
  val pageOrders: List<VendorOrderGroupUiModel>,
  val effectivePage: Int
)

data class VendorCaseItemUiModel(
  val id: String,
  val caseType: String,
  val status: String,
  val reason: String,
  val sellerResponse: String
)

data class VendorSupportMessageUiModel(
  val senderRole: String,
  val message: String,
  val createdAt: String
)

data class VendorSupportTicketUiModel(
  val id: String,
  val subject: String,
  val message: String,
  val status: String,
  val adminResponse: String
)

data class VendorCasesUiState(
  val casesState: VendorSectionState<List<VendorCaseItemUiModel>> = VendorSectionState.Loading,
  val ticketsState: VendorSectionState<List<VendorSupportTicketUiModel>> = VendorSectionState.Loading,
  val selectedTicketId: String? = null,
  val selectedTicketMessages: List<VendorSupportMessageUiModel> = emptyList(),
  val isSaving: Boolean = false,
  val bannerError: String = "",
  val infoMessage: String = ""
)

data class VendorShopSettingsUiState(
  val shopName: String = "",
  val slug: String = "",
  val accountEmail: String = "",
  val vendorType: String = "individual_seller",
  val businessType: String = "individual_seller",
  val ownerFullName: String = "",
  val cnicNumber: String = "",
  val cnicPrivacyAcknowledged: Boolean = false,
  val phone: String = "",
  val description: String = "",
  val currentAddress: String = "",
  val completeAddress: String = "",
  val locationLabel: String = "",
  val locationText: String = "",
  val showOnMap: Boolean = false,
  val latitude: String = "",
  val longitude: String = "",
  val shopImageUrl: String = "",
  val hasLocalShopImage: Boolean = false,
  val shopIsOpen: Boolean = true,
  val weeklyHours: WeeklyShopHours = WeeklyShopHours.defaults(),
  val frontPath: String = "",
  val backPath: String = "",
  val documentPath: String = "",
  val isSaving: Boolean = false,
  val errorMessage: String = "",
  val infoMessage: String = ""
)

internal fun JSONObject.safeVendorString(key: String, fallback: String = ""): String =
  optString(key, fallback).takeUnless { it == "null" } ?: fallback

internal fun mapProductsToUiModels(
  products: JSONArray,
  deletionRequests: JSONArray,
  imageUrls: Map<String, String>
): List<VendorProductUiModel> {
  val requestsByProduct = (0 until deletionRequests.length())
    .mapNotNull { deletionRequests.optJSONObject(it) }
    .groupBy { it.safeVendorString("product_id") }

  return (0 until products.length()).mapNotNull { index ->
    val p = products.optJSONObject(index) ?: return@mapNotNull null
    val id = p.safeVendorString("id")
    val deletionStatus = requestsByProduct[id]
      ?.maxByOrNull { it.safeVendorString("created_at") }
      ?.safeVendorString("status")
      ?.lowercase()
      .orEmpty()
    val moderation = p.safeVendorString("moderation_status", p.safeVendorString("status", "pending"))
      .ifBlank { "pending" }
      .lowercase()
    val rejection = p.safeVendorString("rejection_reason").trim()
      .ifBlank { p.safeVendorString("moderation_note").trim() }

    VendorProductUiModel(
      id = id,
      name = p.safeVendorString("name", AppLanguageManager.tr("Product", "پروڈکٹ")).ifBlank { AppLanguageManager.tr("Product", "پروڈکٹ") },
      priceRaw = p.safeVendorString("price", "0").ifBlank { "0" },
      publicationStatus = p.safeVendorString("publication_status", p.safeVendorString("status", "draft"))
        .ifBlank { "draft" },
      moderationStatus = moderation,
      isHidden = p.optBoolean("vendor_hidden", p.optBoolean("is_hidden", false)),
      rejectionReason = rejection,
      deletionStatus = deletionStatus,
      imageUrl = imageUrls[id]?.takeIf { it.isNotBlank() },
      rawJson = p
    )
  }
}

internal fun mapOrdersToUiGroups(orders: JSONArray): List<VendorOrderGroupUiModel> {
  val rows = (0 until orders.length()).mapNotNull { orders.optJSONObject(it) }
  return rows.groupBy { row ->
    row.safeVendorString("order_id").ifBlank {
      row.safeVendorString("order_number").ifBlank { row.safeVendorString("id") }
    }
  }.map { (orderId, orderItems) ->
    val first = orderItems.first()
    val canonicalStatus = VendorOrderStatus.resolveGroupStatus(orderItems)

    val rawOrderStatus = orderItems.firstNotNullOfOrNull {
      VendorOrderStatus.extractOrderLevelStatus(it)?.localizedLabel()
    }.orEmpty()
    val rawConfirmationStatus = orderItems.firstNotNullOfOrNull { item ->
      val nested = item.optJSONObject("orders")?.safeVendorString("confirmation_status").orEmpty()
      val value = item.safeVendorString("confirmation_status").ifBlank { nested }
      VendorOrderStatus.parse(value)?.localizedLabel()
        ?: when (value.trim().lowercase()) {
          "not_reachable" -> AppLanguageManager.tr("Not Reachable", "رابطہ ممکن نہیں")
          else -> null
        }
    }.orEmpty()
    val rawPickupStatus = orderItems.firstNotNullOfOrNull {
      VendorOrderStatus.extractPickupStatus(it)?.localizedLabel()
    }.orEmpty()

    val updatedAt = orderItems.firstNotNullOfOrNull { item ->
      item.safeVendorString("order_updated_at")
        .ifBlank { item.safeVendorString("pickup_updated_at") }
        .ifBlank { item.safeVendorString("vendor_updated_at") }
        .ifBlank { item.safeVendorString("updated_at") }
        .takeIf(String::isNotBlank)
    }.orEmpty()

    val pickupReadyAt = orderItems.firstNotNullOfOrNull {
      it.safeVendorString("pickup_ready_at").takeIf(String::isNotBlank)
    }.orEmpty()

    val trackingNumber = orderItems.firstNotNullOfOrNull {
      it.safeVendorString("tracking_number").takeIf(String::isNotBlank)
    }.orEmpty()

    val vendorNote = orderItems.firstNotNullOfOrNull { item ->
      item.safeVendorString("vendor_note")
        .ifBlank { item.safeVendorString("pickup_vendor_note") }
        .takeIf(String::isNotBlank)
    }.orEmpty()

    val createdAt = first.safeVendorString("order_created_at")
      .ifBlank { first.safeVendorString("created_at") }

    VendorOrderGroupUiModel(
      orderId = orderId,
      orderNumber = first.safeVendorString("order_number", orderId).ifBlank { orderId },
      createdAt = createdAt,
      status = canonicalStatus.key,
      canonicalStatus = canonicalStatus,
      orderStatusLabel = rawOrderStatus.ifBlank { canonicalStatus.localizedLabel() },
      confirmationStatusLabel = rawConfirmationStatus,
      pickupStatusLabel = rawPickupStatus,
      updatedAt = updatedAt,
      pickupReadyAt = pickupReadyAt,
      trackingNumber = trackingNumber,
      vendorNote = vendorNote,
      items = orderItems.map { item ->
        val variantObj = item.optJSONObject("variant_snapshot")
        val variantText = if (variantObj != null) {
          val label = variantObj.safeVendorString("label", variantObj.safeVendorString("option_label"))
          val value = variantObj.safeVendorString("value", variantObj.safeVendorString("option_value"))
          if (label.isNotBlank() && value.isNotBlank()) "$label: $value" else "—"
        } else {
          "—"
        }
        VendorOrderLineItemUiModel(
          id = item.safeVendorString("id", item.safeVendorString("order_item_id")),
          productName = item.safeVendorString(
            "product_name_snapshot",
            item.safeVendorString("product_name", AppLanguageManager.tr("Order item", "آرڈر آئٹم"))
          ),
          quantity = item.safeVendorString("quantity", "1"),
          sku = item.safeVendorString("sku_snapshot", "—").ifBlank { "—" },
          variantSummary = variantText,
          unitPrice = item.safeVendorString("unit_price"),
          lineTotal = item.safeVendorString("line_total"),
          canonicalStatus = VendorOrderStatus.resolveItemStatus(item),
          vendorNote = item.safeVendorString("vendor_note"),
          trackingNumber = item.safeVendorString("tracking_number"),
          trackingUrl = item.safeVendorString("tracking_url"),
          rawJson = item
        )
      },
      rawItems = orderItems
    )
  }.sortedByDescending { it.createdAt }
}

internal fun computeVendorOrderStatusCounts(
  groups: List<VendorOrderGroupUiModel>
): Map<VendorOrderStatus, Int> {
  val counts = VendorOrderStatus.entries.associateWith { 0 }.toMutableMap()
  groups.forEach { group ->
    counts[group.canonicalStatus] = (counts[group.canonicalStatus] ?: 0) + 1
  }
  return counts
}

internal fun filterAndPaginateVendorOrders(
  allGroups: List<VendorOrderGroupUiModel>,
  searchQuery: String,
  selectedStatus: String,
  page: Int,
  pageSize: Int = 12
): VendorOrdersFilterResult {
  val statusCounts = computeVendorOrderStatusCounts(allGroups)
  val targetStatus = if (selectedStatus.equals("all", ignoreCase = true)) {
    null
  } else {
    VendorOrderStatus.parse(selectedStatus)
  }
  val query = searchQuery.trim().lowercase()

  val filtered = allGroups.filter { group ->
    val statusMatches = targetStatus == null || group.canonicalStatus == targetStatus
    val searchMatches = query.isEmpty() ||
      group.orderNumber.lowercase().contains(query) ||
      group.orderId.lowercase().contains(query) ||
      group.canonicalStatus.label.lowercase().contains(query) ||
      group.canonicalStatus.urduLabel.lowercase().contains(query) ||
      group.items.any { item ->
        item.productName.lowercase().contains(query) ||
          item.sku.lowercase().contains(query) ||
          item.variantSummary.lowercase().contains(query)
      }
    statusMatches && searchMatches
  }

  val totalPages = if (filtered.isEmpty()) 1 else ((filtered.size - 1) / pageSize) + 1
  val safePage = page.coerceIn(0, totalPages - 1)
  val fromIndex = safePage * pageSize
  val toIndex = minOf(fromIndex + pageSize, filtered.size)
  val pageSlice = if (fromIndex < filtered.size) filtered.subList(fromIndex, toIndex) else emptyList()

  return VendorOrdersFilterResult(
    statusCounts = statusCounts,
    totalAllOrders = allGroups.size,
    totalOrderItemsCount = allGroups.sumOf { it.items.size },
    itemsToPrepareCount = allGroups.count { it.canonicalStatus.needsPreparation },
    filteredOrders = filtered,
    pageOrders = pageSlice,
    effectivePage = safePage
  )
}

internal fun VendorApplication.toShopSettingsDraft(accountEmail: String): VendorShopSettingsUiState =
  VendorShopSettingsUiState(
    shopName = shopName,
    slug = slug,
    accountEmail = accountEmail.ifBlank { contactEmail },
    vendorType = vendorType,
    businessType = businessType,
    ownerFullName = ownerFullName,
    cnicNumber = cnicNumber,
    phone = phone,
    description = description,
    currentAddress = currentAddress,
    completeAddress = completeAddress,
    locationLabel = shopLocationText,
    locationText = shopLocationText,
    showOnMap = showShopOnMap,
    latitude = shopLat?.toString().orEmpty(),
    longitude = shopLng?.toString().orEmpty(),
    shopImageUrl = shopImageUrl,
    shopIsOpen = shopIsOpen,
    weeklyHours = shopHours,
    frontPath = cnicFrontPath,
    backPath = cnicBackPath,
    documentPath = documentPath
  )
