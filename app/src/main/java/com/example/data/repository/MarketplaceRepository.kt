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

object MarketplaceRepository {
  private const val TAG = "MarketplaceRepo"
  private val scope = CoroutineScope(Dispatchers.Default)

  // Loading and error states
  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  // Countdown seconds for "Popular in Lahore - Ends in 04:18:18"
  private val _countdownSeconds = MutableStateFlow(4 * 3600 + 18 * 60 + 18)
  val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

  // Selected Location
  private val _currentLocation = MutableStateFlow("Gulberg III, Lahore")
  val currentLocation: StateFlow<String> = _currentLocation.asStateFlow()

  // Available Locations for quick switching
  val availableLocations = listOf(
    "Gulberg III, Lahore",
    "DHA Phase 5, Lahore",
    "Model Town, Lahore",
    "Johar Town, Lahore",
    "Cantt, Lahore"
  )

  fun setLocation(loc: String) {
    _currentLocation.value = loc
  }

  // Delivery Addresses
  private val _addresses = MutableStateFlow(
    listOf(
      DeliveryAddress(
        id = "addr_1",
        name = "Muhammad Usman",
        phone = "+92 300 4821902",
        addressLine = "House # 42-B, Street 7",
        area = "Gulberg III",
        city = "Lahore",
        province = "Punjab",
        tag = "HOME",
        isDefault = true
      ),
      DeliveryAddress(
        id = "addr_2",
        name = "Muhammad Usman",
        phone = "+92 300 4821902",
        addressLine = "Office 304, Mall One, Main Boulevard",
        area = "Gulberg III",
        city = "Lahore",
        province = "Punjab",
        tag = "OFFICE",
        isDefault = false
      )
    )
  )
  val addresses: StateFlow<List<DeliveryAddress>> = _addresses.asStateFlow()

  private val _selectedAddress = MutableStateFlow(_addresses.value.first())
  val selectedAddress: StateFlow<DeliveryAddress> = _selectedAddress.asStateFlow()

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

    // Persist to Supabase if authenticated
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
    if (_selectedAddress.value.id == addressId && _addresses.value.isNotEmpty()) {
      _selectedAddress.value = _addresses.value.first()
    }
  }

  // Base sample catalog with rich Pakistani descriptions & visual assets
  val sampleProducts = listOf(
    Product(
      id = "prod_multani_plate",
      title = "Multani Artisan Hand-Glazed Ceramic Dinner Plate (Set of 2)",
      vendor = "Multan Craft Heritage",
      category = "Home & Living",
      price = 2450,
      originalPrice = 2990,
      discountPercent = 18,
      rating = 4.9,
      reviewCount = 128,
      drawableRes = R.drawable.item_ceramic_plate,
      badge = "18% OFF",
      inStockText = "In Stock (14 sets left)",
      savingsText = "You save Rs. 540 on this handcraft festival deal",
      vendorVerified = true,
      vendorRating = "98% Positive Seller",
      vendorSales = "4.2k Sales",
      vendorAvatarText = "MC",
      deliveryText = "Delivery to Lahore in 2–3 business days",
      paymentInfo = "Cash on Delivery (COD) & Online Bank Transfer Supported",
      craftStory = "Centuries of traditional South Asian artisan heritage brought straight to your dining table. Each plate is turned on hand-powered potter wheels in Multan and hand-painted with ancient floral patterns known as \"Kashigari\".",
      highlights = listOf(
        "100% Food-Grade, Lead-Free & Cadmium-Free Glazes",
        "Microwave & Dishwasher Safe (Double-Fired Stoneware)",
        "Handmade slight variations signify authentic craft signature"
      ),
      palettes = listOf(
        PaletteOption("Royal Cobalt Blue", 0xFF1E40AF, isSelected = true),
        PaletteOption("Turquoise", 0xFF0D9488, isSelected = false),
        PaletteOption("Terracotta", 0xFFC2410C, isSelected = false)
      ),
      sizes = listOf(
        SizeOption("10.5\" Dinner Plate", "Standard Full Meal • Set of 2", isSelected = true),
        SizeOption("8.5\" Side / Dessert Plate", "Appetizer Plate • Set of 2", isSelected = false)
      ),
      isArtisanCollection = true,
      isWishlisted = false
    ),
    Product(
      id = "prod_shan_biryani",
      title = "Shan Shahi Biryani & Recipe Mix Bundle",
      vendor = "Al-Madina Mart & Foods",
      category = "Food & Groceries",
      price = 380,
      originalPrice = 440,
      discountPercent = 14,
      rating = 4.8,
      reviewCount = 1200,
      drawableRes = R.drawable.item_biryani,
      badge = "14% OFF",
      inStockText = "In Stock",
      vendorVerified = true,
      vendorRating = "99% Positive Seller",
      vendorSales = "15k Sales",
      vendorAvatarText = "AM",
      highlights = listOf(
        "Authentic Karachi-style aromatic spices and saffron aroma",
        "Pack of 3 individual recipe mix sachets with seal freshness",
        "No artificial preservatives or added synthetic colors"
      ),
      craftStory = "Packed with pure ground aromatic spices, dried plums, cardamoms, and whole condiments for restaurant-quality Biryani at home.",
      palettes = emptyList(),
      sizes = listOf(
        SizeOption("Pack of 3 (150g)", "Regular Family Pack", isSelected = true),
        SizeOption("Mega Family Pack (6 Sachets)", "Bulk Savings Pack", isSelected = false)
      )
    ),
    Product(
      id = "prod_leather_bag",
      title = "Premium Handcrafted Leather Crossbody Bag",
      vendor = "Karigar Leather",
      category = "Bags & Accessories",
      price = 2650,
      originalPrice = 3200,
      discountPercent = 17,
      rating = 4.9,
      reviewCount = 430,
      drawableRes = R.drawable.item_leather_bag,
      badge = "17% OFF",
      inStockText = "Only 5 Left",
      vendorVerified = true,
      vendorRating = "97% Positive Seller",
      vendorSales = "2.8k Sales",
      vendorAvatarText = "KL",
      highlights = listOf(
        "100% Genuine Full-Grain Buff Leather with natural patina",
        "Double-stitched brass buckles with heavy duty nylon thread",
        "Padded laptop/tablet compartment and quick magnetic latch"
      ),
      craftStory = "Handcrafted by hereditary master tanners in Sialkot, oiled with pure beeswax to endure everyday use and develop a deep vintage sheen over time.",
      palettes = listOf(
        PaletteOption("Vintage Tan", 0xFF854D0E, isSelected = true),
        PaletteOption("Deep Espresso", 0xFF3E2723, isSelected = false)
      )
    ),
    Product(
      id = "prod_pure_honey",
      title = "Pure Honey Blossom Nectar 500g",
      vendor = "Al-Madina Mart & Foods",
      category = "Food & Groceries",
      price = 950,
      originalPrice = 1150,
      discountPercent = 17,
      rating = 4.7,
      reviewCount = 89,
      drawableRes = R.drawable.item_honey,
      badge = "17% OFF",
      inStockText = "Verified Food Seller",
      vendorVerified = true,
      vendorRating = "99% Positive Seller",
      vendorSales = "15k Sales",
      vendorAvatarText = "AM",
      highlights = listOf(
        "100% Raw, Unfiltered Berry (Sidr) Blossom Honey",
        "Directly harvested from Margalla foothills bee farms",
        "Rich in natural antioxidants, zero corn syrup or adulterants"
      ),
      craftStory = "Pure unheated Sidr honey bottled fresh in sanitized glass jars with wooden honey dipper included.",
      palettes = emptyList(),
      sizes = listOf(
        SizeOption("500g Glass Jar", "Standard Kitchen Jar", isSelected = true),
        SizeOption("1kg Value Bottle", "Double Weight Saver", isSelected = false)
      )
    ),
    Product(
      id = "prod_multani_bowl",
      title = "Multani Clay Hand-painted Ceramic Dinner Bowl",
      vendor = "Multan Craft Heritage",
      category = "Home & Living",
      price = 1450,
      originalPrice = 1800,
      discountPercent = 19,
      rating = 4.9,
      reviewCount = 210,
      drawableRes = R.drawable.item_plate_thumb,
      badge = "19% OFF",
      inStockText = "In Stock",
      vendorVerified = true,
      vendorRating = "98% Positive Seller",
      vendorSales = "4.2k Sales",
      vendorAvatarText = "MC",
      highlights = listOf(
        "Classic Multan Blue Pottery serving bowl",
        "Lead-free food-grade glaze suitable for curries and soups",
        "Hand-painted floral motifs inspired by Shah Rukn-e-Alam architecture"
      ),
      craftStory = "Double-baked in high temperature wood kilns for exceptional durability and chip-resistance.",
      isArtisanCollection = true
    )
  )

  // Products State
  private val _products = MutableStateFlow(sampleProducts)
  val products: StateFlow<List<Product>> = _products.asStateFlow()

  // Selected Product for Details
  private val _selectedProduct = MutableStateFlow(sampleProducts[0])
  val selectedProduct: StateFlow<Product> = _selectedProduct.asStateFlow()

  fun selectProduct(product: Product) {
    _selectedProduct.value = product
  }

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
    if (_selectedProduct.value.id == productId) {
      _selectedProduct.update { it.copy(isWishlisted = newWishlistedState) }
    }

    // Backend sync
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

  // Cart
  private val _cartItems = MutableStateFlow<List<CartItem>>(
    listOf(
      CartItem(
        id = "cart_item_1",
        product = sampleProducts[0],
        quantity = 1,
        selectedPalette = "Cobalt Blue",
        selectedSize = "10.5 in"
      ),
      CartItem(
        id = "cart_item_2",
        product = sampleProducts[1],
        quantity = 2,
        selectedPalette = null,
        selectedSize = "Pack of 3"
      ),
      CartItem(
        id = "cart_item_3",
        product = sampleProducts[3],
        quantity = 1,
        selectedPalette = null,
        selectedSize = "500g Bottle"
      )
    )
  )
  val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

  private val _cartCount = MutableStateFlow(3)
  val cartCount: StateFlow<Int> = _cartCount.asStateFlow()

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

    // Sync to Supabase
    val userId = SupabaseClient.currentUserId
    if (userId != null) {
      scope.launch {
        SupabaseApiService.addToCart(userId, product.id, quantity, palette, size)
      }
    }
  }

  fun updateQuantity(itemId: String, delta: Int) {
    var newQty = 0
    _cartItems.update { current ->
      current.mapNotNull { item ->
        if (item.id == itemId) {
          val calculated = item.quantity + delta
          newQty = calculated
          if (calculated > 0) item.copy(quantity = calculated) else null
        } else {
          item
        }
      }
    }
    updateCartCount()

    // Sync to Supabase
    scope.launch {
      if (newQty > 0) {
        SupabaseApiService.updateCartQuantity(itemId, newQty)
      } else {
        SupabaseApiService.removeCartItem(itemId)
      }
    }
  }

  fun removeFromCart(itemId: String) {
    _cartItems.update { current ->
      current.filterNot { it.id == itemId }
    }
    updateCartCount()
    scope.launch {
      SupabaseApiService.removeCartItem(itemId)
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

  private fun updateCartCount() {
    _cartCount.value = _cartItems.value.sumOf { it.quantity }
  }

  // Categories list
  val defaultCategories = listOf(
    CategoryItem(
      id = "cat_groceries",
      title = "Food & Groceries",
      countText = "480+ Products",
      sampleItems = "Atta, Rice, Cooking Oil, Dairy, Pure Spices",
      drawableRes = R.drawable.cat_groceries,
      badge = "Fast Delivery"
    ),
    CategoryItem(
      id = "cat_homemade",
      title = "Homemade Food",
      countText = "65 Home Kitchens",
      sampleItems = "Biryani, Nihari, Karahi, Frozen Parathas",
      drawableRes = R.drawable.cat_homemade,
      badge = "Fresh Home Chefs"
    ),
    CategoryItem(
      id = "cat_fastfood",
      title = "Fast Food & Snacks",
      countText = "42 Restaurants",
      sampleItems = "Burgers, Shawarma, Chai, Fries",
      drawableRes = R.drawable.cat_fastfood
    ),
    CategoryItem(
      id = "cat_bags",
      title = "Bags & Accessories",
      countText = "210+ Items",
      sampleItems = "Handbags, Backpacks, Wallets",
      drawableRes = R.drawable.cat_bags
    ),
    CategoryItem(
      id = "cat_homeliving",
      title = "Home & Living",
      countText = "340+ Items",
      sampleItems = "Cushions, Bedding, Rugs, Wall Art",
      drawableRes = R.drawable.cat_homeliving
    ),
    CategoryItem(
      id = "cat_kitchen",
      title = "Kitchen & Dining",
      countText = "190+ Items",
      sampleItems = "Cookware, Cutlery, Storage",
      drawableRes = R.drawable.cat_kitchen
    ),
    CategoryItem(
      id = "cat_stationery",
      title = "Stationery & Books",
      countText = "120+ Items",
      sampleItems = "Notebooks, Planners, Islamic Books",
      drawableRes = R.drawable.cat_stationery
    ),
    CategoryItem(
      id = "cat_wellness",
      title = "Wellness & Care",
      countText = "95+ Items",
      sampleItems = "Organic Oils, Soaps, Skincare",
      drawableRes = R.drawable.cat_wellness
    ),
    CategoryItem(
      id = "cat_lighting",
      title = "Lighting & Decor",
      countText = "80+ Items",
      sampleItems = "Fairy Lights, Lamps, Wall Clocks",
      drawableRes = R.drawable.cat_lighting
    ),
    CategoryItem(
      id = "cat_artisan",
      title = "Artisan Handicrafts",
      countText = "Traditional Art",
      sampleItems = "Blue Pottery, Chundri, Brass Decor",
      drawableRes = R.drawable.cat_artisan,
      badge = "Handcrafted"
    )
  )

  private val _categories = MutableStateFlow(defaultCategories)
  val categories: StateFlow<List<CategoryItem>> = _categories.asStateFlow()

  // Orders
  val sampleOrder = OrderRecord(
    orderId = "JT-94821-PK",
    date = "Today, 02:45 PM",
    items = _cartItems.value,
    subtotal = 4160,
    discount = 100,
    deliveryFee = 150,
    grandTotal = 4210,
    address = _selectedAddress.value,
    deliveryNote = "Leave parcel with security guard / gate # 2",
    statusHeader = "Order Confirmed • Processing",
    orderStatus = "PROCESSING",
    timeline = listOf(
      OrderTimelineStep(
        title = "Order Placed",
        timestamp = "Today, 02:45 PM",
        description = "Central JTBazar order registered. Dispatch team notified.",
        state = StepState.COMPLETED
      ),
      OrderTimelineStep(
        title = "Vendor Coordination & QC",
        timestamp = "In Progress",
        description = "JTBazar team verifying items with vendors and sealing dispatch bag.",
        state = StepState.IN_PROGRESS
      ),
      OrderTimelineStep(
        title = "Courier Pickup",
        timestamp = "Tomorrow",
        description = "Assigned to JTBazar Express delivery rider.",
        state = StepState.PENDING
      ),
      OrderTimelineStep(
        title = "Out for Delivery & COD Collection",
        timestamp = "Estimated 2–3 Days",
        description = "Doorstep delivery in Lahore with Cash on Delivery payment.",
        state = StepState.PENDING
      ),
      OrderTimelineStep(
        title = "Delivered",
        timestamp = "Pending Delivery",
        description = "Package handed over with digital acknowledgment.",
        state = StepState.PENDING
      )
    )
  )

  private val _orders = MutableStateFlow(listOf(sampleOrder))
  val orders: StateFlow<List<OrderRecord>> = _orders.asStateFlow()

  private val _activeOrder = MutableStateFlow(sampleOrder)
  val activeOrder: StateFlow<OrderRecord> = _activeOrder.asStateFlow()

  // Reviews
  private val _reviews = MutableStateFlow<List<SupabaseReviewDto>>(emptyList())
  val reviews: StateFlow<List<SupabaseReviewDto>> = _reviews.asStateFlow()

  // Notifications
  private val _notifications = MutableStateFlow<List<SupabaseNotificationDto>>(
    listOf(
      SupabaseNotificationDto(
        id = "notif_1",
        userId = "user_default",
        title = "Order JT-94821-PK Confirmed!",
        message = "Your Cash on Delivery order has been confirmed by JTBazar. Rider will arrive in 2–3 days.",
        type = "ORDER",
        isRead = false,
        createdAt = "Just now"
      ),
      SupabaseNotificationDto(
        id = "notif_2",
        userId = "user_default",
        title = "Grand Bachat Offer Live!",
        message = "Enjoy up to 40% OFF on Multani Ceramics & Spices in Lahore this weekend.",
        type = "PROMOTION",
        isRead = true,
        createdAt = "1 day ago"
      )
    )
  )
  val notifications: StateFlow<List<SupabaseNotificationDto>> = _notifications.asStateFlow()

  // ----------------------------------------------------
  // ASYNC LIVE DATA SYNC
  // ----------------------------------------------------

  fun refreshLiveCatalog() {
    scope.launch {
      _isLoading.value = true
      _errorMessage.value = null

      try {
        // Fetch products from Supabase
        val productResult = SupabaseApiService.fetchProducts(limit = 60)
        if (productResult.isSuccess) {
          val dtoList = productResult.getOrNull().orEmpty()
          if (dtoList.isNotEmpty()) {
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
            Log.d(TAG, "Loaded ${mappedProducts.size} live products from Supabase")
          }
        }

        // Fetch categories from Supabase
        val categoryResult = SupabaseApiService.fetchCategories()
        if (categoryResult.isSuccess) {
          val catDtoList = categoryResult.getOrNull().orEmpty()
          if (catDtoList.isNotEmpty()) {
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
        }
      } catch (e: Exception) {
        Log.w(TAG, "Backend sync notice: ${e.message}. Using high-quality cached catalog.")
        _errorMessage.value = e.message
      } finally {
        _isLoading.value = false
      }
    }
  }

  fun placeCurrentOrder(deliveryNote: String = ""): OrderRecord {
    val items = _cartItems.value
    val subtotal = items.sumOf { it.totalPrice }
    val discount = if (subtotal > 0) 100 else 0
    val deliveryFee = if (subtotal > 0) 150 else 0
    val grandTotal = (subtotal - discount + deliveryFee).coerceAtLeast(0)

    val randomNum = (10000..99999).random()
    val newOrderNumber = "JT-$randomNum-PK"

    val newOrder = OrderRecord(
      orderId = newOrderNumber,
      date = "Today, Just Now",
      items = items.ifEmpty { sampleOrder.items },
      subtotal = if (subtotal > 0) subtotal else 4160,
      discount = discount,
      deliveryFee = deliveryFee,
      grandTotal = if (grandTotal > 0) grandTotal else 4210,
      address = _selectedAddress.value,
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
          title = "Vendor Coordination & QC",
          timestamp = "Today",
          description = "JTBazar team verifying package with verified vendors.",
          state = StepState.IN_PROGRESS
        ),
        OrderTimelineStep(
          title = "Courier Pickup",
          timestamp = "Tomorrow",
          description = "Dispatch to Lahore doorstep rider.",
          state = StepState.PENDING
        ),
        OrderTimelineStep(
          title = "Out for Delivery & COD Collection",
          timestamp = "Estimated 2–3 Days",
          description = "Rider delivering to ${_selectedAddress.value.area}, Lahore.",
          state = StepState.PENDING
        ),
        OrderTimelineStep(
          title = "Delivered",
          timestamp = "Pending Delivery",
          description = "Package handed over with digital acknowledgment.",
          state = StepState.PENDING
        )
      )
    )

    _activeOrder.value = newOrder
    _orders.update { listOf(newOrder) + it }

    // Backend order creation
    val userId = SupabaseClient.currentUserId ?: "guest_customer"
    scope.launch {
      val orderDto = SupabaseOrderDto(
        id = "",
        orderNumber = newOrderNumber,
        userId = userId,
        subtotal = newOrder.subtotal,
        discount = newOrder.discount,
        deliveryFee = newOrder.deliveryFee,
        grandTotal = newOrder.grandTotal,
        paymentMethod = "COD",
        paymentStatus = "PENDING",
        orderStatus = "PENDING",
        deliveryNote = deliveryNote,
        addressName = newOrder.address.name,
        addressPhone = newOrder.address.phone,
        addressLine = newOrder.address.addressLine,
        addressArea = newOrder.address.area,
        addressCity = newOrder.address.city,
        createdAt = System.currentTimeMillis().toString()
      )

      val orderItemsDto = newOrder.items.map { item ->
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

      SupabaseApiService.createOrder(orderDto, orderItemsDto)
    }

    // Add confirmation notification
    _notifications.update { current ->
      listOf(
        SupabaseNotificationDto(
          id = "notif_${System.currentTimeMillis()}",
          userId = userId,
          title = "Order $newOrderNumber Placed!",
          message = "Total: PKR ${String.format("%,d", grandTotal)} via Cash on Delivery. JTBazar is coordinating dispatch.",
          type = "ORDER",
          isRead = false,
          createdAt = "Just now"
        )
      ) + current
    }

    return newOrder
  }

  fun cancelOrder(orderId: String): Boolean {
    val existing = _orders.value.find { it.orderId == orderId }
    if (existing != null && existing.orderStatus == "PENDING") {
      val cancelled = existing.copy(
        orderStatus = "CANCELLED",
        statusHeader = "Order Cancelled",
        timeline = existing.timeline.map {
          if (it.state == StepState.IN_PROGRESS) it.copy(state = StepState.PENDING, description = "Cancelled by customer") else it
        }
      )
      _orders.update { list -> list.map { if (it.orderId == orderId) cancelled else it } }
      if (_activeOrder.value.orderId == orderId) {
        _activeOrder.value = cancelled
      }
      return true
    }
    return false
  }

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
    // Start countdown ticker
    scope.launch {
      while (true) {
        delay(1000)
        _countdownSeconds.update { curr ->
          if (curr > 0) curr - 1 else 4 * 3600 + 18 * 60 + 18
        }
      }
    }
    // Asynchronously refresh live catalog
    refreshLiveCatalog()
  }
}
