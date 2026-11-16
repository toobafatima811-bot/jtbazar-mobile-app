package com.example.data.supabase

import android.util.Log
import com.example.data.model.ShopRecord
import com.example.data.repository.ProductSortOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
  private const val METADATA_CACHE_TTL_MS = 60_000L
  private val categoryCacheMutex = Mutex()
  @Volatile private var cachedCategories: List<SupabaseCategoryDto>? = null
  @Volatile private var cachedCategoriesAtMs: Long = 0L

  private val shopCacheMutex = Mutex()
  @Volatile private var cachedShops: List<ShopRecord>? = null
  @Volatile private var cachedShopsAtMs: Long = 0L

  fun clearCaches() {
    cachedCategories = null
    cachedCategoriesAtMs = 0L
    cachedShops = null
    cachedShopsAtMs = 0L
  }

  // ----------------------------------------------------
  // PRODUCTS
  // ----------------------------------------------------

  suspend fun fetchProductVideos(limit: Int = 100): Result<List<SupabaseProductVideoDto>> = withContext(Dispatchers.IO) {
    try {
      SupabaseClient.get("product_videos?select=id,product_id,video_url,duration_seconds,created_at&is_active=eq.true&order=created_at.desc&limit=${limit.coerceIn(1, 100)}")
        .mapCatching { json ->
          val rows = JSONArray(json)
          (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val url = row.optString("video_url").takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            SupabaseProductVideoDto(row.optString("id"), row.optString("product_id"), url, row.optInt("duration_seconds"), row.optString("created_at"))
          }
        }
    } catch (e: Exception) { Result.failure(e) }
  }

  private const val PRODUCT_SELECT_COLUMNS =
    "id,category_id,seller_id,name,slug,sku,short_description,description,price,discount_percent,food_selling_unit,food_unit_price,food_quantity_increment,food_minimum_quantity,is_daily_item,daily_available_quantity,daily_minimum_quantity,daily_available_on,daily_status,currency,specifications,publication_status,moderation_status,stock_verification_status,is_featured,track_inventory,seo_title,seo_description,created_at"

  suspend fun fetchProductsPage(
    page: Int = 0,
    pageSize: Int = 12,
    categoryIds: List<String> = emptyList(),
    isFeatured: Boolean? = null,
    searchQuery: String? = null,
    sortBy: ProductSortOption = ProductSortOption.NEWEST,
    maxPrice: Int? = null
  ): Result<PaginatedProductsDto> = withContext(Dispatchers.IO) {
    try {
      val safePage = page.coerceAtLeast(0)
      val safeSize = pageSize.coerceIn(1, 50)
      val fetchLimit = safeSize + 1
      val offset = safePage * safeSize

      val params = mutableListOf(
        "select=$PRODUCT_SELECT_COLUMNS",
        "publication_status=eq.published",
        "moderation_status=eq.approved",
        "vendor_hidden=eq.false",
        "stock_verification_status=neq.paused",
        "order=${sortBy.orderParam}",
        "limit=$fetchLimit",
        "offset=$offset"
      )

      val cleanCatIds = categoryIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
      if (cleanCatIds.size == 1) {
        params.add("category_id=eq.${URLEncoder.encode(cleanCatIds.first(), "UTF-8")}")
      } else if (cleanCatIds.size > 1) {
        val joined = cleanCatIds.joinToString(",") { URLEncoder.encode(it, "UTF-8") }
        params.add("category_id=in.($joined)")
      }
      if (isFeatured == true) {
        params.add("is_featured=eq.true")
      }
      if (maxPrice != null && maxPrice >= 0) {
        params.add("or=(food_unit_price.lte.$maxPrice,and(food_unit_price.is.null,price.lte.$maxPrice))")
      }

      val sanitizedSearch = searchQuery
        ?.trim()
        ?.replace("*", "")
        ?.replace(",", " ")
        ?.replace("(", " ")
        ?.replace(")", " ")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        .orEmpty()
      if (sanitizedSearch.isNotBlank()) {
        val encoded = URLEncoder.encode(sanitizedSearch, "UTF-8").replace("+", "%20")
        params.add("or=(name.ilike.*$encoded*,short_description.ilike.*$encoded*,description.ilike.*$encoded*)")
      }

      val productJson = SupabaseClient.get("products?${params.joinToString("&")}").getOrThrow()
      val rawProducts = JSONArray(productJson)
      val hasMore = rawProducts.length() > safeSize
      val slicedCount = minOf(rawProducts.length(), safeSize)
      if (slicedCount == 0) {
        return@withContext Result.success(PaginatedProductsDto(items = emptyList(), page = safePage, hasMore = false))
      }

      val products = JSONArray()
      for (i in 0 until slicedCount) {
        products.put(rawProducts.getJSONObject(i))
      }

      val enriched = enrichProductRows(products)
      Result.success(PaginatedProductsDto(items = enriched, page = safePage, hasMore = hasMore))
    } catch (e: Exception) {
      runCatching { Log.e(TAG, "fetchProductsPage error", e) }
      Result.failure(e)
    }
  }

  suspend fun fetchProducts(category: String? = null, isFeatured: Boolean? = null, limit: Int = 50): Result<List<SupabaseProductDto>> {
    return fetchProductsPage(
      page = 0,
      pageSize = limit.coerceIn(1, 50),
      categoryIds = if (category.isNullOrBlank()) emptyList() else listOf(category),
      isFeatured = isFeatured
    ).map { it.items }
  }

  suspend fun searchProducts(query: String): Result<List<SupabaseProductDto>> {
    return fetchProductsPage(
      page = 0,
      pageSize = 12,
      searchQuery = query
    ).map { it.items }
  }

  suspend fun fetchProductById(id: String): Result<SupabaseProductDto> = withContext(Dispatchers.IO) {
    try {
      val cleanId = id.trim()
      if (cleanId.isBlank()) throw IOException("Product not found")
      val encodedId = URLEncoder.encode(cleanId, "UTF-8")
      val url = "products?select=$PRODUCT_SELECT_COLUMNS&id=eq.$encodedId&publication_status=eq.published&moderation_status=eq.approved&vendor_hidden=eq.false&limit=1"
      val body = SupabaseClient.get(url).getOrThrow()
      val rows = JSONArray(body)
      if (rows.length() == 0) throw IOException("Product not found")
      val enriched = enrichProductRows(rows)
      Result.success(enriched.firstOrNull() ?: throw IOException("Product not found"))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun fetchProductsByIds(ids: List<String>): Result<List<SupabaseProductDto>> = withContext(Dispatchers.IO) {
    try {
      val cleanIds = ids.map { it.trim() }.filter { it.isNotBlank() }.distinct()
      if (cleanIds.isEmpty()) return@withContext Result.success(emptyList())

      val aggregated = mutableListOf<SupabaseProductDto>()
      cleanIds.chunked(40).forEach { chunk ->
        val encoded = chunk.joinToString(",") { URLEncoder.encode(it, "UTF-8") }
        val url = "products?select=$PRODUCT_SELECT_COLUMNS&id=in.($encoded)&publication_status=eq.published&moderation_status=eq.approved&vendor_hidden=eq.false&limit=${chunk.size}"
        val body = SupabaseClient.get(url).getOrThrow()
        val rows = JSONArray(body)
        if (rows.length() > 0) {
          aggregated.addAll(enrichProductRows(rows))
        }
      }
      Result.success(aggregated)
    } catch (e: Exception) {
      runCatching { Log.e(TAG, "fetchProductsByIds error", e) }
      Result.failure(e)
    }
  }

  private suspend fun enrichProductRows(products: JSONArray): List<SupabaseProductDto> = coroutineScope {
    if (products.length() == 0) return@coroutineScope emptyList()

    val ids = (0 until products.length()).mapNotNull { index ->
      products.optJSONObject(index)?.optString("id")?.takeIf { it.isNotBlank() }
    }
    if (ids.isEmpty()) return@coroutineScope emptyList()
    val idFilter = ids.joinToString(",")
    val rpcRequest = JSONObject().put("product_ids", JSONArray(ids)).toString()

    // Execute all enrichment lookups concurrently to minimize page load latency.
    val shopsDeferred = async { fetchShops(forceRefresh = false).getOrNull().orEmpty() }
    val categoriesDeferred = async { fetchCategories(forceRefresh = false).getOrNull().orEmpty() }
    val imagesDeferred = async {
      SupabaseClient.get(
        "product_images?select=product_id,image_url,storage_path,is_primary,sort_order&product_id=in.($idFilter)&order=sort_order.asc"
      ).getOrNull() ?: "[]"
    }
    val variantsDeferred = async {
      SupabaseClient.get(
        "product_variants?select=id,product_id,option_label,option_value,is_active,sort_order&product_id=in.($idFilter)&is_active=eq.true&order=sort_order.asc"
      ).getOrNull() ?: "[]"
    }
    val inventoryDeferred = async {
      SupabaseClient.rpc("get_public_inventory", rpcRequest).getOrNull()?.let(::JSONArray)
    }
    val availabilityDeferred = async {
      SupabaseClient.rpc("get_public_availability", rpcRequest).getOrNull()?.let(::JSONArray)
    }

    val shopsList = shopsDeferred.await()
    val categoriesList = categoriesDeferred.await()
    val imageJson = imagesDeferred.await()
    val variantJson = variantsDeferred.await()
    val inventory = inventoryDeferred.await()
    val availability = availabilityDeferred.await()

    val shopsById = shopsList.associateBy { it.id }
    val categoriesById = categoriesList.associate {
      it.id to JSONObject().put("id", it.id).put("name", it.title)
    }

    for (productIndex in 0 until products.length()) {
      val product = products.optJSONObject(productIndex) ?: continue
      val sellerId = product.optString("seller_id")
      val shop = shopsById[sellerId]
      if (shop != null) {
        product.put("seller", JSONObject().put("shop_name", shop.name))
      }
    }

    val images = JSONArray(imageJson)
    val variants = JSONArray(variantJson)
    val imagesByProduct = mutableMapOf<String, JSONArray>()
    for (i in 0 until images.length()) {
      val row = images.optJSONObject(i) ?: continue
      val pid = row.optString("product_id")
      imagesByProduct.getOrPut(pid) { JSONArray() }.put(row)
    }
    val variantsByProduct = mutableMapOf<String, JSONArray>()
    for (i in 0 until variants.length()) {
      val row = variants.optJSONObject(i) ?: continue
      val pid = row.optString("product_id")
      variantsByProduct.getOrPut(pid) { JSONArray() }.put(row)
    }

    for (i in 0 until products.length()) {
      val row = products.optJSONObject(i) ?: continue
      val pid = row.optString("id")
      row.put("images", imagesByProduct[pid] ?: JSONArray())
      row.put("variants", variantsByProduct[pid] ?: JSONArray())
      row.put("category", categoriesById[row.optString("category_id")] ?: JSONObject())
    }
    val quantities = mutableMapOf<String, Int>()
    if (inventory != null) for (i in 0 until inventory.length()) {
      val row = inventory.getJSONObject(i)
      val pid = row.optString("product_id")
      quantities[pid] = (quantities[pid] ?: 0) + row.optInt("available_quantity", 0)
    }
    val available = mutableMapOf<String, Boolean>()
    if (availability != null) for (i in 0 until availability.length()) {
      val row = availability.getJSONObject(i)
      val pid = row.optString("product_id")
      available[pid] = (available[pid] ?: false) || row.optBoolean("is_available", false)
    }
    parseProductsJson(products.toString(), quantities, available, availability != null, availability)
  }

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
        val rawVariantId = variant.optString("id").takeIf { !variant.isNull("id") && it.isNotBlank() && it != "null" }
        val validVariantId = rawVariantId?.takeIf { UUID_REGEX.matches(it) }
        if (label.contains("color", true) || label.contains("colour", true)) {
          palettes.add(value)
          if (validVariantId != null) variantIds["color:" + value] = validVariantId
        }
        if (label.contains("size", true)) {
          sizes.add(value)
          if (validVariantId != null) variantIds["size:" + value] = validVariantId
        }
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
      SupabaseProductDto(id=id,sellerId=obj.optString("seller_id").takeIf { it.isNotBlank() && it != "null" },title=obj.optString("name",""),vendor=seller?.optString("shop_name")?.takeIf{it.isNotBlank()}?:"",createdAt=obj.optString("created_at",""),category=category?.optString("name")?:"",categoryId=category?.optString("id")?:"",price=price,originalPrice=basePrice.toInt(),discountPercent=discount,rating=0.0,reviewCount=0,imageUrl=imageUrl,badge=if(discount>0)"$discount% OFF" else null,inStock=stock,stockQuantity=quantities[id]?:0,inStockText=if(!known)"Availability not verified" else if(stock)"In Stock" else "Out of Stock",vendorVerified=false,vendorRating="",vendorSales="",vendorAvatarText="",deliveryText="Delivered by JTBazar",paymentInfo="Cash on Delivery",craftStory=obj.optString("description",""),highlights=highlights,palettes=palettes.distinct(),sizes=sizes.distinct(),variantIds=variantIds,isArtisanCollection=false,isFeatured=obj.optBoolean("is_featured",false),isBestSeller=false,isNewArrival=obj.optBoolean("is_new_arrival",false),availabilityKnown=known,dailyStatus=dailyStatus,foodSellingUnit=foodUnit,variantAvailability=variantAvailability)
    }
  }

  suspend fun fetchStoreSettings(): Result<SupabaseStoreSettingsDto> = withContext(Dispatchers.IO) {
    try {
      SupabaseClient.get("store_settings?id=eq.default&select=store_name,support_email,support_phone,support_whatsapp,delivery_fee,free_delivery_enabled,additional_delivery_fee,cod_enabled,return_window_days&limit=1").mapCatching { json ->
        val rows = JSONArray(json)
        if (rows.length() == 0) throw IOException("Store settings are not configured.")
        val row = rows.getJSONObject(0)
        SupabaseStoreSettingsDto(
          storeName = row.optString("store_name", "JTBazar"),
          supportEmail = row.optString("support_email").takeIf { it.isNotBlank() && it != "null" },
          supportPhone = row.optString("support_phone").takeIf { it.isNotBlank() && it != "null" },
          supportWhatsapp = row.optString("support_whatsapp").takeIf { it.isNotBlank() && it != "null" },
          deliveryFee = row.optDouble("delivery_fee", 0.0).toInt(),
          freeDeliveryEnabled = row.optBoolean("free_delivery_enabled", false),
          additionalDeliveryFee = row.optDouble("additional_delivery_fee", 0.0).toInt(),
          codEnabled = row.optBoolean("cod_enabled", false),
          returnWindowDays = row.optInt("return_window_days", 0)
        )
      }
    } catch (e: Exception) { Result.failure(e) }
  }

  // LIVE HOMEPAGE CONTENT (same published rows as the website)
  suspend fun fetchHomepageHeroes(): Result<List<SupabaseHomepageHeroDto>> = withContext(Dispatchers.IO) {
    try {
      SupabaseClient.get("homepage_heroes?select=id,title,subtitle,cta_text,cta_destination,image_url,mobile_image_url,image_alt_text,display_order,start_date,end_date&status=eq.published&order=display_order.asc")
        .mapCatching { json ->
          val rows = JSONArray(json)
          val nowIso = java.time.Instant.now().toString()
          (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val startDate = row.optString("start_date").takeIf { it.isNotBlank() && it != "null" }
            val endDate = row.optString("end_date").takeIf { it.isNotBlank() && it != "null" }
            if (!startDate.isNullOrBlank() && startDate > nowIso) return@mapNotNull null
            if (!endDate.isNullOrBlank() && endDate < nowIso) return@mapNotNull null

            val image = row.optString("image_url").takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            SupabaseHomepageHeroDto(
              id = row.optString("id"),
              title = row.optString("title"),
              subtitle = row.optString("subtitle").takeIf { it.isNotBlank() && it != "null" },
              ctaText = row.optString("cta_text").takeIf { it.isNotBlank() && it != "null" },
              ctaDestination = row.optString("cta_destination").takeIf { it.isNotBlank() && it != "null" },
              imageUrl = image,
              mobileImageUrl = row.optString("mobile_image_url").takeIf { it.startsWith("https://") },
              imageAltText = row.optString("image_alt_text").takeIf { it.isNotBlank() && it != "null" },
              displayOrder = row.optInt("display_order", index),
              startDate = startDate,
              endDate = endDate
            )
          }
        }
    } catch (e: Exception) {
      Log.e(TAG, "fetchHomepageHeroes error", e)
      Result.failure(e)
    }
  }

  private fun normalizePromotionImageUrl(raw: String?): String? {
    val clean = raw?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: return null
    return when {
      clean.startsWith("https://", ignoreCase = true) || clean.startsWith("http://", ignoreCase = true) -> clean
      clean.startsWith("/storage/v1/") -> "${SupabaseConfig.projectUrl}$clean"
      clean.startsWith("/") -> "${SupabaseConfig.projectUrl}$clean"
      !clean.contains("://") -> "${SupabaseConfig.storageUrl}/object/public/product-images/${clean.removePrefix("/")}"
      else -> null
    }
  }

  private fun JSONObject.optCleanString(vararg keys: String): String? {
    for (key in keys) {
      if (!has(key) || isNull(key)) continue
      val value = optString(key).trim()
      if (value.isNotBlank() && !value.equals("null", ignoreCase = true)) return value
    }
    return null
  }

  private fun JSONObject.optActiveFlag(): Boolean {
    for (key in listOf("is_active", "active", "is_enabled", "enabled")) {
      if (has(key) && !isNull(key)) {
        return optBoolean(key, true)
      }
    }
    return true
  }

  suspend fun fetchActivePromotions(position: String? = null): Result<List<SupabasePromotionDto>> = withContext(Dispatchers.IO) {
    try {
      val positionFilter = position?.takeIf { it.isNotBlank() }?.let { "&display_position=eq.${URLEncoder.encode(it, "UTF-8")}" } ?: ""
      SupabaseClient.get("promotions?select=*&status=eq.approved&order=priority.desc,created_at.desc&limit=30$positionFilter")
        .mapCatching { json ->
          val rows = JSONArray(json)
          (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val images = row.optJSONArray("banner_image_urls")
            val urls = if (images == null) {
              emptyList()
            } else {
              (0 until images.length()).mapNotNull { normalizePromotionImageUrl(images.optString(it)) }.distinct()
            }
            val primaryImage = normalizePromotionImageUrl(row.optCleanString("banner_image_url"))
            SupabasePromotionDto(
              id = row.optString("id"),
              title = row.optString("title"),
              description = row.optCleanString("description"),
              bannerImageUrl = primaryImage,
              bannerImageUrls = urls,
              ctaText = row.optCleanString("cta_text"),
              ctaDestination = row.optCleanString("cta_destination"),
              displayPosition = row.optCleanString("display_position") ?: "",
              priority = row.optInt("priority", 0),
              targetPlatform = row.optCleanString("target_platform") ?: "both",
              campaignType = row.optCleanString("campaign_type") ?: "banner",
              showOnLaunch = row.optBoolean("show_on_launch", false),
              startsAt = row.optCleanString("starts_at", "start_date"),
              endsAt = row.optCleanString("ends_at", "end_date"),
              couponCode = row.optCleanString("coupon_code"),
              discountPercent = if (!row.has("discount_percent") || row.isNull("discount_percent")) null else row.optDouble("discount_percent"),
              status = row.optCleanString("status") ?: "approved",
              isActive = row.optActiveFlag(),
              createdAt = row.optCleanString("created_at"),
              updatedAt = row.optCleanString("updated_at")
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

  suspend fun fetchCategories(forceRefresh: Boolean = false): Result<List<SupabaseCategoryDto>> = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    val cached = cachedCategories
    if (!forceRefresh && cached != null && (now - cachedCategoriesAtMs) < METADATA_CACHE_TTL_MS) {
      return@withContext Result.success(cached)
    }
    categoryCacheMutex.withLock {
      val latestCached = cachedCategories
      if (!forceRefresh && latestCached != null && (System.currentTimeMillis() - cachedCategoriesAtMs) < METADATA_CACHE_TTL_MS) {
        return@withContext Result.success(latestCached)
      }
      try {
        val result = SupabaseClient.get(
          "categories?select=id,name,slug,description,image_url,sort_order,is_coming_soon,parent_id,is_food_category&is_active=eq.true&order=sort_order.asc"
        ).mapCatching { json ->
          val arr = JSONArray(json)
          (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            SupabaseCategoryDto(
              id = obj.optString("id"),
              title = obj.optString("name"),
              slug = obj.optCleanString("slug") ?: "",
              countText = "",
              sampleItems = obj.optCleanString("description") ?: "",
              imageUrl = obj.optCleanString("image_url"),
              badge = if (obj.optBoolean("is_coming_soon")) "Coming soon" else null,
              displayOrder = obj.optInt("sort_order", i),
              parentId = obj.optCleanString("parent_id"),
              isFoodCategory = obj.optBoolean("is_food_category", false)
            )
          }
        }
        result.onSuccess { list ->
          cachedCategories = list
          cachedCategoriesAtMs = System.currentTimeMillis()
        }
        result
      } catch (e: Exception) {
        runCatching { Log.e(TAG, "fetchCategories error", e) }
        Result.failure(e)
      }
    }
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

  private fun JSONObject.optNonNullString(key: String, fallback: String = ""): String {
    if (!has(key) || isNull(key)) return fallback
    val value = optString(key, fallback)
    return if (value.equals("null", ignoreCase = true)) fallback else value
  }

  private fun parseAddressDto(obj: JSONObject, fallbackUserId: String): SupabaseAddressDto {
    return SupabaseAddressDto(
      id = obj.optNonNullString("id", ""),
      userId = obj.optNonNullString("user_id", fallbackUserId),
      name = obj.optNonNullString("recipient_name", ""),
      phone = obj.optNonNullString("phone", ""),
      addressLine = obj.optNonNullString("address_line_1", ""),
      area = obj.optNonNullString("address_line_2", ""),
      city = obj.optNonNullString("city", ""),
      province = obj.optNonNullString("province", ""),
      postalCode = obj.optNonNullString("postal_code", "").takeIf { it.isNotBlank() },
      tag = obj.optNonNullString("tag", "HOME").ifBlank { "HOME" },
      isDefault = obj.optBoolean("is_default", false),
      latitude = if (obj.isNull("latitude")) null else obj.optDouble("latitude"),
      longitude = if (obj.isNull("longitude")) null else obj.optDouble("longitude")
    )
  }

  suspend fun fetchAddresses(userId: String): Result<List<SupabaseAddressDto>> = withContext(Dispatchers.IO) {
    try {
      val res = SupabaseClient.get("customer_addresses?user_id=eq.$userId&select=*&order=is_default.desc,updated_at.desc")
      res.mapCatching { jsonStr ->
        val arr = JSONArray(jsonStr)
        val list = mutableListOf<SupabaseAddressDto>()
        for (i in 0 until arr.length()) {
          list.add(parseAddressDto(arr.getJSONObject(i), userId))
        }
        list
      }
    } catch (e: Exception) {
      Log.e(TAG, "fetchAddresses error", e)
      Result.failure(e)
    }
  }

  private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

  internal fun normalizePakistaniMobile(value: String): String {
    val phone = value.trim().replace(Regex("[\\s-]+"), "")
    if (!Regex("^(03[0-9]{9}|\\+923[0-9]{9})$").matches(phone)) {
      throw IOException("Enter a valid Pakistani mobile number like 03001234567 or +923001234567.")
    }
    return if (phone.startsWith("03")) "+92" + phone.substring(1) else phone
  }

  internal fun isValidPakistaniMobile(value: String): Boolean {
    val phone = value.trim().replace(Regex("[\\s-]+"), "")
    return Regex("^(03[0-9]{9}|\\+923[0-9]{9})$").matches(phone)
  }

  suspend fun saveAddress(address: SupabaseAddressDto): Result<SupabaseAddressDto> = withContext(Dispatchers.IO) {
    try {
      val cleanName = address.name.trim()
      val cleanLine = address.addressLine.trim()
      val cleanArea = address.area.trim()
      val cleanCity = address.city.trim()
      val cleanProvince = address.province.trim()

      if (cleanName.isEmpty()) throw IOException("Please enter recipient full name.")
      if (cleanLine.length < 3) throw IOException("Please enter a complete street address (at least 3 characters).")
      if (cleanCity.isEmpty()) throw IOException("Please enter your city.")
      val normalizedPhone = normalizePakistaniMobile(address.phone)

      // Note: public.customer_addresses schema does not have a `tag` column; keep only valid DB columns.
      val json = JSONObject().apply {
        if (address.id.isNotBlank() && !address.id.startsWith("addr_")) put("id", address.id)
        put("user_id", address.userId)
        put("recipient_name", cleanName)
        put("phone", normalizedPhone)
        put("address_line_1", cleanLine)
        if (cleanArea.isNotBlank()) put("address_line_2", cleanArea)
        put("city", cleanCity)
        if (cleanProvince.isNotBlank()) put("province", cleanProvince)
        if (!address.postalCode.isNullOrBlank()) put("postal_code", address.postalCode.trim())
        put("is_default", address.isDefault)
        address.latitude?.let { put("latitude", it) }
        address.longitude?.let { put("longitude", it) }
      }

      val writeResult = if (address.id.isNotBlank() && !address.id.startsWith("addr_")) {
        SupabaseClient.patch("customer_addresses?id=eq.${address.id}&user_id=eq.${address.userId}", json.toString())
      } else {
        SupabaseClient.post("customer_addresses", json.toString())
      }

      val savedDto = writeResult.mapCatching { body ->
        val trimmed = body.trim()
        val obj = when {
          trimmed.startsWith("[") -> JSONArray(trimmed).optJSONObject(0)
          trimmed.startsWith("{") -> JSONObject(trimmed)
          else -> null
        } ?: throw IOException("Address save confirmation was empty.")
        parseAddressDto(obj, address.userId).copy(tag = address.tag.ifBlank { "HOME" })
      }.getOrThrow()

      if (savedDto.isDefault && savedDto.id.isNotBlank()) {
        runCatching {
          SupabaseClient.patch(
            "customer_addresses?user_id=eq.${address.userId}&id=neq.${savedDto.id}&is_default=eq.true",
            JSONObject().put("is_default", false).toString()
          )
        }
      }

      Result.success(savedDto)
    } catch (e: Exception) {
      Log.e(TAG, "saveAddress error", e)
      Result.failure(e)
    }
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
        val productId = item.productId.trim()
        if (!UUID_REGEX.matches(productId)) {
          throw IOException("A cart product has an invalid ID. Remove it and add the product again.")
        }
        val rawVariantId = item.variantId?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        if (rawVariantId != null && !UUID_REGEX.matches(rawVariantId)) {
          throw IOException("The selected product option is outdated. Refresh the product, select its option again, and retry.")
        }
        lines.put(JSONObject().apply {
          put("product_id", productId)
          if (rawVariantId != null) put("variant_id", rawVariantId)
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

  // PRODUCT CAMPAIGNS (FLASH SALE, BOGO, BUNDLE, SEASONAL, CUSTOM)
  // ----------------------------------------------------

  suspend fun fetchActiveProductCampaigns(): Result<List<SupabaseProductCampaignDto>> = withContext(Dispatchers.IO) {
    try {
      val res = SupabaseClient.rpc("list_active_product_campaigns")
      res.fold(
        onSuccess = { jsonStr ->
          val arr = JSONArray(jsonStr)
          val list = mutableListOf<SupabaseProductCampaignDto>()
          for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val productIdsArray = obj.optJSONArray("product_ids")
            val productIdsList = mutableListOf<String>()
            if (productIdsArray != null) {
              for (j in 0 until productIdsArray.length()) {
                val pid = productIdsArray.optString(j)
                if (pid.isNotBlank()) productIdsList.add(pid)
              }
            }
            list.add(
              SupabaseProductCampaignDto(
                id = obj.optString("id", ""),
                name = obj.optString("name", ""),
                campaignType = obj.optString("campaign_type", "flash_sale"),
                displayTitle = obj.optString("display_title", obj.optString("name", "Campaign")),
                description = obj.optString("description").takeIf { it.isNotBlank() && it != "null" },
                discountPercent = obj.optDouble("discount_percent", 0.0),
                buyQuantity = obj.optInt("buy_quantity", 1),
                getQuantity = obj.optInt("get_quantity", 1),
                bundlePrice = if (!obj.isNull("bundle_price")) obj.optDouble("bundle_price") else null,
                startsAt = obj.optString("starts_at").takeIf { it.isNotBlank() && it != "null" },
                endsAt = obj.optString("ends_at").takeIf { it.isNotBlank() && it != "null" },
                productIds = productIdsList
              )
            )
          }
          Result.success(list)
        },
        onFailure = {
          Log.w(TAG, "list_active_product_campaigns RPC failed: ${it.message}")
          Result.success(emptyList())
        }
      )
    } catch (e: Exception) {
      Log.e(TAG, "fetchActiveProductCampaigns error", e)
      Result.success(emptyList())
    }
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
          val readAtStr = obj.optString("read_at").takeIf { it.isNotBlank() && it != "null" }
          list.add(
            SupabaseNotificationDto(
              id = obj.optString("id", ""),
              userId = obj.optString("user_id", userId),
              title = obj.optString("title", "Notification"),
              message = obj.optString("body", ""),
              type = obj.optString("kind", "store"),
              audience = obj.optString("audience").takeIf { it.isNotBlank() && it != "null" },
              relatedOrderId = obj.optString("related_order_id").takeIf { it.isNotBlank() && it != "null" },
              readAt = readAtStr,
              isRead = readAtStr != null,
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

  suspend fun fetchShops(forceRefresh: Boolean = false): Result<List<ShopRecord>> = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    val cached = cachedShops
    if (!forceRefresh && cached != null && (now - cachedShopsAtMs) < METADATA_CACHE_TTL_MS) {
      return@withContext Result.success(cached)
    }
    shopCacheMutex.withLock {
      val latestCached = cachedShops
      if (!forceRefresh && latestCached != null && (System.currentTimeMillis() - cachedShopsAtMs) < METADATA_CACHE_TTL_MS) {
        return@withContext Result.success(latestCached)
      }
      try {
        val (shopsResult, ratingsResult) = coroutineScope {
          val shopsDeferred = async { SupabaseClient.rpc("list_public_shops") }
          val reviewsDeferred = async {
            runCatching {
              SupabaseClient.get("seller_reviews?status=eq.approved&select=seller_id,rating&limit=1000")
            }.getOrNull()
          }
          shopsDeferred.await() to reviewsDeferred.await()
        }
        val ratingStatsBySeller = mutableMapOf<String, Pair<Int, Int>>()
        val reviewsJson = ratingsResult?.getOrNull()
        if (!reviewsJson.isNullOrBlank()) {
          runCatching {
            val reviewArr = JSONArray(reviewsJson)
            for (i in 0 until reviewArr.length()) {
              val rev = reviewArr.optJSONObject(i) ?: continue
              val sellerId = rev.optString("seller_id", "").takeIf { it.isNotBlank() && it != "null" } ?: continue
              val ratingVal = rev.optInt("rating", 0)
              if (ratingVal in 1..5) {
                val prev = ratingStatsBySeller[sellerId] ?: (0 to 0)
                ratingStatsBySeller[sellerId] = (prev.first + ratingVal) to (prev.second + 1)
              }
            }
          }
        }
        shopsResult.fold(
          onSuccess = { jsonStr ->
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<ShopRecord>()
            for (i in 0 until arr.length()) {
              val obj = arr.optJSONObject(i) ?: continue
              val name = obj.optString("shop_name", "").takeIf { it.isNotBlank() } ?: continue
              val shopId = obj.optString("id", "").takeIf { it.isNotBlank() } ?: continue
              val stats = ratingStatsBySeller[shopId]
              val computedAvgRating = if (stats != null && stats.second > 0) {
                stats.first.toDouble() / stats.second.toDouble()
              } else {
                listOf("rating", "average_rating", "avg_rating")
                  .firstNotNullOfOrNull { key ->
                    if (obj.has(key) && !obj.isNull(key)) obj.optDouble(key, 0.0).takeIf { !it.isNaN() && it > 0.0 } else null
                  } ?: 0.0
              }
              list.add(
                ShopRecord(
                  id = shopId,
                  name = name,
                  slug = obj.optString("slug", ""),
                  productCount = obj.optInt("product_count", 0),
                  description = obj.optString("description", ""),
                  logoUrl = obj.optString("shop_image_url").takeIf { it.startsWith("http") },
                  bannerUrl = null,
                  city = obj.optString("shop_location_label", ""),
                  address = obj.optString("shop_location_label", ""),
                  latitude = if (!obj.isNull("shop_lat")) obj.optDouble("shop_lat", 0.0) else 0.0,
                  longitude = if (!obj.isNull("shop_lng")) obj.optDouble("shop_lng", 0.0) else 0.0,
                  rating = computedAvgRating,
                  isVerified = obj.optBoolean("is_verified", false),
                  phone = "",
                  deliveryRadiusKm = obj.optDouble("delivery_radius_km", 0.0)
                )
              )
            }
            cachedShops = list
            cachedShopsAtMs = System.currentTimeMillis()
            Result.success(list)
          },
          onFailure = {
            runCatching { Log.w(TAG, "Public shop directory: ${it.message}") }
            Result.success(cachedShops ?: emptyList())
          }
        )
      } catch (e: Exception) {
        runCatching { Log.e(TAG, "fetchShops error", e) }
        Result.success(cachedShops ?: emptyList())
      }
    }
  }
}
