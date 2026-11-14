package com.example.data.supabase

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

data class VendorApplication(
  val id: String,
  val status: String,
  val shopName: String,
  val rejectionReason: String? = null,
  val vendorLocked: Boolean = false,
  val lockReason: String? = null,
  val slug: String = "",
  val description: String = "",
  val phone: String = "",
  val currentAddress: String = "",
  val completeAddress: String = "",
  val contactEmail: String = "",
  val vendorType: String = "individual_seller",
  val ownerFullName: String = "",
  val cnicNumber: String = "",
  val shopLocationText: String = "",
  val shopLat: Double? = null,
  val shopLng: Double? = null,
  val shopImageUrl: String = "",
  val showShopOnMap: Boolean = false,
  val shopIsOpen: Boolean = true,
  val minimumOrderValue: Int = 0,
  val createdAt: String = "",
  val documentPath: String = "",
  val cnicFrontPath: String = "",
  val cnicBackPath: String = ""
)

object VendorService {
  private fun fail(result: Result<String>): String = result.getOrElse { throw it }
  private fun text(o: JSONObject, key: String, fallback: String = "") = o.optString(key, fallback).takeUnless { it == "null" } ?: fallback

  suspend fun getOwnApplication(): VendorApplication? {
    val user = SupabaseClient.currentUser.value ?: return null
    val raw = fail(SupabaseClient.get("seller_profiles?user_id=eq.${user.id}&select=id,status,shop_name,slug,description,phone,current_address,complete_address,contact_email,vendor_type,owner_full_name,cnic_number,shop_location_text,shop_lat,shop_lng,shop_image_url,show_shop_on_map,shop_is_open,minimum_order_value,rejection_reason,vendor_locked,vendor_lock_reason,created_at,document_path,cnic_front_path,cnic_back_path&limit=1"))
    val rows = JSONArray(raw)
    if (rows.length() == 0) return null
    val o = rows.getJSONObject(0)
    return VendorApplication(text(o,"id"), text(o,"status"), text(o,"shop_name"), text(o,"rejection_reason").ifBlank { null }, o.optBoolean("vendor_locked", false), text(o,"vendor_lock_reason").ifBlank { null },
      text(o,"slug"), text(o,"description"), text(o,"phone"), text(o,"current_address"), text(o,"complete_address"), text(o,"contact_email"),
      text(o,"vendor_type","individual_seller"), text(o,"owner_full_name"), text(o,"cnic_number"), text(o,"shop_location_text"),
      if (o.isNull("shop_lat")) null else o.optDouble("shop_lat"), if (o.isNull("shop_lng")) null else o.optDouble("shop_lng"),
      text(o,"shop_image_url"), o.optBoolean("show_shop_on_map", false), o.optBoolean("shop_is_open", true), o.optInt("minimum_order_value", 0), text(o,"created_at"), text(o,"document_path"), text(o,"cnic_front_path"), text(o,"cnic_back_path"))
  }

  suspend fun isVendor(): Boolean {
    if (SupabaseClient.currentUser.value == null) return false
    // user_roles intentionally denies direct client SELECT; use the narrowly scoped
    // security-definer RPC which checks only the currently authenticated user's role.
    val raw = fail(SupabaseClient.rpc("current_user_is_vendor", "{}"))
    return raw.trim().equals("true", ignoreCase = true)
  }

  suspend fun uploadPrivateDocument(path: String, bytes: ByteArray, mime: String): String {
    val result = SupabaseClient.uploadPrivateFile("vendor-documents", path, bytes, mime)
    return result.getOrThrow()
  }

  suspend fun submitApplication(data: JSONObject): VendorApplication {
    val raw = fail(SupabaseClient.post("seller_profiles", data.toString(), preferHeader = "return=representation"))
    val rows = JSONArray(raw)
    if (rows.length() == 0) throw IOException("Application was not returned after submission.")
    val o = rows.getJSONObject(0)
    return VendorApplication(text(o,"id"), text(o,"status","pending"), text(o,"shop_name"), createdAt = text(o,"created_at"))
  }

  suspend fun listProducts(): JSONArray = JSONArray(fail(SupabaseClient.rpc("vendor_list_products")))

  suspend fun listProductImageUrls(productId: String): List<String> {
    val raw = fail(SupabaseClient.get("product_images?product_id=eq.$productId&select=image_url,storage_path&order=sort_order.asc&limit=8"))
    val rows = JSONArray(raw)
    return (0 until rows.length()).mapNotNull { index ->
      val row = rows.optJSONObject(index) ?: return@mapNotNull null
      val direct = text(row, "image_url")
      if (direct.isNotBlank()) direct else {
        val path = text(row, "storage_path")
        if (path.isBlank()) null else {
          val encoded = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
          "${SupabaseConfig.storageUrl}/object/public/product-images/$encoded"
        }
      }
    }
  }
  suspend fun listOrders(page: Int = 0, search: String = "", status: String = "all"): JSONArray =
    JSONArray(fail(SupabaseClient.rpc("vendor_order_items_page", JSONObject()
      .put("p_limit", 12).put("p_offset", page.coerceAtLeast(0) * 12)
      .put("p_search", search.trim().takeIf { it.isNotEmpty() } ?: JSONObject.NULL)
      .put("p_status", status.takeIf { it in setOf("pending", "delivered", "not_delivered") } ?: JSONObject.NULL).toString())))
  suspend fun markOrderReady(orderId: String, note: String = "") {
    val raw = try {
      fail(SupabaseClient.rpc("vendor_mark_order_ready", JSONObject().put("p_order_id", orderId).put("p_vendor_note", note.ifBlank { JSONObject.NULL }).toString()))
    } catch (e: Exception) {
      val details = generateSequence(e as Throwable?) { it.cause }.mapNotNull { it.message }.joinToString(" ").lowercase()
      if (details.contains("only customer-confirmed orders")) {
        throw IOException("Customer ne abhi order confirm nahi kiya. Confirmation ke baad hi item Ready for pickup ho sakta hai.")
      }
      throw e
    }
    if (raw == "null" || raw == "false" || raw.isBlank()) throw IOException("Order could not be marked ready.")
  }
  suspend fun setProductVisibility(productId: String, hidden: Boolean) {
    fail(SupabaseClient.rpc("vendor_set_product_visibility", JSONObject().put("p_product_id", productId).put("p_hidden", hidden).toString()))
  }
  suspend fun setInventory(productId: String, quantity: Int, note: String = "Updated from Android app") {
    fail(SupabaseClient.rpc("vendor_set_product_inventory", JSONObject().put("p_product_id", productId).put("p_variant_id", JSONObject.NULL).put("p_new_quantity", quantity).put("p_note", note).toString()))
  }

  suspend fun uploadShopImage(bytes: ByteArray, mime: String): String {
    val user = SupabaseClient.currentUser.value ?: throw IOException("Please sign in again.")
    require(bytes.size <= 5 * 1024 * 1024) { "Shop images must be 5 MB or smaller." }
    require(mime in listOf("image/jpeg", "image/png", "image/webp", "image/avif")) { "Choose JPG, PNG, WebP or AVIF." }
    val ext = when (mime) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; else -> "avif" }
    val path = "${user.id}/${System.currentTimeMillis()}-${UUID.randomUUID()}.$ext"
    return SupabaseClient.uploadPublicFile("shop-images", path, bytes, mime).getOrThrow()
  }

  suspend fun uploadProductImage(productId: String, bytes: ByteArray, mime: String, altText: String = ""): String {
    val user = SupabaseClient.currentUser.value ?: throw IOException("Please sign in again.")
    require(productId.isNotBlank()) { "Product ID is missing; image cannot be attached." }
    require(bytes.isNotEmpty()) { "The selected image is empty. Choose the image again." }
    require(bytes.size <= 10 * 1024 * 1024) { "Product images must be 10 MB or smaller." }
    val normalizedMime = when (mime.lowercase().substringBefore(';').trim()) {
      "image/jpg", "image/pjpeg" -> "image/jpeg"
      "image/jpeg", "image/png", "image/webp", "image/avif" -> mime.lowercase().substringBefore(';').trim()
      else -> throw IOException("Unsupported image type ($mime). Choose JPG, PNG, WebP or AVIF.")
    }
    val ext = when (normalizedMime) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; else -> "avif" }
    val path = "${user.id}/$productId/${System.currentTimeMillis()}-${UUID.randomUUID()}.$ext"
    val url = try {
      SupabaseClient.uploadPublicFile("product-images", path, bytes, normalizedMime, upsert = false).getOrThrow()
    } catch (e: Exception) {
      throw IOException("Image upload to Supabase Storage failed (${e.message ?: "unknown storage error"}). Product was created, but its image was not uploaded.", e)
    }
    val countRows = try {
      JSONArray(fail(SupabaseClient.get("product_images?product_id=eq.$productId&select=sort_order&order=sort_order.desc&limit=1")))
    } catch (e: Exception) {
      throw IOException("Image file uploaded, but JTBazar could not read the product image list: ${e.message}", e)
    }
    val sort = if (countRows.length() > 0) countRows.getJSONObject(0).optInt("sort_order", -1) + 1 else 0
    val body = JSONObject().put("product_id", productId).put("storage_path", path)
      .put("alt_text", altText.take(200)).put("sort_order", sort).put("is_primary", sort == 0)
    try {
      fail(SupabaseClient.post("product_images", body.toString(), preferHeader = "return=representation"))
    } catch (e: Exception) {
      throw IOException("Image uploaded to Storage, but Supabase could not attach it to the product (product_images): ${e.message}", e)
    }
    return url
  }

  suspend fun updateOwnShop(data: JSONObject) {
    val user = SupabaseClient.currentUser.value ?: throw IOException("Please sign in again.")
    val result = fail(SupabaseClient.patch("seller_profiles?user_id=eq.${user.id}", data.toString()))
    if (result.isBlank() || result == "[]") throw IOException("Shop details were not updated.")
  }

  suspend fun resubmitApplication(): VendorApplication {
    val raw = fail(SupabaseClient.rpc("seller_resubmit_application"))
    val row = if (raw.trim().startsWith("[")) JSONArray(raw).optJSONObject(0) else JSONObject(raw)
    row ?: throw IOException("Application could not be resubmitted.")
    return VendorApplication(text(row,"id"), text(row,"status","pending"), text(row,"shop_name"), text(row,"rejection_reason").ifBlank { null }, createdAt = text(row,"created_at"))
  }

  suspend fun saveVariants(productId: String, variants: JSONArray) {
    require(variants.length() <= 100) { "A product can have no more than 100 variants." }
    fail(SupabaseClient.rpc("vendor_save_product_variants", JSONObject().put("p_product_id", productId).put("p_variants", variants).toString()))
  }

  suspend fun listVariants(productId: String): JSONArray =
    JSONArray(fail(SupabaseClient.get("product_variants?product_id=eq.$productId&select=id,option_label,option_value,sku,price_override,attributes,sort_order&order=sort_order.asc&limit=100")))

  suspend fun setDailyAvailability(productId: String, isDailyItem: Boolean, quantity: Int, minimumQuantity: Int = 1, minimumOrderValue: Int? = null, shopIsOpen: Boolean? = null) {
    require(quantity >= 0 && minimumQuantity >= 1) { "Enter valid daily quantities." }
    require(minimumOrderValue == null || minimumOrderValue >= 0) { "Minimum order value cannot be negative." }
    val params = JSONObject().put("p_product_id", productId).put("p_is_daily_item", isDailyItem)
      .put("p_available_quantity", quantity).put("p_minimum_quantity", minimumQuantity)
      .put("p_minimum_order_value", minimumOrderValue ?: JSONObject.NULL)
      .put("p_shop_is_open", shopIsOpen ?: JSONObject.NULL)
    fail(SupabaseClient.rpc("vendor_set_daily_availability", params.toString()))
  }

  suspend fun requestProductDeletion(productId: String, reason: String) {
    require(reason.trim().length >= 3) { "Please provide a reason of at least 3 characters." }
    fail(SupabaseClient.rpc("vendor_request_product_deletion", JSONObject().put("p_product_id", productId).put("p_reason", reason.trim()).toString()))
  }

  suspend fun listProductDeletionRequests(): JSONArray =
    JSONArray(fail(SupabaseClient.get("product_deletion_requests?select=id,product_id,status,reason,created_at&order=created_at.desc&limit=100")))

  suspend fun listInventoryMovements(): JSONArray =
    JSONArray(fail(SupabaseClient.get("inventory_movements?select=id,product_id,variant_id,quantity_delta,movement_type,note,created_at,products!inner(name),product_variants(option_label,option_value)&order=created_at.desc&limit=100")))

  suspend fun listCases(): JSONArray =
    JSONArray(fail(SupabaseClient.get("marketplace_cases?select=id,order_id,order_item_id,case_type,status,reason,evidence_path,seller_response,admin_decision,created_at,updated_at&order=created_at.desc&limit=100")))

  suspend fun respondToCase(caseId: String, response: String) {
    require(response.trim().length >= 3) { "Write a response of at least 3 characters." }
    val body = JSONObject().put("seller_response", response.trim()).put("status", "seller_responded")
    fail(SupabaseClient.patch("marketplace_cases?id=eq.$caseId", body.toString()))
  }

  suspend fun listSupportTickets(): JSONArray =
    JSONArray(fail(SupabaseClient.get("support_tickets?select=id,subject,message,status,admin_response,created_at,updated_at&order=created_at.desc&limit=100")))

  suspend fun listSupportMessages(ticketId: String): JSONArray =
    JSONArray(fail(SupabaseClient.get("support_ticket_messages?ticket_id=eq.$ticketId&select=*&order=created_at.asc&limit=500")))

  suspend fun sendSupportMessage(ticketId: String, message: String) {
    val user = SupabaseClient.currentUser.value ?: throw IOException("Please sign in again.")
    require(message.trim().isNotEmpty() && message.trim().length <= 4000) { "Enter a message up to 4,000 characters." }
    val body = JSONObject().put("ticket_id", ticketId).put("sender_id", user.id)
      .put("sender_role", "seller").put("message", message.trim())
    fail(SupabaseClient.post("support_ticket_messages", body.toString(), preferHeader = "return=representation"))
  }

  suspend fun createSupportTicket(subject: String, message: String) {
    val user = SupabaseClient.currentUser.value ?: throw IOException("Please sign in again.")
    val profile = getOwnApplication() ?: throw IOException("Seller profile was not found.")
    require(subject.trim().length >= 3 && message.trim().length >= 5) { "Enter a subject and a detailed message." }
    val body = JSONObject().put("seller_id", profile.id).put("created_by", user.id)
      .put("subject", subject.trim()).put("message", message.trim())
    fail(SupabaseClient.post("support_tickets", body.toString(), preferHeader = "return=representation"))
  }

  suspend fun listInventory(): JSONArray =
    JSONArray(fail(SupabaseClient.get("product_inventory?select=id,product_id,variant_id,quantity,low_stock_threshold,products!inner(name),product_variants(option_label,option_value)&order=updated_at.desc&limit=200")))

  suspend fun setVariantInventory(productId: String, variantId: String?, quantity: Int, note: String = "Updated from Android app") {
    require(quantity >= 0) { "Stock cannot be negative." }
    val params = JSONObject().put("p_product_id", productId).put("p_variant_id", variantId ?: JSONObject.NULL)
      .put("p_new_quantity", quantity).put("p_note", note)
    fail(SupabaseClient.rpc("vendor_set_product_inventory", params.toString()))
  }

  suspend fun updateFoodProductDetails(
    productId: String, sellingUnit: String, unitPrice: Double, quantityIncrement: Int,
    minimumQuantity: Int, isDailyItem: Boolean, dailyStatus: String, dailyAvailableQuantity: Int
  ) {
    require(sellingUnit in setOf("kg", "g", "l", "ml", "piece", "dozen", "pack")) { "Select a valid food selling unit." }
    require(unitPrice >= 0 && quantityIncrement >= 1 && minimumQuantity >= 1 && dailyAvailableQuantity >= 0) {
      "Enter valid food unit price and quantities."
    }
    require(dailyStatus in setOf("available", "not_today", "coming_soon")) { "Select a valid daily availability status." }
    val params = JSONObject()
      .put("p_product_id", productId)
      .put("p_selling_unit", sellingUnit)
      .put("p_unit_price", unitPrice)
      .put("p_quantity_increment", quantityIncrement)
      .put("p_minimum_quantity", minimumQuantity)
      .put("p_is_daily_item", isDailyItem)
      .put("p_daily_status", dailyStatus)
      .put("p_daily_available_quantity", dailyAvailableQuantity)
    fail(SupabaseClient.rpc("vendor_update_food_product_details", params.toString()))
  }

  suspend fun setFoodProductDetails(productId: String, sellingUnit: String, unitPrice: Double, quantityIncrement: Int, minimumQuantity: Int) {
    require(sellingUnit in setOf("kg", "g", "l", "ml", "piece", "dozen", "pack")) { "Select a valid food selling unit." }
    require(unitPrice >= 0 && quantityIncrement >= 1 && minimumQuantity >= 1) { "Enter valid food unit pricing and quantities." }
    fail(SupabaseClient.rpc("vendor_set_food_product_details", JSONObject()
      .put("p_product_id", productId).put("p_selling_unit", sellingUnit)
      .put("p_unit_price", unitPrice).put("p_quantity_increment", quantityIncrement)
      .put("p_minimum_quantity", minimumQuantity).toString()))
  }

  suspend fun updateProductFull(productId: String, payload: JSONObject) {
    require(payload.optString("p_name").trim().length >= 2) { "Product name must be at least 2 characters." }
    require(payload.optString("p_slug").matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) { "Enter a valid product URL slug." }
    require(payload.optDouble("p_price", -1.0) > 0) { "Selling price must be greater than zero." }
    val supplierCost = if (payload.isNull("p_supplier_cost")) 0.0 else payload.optDouble("p_supplier_cost", 0.0)
    val fulfilment = payload.optDouble("p_fulfillment_expense", 0.0)
    val profit = payload.optDouble("p_minimum_profit", 0.0)
    require(payload.optDouble("p_price") >= supplierCost + fulfilment + profit) {
      "Selling price must cover supplier cost, fulfilment expense and minimum profit."
    }
    try {
      val ownedRows = JSONArray(fail(SupabaseClient.rpc("vendor_list_products")))
      val isOwned = (0 until ownedRows.length()).any { ownedRows.optJSONObject(it)?.optString("id") == productId }
      if (!isOwned) throw IOException("Access denied: this listing is not linked to the currently signed-in vendor account. Refresh Listings and make sure you are signed in with the account that owns this product.")
      fail(SupabaseClient.rpc("vendor_update_product", payload.put("p_product_id", productId).toString()))
    } catch (e: Exception) {
      val detail = e.message.orEmpty()
      when {
        detail.contains("authenticator verification", ignoreCase = true) ->
          throw IOException("JTBazar authenticator verification is required. Complete the verification challenge, then try saving again.", e)
        detail.contains("Product access was denied", ignoreCase = true) ||
          detail.contains("not available to your vendor account", ignoreCase = true) ->
          throw IOException("Access denied: this product does not belong to the currently signed-in vendor account, or the vendor account is not approved. Refresh Listings and sign in with the correct vendor account.", e)
        else -> throw e
      }
    }
  }

  suspend fun updateProduct(productId: String, name: String, description: String, price: Int, stock: Int) {
    require(name.trim().length >= 2 && price > 0 && stock >= 0) { "Check product name, price and stock." }
    val current = JSONArray(fail(SupabaseClient.rpc("vendor_list_products")))
    val product = (0 until current.length()).mapNotNull { current.optJSONObject(it) }.firstOrNull { it.optString("id") == productId }
      ?: throw IOException("Product not found.")
    val params = JSONObject().put("p_product_id", productId).put("p_category_id", product.optString("category_id").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
      .put("p_name", name.trim()).put("p_slug", product.optString("slug")).put("p_sku", product.optString("sku").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
      .put("p_short_description", description.take(180)).put("p_description", description).put("p_price", product.opt("vendor_base_price") ?: price)
      .put("p_supplier_cost", product.opt("supplier_cost") ?: JSONObject.NULL).put("p_fulfillment_expense", product.optInt("fulfillment_expense",0))
      .put("p_minimum_profit", product.optInt("minimum_profit",0)).put("p_discount_percent", product.optInt("discount_percent",0))
      .put("p_weight_grams", product.opt("weight_grams") ?: JSONObject.NULL).put("p_specifications", product.optJSONArray("specifications") ?: JSONArray())
      .put("p_dimensions", product.optJSONObject("dimensions") ?: JSONObject()).put("p_seo_title", product.opt("seo_title") ?: JSONObject.NULL)
      .put("p_seo_description", product.opt("seo_description") ?: JSONObject.NULL)
    fail(SupabaseClient.rpc("vendor_update_product", params.toString()))
    setInventory(productId, stock)
  }

  suspend fun listCategories(): JSONArray =
    JSONArray(fail(SupabaseClient.get("categories?select=id,name,slug,parent_id,is_food_category&is_active=eq.true&order=sort_order.asc&limit=500")))

  suspend fun createProduct(
    name: String, slug: String, description: String, price: Int, stock: Int,
    categoryId: String? = null, sku: String? = null, shortDescription: String = "",
    supplierCost: Double? = null, fulfilmentExpense: Double = 0.0, minimumProfit: Double = 0.0,
    discountPercent: Double = 0.0, weightGrams: Int? = null,
    specifications: JSONArray = JSONArray(), dimensions: JSONObject = JSONObject(),
    seoTitle: String? = null, seoDescription: String? = null, trackInventory: Boolean = true
  ): String {
    require(name.trim().length >= 2 && slug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) { "Enter a valid product name and slug." }
    require(price > 0 && stock >= 0 && discountPercent in 0.0..100.0) { "Check price, discount and stock." }
    val params = JSONObject()
      .put("p_category_id", categoryId ?: JSONObject.NULL)
      .put("p_name", name.trim()).put("p_slug", slug).put("p_sku", sku ?: JSONObject.NULL)
      .put("p_short_description", shortDescription.take(300)).put("p_description", description)
      .put("p_price", price).put("p_supplier_cost", supplierCost ?: JSONObject.NULL)
      .put("p_fulfillment_expense", fulfilmentExpense).put("p_minimum_profit", minimumProfit)
      .put("p_track_inventory", trackInventory).put("p_discount_percent", discountPercent)
      .put("p_weight_grams", weightGrams ?: JSONObject.NULL).put("p_specifications", specifications)
      .put("p_dimensions", dimensions).put("p_seo_title", seoTitle ?: JSONObject.NULL)
      .put("p_seo_description", seoDescription ?: JSONObject.NULL).put("p_initial_stock", stock)
    return fail(SupabaseClient.rpc("vendor_create_product", params.toString())).trim('"')
  }
}
