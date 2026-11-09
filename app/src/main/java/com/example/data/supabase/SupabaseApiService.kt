package com.example.data.supabase

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Service executing structured PostgREST queries against Supabase tables and RPC endpoints.
 */
object SupabaseApiService {
  private const val TAG = "SupabaseApiService"

  // ----------------------------------------------------
  // PRODUCTS
  // ----------------------------------------------------

  suspend fun fetchProducts(category: String? = null, isFeatured: Boolean? = null, limit: Int = 50): Result<List<SupabaseProductDto>> = withContext(Dispatchers.IO) {
    try {
      // Match the website catalog service: fetch public product rows first, then
      // load images, variants, categories and public inventory independently.
      // Embedded PostgREST joins were preventing the Android catalog from loading
      // when relationship metadata differed from the website's explicit queries.
      val params = mutableListOf(
        "select=id,category_id,seller_id,name,slug,sku,short_description,description,price,discount_percent,food_selling_unit,food_unit_price,food_quantity_increment,food_minimum_quantity,is_daily_item,daily_available_quantity,daily_minimum_quantity,daily_available_on,daily_status,currency,specifications,publication_status,moderation_status,stock_verification_status,is_featured,track_inventory,seo_title,seo_description,created_at",
        "publication_status=eq.published",
        "moderation_status=eq.approved",
        "order=created_at.desc",
        "limit=${limit.coerceIn(1, 100)}"
      )
      if (!category.isNullOrBlank()) params.add("category_id=eq.${URLEncoder.encode(category.trim(), "UTF-8")}")
      if (isFeatured == true) params.add("is_featured=eq.true")

      val productJson = SupabaseClient.get("products?${params.joinToString("&")}").getOrThrow()
      val products = JSONArray(productJson)
      if (products.length() == 0) return@withContext Result.success(emptyList())

      val ids = (0 until products.length()).mapNotNull { index ->
        products.optJSONObject(index)?.optString("id")?.takeIf { it.isNotBlank() }
      }
      val idFilter = ids.joinToString(",")
      // Product media and variants are optional enrichment. A failure in one
      // enrichment endpoint must not hide the published product catalog.
      val imageJson = SupabaseClient.get(
        "product_images?select=product_id,image_url,storage_path,is_primary,sort_order&product_id=in.($idFilter)&order=sort_order.asc"
      ).getOrNull() ?: "[]"
      val variantJson = SupabaseClient.get(
        "product_variants?select=id,product_id,option_label,option_value,is_active,sort_order&product_id=in.($idFilter)&is_active=eq.true&order=sort_order.asc"
      ).getOrNull() ?: "[]"
      val categoryJson = SupabaseClient.get(
        "categories?select=id,name&is_active=eq.true"
      ).getOrNull() ?: "[]"

      val images = JSONArray(imageJson)
      val variants = JSONArray(variantJson)
      val categories = JSONArray(categoryJson)
      val categoriesById = mutableMapOf<String, JSONObject>()
      for (i in 0 until categories.length()) {
        val row = categories.optJSONObject(i) ?: continue
        categoriesById[row.optString("id")] = JSONObject().put("id", row.optString("id")).put("name", row.optString("name"))
      }
      val imagesByProduct = mutableMapOf<String, JSONArray>()
      for (i in 0 until images.length()) {
        val row = images.optJSONObject(i) ?: continue
        val id = row.optString("product_id")
        imagesByProduct.getOrPut(id) { JSONArray() }.put(row)
      }
      val variantsByProduct = mutableMapOf<String, JSONArray>()
      for (i in 0 until variants.length()) {
        val row = variants.optJSONObject(i) ?: continue
        val id = row.optString("product_id")
        variantsByProduct.getOrPut(id) { JSONArray() }.put(row)
      }

      for (i in 0 until products.length()) {
        val row = products.optJSONObject(i) ?: continue
        val id = row.optString("id")
        row.put("images", imagesByProduct[id] ?: JSONArray())
        row.put("variants", variantsByProduct[id] ?: JSONArray())
        row.put("category", categoriesById[row.optString("category_id")] ?: JSONObject())
      }

      val request = JSONObject().put("product_ids", JSONArray(ids)).toString()
      val inventory = SupabaseClient.rpc("get_public_inventory", request).getOrNull()?.let(::JSONArray)
      val availability = SupabaseClient.rpc("get_public_availability", request).getOrNull()?.let(::JSONArray)
      val quantities = mutableMapOf<String, Int>()
      if (inventory != null) for (i in 0 until inventory.length()) {
        val row = inventory.getJSONObject(i)
        val id = row.optString("product_id")
        quantities[id] = (quantities[id] ?: 0) + row.optInt("available_quantity", 0)
      }
      val available = mutableMapOf<String, Boolean>()
      if (availability != null) for (i in 0 until availability.length()) {
        val row = availability.getJSONObject(i)
        val id = row.optString("product_id")
        available[id] = (available[id] ?: false) || row.optBoolean("is_available", false)
      }
      Result.success(parseProductsJson(products.toString(), quantities, available, availability != null, availability))
    } catch (e: Exception) {
      Log.e(TAG, "fetchProducts error", e)
      Result.failure(e)
    }
  }

  suspend fun searchProducts(query:String):Result<List<SupabaseProductDto>> = withContext(Dispatchers.IO){
    try{val q=URLEncoder.encode(query.trim(),"UTF-8");val url="products?select=*,category:categories(id,name),images:product_images(image_url,storage_path,is_primary,sort_order),variants:product_variants(id,option_label,option_value,is_active,sort_order),seller:seller_profiles(shop_name)&publication_status=eq.published&moderation_status=eq.approved&vendor_hidden=eq.false&or=(name.ilike.*$q*,description.ilike.*$q*,short_description.ilike.*$q*)&limit=30";SupabaseClient.get(url).mapCatching{parseProductsJson(it)}}catch(e:Exception){Result.failure(e)}
  }

  suspend fun fetchProductById(id:String):Result<SupabaseProductDto> = fetchProducts(limit=100).mapCatching{it.firstOrNull{p->p.id==id}?:throw IOException("Product not found")}

  private fun parseProductsJson(jsonStr: String, quantities: Map<String, Int> = emptyMap(), availability: Map<String, Boolean> = emptyMap(), availabilityKnown: Boolean = false, availabilityRows: JSONArray? = null): List<SupabaseProductDto> {
    val rows = JSONArray(jsonStr)
    return (0 until rows.length()).mapNotNull { index ->
      val obj = rows.optJSONObject(index) ?: return@mapNotNull null
      val category = obj.optJSONObject("category") ?: obj.optJSONArray("category")?.optJSONObject(0)
      val seller = obj.optJSONObject("seller") ?: obj.optJSONArray("seller")?.optJSONObject(0)
      val basePrice = if (!obj.isNull("food_unit_price")) obj.optDouble("food_unit_price", obj.optDouble("price", 0.0)) else obj.optDouble("price", 0.0)
      val discount = obj.optDouble("discount_percent", 0.0).toInt().coerceIn(0, 100)
      val price = (basePrice * (100 - discount) / 100.0).toInt()
      val imageRows = obj.optJSONArray("images")
      val primaryImage = if (imageRows == null) null else (0 until imageRows.length()).mapNotNull { imageRows.optJSONObject(it) }.sortedWith(compareByDescending<JSONObject> { it.optBoolean("is_primary") }.thenBy { it.optInt("sort_order") }).firstOrNull()
      val directImage = primaryImage?.optString("image_url", "") ?: ""
      val storagePath = primaryImage?.optString("storage_path", "") ?: ""
      val imageUrl = directImage.takeIf { it.startsWith("https://") } ?: storagePath.takeIf { it.isNotBlank() }?.let { "${SupabaseConfig.storageUrl}/object/public/product-images/${it.replace(" ", "%20")}" }
      val palettes = mutableListOf<String>(); val sizes = mutableListOf<String>(); val variantIds = mutableMapOf<String, String>(); val variants = obj.optJSONArray("variants")
      if (variants != null) for (i in 0 until variants.length()) {
        val variant = variants.optJSONObject(i) ?: continue
        if (!variant.optBoolean("is_active", true)) continue
        val label = variant.optString("option_label"); val value = variant.optString("option_value")
        if (label.contains("color", true) || label.contains("colour", true)) { palettes.add(value); variantIds["color:" + value] = variant.optString("id") }
        if (label.contains("size", true)) { sizes.add(value); variantIds["size:" + value] = variant.optString("id") }
      }
      val highlights = mutableListOf<String>(); val specs = obj.optJSONArray("specifications")
      if (specs != null) for (i in 0 until specs.length()) {
        val spec = specs.optJSONObject(i) ?: continue
        if (spec.optString("label").isNotBlank() && spec.optString("value").isNotBlank()) highlights.add(spec.optString("label") + ": " + spec.optString("value"))
      }
      val id = obj.optString("id"); val known = availabilityKnown && availability.containsKey(id); val stock = known && availability[id] == true
      val variantAvailability = mutableMapOf<String, Boolean>()
      if (availabilityRows != null) for (availabilityIndex in 0 until availabilityRows.length()) {
        val availabilityRow = availabilityRows.optJSONObject(availabilityIndex) ?: continue
        if (availabilityRow.optString("product_id") == id && !availabilityRow.isNull("variant_id")) variantAvailability[availabilityRow.optString("variant_id")] = availabilityRow.optBoolean("is_available", false)
      }
      val foodUnit = obj.optString("food_selling_unit").takeIf { it.isNotBlank() && it != "null" }
      val dailyStatus = obj.optString("daily_status").takeIf { it.isNotBlank() && it != "null" }
      SupabaseProductDto(id=id,title=obj.optString("name",""),vendor=seller?.optString("shop_name")?.takeIf{it.isNotBlank()}?:"JTBazar",category=category?.optString("name")?:"",categoryId=category?.optString("id")?:"",price=price,originalPrice=basePrice.toInt(),discountPercent=discount,rating=0.0,reviewCount=0,imageUrl=imageUrl,badge=if(discount>0)"$discount% OFF" else null,inStock=stock,stockQuantity=quantities[id]?:0,inStockText=if(!known)"Availability not verified" else if(stock)"In Stock" else "Out of Stock",vendorVerified=false,vendorRating="",vendorSales="",vendorAvatarText="",deliveryText="Delivered by JTBazar",paymentInfo="Cash on Delivery",craftStory=obj.optString("description",""),highlights=highlights,palettes=palettes.distinct(),sizes=sizes.distinct(),variantIds=variantIds,isArtisanCollection=false,isFeatured=obj.optBoolean("is_featured",false),isBestSeller=false,isNewArrival=true,availabilityKnown=known,dailyStatus=dailyStatus,foodSellingUnit=foodUnit,variantAvailability=variantAvailability)
    }
  }

  suspend fun fetchStoreSettings(): Result<SupabaseStoreSettingsDto> = withContext(Dispatchers.IO) {
    try {
      SupabaseClient.get("store_settings?id=eq.default&select=delivery_fee,cod_enabled,return_window_days&limit=1").mapCatching { json ->
        val rows = JSONArray(json)
        if (rows.length() == 0) throw IOException("Store settings are not configured.")
        val row = rows.getJSONObject(0)
        SupabaseStoreSettingsDto(
          deliveryFee = row.optDouble("delivery_fee", 0.0).toInt(),
          codEnabled = row.optBoolean("cod_enabled", false),
          returnWindowDays = row.optInt("return_window_days", 7)
        )
      }
    } catch (e: Exception) { Result.failure(e) }
  }

  // LIVE HOMEPAGE CONTENT (same published rows as the website)
  suspend fun fetchHomepageHeroes(): Result<List<SupabaseHomepageHeroDto>> = withContext(Dispatchers.IO) {
    try {
      SupabaseClient.get("homepage_heroes?select=id,title,subtitle,cta_text,cta_destination,image_url,mobile_image_url,image_alt_text,display_order&status=eq.published&order=display_order.asc")
        .mapCatching { json ->
          val rows = JSONArray(json)
          (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val image = row.optString("image_url").takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            SupabaseHomepageHeroDto(
              id = row.optString("id"),
              title = row.optString("title"),
              subtitle = row.optString("subtitle").takeIf { it.isNotBlank() },
              ctaText = row.optString("cta_text").takeIf { it.isNotBlank() },
              ctaDestination = row.optString("cta_destination").takeIf { it.isNotBlank() },
              imageUrl = image,
              mobileImageUrl = row.optString("mobile_image_url").takeIf { it.startsWith("https://") },
              imageAltText = row.optString("image_alt_text").takeIf { it.isNotBlank() },
              displayOrder = row.optInt("display_order", index)
            )
          }
        }
    } catch (e: Exception) {
      Log.e(TAG, "fetchHomepageHeroes error", e)
      Result.failure(e)
    }
  }

  suspend fun fetchActivePromotions(position: String? = null): Result<List<SupabasePromotionDto>> = withContext(Dispatchers.IO) {
    try {
      val positionFilter = position?.takeIf { it.isNotBlank() }?.let { "&display_position=eq.${URLEncoder.encode(it, "UTF-8")}" } ?: ""
      SupabaseClient.get("promotions?select=id,title,description,banner_image_url,banner_image_urls,cta_text,cta_destination,display_position,priority&status=eq.approved&order=priority.desc$positionFilter")
        .mapCatching { json ->
          val rows = JSONArray(json)
          (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val images = row.optJSONArray("banner_image_urls")
            val urls = if (images == null) emptyList() else (0 until images.length()).mapNotNull { images.optString(it).takeIf { url -> url.startsWith("https://") } }
            SupabasePromotionDto(
              id = row.optString("id"),
              title = row.optString("title"),
              description = row.optString("description").takeIf { it.isNotBlank() },
              bannerImageUrl = row.optString("banner_image_url").takeIf { it.startsWith("https://") },
              bannerImageUrls = urls,
              ctaText = row.optString("cta_text").takeIf { it.isNotBlank() },
              ctaDestination = row.optString("cta_destination").takeIf { it.isNotBlank() },
              displayPosition = row.optString("display_position"),
              priority = row.optInt("priority")
            )
          }
        }
    } catch (e: Exception) {
      Log.e(TAG, "fetchActivePromotions error", e)
      Result.failure(e)
    }
  }

  // CATEGORIES
  // ----------------------------------------------------

  suspend fun fetchCategories():Result<List<SupabaseCategoryDto>> = withContext(Dispatchers.IO){
    try{SupabaseClient.get("categories?select=id,name,description,image_url,sort_order,is_coming_soon&is_active=eq.true&order=sort_order.asc").mapCatching{json->val a=JSONArray(json);(0 until a.length()).mapNotNull{i->val o=a.optJSONObject(i)?:return@mapNotNull null;SupabaseCategoryDto(id=o.optString("id"),title=o.optString("name"),countText="",sampleItems=o.optString("description",""),imageUrl=if(o.isNull("image_url"))null else o.optString("image_url"),badge=if(o.optBoolean("is_coming_soon"))"Coming soon" else null,displayOrder=o.optInt("sort_order",i))}}}catch(e:Exception){Log.e(TAG,"fetchCategories error",e);Result.failure(e)}
  }

  // CART
  // ----------------------------------------------------

  suspend fun fetchCart(userId: String): Result<String> = withContext(Dispatchers.IO) {
    SupabaseClient.get("cart_items?user_id=eq.$userId&select=*,product:products(*)")
  }

  suspend fun addToCart(userId: String, productId: String, quantity: Int, variantId: String?): Result<String> = withContext(Dispatchers.IO) {
    try {
      var query = "cart_items?user_id=eq.$userId&product_id=eq.$productId&select=id,quantity"
      query += if (variantId.isNullOrBlank()) "&variant_id=is.null" else "&variant_id=eq.$variantId"
      val existingBody = SupabaseClient.get(query).getOrThrow()
      val rows = JSONArray(existingBody)
      if (rows.length() > 0) {
        val row = rows.getJSONObject(0)
        val id = row.optString("id")
        val update = JSONObject().put("quantity", row.optInt("quantity", 0) + quantity)
        SupabaseClient.patch("cart_items?id=eq.$id&user_id=eq.$userId", update.toString()).getOrThrow()
        Result.success(id)
      } else {
        val body = JSONObject().apply {
          put("user_id", userId); put("product_id", productId)
          if (!variantId.isNullOrBlank()) put("variant_id", variantId)
          put("quantity", quantity)
        }
        val inserted = JSONArray(SupabaseClient.post("cart_items", body.toString()).getOrThrow())
        if (inserted.length() == 0) throw IOException("Cart item was not saved.")
        Result.success(inserted.getJSONObject(0).optString("id"))
      }
    } catch (e: Exception) { Log.e(TAG, "addToCart error", e); Result.failure(e) }
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
    try {
      val check = SupabaseClient.get("wishlist_items?user_id=eq.$userId&product_id=eq.$productId&select=id").getOrNull()
      if (check != null && JSONArray(check).length() > 0) {
        return@withContext Result.success("already_in_wishlist")
      }
      val json = JSONObject().apply {
        put("user_id", userId)
        put("product_id", productId)
      }
      SupabaseClient.post("wishlist_items", json.toString())
    } catch (e: Exception) {
      Log.e(TAG, "addToWishlist error", e)
      Result.failure(e)
    }
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
              name = obj.optString("recipient_name", ""),
              phone = obj.optString("phone", ""),
              addressLine = obj.optString("address_line_1", ""),
              area = obj.optString("address_line_2", ""),
              city = obj.optString("city", ""),
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

  private fun normalizePakistaniMobile(value: String): String {
    val phone = value.trim()
    if (!Regex("^(03[0-9]{9}|\\+923[0-9]{9})$").matches(phone)) throw IOException("Enter exactly 03001234567 or +923001234567.")
    return if (phone.startsWith("03")) "+92" + phone.substring(1) else phone
  }

  suspend fun saveAddress(address: SupabaseAddressDto): Result<String> = withContext(Dispatchers.IO) {
    val json = JSONObject().apply {
      if (address.id.isNotBlank() && !address.id.startsWith("addr_")) put("id", address.id)
      put("user_id", address.userId)
      put("recipient_name", address.name)
      put("phone", normalizePakistaniMobile(address.phone))
      put("address_line_1", address.addressLine)
      if (address.area.isNotBlank()) put("address_line_2", address.area)
      put("city", address.city)
      put("province", address.province)
      if (!address.postalCode.isNullOrBlank()) put("postal_code", address.postalCode)
      put("is_default", address.isDefault)
    }
    if (address.id.isNotBlank() && !address.id.startsWith("addr_")) {
      SupabaseClient.patch("customer_addresses?id=eq.${address.id}&user_id=eq.${address.userId}", json.toString())
    } else SupabaseClient.post("customer_addresses", json.toString())
  }

  suspend fun deleteAddress(addressId: String, userId: String): Result<Boolean> = withContext(Dispatchers.IO) {
    SupabaseClient.delete("customer_addresses?id=eq.$addressId&user_id=eq.$userId")
  }

  // ----------------------------------------------------
  // ORDERS
  // ----------------------------------------------------

  suspend fun fetchOrders(userId:String):Result<List<SupabaseOrderDto>> = withContext(Dispatchers.IO){
    try{SupabaseClient.get("orders?customer_id=eq.$userId&select=*&order=created_at.desc").mapCatching{json->val a=JSONArray(json);(0 until a.length()).mapNotNull{i->val o=a.optJSONObject(i)?:return@mapNotNull null;val ad=o.optJSONObject("shipping_address_snapshot")?:JSONObject();SupabaseOrderDto(id=o.optString("id"),orderNumber=o.optString("order_number"),userId=o.optString("customer_id",userId),subtotal=o.optDouble("subtotal").toInt(),discount=o.optDouble("discount_amount").toInt(),deliveryFee=o.optDouble("delivery_fee").toInt(),grandTotal=o.optDouble("total").toInt(),paymentMethod=o.optString("payment_method","cod").uppercase(),paymentStatus=o.optString("payment_status","pending").uppercase(),orderStatus=o.optString("order_status","pending").uppercase(),deliveryNote=if(o.isNull("customer_note"))null else o.optString("customer_note"),addressName=ad.optString("recipient_name",o.optString("customer_name","")),addressPhone=ad.optString("phone",o.optString("customer_phone","")),addressLine=ad.optString("address_line_1",""),addressArea=ad.optString("address_line_2",""),addressCity=ad.optString("city",""),createdAt=o.optString("created_at",""))}}}catch(e:Exception){Log.e(TAG,"fetchOrders error",e);Result.failure(e)}
  }
  suspend fun fetchOrderItems(orderId:String):Result<List<SupabaseOrderItemDto>> = withContext(Dispatchers.IO){
    try{SupabaseClient.get("order_items?order_id=eq.$orderId&select=*&order=created_at.asc").mapCatching{json->val a=JSONArray(json);(0 until a.length()).mapNotNull{i->val o=a.optJSONObject(i)?:return@mapNotNull null;val v=o.optJSONObject("variant_snapshot")?:JSONObject();SupabaseOrderItemDto(id=o.optString("id"),orderId=o.optString("order_id"),productId=o.optString("product_id",""),productTitle=o.optString("product_name_snapshot",""),unitPrice=o.optDouble("unit_price").toInt(),quantity=o.optInt("quantity",1),totalPrice=o.optDouble("line_total").toInt(),selectedPalette=v.optString("value").takeIf{v.optString("label").contains("color",true)},selectedSize=v.optString("value").takeIf{v.optString("label").contains("size",true)},variantId=o.optString("variant_id").takeIf{it.isNotBlank()})}}}catch(e:Exception){Log.e(TAG,"fetchOrderItems error",e);Result.failure(e)}
  }
  suspend fun createOrder(
    order: SupabaseOrderDto,
    items: List<SupabaseOrderItemDto>,
    deliveryLat: Double? = null,
    deliveryLng: Double? = null,
    deliveryLocationName: String? = null,
    deliveryLocationCapturedAt: String? = null
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      if ((deliveryLat == null) != (deliveryLng == null)) {
        throw IOException("Both delivery latitude and longitude are required.")
      }
      val address = JSONObject().apply {
        put("recipient_name", order.addressName)
        put("phone", normalizePakistaniMobile(order.addressPhone))
        put("address_line_1", order.addressLine)
        if (order.addressArea.isNotBlank()) put("address_line_2", order.addressArea)
        put("city", order.addressCity)
        if (order.addressProvince.isNotBlank()) put("province", order.addressProvince)
        if (!order.postalCode.isNullOrBlank()) put("postal_code", order.postalCode)
        if (deliveryLat != null) put("latitude", deliveryLat)
        if (deliveryLng != null) put("longitude", deliveryLng)
      }
      val lines = JSONArray()
      items.forEach { item ->
        lines.put(JSONObject().apply {
          put("product_id", item.productId)
          if (!item.variantId.isNullOrBlank()) put("variant_id", item.variantId)
          put("quantity", item.quantity)
        })
      }
      val params = JSONObject().apply {
        put("p_customer_name", order.addressName)
        put("p_customer_phone", normalizePakistaniMobile(order.addressPhone))
        put("p_customer_email", order.customerEmail?.trim()?.takeIf { it.isNotBlank() }?.let { if (android.util.Patterns.EMAIL_ADDRESS.matcher(it).matches() && it.length <= 254) it else throw IOException("Invalid email address format.") } ?: JSONObject.NULL)
        put("p_shipping_address", address)
        put("p_items", lines)
        put("p_idempotency_key", order.idempotencyKey)
        put("p_customer_note", order.deliveryNote ?: "")
        put("p_delivery_lat", deliveryLat ?: JSONObject.NULL)
        put("p_delivery_lng", deliveryLng ?: JSONObject.NULL)
        put("p_delivery_location_name", deliveryLocationName ?: JSONObject.NULL)
        put("p_delivery_location_captured_at", deliveryLocationCapturedAt ?: JSONObject.NULL)
      }
      val body = SupabaseClient.rpc("place_order", params.toString()).getOrThrow()
      val rows = JSONArray(body)
      if (rows.length() == 0) throw IOException("Order was not created.")
      val id = rows.getJSONObject(0).optString("order_id")
      if (id.isBlank()) throw IOException("Server did not return an order ID.")
      Result.success(id)
    } catch (e: Exception) {
      Log.e(TAG, "Secure place_order RPC failed", e)
      Result.failure(e)
    }
  }

  suspend fun cancelOrder(orderId:String):Result<Boolean> = withContext(Dispatchers.IO){
    try{val body=SupabaseClient.rpc("cancel_customer_order",JSONObject().put("p_order_id",orderId).toString()).getOrThrow();Result.success(body.trim()=="true"||body.trim()=="\"true\"")}catch(e:Exception){Log.e(TAG,"cancel_customer_order RPC failed",e);Result.failure(e)}
  }

  // REVIEWS & RATINGS
  // ----------------------------------------------------

  suspend fun fetchProductReviews(productId: String): Result<List<SupabaseReviewDto>> = withContext(Dispatchers.IO) {
    try {
      val res = SupabaseClient.get("product_reviews?product_id=eq.$productId&status=eq.approved&select=*&order=created_at.desc")
      res.mapCatching { jsonStr ->
        val arr = JSONArray(jsonStr)
        val list = mutableListOf<SupabaseReviewDto>()
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          list.add(
            SupabaseReviewDto(
              id = obj.optString("id", ""),
              productId = obj.optString("product_id", productId),
              orderId = obj.optString("order_id", ""),
              userId = obj.optString("buyer_id", ""),
              userName = "",
              rating = obj.optInt("rating", 0),
              title = obj.optString("title", ""),
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
    try {
      val orderId = review.orderId?.takeIf { it.isNotBlank() } ?: throw IOException("A delivered order is required to submit a review.")
      val body = JSONObject().apply {
        put("product_id", review.productId)
        put("order_id", orderId)
        put("buyer_id", review.userId)
        put("rating", review.rating.coerceIn(1, 5))
        if (review.title.isNotBlank()) put("title", review.title)
        if (review.comment.isNotBlank()) put("comment", review.comment)
      }
      SupabaseClient.post("product_reviews", body.toString())
    } catch (e: Exception) { Result.failure(e) }
  }

  // CASES: RETURNS & COMPLAINTS
  // ----------------------------------------------------

  suspend fun submitReturnRequest(req: SupabaseReturnRequestDto): Result<String> = withContext(Dispatchers.IO) {
    Result.failure(IllegalStateException("Returns must reference a delivered order item and include required evidence."))
  }

  suspend fun submitComplaint(complaint: SupabaseComplaintDto): Result<String> = withContext(Dispatchers.IO) {
    try {
      val user = SupabaseClient.currentUser.value ?: throw IOException("Please sign in to contact support.")
      val email = user.email?.takeIf { it.isNotBlank() } ?: throw IOException("A verified account email is required.")
      val body = JSONObject().apply {
        put("user_id", user.id)
        put("requester_name", user.fullName ?: "Customer")
        put("requester_email", email)
        put("subject", complaint.subject)
        put("message", complaint.message)
      }
      SupabaseClient.post("customer_support_tickets", body.toString())
    } catch (e: Exception) { Result.failure(e) }
  }

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
              message = obj.optString("body", ""),
              type = obj.optString("kind", "store"),
              isRead = !obj.isNull("read_at"),
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

  suspend fun markNotificationRead(notificationId: String): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
      val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
      }.format(java.util.Date())
      val body = JSONObject().put("read_at", nowIso)
      SupabaseClient.patch("notifications?id=eq.$notificationId", body.toString())
      Result.success(true)
    } catch (e: Exception) {
      Log.e(TAG, "markNotificationRead error", e)
      Result.failure(e)
    }
  }

  suspend fun markAllNotificationsRead(userId: String): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
      val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
      }.format(java.util.Date())
      val body = JSONObject().put("read_at", nowIso)
      SupabaseClient.patch("notifications?user_id=eq.$userId&read_at=is.null", body.toString())
      Result.success(true)
    } catch (e: Exception) {
      Log.e(TAG, "markAllNotificationsRead error", e)
      Result.failure(e)
    }
  }

  // SHOPS & NEARBY VENDORS
  // ----------------------------------------------------

  suspend fun fetchShops(): Result<List<com.example.data.model.ShopRecord>> = withContext(Dispatchers.IO) {
    try {
      val res = SupabaseClient.get("seller_profiles?select=id,shop_name,description,shop_image_url,phone,shop_lat,shop_lng,shop_location_label,current_address,complete_address,status,shop_is_open&status=eq.approved&limit=100")
      res.fold(
        onSuccess = { jsonStr ->
          val arr = JSONArray(jsonStr)
          val list = mutableListOf<com.example.data.model.ShopRecord>()
          for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("shop_name", "").takeIf { it.isNotBlank() } ?: continue
            list.add(
              com.example.data.model.ShopRecord(
                id = obj.optString("id", "shop_$i"),
                name = name,
                description = obj.optString("description", "Verified Vendor"),
                logoUrl = obj.optString("shop_image_url").takeIf { it.startsWith("http") },
                bannerUrl = null,
                city = obj.optString("shop_location_label", obj.optString("current_address", "")),
                address = obj.optString("complete_address", obj.optString("current_address", "")),
                latitude = if (!obj.isNull("shop_lat")) obj.optDouble("shop_lat", 0.0) else 0.0,
                longitude = if (!obj.isNull("shop_lng")) obj.optDouble("shop_lng", 0.0) else 0.0,
                isVerified = obj.optString("status") == "approved",
                phone = obj.optString("phone", ""),
                deliveryRadiusKm = 5.0
              )
            )
          }
          Result.success(list)
        },
        onFailure = {
          Log.w(TAG, "seller_profiles access: ${it.message}")
          Result.success(emptyList())
        }
      )
    } catch (e: Exception) {
      Log.e(TAG, "fetchShops error", e)
      Result.success(emptyList())
    }
  }
}
