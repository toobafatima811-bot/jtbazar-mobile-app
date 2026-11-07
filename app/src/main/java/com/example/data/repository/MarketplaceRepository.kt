package com.example.data.repository

import android.util.Log
import com.example.R
import com.example.data.model.*
import com.example.data.supabase.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Central Data Repository for JTBazar.
 * Strictly connects to real Supabase backend with ZERO hardcoded dummy/sample products,
 * dummy orders, or sample addresses.
 */
object MarketplaceRepository {
  private const val TAG = "MarketplaceRepo"
  private val scope = CoroutineScope(Dispatchers.Default)

  // Loading and error states
  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _isPlacingOrder = MutableStateFlow(false)
  val isPlacingOrder: StateFlow<Boolean> = _isPlacingOrder.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  // Countdown seconds for daily promotion
  private val _countdownSeconds = MutableStateFlow(4 * 3600 + 18 * 60 + 18)
  val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

  // Selected Location (Default to Lahore, updated when user picks an address)
  private val _currentLocation = MutableStateFlow("Lahore, Pakistan")
  val currentLocation: StateFlow<String> = _currentLocation.asStateFlow()

  fun setLocation(loc: String) {
    _currentLocation.value = loc
  }

  // ----------------------------------------------------
  // PRODUCTS STATE (REAL SUPABASE CATALOG ONLY)
  // ----------------------------------------------------
  private val _products = MutableStateFlow<List<Product>>(emptyList())
  val products: StateFlow<List<Product>> = _products.asStateFlow()

  private val _selectedProduct = MutableStateFlow<Product?>(null)
  val selectedProduct: StateFlow<Product?> = _selectedProduct.asStateFlow()

  fun selectProduct(product: Product) {
    _selectedProduct.value = product
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

  fun addToCart(product: Product, quantity: Int = 1, palette: String? = null, size: String? = null) {
    _cartItems.update { current ->
      val existingIndex = current.indexOfFirst {
        it.product.id == product.id && it.selectedPalette == palette && it.selectedSize == size
      }
      if (existingIndex >= 0) {
        current.toMutableList().also { list ->
          val item = list[existingIndex]
          list[existingIndex] = item.copy(quantity = item.quantity + quantity)
        }
      } else {
        current + CartItem(
          id = "cart_${System.currentTimeMillis()}",
          product = product,
          quantity = quantity,
          selectedPalette = palette,
          selectedSize = size
        )
      }
    }
    updateCartCount()

    // Sync to Supabase if customer is authenticated
    val userId = SupabaseClient.currentUserId
    if (userId != null) {
      scope.launch {
        SupabaseApiService.addToCart(userId, product.id, quantity, palette, size)
      }
    }
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
    if (userId != null) {
      scope.launch {
        SupabaseApiService.updateCartQuantity(itemId, quantity)
      }
    }
  }

  fun removeFromCart(itemId: String) {
    _cartItems.update { list -> list.filterNot { it.id == itemId } }
    updateCartCount()

    val userId = SupabaseClient.currentUserId
    if (userId != null) {
      scope.launch {
        SupabaseApiService.removeCartItem(itemId)
      }
    }
  }

  fun clearCart() {
    _cartItems.value = emptyList()
    updateCartCount()

    val userId = SupabaseClient.currentUserId
    if (userId != null) {
      scope.launch {
        SupabaseApiService.clearCart(userId)
      }
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
    if (userId != null) {
      scope.launch {
        if (newWishlistedState) {
          SupabaseApiService.addToWishlist(userId, productId)
        } else {
          SupabaseApiService.removeFromWishlist(userId, productId)
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
            isDefault = newAddress.isDefault
          )
        )
      }
    }
  }

  fun deleteAddress(addressId: String) {
    _addresses.update { list -> list.filterNot { it.id == addressId } }
    if (_selectedAddress.value?.id == addressId) {
      _selectedAddress.value = _addresses.value.firstOrNull()
    }
  }

  // ----------------------------------------------------
  // ORDERS (AUTHENTICATED CUSTOMER ORDERS ONLY)
  // ----------------------------------------------------
  private val _orders = MutableStateFlow<List<OrderRecord>>(emptyList())
  val orders: StateFlow<List<OrderRecord>> = _orders.asStateFlow()

  private val _activeOrder = MutableStateFlow<OrderRecord?>(null)
  val activeOrder: StateFlow<OrderRecord?> = _activeOrder.asStateFlow()

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
  fun refreshLiveCatalog() {
    scope.launch {
      _isLoading.value = true
      _errorMessage.value = null

      try {
        // 1. Fetch live products from Supabase
        val productResult = SupabaseApiService.fetchProducts(limit = 100)
        if (productResult.isSuccess) {
          val dtoList = productResult.getOrNull().orEmpty()
          val mappedProducts = dtoList.map { dto ->
            Product(
              id = dto.id,
              title = dto.title,
              vendor = dto.vendor,
              category = dto.category,
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
              isArtisanCollection = dto.isArtisanCollection,
              isWishlisted = false
            )
          }
          _products.value = mappedProducts
          if (_selectedProduct.value == null && mappedProducts.isNotEmpty()) {
            _selectedProduct.value = mappedProducts.first()
          }
          Log.d(TAG, "Loaded ${mappedProducts.size} live products from Supabase")
        } else {
          _errorMessage.value = productResult.exceptionOrNull()?.message
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
        }
      } catch (e: Exception) {
        Log.e(TAG, "Backend sync error: ${e.message}", e)
        _errorMessage.value = e.message
      } finally {
        _isLoading.value = false
      }
    }
  }

  // ----------------------------------------------------
  // ORDER PLACEMENT (CENTRALIZED JTBAZAR COD MODEL)
  // ----------------------------------------------------
  fun placeCurrentOrder(
    deliveryNote: String = "",
    onComplete: ((Result<OrderRecord>) -> Unit)? = null
  ): OrderRecord? {
    val items = _cartItems.value
    if (items.isEmpty()) {
      onComplete?.invoke(Result.failure(IllegalStateException("Shopping cart is empty")))
      return null
    }

    val address = _selectedAddress.value
    if (address == null) {
      onComplete?.invoke(Result.failure(IllegalStateException("Delivery address is required")))
      return null
    }

    if (_isPlacingOrder.value) {
      // Prevent duplicate submissions
      return null
    }

    _isPlacingOrder.value = true

    // Server-grade price calculation based on cart items
    val subtotal = items.sumOf { it.totalPrice }
    val deliveryFee = if (subtotal >= 1999) 0 else 150 // Free delivery above Rs. 1,999
    val discount = 0
    val grandTotal = (subtotal - discount + deliveryFee).coerceAtLeast(0)

    val randomNum = (10000..99999).random()
    val newOrderNumber = "JT-$randomNum-PK"

    val newOrder = OrderRecord(
      orderId = newOrderNumber,
      date = "Today, Just Now",
      items = items,
      subtotal = subtotal,
      discount = discount,
      deliveryFee = deliveryFee,
      grandTotal = grandTotal,
      address = address,
      deliveryNote = deliveryNote,
      statusHeader = "Order Placed • Cash on Delivery",
      orderStatus = "PENDING",
      timeline = listOf(
        OrderTimelineStep(
          title = "Order Placed",
          timestamp = "Just Now",
          description = "Order registered with JTBazar centralized hub. Cash on Delivery payable: PKR ${String.format("%,d", grandTotal)}.",
          state = StepState.COMPLETED
        ),
        OrderTimelineStep(
          title = "Vendor Coordination & Collection",
          timestamp = "In Progress",
          description = "JTBazar team verifying items with vendors and collecting package.",
          state = StepState.IN_PROGRESS
        ),
        OrderTimelineStep(
          title = "JTBazar Central QC & Sealing",
          timestamp = "Pending",
          description = "Central quality check and sealing with JTBazar tamper-evident security tape.",
          state = StepState.PENDING
        ),
        OrderTimelineStep(
          title = "Out for Delivery & COD Collection",
          timestamp = "Estimated 2–3 Days",
          description = "Rider delivering to ${address.area}, ${address.city} with Cash on Delivery payment.",
          state = StepState.PENDING
        ),
        OrderTimelineStep(
          title = "Delivered",
          timestamp = "Pending Delivery",
          description = "Package handed over with digital acknowledgment (7-day return period starts upon delivery).",
          state = StepState.PENDING
        )
      )
    )

    val userId = SupabaseClient.currentUserId ?: "guest_customer"

    scope.launch {
      try {
        val orderDto = SupabaseOrderDto(
          id = "",
          orderNumber = newOrderNumber,
          userId = userId,
          subtotal = subtotal,
          discount = discount,
          deliveryFee = deliveryFee,
          grandTotal = grandTotal,
          paymentMethod = "COD",
          paymentStatus = "PENDING",
          orderStatus = "PENDING",
          deliveryNote = deliveryNote,
          addressName = address.name,
          addressPhone = address.phone,
          addressLine = address.addressLine,
          addressArea = address.area,
          addressCity = address.city,
          createdAt = System.currentTimeMillis().toString()
        )

        val orderItemsDto = items.map { item ->
          SupabaseOrderItemDto(
            id = "",
            orderId = newOrderNumber,
            productId = item.product.id,
            productTitle = item.product.title,
            unitPrice = item.product.price,
            quantity = item.quantity,
            totalPrice = item.totalPrice,
            selectedPalette = item.selectedPalette,
            selectedSize = item.selectedSize
          )
        }

        val createRes = SupabaseApiService.createOrder(orderDto, orderItemsDto)
        if (createRes.isSuccess) {
          _activeOrder.value = newOrder
          _orders.update { listOf(newOrder) + it }

          // Clear cart only after verified order creation
          clearCart()

          // Add real confirmation notification
          val confirmationNotif = SupabaseNotificationDto(
            id = "notif_${System.currentTimeMillis()}",
            userId = userId,
            title = "Order $newOrderNumber Placed!",
            message = "Total: PKR ${String.format("%,d", grandTotal)} via Cash on Delivery. JTBazar is coordinating dispatch.",
            type = "ORDER",
            isRead = false,
            createdAt = "Just now"
          )
          _notifications.update { listOf(confirmationNotif) + it }

          onComplete?.invoke(Result.success(newOrder))
        } else {
          onComplete?.invoke(Result.failure(createRes.exceptionOrNull() ?: Exception("Failed to create order on server")))
        }
      } catch (e: Exception) {
        Log.e(TAG, "Order creation error", e)
        onComplete?.invoke(Result.failure(e))
      } finally {
        _isPlacingOrder.value = false
      }
    }

    return newOrder
  }

  fun cancelOrder(orderId: String, onComplete: ((Boolean) -> Unit)? = null): Boolean {
    val existing = _orders.value.find { it.orderId == orderId }
    if (existing != null && existing.orderStatus == "PENDING") {
      val cancelled = existing.copy(
        orderStatus = "CANCELLED",
        statusHeader = "Order Cancelled by Customer",
        timeline = existing.timeline.map {
          if (it.state == StepState.IN_PROGRESS) it.copy(state = StepState.PENDING, description = "Cancelled by customer") else it
        }
      )
      _orders.update { list -> list.map { if (it.orderId == orderId) cancelled else it } }
      if (_activeOrder.value?.orderId == orderId) {
        _activeOrder.value = cancelled
      }

      scope.launch {
        val res = SupabaseApiService.cancelOrder(orderId)
        onComplete?.invoke(res.isSuccess)
      }
      return true
    }
    onComplete?.invoke(false)
    return false
  }

  // ----------------------------------------------------
  // REVIEWS & CASES
  // ----------------------------------------------------
  fun submitProductReview(productId: String, rating: Int, title: String, comment: String) {
    val userId = SupabaseClient.currentUserId ?: "cust_user"
    val userName = SupabaseClient.currentUser.value?.fullName ?: "Verified Buyer"
    val review = SupabaseReviewDto(
      id = "rev_${System.currentTimeMillis()}",
      productId = productId,
      userId = userId,
      userName = userName,
      rating = rating,
      title = title,
      comment = comment,
      createdAt = "Just now"
    )
    _reviews.update { listOf(review) + it }
    scope.launch {
      SupabaseApiService.submitProductReview(review)
    }
  }

  fun submitReturnRequest(orderId: String, reason: String, description: String) {
    val userId = SupabaseClient.currentUserId ?: "cust_user"
    val req = SupabaseReturnRequestDto(
      id = "ret_${System.currentTimeMillis()}",
      orderId = orderId,
      userId = userId,
      reason = reason,
      description = description,
      status = "PENDING",
      createdAt = "Just now"
    )
    scope.launch {
      SupabaseApiService.submitReturnRequest(req)
    }
  }

  fun submitComplaint(orderId: String?, subject: String, message: String) {
    val userId = SupabaseClient.currentUserId ?: "cust_user"
    val complaint = SupabaseComplaintDto(
      id = "comp_${System.currentTimeMillis()}",
      orderId = orderId,
      userId = userId,
      subject = subject,
      message = message,
      status = "OPEN",
      createdAt = "Just now"
    )
    scope.launch {
      SupabaseApiService.submitComplaint(complaint)
    }
  }

  // ----------------------------------------------------
  // USER SPECIFIC DATA SYNC
  // ----------------------------------------------------
  fun loadUserData(userId: String) {
    scope.launch {
      try {
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
              orderId = dto.orderNumber,
              date = dto.createdAt,
              items = emptyList(),
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
                province = "Punjab"
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
                  title = "Order Placed",
                  timestamp = "Registered",
                  description = "Order registered with JTBazar centralized hub.",
                  state = StepState.COMPLETED
                ),
                OrderTimelineStep(
                  title = "Central Delivery & Dispatch",
                  timestamp = "JTBazar Fleet",
                  description = "Centralized delivery team processing package.",
                  state = if (dto.orderStatus == "PENDING") StepState.IN_PROGRESS else StepState.COMPLETED
                ),
                OrderTimelineStep(
                  title = "Delivered & COD Collected",
                  timestamp = "Doorstep",
                  description = "Cash on Delivery payment received upon handover.",
                  state = if (dto.orderStatus == "DELIVERED") StepState.COMPLETED else StepState.PENDING
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
    _currentLocation.value = "Lahore, Pakistan"
  }

  // ----------------------------------------------------
  // DRAWABLE ASSET RESOLUTION (FALLBACK FOR REMOTE IMAGES)
  // ----------------------------------------------------
  private fun resolveDrawable(category: String, id: String): Int {
    return when {
      id.contains("biryani", ignoreCase = true) -> R.drawable.item_biryani
      id.contains("honey", ignoreCase = true) -> R.drawable.item_honey
      id.contains("leather", ignoreCase = true) -> R.drawable.item_leather_bag
      id.contains("plate", ignoreCase = true) -> R.drawable.item_ceramic_plate
      id.contains("bowl", ignoreCase = true) -> R.drawable.item_plate_thumb
      category.contains("food", ignoreCase = true) -> R.drawable.item_biryani
      category.contains("living", ignoreCase = true) -> R.drawable.item_ceramic_plate
      category.contains("bag", ignoreCase = true) -> R.drawable.item_leather_bag
      else -> R.drawable.item_ceramic_plate
    }
  }

  private fun resolveCategoryDrawable(title: String): Int {
    return when {
      title.contains("groceries", ignoreCase = true) -> R.drawable.cat_groceries
      title.contains("homemade", ignoreCase = true) -> R.drawable.cat_homemade
      title.contains("fast", ignoreCase = true) -> R.drawable.cat_fastfood
      title.contains("bag", ignoreCase = true) -> R.drawable.cat_bags
      title.contains("living", ignoreCase = true) -> R.drawable.cat_homeliving
      title.contains("kitchen", ignoreCase = true) -> R.drawable.cat_kitchen
      title.contains("stationery", ignoreCase = true) -> R.drawable.cat_stationery
      title.contains("wellness", ignoreCase = true) -> R.drawable.cat_wellness
      title.contains("lighting", ignoreCase = true) -> R.drawable.cat_lighting
      title.contains("artisan", ignoreCase = true) -> R.drawable.cat_artisan
      else -> R.drawable.cat_groceries
    }
  }

  init {
    updateCartCount()

    // Countdown timer ticker
    scope.launch {
      while (true) {
        delay(1000)
        _countdownSeconds.update { curr ->
          if (curr > 0) curr - 1 else 4 * 3600 + 18 * 60 + 18
        }
      }
    }

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
