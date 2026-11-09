package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.Product
import com.example.data.model.ShopRecord
import com.example.data.repository.MarketplaceRepository
import com.example.ui.components.JTBazarTopBar
import com.example.ui.theme.*
import kotlin.math.*
import java.util.Locale
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

enum class DiscoverTab {
  NEARBY_SHOPS,
  PRODUCTS_FEED
}

@Composable
fun DiscoverScreen(
  onProductClick: (Product) -> Unit,
  onCartClick: () -> Unit,
  onAccountClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val cartCount by MarketplaceRepository.cartCount.collectAsState()
  val currentLocation by MarketplaceRepository.currentLocation.collectAsState()
  val products by MarketplaceRepository.products.collectAsState()
  val shops by MarketplaceRepository.shops.collectAsState()

  var selectedTab by remember { mutableStateOf(DiscoverTab.PRODUCTS_FEED) }

  // Location State
  var hasLocationPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    )
  }
  var userLatitude by remember { mutableStateOf<Double?>(null) }
  var userLongitude by remember { mutableStateOf<Double?>(null) }
  var selectedCityName by remember { mutableStateOf("Select city") }
  var showCityPickerDialog by remember { mutableStateOf(false) }
  var selectedShop by remember { mutableStateOf<ShopRecord?>(null) }

  fun loadCurrentLocation() {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) return
    try {
      LocationServices.getFusedLocationProviderClient(context)
        .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
        .addOnSuccessListener { location ->
          if (location != null) {
            userLatitude = location.latitude
            userLongitude = location.longitude
            selectedCityName = "Your current area"
          } else {
            Toast.makeText(context, "Current location unavailable. Please enable device location.", Toast.LENGTH_SHORT).show()
          }
        }
        .addOnFailureListener {
          Toast.makeText(context, "Unable to get your location. Try again.", Toast.LENGTH_SHORT).show()
        }
    } catch (_: SecurityException) {
      hasLocationPermission = false
    }
  }

  val locationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    hasLocationPermission = granted
    if (granted) loadCurrentLocation()
  }

  LaunchedEffect(hasLocationPermission) {
    if (hasLocationPermission && userLatitude == null) loadCurrentLocation()
  }

  Scaffold(
    containerColor = if (selectedTab == DiscoverTab.PRODUCTS_FEED) Color.Black else Color.White,
    topBar = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(if (selectedTab == DiscoverTab.PRODUCTS_FEED) Color.Black else Color.White)
      ) {
        JTBazarTopBar(
          title = "Discover",
          onCartClick = onCartClick,
          onAvatarClick = onAccountClick,
          cartCount = cartCount,
          currentLocation = currentLocation
        )

        // TWO CLEAR BUTTONS/TABS: A. Nearby Shops, B. Products Feed
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Tab A: Nearby Shops
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (selectedTab == DiscoverTab.NEARBY_SHOPS) JTOrange else (if (selectedTab == DiscoverTab.PRODUCTS_FEED) Color(0xFF1F2937) else JTSurfaceVariant),
            modifier = Modifier
              .weight(1f)
              .height(38.dp)
              .clickable { selectedTab = DiscoverTab.NEARBY_SHOPS }
              .testTag("tab_nearby_shops")
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center
            ) {
              Icon(
                imageVector = Icons.Filled.Store,
                contentDescription = null,
                tint = if (selectedTab == DiscoverTab.NEARBY_SHOPS) Color.White else (if (selectedTab == DiscoverTab.PRODUCTS_FEED) Color.White else JTTextPrimary),
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Nearby Shops",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (selectedTab == DiscoverTab.NEARBY_SHOPS) Color.White else (if (selectedTab == DiscoverTab.PRODUCTS_FEED) Color.White else JTTextPrimary)
              )
            }
          }

          // Tab B: Products Feed
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (selectedTab == DiscoverTab.PRODUCTS_FEED) JTOrange else JTSurfaceVariant,
            modifier = Modifier
              .weight(1f)
              .height(38.dp)
              .clickable { selectedTab = DiscoverTab.PRODUCTS_FEED }
              .testTag("tab_products_feed")
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center
            ) {
              Icon(
                imageVector = Icons.Filled.PlayCircleOutline,
                contentDescription = null,
                tint = if (selectedTab == DiscoverTab.PRODUCTS_FEED) Color.White else JTTextPrimary,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Products Feed",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (selectedTab == DiscoverTab.PRODUCTS_FEED) Color.White else JTTextPrimary
              )
            }
          }
        }
      }
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      when (selectedTab) {
        DiscoverTab.NEARBY_SHOPS -> {
          NearbyShopsMapView(
            shops = shops,
            userLat = userLatitude,
            userLng = userLongitude,
            cityName = selectedCityName,
            hasPermission = hasLocationPermission,
            onRequestPermission = {
              locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
              )
            },
            onPickCity = { showCityPickerDialog = true },
            selectedShop = selectedShop,
            onSelectShop = { selectedShop = it },
            onExploreShopProducts = { shop ->
              // Filter products for this shop and show toast
              Toast.makeText(context, "Opening ${shop.name}...", Toast.LENGTH_SHORT).show()
            }
          )
        }

        DiscoverTab.PRODUCTS_FEED -> {
          ProductsFeedReelsView(
            products = products,
            onProductClick = onProductClick,
            onAddToCart = { prod ->
              MarketplaceRepository.addToCart(prod, 1)
              Toast.makeText(context, "Added ${prod.title} to cart!", Toast.LENGTH_SHORT).show()
            },
            onToggleWishlist = { prod ->
              MarketplaceRepository.toggleWishlist(prod.id)
            },
            onShare = { prod ->
              val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Check out ${prod.title} on JTBazar: Rs. ${prod.price}")
                type = "text/plain"
              }
              context.startActivity(Intent.createChooser(sendIntent, "Share Product"))
            }
          )
        }
      }
    }
  }

  // Manual City Selection Dialog when location permission is not available or user wants to switch
  if (showCityPickerDialog) {
    val majorCities = listOf(
      "Lahore" to Pair(31.5204, 74.3587),
      "Karachi" to Pair(24.8607, 67.0011),
      "Islamabad" to Pair(33.6844, 73.0479),
      "Rawalpindi" to Pair(33.5651, 73.0169),
      "Faisalabad" to Pair(31.4504, 73.1350),
      "Multan" to Pair(30.1575, 71.5249),
      "Peshawar" to Pair(34.0151, 71.5249)
    )

    AlertDialog(
      onDismissRequest = { showCityPickerDialog = false },
      title = { Text("Select Your City", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Choose a city to explore local shops within delivery range:", fontSize = 12.sp, color = JTTextSecondary)
          majorCities.forEach { (city, coords) ->
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (selectedCityName == city) JTPurpleContainer else JTSurfaceVariant,
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  selectedCityName = city
                  userLatitude = coords.first
                  userLongitude = coords.second
                  showCityPickerDialog = false
                }
            ) {
              Text(
                text = city,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
              )
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showCityPickerDialog = false }) { Text("Close") }
      }
    )
  }
}

// ----------------------------------------------------
// TAB A: NEARBY SHOPS INTERACTIVE MAP VIEW
// ----------------------------------------------------
@Composable
private fun NearbyShopsMapView(
  shops: List<ShopRecord>,
  userLat: Double?,
  userLng: Double?,
  cityName: String,
  hasPermission: Boolean,
  onRequestPermission: () -> Unit,
  onPickCity: () -> Unit,
  selectedShop: ShopRecord?,
  onSelectShop: (ShopRecord) -> Unit,
  onExploreShopProducts: (ShopRecord) -> Unit
) {
  val context = LocalContext.current
  val validShops = remember(shops) {
    shops.filter { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 &&
      !(it.latitude == 0.0 && it.longitude == 0.0) }
  }
  fun distanceKm(shop: ShopRecord): Double? {
    if (userLat == null || userLng == null) return null
    val result = FloatArray(1)
    Location.distanceBetween(userLat, userLng, shop.latitude, shop.longitude, result)
    return result[0] / 1000.0
  }
  val nearbyShops = remember(validShops, userLat, userLng, cityName) {
    validShops.map { it to distanceKm(it) }
      .filter { (shop, distance) ->
        if (distance != null) distance <= 50.0
        else cityName != "Select city" && (shop.city.contains(cityName, ignoreCase = true) ||
          shop.address.contains(cityName, ignoreCase = true))
      }
      .sortedBy { it.second ?: Double.MAX_VALUE }
  }
  val mapCenter = remember(userLat, userLng, nearbyShops) {
    when {
      userLat != null && userLng != null -> GeoPoint(userLat, userLng)
      nearbyShops.firstOrNull() != null -> GeoPoint(nearbyShops.first().first.latitude, nearbyShops.first().first.longitude)
      else -> GeoPoint(30.3753, 69.3451)
    }
  }

  DisposableEffect(Unit) {
    Configuration.getInstance().userAgentValue = context.packageName
    onDispose { }
  }

  Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
    Surface(color = JTSurfaceVariant, modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(Icons.Filled.MyLocation, contentDescription = null, tint = JTOrange, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(7.dp))
          Column {
            Text(
              if (hasPermission && userLat != null) "Your current location" else cityName,
              fontWeight = FontWeight.Bold, fontSize = 12.sp, color = JTTextPrimary
            )
            Text(
              if (userLat != null && userLng != null) "Showing shops nearest to you"
              else "Choose a city or enable location",
              fontSize = 10.sp, color = JTTextSecondary
            )
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          if (!hasPermission) {
            Button(
              onClick = onRequestPermission,
              shape = RoundedCornerShape(8.dp),
              colors = ButtonDefaults.buttonColors(containerColor = JTPurplePrimary),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
              modifier = Modifier.height(32.dp).testTag("nearby_gps_button")
            ) { Text("Use my location", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
          }
          OutlinedButton(
            onClick = onPickCity,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.height(32.dp).testTag("nearby_city_picker")
          ) { Text("City", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
      }
    }

    AndroidView(
      modifier = Modifier.fillMaxWidth().weight(1f).testTag("nearby_shops_real_map"),
      factory = { ctx ->
        MapView(ctx).apply {
          setTileSource(TileSourceFactory.MAPNIK)
          setMultiTouchControls(true)
          controller.setZoom(if (userLat == null && userLng == null) 5.5 else 12.0)
          controller.setCenter(mapCenter)
        }
      },
      update = { map ->
        map.overlays.clear()
        map.controller.setCenter(mapCenter)
        if (userLat != null && userLng != null) {
          Marker(map).apply {
            position = GeoPoint(userLat, userLng)
            title = "You are here"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            map.overlays.add(this)
          }
        }
        nearbyShops.forEach { (shop, distance) ->
          Marker(map).apply {
            position = GeoPoint(shop.latitude, shop.longitude)
            title = shop.name
            snippet = distance?.let { String.format(Locale.US, "%.1f km away", it) } ?: shop.city
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setOnMarkerClickListener { _, _ ->
              onSelectShop(shop)
              true
            }
            map.overlays.add(this)
          }
        }
        map.invalidate()
      }
    )

    if (nearbyShops.isEmpty()) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text("No shops with map locations found nearby", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = JTTextPrimary)
        Text(
          if (validShops.isEmpty()) "Approved shops need valid latitude and longitude to appear on the map."
          else "Try another city or expand your search area.",
          fontSize = 11.sp, color = JTTextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
      }
    } else {
      val activeShop = selectedShop?.takeIf { selected -> nearbyShops.any { it.first.id == selected.id } }
        ?: nearbyShops.first().first
      val activeDistance = distanceKm(activeShop)
      Surface(color = Color.White, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(JTPurpleContainer),
              contentAlignment = Alignment.Center
            ) {
              if (!activeShop.logoUrl.isNullOrBlank()) {
                AsyncImage(model = activeShop.logoUrl, contentDescription = activeShop.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
              } else {
                Icon(Icons.Filled.Store, contentDescription = null, tint = JTPurplePrimary)
              }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(activeShop.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = JTTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (activeShop.isVerified) {
                  Spacer(Modifier.width(4.dp))
                  Icon(Icons.Filled.Verified, contentDescription = "Verified", tint = JTPurplePrimary, modifier = Modifier.size(14.dp))
                }
              }
              Text(
                activeDistance?.let { String.format(Locale.US, "%.1f km from you", it) } ?: activeShop.city.ifBlank { activeShop.address },
                fontSize = 11.sp, color = JTTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
              )
            }
            Text("${nearbyShops.size} nearby", fontSize = 10.sp, color = JTPurplePrimary, fontWeight = FontWeight.Bold)
          }
          if (activeShop.description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(activeShop.description, fontSize = 11.sp, color = JTTextSecondary, maxLines = 2, lineHeight = 15.sp)
          }
          Spacer(Modifier.height(10.dp))
          Button(
            onClick = { onExploreShopProducts(activeShop) },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(40.dp).testTag("open_shop_button")
          ) { Text("Open Shop & View Products", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
      }
    }
  }
}

// ----------------------------------------------------
// TAB B: PRODUCTS FEED — INSTAGRAM REELS STYLE
// ----------------------------------------------------
@Composable
private fun ProductsFeedReelsView(
  products: List<Product>,
  onProductClick: (Product) -> Unit,
  onAddToCart: (Product) -> Unit,
  onToggleWishlist: (Product) -> Unit,
  onShare: (Product) -> Unit
) {
  if (products.isEmpty()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .padding(24.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.ShoppingBag, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("No Products in Feed", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Products will appear here in full discovery mode.", fontSize = 12.sp, color = Color.Gray)
      }
    }
  } else {
    val pagerState = rememberPagerState(pageCount = { products.size })

    VerticalPager(
      state = pagerState,
      modifier = Modifier.fillMaxSize()
    ) { page ->
      val product = products[page]

      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color(0xFFF5F5F5))
      ) {
        // Fit the whole product image within the available feed area.
        // Coil also loads the bundled placeholder resource when no remote image exists.
        AsyncImage(
          model = product.imageUrl?.takeIf { it.isNotBlank() } ?: R.drawable.ic_launcher_background,
          contentDescription = product.title,
          contentScale = ContentScale.Fit,
          modifier = Modifier
            .fillMaxSize()
            .align(Alignment.Center)
        )

        // Bottom Gradient Overlay for text contrast
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .align(Alignment.BottomCenter)
            .background(
              Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f), Color.Black)
              )
            )
        )

        // Bottom Product Info (Left side)
        Column(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth(0.78f)
            .padding(start = 20.dp, bottom = 24.dp)
        ) {
          // Vendor Name Chip
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.padding(bottom = 6.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Icon(Icons.Filled.Store, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = product.vendor,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
              )
            }
          }

          // Product Title
          Text(
            text = product.title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )

          Spacer(modifier = Modifier.height(4.dp))

          // Price & Discount
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "Rs. ${String.format("%,d", product.price)}",
              color = Color(0xFFF59E0B),
              fontSize = 20.sp,
              fontWeight = FontWeight.ExtraBold
            )
            if (product.discountPercent > 0) {
              Spacer(modifier = Modifier.width(8.dp))
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = JTOrange
              ) {
                Text(
                  text = "${product.discountPercent}% OFF",
                  color = Color.White,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = product.inStockText,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
          )

          Spacer(modifier = Modifier.height(10.dp))

          // Tap to view full details
          Button(
            onClick = { onProductClick(product) },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.25f), contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.height(34.dp)
          ) {
            Text("View Full Details", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp))
          }
        }

        // RIGHT SIDE: Floating Action Column (Instagram Reels Style)
        Column(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = 32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          // 1. Wishlist / Heart Action
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FloatingReelsAction(
              icon = if (product.isWishlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
              tint = if (product.isWishlisted) Color(0xFFEF4444) else Color.White,
              onClick = { onToggleWishlist(product) },
              tag = "reels_wishlist_btn_${product.id}"
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = if (product.isWishlisted) "Saved" else "Save",
              color = Color.White,
              fontSize = 10.sp,
              fontWeight = FontWeight.Medium
            )
          }

          // 2. Add to Cart Action
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FloatingReelsAction(
              icon = Icons.Filled.AddShoppingCart,
              tint = JTOrange,
              onClick = { onAddToCart(product) },
              tag = "reels_add_cart_btn_${product.id}"
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Add",
              color = Color.White,
              fontSize = 10.sp,
              fontWeight = FontWeight.Medium
            )
          }

          // 3. Share Action
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FloatingReelsAction(
              icon = Icons.Outlined.Share,
              tint = Color.White,
              onClick = { onShare(product) },
              tag = "reels_share_btn_${product.id}"
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Share",
              color = Color.White,
              fontSize = 10.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }
      }
    }
  }
}

@Composable
private fun FloatingReelsAction(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  tint: Color,
  onClick: () -> Unit,
  tag: String
) {
  Box(
    modifier = Modifier
      .size(46.dp)
      .clip(CircleShape)
      .background(Color.Black.copy(alpha = 0.55f))
      .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
      .clickable(onClick = onClick)
      .testTag(tag),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(24.dp)
    )
  }
}
