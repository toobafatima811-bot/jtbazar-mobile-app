package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import android.widget.VideoView
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.example.data.supabase.SupabaseApiService
import com.example.data.supabase.SupabaseProductVideoDto
import com.example.data.model.ShopRecord
import com.example.data.repository.MarketplaceRepository
import com.example.ui.components.JTBazarTopBar
import com.example.ui.theme.*
import com.example.location.AddressReverseGeocoder
import kotlinx.coroutines.launch
import kotlin.math.*
import java.util.Locale
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

enum class DiscoverTab {
  NEARBY_SHOPS,
  PRODUCTS_FEED
}

@Composable
fun DiscoverScreen(
  onProductClick: (Product) -> Unit,
  onCartClick: () -> Unit,
  onAccountClick: () -> Unit,
  onOpenShop: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val locationScope = rememberCoroutineScope()
  val cartCount by MarketplaceRepository.cartCount.collectAsState()
  val currentLocation by MarketplaceRepository.currentLocation.collectAsState()
  val products by MarketplaceRepository.products.collectAsState()
  val shops by MarketplaceRepository.shops.collectAsState()
  var productVideos by remember { mutableStateOf<List<SupabaseProductVideoDto>>(emptyList()) }
  LaunchedEffect(Unit) {
    SupabaseApiService.fetchProductVideos().onSuccess { productVideos = it }
  }

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
            locationScope.launch {
              val resolved = AddressReverseGeocoder.resolve(context, location.latitude, location.longitude)
              selectedCityName = listOf(resolved?.area, resolved?.city)
                .filterNotNull().filter { it.isNotBlank() }.distinct().joinToString(", ")
                .ifBlank { "Your current area" }
            }
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

  LaunchedEffect(Unit) {
    if (hasLocationPermission) loadCurrentLocation()
    else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
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
            onPickCity = { if (hasLocationPermission) loadCurrentLocation() else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
            selectedShop = selectedShop,
            onSelectShop = { selectedShop = it },
            onExploreShopProducts = { shop ->
              onOpenShop(shop.id)
            }
          )
        }

        DiscoverTab.PRODUCTS_FEED -> {
          ProductsFeedReelsView(
            products = products,
            videos = productVideos,
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
  var mapRevision by remember { mutableIntStateOf(0) }
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
        if (distance != null) distance <= 10.0
        else cityName != "Select city" && (shop.city.contains(cityName, ignoreCase = true) ||
          shop.address.contains(cityName, ignoreCase = true))
      }
      .sortedBy { it.second ?: Double.MAX_VALUE }
  }
  val mapCenter = remember(userLat, userLng, nearbyShops) {
    when {
      userLat != null && userLng != null -> GeoPoint(userLat, userLng)
      nearbyShops.firstOrNull() != null -> GeoPoint(nearbyShops.first().first.latitude, nearbyShops.first().first.longitude)
      else -> GeoPoint(0.0, 0.0)
    }
  }

  DisposableEffect(Unit) {
    Configuration.getInstance().userAgentValue = context.packageName
    onDispose { }
  }

  Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
    // Keep map actions in a Compose overlay, independent from native map gestures.
    // Panning/zooming the map must never hide or consume these controls.
    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
      AndroidView(
        modifier = Modifier.fillMaxSize().testTag("nearby_shops_real_map"),
        factory = { ctx ->
          MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(if (userLat == null && userLng == null) 5.5 else 12.0)
            controller.setCenter(mapCenter)
            tag = mapCenter
            addMapListener(object : MapListener {
              override fun onScroll(event: ScrollEvent?): Boolean { mapRevision++; return true }
              override fun onZoom(event: ZoomEvent?): Boolean { mapRevision++; return true }
            })
          }
        },
        update = { map ->
          val renderRevision = mapRevision
          if (renderRevision < 0) map.invalidate()
          if (map.tag != mapCenter) {
            map.controller.setCenter(mapCenter)
            map.tag = mapCenter
          }
          map.overlays.clear()
          if (userLat != null && userLng != null) {
            Marker(map).apply {
              position = GeoPoint(userLat, userLng)
              title = "You are here"
              setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
              map.overlays.add(this)
            }
            val earthRadiusKm = 6371.0
            val radiusRadians = 10.0 / earthRadiusKm
            val centerLat = Math.toRadians(userLat)
            val centerLng = Math.toRadians(userLng)
            val circlePoints = (0..72).map { step ->
              val bearing = 2.0 * Math.PI * step / 72.0
              val lat = asin(sin(centerLat) * cos(radiusRadians) + cos(centerLat) * sin(radiusRadians) * cos(bearing))
              val lng = centerLng + atan2(sin(bearing) * sin(radiusRadians) * cos(centerLat), cos(radiusRadians) - sin(centerLat) * sin(lat))
              GeoPoint(Math.toDegrees(lat), Math.toDegrees(lng))
            }
            Polygon(map).apply {
              points = circlePoints
              fillPaint.color = android.graphics.Color.argb(28, 255, 140, 0)
              outlinePaint.color = android.graphics.Color.argb(190, 255, 140, 0)
              outlinePaint.strokeWidth = 3f
              map.overlays.add(this)
            }
          }

          // Cluster overlapping shop markers in screen pixels; zooming separates them.
          val remaining = nearbyShops.map { (shop, distance) ->
            val point = Point()
            map.projection.toPixels(GeoPoint(shop.latitude, shop.longitude), point)
            Triple(shop, distance, point)
          }.toMutableList()
          while (remaining.isNotEmpty()) {
            val seed = remaining.removeAt(0)
            val group = mutableListOf(seed)
            var changed = true
            while (changed) {
              changed = false
              val iterator = remaining.iterator()
              while (iterator.hasNext()) {
                val candidate = iterator.next()
                val joins = group.any { member ->
                  val dx = (candidate.third.x - member.third.x).toDouble()
                  val dy = (candidate.third.y - member.third.y).toDouble()
                  dx * dx + dy * dy <= 72.0 * 72.0
                }
                if (joins) { group.add(candidate); iterator.remove(); changed = true }
              }
            }
            val center = GeoPoint(group.map { it.first.latitude }.average(), group.map { it.first.longitude }.average())
            if (group.size == 1) {
              val (shop, distance, _) = group.first()
              Marker(map).apply {
                position = center
                title = shop.name
                snippet = distance?.let { String.format(Locale.US, "%.1f km away", it) } ?: shop.city
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ -> onSelectShop(shop); true }
                map.overlays.add(this)
              }
            } else {
              val names = group.map { it.first.name }
              Marker(map).apply {
                position = center
                title = "${group.size} nearby shops"
                snippet = names.take(4).joinToString(" • ") + if (names.size > 4) " • +${names.size - 4} more" else ""
                icon = createShopClusterIcon(context, group.size)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { marker, _ ->
                  map.controller.animateTo(marker.position)
                  map.controller.setZoom((map.zoomLevelDouble + 2.0).coerceAtMost(19.0))
                  true
                }
                map.overlays.add(this)
              }
            }
          }
          map.invalidate()
        }
      )

      Surface(
        color = JTSurfaceVariant.copy(alpha = 0.97f),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 8.dp,
        modifier = Modifier
          .align(Alignment.TopCenter)
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
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
                if (userLat != null && userLng != null) "Shops within 10 km radius"
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
                modifier = Modifier.height(34.dp).testTag("nearby_gps_button")
              ) { Text("Use my location", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            }
            OutlinedButton(
              onClick = onPickCity,
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
              modifier = Modifier.height(34.dp).testTag("nearby_city_picker")
            ) { Text("City", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
          }
        }
      }
    }

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
          Spacer(Modifier.height(10.dp))
          Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            nearbyShops.forEach { (shop, distance) ->
              val selected = shop.id == activeShop.id
              Surface(color = if (selected) JTOrange else JTSurfaceVariant, shape = RoundedCornerShape(18.dp), modifier = Modifier.clickable { onSelectShop(shop) }) {
                Text(text = shop.name + (distance?.let { " · " + String.format(Locale.US, "%.1f", it) + " km" } ?: ""), color = if (selected) Color.White else JTTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
              }
            }
          }
        }
      }
    }
  }
}


private fun createShopClusterIcon(context: Context, count: Int): BitmapDrawable {
  val density = context.resources.displayMetrics.density
  val size = (48f * density).toInt().coerceAtLeast(48)
  val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
  val canvas = AndroidCanvas(bitmap)
  val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL }
  val center = size / 2f
  canvas.drawCircle(center, center, center - 2f * density, paint)
  paint.color = android.graphics.Color.rgb(245, 96, 0)
  canvas.drawCircle(center, center, center - 5f * density, paint)
  paint.color = android.graphics.Color.WHITE
  paint.textAlign = Paint.Align.CENTER
  paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
  paint.textSize = (if (count > 99) 12f else 15f) * density
  val baseline = center - (paint.ascent() + paint.descent()) / 2f
  canvas.drawText(if (count > 99) "99+" else count.toString(), center, baseline, paint)
  return BitmapDrawable(context.resources, bitmap)
}

// ----------------------------------------------------
// TAB B: PRODUCTS FEED — INSTAGRAM REELS STYLE
// ----------------------------------------------------
@Composable
private data class ProductFeedEntry(val product: Product, val videoUrl: String?, val createdAt: String)

private fun ProductsFeedReelsView(
  products: List<Product>,
  videos: List<SupabaseProductVideoDto>,
  onProductClick: (Product) -> Unit,
  onAddToCart: (Product) -> Unit,
  onToggleWishlist: (Product) -> Unit,
  onShare: (Product) -> Unit
) {
  val feedEntries = remember(products, videos) {
    val videosByProduct = videos.groupBy { it.productId }
    val videoProductIds = videosByProduct.keys
    val entries = buildList {
      products.filterNot { it.id in videoProductIds }.forEach { add(ProductFeedEntry(it, null, it.createdAt)) }
      videos.forEach { video ->
        products.firstOrNull { it.id == video.productId }?.let { product ->
          add(ProductFeedEntry(product, video.videoUrl, video.createdAt))
        }
      }
    }
    val sessionSeed = products.size.toLong() * 31L + videos.size.toLong() * 17L
    entries.sortedWith(
      compareByDescending<ProductFeedEntry> { runCatching { java.time.Instant.parse(it.createdAt).toEpochMilli() }.getOrDefault(0L) }
        .thenByDescending { it.product.reviewCount }
        .thenByDescending { it.product.rating }
        .thenBy { (it.product.id.hashCode().toLong() xor sessionSeed).absoluteValue }
    )
  }
  if (feedEntries.isEmpty()) {
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
    val pagerState = rememberPagerState(pageCount = { feedEntries.size })

    VerticalPager(
      state = pagerState,
      modifier = Modifier.fillMaxSize()
    ) { page ->
      val entry = feedEntries[page]
      val product = entry.product

      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color(0xFFF5F5F5))
      ) {
        if (entry.videoUrl != null) {
          AndroidView(
            modifier = Modifier.fillMaxSize().align(Alignment.Center),
            factory = { ctx ->
              VideoView(ctx).apply {
                setVideoURI(Uri.parse(entry.videoUrl))
                setOnPreparedListener { player ->
                  player.isLooping = true
                  player.setVolume(0f, 0f)
                  if (page == pagerState.currentPage) start()
                }
              }
            },
            update = { player ->
              if (page == pagerState.currentPage) {
                if (!player.isPlaying) player.start()
              } else if (player.isPlaying) player.pause()
            }
          )
          Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
              Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
              Spacer(Modifier.width(5.dp))
              Text("Product video", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
          }
        } else {
          AsyncImage(
            model = product.imageUrl?.takeIf { it.isNotBlank() } ?: R.drawable.ic_launcher_background,
            contentDescription = product.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().align(Alignment.Center)
          )
        }

        // Bottom Gradient Overlay for text contrast
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.42f)
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
            .fillMaxWidth(0.88f)
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

          // 2. Add to Cart / out-of-stock state
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val dailyState = product.dailyStatus?.trim()?.lowercase()
            val isOutOfStock = (product.availabilityKnown && !product.isAvailable) ||
              dailyState in setOf("out_of_stock", "out-of-stock", "unavailable", "not_available", "not available", "sold_out", "sold out", "closed")
            if (isOutOfStock) {
              Text("Out of Stock", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            } else {
              FloatingReelsAction(
                icon = Icons.Filled.AddShoppingCart,
                tint = JTOrange,
                onClick = { onAddToCart(product) },
                tag = "reels_add_cart_btn_${product.id}"
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text("Add", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
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
