package com.example.ui.screens

import com.example.data.supabase.VendorApplication
import org.json.JSONArray
import org.json.JSONObject

enum class VendorBottomTab(val routeKey: String, val label: String) {
  OVERVIEW("overview", "Overview"),
  PRODUCTS("listings", "Products"),
  INVENTORY("inventory", "Inventory"),
  ORDERS("fulfilment", "Orders"),
  MORE("more", "More")
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
  val orderItemsCount: Int = 0,
  val itemsToPrepareCount: Int = 0,
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
  val rawJson: JSONObject
)

data class VendorOrderGroupUiModel(
  val orderId: String,
  val orderNumber: String,
  val createdAt: String,
  val status: String,
  val items: List<VendorOrderLineItemUiModel>,
  val rawItems: List<JSONObject>
)

data class VendorOrdersUiState(
  val searchQuery: String = "",
  val selectedStatus: String = "all",
  val page: Int = 0,
  val totalOrders: Int = 0,
  val pageSize: Int = 12,
  val ordersState: VendorSectionState<List<VendorOrderGroupUiModel>> = VendorSectionState.Loading,
  val isBusy: Boolean = false,
  val actionError: String = "",
  val infoMessage: String = ""
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
  val ownerFullName: String = "",
  val cnicNumber: String = "",
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
      name = p.safeVendorString("name", "Product").ifBlank { "Product" },
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
  return rows.groupBy { it.safeVendorString("order_id") }.map { (orderId, orderItems) ->
    val first = orderItems.first()
    val status = first.safeVendorString(
      "vendor_fulfillment_status",
      first.safeVendorString("status", "processing")
    ).ifBlank { "processing" }

    VendorOrderGroupUiModel(
      orderId = orderId,
      orderNumber = first.safeVendorString("order_number", orderId),
      createdAt = first.safeVendorString("created_at"),
      status = status,
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
          productName = item.safeVendorString(
            "product_name_snapshot",
            item.safeVendorString("product_name", "Order item")
          ),
          quantity = item.safeVendorString("quantity", "1"),
          sku = item.safeVendorString("sku_snapshot", "—").ifBlank { "—" },
          variantSummary = variantText,
          rawJson = item
        )
      },
      rawItems = orderItems
    )
  }
}

internal fun VendorApplication.toShopSettingsDraft(accountEmail: String): VendorShopSettingsUiState =
  VendorShopSettingsUiState(
    shopName = shopName,
    slug = slug,
    accountEmail = accountEmail.ifBlank { contactEmail },
    vendorType = vendorType,
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
    frontPath = cnicFrontPath,
    backPath = cnicBackPath,
    documentPath = documentPath
  )
