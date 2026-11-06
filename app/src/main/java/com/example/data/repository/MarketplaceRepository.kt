package com.example.data.repository

import com.example.R
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object MarketplaceRepository {
  private val scope = CoroutineScope(Dispatchers.Default)

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
  }

  // Products Catalog
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
    _products.update { list ->
      list.map { prod ->
        if (prod.id == productId) prod.copy(isWishlisted = !prod.isWishlisted) else prod
      }
    }
    if (_selectedProduct.value.id == productId) {
      _selectedProduct.update { it.copy(isWishlisted = !it.isWishlisted) }
    }
  }

  // Pre-populated Cart matching the screenshots!
  // Screen 4 shows:
  // 1. Multani Hand-Glazed Ceramic Plate Set (Cobalt Blue, 10.5 in) x 1 = Rs. 2,450
  // 2. Shan Shahi Biryani Recipe Mix Pack (Pack of 3) x 2 = Rs. 760
  // 3. Pure Blossom Honey 500g Bottle x 1 = Rs. 950
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

  val cartCount: StateFlow<Int> = MutableStateFlow(3).apply {
    scope.launch {
      _cartItems.collect { items ->
        value = items.sumOf { it.quantity }
      }
    }
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
  }

  fun updateQuantity(itemId: String, delta: Int) {
    _cartItems.update { current ->
      current.mapNotNull { item ->
        if (item.id == itemId) {
          val newQty = item.quantity + delta
          if (newQty > 0) item.copy(quantity = newQty) else null
        } else {
          item
        }
      }
    }
  }

  fun removeFromCart(itemId: String) {
    _cartItems.update { current ->
      current.filterNot { it.id == itemId }
    }
  }

  // Categories list matching screen_07936dc6.png
  val categories = listOf(
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
      badge = "Coming Soon"
    )
  )

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
    statusHeader = "2 Vendors Processing",
    timeline = listOf(
      OrderTimelineStep(
        title = "Order Placed",
        timestamp = "Today, 02:45 PM",
        description = "Notified Multan Craft Heritage & Al-Madina Mart.",
        state = StepState.COMPLETED
      ),
      OrderTimelineStep(
        title = "Packing & Verification",
        timestamp = "In Progress",
        description = "Vendors are wrapping items and finalizing QC dispatch bags.",
        state = StepState.IN_PROGRESS
      ),
      OrderTimelineStep(
        title = "Courier Pickup",
        timestamp = "Tomorrow",
        description = "Logistics rider assigned for doorstep delivery.",
        state = StepState.PENDING
      ),
      OrderTimelineStep(
        title = "Out for Delivery & COD Collection",
        timestamp = "Estimated 2–3 Days",
        description = "Final mile dispatch in Gulberg III, Lahore.",
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

  private val _activeOrder = MutableStateFlow(sampleOrder)
  val activeOrder: StateFlow<OrderRecord> = _activeOrder.asStateFlow()

  fun placeCurrentOrder(deliveryNote: String = ""): OrderRecord {
    val items = _cartItems.value
    val subtotal = items.sumOf { it.totalPrice }
    val discount = 100
    val deliveryFee = 150
    val grandTotal = (subtotal - discount + deliveryFee).coerceAtLeast(0)

    val newOrder = sampleOrder.copy(
      orderId = "JT-${(10000..99999).random()}-PK",
      items = items.ifEmpty { sampleOrder.items },
      subtotal = if (subtotal > 0) subtotal else 4160,
      grandTotal = if (grandTotal > 0) grandTotal else 4210,
      address = _selectedAddress.value,
      deliveryNote = deliveryNote
    )
    _activeOrder.value = newOrder
    return newOrder
  }

  init {
    // Start live countdown ticker
    scope.launch {
      while (true) {
        delay(1000)
        _countdownSeconds.update { curr ->
          if (curr > 0) curr - 1 else 4 * 3600 + 18 * 60 + 18
        }
      }
    }
  }
}
