package com.example.ui.screens

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.result.IntentSenderRequest
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.location.AddressReverseGeocoder
import com.example.location.LocationFetchResult
import com.example.location.LocationResolver
import com.example.location.toReadableFullAddress
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.util.Locale
import com.example.data.supabase.GlobalLoadingState
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.R
import com.example.data.supabase.SupabaseClient
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

fun driverAddressText(delivery: JSONObject): String {
  val raw = delivery.opt("delivery_address")
  val address = when (raw) {
    is JSONObject -> raw
    is String -> try { JSONObject(raw) } catch (_: Exception) { null }
    else -> null
  }
  if (address != null) {
    val parts = listOf(
      address.optString("address_line_1"),
      address.optString("address_line_2"),
      address.optString("area"),
      address.optString("city"),
      address.optString("province"),
      address.optString("postal_code")
    ).map { it.trim() }.filter { it.isNotBlank() && it != "null" }
    if (parts.isNotEmpty()) return parts.distinct().joinToString(", ")
  }
  return delivery.optString("delivery_location_name")
    .takeIf { it.isNotBlank() && it != "null" }
    ?: "Saved delivery address"
}

enum class DriverDocType {
  CNIC_FRONT,
  CNIC_BACK,
  VEHICLE_PLATE
}

data class DocUploadItem(
  val localUri: Uri? = null,
  val localBitmap: Bitmap? = null,
  val storagePath: String? = null,
  val isUploading: Boolean = false,
  val errorMessage: String? = null
)

enum class DriverDeliveryFilter(val label: String) {
  ALL("All"),
  PENDING("Pending"),
  CONFIRMED("Confirmed"),
  PROCESSING("Processing"),
  ACTIVE("Active"),
  SHIPPED("Shipped"),
  PICKED_UP("In Transit"),
  DELIVERED("Delivered"),
  CANCELLED("Cancelled")
}

enum class DriverBottomNavTab {
  DELIVERIES,
  HISTORY,
  PROFILE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverCenterScreen(
  onBack: () -> Unit,
  onOpenProfile: () -> Unit = {},
  onOpenNotifications: () -> Unit = {},
  onViewCustomerLocation: (JSONObject) -> Unit = {}
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val user by SupabaseClient.currentUser.collectAsState()

  var enabled by remember { mutableStateOf(false) }
  var application by remember { mutableStateOf<JSONObject?>(null) }
  var deliveries by remember { mutableStateOf(listOf<JSONObject>()) }

  // Filter and Nav state
  var selectedFilter by remember { mutableStateOf(DriverDeliveryFilter.ALL) }
  var selectedTab by remember { mutableStateOf(DriverBottomNavTab.DELIVERIES) }

  // Form input fields
  var fullName by remember { mutableStateOf(user?.fullName.orEmpty()) }
  var phone by remember { mutableStateOf(user?.phone.orEmpty()) }
  var currentAddress by remember { mutableStateOf("") }
  var applicationLat by remember { mutableStateOf<Double?>(null) }
  var applicationLng by remember { mutableStateOf<Double?>(null) }
  var locatingAddress by remember { mutableStateOf(false) }
  var addressLocationStatus by remember { mutableStateOf<String?>(null) }
  var addressLocationError by remember { mutableStateOf<String?>(null) }
  var showApplicationLocationPicker by remember { mutableStateOf(false) }
  var vehicleType by remember { mutableStateOf("Bike") }
  var isVehicleDropdownExpanded by remember { mutableStateOf(false) }

  // Uploaded document states
  var cnicFrontDoc by remember { mutableStateOf(DocUploadItem()) }
  var cnicBackDoc by remember { mutableStateOf(DocUploadItem()) }
  var plateDoc by remember { mutableStateOf(DocUploadItem()) }

  // Active target for camera/gallery picker
  var activeDocForPicker by remember { mutableStateOf<DriverDocType?>(null) }
  var showPickerSheet by remember { mutableStateOf(false) }
  var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

  var loading by remember { mutableStateOf(true) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var message by remember { mutableStateOf<String?>(null) }

  suspend fun applyDriverDeviceLocation(location: android.location.Location) {
    applicationLat = location.latitude
    applicationLng = location.longitude
    val resolved = AddressReverseGeocoder.resolve(context, location.latitude, location.longitude)
    val fullAddr = resolved?.toReadableFullAddress().orEmpty()
    if (fullAddr.isNotBlank()) {
      currentAddress = fullAddr
      addressLocationError = null
      addressLocationStatus = "Current location & street address filled. Verify or adjust on map if needed."
    } else {
      addressLocationError = null
      addressLocationStatus = "Location coordinates captured. Please enter or verify your street address."
    }
  }

  lateinit var startDriverLocationFetch: () -> Unit

  val driverLocationSettingsLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      startDriverLocationFetch()
    } else {
      locatingAddress = false
      addressLocationError = "Turn on device location / GPS to fill your current address."
    }
  }

  startDriverLocationFetch = {
    locatingAddress = true
    addressLocationError = null
    addressLocationStatus = null
    scope.launch {
      try {
        when (val result = LocationResolver.fetchCurrentLocation(context)) {
          is LocationFetchResult.Success -> {
            applyDriverDeviceLocation(result.location)
            locatingAddress = false
          }
          is LocationFetchResult.NeedsResolution -> {
            runCatching {
              val request = IntentSenderRequest.Builder(result.exception.resolution).build()
              driverLocationSettingsLauncher.launch(request)
            }.onFailure {
              locatingAddress = false
              addressLocationError = "Turn on device location / GPS and try again."
            }
          }
          is LocationFetchResult.Failure -> {
            locatingAddress = false
            addressLocationError = result.message
          }
        }
      } catch (_: Exception) {
        locatingAddress = false
        addressLocationError = "Unable to detect current location. Please try again."
      }
    }
  }

  val driverLocationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    if (granted) {
      startDriverLocationFetch()
    } else {
      locatingAddress = false
      addressLocationError = "Location permission denied. Grant permission or pick on map."
    }
  }

  fun requestDriverCurrentLocation() {
    if (LocationResolver.hasLocationPermission(context)) {
      startDriverLocationFetch()
    } else {
      driverLocationPermissionLauncher.launch(
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
      )
    }
  }

  val isApprovedDriver = application?.optString("status") == "approved"

  suspend fun uploadDocBytes(docType: DriverDocType, bytes: ByteArray, uri: Uri?, bitmap: Bitmap?) {
    val uid = user?.id ?: return
    val prefix = when (docType) {
      DriverDocType.CNIC_FRONT -> "cnic_front"
      DriverDocType.CNIC_BACK -> "cnic_back"
      DriverDocType.VEHICLE_PLATE -> "vehicle_plate"
    }

    val uploadingItem = DocUploadItem(localUri = uri, localBitmap = bitmap, isUploading = true)
    when (docType) {
      DriverDocType.CNIC_FRONT -> cnicFrontDoc = uploadingItem
      DriverDocType.CNIC_BACK -> cnicBackDoc = uploadingItem
      DriverDocType.VEHICLE_PLATE -> plateDoc = uploadingItem
    }

    val path = "$uid/${prefix}_${System.currentTimeMillis()}.jpg"
    val result = SupabaseClient.uploadPrivateFile("driver-documents", path, bytes, "image/jpeg")

    result.fold(
      onSuccess = { savedPath ->
        val completed = DocUploadItem(localUri = uri, localBitmap = bitmap, storagePath = savedPath, isUploading = false)
        when (docType) {
          DriverDocType.CNIC_FRONT -> cnicFrontDoc = completed
          DriverDocType.CNIC_BACK -> cnicBackDoc = completed
          DriverDocType.VEHICLE_PLATE -> plateDoc = completed
        }
      },
      onFailure = { err ->
        val failed = DocUploadItem(localUri = uri, localBitmap = bitmap, isUploading = false, errorMessage = err.message ?: "Upload failed")
        when (docType) {
          DriverDocType.CNIC_FRONT -> cnicFrontDoc = failed
          DriverDocType.CNIC_BACK -> cnicBackDoc = failed
          DriverDocType.VEHICLE_PLATE -> plateDoc = failed
        }
      }
    )
  }

  // Camera photo capture launcher
  val takePictureLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.TakePicture()
  ) { success ->
    if (success && tempCameraUri != null && activeDocForPicker != null) {
      val target = activeDocForPicker!!
      val uri = tempCameraUri!!
      scope.launch(Dispatchers.IO) {
        try {
          val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          if (bytes != null && bytes.isNotEmpty()) {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            uploadDocBytes(target, bytes, uri, bmp)
          }
        } catch (e: Exception) {
          withContext(Dispatchers.Main) { error = "Could not read camera photo: ${e.message}" }
        }
      }
    }
  }

  // Gallery photo picker launcher
  val pickMediaLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null && activeDocForPicker != null) {
      val target = activeDocForPicker!!
      scope.launch(Dispatchers.IO) {
        try {
          val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          if (bytes != null && bytes.isNotEmpty()) {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            uploadDocBytes(target, bytes, uri, bmp)
          }
        } catch (e: Exception) {
          withContext(Dispatchers.Main) { error = "Could not read selected photo: ${e.message}" }
        }
      }
    }
  }

  // Camera permission launcher
  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      try {
        val tempFile = File.createTempFile("driver_doc_", ".jpg", context.cacheDir)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
        tempCameraUri = uri
        takePictureLauncher.launch(uri)
      } catch (e: Exception) {
        error = "Failed to launch camera: ${e.message}"
      }
    } else {
      error = "Camera permission is required to take photo"
    }
  }

  fun startCameraCapture() {
    val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    if (hasCam) {
      try {
        val tempFile = File.createTempFile("driver_doc_", ".jpg", context.cacheDir)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
        tempCameraUri = uri
        takePictureLauncher.launch(uri)
      } catch (e: Exception) {
        error = "Failed to launch camera: ${e.message}"
      }
    } else {
      cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  suspend fun load() {
    loading = true
    error = null
    try {
      val access = SupabaseClient.rpc("get_my_driver_access").getOrThrow()
      enabled = access.trim().equals("true", ignoreCase = true)
      val uid = user?.id ?: throw IllegalStateException("Please sign in first.")
      val appRows = JSONArray(
        SupabaseClient.get("driver_applications?select=id,full_name,phone,vehicle_type,current_address,status,admin_note&user_id=eq.$uid&limit=1").getOrThrow()
      )
      application = if (appRows.length() > 0) appRows.getJSONObject(0) else null

      // Security check: Customer locations & deliveries are only accessible to approved drivers
      deliveries = if (application?.optString("status") == "approved") {
        JSONArray(SupabaseClient.rpc("driver_list_assigned_orders").getOrThrow()).let { rows ->
          (0 until rows.length()).map { rows.getJSONObject(it) }
        }
      } else emptyList()
    } catch (e: Exception) {
      error = e.message ?: "Could not load driver information."
    } finally {
      loading = false
    }
  }

  LaunchedEffect(user?.id) { load() }

  // Delivery filter counts
  val allCount = deliveries.size
  val activeCount = deliveries.count { it.optString("assignment_status", "assigned") in setOf("assigned", "pending") }
  val pickedUpCount = deliveries.count { it.optString("assignment_status") in setOf("picked_up", "in_transit") }
  val deliveredCount = deliveries.count { it.optString("assignment_status") == "delivered" }

  val filteredDeliveries = remember(deliveries, selectedFilter, selectedTab) {
    if (selectedTab == DriverBottomNavTab.HISTORY) {
      deliveries.filter { it.optString("assignment_status") in setOf("delivered", "cancelled") }
    } else {
      when (selectedFilter) {
        DriverDeliveryFilter.ALL -> deliveries
        DriverDeliveryFilter.PENDING -> deliveries.filter { it.optString("order_status").equals("pending", true) }
        DriverDeliveryFilter.CONFIRMED -> deliveries.filter { it.optString("order_status").equals("confirmed", true) }
        DriverDeliveryFilter.PROCESSING -> deliveries.filter { it.optString("order_status").equals("processing", true) }
        DriverDeliveryFilter.ACTIVE -> deliveries.filter { it.optString("assignment_status", "assigned") in setOf("assigned", "pending") }
        DriverDeliveryFilter.SHIPPED -> deliveries.filter { it.optString("order_status").equals("shipped", true) }
        DriverDeliveryFilter.PICKED_UP -> deliveries.filter { it.optString("assignment_status") in setOf("picked_up", "in_transit") }
        DriverDeliveryFilter.DELIVERED -> deliveries.filter { it.optString("assignment_status") == "delivered" }
        DriverDeliveryFilter.CANCELLED -> deliveries.filter { it.optString("assignment_status") == "cancelled" }
      }
    }
  }

  Scaffold(
    containerColor = Color(0xFFF8FAFC),
    bottomBar = {
      if (isApprovedDriver) {
        Surface(
          color = Color.White,
          shadowElevation = 10.dp,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .navigationBarsPadding()
              .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Tab 1: Deliveries
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .clickable {
                  selectedTab = DriverBottomNavTab.DELIVERIES
                  selectedFilter = DriverDeliveryFilter.ALL
                }
                .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
              Icon(
                Icons.Filled.LocalShipping,
                contentDescription = "Deliveries",
                tint = if (selectedTab == DriverBottomNavTab.DELIVERIES) Color(0xFFEA580C) else Color(0xFF94A3B8),
                modifier = Modifier.size(24.dp)
              )
              Spacer(Modifier.height(3.dp))
              Text(
                "Deliveries",
                fontSize = 11.sp,
                fontWeight = if (selectedTab == DriverBottomNavTab.DELIVERIES) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedTab == DriverBottomNavTab.DELIVERIES) Color(0xFFEA580C) else Color(0xFF94A3B8)
              )
              if (selectedTab == DriverBottomNavTab.DELIVERIES) {
                Box(
                  modifier = Modifier
                    .padding(top = 4.dp)
                    .width(28.dp)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFEA580C))
                )
              }
            }

            // Tab 2: History
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .clickable {
                  selectedTab = DriverBottomNavTab.HISTORY
                }
                .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
              Icon(
                Icons.Filled.ReceiptLong,
                contentDescription = "History",
                tint = if (selectedTab == DriverBottomNavTab.HISTORY) Color(0xFFEA580C) else Color(0xFF94A3B8),
                modifier = Modifier.size(24.dp)
              )
              Spacer(Modifier.height(3.dp))
              Text(
                "History",
                fontSize = 11.sp,
                fontWeight = if (selectedTab == DriverBottomNavTab.HISTORY) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedTab == DriverBottomNavTab.HISTORY) Color(0xFFEA580C) else Color(0xFF94A3B8)
              )
              if (selectedTab == DriverBottomNavTab.HISTORY) {
                Box(
                  modifier = Modifier
                    .padding(top = 4.dp)
                    .width(28.dp)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFEA580C))
                )
              }
            }

            // Tab 3: Profile
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .clickable { onOpenProfile() }
                .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
              Icon(
                Icons.Filled.Person,
                contentDescription = "Profile",
                tint = if (selectedTab == DriverBottomNavTab.PROFILE) Color(0xFFEA580C) else Color(0xFF94A3B8),
                modifier = Modifier.size(24.dp)
              )
              Spacer(Modifier.height(3.dp))
              Text(
                "Profile",
                fontSize = 11.sp,
                fontWeight = if (selectedTab == DriverBottomNavTab.PROFILE) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedTab == DriverBottomNavTab.PROFILE) Color(0xFFEA580C) else Color(0xFF94A3B8)
              )
              if (selectedTab == DriverBottomNavTab.PROFILE) {
                Box(
                  modifier = Modifier
                    .padding(top = 4.dp)
                    .width(28.dp)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFEA580C))
                )
              }
            }
          }
        }
      }
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
      contentPadding = PaddingValues(bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // 1. TOP HEADER BANNER WITH WARM ORANGE GRADIENT
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              brush = Brush.verticalGradient(
                colors = listOf(
                  Color(0xFFF97316),
                  Color(0xFFEA580C),
                  Color(0xFFD94606)
                )
              )
            )
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Circular Back Button
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
                .clickable { onBack() },
              contentAlignment = Alignment.Center
            ) {
              Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
              )
            }

            // Title and Subtitle
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.weight(1f)
            ) {
              Text(
                "JTBazar Driver",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
              Text(
                "Driver Dashboard",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFFED7AA)
              )
            }

            // Circular Notification Button with red dot
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
                .clickable { onOpenNotifications() },
              contentAlignment = Alignment.Center
            ) {
              Icon(
                Icons.Filled.Notifications,
                contentDescription = "Notifications",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
              )
              Box(
                modifier = Modifier
                  .align(Alignment.TopEnd)
                  .padding(top = 8.dp, end = 8.dp)
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFEF4444))
              )
            }
          }
        }
      }

      // LOADING / ERROR ALERTS
      if (loading) {
        item {
          LinearProgressIndicator(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            color = Color(0xFFEA580C)
          )
        }
      }

      error?.let { err ->
        item {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
              Text(err, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
            }
          }
        }
      }

      message?.let { msg ->
        item {
          Surface(
            color = Color(0xFFF0FDF4),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A))
              Text(msg, color = Color(0xFF166534), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }

      // 2. DRIVER APPLICATION CARD (MATCHING REFERENCE IMAGE)
      if (application != null) {
        val appObj = application!!
        val applicantName = appObj.optString("full_name").takeIf { it.isNotBlank() && !it.equals("null", true) } ?: user?.fullName?.takeIf { it.isNotBlank() && !it.equals("null", true) } ?: "Driver"
        val vehType = appObj.optString("vehicle_type").takeIf { it.isNotBlank() && !it.equals("null", true) } ?: "Vehicle not set"
        val rawAddr = appObj.optString("current_address")
        val addrDisplay = if (rawAddr.isNotBlank() && rawAddr != "null") rawAddr else "Location not set"
        val status = appObj.optString("status", "pending")

        item {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
              .shadow(elevation = 3.dp, shape = RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9))
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              // Left: Rider avatar with green checkmark badge
              Box(
                modifier = Modifier
                  .size(70.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFEFF6FF)),
                contentAlignment = Alignment.Center
              ) {
                Image(
                  painter = painterResource(id = R.drawable.ic_driver_courier),
                  contentDescription = "Driver Avatar",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )

                if (status == "approved") {
                  Box(
                    modifier = Modifier
                      .align(Alignment.BottomEnd)
                      .size(20.dp)
                      .clip(CircleShape)
                      .background(Color(0xFF16A34A))
                      .padding(2.dp),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      Icons.Filled.Check,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(12.dp)
                    )
                  }
                }
              }

              Spacer(Modifier.width(14.dp))

              // Middle: Driver Application details
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
              ) {
                Text(
                  "Driver Application",
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF0F172A)
                )

                Spacer(Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = Color(0xFF1E293B), modifier = Modifier.size(13.dp))
                  Text("Applicant: $applicantName", fontSize = 11.5.sp, color = Color(0xFF334155), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Icon(Icons.Filled.TwoWheeler, contentDescription = null, tint = Color(0xFF1E293B), modifier = Modifier.size(13.dp))
                  Text("Vehicle: $vehType", fontSize = 11.5.sp, color = Color(0xFF334155))
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Icon(Icons.Filled.Place, contentDescription = null, tint = Color(0xFF1E293B), modifier = Modifier.size(13.dp))
                  Text("Address: $addrDisplay", fontSize = 11.5.sp, color = Color(0xFF334155), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
              }

              // Right: Status badge pill
              Surface(
                color = when (status) {
                  "approved" -> Color(0xFFDCFCE7)
                  "rejected", "suspended" -> Color(0xFFFEE2E2)
                  else -> Color(0xFFFEF3C7)
                },
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(
                  1.dp,
                  when (status) {
                    "approved" -> Color(0xFF86EFAC)
                    "rejected", "suspended" -> Color(0xFFFCA5A5)
                    else -> Color(0xFFFDE68A)
                  }
                )
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  if (status == "approved") {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(14.dp))
                  }
                  Text(
                    text = status.replaceFirstChar { it.uppercase() },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (status) {
                      "approved" -> Color(0xFF16A34A)
                      "rejected", "suspended" -> Color(0xFFB91C1C)
                      else -> Color(0xFF92400E)
                    }
                  )
                }
              }
            }
          }
        }
      }

      // NOT APPROVED NOTICE / APPLICATION FORM
      if (!enabled) {
        item {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text("Driver System Disabled", fontWeight = FontWeight.Bold, fontSize = 15.sp)
              Text("Driver applications are currently paused by administrator.", fontSize = 12.sp, color = JTTextSecondary)
            }
          }
        }
      } else if (application == null) {
        // DRIVER APPLICATION FORM WITH 7 REQUIRED FIELDS
        item {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Text("Become a JTBazar Driver", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
              Text("Enter your details and upload required verification documents.", fontSize = 12.sp, color = Color(0xFF64748B))

              // 1. Full Name
              OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Full Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
              )

              // 2. Phone
              OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Mobile Phone Number *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
              )

              // 3. Current Address + auto-detect GPS / map pin
              OutlinedTextField(
                value = currentAddress,
                onValueChange = { currentAddress = it },
                label = { Text("Current Address *") },
                placeholder = { Text("Street, house/shop number, area, city") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                supportingText = {
                  Text(
                    if (applicationLat != null && applicationLng != null) {
                      "Location pinned (${String.format(Locale.US, "%.5f", applicationLat)}, ${String.format(Locale.US, "%.5f", applicationLng)})"
                    } else {
                      "Tap 'Use Current Location' to auto-fill your street address, or pick on map."
                    }
                  )
                }
              )

              addressLocationStatus?.let { statusMsg ->
                Surface(
                  color = Color(0xFFF0FDF4),
                  shape = RoundedCornerShape(10.dp),
                  border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                    Text(statusMsg, color = Color(0xFF166534), fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                  }
                }
              }

              addressLocationError?.let { locErr ->
                Text(locErr, color = MaterialTheme.colorScheme.error, fontSize = 11.5.sp)
              }

              Button(
                onClick = { requestDriverCurrentLocation() },
                enabled = !locatingAddress && !busy,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = Color(0xFFFFF7ED),
                  contentColor = Color(0xFFC2410C)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFED7AA))
              ) {
                if (locatingAddress) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFFC2410C)
                  )
                  Spacer(Modifier.width(8.dp))
                  Text("Detecting current address…", fontWeight = FontWeight.SemiBold)
                } else {
                  Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(8.dp))
                  Text("Use Current Location", fontWeight = FontWeight.Bold)
                }
              }

              OutlinedButton(
                onClick = { showApplicationLocationPicker = true },
                enabled = !locatingAddress && !busy,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEA580C))
              ) {
                Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (applicationLat != null && applicationLng != null) "Adjust Location on Map" else "Choose Location on Map")
              }

              // 4. Vehicle Type Dropdown
              ExposedDropdownMenuBox(
                expanded = isVehicleDropdownExpanded,
                onExpandedChange = { isVehicleDropdownExpanded = it }
              ) {
                OutlinedTextField(
                  value = vehicleType,
                  onValueChange = {},
                  readOnly = true,
                  label = { Text("Vehicle Type *") },
                  trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isVehicleDropdownExpanded) },
                  modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                  shape = RoundedCornerShape(10.dp)
                )
                ExposedDropdownMenu(
                  expanded = isVehicleDropdownExpanded,
                  onDismissRequest = { isVehicleDropdownExpanded = false }
                ) {
                  DropdownMenuItem(
                    text = { Text("Bike (Motorcycle)") },
                    onClick = {
                      vehicleType = "Bike"
                      isVehicleDropdownExpanded = false
                    }
                  )
                  DropdownMenuItem(
                    text = { Text("Car (Automobile)") },
                    onClick = {
                      vehicleType = "Car"
                      isVehicleDropdownExpanded = false
                    }
                  )
                }
              }

              HorizontalDivider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 4.dp))

              Text("Required Verification Documents", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))

              // 5. CNIC Front
              DocumentUploadCard(
                title = "CNIC Front Side *",
                subtitle = "Clear photo of front side",
                docState = cnicFrontDoc,
                onChoosePhoto = {
                  activeDocForPicker = DriverDocType.CNIC_FRONT
                  showPickerSheet = true
                },
                onClear = { cnicFrontDoc = DocUploadItem() }
              )

              // 6. CNIC Back
              DocumentUploadCard(
                title = "CNIC Back Side *",
                subtitle = "Clear photo of back side",
                docState = cnicBackDoc,
                onChoosePhoto = {
                  activeDocForPicker = DriverDocType.CNIC_BACK
                  showPickerSheet = true
                },
                onClear = { cnicBackDoc = DocUploadItem() }
              )

              // 7. Vehicle Plate
              DocumentUploadCard(
                title = "Vehicle Number Plate *",
                subtitle = "Clear rear photo of bike/car",
                docState = plateDoc,
                onChoosePhoto = {
                  activeDocForPicker = DriverDocType.VEHICLE_PLATE
                  showPickerSheet = true
                },
                onClear = { plateDoc = DocUploadItem() }
              )

              val isFormValid = fullName.trim().isNotBlank() &&
                phone.trim().isNotBlank() &&
                currentAddress.trim().isNotBlank() &&
                applicationLat != null && applicationLng != null &&
                !cnicFrontDoc.storagePath.isNullOrBlank() &&
                !cnicBackDoc.storagePath.isNullOrBlank() &&
                !plateDoc.storagePath.isNullOrBlank() &&
                !cnicFrontDoc.isUploading &&
                !cnicBackDoc.isUploading &&
                !plateDoc.isUploading

              Button(
                enabled = !busy && isFormValid,
                onClick = {
                  scope.launch {
                    busy = true
                    message = null
                    error = null
                    try {
                      val body = JSONObject().apply {
                        put("p_full_name", fullName.trim())
                        put("p_phone", phone.trim())
                        put("p_vehicle_type", vehicleType)
                        put("p_current_address", currentAddress.trim())
                        put("p_cnic_front_path", cnicFrontDoc.storagePath)
                        put("p_cnic_back_path", cnicBackDoc.storagePath)
                        put("p_vehicle_plate_image_path", plateDoc.storagePath)
                      }
                      val result = SupabaseClient.rpc("submit_driver_application", body.toString())
                      if (result.isSuccess) {
                        val locationResult = SupabaseClient.rpc(
                          "driver_save_application_location",
                          JSONObject()
                            .put("p_lat", applicationLat)
                            .put("p_lng", applicationLng)
                            .put("p_address", currentAddress.trim())
                            .toString()
                        )
                        if (locationResult.isFailure) throw (locationResult.exceptionOrNull() ?: IllegalStateException("Could not save map location"))
                        message = "Driver application submitted! Admin approval is required."
                        load()
                      } else {
                        error = result.exceptionOrNull()?.message ?: "Application failed"
                      }
                    } catch (e: Exception) {
                      error = e.message
                    } finally {
                      busy = false
                    }
                  }
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
              ) {
                Text(if (busy) "Submitting…" else "Submit Driver Application", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      // 3. SECTION HEADER: "My Assigned Deliveries"
      if (isApprovedDriver) {
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                if (selectedTab == DriverBottomNavTab.HISTORY) "Delivery History" else "My Assigned Deliveries",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
              )
              Text(
                if (selectedTab == DriverBottomNavTab.HISTORY) "${deliveredCount + deliveries.count { it.optString("assignment_status") == "cancelled" }} completed / cancelled orders" else "$activeCount active delivery assignments",
                fontSize = 12.sp,
                color = Color(0xFF64748B)
              )
            }

            // Refresh pill button
            Surface(
              color = Color(0xFFEFF6FF),
              shape = RoundedCornerShape(20.dp),
              modifier = Modifier.clickable { scope.launch { load() } }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(
                  Icons.Filled.Refresh,
                  contentDescription = null,
                  tint = Color(0xFF2563EB),
                  modifier = Modifier.size(15.dp)
                )
                Text(
                  "Refresh",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF2563EB)
                )
              }
            }
          }
        }

        // 4. DELIVERY STATUS FILTER PILLS (ALL, ACTIVE, PICKED UP, DELIVERED)
        if (selectedTab == DriverBottomNavTab.DELIVERIES) item {
          LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            val filtersWithCounts = listOf(
              DriverDeliveryFilter.ALL to allCount,
              DriverDeliveryFilter.PENDING to deliveries.count { it.optString("order_status").equals("pending", true) },
              DriverDeliveryFilter.CONFIRMED to deliveries.count { it.optString("order_status").equals("confirmed", true) },
              DriverDeliveryFilter.PROCESSING to deliveries.count { it.optString("order_status").equals("processing", true) },
              DriverDeliveryFilter.ACTIVE to activeCount,
              DriverDeliveryFilter.SHIPPED to deliveries.count { it.optString("order_status").equals("shipped", true) },
              DriverDeliveryFilter.PICKED_UP to pickedUpCount,
              DriverDeliveryFilter.DELIVERED to deliveredCount,
              DriverDeliveryFilter.CANCELLED to deliveries.count { it.optString("assignment_status") == "cancelled" }
            )

            items(filtersWithCounts) { (filter, count) ->
              val isSelected = selectedFilter == filter && selectedTab != DriverBottomNavTab.HISTORY
              Surface(
                color = if (isSelected) Color(0xFFEA580C) else Color.White,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(
                  1.dp,
                  if (isSelected) Color(0xFFEA580C) else Color(0xFFE2E8F0)
                ),
                modifier = Modifier.clickable {
                  selectedFilter = filter
                  selectedTab = DriverBottomNavTab.DELIVERIES
                }
              ) {
                Text(
                  text = "${filter.label} ($count)",
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) Color.White else Color(0xFF475569)
                )
              }
            }
          }
        }

        // 5. DELIVERY CARDS (MATCHING REFERENCE IMAGE EXACTLY)
        if (filteredDeliveries.isEmpty()) {
          item {
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(36.dp))
                Text("No Deliveries In This Section", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                Text("Check other status tabs or refresh for newly assigned orders.", fontSize = 12.sp, color = Color(0xFF64748B), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
              }
            }
          }
        }

        items(filteredDeliveries, key = { it.optString("assignment_id") }) { delivery ->
          val lat = delivery.optDouble("delivery_lat", Double.NaN)
          val lng = delivery.optDouble("delivery_lng", Double.NaN)
          val assignmentStatus = delivery.optString("assignment_status", "assigned")
          val orderNumber = delivery.optString("order_number").ifBlank { "Order number unavailable" }
          val customerName = delivery.optString("customer_name").ifBlank { "Customer" }
          val customerPhone = delivery.optString("customer_phone")
          val addressText = driverAddressText(delivery)
          val isDelivered = assignmentStatus == "delivered" || assignmentStatus == "cancelled"

          // Format items summary (e.g. "BOLT B155 20000mAh Power Bank × 1")
          val itemsArr = delivery.optJSONArray("items")
          val itemsText = if (itemsArr != null && itemsArr.length() > 0) {
            val list = mutableListOf<String>()
            for (i in 0 until itemsArr.length()) {
              val itm = itemsArr.optJSONObject(i) ?: continue
              list.add("${itm.optString("name", "Product")} × ${itm.optInt("quantity", 1)}")
            }
            list.joinToString(", ")
          } else {
            "No item details available"
          }

          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
              .shadow(elevation = 2.dp, shape = RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9))
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              // Row 1: Status Pill & Order ID Pill
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                // Status Pill (e.g., • Active, • Picked Up, • Delivered)
                Surface(
                  color = when (assignmentStatus) { "delivered" -> Color(0xFFDCFCE7); "cancelled" -> Color(0xFFFEE2E2); "picked_up" -> Color(0xFFEFF6FF); else -> Color(0xFFFFF7ED) },
                  shape = RoundedCornerShape(20.dp)
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Box(
                      modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(when (assignmentStatus) { "delivered" -> Color(0xFF16A34A); "cancelled" -> Color(0xFFDC2626); "picked_up" -> Color(0xFF2563EB); else -> Color(0xFFEA580C) })
                    )
                    Text(
                      text = when {
                    assignmentStatus == "delivered" -> "Delivered"
                    assignmentStatus == "cancelled" -> "Cancelled"
                    assignmentStatus == "picked_up" -> "In Transit"
                    delivery.optString("order_status").equals("shipped", true) -> "Shipped"
                    delivery.optString("order_status").equals("processing", true) -> "Processing"
                    delivery.optString("order_status").equals("confirmed", true) -> "Confirmed"
                    delivery.optString("order_status").equals("pending", true) -> "Pending"
                    else -> "Active"
                  },
                      fontSize = 11.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = when (assignmentStatus) { "delivered" -> Color(0xFF16A34A); "cancelled" -> Color(0xFFDC2626); "picked_up" -> Color(0xFF2563EB); else -> Color(0xFFEA580C) }
                    )
                  }
                }

                // Order Number Pill with Box icon
                Surface(
                  color = Color(0xFFEFF6FF),
                  shape = RoundedCornerShape(20.dp),
                  border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                  ) {
                    Icon(
                      Icons.Filled.Inventory2,
                      contentDescription = null,
                      tint = Color(0xFF1D4ED8),
                      modifier = Modifier.size(13.dp)
                    )
                    Text(
                      "Order #$orderNumber",
                      fontSize = 11.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFF1D4ED8)
                    )
                  }
                }
              }

              // Row 2: Customer Details & Map (active deliveries only)
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
              ) {
                // Left Column: Customer Name, Phone, Address
                Column(
                  modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  // Customer name
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(16.dp))
                    Column {
                      Text("Customer", fontSize = 10.sp, color = Color(0xFF64748B))
                      Text(customerName, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }
                  }

                  // Phone
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable {
                      if (customerPhone.isNotBlank()) {
                        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$customerPhone"))
                        context.startActivity(dial)
                      }
                    }
                  ) {
                    Icon(Icons.Filled.Phone, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(15.dp))
                    Text(customerPhone, fontSize = 12.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Medium)
                  }

                  // Customer address is intentionally hidden after delivery completion.
                  if (!isDelivered) Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Place, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(15.dp).padding(top = 2.dp))
                    Text(
                      addressText,
                      fontSize = 11.5.sp,
                      color = Color(0xFF334155),
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                }

                // Location preview is only available for active deliveries.
                if (!isDelivered) Box(
                  modifier = Modifier
                    .size(width = 110.dp, height = 80.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFE2E8F0))
                    .clickable {
                      if (isApprovedDriver) {
                        onViewCustomerLocation(delivery)
                      }
                    }
                ) {
                  // Illustrated street grid canvas
                  Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(Color(0xFFE8EEF5))
                    // Roads
                    drawLine(Color.White, Offset(0f, size.height * 0.4f), Offset(size.width, size.height * 0.35f), strokeWidth = 14f)
                    drawLine(Color.White, Offset(size.width * 0.45f, 0f), Offset(size.width * 0.55f, size.height), strokeWidth = 12f)
                    drawLine(Color(0xFFBFDBFE), Offset(0f, size.height * 0.4f), Offset(size.width, size.height * 0.35f), strokeWidth = 3f)
                    // Green park patch
                    drawRoundRect(Color(0xFFDCFCE7), Offset(size.width * 0.05f, size.height * 0.05f), androidx.compose.ui.geometry.Size(size.width * 0.35f, size.height * 0.28f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f))
                  }

                  // Center Red Place Pin
                  Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier
                      .align(Alignment.Center)
                      .size(24.dp)
                  )

                  // Bottom dark pill: "🧭 View Location"
                  Surface(
                    color = Color(0xFF1E293B).copy(alpha = 0.92f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                      .align(Alignment.BottomCenter)
                      .padding(bottom = 6.dp)
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                      Icon(
                        Icons.Filled.Navigation,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(9.dp)
                      )
                      Text(
                        "View Location",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                      )
                    }
                  }
                }
              }

              // Divider
              HorizontalDivider(color = Color(0xFFF1F5F9))

              // Row 3: Items summary
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  Icons.Filled.Widgets,
                  contentDescription = null,
                  tint = Color(0xFFEA580C),
                  modifier = Modifier.size(18.dp)
                )
                Column {
                  Text("Items", fontSize = 10.sp, color = Color(0xFF64748B))
                  Text(
                    itemsText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }

              // Row 4: Action Buttons (View Customer Location & Picked Up / Delivered)
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                // Completed orders never expose customer location.
                if (!isDelivered) Button(
                  onClick = {
                    if (isApprovedDriver) {
                      onViewCustomerLocation(delivery)
                    }
                  },
                  modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                  shape = RoundedCornerShape(12.dp),
                  colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFEDD5),
                    contentColor = Color(0xFFEA580C)
                  ),
                  contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                  Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(15.dp))
                  Spacer(Modifier.width(6.dp))
                  Text(
                    "View Customer Location",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                  )
                }

                // Right: Green Status Button ("Picked Up" / "Delivered" / "Completed")
                if (assignmentStatus == "assigned") {
                  Button(
                    enabled = !busy,
                    onClick = {
                      scope.launch {
                        busy = true
                        val r = SupabaseClient.rpc(
                          "driver_update_delivery_status",
                          JSONObject().put("p_assignment_id", delivery.optString("assignment_id")).put("p_action", "picked_up").toString()
                        )
                        if (r.isSuccess) load() else error = r.exceptionOrNull()?.message
                        busy = false
                      }
                    },
                    modifier = Modifier
                      .weight(1f)
                      .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                      containerColor = Color(0xFF16A34A),
                      contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                  ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Picked Up", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  }
                } else if (assignmentStatus == "picked_up") {
                  Button(
                    enabled = !busy,
                    onClick = {
                      scope.launch {
                        busy = true
                        val r = SupabaseClient.rpc(
                          "driver_update_delivery_status",
                          JSONObject().put("p_assignment_id", delivery.optString("assignment_id")).put("p_action", "delivered").toString()
                        )
                        if (r.isSuccess) load() else error = r.exceptionOrNull()?.message
                        busy = false
                      }
                    },
                    modifier = Modifier
                      .weight(1f)
                      .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                      containerColor = Color(0xFF16A34A),
                      contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                  ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delivered", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  }
                } else {
                  Surface(
                    color = if (assignmentStatus == "cancelled") Color(0xFFFEE2E2) else Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                      .weight(1f)
                      .height(42.dp)
                  ) {
                    Row(
                      modifier = Modifier.fillMaxSize(),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.Center
                    ) {
                      Icon(if (assignmentStatus == "cancelled") Icons.Filled.Cancel else Icons.Filled.CheckCircle, contentDescription = null, tint = if (assignmentStatus == "cancelled") Color(0xFFDC2626) else Color(0xFF16A34A), modifier = Modifier.size(15.dp))
                      Spacer(Modifier.width(6.dp))
                      Text(if (assignmentStatus == "cancelled") "Cancelled" else "Delivered", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (assignmentStatus == "cancelled") Color(0xFFDC2626) else Color(0xFF16A34A))
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

  if (showApplicationLocationPicker) {
    DriverApplicationLocationPicker(
      initialLat = applicationLat,
      initialLng = applicationLng,
      onDismiss = { showApplicationLocationPicker = false },
      onLocationSelected = { lat, lng, address ->
        applicationLat = lat
        applicationLng = lng
        addressLocationError = null
        if (address.isNotBlank()) {
          currentAddress = address
          addressLocationStatus = "Map location selected and street address filled."
        } else {
          addressLocationStatus = "Map location pinned. Verify your street address above."
        }
        showApplicationLocationPicker = false
      }
    )
  }

  // BOTTOM SHEET: PICK CAMERA OR GALLERY
  if (showPickerSheet && activeDocForPicker != null) {
    ModalBottomSheet(
      onDismissRequest = {
        showPickerSheet = false
      },
      containerColor = Color.White
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        val title = when (activeDocForPicker) {
          DriverDocType.CNIC_FRONT -> "CNIC Front Image"
          DriverDocType.CNIC_BACK -> "CNIC Back Image"
          DriverDocType.VEHICLE_PLATE -> "Vehicle Number Plate"
          null -> "Document"
        }
        Text("Select $title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Choose camera to take a photo or select from device gallery.", fontSize = 12.sp, color = JTTextSecondary)

        Spacer(Modifier.height(4.dp))

        OutlinedButton(
          onClick = {
            showPickerSheet = false
            startCameraCapture()
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
          shape = RoundedCornerShape(10.dp)
        ) {
          Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = Color(0xFFEA580C))
          Spacer(Modifier.width(10.dp))
          Text("Take Photo with Camera", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        }

        Button(
          onClick = {
            showPickerSheet = false
            pickMediaLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
        ) {
          Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
          Spacer(Modifier.width(10.dp))
          Text("Choose from Gallery", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(12.dp))
      }
    }
  }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverApplicationLocationPicker(
  initialLat: Double? = null,
  initialLng: Double? = null,
  onDismiss: () -> Unit,
  onLocationSelected: (Double, Double, String) -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var mapView by remember { mutableStateOf<MapView?>(null) }
  val hasInitialPoint = initialLat != null && initialLng != null &&
    initialLat in -90.0..90.0 && initialLng in -180.0..180.0 &&
    !(initialLat == 0.0 && initialLng == 0.0)
  var selectedPoint by remember {
    mutableStateOf(if (hasInitialPoint) GeoPoint(initialLat!!, initialLng!!) else GeoPoint(0.0, 0.0))
  }
  var isLocating by remember { mutableStateOf(false) }
  var isResolvingAddress by remember { mutableStateOf(false) }
  var previewAddress by remember { mutableStateOf("") }
  var locationError by remember { mutableStateOf<String?>(null) }
  var hasLocationPermission by remember {
    mutableStateOf(LocationResolver.hasLocationPermission(context))
  }

  lateinit var centerOnCurrentLocation: () -> Unit

  val locationSettingsLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      centerOnCurrentLocation()
    } else {
      isLocating = false
      locationError = "Turn on device location / GPS to detect your current location."
    }
  }

  centerOnCurrentLocation = {
    if (hasLocationPermission) {
      isLocating = true
      locationError = null
      scope.launch {
        try {
          when (val result = LocationResolver.fetchCurrentLocation(context)) {
            is LocationFetchResult.Success -> {
              val point = GeoPoint(result.location.latitude, result.location.longitude)
              selectedPoint = point
              mapView?.let { map ->
                map.controller.setZoom(16.5)
                map.controller.animateTo(point)
              }
              val resolved = AddressReverseGeocoder.resolve(context, point.latitude, point.longitude)
              previewAddress = resolved?.toReadableFullAddress().orEmpty()
              isLocating = false
            }
            is LocationFetchResult.NeedsResolution -> {
              runCatching {
                val request = IntentSenderRequest.Builder(result.exception.resolution).build()
                locationSettingsLauncher.launch(request)
              }.onFailure {
                isLocating = false
                locationError = "Turn on device location / GPS and try again."
              }
            }
            is LocationFetchResult.Failure -> {
              isLocating = false
              locationError = result.message
            }
          }
        } catch (_: Exception) {
          isLocating = false
          locationError = "Could not get current location. Move the map pin manually."
        }
      }
    }
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    if (hasLocationPermission) centerOnCurrentLocation()
    else locationError = "Location permission denied. You can still select a point on the map."
  }

  LaunchedEffect(Unit) {
    if (!hasInitialPoint && hasLocationPermission) {
      centerOnCurrentLocation()
    } else if (hasInitialPoint) {
      val resolved = AddressReverseGeocoder.resolve(context, selectedPoint.latitude, selectedPoint.longitude)
      previewAddress = resolved?.toReadableFullAddress().orEmpty()
    }
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp),
      shape = RoundedCornerShape(22.dp),
      color = Color.White
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
        Text("Select Your Current Location", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        Text("Move the map until the pin is on your exact location, or tap Use My Current Location.", fontSize = 12.sp, color = Color(0xFF64748B))
        Box(
          modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 220.dp, max = 380.dp).fillMaxHeight(0.45f).clip(RoundedCornerShape(16.dp))
        ) {
          AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
              Configuration.getInstance().userAgentValue = ctx.packageName
              Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
              MapView(ctx).apply {
                setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                isTilesScaledToDpi = true
                controller.setZoom(if (hasInitialPoint) 16.5 else 3.0)
                controller.setCenter(selectedPoint)
                mapView = this
              }
            },
            update = { map ->
              mapView = map
            }
          )
          Icon(
            Icons.Filled.LocationOn,
            contentDescription = "Selected map point",
            tint = Color(0xFFEA580C),
            modifier = Modifier.align(Alignment.Center).size(42.dp)
          )
          Surface(
            color = Color.White.copy(alpha = 0.94f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
          ) {
            Text(
              text = if (isLocating) "Finding your location…" else "Pin location",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFEA580C),
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
          }
        }
        if (previewAddress.isNotBlank()) {
          Surface(
            color = Color(0xFFF8FAFC),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = previewAddress,
              fontSize = 11.5.sp,
              color = Color(0xFF334155),
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
          }
        }
        OutlinedButton(
          onClick = {
            if (hasLocationPermission) centerOnCurrentLocation()
            else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
          },
          modifier = Modifier.fillMaxWidth().height(48.dp),
          enabled = !isLocating && !isResolvingAddress,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEA580C))
        ) {
          if (isLocating) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
          else Icon(Icons.Filled.MyLocation, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text(if (isLocating) "Finding location…" else "Use My Current Location")
        }
        locationError?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedButton(
            onClick = onDismiss,
            enabled = !isResolvingAddress,
            modifier = Modifier.weight(1f).height(48.dp)
          ) { Text("Cancel") }
          Button(
            onClick = {
              val point = mapView?.mapCenter ?: selectedPoint
              val lat = point.latitude
              val lng = point.longitude
              if (lat == 0.0 && lng == 0.0) {
                locationError = "Select your actual location using GPS or move the map to the correct point."
                return@Button
              }
              scope.launch {
                isResolvingAddress = true
                val address = runCatching {
                  AddressReverseGeocoder.resolve(context, lat, lng)?.toReadableFullAddress().orEmpty()
                }.getOrDefault("")
                isResolvingAddress = false
                onLocationSelected(lat, lng, address)
              }
            },
            enabled = !isResolvingAddress,
            modifier = Modifier.weight(1f).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
          ) {
            if (isResolvingAddress) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
              Spacer(Modifier.width(6.dp))
              Text("Saving…")
            } else {
              Text("Confirm Location")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DocumentUploadCard(
  title: String,
  subtitle: String,
  docState: DocUploadItem,
  onChoosePhoto: () -> Unit,
  onClear: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (docState.storagePath != null) Color(0xFFF0FDF4) else Color(0xFFFFFBF7)
    ),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      if (docState.storagePath != null) Color(0xFF86EFAC) else Color(0xFFFED7AA)
    )
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = JTTextPrimary)
          Text(subtitle, fontSize = 11.sp, color = JTTextSecondary)
        }
        if (docState.storagePath != null) {
          Surface(
            color = Color(0xFFDCFCE7),
            shape = RoundedCornerShape(50)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(14.dp))
              Text("Uploaded", color = Color(0xFF166534), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      if (docState.localBitmap != null || docState.localUri != null) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE2E8F0))
        ) {
          if (docState.localBitmap != null) {
            androidx.compose.foundation.Image(
              bitmap = docState.localBitmap.asImageBitmap(),
              contentDescription = title,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          } else if (docState.localUri != null) {
            AsyncImage(
              model = docState.localUri,
              contentDescription = title,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          }

          if (docState.isUploading) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
              contentAlignment = Alignment.Center
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.height(6.dp))
                Text("Uploading securely…", color = Color.White, fontSize = 11.sp)
              }
            }
          }
        }
      }

      docState.errorMessage?.let { err ->
        Text(err, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (docState.storagePath == null && !docState.isUploading) {
          Button(
            onClick = onChoosePhoto,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
          ) {
            Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Select / Take Photo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          }
        } else {
          OutlinedButton(
            onClick = onChoosePhoto,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp)
          ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Replace", fontSize = 11.5.sp)
          }
          OutlinedButton(
            onClick = onClear,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
          ) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Remove", fontSize = 11.5.sp)
          }
        }
      }
    }
  }
}
