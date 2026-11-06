package com.example.data.supabase

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Service executing structured PostgREST queries against Supabase tables and RPC endpoints.
 */
object SupabaseApiService {
  private const val TAG = "SupabaseApiService"

  // ----------------------------------------------------
  // PRODUCTS
  // ----------------------------------------------------

  suspend fun fetchProducts(
    category: String? = null,
    isFeatured: Boolean? = null,
    limit: Int = 50
  ): Result<List<SupabaseProductDto>> = withContext(Dispatchers.IO) {
    try {
      val queryParams = mutableListOf("select=*")
      if (!category.isNullOrBlank()) {
        queryParams.add("category=ilike.*${category.trim()}*")
      }
      if (isFeatured == true) {
        queryParams.add("is_featured=eq.true")
      }
      queryParams.add("limit=$limit")
      queryParams.add("order=created_at.desc.nullslast")

      val query = "products?" + queryParams.joinToString("&")
      val res = SupabaseClient.get(query)

      res.mapCatching { jsonStr ->
        parseProductsJson(jsonStr)
      }
    } catch (e: Exception) {
      Log.e(TAG, "fetchProducts error", e)
      Result.failure(e)
    }
  }

  suspend fun searchProducts(query: String): Result<List<SupabaseProductDto>> = withContext(Dispatchers.IO) {
    try {
      val encoded = query.trim()
      val filter = "title=ilike.*$encoded*,description=ilike.*$encoded*,vendor=ilike.*$encoded*,category=ilike.*$encoded*"
      val url = "products?select=*&or=($filter)&limit=30"
      val res = SupabaseClient.get(url)

      res.mapCatching { jsonStr ->
        parseProductsJson(jsonStr)
      }
    } catch (e: Exception) {
      Log.e(TAG, "searchProducts error", e)
      Result.failure(e)
    }
  }

  suspend fun fetchProductById(id: String): Result<SupabaseProductDto> = withContext(Dispatchers.IO) {
    try {
      val res = SupabaseClient.get("products?id=eq.$id&select=*")
      res.mapCatching { jsonStr ->
        val list = parseProductsJson(jsonStr)
        list.firstOrNull() ?: throw IOException("Product $id not found")
      }
    } catch (e: Exception) {
      Log.e(TAG, "fetchProductById error", e)
      Result.failure(e)
    }
  }

  private fun parseProductsJson(jsonStr: String): List<SupabaseProductDto> {
    val result = mutableListOf<SupabaseProductDto>()
    val arr = JSONArray(jsonStr)
    for (i in 0 until arr.length()) {
      val obj = arr.getJSONObject(i)
      val id = obj.optString("id", "")
      val title = obj.optString("title", obj.optString("name", "Product"))
      val vendor = obj.optString("vendor", obj.optString("vendor_name", "JTBazar Seller"))
      val category = obj.optString("category", obj.optString("category_name", "General"))
      val price = obj.optInt("price", 0)
      val originalPrice = obj.optInt("original_price", price)
      val discountPercent = obj.optInt("discount_percent", if (originalPrice > price) ((originalPrice - price) * 100 / originalPrice) else 0)
      val rating = obj.optDouble("rating", 4.8)
      val reviewCount = obj.optInt("review_count", 0)
      val imageUrl = if (obj.has("image_url")) obj.optString("image_url") else if (obj.has("thumbnail")) obj.optString("thumbnail") else null
      val badge = if (obj.has("badge")) obj.optString("badge") else if (discountPercent > 0) "$discountPercent% OFF" else null
      val inStock = obj.optBoolean("in_stock", true)
      val stockQty = obj.optInt("stock_quantity", 50)
      val craftStory = obj.optString("craft_story", obj.optString("description", ""))

      // Highlights
      val highlights = mutableListOf<String>()
      val hlArr = obj.optJSONArray("highlights")
      if (hlArr != null) {
        for (j in 0 until hlArr.length()) {
          highlights.add(hlArr.getString(j))
        }
      }

      // Palettes / variants
      val palettes = mutableListOf<String>()
      val palArr = obj.optJSONArray("palettes")
      if (palArr != null) {
        for (j in 0 until palArr.length()) {
          palettes.add(palArr.getString(j))
        }
      }

      // Sizes
      val sizes = mutableListOf<String>()
      val sizeArr = obj.optJSONArray("sizes")
      if (sizeArr != null) {
        for (j in 0 until sizeArr.length()) {
          sizes.add(sizeArr.getString(j))
        }
      }

      result.add(
        SupabaseProductDto(
          id = id,
          title = title,
          vendor = vendor,
          category = category,
          price = price,
          originalPrice = originalPrice,
          discountPercent = discountPercent,
          rating = rating,
          reviewCount = reviewCount,
          imageUrl = imageUrl,
          badge = badge,
          inStock = inStock,
          stockQuantity = stockQty,
          craftStory = craftStory,
          highlights = highlights,
          palettes = palettes,
          sizes = sizes,
          isArtisanCollection = obj.optBoolean("is_artisan", false),
          isFeatured = obj.optBoolean("is_featured", false),
          isBestSeller = obj.optBoolean("is_best_seller", false),
          isNewArrival = obj.optBoolean("is_new_arrival", false)
        )
      )
    }
    return result
  }

  // ----------------------------------------------------
  // CATEGORIES
  // ----------------------------------------------------

  suspend fun fetchCategories(): Result<List<SupabaseCategoryDto>> = withContext(Dispatchers.IO) {
    try {
      val res = SupabaseClient.get("categories?select=*&order=display_order.asc.nullslast")
      res.mapCatching { jsonStr ->
        val list = mutableListOf<SupabaseCategoryDto>()
        val arr = JSONArray(jsonStr)
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          list.add(
            SupabaseCategoryDto(
              id = obj.optString("id", ""),
              title = obj.optString("title", obj.optString("name", "Category")),
              countText = obj.optString("count_text", "100+ Products"),
              sampleItems = obj.optString("sample_items", obj.optString("description", "")),
              imageUrl = if (obj.has("image_url")) obj.optString("image_url") else null,
              badge = if (obj.has("badge")) obj.optString("badge") else null,
              displayOrder = obj.optInt("display_order", i)
            )
          )
        }
        list
      }
    } catch (e: Exception) {
      Log.e(TAG, "fetchCategories error", e)
      Result.failure(e)
    }
  }

  // ----------------------------------------------------
  // CART
  // ----------------------------------------------------

  suspend fun fetchCart(userId: String): Result<String> = withContext(Dispatchers.IO) {
    SupabaseClient.get("cart_items?user_id=eq.$userId&select=*,product:products(*)")
  }

  suspend fun addToCart(
    userId: String,
    productId: String,
    quantity: Int,
    palette: String?,
    size: String?
  ): Result<String> = withContext(Dispatchers.IO) {
    val json = JSONObject().apply {
      put("user_id", userId)
      put("product_id", productId)
      put("quantity", quantity)
      if (!palette.isNullOrBlank()) put("selected_palette", palette)
      if (!size.isNullOrBlank()) put("selected_size", size)
    }
    SupabaseClient.post("cart_items", json.toString())
  }

  suspend fun updateCartQuantity(cartItemId: String, quantity: Int): Result<String> = withContext(Dispatchers.IO) {
    val json = JSONObject().apply {
      put("quantity", quantity)
    }
    SupabaseClient.patch("cart_items?id=eq.$cartItemId", json.toString())
  }

  suspend fun removeCartItem(cartItemId: String): Result<Boolean> = withContext(Dispatchers.IO) {
    SupabaseClient.delete("cart_items?id=eq.$cartItemId")
  }

  suspend fun clearCart(userId: String): Result<Boolean> = withContext(Dispatchers.IO) {
    SupabaseClient.delete("cart_items?user_id=eq.$userId")
  }

  // ----------------------------------------------------
  // WISHLIST
  // ----------------------------------------------------

  suspend fun fetchWishlist(userId: String): Result<String> = withContext(Dispatchers.IO) {
    SupabaseClient.get("wishlist_items?user_id=eq.$userId&select=*,product:products(*)")
  }

  suspend fun addToWishlist(userId: String, productId: String): Result<String> = withContext(Dispatchers.IO) {
    val json = JSONObject().apply {
      put("user_id", userId)
      put("product_id", productId)
    }
    SupabaseClient.post("wishlist_items", json.toString())
  }

  suspend fun removeFromWishlist(userId: String, productId: String): Result<Boolean> = withContext(Dispatchers.IO) {
    SupabaseClient.delete("wishlist_items?user_id=eq.$userId&product_id=eq.$productId")
  }

  // ----------------------------------------------------
  // ADDRESSES
  // ----------------------------------------------------

  suspend fun fetchAddresses(userId: String): Result<List<SupabaseAddressDto>> = withContext(Dispatchers.IO) {
    try {
      val res = SupabaseClient.get("customer_addresses?user_id=eq.$userId&select=*&order=is_default.desc")
      res.mapCatching { jsonStr ->
        val arr = JSONArray(jsonStr)
        val list = mutableListOf<SupabaseAddressDto>()
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          list.add(
            SupabaseAddressDto(
              id = obj.optString("id", ""),
              userId = obj.optString("user_id", userId),
              name = obj.optString("name", obj.optString("recipient_name", "")),
              phone = obj.optString("phone", ""),
              addressLine = obj.optString("address_line", obj.optString("address", "")),
              area = obj.optString("area", ""),
              city = obj.optString("city", "Lahore"),
              province = obj.optString("province", "Punjab"),
              postalCode = if (obj.has("postal_code")) obj.optString("postal_code") else null,
              tag = obj.optString("tag", "HOME"),
              isDefault = obj.optBoolean("is_default", false)
            )
          )
        }
        list
      }
    } catch (e: Exception) {
      Log.e(TAG, "fetchAddresses error", e)
      Result.failure(e)
    }
  }

  suspend fun saveAddress(address: SupabaseAddressDto): Result<String> = withContext(Dispatchers.IO) {
    val json = JSONObject().apply {
      if (address.id.isNotBlank()) put("id", address.id)
      put("user_id", address.userId)
      put("name", address.name)
      put("phone", address.phone)
      put("address_line", address.addressLine)
      put("area", address.area)
      put("city", address.city)
      put("province", address.province)
      put("tag", address.tag)
      put("is_default", address.isDefault)
    }
    if (address.id.isNotBlank()) {
      SupabaseClient.patch("customer_addresses?id=eq.${address.id}", json.toString())
    } else {
      SupabaseClient.post("customer_addresses", json.toString())
    }
  }

  // ----------------------------------------------------
  // ORDERS
  // ----------------------------------------------------

  suspend fun fetchOrders(userId: String): Result<List<SupabaseOrderDto>> = withContext(Dispatchers.IO) {
    try {
      val res = SupabaseClient.get("orders?user_id=eq.$userId&select=*&order=created_at.desc")
      res.mapCatching { jsonStr ->
        val arr = JSONArray(jsonStr)
        val list = mutableListOf<SupabaseOrderDto>()
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          list.add(
            SupabaseOrderDto(
              id = obj.optString("id", ""),
              orderNumber = obj.optString("order_number", obj.optString("order_id", "JT-00000-PK")),
              userId = obj.optString("user_id", userId),
              subtotal = obj.optInt("subtotal", 0),
              discount = obj.optInt("discount", 0),
              deliveryFee = obj.optInt("delivery_fee", 0),
              grandTotal = obj.optInt("grand_total", obj.optInt("total", 0)),
              paymentMethod = obj.optString("payment_method", "COD"),
              paymentStatus = obj.optString("payment_status", "PENDING"),
              orderStatus = obj.optString("order_status", obj.optString("status", "PENDING")),
              deliveryNote = if (obj.has("delivery_note")) obj.optString("delivery_note") else null,
              addressName = obj.optString("address_name", ""),
              addressPhone = obj.optString("address_phone", ""),
              addressLine = obj.optString("address_line", ""),
              addressArea = obj.optString("address_area", ""),
              addressCity = obj.optString("address_city", "Lahore"),
              createdAt = obj.optString("created_at", "")
            )
          )
        }
        list
      }
    } catch (e: Exception) {
      Log.e(TAG, "fetchOrders error", e)
      Result.failure(e)
    }
  }

  suspend fun fetchOrderItems(orderId: String): Result<List<SupabaseOrderItemDto>> = withContext(Dispatchers.IO) {
    try {
      val res = SupabaseClient.get("order_items?order_id=eq.$orderId&select=*")
      res.mapCatching { jsonStr ->
        val arr = JSONArray(jsonStr)
        val list = mutableListOf<SupabaseOrderItemDto>()
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          list.add(
            SupabaseOrderItemDto(
              id = obj.optString("id", ""),
              orderId = obj.optString("order_id", orderId),
              productId = obj.optString("product_id", ""),
              productTitle = obj.optString("product_title", obj.optString("title", "Product")),
              unitPrice = obj.optInt("unit_price", obj.optInt("price", 0)),
              quantity = obj.optInt("quantity", 1),
              totalPrice = obj.optInt("total_price", 0),
              selectedPalette = if (obj.has("selected_palette")) obj.optString("selected_palette") else null,
              selectedSize = if (obj.has("selected_size")) obj.optString("selected_size") else null,
              imageUrl = if (obj.has("image_url")) obj.optString("image_url") else null
            )
          )
        }
        list
      }
    } catch (e: Exception) {
      Log.e(TAG, "fetchOrderItems error", e)
      Result.failure(e)
    }
  }

  suspend fun createOrder(
    order: SupabaseOrderDto,
    items: List<SupabaseOrderItemDto>
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      // First try executing secure RPC create_order if defined
      val rpcJson = JSONObject().apply {
        put("p_user_id", order.userId)
        put("p_order_number", order.orderNumber)
        put("p_subtotal", order.subtotal)
        put("p_discount", order.discount)
        put("p_delivery_fee", order.deliveryFee)
        put("p_grand_total", order.grandTotal)
        put("p_delivery_note", order.deliveryNote ?: "")
        put("p_address_line", order.addressLine)
        put("p_area", order.addressArea)
        put("p_city", order.addressCity)
      }

      val rpcRes = SupabaseClient.rpc("create_order", rpcJson.toString())
      if (rpcRes.isSuccess) {
        return@withContext rpcRes
      }

      // Fallback: Direct table insert into orders and order_items
      val orderJson = JSONObject().apply {
        put("order_number", order.orderNumber)
        put("user_id", order.userId)
        put("subtotal", order.subtotal)
        put("discount", order.discount)
        put("delivery_fee", order.deliveryFee)
        put("grand_total", order.grandTotal)
        put("payment_method", order.paymentMethod)
        put("payment_status", order.paymentStatus)
        put("order_status", order.orderStatus)
        if (!order.deliveryNote.isNullOrBlank()) put("delivery_note", order.deliveryNote)
        put("address_name", order.addressName)
        put("address_phone", order.addressPhone)
        put("address_line", order.addressLine)
        put("address_area", order.addressArea)
        put("address_city", order.addressCity)
      }

      val insertRes = SupabaseClient.post("orders", orderJson.toString())
      val insertedBody = insertRes.getOrThrow()
      val insertedArray = JSONArray(insertedBody)
      val createdOrderId = insertedArray.getJSONObject(0).optString("id", order.orderNumber)

      // Insert items
      for (item in items) {
        val itemJson = JSONObject().apply {
          put("order_id", createdOrderId)
          put("product_id", item.productId)
          put("product_title", item.productTitle)
          put("unit_price", item.unitPrice)
          put("quantity", item.quantity)
          put("total_price", item.totalPrice)
          if (!item.selectedPalette.isNullOrBlank()) put("selected_palette", item.selectedPalette)
          if (!item.selectedSize.isNullOrBlank()) put("selected_size", item.selectedSize)
        }
        SupabaseClient.post("order_items", itemJson.toString(), preferReturn = false)
      }

      Result.success(createdOrderId)
    } catch (e: Exception) {
      Log.e(TAG, "createOrder error", e)
      Result.failure(e)
    }
  }

  // ----------------------------------------------------
  // REVIEWS & RATINGS
  // ----------------------------------------------------

  suspend fun fetchProductReviews(productId: String): Result<List<SupabaseReviewDto>> = withContext(Dispatchers.IO) {
    try {
      val res = SupabaseClient.get("product_reviews?product_id=eq.$productId&select=*&order=created_at.desc")
      res.mapCatching { jsonStr ->
        val arr = JSONArray(jsonStr)
        val list = mutableListOf<SupabaseReviewDto>()
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          list.add(
            SupabaseReviewDto(
              id = obj.optString("id", ""),
              productId = obj.optString("product_id", productId),
              userId = obj.optString("user_id", ""),
              userName = obj.optString("user_name", "Verified Buyer"),
              rating = obj.optInt("rating", 5),
              title = obj.optString("title", "Great product"),
              comment = obj.optString("comment", obj.optString("description", "")),
              createdAt = obj.optString("created_at", "")
            )
          )
        }
        list
      }
    } catch (e: Exception) {
      Log.e(TAG, "fetchProductReviews error", e)
      Result.failure(e)
    }
  }

  suspend fun submitProductReview(review: SupabaseReviewDto): Result<String> = withContext(Dispatchers.IO) {
    val json = JSONObject().apply {
      put("product_id", review.productId)
      put("user_id", review.userId)
      put("user_name", review.userName)
      put("rating", review.rating)
      put("title", review.title)
      put("comment", review.comment)
    }
    SupabaseClient.post("product_reviews", json.toString())
  }

  // ----------------------------------------------------
  // CASES: RETURNS & COMPLAINTS
  // ----------------------------------------------------

  suspend fun submitReturnRequest(req: SupabaseReturnRequestDto): Result<String> = withContext(Dispatchers.IO) {
    val json = JSONObject().apply {
      put("order_id", req.orderId)
      put("user_id", req.userId)
      put("reason", req.reason)
      put("description", req.description)
      put("status", "PENDING")
      if (!req.evidenceUrl.isNullOrBlank()) put("evidence_url", req.evidenceUrl)
    }
    SupabaseClient.post("marketplace_cases", json.toString())
  }

  suspend fun submitComplaint(complaint: SupabaseComplaintDto): Result<String> = withContext(Dispatchers.IO) {
    val json = JSONObject().apply {
      if (!complaint.orderId.isNullOrBlank()) put("order_id", complaint.orderId)
      put("user_id", complaint.userId)
      put("subject", complaint.subject)
      put("message", complaint.message)
      put("status", "OPEN")
    }
    SupabaseClient.post("marketplace_cases", json.toString())
  }

  // ----------------------------------------------------
  // NOTIFICATIONS
  // ----------------------------------------------------

  suspend fun fetchNotifications(userId: String): Result<List<SupabaseNotificationDto>> = withContext(Dispatchers.IO) {
    try {
      val res = SupabaseClient.get("notifications?user_id=eq.$userId&select=*&order=created_at.desc")
      res.mapCatching { jsonStr ->
        val arr = JSONArray(jsonStr)
        val list = mutableListOf<SupabaseNotificationDto>()
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          list.add(
            SupabaseNotificationDto(
              id = obj.optString("id", ""),
              userId = obj.optString("user_id", userId),
              title = obj.optString("title", "Notification"),
              message = obj.optString("message", ""),
              type = obj.optString("type", "GENERAL"),
              isRead = obj.optBoolean("is_read", false),
              createdAt = obj.optString("created_at", "")
            )
          )
        }
        list
      }
    } catch (e: Exception) {
      Log.e(TAG, "fetchNotifications error", e)
      Result.failure(e)
    }
  }
}
