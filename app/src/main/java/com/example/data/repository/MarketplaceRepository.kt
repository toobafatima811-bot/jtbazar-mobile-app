package com.example.data.repository

import android.util.Log
import com.example.R
import com.example.data.model.*
import com.example.data.supabase.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray

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
  // Repository state and completion callbacks are consumed by Compose UI.
  // Keep them on Main; SupabaseApiService moves network calls to Dispatchers.IO.
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val catalogRefreshMutex = Mutex()

  // Loading and error states
  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _isPlacingOrder = MutableStateFlow(false)
  val isPlacingOrder: StateFlow<Boolean> = _isPlacingOrder.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  private val _deliveryFee = MutableStateFlow(0)
  val deliveryFee: StateFlow<Int> = _deliveryFee.asStateFlow()
  private val _freeDeliveryEnabled = MutableStateFlow(false)
  val freeDeliveryEnabled: StateFlow<Boolean> = _freeDeliveryEnabled.asStateFlow()
  private val _additionalDeliveryFee = MutableStateFlow(0)
  val additionalDeliveryFee: StateFlow<Int> = _additionalDeliveryFee.asStateFlow()
  private val _storeSettings = MutableStateFlow<SupabaseStoreSettingsDto?>(null)
  val storeSettings: StateFlow<SupabaseStoreSettingsDto?> = _storeSettings.asStateFlow()
  private val _codEnabled = MutableStateFlow(false)
  val codEnabled: StateFlow<Boolean> = _codEnabled.asStateFlow()
  private val _returnWindowDays = MutableStateFlow(0)
  val returnWindowDays: StateFlow<Int> = _returnWindowDays.asStateFlow()

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

  fun refreshShops() {
    scope.launch {
      SupabaseApiService.fetchShops().onSuccess { fetchedShops ->
        _shops.value = fetchedShops
      }.onFailure {
        Log.e(TAG, "Failed to load shops: ${it.message}")
      }
    }
  }

  // ----------------------------------------------------
  // PRODUCTS STATE (REAL SUPABASE CATALOG ONLY)
  // ----------------------------------------------------
  private val _products = MutableStateFlow<List<Product>>(emptyList())
  val products: StateFlow<List<Product>> = _products.asStateFlow()

  private val _selectedProduct = MutableStateFlow<Product?>(null)
  val selectedProduct: StateFlow<Product?> = _selectedProduct.asStateFlow()

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
    scope.launch {
      SupabaseApiService.fetchProductReviews(product.id).onSuccess { rows ->
        _reviews.update { current -> current.filterNot { it.productId == product.id } + rows }
        applyProductReviewAggregate(product.id, rows)
      }.onFailure {
        _errorMessage.value = it.message
        // Do not present cached or guessed ratings when live reviews cannot be verified.
        applyProductReviewAggregate(product.id, emptyList())
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

  private fun updateCartCount() {
    _cartCount.value = _cartItems.value.sumOf { it.quantity }
  }

  private const val GUEST_PREFS = "jtbazar_guest_shopping"
  private const val GUEST_CART = "guest_cart"
  private const val GUEST_WISHLIST = "guest_wishlist"
  private fun guestPrefs() = SupabaseClient.preferences(GUEST_PREFS)
  private fun persistGuestCart() {
    if (SupabaseClient.currentUserId != null) return
    val rows = JSONArray()
    _cartItems.value.forEach { item -> rows.put(org.json.JSONObject().apply { put("product_id", item.product.id); put("quantity", item.quantity); put("palette", item.selectedPalette); put("size", item.selectedSize) }) }
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
      val variantId = if (!size.isNullOrBlank()) product.variantIds["size:$size"] else if (!palette.isNullOrBlank()) product.variantIds["color:$palette"] else null
      CartItem(id="cart_guest_${productId}_${index}", product=product, quantity=row.optInt("quantity", 1), selectedPalette=palette, selectedSize=size, variantId=variantId)
    }
    if (restored.isNotEmpty()) { _cartItems.value = (restored + _cartItems.value).distinctBy { it.product.id to it.variantId }; updateCartCount() }
    val wishlistRows = JSONArray(prefs.getString(GUEST_WISHLIST, "[]") ?: "[]")
    val ids = (0 until wishlistRows.length()).mapNotNull { wishlistRows.optString(it).takeIf(String::isNotBlank) }.toSet()
    if (ids.isNotEmpty()) _products.update { list -> list.map { it.copy(isWishlisted = it.id in ids) } }
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
      val variantId = if (!size.isNullOrBlank()) product.variantIds["size:$size"] else if (!palette.isNullOrBlank()) product.variantIds["color:$palette"] else null
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

  fun canAddProductToCart(product: Product, palette: String? = null, size: String? = null): Boolean {
    val latest = _products.value.firstOrNull { it.id == product.id } ?: product
    // Fail closed when the live availability RPC did not verify this product.
    // This prevents the detail screen from allowing a purchase when the card showed
    // unavailable or when stock data could not be loaded.
    if (!latest.availabilityKnown || !latest.isAvailable) return false
    if (latest.foodSellingUnit != null && isUnavailableStatus(latest.dailyStatus)) return false

    val variantKey = if (!size.isNullOrBlank()) "size:$size" else if (!palette.isNullOrBlank()) "color:$palette" else null
    if (latest.variantIds.isNotEmpty() && variantKey == null) return false
    if (variantKey != null) {
      val variantId = latest.variantIds[variantKey] ?: return false
      if (latest.variantAvailability[variantId] != true) return false
    }
    return true
  }

  fun addToCart(product: Product, quantity: Int = 1, palette: String? = null, size: String? = null) {
    val latestProduct = _products.value.firstOrNull { it.id == product.id } ?: product
    if (!canAddProductToCart(latestProduct, palette, size)) {
      _errorMessage.value = "${latestProduct.title} is out of stock or unavailable."
      return
    }
    val productToAdd = latestProduct
    val variantId = if (!size.isNullOrBlank()) productToAdd.variantIds["size:" + size]
      else if (!palette.isNullOrBlank()) productToAdd.variantIds["color:" + palette] else null
    var localId = ""
    _cartItems.update { current ->
      val existingIndex = current.indexOfFirst {
        it.product.id == productToAdd.id && it.variantId == variantId
      }
      if (existingIndex >= 0) {
        current.toMutableList().also { list ->
          val item = list[existingIndex]
          localId = item.id
          list[existingIndex] = item.copy(quantity = item.quantity + quantity)
        }
      } else {
        val newItem = CartItem(
          id = "cart_" + java.util.UUID.randomUUID().toString(),
          product = productToAdd,
          quantity = quantity,
          selectedPalette = palette,
          selectedSize = size,
          variantId = variantId
        )
        localId = newItem.id
        current + newItem
      }
    }
    updateCartCount()
    val userId = SupabaseClient.currentUserId
    if (userId == null) persistGuestCart()
    if (userId != null) scope.launch {
      SupabaseApiService.addToCart(userId, productToAdd.id, quantity, variantId)
        .onSuccess { remoteId ->
          _cartItems.update { rows -> rows.map { if (it.id == localId) it.copy(id = remoteId) else it } }
        }
        .onFailure { _errorMessage.value = it.message }
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
      SupabaseApiService.updateCartQuantity(itemId, quantity).onFailure { _errorMessage.value = it.message }
    }
  }

  fun removeFromCart(itemId: String) {
    _cartItems.update { list -> list.filterNot { it.id == itemId } }
    updateCartCount()
    val userId = SupabaseClient.currentUserId
    if (userId == null) persistGuestCart()
    if (userId != null && !itemId.startsWith("cart_")) scope.launch {
      SupabaseApiService.removeCartItem(itemId).onFailure { _errorMessage.value = it.message }
    }
  }

  fun clearCart() {
    _cartItems.value = emptyList()
    updateCartCount()
    val userId = SupabaseClient.currentUserId
    if (userId == null) persistGuestCart()
    if (userId != null) scope.launch {
      SupabaseApiService.clearCart(userId).onFailure { _errorMessage.value = it.message }
    }
  }

  // ----------------------------------------------------
  // WISHLIST
  // ----------------------------------------------------
  fun toggleWishlist(productId: String) {
    var newWishlistedState = false
    _products.update { list ->
      list.map { prod ->
        if (prod.id == productId) {
          val updated = !prod.isWishlisted
          newWishlistedState = updated
          prod.copy(isWishlisted = updated)
        } else {
          prod
        }
      }
    }
    if (_selectedProduct.value?.id == productId) {
      _selectedProduct.update { it?.copy(isWishlisted = newWishlistedState) }
    }

    val userId = SupabaseClient.currentUserId
    if (userId == null) persistGuestWishlist()
    if (userId != null) {
      scope.launch {
        val result = if (newWishlistedState) {
          SupabaseApiService.addToWishlist(userId, productId)
        } else {
          SupabaseApiService.removeFromWishlist(userId, productId).map { "removed" }
        }
        result.onFailure { error ->
          // Roll back the optimistic UI update when Supabase rejects the change.
          _products.update { list ->
            list.map { product ->
              if (product.id == productId) product.copy(isWishlisted = !newWishlistedState) else product
            }
          }
          _selectedProduct.update { product ->
            if (product?.id == productId) product.copy(isWishlisted = !newWishlistedState) else product
          }
          _errorMessage.value = "Wishlist update failed: ${error.message ?: "Please try again."}"
          Log.e(TAG, "Wishlist update failed for product $productId", error)
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

  fun selectAddress(address: DeliveryAddress) {
    _selectedAddress.value = address
    _currentLocation.value = "${address.area}, ${address.city}"
  }

  fun addOrUpdateAddress(newAddress: DeliveryAddress) {
    _addresses.update { list ->
      val index = list.indexOfFirst { it.id == newAddress.id }
      if (index >= 0) {
        list.toMutableList().also { it[index] = newAddress }
      } else {
        list + newAddress
      }
    }
    _selectedAddress.value = newAddress
    _currentLocation.value = "${newAddress.area}, ${newAddress.city}"

    val userId = SupabaseClient.currentUserId
    if (userId != null) {
      scope.launch {
        SupabaseApiService.saveAddress(
          SupabaseAddressDto(
            id = if (newAddress.id.startsWith("addr_")) "" else newAddress.id,
            userId = userId,
            name = newAddress.name,
            phone = newAddress.phone,
            addressLine = newAddress.addressLine,
            area = newAddress.area,
            city = newAddress.city,
            province = newAddress.province,
            tag = newAddress.tag,
            isDefault = newAddress.isDefault,
            latitude = newAddress.latitude,
            longitude = newAddress.longitude
          )
        ).onSuccess { loadUserData(userId) }.onFailure { _errorMessage.value = it.message }
      }
    }
  }

  fun deleteAddress(addressId: String) {
    _addresses.update { list -> list.filterNot { it.id == addressId } }
    if (_selectedAddress.value?.id == addressId) _selectedAddress.value = _addresses.value.firstOrNull()
    val userId = SupabaseClient.currentUserId
    if (userId != null && !addressId.startsWith("addr_")) scope.launch {
      SupabaseApiService.deleteAddress(addressId, userId).onFailure { _errorMessage.value = it.message }
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
      _errorMessage.value = e.message
      false
    } finally {
      if (showLoading) _isLoading.value = false
      catalogRefreshMutex.unlock()
    }
  }

  fun refreshLiveCatalog(showLoading: Boolean = true, refreshUserData: Boolean = true) {
    scope.launch {
      refreshLiveCatalogSuspending(showLoading = showLoading, refreshUserData = refreshUserData)
    }
  }

  private suspend fun performLiveCatalogSync(refreshUserData: Boolean) {
    val settings = SupabaseApiService.fetchStoreSettings()
    settings.onSuccess {
      if (_storeSettings.value != it) {
        _storeSettings.value = it
        _freeDeliveryEnabled.value = it.freeDeliveryEnabled
        _additionalDeliveryFee.value = it.additionalDeliveryFee
        _deliveryFee.value = it.effectiveDeliveryFee
        _codEnabled.value = it.codEnabled
        _returnWindowDays.value = it.returnWindowDays
      }
    }.onFailure { _errorMessage.value = it.message }

    // Fetch the same published homepage content used by the website.
    SupabaseApiService.fetchHomepageHeroes()
      .onSuccess { if (_homepageHeroes.value != it) _homepageHeroes.value = it }
      .onFailure { _homepageHeroes.value = emptyList(); Log.w(TAG, "Homepage heroes unavailable: ${it.message}") }
    SupabaseApiService.fetchActivePromotions()
      .onSuccess {
        if (_homepagePromotions.value != it) _homepagePromotions.value = it
        CampaignPopupManager.evaluateLaunchCampaign(it)
      }
      .onFailure { _homepagePromotions.value = emptyList(); Log.w(TAG, "Homepage promotions unavailable: ${it.message}") }

        // 1. Fetch live products from Supabase
        val productResult = SupabaseApiService.fetchProducts(limit = 100)
        if (productResult.isSuccess) {
          val dtoList = productResult.getOrNull().orEmpty()
          val previousWishlist = _products.value.associate { it.id to it.isWishlisted }
          val mappedProducts = dtoList.map { dto ->
            Product(
              id = dto.id,
              sellerId = dto.sellerId,
              title = dto.title,
              vendor = dto.vendor,
              category = dto.category,
              categoryId = dto.categoryId,
              price = dto.price,
              originalPrice = dto.originalPrice,
              discountPercent = dto.discountPercent,
              rating = dto.rating,
              reviewCount = dto.reviewCount,
              drawableRes = resolveDrawable(dto.category, dto.id),
              imageUrl = dto.imageUrl,
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
              variantIds = dto.variantIds,
              isArtisanCollection = dto.isArtisanCollection,
              isFeatured = dto.isFeatured,
              isNewArrival = dto.isNewArrival,
              isWishlisted = previousWishlist[dto.id] ?: false,
              isAvailable = dto.inStock,
              availabilityKnown = dto.availabilityKnown,
              dailyStatus = dto.dailyStatus,
              foodSellingUnit = dto.foodSellingUnit,
              variantAvailability = dto.variantAvailability
            )
          }
          if (_products.value != mappedProducts) {
            _products.value = mappedProducts
            val selectedProductId = _selectedProduct.value?.id
            _selectedProduct.value = if (selectedProductId == null) mappedProducts.firstOrNull() else mappedProducts.firstOrNull { it.id == selectedProductId }
            _cartItems.update { cart -> cart.map { item -> mappedProducts.firstOrNull { it.id == item.product.id }?.let { latest -> item.copy(product = latest) } ?: item } }
            updateCartCount()
          }
          if (SupabaseClient.currentUserId == null) restoreGuestShoppingData()
          Log.d(TAG, "Loaded ${mappedProducts.size} live products from Supabase")

          // 1b. Fetch active product campaigns (Flash Sale, BOGO, Bundle, Seasonal, Custom)
          val campaignResult = SupabaseApiService.fetchActiveProductCampaigns()
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

            val mappedCampaigns = validCampaigns.mapNotNull { camp ->
              val campaignProductIds = camp.productIds.toSet()
              val matchedProds = mappedProducts.filter { it.id in campaignProductIds }
              if (matchedProds.isEmpty()) return@mapNotNull null

              val enrichedProds = matchedProds.map { prod ->
                // Only percentage-based campaigns may affect the displayed unit price.
                // BOGO and bundle offers are informational here; server checkout remains authoritative.
                val supportsPercentPrice = camp.campaignType in setOf("flash_sale", "seasonal", "custom")
                if (!supportsPercentPrice || camp.discountPercent <= 0.0) {
                  prod
                } else {
                  val campaignPercent = camp.discountPercent.toInt().coerceIn(0, 100)
                  val bestDiscount = maxOf(prod.discountPercent, campaignPercent)
                  val discountedPrice = (prod.originalPrice * (100 - bestDiscount) / 100.0).toInt()
                  prod.copy(
                    discountPercent = bestDiscount,
                    price = discountedPrice,
                    badge = "${bestDiscount}% OFF"
                  )
                }
              }
              ActiveProductCampaign(campaign = camp, products = enrichedProds)
            }
            _activeProductCampaigns.value = mappedCampaigns
            Log.d(TAG, "Loaded ${mappedCampaigns.size} active product campaigns from Supabase")
          } else {
            _activeProductCampaigns.value = emptyList()
          }
        } else {
          _activeProductCampaigns.value = emptyList()
          _errorMessage.value = productResult.exceptionOrNull()?.message
          Log.w(TAG, "Catalog refresh failed; retaining last successful product data")
        }

        // 2. Fetch live categories from Supabase
        val categoryResult = SupabaseApiService.fetchCategories()
        if (categoryResult.isSuccess) {
          val catDtoList = categoryResult.getOrNull().orEmpty()
          val mappedCategories = catDtoList.map { dto ->
            CategoryItem(
              id = dto.id,
              title = dto.title,
              countText = dto.countText,
              sampleItems = dto.sampleItems,
              drawableRes = resolveCategoryDrawable(dto.title),
              imageUrl = dto.imageUrl,
              badge = dto.badge
            )
          }
          _categories.value = mappedCategories
          Log.d(TAG, "Loaded ${mappedCategories.size} categories from Supabase")
        } else {
          _categories.value = emptyList()
          _errorMessage.value = categoryResult.exceptionOrNull()?.message
        }
        refreshShops()
        if (refreshUserData) SupabaseClient.currentUserId?.let { loadUserData(it) }
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
    val phone = address.phone.trim()
    if (!Regex("^(03[0-9]{9}|\\+923[0-9]{9})$").matches(phone)) {
      onComplete?.invoke(Result.failure(IllegalArgumentException("Enter exactly 03001234567 or +923001234567.")))
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
        val latestProducts = SupabaseApiService.fetchProducts(limit = 100).getOrThrow().associateBy { it.id }
        val unavailable = items.firstOrNull { item ->
          val latest = latestProducts[item.product.id]
          latest == null || !latest.availabilityKnown || !latest.inStock ||
            (latest.foodSellingUnit != null && latest.dailyStatus != null && latest.dailyStatus != "available") ||
            (latest.variantIds.isNotEmpty() && item.variantId.isNullOrBlank()) ||
            (!item.variantId.isNullOrBlank() && latest.variantAvailability[item.variantId] != true)
        }
        if (unavailable != null) throw IllegalStateException(unavailable.product.title + " is out of stock or unavailable. Remove it from your cart before checkout.")
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
          // Use the server-calculated order-item unit price (including campaign pricing),
          // not the pre-checkout catalog price, in the confirmation/receipt.
          val sourceProduct = items.firstOrNull { it.product.id == line.productId }?.product
          val product = sourceProduct?.copy(
            price = line.unitPrice,
            discountPercent = if (sourceProduct.originalPrice > 0)
              (((sourceProduct.originalPrice - line.unitPrice) * 100) / sourceProduct.originalPrice).coerceIn(0, 100)
            else sourceProduct.discountPercent
          ) ?: Product(id=line.productId, title=line.productTitle, vendor="", category="", price=line.unitPrice, originalPrice=line.unitPrice, discountPercent=0, rating=0.0, reviewCount=0)
          CartItem(id=line.id, product=product, quantity=line.quantity, selectedPalette=line.selectedPalette, selectedSize=line.selectedSize, variantId=line.variantId)
        }
        val record = OrderRecord(orderId=saved.id, orderNumber=saved.orderNumber, date=saved.createdAt, items=savedItems, subtotal=saved.subtotal, discount=saved.discount, deliveryFee=saved.deliveryFee, grandTotal=saved.grandTotal, address=DeliveryAddress(id="order-address", name=saved.addressName, phone=saved.addressPhone, addressLine=saved.addressLine, area=saved.addressArea, city=saved.addressCity, province=address.province, isDefault=false), deliveryNote=saved.deliveryNote ?: "", statusHeader=saved.orderStatus, orderStatus=saved.orderStatus)
        _activeOrder.value = record
        _orders.update { listOf(record) + it.filterNot { old -> old.orderId == record.orderId } }
        clearCart()
        loadUserData(userId)
        onComplete?.invoke(Result.success(record))
      } catch (e: Exception) {
        Log.e(TAG, "Secure order placement failed", e)
        onComplete?.invoke(Result.failure(e))
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
    scope.launch {
      val result = SupabaseApiService.cancelOrder(orderId)
      if (result.isSuccess && result.getOrNull() == true) {
        SupabaseClient.currentUserId?.let { loadUserData(it) }
        onComplete?.invoke(true)
      } else {
        _errorMessage.value = result.exceptionOrNull()?.message ?: "Order cancellation was not accepted."
        onComplete?.invoke(false)
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
        .onFailure { _errorMessage.value = it.message }
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

  fun refreshNotifications(userId: String? = null) {
    val targetUid = userId ?: SupabaseClient.currentUserId ?: return
    scope.launch {
      SupabaseApiService.fetchNotifications(targetUid).onSuccess { list ->
        _notifications.value = list
      }
    }
  }

  // ----------------------------------------------------
  // USER SPECIFIC DATA SYNC
  // ----------------------------------------------------
  fun loadUserData(userId: String) {
    scope.launch {
      _cartItems.value = emptyList()
      _cartCount.value = 0
      _addresses.value = emptyList()
      _selectedAddress.value = null
      _orders.value = emptyList()
      _activeOrder.value = null
      _notifications.value = emptyList()
      try {
        // Merge anonymous shopping choices into the account before loading remote state.
        mergeGuestShoppingData(userId)
        val cartRes = SupabaseApiService.fetchCart(userId)
        if (cartRes.isSuccess) {
          val rows = JSONArray(cartRes.getOrNull() ?: "[]")
          val mappedCart = (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val productId = row.optString("product_id")
            val product = _products.value.firstOrNull { it.id == productId } ?: return@mapNotNull null
            // JSONObject.optString() turns JSON null into the literal string "null".
            // Never pass that text as a UUID to place_order.
            val variantId = row.optString("variant_id")
              .takeIf { !row.isNull("variant_id") && it.isNotBlank() && it != "null" }
            val selectedOption = product.variantIds.entries.firstOrNull { it.value == variantId }?.key
            CartItem(
              id = row.optString("id"), product = product, quantity = row.optInt("quantity", 1),
              selectedPalette = selectedOption?.takeIf { it.startsWith("color:") }?.removePrefix("color:"),
              selectedSize = selectedOption?.takeIf { it.startsWith("size:") }?.removePrefix("size:"),
              variantId = variantId
            )
          }
          _cartItems.value = mappedCart
          updateCartCount()
        }
        val wishlistRes = SupabaseApiService.fetchWishlist(userId)
        if (wishlistRes.isSuccess) {
          val rows = JSONArray(wishlistRes.getOrNull() ?: "[]")
          val ids = (0 until rows.length()).mapNotNull { rows.optJSONObject(it)?.optString("product_id") }.toSet()
          _products.update { list -> list.map { it.copy(isWishlisted = ids.contains(it.id)) } }
        }

        // 1. Load addresses
        val addrRes = SupabaseApiService.fetchAddresses(userId)
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
              isDefault = dto.isDefault
            )
          }
          _addresses.value = addrList
          _selectedAddress.value = addrList.firstOrNull { it.isDefault } ?: addrList.firstOrNull()
          _selectedAddress.value?.let { _currentLocation.value = "${it.area}, ${it.city}" }
        }

        // 2. Load orders
        val ordersRes = SupabaseApiService.fetchOrders(userId)
        if (ordersRes.isSuccess) {
          val orderDtoList = ordersRes.getOrNull().orEmpty()
          val mappedOrders = orderDtoList.map { dto ->
            OrderRecord(
              orderId = dto.id,
              orderNumber = dto.orderNumber,
              date = dto.createdAt,
              items = SupabaseApiService.fetchOrderItems(dto.id).getOrNull().orEmpty().map { line ->
                val product = Product(id=line.productId, title=line.productTitle, vendor="", category="", price=line.unitPrice, originalPrice=line.unitPrice, discountPercent=0, rating=0.0, reviewCount=0, imageUrl=line.imageUrl)
                CartItem(id=line.id, product=product, quantity=line.quantity, selectedPalette=line.selectedPalette, selectedSize=line.selectedSize)
              },
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
              statusHeader = when (dto.orderStatus) {
                "PENDING" -> "Order Placed • Cash on Delivery"
                "PROCESSING" -> "Vendor Coordination & QC"
                "SHIPPED" -> "Out for Delivery"
                "DELIVERED" -> "Delivered"
                "CANCELLED" -> "Cancelled"
                else -> dto.orderStatus
              },
              orderStatus = dto.orderStatus,
              timeline = listOf(
                OrderTimelineStep(
                  title = "Order status: " + dto.orderStatus,
                  timestamp = dto.createdAt,
                  description = "Latest order status saved by JTBazar.",
                  state = StepState.COMPLETED
                )
              )
            )
          }
          _orders.value = mappedOrders
          _activeOrder.value = mappedOrders.firstOrNull { it.orderStatus != "DELIVERED" && it.orderStatus != "CANCELLED" } ?: mappedOrders.firstOrNull()
        }

        // 3. Load notifications
        val notifRes = SupabaseApiService.fetchNotifications(userId)
        if (notifRes.isSuccess) {
          _notifications.value = notifRes.getOrNull().orEmpty()
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error loading user data for $userId", e)
      }
    }
  }

  fun clearUserData() {
    _addresses.value = emptyList()
    _selectedAddress.value = null
    _orders.value = emptyList()
    _activeOrder.value = null
    _cartItems.value = emptyList()
    _cartCount.value = 0
    _notifications.value = emptyList()
    _currentLocation.value = "Select delivery address"
  }

  // ----------------------------------------------------
  // DRAWABLE ASSET RESOLUTION (FALLBACK FOR REMOTE IMAGES)
  // ----------------------------------------------------
  private fun resolveDrawable(category: String, id: String): Int {
    return R.drawable.ic_launcher_background
    /* return when {
      id.contains("biryani", ignoreCase = true) -> R.drawable.item_biryani
      id.contains("honey", ignoreCase = true) -> R.drawable.item_honey
      id.contains("leather", ignoreCase = true) -> R.drawable.item_leather_bag
      id.contains("plate", ignoreCase = true) -> R.drawable.item_ceramic_plate
      id.contains("bowl", ignoreCase = true) -> R.drawable.item_plate_thumb
      category.contains("food", ignoreCase = true) -> R.drawable.item_biryani
      category.contains("living", ignoreCase = true) -> R.drawable.item_ceramic_plate
      category.contains("bag", ignoreCase = true) -> R.drawable.item_leather_bag
      else -> R.drawable.item_ceramic_plate
    } */
  }

  private fun resolveCategoryDrawable(title: String): Int = R.drawable.ic_launcher_background

  init {
    updateCartCount()

    // Observe customer authentication session
    scope.launch {
      SupabaseClient.session.collect { session ->
        if (session != null) {
          loadUserData(session.user.id)
        } else {
          clearUserData()
        }
      }
    }

    // Asynchronously fetch live catalog from Supabase
    refreshLiveCatalog()
  }
}
