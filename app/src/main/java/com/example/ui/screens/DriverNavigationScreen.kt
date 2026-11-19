package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.supabase.SupabaseClient
import com.example.localization.AppLanguageManager
import com.example.localization.appText
import com.example.ui.theme.JTTextPrimary
import com.example.ui.theme.JTTextSecondary
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import java.util.Locale

private val NavOrange = Color(0xFFEA580C)
private val NavOrangeDark = Color(0xFFC2410C)
private val NavOrangeSoft = Color(0xFFFFF7ED)
private val NavOrangeBorder = Color(0xFFFED7AA)
private val NavBlue = Color(0xFF2563EB)
private val NavBlueSoft = Color(0xFFEFF6FF)
private val NavBlueBorder = Color(0xFFBFDBFE)
private val NavGreen = Color(0xFF16A34A)
private val NavInk = Color(0xFF0F172A)
private val NavBorder = Color(0xFFE2E8F0)

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverNavigationScreen(
  orderNumber: String,
  customerName: String,
  customerPhone: String,
  deliveryAddress: String,
  customerLat: Double,
  customerLng: Double,
  assignmentStatus: String,
  assignmentId: String,
  otpVerified: Boolean = false,
  hasDeliveryProof: Boolean = false,
  isManualOrder: Boolean = false,
  onStatusUpdated: () -> Unit,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val session by SupabaseClient.session.collectAsState()
  val isAuthenticated = session != null && !SupabaseClient.currentUserId.isNullOrBlank()
  val isActiveDelivery = assignmentStatus.trim().lowercase() !in setOf("delivered", "cancelled", "failed")

  if (!isAuthenticated || !isActiveDelivery) {
    Scaffold(
      containerColor = Color(0xFFF6F7FB),
      topBar = {
        TopAppBar(
          title = {
            Text(
              appText("Customer Location", "کسٹمر کا مقام"),
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold
            )
          },
          navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = appText("Back", "واپس"))
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )
      }
    ) { padding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Card(
          modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth(),
          shape = RoundedCornerShape(22.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          border = BorderStroke(1.dp, NavBorder)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Surface(
              color = NavOrangeSoft,
              shape = CircleShape,
              modifier = Modifier.size(64.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = NavOrange, modifier = Modifier.size(32.dp))
              }
            }
            Text(
              text = if (!isAuthenticated) {
                appText("Driver Sign-In Required", "ڈرائیور سائن ان درکار ہے")
              } else {
                appText("Customer Location Unavailable", "کسٹمر کا مقام دستیاب نہیں")
              },
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold,
              color = JTTextPrimary,
              textAlign = TextAlign.Center
            )
            Text(
              text = if (!isAuthenticated) {
                appText(
                  "Sign in with an approved, enabled driver account to view active customer delivery details.",
                  "فعال کسٹمر کی ترسیل کی تفصیلات دیکھنے کے لیے منظور شدہ اور فعال ڈرائیور اکاؤنٹ سے سائن ان کریں۔"
                )
              } else {
                appText(
                  "Customer delivery details and GPS coordinates are hidden once a delivery is completed or cancelled.",
                  "ترسیل مکمل یا منسوخ ہونے کے بعد کسٹمر کی تفصیلات اور جی پی ایس کوآرڈینیٹس مخفی کر دیے جاتے ہیں۔"
                )
              },
              fontSize = 12.5.sp,
              color = JTTextSecondary,
              textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Button(
              onClick = onBack,
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = NavOrange)
            ) {
              Text(appText("Back to Driver Center", "ڈرائیور سینٹر پر واپس جائیں"), fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
    return
  }

  var driverLocation by remember { mutableStateOf<Location?>(null) }
  var hasLocationPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { perms ->
    val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
      perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    hasLocationPermission = granted
    if (granted) {
      val fused = LocationServices.getFusedLocationProviderClient(context)
      fused.lastLocation.addOnSuccessListener { loc ->
        if (loc != null) driverLocation = loc
      }
      fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
        if (loc != null) driverLocation = loc
      }
    }
  }

  // Request location on launch if not granted, or obtain current location
  LaunchedEffect(Unit) {
    if (!hasLocationPermission) {
      permissionLauncher.launch(
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
      )
    } else {
      val fused = LocationServices.getFusedLocationProviderClient(context)
      fused.lastLocation.addOnSuccessListener { loc ->
        if (loc != null) driverLocation = loc
      }
      fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
        if (loc != null) driverLocation = loc
      }
    }
  }

  // Keep the driver's position fresh while this navigation screen is visible.
  DisposableEffect(hasLocationPermission) {
    if (!hasLocationPermission) return@DisposableEffect onDispose { }
    val fused = LocationServices.getFusedLocationProviderClient(context)
    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
      .setMinUpdateIntervalMillis(1500L)
      .setWaitForAccurateLocation(true)
      .build()
    val callback = object : LocationCallback() {
      override fun onLocationResult(result: LocationResult) {
        result.lastLocation?.let { driverLocation = it }
      }
    }
    try {
      fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
    } catch (_: SecurityException) { }
    onDispose { fused.removeLocationUpdates(callback) }
  }

  // Route state
  var routePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
  var distanceText by remember { mutableStateOf<String?>(null) }
  var durationText by remember { mutableStateOf<String?>(null) }
  var isFetchingRoute by remember { mutableStateOf(false) }
  var osmdroidMap by remember { mutableStateOf<MapView?>(null) }
  var mapCameraInitialized by remember { mutableStateOf(false) }

  // Action status state
  var currentStatus by remember { mutableStateOf(assignmentStatus) }
  var isBusy by remember { mutableStateOf(false) }
  var actionError by remember { mutableStateOf<String?>(null) }

  val hasValidCustomerCoords = customerLat.isFinite() && customerLng.isFinite() &&
    !(customerLat == 0.0 && customerLng == 0.0) && customerLat in -90.0..90.0 && customerLng in -180.0..180.0

  // Fetch real road route using public OSRM router
  LaunchedEffect(driverLocation, customerLat, customerLng) {
    val dLoc = driverLocation
    if (dLoc != null && hasValidCustomerCoords) {
      isFetchingRoute = true
      val dLat = dLoc.latitude
      val dLng = dLoc.longitude

      withContext(Dispatchers.IO) {
        try {
          val url = "https://router.project-osrm.org/route/v1/driving/$dLng,$dLat;$customerLng,$customerLat?overview=full&geometries=geojson"
          val req = Request.Builder().url(url).build()
          SupabaseClient.okHttpClient.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
              val json = JSONObject(resp.body?.string().orEmpty())
              val routes = json.optJSONArray("routes")
              if (routes != null && routes.length() > 0) {
                val routeObj = routes.getJSONObject(0)
                val meters = routeObj.optDouble("distance", 0.0)
                val seconds = routeObj.optDouble("duration", 0.0)

                val geom = routeObj.optJSONObject("geometry")
                val coords = geom?.optJSONArray("coordinates")
                val pts = mutableListOf<GeoPoint>()
                if (coords != null) {
                  for (i in 0 until coords.length()) {
                    val coord = coords.getJSONArray(i)
                    val lng = coord.getDouble(0)
                    val lat = coord.getDouble(1)
                    pts.add(GeoPoint(lat, lng))
                  }
                }
                withContext(Dispatchers.Main) {
                  routePoints = pts
                  distanceText = String.format(Locale.US, "%.1f km", meters / 1000.0)
                  val mins = (seconds / 60.0).toInt().coerceAtLeast(1)
                  durationText = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "$mins min"
                }
                return@withContext
              }
            }
          }
        } catch (_: Exception) {}

        // Fallback: geodesic straight line & distance between
        val results = FloatArray(1)
        Location.distanceBetween(dLat, dLng, customerLat, customerLng, results)
        val distKm = results[0] / 1000.0
        val estMins = ((distKm / 35.0) * 60).toInt().coerceAtLeast(1)
        withContext(Dispatchers.Main) {
          routePoints = listOf(GeoPoint(dLat, dLng), GeoPoint(customerLat, customerLng))
          distanceText = String.format(Locale.US, "%.1f km", distKm)
          durationText = "~$estMins min"
        }
      }
      isFetchingRoute = false
    }
  }

  val statusRpcName = if (isManualOrder) {
    "driver_update_manual_order_delivery_status"
  } else {
    "driver_update_delivery_status"
  }

  Scaffold(
    containerColor = Color(0xFFF6F7FB),
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      // Express Courier Gradient Top Bar
      Surface(shadowElevation = 6.dp, color = Color.Transparent) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              Brush.horizontalGradient(
                colors = listOf(NavOrange, NavOrangeDark)
              )
            )
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
          Row(
            modifier = Modifier
              .widthIn(max = 640.dp)
              .fillMaxWidth()
              .align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            IconButton(
              onClick = onBack,
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
            ) {
              Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = appText("Back", "واپس"),
                tint = Color.White
              )
            }

            Column(
              modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                appText("Customer Location", "کسٹمر کا مقام"),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                appText("Order #$orderNumber", "آرڈر #$orderNumber"),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFFFEDD5),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }

            if (hasValidCustomerCoords) {
              IconButton(
                onClick = {
                  osmdroidMap?.controller?.animateTo(GeoPoint(customerLat, customerLng))
                  osmdroidMap?.controller?.setZoom(16.5)
                },
                modifier = Modifier
                  .size(48.dp)
                  .clip(CircleShape)
                  .background(Color.White.copy(alpha = 0.2f))
              ) {
                Icon(
                  Icons.Filled.Directions,
                  contentDescription = appText("Focus Customer", "کسٹمر پر توجہ مرکوز کریں"),
                  tint = Color.White
                )
              }
            } else {
              Spacer(Modifier.size(48.dp))
            }
          }
        }
      }
    }
  ) { padding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
    ) {
      if (!hasValidCustomerCoords) {
        // Missing coordinates view (with preserved Open Address in Maps fallback)
        Box(
          modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(20.dp),
          contentAlignment = Alignment.Center
        ) {
          Card(
            modifier = Modifier
              .widthIn(max = 560.dp)
              .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, NavBorder)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(68.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    Icons.Filled.LocationOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(34.dp)
                  )
                }
              }
              Text(
                appText("Customer GPS Pin Unavailable", "کسٹمر کا جی پی ایس پن دستیاب نہیں"),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
              )
              Text(
                appText(
                  "This order does not have verified map coordinates saved by the customer. Please contact the customer or Admin for delivery location details.",
                  "اس آرڈر میں کسٹمر کی جانب سے تصدیق شدہ نقشے کے کوآرڈینیٹس محفوظ نہیں ہیں۔ ترسیل کے مقام کے لیے کسٹمر یا ایڈمن سے رابطہ کریں۔"
                ),
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )
              Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, NavBorder)
              ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                  Text(
                    appText("Customer: $customerName", "کسٹمر: $customerName"),
                    fontWeight = FontWeight.Bold,
                    color = NavInk
                  )
                  Text(
                    appText("Address: $deliveryAddress", "پتہ: $deliveryAddress"),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  if (customerPhone.isNotBlank()) {
                    Text(
                      appText("Phone: $customerPhone", "فون: $customerPhone"),
                      fontSize = 12.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = NavBlue
                    )
                  }
                }
              }
              if (deliveryAddress.isNotBlank()) {
                OutlinedButton(
                  onClick = {
                    val mapUri = Uri.parse("geo:0,0?q=${Uri.encode(deliveryAddress)}")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, mapUri)) }
                  },
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                  shape = RoundedCornerShape(12.dp),
                  border = BorderStroke(1.dp, NavOrangeBorder),
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = NavOrangeDark)
                ) {
                  Icon(Icons.Filled.Map, contentDescription = null)
                  Spacer(Modifier.width(8.dp))
                  Text(appText("Open Address in Maps", "نقشے میں پتہ کھولیں"), fontWeight = FontWeight.SemiBold)
                }
              }
              if (customerPhone.isNotBlank()) {
                Button(
                  onClick = {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$customerPhone"))
                    context.startActivity(dialIntent)
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = NavBlue),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                ) {
                  Icon(Icons.Filled.Phone, contentDescription = null)
                  Spacer(Modifier.width(8.dp))
                  Text(appText("Call Customer ($customerPhone)", "کسٹمر کو کال کریں ($customerPhone)"), fontWeight = FontWeight.Bold)
                }
              }
              OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp)
              ) {
                Text(appText("Back to Driver Center", "ڈرائیور سینٹر پر واپس جائیں"), fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }
      } else {
        // Interactive road map with osmdroid
        AndroidView(
          modifier = Modifier.fillMaxSize(),
          factory = { ctx ->
            Configuration.getInstance().userAgentValue = ctx.packageName
            Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))

            MapView(ctx).apply {
              setTileSource(TileSourceFactory.MAPNIK)
              setMultiTouchControls(true)
              val rotationGestures = RotationGestureOverlay(this).apply { isEnabled = true }
              overlays.add(rotationGestures)
              isTilesScaledToDpi = true
              controller.setZoom(15.0)
              controller.setCenter(GeoPoint(customerLat, customerLng))
              osmdroidMap = this
            }
          },
          update = { map ->
            val rotationOverlay = map.overlays.filterIsInstance<RotationGestureOverlay>().firstOrNull()
            map.overlays.clear()
            rotationOverlay?.let { map.overlays.add(it) }

            if (routePoints.isNotEmpty()) {
              val line = Polyline(map).apply {
                setPoints(routePoints)
                outlinePaint.color = android.graphics.Color.parseColor("#EA580C")
                outlinePaint.strokeWidth = 14f
                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
              }
              map.overlays.add(line)
            }

            val customerMarker = Marker(map).apply {
              position = GeoPoint(customerLat, customerLng)
              title = AppLanguageManager.tr("Destination: $customerName", "منزل: $customerName")
              snippet = deliveryAddress
              setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(customerMarker)

            val dLoc = driverLocation
            if (dLoc != null) {
              val driverMarker = Marker(map).apply {
                position = GeoPoint(dLoc.latitude, dLoc.longitude)
                title = AppLanguageManager.tr("Your Location (Driver)", "آپ کا مقام (ڈرائیور)")
                snippet = AppLanguageManager.tr("Starting point", "ابتدائی مقام")
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
              }
              map.overlays.add(driverMarker)

              if (!mapCameraInitialized) {
                try {
                  val box = BoundingBox.fromGeoPoints(listOf(GeoPoint(dLoc.latitude, dLoc.longitude), GeoPoint(customerLat, customerLng)))
                  map.zoomToBoundingBox(box.increaseByScale(1.35f), true)
                  mapCameraInitialized = true
                } catch (_: Exception) { }
              }
            } else if (!mapCameraInitialized) {
              map.controller.setCenter(GeoPoint(customerLat, customerLng))
              mapCameraInitialized = true
            }

            map.invalidate()
          }
        )

        // Dark frosted GPS Live Route chip at top
        Surface(
          color = NavInk.copy(alpha = 0.88f),
          shape = RoundedCornerShape(24.dp),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
          modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 12.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (driverLocation != null) Color(0xFF22C55E) else NavOrange)
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = when {
                driverLocation != null -> appText("GPS Live Route", "لائیو جی پی ایس روٹ")
                !hasLocationPermission -> appText("Tap location permission", "لوکیشن کی اجازت دیں")
                else -> appText("Acquiring GPS…", "جی پی ایس حاصل کیا جا رہا ہے…")
              },
              fontSize = 11.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color.White
            )
            if (isFetchingRoute) {
              Spacer(Modifier.width(8.dp))
              CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Color(0xFFFDBA74))
            }
          }
        }

        // Top-right floating recenter FABs (48.dp touch target, never collides with bottom sheet)
        Column(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 12.dp, end = 12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          FloatingActionButton(
            onClick = {
              val dLoc = driverLocation
              if (dLoc != null && osmdroidMap != null) {
                osmdroidMap?.controller?.animateTo(GeoPoint(dLoc.latitude, dLoc.longitude))
                osmdroidMap?.controller?.setZoom(17.0)
                osmdroidMap?.mapOrientation = 0f
              } else if (!hasLocationPermission) {
                permissionLauncher.launch(
                  arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
              }
            },
            modifier = Modifier.size(48.dp),
            containerColor = Color.White,
            contentColor = NavBlue,
            shape = CircleShape
          ) {
            Icon(Icons.Filled.MyLocation, contentDescription = appText("My location", "میرا مقام"), modifier = Modifier.size(22.dp))
          }

          FloatingActionButton(
            onClick = {
              osmdroidMap?.controller?.animateTo(GeoPoint(customerLat, customerLng))
              osmdroidMap?.controller?.setZoom(16.5)
            },
            modifier = Modifier.size(48.dp),
            containerColor = Color.White,
            contentColor = NavOrange,
            shape = CircleShape
          ) {
            Icon(Icons.Filled.Place, contentDescription = appText("Customer location", "کسٹمر کا مقام"), modifier = Modifier.size(22.dp))
          }
        }

        // Bottom Navigation Sheet (26.dp rounded corners, constrained width on tablets)
        Box(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
          contentAlignment = Alignment.BottomCenter
        ) {
          Card(
            modifier = Modifier
              .widthIn(max = 600.dp)
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 10.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            border = BorderStroke(1.dp, NavBorder)
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              // Sheet drag handle
              Box(
                modifier = Modifier
                  .align(Alignment.CenterHorizontally)
                  .width(38.dp)
                  .height(4.dp)
                  .clip(RoundedCornerShape(2.dp))
                  .background(Color(0xFFCBD5E1))
              )

              // Header: Customer name, status badge & 48dp call button
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Surface(
                      color = NavOrangeSoft,
                      shape = RoundedCornerShape(8.dp),
                      border = BorderStroke(1.dp, NavOrangeBorder)
                    ) {
                      Text(
                        text = appText("ORDER #$orderNumber", "آرڈر #$orderNumber"),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavOrangeDark
                      )
                    }
                    Text(
                      text = driverAssignmentStatusLabel(currentStatus),
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFF64748B)
                    )
                  }
                  Spacer(Modifier.height(4.dp))
                  Text(
                    text = customerName.ifBlank { appText("Customer", "کسٹمر") },
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = JTTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }

                if (customerPhone.isNotBlank()) {
                  IconButton(
                    onClick = {
                      val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$customerPhone"))
                      context.startActivity(dial)
                    },
                    modifier = Modifier
                      .size(48.dp)
                      .clip(CircleShape)
                      .background(NavBlueSoft)
                      .border(1.dp, NavBlueBorder, CircleShape)
                  ) {
                    Icon(
                      Icons.Filled.Phone,
                      contentDescription = appText("Call Customer", "کسٹمر کو کال کریں"),
                      tint = NavBlue,
                      modifier = Modifier.size(20.dp)
                    )
                  }
                }
              }

              // Customer address row
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(Icons.Filled.Place, contentDescription = null, tint = NavOrange, modifier = Modifier.size(18.dp))
                Text(
                  text = deliveryAddress,
                  fontSize = 12.sp,
                  color = JTTextSecondary,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f)
                )
              }

              // Tinted Route Statistics (Distance & Travel Time)
              if (distanceText != null || durationText != null) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Surface(
                    modifier = Modifier.weight(1f),
                    color = NavOrangeSoft,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NavOrangeBorder)
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      Icon(Icons.Filled.Straighten, contentDescription = null, tint = NavOrange, modifier = Modifier.size(17.dp))
                      Column {
                        Text(appText("Distance", "فاصلہ"), fontSize = 10.sp, color = NavOrangeDark)
                        Text(distanceText ?: "--", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary)
                      }
                    }
                  }

                  Surface(
                    modifier = Modifier.weight(1f),
                    color = NavBlueSoft,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NavBlueBorder)
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      Icon(Icons.Filled.Timer, contentDescription = null, tint = NavBlue, modifier = Modifier.size(17.dp))
                      Column {
                        Text(appText("Est. Travel Time", "متوقع وقت"), fontSize = 10.sp, color = NavBlue)
                        Text(durationText ?: "--", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary)
                      }
                    }
                  }
                }
              }

              actionError?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
              }

              // Primary: Continue In-App Navigation
              Button(
                onClick = {
                  osmdroidMap?.controller?.animateTo(GeoPoint(customerLat, customerLng))
                  osmdroidMap?.controller?.setZoom(16.5)
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavOrange)
              ) {
                Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                  appText("Continue In-App Navigation", "ایپ میں نیویگیشن جاری رکھیں"),
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold
                )
              }

              // Secondary: Delivery Status Actions
              if (currentStatus == "picked_up" && !(otpVerified && hasDeliveryProof)) {
                Text(
                  text = missingCompletionStepsMessage(otpVerified, hasDeliveryProof)
                    ?: appText(
                      "Verify customer OTP and upload delivery photo proof in Driver Center before completing delivery.",
                      "ترسیل مکمل کرنے سے پہلے ڈرائیور سینٹر میں کسٹمر او ٹی پی کی تصدیق کریں اور ترسیل کی تصویر اپ لوڈ کریں۔"
                    ),
                  fontSize = 11.sp,
                  color = Color(0xFF92400E),
                  fontWeight = FontWeight.Medium
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                if (currentStatus == "assigned") {
                  OutlinedButton(
                    enabled = !isBusy,
                    onClick = {
                      if (isBusy) return@OutlinedButton
                      scope.launch {
                        isBusy = true
                        actionError = null
                        val r = SupabaseClient.rpc(
                          statusRpcName,
                          JSONObject().put("p_assignment_id", assignmentId).put("p_action", "picked_up").toString(),
                          requireAuth = true
                        )
                        if (r.isSuccess) {
                          currentStatus = "picked_up"
                          onStatusUpdated()
                        } else {
                          actionError = extractDriverFriendlyError(
                            r.exceptionOrNull(),
                            AppLanguageManager.tr("Could not update pickup status.", "پک اپ اسٹیٹس اپ ڈیٹ نہیں ہو سکا۔")
                          )
                        }
                        isBusy = false
                      }
                    },
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp)
                  ) {
                    Text(
                      if (isBusy) appText("Updating…", "اپ ڈیٹ ہو رہا ہے…")
                      else appText("Mark Picked Up", "پارسل اٹھا لیا"),
                      fontSize = 12.sp,
                      fontWeight = FontWeight.SemiBold
                    )
                  }
                } else if (currentStatus == "picked_up") {
                  if (otpVerified && hasDeliveryProof) {
                    Button(
                      enabled = !isBusy,
                      onClick = {
                        if (isBusy) return@Button
                        scope.launch {
                          isBusy = true
                          actionError = null
                          val r = SupabaseClient.rpc(
                            statusRpcName,
                            JSONObject().put("p_assignment_id", assignmentId).put("p_action", "delivered").toString(),
                            requireAuth = true
                          )
                          if (r.isSuccess) {
                            currentStatus = "delivered"
                            onStatusUpdated()
                          } else {
                            actionError = extractDriverFriendlyError(
                              r.exceptionOrNull(),
                              AppLanguageManager.tr("Could not complete delivery.", "ترسیل مکمل نہیں ہو سکی۔")
                            )
                          }
                          isBusy = false
                        }
                      },
                      modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                      shape = RoundedCornerShape(12.dp),
                      colors = ButtonDefaults.buttonColors(containerColor = NavGreen)
                    ) {
                      Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(6.dp))
                      Text(
                        if (isBusy) appText("Completing…", "مکمل ہو رہا ہے…")
                        else appText("Complete Delivery", "ترسیل مکمل کریں"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                      )
                    }
                  } else {
                    Button(
                      enabled = !isBusy,
                      onClick = onBack,
                      modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                      shape = RoundedCornerShape(12.dp),
                      colors = ButtonDefaults.buttonColors(containerColor = NavBlue)
                    ) {
                      Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(6.dp))
                      Text(
                        appText("Verify OTP & Upload Proof", "او ٹی پی تصدیق و تصویر اپ لوڈ"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
