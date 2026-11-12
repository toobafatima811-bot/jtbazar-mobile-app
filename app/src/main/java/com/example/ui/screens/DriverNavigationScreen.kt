package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.supabase.SupabaseClient
import com.example.ui.theme.*
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
import java.util.Locale

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
  onStatusUpdated: () -> Unit,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

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

  // Route state
  var routePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
  var distanceText by remember { mutableStateOf<String?>(null) }
  var durationText by remember { mutableStateOf<String?>(null) }
  var isFetchingRoute by remember { mutableStateOf(false) }

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
                  durationText = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "$mins mins"
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
          durationText = "~$estMins mins"
        }
      }
      isFetchingRoute = false
    }
  }

  fun launchExternalNavigation() {
    if (!hasValidCustomerCoords) return
    try {
      val gmmIntentUri = Uri.parse("google.navigation:q=$customerLat,$customerLng&mode=d")
      val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
        setPackage("com.google.android.apps.maps")
      }
      if (mapIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(mapIntent)
      } else {
        val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$customerLat,$customerLng&travelmode=driving&dir_action=navigate")
        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
      }
    } catch (_: Exception) {
      val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$customerLat,$customerLng&travelmode=driving&dir_action=navigate")
      context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Customer Location", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Order #$orderNumber", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          if (hasValidCustomerCoords) {
            IconButton(onClick = { launchExternalNavigation() }) {
              Icon(Icons.Filled.Directions, contentDescription = "Google Maps", tint = Color(0xFFEA580C))
            }
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
    ) {
      if (!hasValidCustomerCoords) {
        // Missing coordinates view
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.size(68.dp)
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(Icons.Filled.LocationOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
            }
          }
          Spacer(Modifier.height(16.dp))
          Text("Customer GPS Pin Unavailable", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
          Spacer(Modifier.height(8.dp))
          Text(
            "This order does not have verified map coordinates saved by the customer. Please contact the customer or Admin for delivery location details.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
          )
          Spacer(Modifier.height(16.dp))
          Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("Customer: $customerName", fontWeight = FontWeight.SemiBold)
              Text("Address: $deliveryAddress", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text("Phone: $customerPhone", fontSize = 12.sp, color = Color(0xFF2563EB))
            }
          }
          Spacer(Modifier.height(20.dp))
          if (customerPhone.isNotBlank()) {
            Button(
              onClick = {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$customerPhone"))
                context.startActivity(dialIntent)
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Icon(Icons.Filled.Phone, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text("Call Customer ($customerPhone)")
            }
          }
          Spacer(Modifier.height(10.dp))
          OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Driver Center")
          }
        }
      } else {
        // Interactive road map with osmdroid
        var osmdroidMap by remember { mutableStateOf<MapView?>(null) }

        AndroidView(
          modifier = Modifier.fillMaxSize(),
          factory = { ctx ->
            Configuration.getInstance().userAgentValue = ctx.packageName
            Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))

            MapView(ctx).apply {
              setTileSource(TileSourceFactory.MAPNIK)
              setMultiTouchControls(true)
              isTilesScaledToDpi = true
              controller.setZoom(15.0)
              controller.setCenter(GeoPoint(customerLat, customerLng))
              osmdroidMap = this
            }
          },
          update = { map ->
            map.overlays.clear()

            // 1. Add route polyline if available
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

            // 2. Customer destination marker
            val customerMarker = Marker(map).apply {
              position = GeoPoint(customerLat, customerLng)
              title = "Destination: $customerName"
              snippet = deliveryAddress
              setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(customerMarker)

            // 3. Driver starting marker
            val dLoc = driverLocation
            if (dLoc != null) {
              val driverMarker = Marker(map).apply {
                position = GeoPoint(dLoc.latitude, dLoc.longitude)
                title = "Your Location (Driver)"
                snippet = "Starting point"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
              }
              map.overlays.add(driverMarker)

              // Adjust bounding box to show both points comfortably
              try {
                val box = BoundingBox.fromGeoPoints(listOf(GeoPoint(dLoc.latitude, dLoc.longitude), GeoPoint(customerLat, customerLng)))
                map.zoomToBoundingBox(box.increaseByScale(1.35f), true)
              } catch (_: Exception) {}
            } else {
              map.controller.setCenter(GeoPoint(customerLat, customerLng))
            }

            map.invalidate()
          }
        )

        // GPS status chip at top
        Row(
          modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.94f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(if (driverLocation != null) Color(0xFF16A34A) else Color(0xFFEA580C))
          )
          Spacer(Modifier.width(8.dp))
          Text(
            text = if (driverLocation != null) "GPS Live Route" else if (!hasLocationPermission) "Tap location permission" else "Acquiring GPS…",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = JTTextPrimary
          )
          if (isFetchingRoute) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Color(0xFFEA580C))
          }
        }

        // Recenter on Driver Floating Button
        FloatingActionButton(
          onClick = {
            val dLoc = driverLocation
            if (dLoc != null && osmdroidMap != null) {
              osmdroidMap?.controller?.animateTo(GeoPoint(dLoc.latitude, dLoc.longitude))
              osmdroidMap?.controller?.setZoom(16.5)
            } else if (!hasLocationPermission) {
              permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
              )
            }
          },
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = 240.dp)
            .size(46.dp),
          containerColor = Color.White,
          contentColor = Color(0xFF2563EB),
          shape = CircleShape
        ) {
          Icon(Icons.Filled.MyLocation, contentDescription = "My location", modifier = Modifier.size(22.dp))
        }

        // Recenter on Customer Floating Button
        FloatingActionButton(
          onClick = {
            osmdroidMap?.controller?.animateTo(GeoPoint(customerLat, customerLng))
            osmdroidMap?.controller?.setZoom(16.5)
          },
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = 184.dp)
            .size(46.dp),
          containerColor = Color.White,
          contentColor = Color(0xFFEA580C),
          shape = CircleShape
        ) {
          Icon(Icons.Filled.Place, contentDescription = "Customer location", modifier = Modifier.size(22.dp))
        }

        // Bottom Information & Navigation Panel
        Card(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(12.dp),
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // Header: Customer name, phone & call button
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Surface(
                    color = Color(0xFFFFF7ED),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDBA74))
                  ) {
                    Text(
                      text = "ORDER #$orderNumber",
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFFC2410C)
                    )
                  }
                  Text(
                    text = currentStatus.replace('_', ' ').uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                  )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                  text = customerName.ifBlank { "Customer" },
                  fontSize = 15.sp,
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
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEFF6FF))
                ) {
                  Icon(Icons.Filled.Phone, contentDescription = "Call", tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                }
              }
            }

            // Customer address row
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.Top,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Filled.Place, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(18.dp))
              Text(
                text = deliveryAddress,
                fontSize = 12.sp,
                color = JTTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
            }

            // Route statistics (Distance & Travel Time)
            if (distanceText != null || durationText != null) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Surface(
                  modifier = Modifier.weight(1f),
                  color = Color(0xFFF8FAFC),
                  shape = RoundedCornerShape(10.dp),
                  border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Icon(Icons.Filled.Straighten, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(16.dp))
                    Column {
                      Text("Distance", fontSize = 9.5.sp, color = JTTextSecondary)
                      Text(distanceText ?: "--", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary)
                    }
                  }
                }

                Surface(
                  modifier = Modifier.weight(1f),
                  color = Color(0xFFF8FAFC),
                  shape = RoundedCornerShape(10.dp),
                  border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Icon(Icons.Filled.Timer, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                    Column {
                      Text("Est. Travel Time", fontSize = 9.5.sp, color = JTTextSecondary)
                      Text(durationText ?: "--", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary)
                    }
                  }
                }
              }
            }

            actionError?.let { err ->
              Text(err, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            }

            // Primary: Start Navigation Button
            Button(
              onClick = { launchExternalNavigation() },
              modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
            ) {
              Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text("Start Google Maps Navigation", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            // Secondary: Delivery Status Buttons (Picked Up / Delivered)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              if (currentStatus == "assigned") {
                OutlinedButton(
                  enabled = !isBusy,
                  onClick = {
                    scope.launch {
                      isBusy = true
                      actionError = null
                      val r = SupabaseClient.rpc(
                        "driver_update_delivery_status",
                        JSONObject().put("p_assignment_id", assignmentId).put("p_action", "picked_up").toString()
                      )
                      if (r.isSuccess) {
                        currentStatus = "picked_up"
                        onStatusUpdated()
                      } else {
                        actionError = r.exceptionOrNull()?.message
                      }
                      isBusy = false
                    }
                  },
                  modifier = Modifier.weight(1f),
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Text(if (isBusy) "Updating…" else "Mark Picked Up", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
              }

              if (currentStatus != "delivered") {
                Button(
                  enabled = !isBusy,
                  onClick = {
                    scope.launch {
                      isBusy = true
                      actionError = null
                      val r = SupabaseClient.rpc(
                        "driver_update_delivery_status",
                        JSONObject().put("p_assignment_id", assignmentId).put("p_action", "delivered").toString()
                      )
                      if (r.isSuccess) {
                        currentStatus = "delivered"
                        onStatusUpdated()
                      } else {
                        actionError = r.exceptionOrNull()?.message
                      }
                      isBusy = false
                    }
                  },
                  modifier = Modifier.weight(1f),
                  shape = RoundedCornerShape(8.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) {
                  Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                  Spacer(Modifier.width(4.dp))
                  Text(if (isBusy) "Updating…" else "Mark Delivered", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      }
    }
  }
}
