package com.example.data.repository

import android.util.Log
import com.example.R
import com.example.data.model.*
import com.example.data.supabase.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

data class ActiveProductCampaign(
  val campaign: SupabaseProductCampaignDto,
  val products: List<Product>
)

/**
 * Central Data Repository for JTBazar.
 * Strictly connects to real Supabase backend with ZERO hardcoded dummy/sample products,
 * dummy orders, or sample addresses.
 */
object MarketplaceRepository {
  private const val TAG = "MarketplaceRepo"
  // Repository background work runs on Default/IO so Main UI thread stays responsive.
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val catalogRefreshMutex = Mutex()
  private val userDataMutex = Mutex()
  private val ordersRefreshMutex = Mutex()
  private val shopsRefreshMutex = Mutex()
  private val notificationsRefreshMutex = Mutex()
  private const val PROMO_REFRESH_COOLDOWN_MS = 30_000L
  private const val USER_DATA_COOLDOWN_MS = 3_000L
  @Volatile private var lastPromotionsSyncMs = 0L
  @Volatile private var lastUserDataSyncMs = 0L
  @Volatile private var lastUserDataSyncUid: String? = null
  @Volatile private var lastNotificationsSyncMs = 0L
  @Volatile private var selectProductJob: Job? = null

  // Loading and error states
  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _isPlacingOrder = MutableStateFlow(false)
  val isPlacingOrder: StateFlow<Boolean> = _isPlacingOrder.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  fun clearErrorMessage() {
    _errorMessage.value = null
  }

  private val cartAddInFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
  private val wishlistInFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
  private val cancelInFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

sealed interface DeliveryFeeQuoteState {
  data object Idle : DeliveryFeeQuoteState
  data object Loading : DeliveryFeeQuoteState
  data class Quoted(
    val deliveryFee: Int,
    val distanceKm: Double? = null,
    val originLat: Double? = null,
    val originLng: Double? = null,
    val maxRadiusKm: Double? = null,
    val isServiceable: Boolean = true,
    val message: String? = null
  ) : DeliveryFeeQuoteState
  data class Unavailable(
    val reason: String,
    val originLat: Double? = null,
    val originLng: Double? = null,
    val maxRadiusKm: Double? = null
  ) : DeliveryFeeQuoteState
}

  private val _deliveryFee = MutableStateFlow(0)
  val deliveryFee: StateFlow<Int> = _deliveryFee.asStateFlow()
  private val _freeDeliveryEnabled = MutableStateFlow(false)
  val freeDeliveryEnabled: StateFlow<Boolean> = _freeDeliveryEnabled.asStateFlow()
  private val _additionalDeliveryFee = MutableStateFlow(0)
  val additionalDeliveryFee: StateFlow<Int> = _additionalDeliveryFee.asStateFlow()
  private val _storeSettings = MutableStateFlow<SupabaseStoreSettingsDto?>(null)
  val storeSettings: StateFlow<SupabaseStoreSettingsDto?> = _storeSettings.asStateFlow()
  private val _deliveryPricingSettings = MutableStateFlow<DeliveryPricingSettingsDto?>(null)
  val deliveryPricingSettings: StateFlow<DeliveryPricingSettingsDto?> = _deliveryPricingSettings.asStateFlow()
  private val _deliveryFeeQuoteState = MutableStateFlow<DeliveryFeeQuoteState>(DeliveryFeeQuoteState.Idle)
  val deliveryFeeQuoteState: StateFlow<DeliveryFeeQuoteState> = _deliveryFeeQuoteState.asStateFlow()
  @Volatile private var deliveryQuoteJob: Job? = null
  private val _codEnabled = MutableStateFlow(false)
  val codEnabled: StateFlow<Boolean> = _codEnabled.asStateFlow()
  private val _returnWindowDays = MutableStateFlow(0)
  val returnWindowDays: StateFlow<Int> = _returnWindowDays.asStateFlow()

  fun clearDeliveryFeeQuote() {
    deliveryQuoteJob?.cancel()
    _deliveryFeeQuoteState.value = DeliveryFeeQuoteState.Idle
  }

  suspend fun refreshDeliveryFeeQuoteSuspending(latitude: Double, longitude: Double): DeliveryFeeQuoteState {
    _deliveryFeeQuoteState.value = DeliveryFeeQuoteState.Loading
    val settings = _deliveryPricingSettings.value
      ?: SupabaseApiService.fetchDeliveryPricingSettings().getOrNull()?.also { _deliveryPricingSettings.value = it }
    val quoteRes = SupabaseApiService.fetchDeliveryFeeQuote(latitude, longitude)
    val nextState = quoteRes.fold(
      onSuccess = { quote ->
        DeliveryFeeQuoteState.Quoted(
          deliveryFee = quote.deliveryFee,
          distanceKm = quote.distanceKm,
          originLat = quote.originLat ?: settings?.originLat,
          originLng = quote.originLng ?: settings?.originLng,
          maxRadiusKm = quote.maxRadiusKm ?: settings?.maxDeliveryRadiusKm,
          isServiceable = quote.isServiceable,
          message = quote.message
        )
      },
      onFailure = { err ->
        val raw = err.message.orEmpty()
        val friendly = when {
          raw.contains("out of", ignoreCase = true) || raw.contains("range", ignoreCase = true) || raw.contains("radius", ignoreCase = true) ->
            com.example.localization.AppLanguageManager.tr(
              "Selected location is outside our delivery range. Please move the pin closer.",
              "منتخب کردہ مقام ہماری ڈیلیوری حدود سے باہر ہے۔ براہ کرم پن کو قریب کریں۔"
            )
          raw.contains("origin", ignoreCase = true) || raw.contains("not configured", ignoreCase = true) ->
            com.example.localization.AppLanguageManager.tr(
              "Distance delivery pricing is currently unavailable. Please try again later.",
              "فاصلے کے مطابق ڈیلیوری چارجز فی الحال دستیاب نہیں ہیں۔ براہ کرم بعد میں کوشش کریں۔"
            )
          else -> com.example.localization.AppLanguageManager.tr(
            "Could not calculate distance delivery charges for this location. Please retry.",
            "اس مقام کے لیے فاصلے کے مطابق ڈیلیوری چارجز معلوم نہیں ہو سکے۔ دوبارہ کوشش کریں۔"
          )
        }
        DeliveryFeeQuoteState.Unavailable(
          reason = friendly,
          originLat = settings?.originLat,
          originLng = settings?.originLng,
          maxRadiusKm = settings?.maxDeliveryRadiusKm
        )
      }
    )
    _deliveryFeeQuoteState.value = nextState
    return nextState
  }

  fun refreshDeliveryFeeQuote(latitude: Double, longitude: Double) {
    deliveryQuoteJob?.cancel()
    deliveryQuoteJob = scope.launch {
      refreshDeliveryFeeQuoteSuspending(latitude, longitude)
    }
  }

  // Countdown seconds for daily promotion
  private val _countdownSeconds = MutableStateFlow(0)
  val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

  // Selected Location (Default to Lahore, updated when user picks an address)
  private val _currentLocation = MutableStateFlow("Select delivery address")
  val currentLocation: StateFlow<String> = _currentLocation.asStateFlow()

  fun setLocation(loc: String) {
    _currentLocation.value = loc
  }

  private val _homepageHeroes = MutableStateFlow<List<SupabaseHomepageHeroDto>>(emptyList())
  val homepageHeroes: StateFlow<List<SupabaseHomepageHeroDto>> = _homepageHeroes.asStateFlow()
  private val _homepagePromotions = MutableStateFlow<List<SupabasePromotionDto>>(emptyList())
  val homepagePromotions: StateFlow<List<SupabasePromotionDto>> = _homepagePromotions.asStateFlow()

  private val _activeProductCampaigns = MutableStateFlow<List<ActiveProductCampaign>>(emptyList())
  val activeProductCampaigns: StateFlow<List<ActiveProductCampaign>> = _activeProductCampaigns.asStateFlow()

  private val _shops = MutableStateFlow<List<ShopRecord>>(emptyList())
  val shops: StateFlow<List<ShopRecord>> = _shops.asStateFlow()

  private val _ownVendorStatus = MutableStateFlow<String?>(null)
  val ownVendorStatus: StateFlow<String?> = _ownVendorStatus.asStateFlow()

  fun refreshShops(force: Boolean = false) {
    scope.launch {
      if (!shopsRefreshMutex.tryLock()) return@launch
      try {
        SupabaseApiService.fetchShops(forceRefresh = force).onSuccess { fetchedShops ->
          if (_shops.value != fetchedShops) _shops.value = fetchedShops
        }.onFailure {
          Log.e(TAG, "Failed to load shops: ${it.message}")
        }
      } finally {
        shopsRefreshMutex.unlock()
      }
    }
  }

  // ----------------------------------------------------
  // PRODUCTS STATE (REAL SUPABASE CATALOG ONLY)
  // ----------------------------------------------------
  private val _products = MutableStateFlow<List<Product>>(emptyList())
  val products: StateFlow<List<Product>> = _products.asStateFlow()

  private val _homePopularProducts = MutableStateFlow<List<Product>>(emptyList())
  val homePopularProducts: StateFlow<List<Product>> = _homePopularProducts.asStateFlow()

  private val _hasMorePopularProducts = MutableStateFlow(false)
  val hasMorePopularProducts: StateFlow<Boolean> = _hasMorePopularProducts.asStateFlow()

  private val _popularUsesFeaturedFilter = MutableStateFlow(false)
  val popularUsesFeaturedFilter: StateFlow<Boolean> = _popularUsesFeaturedFilter.asStateFlow()

  private val _hasMoreCatalogProducts = MutableStateFlow(false)
  val hasMoreCatalogProducts: StateFlow<Boolean> = _hasMoreCatalogProducts.asStateFlow()

  private val _wishlistedIds = MutableStateFlow<Set<String>>(emptySet())
  val wishlistedIds: StateFlow<Set<String>> = _wishlistedIds.asStateFlow()
  private val _wishlistedProducts = MutableStateFlow<List<Product>>(emptyList())
  val wishlistedProducts: StateFlow<List<Product>> = _wishlistedProducts.asStateFlow()

  private val _selectedProduct = MutableStateFlow<Product?>(null)
  val selectedProduct: StateFlow<Product?> = _selectedProduct.asStateFlow()

  val homePaginator: CatalogPaginator by lazy {
    CatalogPaginator(
      scope = scope,
      pageSize = CatalogPaginator.DEFAULT_PAGE_SIZE,
      errorContext = ErrorContext.PRODUCT_LOAD,
      fetcher = ::fetchCatalogPage
    )
  }

  fun createCatalogPaginator(
    paginatorScope: kotlinx.coroutines.CoroutineScope,
    errorContext: ErrorContext = ErrorContext.PRODUCT_LOAD
  ): CatalogPaginator {
    return CatalogPaginator(
      scope = paginatorScope,
      pageSize = CatalogPaginator.DEFAULT_PAGE_SIZE,
      errorContext = errorContext,
      fetcher = ::fetchCatalogPage
    )
  }

  fun applyCampaignPricing(product: Product, campaign: SupabaseProductCampaignDto): Product {
    val type = campaign.campaignType.lowercase()
    val supportsPercentPrice = type in setOf("flash_sale", "seasonal", "custom")
    if (!supportsPercentPrice || campaign.discountPercent <= 0.0) {
      val fallbackBadge = when (type) {
        "bogo" -> "Buy ${campaign.buyQuantity}, Get ${campaign.getQuantity}"
        "bundle" -> "Bundle price deal"
        else -> null
      }
      return if (product.badge == null && fallbackBadge != null) product.copy(badge = fallbackBadge) else product
    }
    val campaignPercent = campaign.discountPercent.toInt().coerceIn(0, 100)
    val bestDiscount = maxOf(product.discountPercent, campaignPercent)
    val discountedPrice = (product.originalPrice * (100 - bestDiscount) / 100.0).toInt()
    return product.copy(
      discountPercent = bestDiscount,
      price = discountedPrice,
      badge = "${bestDiscount}% OFF"
    )
  }

  fun createCampaignPaginator(
    paginatorScope: kotlinx.coroutines.CoroutineScope,
    activeCampaign: ActiveProductCampaign
  ): CatalogPaginator {
    val campaign = activeCampaign.campaign
    val allIds = campaign.productIds.distinct()
    val prehydrated = activeCampaign.products.associateBy { it.id }
    return CatalogPaginator(
      scope = paginatorScope,
      pageSize = CatalogPaginator.DEFAULT_PAGE_SIZE,
      errorContext = ErrorContext.PRODUCT_LOAD
    ) { _, page, pageSize ->
      val start = page * pageSize
      if (start >= allIds.size) {
        return@CatalogPaginator Result.success(PaginatedProductsPage(items = emptyList(), page = page, hasMore = false))
      }
      val end = minOf(start + pageSize, allIds.size)
      val sliceIds = allIds.subList(start, end)
      val wishlistSet = _wishlistedIds.value
      val missingIds = sliceIds.filter { it !in prehydrated }
      val fetchedMap = if (missingIds.isNotEmpty()) {
        val res = SupabaseApiService.fetchProductsByIds(missingIds)
        if (res.isFailure) {
          return@CatalogPaginator Result.failure(
            res.exceptionOrNull() ?: IllegalStateException(
              com.example.localization.AppLanguageManager.tr(
                "Could not load deals.",
                "ڈیلز لوڈ نہیں ہو سکیں۔"
              )
            )
          )
        }
        res.getOrNull().orEmpty()
          .map { applyCampaignPricing(mapDtoToProduct(it, wishlistSet), campaign) }
          .associateBy { it.id }
      } else {
        emptyMap()
      }
      val orderedItems = sliceIds.mapNotNull { id ->
        val resolved = fetchedMap[id] ?: prehydrated[id]
        resolved?.copy(isWishlisted = id in wishlistSet)
      }
      Result.success(
        PaginatedProductsPage(
          items = orderedItems,
          page = page,
          hasMore = end < allIds.size
        )
      )
    }
  }

  suspend fun fetchCatalogPage(
    query: CatalogQuery,
    page: Int,
    pageSize: Int = CatalogPaginator.DEFAULT_PAGE_SIZE
  ): Result<PaginatedProductsPage> {
    val wishlistSet = _wishlistedIds.value
    return SupabaseApiService.fetchProductsPage(
      page = page,
      pageSize = pageSize,
      categoryIds = query.categoryIds,
      isFeatured = query.isFeatured,
      searchQuery = query.searchQuery.takeIf { it.isNotBlank() },
      sortBy = query.sortBy,
      maxPrice = query.maxPrice
    ).map { dtoPage ->
      val mapped = dtoPage.items.map { mapDtoToProduct(it, wishlistSet) }
      PaginatedProductsPage(
        items = mapped,
        page = dtoPage.page,
        hasMore = dtoPage.hasMore
      )
    }
  }

  fun mapDtoToProduct(dto: SupabaseProductDto, wishlistedIds: Set<String> = _wishlistedIds.value): Product {
    val mappedVariants = dto.variants.map { v ->
      ProductVariant(
        id = v.id,
        optionLabel = v.optionLabel,
        optionValue = v.optionValue,
        sku = v.sku,
        priceOverride = v.priceOverride,
        imageUrl = v.imageUrl,
        isAvailable = v.isAvailable,
        stockQuantity = v.stockQuantity,
        sortOrder = v.sortOrder
      )
    }
    return Product(
      id = dto.id,
      sellerId = dto.sellerId,
      title = dto.title,
      vendor = dto.vendor,
      createdAt = dto.createdAt,
      category = dto.category,
      categoryId = dto.categoryId,
      price = dto.price,
      originalPrice = dto.originalPrice,
      discountPercent = dto.discountPercent,
      rating = dto.rating,
      reviewCount = dto.reviewCount,
      drawableRes = resolveDrawable(dto.category, dto.id),
      imageUrl = dto.imageUrl,
      imageUrls = dto.imageUrls,
      badge = dto.badge,
      inStockText = dto.inStockText,
      savingsText = dto.savingsText,
      vendorVerified = dto.vendorVerified,
      vendorRating = dto.vendorRating,
      vendorSales = dto.vendorSales,
      vendorAvatarText = dto.vendorAvatarText,
      deliveryText = dto.deliveryText,
      paymentInfo = dto.paymentInfo,
      craftStory = dto.craftStory,
      highlights = dto.highlights,
      palettes = dto.palettes.map { PaletteOption(it, 0xFF1E40AF) },
      sizes = dto.sizes.map { SizeOption(it, "Option") },
      variants = mappedVariants,
      variantIds = dto.variantIds,
      isArtisanCollection = dto.isArtisanCollection,
      isFeatured = dto.isFeatured,
      isNewArrival = dto.isNewArrival,
      isWishlisted = wishlistedIds.contains(dto.id),
      isAvailable = dto.inStock,
      availabilityKnown = dto.availabilityKnown,
      dailyStatus = dto.dailyStatus,
      foodSellingUnit = dto.foodSellingUnit,
      variantAvailability = dto.variantAvailability
    )
  }

  private fun applyProductReviewAggregate(productId: String, rows: List<SupabaseReviewDto>) {
    val validRatings = rows.map { it.rating }.filter { it in 1..5 }
    val count = validRatings.size
    val average = if (count > 0) validRatings.average() else 0.0
    _products.update { products ->
      products.map { product ->
        if (product.id == productId) product.copy(rating = average, reviewCount = count) else product
      }
    }
    _selectedProduct.value?.let { selected ->
      if (selected.id == productId) _selectedProduct.value = selected.copy(rating = average, reviewCount = count)
    }
  }

  fun selectProduct(product: Product) {
    _selectedProduct.value = product
    selectProductJob?.cancel()
    selectProductJob = scope.launch {
      SupabaseApiService.fetchProductReviews(product.id).onSuccess { rows ->
        if (_selectedProduct.value?.id == product.id) {
          _reviews.update { current -> current.filterNot { it.productId == product.id } + rows }
          applyProductReviewAggregate(product.id, rows)
        }
      }.onFailure { err ->
        if (err !is kotlinx.coroutines.CancellationException) {
          Log.w(TAG, "Product reviews unavailable for ${product.id}: ${err.message}")
          if (_selectedProduct.value?.id == product.id) {
            applyProductReviewAggregate(product.id, emptyList())
          }
        }
      }
    }
  }

  // ----------------------------------------------------
  // CATEGORIES STATE (REAL SUPABASE CATEGORIES ONLY)
  // ----------------------------------------------------
  private val _categories = MutableStateFlow<List<CategoryItem>>(emptyList())
  val categories: StateFlow<List<CategoryItem>> = _categories.asStateFlow()

  // ----------------------------------------------------
  // CART STATE (EMPTY BY DEFAULT, SYNCED PER USER)
  // ----------------------------------------------------
  private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
  val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

  private val _cartCount = MutableStateFlow(0)
  val cartCount: StateFlow<Int> = _cartCount.asStateFlow()

  private val _cartProductIds = MutableStateFlow<Set<String>>(emptySet())
  val cartProductIds: StateFlow<Set<String>> = _cartProductIds.asStateFlow()

  private fun updateCartCount() {
    val snapshot = _cartItems.value
    _cartCount.value = snapshot.sumOf { it.quantity }
    _cartProductIds.value = snapshot.mapTo(HashSet()) { it.product.id }
  }

  private const val GUEST_PREFS = "jtbazar_guest_shopping"
  private const val GUEST_CART = "guest_cart"
  private const val GUEST_WISHLIST = "guest_wishlist"
  private fun guestPrefs() = SupabaseClient.preferences(GUEST_PREFS)
  private fun persistGuestCart() {
    if (SupabaseClient.currentUserId != null) return
    val rows = JSONArray()
    _cartItems.value.forEach { item ->
      rows.put(org.json.JSONObject().apply {
        put("product_id", item.product.id)
        put("quantity", item.quantity)
        put("palette", item.selectedPalette)
        put("size", item.selectedSize)
        if (!item.variantId.isNullOrBlank()) put("variant_id", item.variantId)
        item.unitPrice?.let { put("unit_price", it) }
        if (!item.variantLabel.isNullOrBlank()) put("variant_label", item.variantLabel)
        if (!item.variantValue.isNullOrBlank()) put("variant_value", item.variantValue)
        if (!item.variantImageUrl.isNullOrBlank()) put("variant_image_url", item.variantImageUrl)
      })
    }
    guestPrefs()?.edit()?.putString(GUEST_CART, rows.toString())?.apply()
  }
  private fun persistGuestWishlist() {
    if (SupabaseClient.currentUserId != null) return
    val ids = JSONArray()
    _products.value.filter { it.isWishlisted }.forEach { ids.put(it.id) }
    guestPrefs()?.edit()?.putString(GUEST_WISHLIST, ids.toString())?.apply()
  }
  private fun restoreGuestShoppingData() {
    if (SupabaseClient.currentUserId != null) return
    val prefs = guestPrefs() ?: return
    val cartRows = JSONArray(prefs.getString(GUEST_CART, "[]") ?: "[]")
    val restored = (0 until cartRows.length()).mapNotNull { index ->
      val row = cartRows.optJSONObject(index) ?: return@mapNotNull null
      val productId = row.optString("product_id")
      val product = _products.value.firstOrNull { it.id == productId } ?: return@mapNotNull null
      val palette = row.optString("palette").takeIf { it.isNotBlank() && it != "null" }
      val size = row.optString("size").takeIf { it.isNotBlank() && it != "null" }
      val explicitVariantId = row.optString("variant_id").takeIf { it.isNotBlank() && it != "null" }
      val variantId = explicitVariantId
        ?: if (!size.isNullOrBlank()) product.variantIds["size:$size"]
        else if (!palette.isNullOrBlank()) product.variantIds["color:$palette"]
        else null
      val matchedVariant = variantId?.let { vid -> product.variants.firstOrNull { it.id == vid } }
      val savedUnitPrice = if (row.has("unit_price") && !row.isNull("unit_price")) row.optInt("unit_price") else null
      CartItem(
        id = "cart_guest_${productId}_${index}",
        product = product,
        quantity = row.optInt("quantity", 1),
        selectedPalette = palette,
        selectedSize = size,
        variantId = variantId,
        unitPrice = matchedVariant?.priceOverride ?: savedUnitPrice ?: product.price,
        variantLabel = matchedVariant?.optionLabel ?: row.optString("variant_label").takeIf { it.isNotBlank() && it != "null" },
        variantValue = matchedVariant?.optionValue ?: row.optString("variant_value").takeIf { it.isNotBlank() && it != "null" },
        variantImageUrl = matchedVariant?.imageUrl ?: row.optString("variant_image_url").takeIf { it.isNotBlank() && it != "null" }
      )
    }
    if (restored.isNotEmpty()) { _cartItems.value = (restored + _cartItems.value).distinctBy { it.product.id to it.variantId }; updateCartCount() }
    val wishlistRows = JSONArray(prefs.getString(GUEST_WISHLIST, "[]") ?: "[]")
    val ids = (0 until wishlistRows.length()).mapNotNull { wishlistRows.optString(it).takeIf(String::isNotBlank) }.toSet()
    if (ids.isNotEmpty()) {
      _wishlistedIds.value = ids
      _products.update { list -> list.map { it.copy(isWishlisted = it.id in ids) } }
      homePaginator.syncWishlistStates(ids)
      scope.launch { hydrateWishlistedProducts(ids) }
    }
  }

  private suspend fun hydrateWishlistedProducts(ids: Set<String>) {
    if (ids.isEmpty()) {
      _wishlistedProducts.value = emptyList()
      return
    }
    val localById = _products.value.associateBy { it.id }
    val missingIds = ids.filter { it !in localById }
    val fetched = if (missingIds.isNotEmpty()) {
      SupabaseApiService.fetchProductsByIds(missingIds).getOrNull().orEmpty().map { mapDtoToProduct(it, ids) }
    } else emptyList()
    val allById = localById + fetched.associateBy { it.id }
    _wishlistedProducts.value = ids.mapNotNull { id -> allById[id]?.copy(isWishlisted = true) }
  }
  private suspend fun mergeGuestShoppingData(userId: String) {
    val prefs = guestPrefs() ?: return
    val cartRows = JSONArray(prefs.getString(GUEST_CART, "[]") ?: "[]")
    var cartMerged = true
    for (index in 0 until cartRows.length()) {
      val row = cartRows.optJSONObject(index) ?: continue
      val productId = row.optString("product_id")
      val product = _products.value.firstOrNull { it.id == productId }
      if (product == null) { cartMerged = false; continue }
      val palette = row.optString("palette").takeIf { it.isNotBlank() && it != "null" }
      val size = row.optString("size").takeIf { it.isNotBlank() && it != "null" }
      val explicitVariantId = row.optString("variant_id").takeIf { it.isNotBlank() && it != "null" }
      val variantId = explicitVariantId
        ?: if (!size.isNullOrBlank()) product.variantIds["size:$size"]
        else if (!palette.isNullOrBlank()) product.variantIds["color:$palette"]
        else null
      if (SupabaseApiService.addToCart(userId, productId, row.optInt("quantity", 1), variantId).isFailure) cartMerged = false
    }
    if (cartMerged) prefs.edit().remove(GUEST_CART).apply()
    val wishlistRows = JSONArray(prefs.getString(GUEST_WISHLIST, "[]") ?: "[]")
    var wishlistMerged = true
    for (index in 0 until wishlistRows.length()) {
      val id = wishlistRows.optString(index)
      if (id.isNotBlank() && SupabaseApiService.addToWishlist(userId, id).isFailure) wishlistMerged = false
    }
    if (wishlistMerged) prefs.edit().remove(GUEST_WISHLIST).apply()
  }

  private fun isUnavailableStatus(status: String?): Boolean {
    val normalized = status?.trim()?.lowercase()?.replace('-', '_')?.replace(' ', '_')
    return normalized in setOf("out_of_stock", "unavailable", "not_available", "sold_out", "closed", "paused", "inactive")
  }

  private fun resolveVariantId(
    product: Product,
    palette: String? = null,
    size: String? = null,
    variantId: String? = null
  ): String? {
    val cleanId = variantId?.trim()?.takeIf { it.isNotBlank() }
    if (cleanId != null) {
      val known = product.variants.any { it.id == cleanId } || product.variantIds.values.contains(cleanId)
      if (known) return cleanId
    }
    if (!size.isNullOrBlank()) {
      product.variantIds["size:$size"]?.let { return it }
      product.variants.firstOrNull { it.optionValue.equals(size, ignoreCase = true) }?.id?.let { return it }
    }
    if (!palette.isNullOrBlank()) {
      product.variantIds["color:$palette"]?.let { return it }
      product.variants.firstOrNull { it.optionValue.equals(palette, ignoreCase = true) }?.id?.let { return it }
    }
    return null
  }

  fun canAddProductToCart(
    product: Product,
    palette: String? = null,
    size: String? = null,
    variantId: String? = null
  ): Boolean {
    val latest = _products.value.firstOrNull { it.id == product.id } ?: product
    // Fail closed when the live availability RPC did not verify this product.
    if (!latest.availabilityKnown || !latest.isAvailable) return false
    if (latest.foodSellingUnit != null && isUnavailableStatus(latest.dailyStatus)) return false

    val hasVariants = latest.variants.isNotEmpty() || latest.variantIds.isNotEmpty()
    val resolvedVariantId = resolveVariantId(latest, palette, size, variantId)
    if (hasVariants && resolvedVariantId == null) return false
    if (resolvedVariantId != null) {
      val variantAvailMap = latest.variantAvailability
      if (variantAvailMap.isNotEmpty()) {
        if (variantAvailMap[resolvedVariantId] != true) return false
      } else {
        val matched = latest.variants.firstOrNull { it.id == resolvedVariantId }
        if (matched != null && !matched.isAvailable) return false
        if (matched == null) return false
      }
    }
    return true
  }

  fun addToCart(
    product: Product,
    quantity: Int = 1,
    palette: String? = null,
    size: String? = null,
    variantId: String? = null
  ) {
    val latestProduct = _products.value.firstOrNull { it.id == product.id } ?: product
    if (!canAddProductToCart(latestProduct, palette, size, variantId)) {
      _errorMessage.value = "This item is currently out of stock or unavailable."
      return
    }
    val productToAdd = latestProduct
    val resolvedVariantId = resolveVariantId(productToAdd, palette, size, variantId)
    val selectedVariant = resolvedVariantId?.let { vid ->
      productToAdd.variants.firstOrNull { it.id == vid }
    }
    val resolvedPalette = palette?.takeIf { it.isNotBlank() }
      ?: selectedVariant?.takeIf {
        it.optionLabel.contains("color", true) || it.optionLabel.contains("colour", true)
      }?.optionValue
    val resolvedSize = size?.takeIf { it.isNotBlank() }
      ?: selectedVariant?.takeIf {
        !it.optionLabel.contains("color", true) && !it.optionLabel.contains("colour", true)
      }?.optionValue
    val resolvedUnitPrice = selectedVariant?.priceOverride ?: productToAdd.price
    val opKey = "${productToAdd.id}_${resolvedVariantId.orEmpty()}"
    if (!cartAddInFlight.add(opKey)) return

    var localId = ""
    _cartItems.update { current ->
      val existingIndex = current.indexOfFirst {
        it.product.id == productToAdd.id && it.variantId == resolvedVariantId
      }
      if (existingIndex >= 0) {
        current.toMutableList().also { list ->
          val item = list[existingIndex]
          localId = item.id
          list[existingIndex] = item.copy(
            quantity = item.quantity + quantity,
            unitPrice = resolvedUnitPrice,
            variantLabel = selectedVariant?.optionLabel ?: item.variantLabel,
            variantValue = selectedVariant?.optionValue ?: item.variantValue,
            variantImageUrl = selectedVariant?.imageUrl ?: item.variantImageUrl
          )
        }
      } else {
        val newItem = CartItem(
          id = "cart_" + java.util.UUID.randomUUID().toString(),
          product = productToAdd,
          quantity = quantity,
          selectedPalette = resolvedPalette,
          selectedSize = resolvedSize,
          variantId = resolvedVariantId,
          unitPrice = resolvedUnitPrice,
          variantLabel = selectedVariant?.optionLabel,
          variantValue = selectedVariant?.optionValue,
          variantImageUrl = selectedVariant?.imageUrl
        )
        localId = newItem.id
        current + newItem
      }
    }
    updateCartCount()
    val userId = SupabaseClient.currentUserId
    if (userId == null) {
      persistGuestCart()
      cartAddInFlight.remove(opKey)
    } else {
      scope.launch {
        try {
          SupabaseApiService.addToCart(userId, productToAdd.id, quantity, resolvedVariantId)
            .onSuccess { remoteId ->
              _cartItems.update { rows -> rows.map { if (it.id == localId) it.copy(id = remoteId) else it } }
            }
            .onFailure {
              _errorMessage.value = AppErrorMapper.toCustomerMessage(it, ErrorContext.CART_ADD)
            }
        } finally {
          cartAddInFlight.remove(opKey)
        }
      }
    }
  }

  fun adjustQuantity(itemId: String, delta: Int) {
    val current = _cartItems.value.firstOrNull { it.id == itemId } ?: return
    updateQuantity(itemId, current.quantity + delta)
  }

  fun updateQuantity(itemId: String, quantity: Int) {
    if (quantity <= 0) {
      removeFromCart(itemId)
      return
    }
    _cartItems.update { list ->
      list.map { if (it.id == itemId) it.copy(quantity = quantity) else it }
    }
    updateCartCount()
    val userId = SupabaseClient.currentUserId
    if (userId == null) persistGuestCart()
    if (userId != null && !itemId.startsWith("cart_")) scope.launch {
      SupabaseApiService.updateCartQuantity(itemId, quantity)
        .onFailure { _errorMessage.value = AppErrorMapper.toCustomerMessage(it, ErrorContext.CART_UPDATE) }
    }
  }

  fun removeFromCart(itemId: String) {
    _cartItems.update { list -> list.filterNot { it.id == itemId } }
    updateCartCount()
    val userId = SupabaseClient.currentUserId
    if (userId == null) persistGuestCart()
    if (userId != null && !itemId.startsWith("cart_")) scope.launch {
      SupabaseApiService.removeCartItem(itemId)
        .onFailure { _errorMessage.value = AppErrorMapper.toCustomerMessage(it, ErrorContext.CART_UPDATE) }
    }
  }

  fun clearCart() {
    _cartItems.value = emptyList()
    updateCartCount()
    val userId = SupabaseClient.currentUserId
    if (userId == null) persistGuestCart()
    if (userId != null) scope.launch {
      SupabaseApiService.clearCart(userId)
        .onFailure { _errorMessage.value = AppErrorMapper.toCustomerMessage(it, ErrorContext.CART_UPDATE) }
    }
  }

  // ----------------------------------------------------
  // WISHLIST
  // ----------------------------------------------------
  fun toggleWishlist(productId: String, productHint: Product? = null) {
    if (!wishlistInFlight.add(productId)) return
    val currentSet = _wishlistedIds.value
    val newWishlistedState = productId !in currentSet
    val updatedSet = if (newWishlistedState) currentSet + productId else currentSet - productId
    _wishlistedIds.value = updatedSet

    _products.update { list ->
      list.map { prod -> if (prod.id == productId) prod.copy(isWishlisted = newWishlistedState) else prod }
    }
    _homePopularProducts.update { list ->
      list.map { prod -> if (prod.id == productId) prod.copy(isWishlisted = newWishlistedState) else prod }
    }
    _activeProductCampaigns.update { list ->
      list.map { camp ->
        camp.copy(products = camp.products.map { prod ->
          if (prod.id == productId) prod.copy(isWishlisted = newWishlistedState) else prod
        })
      }
    }
    if (newWishlistedState) {
      val source = productHint ?: _products.value.firstOrNull { it.id == productId } ?: _selectedProduct.value?.takeIf { it.id == productId }
      if (source != null) {
        _wishlistedProducts.update { list ->
          (list.filterNot { it.id == productId } + source.copy(isWishlisted = true))
        }
      }
    } else {
      _wishlistedProducts.update { list -> list.filterNot { it.id == productId } }
    }
    if (_selectedProduct.value?.id == productId) {
      _selectedProduct.update { it?.copy(isWishlisted = newWishlistedState) }
    }
    homePaginator.updateSingleWishlist(productId, newWishlistedState)

    val userId = SupabaseClient.currentUserId
    if (userId == null) {
      persistGuestWishlist()
      wishlistInFlight.remove(productId)
    } else {
      scope.launch {
        try {
          val result = if (newWishlistedState) {
            SupabaseApiService.addToWishlist(userId, productId)
          } else {
            SupabaseApiService.removeFromWishlist(userId, productId).map { "removed" }
          }
          result.onFailure { error ->
            // Roll back optimistic UI update when Supabase rejects the change.
            _wishlistedIds.value = currentSet
            _products.update { list ->
              list.map { product ->
                if (product.id == productId) product.copy(isWishlisted = !newWishlistedState) else product
              }
            }
            _homePopularProducts.update { list ->
              list.map { product ->
                if (product.id == productId) product.copy(isWishlisted = !newWishlistedState) else product
              }
            }
            _activeProductCampaigns.update { list ->
              list.map { camp ->
                camp.copy(products = camp.products.map { product ->
                  if (product.id == productId) product.copy(isWishlisted = !newWishlistedState) else product
                })
              }
            }
            _selectedProduct.update { product ->
              if (product?.id == productId) product.copy(isWishlisted = !newWishlistedState) else product
            }
            homePaginator.updateSingleWishlist(productId, !newWishlistedState)
            scope.launch { hydrateWishlistedProducts(currentSet) }
            _errorMessage.value = AppErrorMapper.toCustomerMessage(error, ErrorContext.WISHLIST)
            Log.e(TAG, "Wishlist update failed for product $productId", error)
          }
        } finally {
          wishlistInFlight.remove(productId)
        }
      }
    }
  }

  // ----------------------------------------------------
  // DELIVERY ADDRESSES (USER SPECIFIC)
  // ----------------------------------------------------
  private val _addresses = MutableStateFlow<List<DeliveryAddress>>(emptyList())
  val addresses: StateFlow<List<DeliveryAddress>> = _addresses.asStateFlow()

  private val _selectedAddress = MutableStateFlow<DeliveryAddress?>(null)
  val selectedAddress: StateFlow<DeliveryAddress?> = _selectedAddress.asStateFlow()

  private fun DeliveryAddress.locationLabel(): String {
    return listOf(area, city).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "Selected address" }
  }

  private fun SupabaseAddressDto.toDeliveryAddress(preferredTag: String? = null): DeliveryAddress {
    return DeliveryAddress(
      id = id,
      name = name,
      phone = phone,
      addressLine = addressLine,
      area = area,
      city = city,
      province = province,
      tag = preferredTag?.takeIf { it.isNotBlank() } ?: tag.ifBlank { "HOME" },
      isDefault = isDefault,
      latitude = latitude,
      longitude = longitude
    )
  }

  fun selectAddress(address: DeliveryAddress) {
    _selectedAddress.value = address
    _currentLocation.value = address.locationLabel()
  }

  fun addOrUpdateAddress(
    newAddress: DeliveryAddress,
    onComplete: ((Result<DeliveryAddress>) -> Unit)? = null
  ) {
    val cleanAddress = newAddress.copy(
      name = newAddress.name.trim(),
      phone = newAddress.phone.trim(),
      addressLine = newAddress.addressLine.trim(),
      area = newAddress.area.trim(),
      city = newAddress.city.trim(),
      province = newAddress.province.trim(),
      tag = newAddress.tag.trim().ifBlank { "HOME" }
    )

    val validationError = when {
      cleanAddress.name.isEmpty() -> "Please enter recipient full name."
      !SupabaseApiService.isValidPakistaniMobile(cleanAddress.phone) ->
        "Enter a valid Pakistani mobile number like 03001234567 or +923001234567."
      cleanAddress.addressLine.length < 3 -> "Please enter a complete street address (at least 3 characters)."
      cleanAddress.city.isEmpty() -> "Please enter your city."
      else -> null
    }
    if (validationError != null) {
      _errorMessage.value = validationError
      onComplete?.invoke(Result.failure(IllegalArgumentException(validationError)))
      return
    }

    val userId = SupabaseClient.currentUserId
    if (userId != null) {
      scope.launch {
        val result = SupabaseApiService.saveAddress(
          SupabaseAddressDto(
            id = if (cleanAddress.id.startsWith("addr_")) "" else cleanAddress.id,
            userId = userId,
            name = cleanAddress.name,
            phone = cleanAddress.phone,
            addressLine = cleanAddress.addressLine,
            area = cleanAddress.area,
            city = cleanAddress.city,
            province = cleanAddress.province,
            tag = cleanAddress.tag,
            isDefault = cleanAddress.isDefault,
            latitude = cleanAddress.latitude,
            longitude = cleanAddress.longitude
          )
        )
        result.onSuccess { savedDto ->
          val existingTags = _addresses.value.associate { it.id to it.tag }
          val savedAddress = savedDto.toDeliveryAddress(preferredTag = cleanAddress.tag)

          val refreshed = SupabaseApiService.fetchAddresses(userId).getOrNull()?.map { dto ->
            val tagForRow = if (dto.id == savedAddress.id) cleanAddress.tag else existingTags[dto.id]
            dto.toDeliveryAddress(preferredTag = tagForRow)
          }

          if (!refreshed.isNullOrEmpty()) {
            _addresses.value = refreshed
            val resolvedSelected = refreshed.firstOrNull { it.id == savedAddress.id } ?: savedAddress
            _selectedAddress.value = resolvedSelected
            _currentLocation.value = resolvedSelected.locationLabel()
          } else {
            _addresses.update { list ->
              val base = if (savedAddress.isDefault) list.map { it.copy(isDefault = false) } else list
              val withoutOld = base.filterNot { it.id == savedAddress.id || it.id == cleanAddress.id }
              if (savedAddress.isDefault) listOf(savedAddress) + withoutOld else withoutOld + savedAddress
            }
            _selectedAddress.value = savedAddress
            _currentLocation.value = savedAddress.locationLabel()
          }

          onComplete?.let { cb -> withContext(Dispatchers.Main) { cb(Result.success(savedAddress)) } }
        }.onFailure { err ->
          val friendly = AppErrorMapper.toCustomerMessage(err, ErrorContext.ADDRESS)
          _errorMessage.value = friendly
          onComplete?.let { cb -> withContext(Dispatchers.Main) { cb(Result.failure(IllegalStateException(friendly))) } }
        }
      }
    } else {
      _addresses.update { list ->
        val base = if (cleanAddress.isDefault) list.map { it.copy(isDefault = false) } else list
        val index = base.indexOfFirst { it.id == cleanAddress.id }
        if (index >= 0) {
          base.toMutableList().also { it[index] = cleanAddress }
        } else {
          base + cleanAddress
        }
      }
      _selectedAddress.value = cleanAddress
      _currentLocation.value = cleanAddress.locationLabel()
      onComplete?.invoke(Result.success(cleanAddress))
    }
  }

  fun deleteAddress(addressId: String) {
    _addresses.update { list -> list.filterNot { it.id == addressId } }
    if (_selectedAddress.value?.id == addressId) _selectedAddress.value = _addresses.value.firstOrNull()
    val userId = SupabaseClient.currentUserId
    if (userId != null && !addressId.startsWith("addr_")) scope.launch {
      SupabaseApiService.deleteAddress(addressId, userId).onFailure { _errorMessage.value = AppErrorMapper.toCustomerMessage(it, ErrorContext.ADDRESS) }
    }
  }

  // ----------------------------------------------------
  // ORDERS (AUTHENTICATED CUSTOMER ORDERS ONLY)
  // ----------------------------------------------------
  private val _orders = MutableStateFlow<List<OrderRecord>>(emptyList())
  val orders: StateFlow<List<OrderRecord>> = _orders.asStateFlow()

  private val _activeOrder = MutableStateFlow<OrderRecord?>(null)
  val activeOrder: StateFlow<OrderRecord?> = _activeOrder.asStateFlow()

  fun selectOrder(order: OrderRecord) { _activeOrder.value = order }

  // ----------------------------------------------------
  // REVIEWS
  // ----------------------------------------------------
  private val _reviews = MutableStateFlow<List<SupabaseReviewDto>>(emptyList())
  val reviews: StateFlow<List<SupabaseReviewDto>> = _reviews.asStateFlow()

  // ----------------------------------------------------
  // NOTIFICATIONS
  // ----------------------------------------------------
  private val _notifications = MutableStateFlow<List<SupabaseNotificationDto>>(emptyList())
  val notifications: StateFlow<List<SupabaseNotificationDto>> = _notifications.asStateFlow()

  // ----------------------------------------------------
  // LIVE SUPABASE CATALOG SYNC
  // ----------------------------------------------------
  suspend fun refreshLiveCatalogSuspending(showLoading: Boolean = false, refreshUserData: Boolean = true): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    if (!catalogRefreshMutex.tryLock()) return@withContext false
    if (showLoading) _isLoading.value = true
    _errorMessage.value = null
    try {
      performLiveCatalogSync(refreshUserData)
      true
    } catch (e: Exception) {
      Log.e(TAG, "Backend sync error: ${e.message}", e)
      _errorMessage.value = AppErrorMapper.toCustomerMessage(e, ErrorContext.PRODUCT_LOAD)
      false
    } finally {
      if (showLoading) _isLoading.value = false
      catalogRefreshMutex.unlock()
    }
  }

  private val promotionsRefreshMutex = Mutex()
  private val realtimeClient = SupabaseRealtimeClient(
    onPromotionsChanged = { refreshPromotions(force = true) },
    onCatalogChanged = { refreshLiveCatalog(showLoading = false, refreshUserData = false) },
    onOrdersChanged = {
      val uid = SupabaseClient.currentUserId
      if (uid != null) {
        scope.launch { refreshUserOrdersSuspending(uid) }
      }
    },
    onNotificationsChanged = { refreshNotifications(force = true) },
    subscribeToCatalogTables = true,
    subscribeToVendorOrderTables = false,
    subscribeToNotificationTables = true,
    customerOrderFilterProvider = { SupabaseClient.currentUserId?.let { "customer_id=eq.$it" } },
    notificationFilterProvider = { SupabaseClient.currentUserId?.let { "user_id=eq.$it" } }
  )

  suspend fun refreshOrdersIfAuthenticated() {
    val uid = SupabaseClient.currentUserId?.takeIf { it.isNotBlank() && SupabaseClient.isAuthenticated } ?: return
    refreshUserOrdersSuspending(uid)
  }

  private suspend fun refreshUserOrdersSuspending(userId: String) = ordersRefreshMutex.withLock {
    withContext(Dispatchers.IO) {
      if (SupabaseClient.currentUserId != userId) return@withContext
      SupabaseApiService.fetchOrders(userId).onSuccess { orderDtoList ->
      val existingById = _orders.value.associateBy { it.orderId }
      val mapped = orderDtoList.map { dto ->
        val items = existingById[dto.id]?.items ?: SupabaseApiService.fetchOrderItems(dto.id).getOrNull().orEmpty().map { line ->
          CartItem(
            id = line.id,
            product = Product(
              id = line.productId,
              title = line.productTitle,
              vendor = "",
              category = "",
              price = line.unitPrice,
              originalPrice = line.unitPrice,
              discountPercent = 0,
              rating = 0.0,
              reviewCount = 0,
              imageUrl = line.imageUrl
            ),
            quantity = line.quantity,
            selectedPalette = line.selectedPalette,
            selectedSize = line.selectedSize,
            vendorFulfillmentStatus = line.vendorFulfillmentStatus,
            deliveryStatus = line.deliveryStatus
          )
        }
        val canonical = VendorOrderStatus.parse(dto.orderStatus)
        OrderRecord(
          orderId = dto.id,
          orderNumber = dto.orderNumber,
          date = dto.createdAt,
          items = items,
          subtotal = dto.subtotal,
          discount = dto.discount,
          deliveryFee = dto.deliveryFee,
          grandTotal = dto.grandTotal,
          address = DeliveryAddress(
            id = "addr_order",
            name = dto.addressName,
            phone = dto.addressPhone,
            addressLine = dto.addressLine,
            area = dto.addressArea,
            city = dto.addressCity,
            province = ""
          ),
          deliveryNote = dto.deliveryNote ?: "",
          statusHeader = when (canonical) {
            VendorOrderStatus.PENDING -> com.example.localization.AppLanguageManager.tr("Order Placed • Cash on Delivery", "آرڈر موصول • کیش آن ڈیلیوری")
            VendorOrderStatus.CONFIRMED -> com.example.localization.AppLanguageManager.tr("Confirmed • Preparing Items", "تصدیق شدہ • اشیاء کی تیاری جاری")
            VendorOrderStatus.PROCESSING -> com.example.localization.AppLanguageManager.tr("Vendor Coordination & QC", "فروخت کنندہ کی تیاری اور جانچ")
            VendorOrderStatus.READY_FOR_PICKUP -> com.example.localization.AppLanguageManager.tr("Ready for Pickup", "پک اپ کے لیے تیار")
            VendorOrderStatus.SHIPPED -> com.example.localization.AppLanguageManager.tr("Out for Delivery", "ترسیل کے لیے روانہ")
            VendorOrderStatus.DELIVERED -> com.example.localization.AppLanguageManager.tr("Delivered", "ترسیل مکمل")
            VendorOrderStatus.NOT_DELIVERED -> com.example.localization.AppLanguageManager.tr("Not Delivered", "ترسیل نہیں ہو سکی")
            VendorOrderStatus.CANCELLED -> com.example.localization.AppLanguageManager.tr("Cancelled", "منسوخ")
            null -> VendorOrderStatus.formatLabel(dto.orderStatus)
          },
          orderStatus = canonical?.name ?: dto.orderStatus,
          timeline = listOf(
            OrderTimelineStep(
              title = com.example.localization.AppLanguageManager.tr(
                "Order status: ${canonical?.label ?: VendorOrderStatus.formatLabel(dto.orderStatus)}",
                "آرڈر کی صورتحال: ${canonical?.localizedLabel() ?: VendorOrderStatus.formatLabel(dto.orderStatus)}"
              ),
              timestamp = dto.createdAt,
              description = com.example.localization.AppLanguageManager.tr(
                "Latest order status saved by JTBazar.",
                "جے ٹی بازار میں محفوظ شدہ تازہ ترین آرڈر کی صورتحال۔"
              ),
              state = StepState.COMPLETED
            )
          )
        )
      }
      _orders.value = mapped
      val currentSelectedId = _activeOrder.value?.orderId
      _activeOrder.value = mapped.firstOrNull { it.orderId == currentSelectedId }
        ?: mapped.firstOrNull { it.orderStatus != "DELIVERED" && it.orderStatus != "CANCELLED" }
        ?: mapped.firstOrNull()
      }
    }
  }

  fun startRealtimeCampaignSync() {
    realtimeClient.start()
  }

  fun stopRealtimeCampaignSync() {
    realtimeClient.stop()
  }

  fun refreshPromotions(force: Boolean = false) {
    scope.launch {
      refreshPromotionsSuspending(force = force)
    }
  }

  suspend fun refreshPromotionsSuspending(force: Boolean = false) {
    val now = System.currentTimeMillis()
    if (!force && lastPromotionsSyncMs > 0L && (now - lastPromotionsSyncMs) < PROMO_REFRESH_COOLDOWN_MS) {
      return
    }
    if (!promotionsRefreshMutex.tryLock()) return
    try {
      val latestNow = System.currentTimeMillis()
      if (!force && lastPromotionsSyncMs > 0L && (latestNow - lastPromotionsSyncMs) < PROMO_REFRESH_COOLDOWN_MS) {
        return
      }
      coroutineScope {
        val heroesDeferred = async { SupabaseApiService.fetchHomepageHeroes() }
        val promosDeferred = async { SupabaseApiService.fetchActivePromotions() }
        heroesDeferred.await()
          .onSuccess { if (_homepageHeroes.value != it) _homepageHeroes.value = it }
          .onFailure { Log.w(TAG, "Homepage heroes refresh unavailable: ${it.message}") }
        promosDeferred.await()
          .onSuccess {
            if (_homepagePromotions.value != it) _homepagePromotions.value = it
            CampaignPopupManager.evaluateLaunchCampaign(it)
            lastPromotionsSyncMs = System.currentTimeMillis()
          }
          .onFailure { Log.w(TAG, "Homepage promotions refresh unavailable: ${it.message}") }
      }
    } finally {
      promotionsRefreshMutex.unlock()
    }
  }

  fun refreshLiveCatalog(showLoading: Boolean = true, refreshUserData: Boolean = true) {
    scope.launch {
      refreshLiveCatalogSuspending(showLoading = showLoading, refreshUserData = refreshUserData)
    }
  }

  private suspend fun performLiveCatalogSync(refreshUserData: Boolean) = coroutineScope {
    val promoSyncAlreadyFresh = (System.currentTimeMillis() - lastPromotionsSyncMs) < PROMO_REFRESH_COOLDOWN_MS &&
      (_homepageHeroes.value.isNotEmpty() || _homepagePromotions.value.isNotEmpty())
    // Mark promo sync window immediately so concurrent STARTED lifecycle observers do not duplicate it.
    if (!promoSyncAlreadyFresh) {
      lastPromotionsSyncMs = System.currentTimeMillis()
    }
    // Launch independent public catalog endpoints concurrently for fast startup.
    val settingsDeferred = async { SupabaseApiService.fetchStoreSettings() }
    val deliveryPricingDeferred = async { SupabaseApiService.fetchDeliveryPricingSettings() }
    val categoryDeferred = async { SupabaseApiService.fetchCategories(forceRefresh = true) }
    val heroesDeferred = if (!promoSyncAlreadyFresh) async { SupabaseApiService.fetchHomepageHeroes() } else null
    val promosDeferred = if (!promoSyncAlreadyFresh) async { SupabaseApiService.fetchActivePromotions() } else null
    val shopsDeferred = async { SupabaseApiService.fetchShops() }
    val productDeferred = async {
      SupabaseApiService.fetchProductsPage(
        page = 0,
        pageSize = CatalogPaginator.DEFAULT_PAGE_SIZE
      )
    }
    val featuredDeferred = async {
      SupabaseApiService.fetchProductsPage(
        page = 0,
        pageSize = 7,
        isFeatured = true
      )
    }
    val campaignDeferred = async { SupabaseApiService.fetchActiveProductCampaigns() }
    val userDataDeferred = if (refreshUserData) {
      SupabaseClient.currentUserId?.let { uid -> async { loadUserDataSuspending(uid) } }
    } else null

    settingsDeferred.await().onSuccess {
      if (_storeSettings.value != it) {
        _storeSettings.value = it
        _freeDeliveryEnabled.value = it.freeDeliveryEnabled
        _additionalDeliveryFee.value = it.additionalDeliveryFee
        _deliveryFee.value = it.effectiveDeliveryFee
        _codEnabled.value = it.codEnabled
        _returnWindowDays.value = it.returnWindowDays
      }
    }.onFailure { _errorMessage.value = AppErrorMapper.toCustomerMessage(it, ErrorContext.STORE_SETTINGS) }
    deliveryPricingDeferred.await().onSuccess { settings ->
      _deliveryPricingSettings.value = settings
    }

    val categoryResult = categoryDeferred.await()
    if (categoryResult.isSuccess) {
      val catDtoList = categoryResult.getOrNull().orEmpty()
      val draftCategories = catDtoList.map { dto ->
        CategoryItem(
          id = dto.id,
          title = dto.title,
          countText = dto.countText,
          sampleItems = dto.sampleItems,
          drawableRes = 0,
          imageUrl = dto.imageUrl,
          badge = dto.badge,
          slug = dto.slug,
          parentId = dto.parentId,
          isFoodCategory = dto.isFoodCategory,
          displayOrder = dto.displayOrder
        )
      }
      val mappedCategories = draftCategories.map { item ->
        item.copy(
          drawableRes = FoodCategoryResolver.resolveFoodDrawableOrNull(item, draftCategories) ?: 0
        )
      }
      _categories.value = mappedCategories
      Log.d(TAG, "Loaded ${mappedCategories.size} categories from Supabase")
    } else if (_categories.value.isEmpty()) {
      _errorMessage.value = AppErrorMapper.toCustomerMessage(categoryResult.exceptionOrNull(), ErrorContext.CATEGORY_LOAD)
    }

    heroesDeferred?.await()
      ?.onSuccess { if (_homepageHeroes.value != it) _homepageHeroes.value = it }
      ?.onFailure { Log.w(TAG, "Homepage heroes unavailable: ${it.message}") }

    promosDeferred?.await()
      ?.onSuccess {
        if (_homepagePromotions.value != it) _homepagePromotions.value = it
        CampaignPopupManager.evaluateLaunchCampaign(it)
        lastPromotionsSyncMs = System.currentTimeMillis()
      }
      ?.onFailure {
        lastPromotionsSyncMs = 0L
        Log.w(TAG, "Homepage promotions unavailable: ${it.message}")
      }

    shopsDeferred.await()
      .onSuccess { fetchedShops -> if (_shops.value != fetchedShops) _shops.value = fetchedShops }
      .onFailure { Log.e(TAG, "Failed to load shops: ${it.message}") }

    val productResult = productDeferred.await()
    if (productResult.isSuccess) {
      val dtoPage = productResult.getOrNull()
      val dtoList = dtoPage?.items.orEmpty()
      val wishlistSet = _wishlistedIds.value + _products.value.filter { it.isWishlisted }.map { it.id }
      _wishlistedIds.value = wishlistSet
      val mappedProducts = dtoList.map { mapDtoToProduct(it, wishlistSet) }
      _hasMoreCatalogProducts.value = dtoPage?.hasMore == true || mappedProducts.size > 6
      if (_products.value != mappedProducts) {
        _products.value = mappedProducts
        val selectedProductId = _selectedProduct.value?.id
        if (selectedProductId != null) {
          mappedProducts.firstOrNull { it.id == selectedProductId }?.let { _selectedProduct.value = it }
        }
        _cartItems.update { cart ->
          cart.map { item ->
            val latest = mappedProducts.firstOrNull { it.id == item.product.id }
            if (latest == null) {
              item
            } else {
              val latestVariant = item.variantId?.let { vid -> latest.variants.firstOrNull { it.id == vid } }
              item.copy(
                product = latest,
                unitPrice = latestVariant?.priceOverride ?: latest.price,
                variantLabel = latestVariant?.optionLabel ?: item.variantLabel,
                variantValue = latestVariant?.optionValue ?: item.variantValue,
                variantImageUrl = latestVariant?.imageUrl ?: item.variantImageUrl
              )
            }
          }
        }
        updateCartCount()
      }
      if (SupabaseClient.currentUserId == null) restoreGuestShoppingData()
      Log.d(TAG, "Loaded ${mappedProducts.size} live products (page 0) from Supabase")

      val featuredPage = featuredDeferred.await().getOrNull()
      val featuredMapped = featuredPage?.items.orEmpty().map { mapDtoToProduct(it, wishlistSet) }
      if (featuredMapped.isNotEmpty()) {
        _homePopularProducts.value = featuredMapped.take(6)
        _hasMorePopularProducts.value = featuredMapped.size > 6 || featuredPage?.hasMore == true
        _popularUsesFeaturedFilter.value = true
      } else {
        _homePopularProducts.value = mappedProducts.take(6)
        _hasMorePopularProducts.value = mappedProducts.size > 6 || dtoPage?.hasMore == true
        _popularUsesFeaturedFilter.value = false
      }

      val campaignResult = campaignDeferred.await()
      if (campaignResult.isSuccess) {
        val activeCampaignList = campaignResult.getOrNull().orEmpty()
        val nowTime = System.currentTimeMillis()
        val validCampaigns = activeCampaignList.filter { camp ->
          if (camp.endsAt.isNullOrBlank()) true
          else {
            runCatching {
              java.time.Instant.parse(camp.endsAt).toEpochMilli() > nowTime
            }.getOrDefault(true)
          }
        }

        val loadedById = (mappedProducts + featuredMapped).associateBy { it.id }
        // Hydrate up to 7 highlighted deals per active campaign (max 28) so Home renders 6 cards and detects > 6.
        val neededIds = validCampaigns
          .flatMap { it.productIds.take(7) }
          .distinct()
          .filter { it !in loadedById }
          .take(28)
        val extraProductsById = if (neededIds.isNotEmpty()) {
          SupabaseApiService.fetchProductsByIds(neededIds).getOrNull().orEmpty()
            .map { mapDtoToProduct(it, wishlistSet) }
            .associateBy { it.id }
        } else emptyMap()
        val combinedById = loadedById + extraProductsById

        val mappedCampaigns = validCampaigns.mapNotNull { camp ->
          val matchedProds = camp.productIds.mapNotNull { combinedById[it] }
          if (matchedProds.isEmpty()) return@mapNotNull null

          val enrichedProds = matchedProds.map { prod -> applyCampaignPricing(prod, camp) }
          ActiveProductCampaign(campaign = camp, products = enrichedProds)
        }
        _activeProductCampaigns.value = mappedCampaigns
        Log.d(TAG, "Loaded ${mappedCampaigns.size} active product campaigns from Supabase")
      } else {
        _activeProductCampaigns.value = emptyList()
      }
    } else {
      featuredDeferred.cancel()
      campaignDeferred.cancel()
      _activeProductCampaigns.value = emptyList()
      _errorMessage.value = AppErrorMapper.toCustomerMessage(productResult.exceptionOrNull(), ErrorContext.PRODUCT_LOAD)
      Log.w(TAG, "Catalog refresh failed; retaining last successful product data")
    }

    userDataDeferred?.await()
  }

  // ----------------------------------------------------
  // ORDER PLACEMENT (CENTRALIZED JTBAZAR COD MODEL)
  // ----------------------------------------------------
  fun placeCurrentOrder(
    deliveryNote: String = "",
    deliveryLat: Double? = null,
    deliveryLng: Double? = null,
    deliveryLocationName: String? = null,
    deliveryLocationCapturedAt: String? = null,
    onComplete: ((Result<OrderRecord>) -> Unit)? = null
  ): OrderRecord? {
    val items = _cartItems.value
    if (items.isEmpty()) { onComplete?.invoke(Result.failure(IllegalStateException("Shopping cart is empty"))); return null }
    val address = _selectedAddress.value
    if (address == null) { onComplete?.invoke(Result.failure(IllegalStateException("Delivery address is required"))); return null }
    val userId = SupabaseClient.currentUserId
    if (userId == null) { onComplete?.invoke(Result.failure(IllegalStateException("Please sign in before placing an order."))); return null }
    if (!SupabaseApiService.isValidPakistaniMobile(address.phone)) {
      onComplete?.invoke(Result.failure(IllegalArgumentException("Enter a valid Pakistani mobile number like 03001234567 or +923001234567.")))
      return null
    }
    val email = SupabaseClient.currentUser.value?.email?.trim().orEmpty()
    if (email.isNotBlank() && (email.length > 254 || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())) {
      onComplete?.invoke(Result.failure(IllegalArgumentException("Invalid email address format.")))
      return null
    }
    if (_isPlacingOrder.value) return null
    _isPlacingOrder.value = true
    scope.launch {
      try {
        val latestProducts = SupabaseApiService.fetchProductsByIds(items.map { it.product.id }.distinct())
          .getOrThrow()
          .associateBy { it.id }
        val unavailable = items.firstOrNull { item ->
          val latest = latestProducts[item.product.id]
          val requiresVariant = latest != null && (latest.variants.isNotEmpty() || latest.variantIds.isNotEmpty())
          val variantAvailable = if (item.variantId.isNullOrBlank()) {
            true
          } else {
            val matched = latest?.variants?.firstOrNull { it.id == item.variantId }
            if (matched != null) matched.isAvailable else (latest?.variantAvailability?.get(item.variantId) == true)
          }
          latest == null || !latest.availabilityKnown || !latest.inStock ||
            (latest.foodSellingUnit != null && latest.dailyStatus != null && latest.dailyStatus != "available") ||
            (requiresVariant && item.variantId.isNullOrBlank()) ||
            !variantAvailable
        }
        if (unavailable != null) throw IllegalStateException(unavailable.product.title + " is out of stock or unavailable. Remove it from your cart before checkout.")
        if (deliveryLat != null && deliveryLng != null) {
          val currentQuote = _deliveryFeeQuoteState.value
          val verifiedQuote = if (currentQuote is DeliveryFeeQuoteState.Quoted && currentQuote.isServiceable) {
            currentQuote
          } else {
            refreshDeliveryFeeQuoteSuspending(deliveryLat, deliveryLng)
          }
          when (verifiedQuote) {
            is DeliveryFeeQuoteState.Quoted -> {
              if (!verifiedQuote.isServiceable) {
                throw IllegalStateException(
                  verifiedQuote.message?.takeIf { it.isNotBlank() }
                    ?: "Selected location is outside our delivery range. Please move the pin closer."
                )
              }
            }
            is DeliveryFeeQuoteState.Unavailable -> throw IllegalStateException(verifiedQuote.reason)
            DeliveryFeeQuoteState.Idle, DeliveryFeeQuoteState.Loading ->
              throw IllegalStateException("Please wait for delivery fee calculation to finish.")
          }
        }
        if (items.any { latestProducts[it.product.id]?.foodSellingUnit != null } && (deliveryLat == null || deliveryLng == null)) {
          throw IllegalStateException("Add your current location pin to confirm the 5 km food delivery area.")
        }
        val currentUser = SupabaseClient.currentUser.value
        val dto = SupabaseOrderDto(id="", orderNumber="", userId=userId, subtotal=0, discount=0, deliveryFee=0, grandTotal=0, paymentMethod="cod", paymentStatus="pending", orderStatus="pending", deliveryNote=deliveryNote, addressName=address.name, addressPhone=address.phone, addressLine=address.addressLine, addressArea=address.area, addressCity=address.city, createdAt="", addressProvince=address.province, customerEmail=currentUser?.email, idempotencyKey=java.util.UUID.randomUUID().toString())
        val lines = items.map { item ->
          SupabaseOrderItemDto(
            id = "",
            orderId = "",
            productId = item.product.id,
            productTitle = item.product.title,
            unitPrice = 0,
            quantity = item.quantity,
            totalPrice = 0,
            selectedPalette = item.selectedPalette,
            selectedSize = item.selectedSize,
            variantId = item.variantId
          )
        }
        val createdId = SupabaseApiService.createOrder(
          dto, lines,
          deliveryLat = deliveryLat,
          deliveryLng = deliveryLng,
          deliveryLocationName = deliveryLocationName,
          deliveryLocationCapturedAt = deliveryLocationCapturedAt
        ).getOrThrow()
        val saved = SupabaseApiService.fetchOrders(userId).getOrThrow().firstOrNull { it.id == createdId } ?: throw IllegalStateException("Order created but could not be reloaded.")
        val savedItems = SupabaseApiService.fetchOrderItems(saved.id).getOrThrow().map { line ->
          val sourceCartItem = items.firstOrNull { it.product.id == line.productId && (line.variantId == null || it.variantId == line.variantId) }
            ?: items.firstOrNull { it.product.id == line.productId }
          val sourceProduct = sourceCartItem?.product
          val product = sourceProduct?.copy(
            price = line.unitPrice,
            discountPercent = if (sourceProduct.originalPrice > 0)
              (((sourceProduct.originalPrice - line.unitPrice) * 100) / sourceProduct.originalPrice).coerceIn(0, 100)
            else sourceProduct.discountPercent
          ) ?: Product(id=line.productId, title=line.productTitle, vendor="", category="", price=line.unitPrice, originalPrice=line.unitPrice, discountPercent=0, rating=0.0, reviewCount=0)
          CartItem(
            id = line.id,
            product = product,
            quantity = line.quantity,
            selectedPalette = line.selectedPalette,
            selectedSize = line.selectedSize,
            variantId = line.variantId,
            unitPrice = line.unitPrice,
            variantLabel = sourceCartItem?.variantLabel,
            variantValue = sourceCartItem?.variantValue,
            variantImageUrl = sourceCartItem?.variantImageUrl
          )
        }
        val record = OrderRecord(orderId=saved.id, orderNumber=saved.orderNumber, date=saved.createdAt, items=savedItems, subtotal=saved.subtotal, discount=saved.discount, deliveryFee=saved.deliveryFee, grandTotal=saved.grandTotal, address=DeliveryAddress(id="order-address", name=saved.addressName, phone=saved.addressPhone, addressLine=saved.addressLine, area=saved.addressArea, city=saved.addressCity, province=address.province, isDefault=false), deliveryNote=saved.deliveryNote ?: "", statusHeader=saved.orderStatus, orderStatus=saved.orderStatus)
        _activeOrder.value = record
        _orders.update { listOf(record) + it.filterNot { old -> old.orderId == record.orderId } }
        clearCart()
        loadUserData(userId)
        onComplete?.let { cb -> withContext(Dispatchers.Main) { cb(Result.success(record)) } }
      } catch (e: Exception) {
        Log.e(TAG, "Secure order placement failed", e)
        val friendly = AppErrorMapper.toCustomerMessage(e, ErrorContext.ORDER_PLACE)
        onComplete?.let { cb -> withContext(Dispatchers.Main) { cb(Result.failure(IllegalStateException(friendly))) } }
      } finally { _isPlacingOrder.value = false }
    }
    return null
  }
  fun cancelOrder(orderId: String, onComplete: ((Boolean) -> Unit)? = null): Boolean {
    val existing = _orders.value.find { it.orderId == orderId }
    if (existing == null || existing.orderStatus !in listOf("PENDING", "CONFIRMED")) {
      onComplete?.invoke(false)
      return false
    }
    if (!cancelInFlight.add(orderId)) return false
    scope.launch {
      try {
        val result = SupabaseApiService.cancelOrder(orderId)
        if (result.isSuccess && result.getOrNull() == true) {
          SupabaseClient.currentUserId?.let { loadUserData(it) }
          onComplete?.let { cb -> withContext(Dispatchers.Main) { cb(true) } }
        } else {
          _errorMessage.value = AppErrorMapper.toCustomerMessage(result.exceptionOrNull(), ErrorContext.ORDER_CANCEL)
          onComplete?.let { cb -> withContext(Dispatchers.Main) { cb(false) } }
        }
      } finally {
        cancelInFlight.remove(orderId)
      }
    }
    return true
  }

  // ----------------------------------------------------
  // REVIEWS & CASES
  // ----------------------------------------------------
  fun submitProductReview(productId: String, rating: Int, title: String, comment: String) {
    val user = SupabaseClient.currentUser.value
    if (user == null) { _errorMessage.value = "Please sign in first."; return }
    val eligibleOrder = _orders.value.firstOrNull { order ->
      order.orderStatus == "DELIVERED" && order.items.any { it.product.id == productId }
    }
    if (eligibleOrder == null) {
      _errorMessage.value = "A delivered order containing this product is required before you can review it."
      return
    }
    val review = SupabaseReviewDto(
      id = "", productId = productId, orderId = eligibleOrder.orderId,
      userId = user.id, userName = "", rating = rating, title = title,
      comment = comment, createdAt = ""
    )
    scope.launch {
      SupabaseApiService.submitProductReview(review)
        .onSuccess {
          SupabaseApiService.fetchProductReviews(productId).onSuccess { rows ->
            _reviews.update { current -> current.filterNot { it.productId == productId } + rows }
            applyProductReviewAggregate(productId, rows)
          }
          _errorMessage.value = "Your review has been submitted for moderation."
        }
        .onFailure { _errorMessage.value = AppErrorMapper.toCustomerMessage(it, ErrorContext.REVIEWS) }
    }
  }

  fun submitReturnRequest(orderId: String, reason: String, description: String) {
    _errorMessage.value = "Return requests must be linked to the exact delivered order item and include product evidence. This app does not yet have the required evidence upload screen."
  }

  suspend fun submitComplaint(orderId: String?, subject: String, message: String): Result<Unit> {
    val user = SupabaseClient.currentUser.value
      ?: return Result.failure(IllegalStateException("Please sign in to contact support."))
    val complaint = SupabaseComplaintDto(id="", orderId=orderId, userId=user.id, subject=subject, message=message, createdAt="")
    return SupabaseApiService.submitComplaint(complaint).map { Unit }
  }

  fun markNotificationRead(id: String) {
    _notifications.update { list ->
      list.map { if (it.id == id) it.copy(isRead = true) else it }
    }
    scope.launch {
      SupabaseApiService.markNotificationRead(id)
    }
  }

  fun markAllNotificationsRead() {
    val userId = SupabaseClient.currentUserId ?: return
    _notifications.update { list ->
      list.map { it.copy(isRead = true) }
    }
    scope.launch {
      SupabaseApiService.markAllNotificationsRead(userId)
    }
  }

  fun refreshNotifications(userId: String? = null, force: Boolean = false) {
    val targetUid = userId ?: SupabaseClient.currentUserId ?: return
    val now = System.currentTimeMillis()
    if (!force && lastNotificationsSyncMs > 0L && (now - lastNotificationsSyncMs) < USER_DATA_COOLDOWN_MS) {
      return
    }
    scope.launch {
      if (!notificationsRefreshMutex.tryLock()) return@launch
      try {
        SupabaseApiService.fetchNotifications(targetUid).onSuccess { list ->
          if (SupabaseClient.currentUserId == targetUid) {
            if (_notifications.value != list) _notifications.value = list
            lastNotificationsSyncMs = System.currentTimeMillis()
          }
        }
      } finally {
        notificationsRefreshMutex.unlock()
      }
    }
  }

  // ----------------------------------------------------
  // USER SPECIFIC DATA SYNC
  // ----------------------------------------------------
  fun loadUserData(userId: String, force: Boolean = false) {
    scope.launch {
      loadUserDataSuspending(userId, force = force)
    }
  }

  private suspend fun loadUserDataSuspending(userId: String, force: Boolean = false) = userDataMutex.withLock {
    withContext(Dispatchers.IO) {
      if (SupabaseClient.currentUserId != userId) return@withContext
      val now = System.currentTimeMillis()
      if (!force && lastUserDataSyncUid == userId && (now - lastUserDataSyncMs) < USER_DATA_COOLDOWN_MS) {
        return@withContext
      }
      val previousUid = lastUserDataSyncUid
      if (previousUid != null && previousUid != userId) {
        clearUserData()
      }
      lastUserDataSyncUid = userId
      lastUserDataSyncMs = now
      try {
        // Fast-path skip if guest prefs contain no anonymous shopping data.
        val prefs = guestPrefs()
        val hasGuestCart = prefs?.getString(GUEST_CART, null).let { !it.isNullOrBlank() && it != "[]" }
        val hasGuestWishlist = prefs?.getString(GUEST_WISHLIST, null).let { !it.isNullOrBlank() && it != "[]" }
        if (hasGuestCart || hasGuestWishlist) {
          mergeGuestShoppingData(userId)
        }

        coroutineScope {
          val cartDeferred = async { SupabaseApiService.fetchCart(userId) }
          val wishlistDeferred = async { SupabaseApiService.fetchWishlist(userId) }
          val addrDeferred = async { SupabaseApiService.fetchAddresses(userId) }
          val ordersDeferred = async { SupabaseApiService.fetchOrders(userId) }
          val notifDeferred = async { SupabaseApiService.fetchNotifications(userId) }
          val vendorDeferred = async { runCatching { VendorService.getOwnApplication()?.status }.getOrNull() }

        val cartRes = cartDeferred.await()
        val wishlistRes = wishlistDeferred.await()
        val cartRows = if (cartRes.isSuccess) JSONArray(cartRes.getOrNull() ?: "[]") else JSONArray()
        val wishlistRows = if (wishlistRes.isSuccess) JSONArray(wishlistRes.getOrNull() ?: "[]") else JSONArray()

        val cartProductIds = (0 until cartRows.length()).mapNotNull { cartRows.optJSONObject(it)?.optString("product_id")?.takeIf(String::isNotBlank) }
        val wishlistIds = (0 until wishlistRows.length()).mapNotNull { wishlistRows.optJSONObject(it)?.optString("product_id")?.takeIf(String::isNotBlank) }.toSet()
        _wishlistedIds.value = wishlistIds

        val localById = _products.value.associateBy { it.id }
        val missingIds = (cartProductIds + wishlistIds).distinct().filter { it !in localById }
        val fetchedMissing = if (missingIds.isNotEmpty()) {
          SupabaseApiService.fetchProductsByIds(missingIds).getOrNull().orEmpty()
            .map { mapDtoToProduct(it, wishlistIds) }
            .associateBy { it.id }
        } else emptyMap()
        val hydratedById = localById + fetchedMissing

        if (cartRes.isSuccess) {
          val mappedCart = (0 until cartRows.length()).mapNotNull { index ->
            val row = cartRows.optJSONObject(index) ?: return@mapNotNull null
            val productId = row.optString("product_id")
            val product = hydratedById[productId] ?: return@mapNotNull null
            val variantId = row.optString("variant_id")
              .takeIf { !row.isNull("variant_id") && it.isNotBlank() && it != "null" }
            val matchedVariant = variantId?.let { vid -> product.variants.firstOrNull { it.id == vid } }
            val selectedOption = product.variantIds.entries.firstOrNull { it.value == variantId }?.key
            CartItem(
              id = row.optString("id"),
              product = product,
              quantity = row.optInt("quantity", 1),
              selectedPalette = matchedVariant?.takeIf {
                it.optionLabel.contains("color", true) || it.optionLabel.contains("colour", true)
              }?.optionValue ?: selectedOption?.takeIf { it.startsWith("color:") }?.removePrefix("color:"),
              selectedSize = matchedVariant?.takeIf {
                !it.optionLabel.contains("color", true) && !it.optionLabel.contains("colour", true)
              }?.optionValue ?: selectedOption?.takeIf { it.startsWith("size:") }?.removePrefix("size:"),
              variantId = variantId,
              unitPrice = matchedVariant?.priceOverride ?: product.price,
              variantLabel = matchedVariant?.optionLabel,
              variantValue = matchedVariant?.optionValue,
              variantImageUrl = matchedVariant?.imageUrl
            )
          }
          _cartItems.value = mappedCart
          updateCartCount()
        }
        if (wishlistRes.isSuccess) {
          _products.update { list -> list.map { it.copy(isWishlisted = wishlistIds.contains(it.id)) } }
          _homePopularProducts.update { list -> list.map { it.copy(isWishlisted = wishlistIds.contains(it.id)) } }
          _activeProductCampaigns.update { list ->
            list.map { camp ->
              camp.copy(products = camp.products.map { it.copy(isWishlisted = wishlistIds.contains(it.id)) })
            }
          }
          _wishlistedProducts.value = wishlistIds.mapNotNull { id -> hydratedById[id]?.copy(isWishlisted = true) }
          homePaginator.syncWishlistStates(wishlistIds)
        }

        // 1. Apply addresses
        val addrRes = addrDeferred.await()
        if (addrRes.isSuccess) {
          val addrList = addrRes.getOrNull().orEmpty().map { dto ->
            DeliveryAddress(
              id = dto.id,
              name = dto.name,
              phone = dto.phone,
              addressLine = dto.addressLine,
              area = dto.area,
              city = dto.city,
              province = dto.province,
              tag = dto.tag,
              isDefault = dto.isDefault,
              latitude = dto.latitude,
              longitude = dto.longitude
            )
          }
          _addresses.value = addrList
          _selectedAddress.value = addrList.firstOrNull { it.isDefault } ?: addrList.firstOrNull()
          _selectedAddress.value?.let { _currentLocation.value = "${it.area}, ${it.city}" }
        }

        // 2. Apply orders (fetch recent order line items concurrently, avoiding N+1 startup storms)
        val ordersRes = ordersDeferred.await()
        if (ordersRes.isSuccess) {
          val orderDtoList = ordersRes.getOrNull().orEmpty()
          val recentDtos = orderDtoList.take(10)
          val itemsByOrderId = recentDtos.map { dto ->
            async {
              dto.id to SupabaseApiService.fetchOrderItems(dto.id).getOrNull().orEmpty().map { line ->
                val product = Product(
                  id = line.productId,
                  title = line.productTitle,
                  vendor = "",
                  category = "",
                  price = line.unitPrice,
                  originalPrice = line.unitPrice,
                  discountPercent = 0,
                  rating = 0.0,
                  reviewCount = 0,
                  imageUrl = line.imageUrl
                )
                CartItem(
                  id = line.id,
                  product = product,
                  quantity = line.quantity,
                  selectedPalette = line.selectedPalette,
                  selectedSize = line.selectedSize,
                  vendorFulfillmentStatus = line.vendorFulfillmentStatus,
                  deliveryStatus = line.deliveryStatus
                )
              }
            }
          }.awaitAll().toMap()

          val mappedOrders = orderDtoList.map { dto ->
            OrderRecord(
              orderId = dto.id,
              orderNumber = dto.orderNumber,
              date = dto.createdAt,
              items = itemsByOrderId[dto.id].orEmpty(),
              subtotal = dto.subtotal,
              discount = dto.discount,
              deliveryFee = dto.deliveryFee,
              grandTotal = dto.grandTotal,
              address = DeliveryAddress(
                id = "addr_order",
                name = dto.addressName,
                phone = dto.addressPhone,
                addressLine = dto.addressLine,
                area = dto.addressArea,
                city = dto.addressCity,
                province = ""
              ),
              deliveryNote = dto.deliveryNote ?: "",
              statusHeader = when (VendorOrderStatus.parse(dto.orderStatus)) {
                VendorOrderStatus.PENDING -> com.example.localization.AppLanguageManager.tr("Order Placed • Cash on Delivery", "آرڈر موصول • کیش آن ڈیلیوری")
                VendorOrderStatus.CONFIRMED -> com.example.localization.AppLanguageManager.tr("Confirmed • Preparing Items", "تصدیق شدہ • اشیاء کی تیاری جاری")
                VendorOrderStatus.PROCESSING -> com.example.localization.AppLanguageManager.tr("Vendor Coordination & QC", "فروخت کنندہ کی تیاری اور جانچ")
                VendorOrderStatus.READY_FOR_PICKUP -> com.example.localization.AppLanguageManager.tr("Ready for Pickup", "پک اپ کے لیے تیار")
                VendorOrderStatus.SHIPPED -> com.example.localization.AppLanguageManager.tr("Out for Delivery", "ترسیل کے لیے روانہ")
                VendorOrderStatus.DELIVERED -> com.example.localization.AppLanguageManager.tr("Delivered", "ترسیل مکمل")
                VendorOrderStatus.NOT_DELIVERED -> com.example.localization.AppLanguageManager.tr("Not Delivered", "ترسیل نہیں ہو سکی")
                VendorOrderStatus.CANCELLED -> com.example.localization.AppLanguageManager.tr("Cancelled", "منسوخ")
                null -> VendorOrderStatus.formatLabel(dto.orderStatus)
              },
              orderStatus = VendorOrderStatus.parse(dto.orderStatus)?.name ?: dto.orderStatus,
              timeline = listOf(
                OrderTimelineStep(
                  title = com.example.localization.AppLanguageManager.tr(
                    "Order status: ${VendorOrderStatus.formatLabel(dto.orderStatus)}",
                    "آرڈر کی صورتحال: ${VendorOrderStatus.formatLabel(dto.orderStatus)}"
                  ),
                  timestamp = dto.createdAt,
                  description = com.example.localization.AppLanguageManager.tr(
                    "Latest order status saved by JTBazar.",
                    "جے ٹی بازار میں محفوظ شدہ تازہ ترین آرڈر کی صورتحال۔"
                  ),
                  state = StepState.COMPLETED
                )
              )
            )
          }
          _orders.value = mappedOrders
          _activeOrder.value = mappedOrders.firstOrNull { it.orderStatus != "DELIVERED" && it.orderStatus != "CANCELLED" } ?: mappedOrders.firstOrNull()
        }

        // 3. Apply notifications
        val notifRes = notifDeferred.await()
        if (notifRes.isSuccess && SupabaseClient.currentUserId == userId) {
          val latestNotifs = notifRes.getOrNull().orEmpty()
          if (_notifications.value != latestNotifs) _notifications.value = latestNotifs
          lastNotificationsSyncMs = System.currentTimeMillis()
        }
        val vendorStatus = vendorDeferred.await()
        if (SupabaseClient.currentUserId == userId && _ownVendorStatus.value != vendorStatus) {
          _ownVendorStatus.value = vendorStatus
        }
      }
      } catch (e: Exception) {
        Log.e(TAG, "Error loading user data for $userId", e)
      }
    }
  }

  fun clearUserData() {
    lastUserDataSyncUid = null
    lastUserDataSyncMs = 0L
    lastNotificationsSyncMs = 0L
    _ownVendorStatus.value = null
    _addresses.value = emptyList()
    _selectedAddress.value = null
    _orders.value = emptyList()
    _activeOrder.value = null
    _cartItems.value = emptyList()
    _cartCount.value = 0
    _cartProductIds.value = emptySet()
    _wishlistedIds.value = emptySet()
    _wishlistedProducts.value = emptyList()
    _notifications.value = emptyList()
    _currentLocation.value = "Select delivery address"
  }

  // ----------------------------------------------------
  // DRAWABLE ASSET RESOLUTION (FOOD-ONLY LOCAL FALLBACKS)
  // ----------------------------------------------------
  private fun resolveDrawable(category: String, id: String): Int = 0

  private fun resolveCategoryDrawable(title: String): Int = 0

  init {
    updateCartCount()

    // Observe customer authentication user-ID transitions once per sign-in/sign-out.
    scope.launch {
      SupabaseClient.session
        .map { it?.user?.id }
        .distinctUntilChanged()
        .collect { uid ->
          if (!uid.isNullOrBlank()) {
            loadUserData(uid)
          } else {
            clearUserData()
          }
        }
    }

    // Asynchronously fetch live catalog from Supabase without duplicating user sync.
    refreshLiveCatalog(showLoading = true, refreshUserData = false)
  }
}
