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
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.R
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseRealtimeClient
import com.example.ui.theme.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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

internal fun buildDriverDocumentPath(
  userId: String,
  docType: DriverDocType,
  timestampMs: Long = System.currentTimeMillis()
): String {
  val prefix = when (docType) {
    DriverDocType.CNIC_FRONT -> "cnic_front"
    DriverDocType.CNIC_BACK -> "cnic_back"
    DriverDocType.VEHICLE_PLATE -> "vehicle_plate"
  }
  return "${userId.trim()}/${prefix}_${timestampMs}.jpg"
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

sealed class DriverPhotoPickerTarget {
  data class ApplicationDoc(val docType: DriverDocType) : DriverPhotoPickerTarget()
  data class DeliveryProof(val assignmentId: String) : DriverPhotoPickerTarget()
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
  val session by SupabaseClient.session.collectAsState()
  val isAuthenticatedSession = session != null && !user?.id.isNullOrBlank()

  var enabled by remember { mutableStateOf(false) }
  var application by remember { mutableStateOf<JSONObject?>(null) }
  var deliveries by remember { mutableStateOf(listOf<JSONObject>()) }
  var showResubmitForm by remember { mutableStateOf(false) }

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

  // Active target for camera/gallery picker (shared across application docs & delivery photo proof)
  var activePickerTarget by remember { mutableStateOf<DriverPhotoPickerTarget?>(null) }
  var showPickerSheet by remember { mutableStateOf(false) }
  var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

  var loading by remember { mutableStateOf(true) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var message by remember { mutableStateOf<String?>(null) }
  var uploadingProofAssignmentId by remember { mutableStateOf<String?>(null) }
  var failedProofUploads by remember { mutableStateOf<Map<String, Pair<Uri, Boolean>>>(emptyMap()) }
  var proofPreviews by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
  var completingAssignmentIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var showOtpDialog by remember { mutableStateOf(false) }
  var otpDialogError by remember { mutableStateOf<String?>(null) }
  var showFailedDialog by remember { mutableStateOf(false) }
  var showCodDialog by remember { mutableStateOf(false) }
  var actionAssignmentId by remember { mutableStateOf("") }
  var otpCode by remember { mutableStateOf("") }
  var failureReason by remember { mutableStateOf("customer_unavailable") }
  var failureNote by remember { mutableStateOf("") }
  var codAmount by remember { mutableStateOf("") }
  var actionBusy by remember { mutableStateOf(false) }

  suspend fun uploadDeliveryProofFromUri(
    assignmentId: String,
    uri: Uri,
    isCameraCapture: Boolean
  ) {
    if (uploadingProofAssignmentId != null || actionBusy) return
    uploadingProofAssignmentId = assignmentId
    actionBusy = true
    error = null
    try {
      val uid = SupabaseClient.currentUserId?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("Please sign in again.")
      val rawBytes = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
      } ?: throw IllegalStateException("Could not read the selected delivery photo.")

      val rawMime = context.contentResolver.getType(uri)
        ?: if (isCameraCapture) "image/jpeg" else null

      val validated = validateDeliveryProofImage(rawBytes, rawMime)
      val decodedBitmap = withContext(Dispatchers.Default) {
        BitmapFactory.decodeByteArray(validated.bytes, 0, validated.bytes.size)
      } ?: throw IllegalArgumentException("Selected file is not a valid image. Please choose a clear photo.")

      // Never trust a stale UI assignment: the server RPC also verifies that this
      // authenticated, approved driver owns the active assignment.
      val storagePath = buildDeliveryProofPath(uid, assignmentId, validated.extension)
      SupabaseClient.uploadPrivateFile("delivery-proofs", storagePath, validated.bytes, validated.mimeType).getOrThrow()
      try {
        SupabaseClient.rpc(
          "driver_set_delivery_proof_path",
          JSONObject()
            .put("p_assignment_id", assignmentId)
            .put("p_storage_path", storagePath)
            .toString(),
          requireAuth = true
        ).getOrThrow()
      } catch (rpcError: Exception) {
        // The object is not considered saved unless the assignment path RPC succeeds.
        // Best-effort cleanup prevents private orphan objects when the DB write fails.
        val cleanup = SupabaseClient.deletePrivateFile("delivery-proofs", storagePath)
        if (cleanup.isFailure) {
          android.util.Log.w("DriverCenterScreen", "Could not clean orphan delivery proof object after RPC failure.")
        }
        throw rpcError
      }

      deliveries = updateAssignmentProofPathInList(deliveries, assignmentId, storagePath)
      proofPreviews = proofPreviews + (assignmentId to decodedBitmap)
      failedProofUploads = failedProofUploads - assignmentId
      message = "Delivery photo proof saved securely."
    } catch (e: Exception) {
      failedProofUploads = failedProofUploads + (assignmentId to (uri to isCameraCapture))
      error = extractDriverFriendlyError(e, "Could not upload delivery photo proof.")
    } finally {
      uploadingProofAssignmentId = null
      actionBusy = false
    }
  }

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

  val appStatus = application?.optCleanString("status")?.lowercase().orEmpty()
  val isApprovedDriver = hasDriverDeliveryAccess(
    isAuthenticated = isAuthenticatedSession,
    accessEnabled = enabled,
    applicationStatus = appStatus
  )

  suspend fun uploadDocBytes(docType: DriverDocType, bytes: ByteArray, uri: Uri?, bitmap: Bitmap?) {
    fun setDocState(item: DocUploadItem) {
      when (docType) {
        DriverDocType.CNIC_FRONT -> cnicFrontDoc = item
        DriverDocType.CNIC_BACK -> cnicBackDoc = item
        DriverDocType.VEHICLE_PLATE -> plateDoc = item
      }
    }

    val uid = SupabaseClient.currentUserId?.takeIf { it.isNotBlank() }
      ?: user?.id?.takeIf { it.isNotBlank() }
    if (uid == null || !SupabaseClient.isAuthenticated) {
      val failed = DocUploadItem(
        localUri = uri,
        localBitmap = bitmap,
        isUploading = false,
        errorMessage = "Please sign in again before uploading verification documents."
      )
      withContext(Dispatchers.Main) {
        setDocState(failed)
        error = "Please sign in again before uploading verification documents."
      }
      return
    }

    val uploadBytes = withContext(Dispatchers.Default) {
      val isJpeg = bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() &&
        bytes[1] == 0xD8.toByte() &&
        bytes[2] == 0xFF.toByte()
      if (isJpeg && bytes.size <= 4 * 1024 * 1024) {
        bytes
      } else if (bitmap != null) {
        ByteArrayOutputStream().use { out ->
          bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
          out.toByteArray()
        }
      } else {
        bytes
      }
    }

    if (uploadBytes.isEmpty() || uploadBytes.size > MAX_DRIVER_IMAGE_BYTES) {
      val failed = DocUploadItem(
        localUri = uri,
        localBitmap = bitmap,
        isUploading = false,
        errorMessage = "Choose a document photo up to 8 MB."
      )
      withContext(Dispatchers.Main) {
        setDocState(failed)
      }
      return
    }

    val uploadingItem = DocUploadItem(localUri = uri, localBitmap = bitmap, isUploading = true)
    withContext(Dispatchers.Main) {
      error = null
      setDocState(uploadingItem)
    }

    val path = buildDriverDocumentPath(uid, docType)
    val result = SupabaseClient.uploadPrivateFile("driver-documents", path, uploadBytes, "image/jpeg")

    result.fold(
      onSuccess = { savedPath ->
        val completed = DocUploadItem(localUri = uri, localBitmap = bitmap, storagePath = savedPath, isUploading = false)
        withContext(Dispatchers.Main) {
          setDocState(completed)
        }
      },
      onFailure = { err ->
        val failed = DocUploadItem(
          localUri = uri,
          localBitmap = bitmap,
          isUploading = false,
          errorMessage = extractDriverFriendlyError(err, "Document upload failed. Please try again.")
        )
        withContext(Dispatchers.Main) {
          setDocState(failed)
        }
      }
    )
  }

  // Camera photo capture launcher (shared for application documents & delivery proof)
  val takePictureLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.TakePicture()
  ) { success ->
    val target = activePickerTarget
    val uri = tempCameraUri
    if (success && uri != null && target != null) {
      when (target) {
        is DriverPhotoPickerTarget.DeliveryProof -> {
          scope.launch { uploadDeliveryProofFromUri(target.assignmentId, uri, isCameraCapture = true) }
        }
        is DriverPhotoPickerTarget.ApplicationDoc -> {
          scope.launch(Dispatchers.IO) {
            try {
              val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
              if (bytes != null && bytes.isNotEmpty()) {
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp == null) {
                  withContext(Dispatchers.Main) { error = "Captured photo could not be decoded. Please try again." }
                } else {
                  uploadDocBytes(target.docType, bytes, uri, bmp)
                }
              } else {
                withContext(Dispatchers.Main) { error = "Captured photo was empty. Please try again." }
              }
            } catch (e: Exception) {
              withContext(Dispatchers.Main) { error = extractDriverFriendlyError(e, "Could not read camera photo.") }
            }
          }
        }
      }
    }
  }

  // Gallery photo picker launcher (shared for application documents & delivery proof)
  val pickMediaLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    val target = activePickerTarget
    if (uri != null && target != null) {
      when (target) {
        is DriverPhotoPickerTarget.DeliveryProof -> {
          scope.launch { uploadDeliveryProofFromUri(target.assignmentId, uri, isCameraCapture = false) }
        }
        is DriverPhotoPickerTarget.ApplicationDoc -> {
          scope.launch(Dispatchers.IO) {
            try {
              val rawMime = context.contentResolver.getType(uri)
              if (!rawMime.isNullOrBlank() && !rawMime.lowercase().startsWith("image/")) {
                withContext(Dispatchers.Main) { error = "Selected file is not an image. Please choose a JPEG or PNG photo." }
                return@launch
              }
              val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
              if (bytes != null && bytes.isNotEmpty()) {
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp == null) {
                  withContext(Dispatchers.Main) { error = "Selected file is not a valid image. Please choose a photo." }
                } else {
                  uploadDocBytes(target.docType, bytes, uri, bmp)
                }
              } else {
                withContext(Dispatchers.Main) { error = "Selected photo was empty. Please choose another file." }
              }
            } catch (e: Exception) {
              withContext(Dispatchers.Main) { error = extractDriverFriendlyError(e, "Could not read selected photo.") }
            }
          }
        }
      }
    }
  }

  fun launchCameraForTarget() {
    try {
      val prefix = when (activePickerTarget) {
        is DriverPhotoPickerTarget.DeliveryProof -> "delivery_proof_"
        else -> "driver_doc_"
      }
      val tempFile = File.createTempFile(prefix, ".jpg", context.cacheDir)
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
      tempCameraUri = uri
      takePictureLauncher.launch(uri)
    } catch (e: Exception) {
      error = extractDriverFriendlyError(e, "Failed to launch camera.")
    }
  }

  // Camera permission launcher
  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      launchCameraForTarget()
    } else {
      error = "Camera permission is required to take a photo. You can still select an image from your gallery."
    }
  }

  fun startCameraCapture() {
    val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    if (hasCam) {
      launchCameraForTarget()
    } else {
      cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  val loadMutex = remember { Mutex() }

  suspend fun load(showLoader: Boolean = true, surfaceErrors: Boolean = true) {
    if (!loadMutex.tryLock()) return
    if (showLoader) loading = true
    if (surfaceErrors) error = null
    try {
      val uid = user?.id?.takeIf { it.isNotBlank() }
      if (!SupabaseClient.isAuthenticated || uid == null) {
        enabled = false
        application = null
        deliveries = emptyList()
        loading = false
        return
      }
      val access = SupabaseClient.rpc("get_my_driver_access", requireAuth = true).getOrThrow()
      enabled = access.trim().equals("true", ignoreCase = true)
      val appRows = JSONArray(
        SupabaseClient.get("driver_applications?select=id,full_name,phone,vehicle_type,current_address,status,admin_note&user_id=eq.$uid&limit=1").getOrThrow()
      )
      application = if (appRows.length() > 0) appRows.getJSONObject(0) else null
      val fetchedStatus = application?.optCleanString("status")

      // Strict security check: customer locations & assigned deliveries are ONLY accessible
      // to authenticated, approved drivers whose customer_driver_access is enabled.
      deliveries = if (hasDriverDeliveryAccess(true, enabled, fetchedStatus)) {
        JSONArray(SupabaseClient.rpc("driver_list_assigned_orders_v2", requireAuth = true).getOrThrow()).let { rows ->
          (0 until rows.length()).map { rows.getJSONObject(it) }
        }
      } else {
        emptyList()
      }
    } catch (e: Exception) {
      if (surfaceErrors) {
        if (deliveries.isEmpty()) deliveries = emptyList()
        error = extractDriverFriendlyError(e, "Could not load driver information. Please try again.")
      }
    } finally {
      loading = false
      loadMutex.unlock()
    }
  }

  val lifecycleOwner = LocalLifecycleOwner.current
  LaunchedEffect(user?.id, session != null) { load(showLoader = deliveries.isEmpty(), surfaceErrors = true) }

  DisposableEffect(lifecycleOwner, isAuthenticatedSession, user?.id) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME && isAuthenticatedSession) {
        scope.launch { load(showLoader = false, surfaceErrors = false) }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(lifecycleOwner, isAuthenticatedSession, user?.id) {
    if (!isAuthenticatedSession) return@LaunchedEffect
    val realtime = SupabaseRealtimeClient(
      subscribeToCatalogTables = false,
      subscribeToVendorOrderTables = false,
      subscribeToNotificationTables = false,
      subscribeToDriverTables = true,
      onDriverTablesChanged = {
        scope.launch { load(showLoader = false, surfaceErrors = false) }
      }
    )
    realtime.start()
    try {
      while (isActive) {
        delay(20_000L)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
          load(showLoader = false, surfaceErrors = false)
        }
      }
    } finally {
      realtime.stop()
    }
  }

  // Delivery filter counts
  val allCount = deliveries.size
  val activeCount = deliveries.count { it.optString("assignment_status", "assigned") in setOf("assigned", "pending") }
  val pickedUpCount = deliveries.count { it.optString("assignment_status") in setOf("picked_up", "in_transit") }
  val deliveredCount = deliveries.count { it.optString("assignment_status") == "delivered" }

  val filteredDeliveries = remember(deliveries, selectedFilter, selectedTab) {
    if (selectedTab == DriverBottomNavTab.HISTORY) {
      deliveries.filter { it.optString("assignment_status") in setOf("delivered", "cancelled", "failed") }
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
        .padding(padding)
        .testTag("driver_center_list"),
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

      if (!isAuthenticatedSession) {
        item {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
              .testTag("driver_signin_required_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(36.dp))
              Text("Sign In Required", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
              Text(
                "Please sign in to your JTBazar account to apply as a driver or manage assigned deliveries.",
                fontSize = 12.5.sp,
                color = JTTextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )
              Button(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
              ) {
                Text("Go Back", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      } else {
      // 2. DRIVER APPLICATION CARD
      if (application != null) {
        val appObj = application!!
        val applicantName = appObj.optCleanString("full_name").ifBlank { user?.fullName?.takeIf { it.isNotBlank() && !it.equals("null", true) } ?: "Driver" }
        val vehType = appObj.optCleanString("vehicle_type").ifBlank { "Vehicle not set" }
        val addrDisplay = appObj.optCleanString("current_address").ifBlank { "Location not set" }
        val status = appObj.optCleanString("status").lowercase().ifBlank { "pending" }
        val rejectionReason = extractDriverRejectionReason(appObj)

        item {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
              .shadow(elevation = 3.dp, shape = RoundedCornerShape(22.dp))
              .testTag("driver_application_status_card"),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9))
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Row(
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

                  if (status == "approved" && enabled) {
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
                  ),
                  modifier = Modifier.testTag("driver_application_status_badge")
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

              if (status == "pending") {
                Surface(
                  color = Color(0xFFFFFBEB),
                  shape = RoundedCornerShape(12.dp),
                  border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("driver_status_pending_card")
                ) {
                  Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(18.dp))
                    Text(
                      "Your application is under review. Assigned deliveries and customer delivery details remain locked until an administrator approves your account.",
                      fontSize = 12.sp,
                      color = Color(0xFF92400E)
                    )
                  }
                }
              } else if (status == "rejected" || status == "suspended") {
                Surface(
                  color = Color(0xFFFEF2F2),
                  shape = RoundedCornerShape(12.dp),
                  border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("driver_status_rejected_card")
                ) {
                  Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                      Text(
                        if (status == "suspended") "Application Suspended" else "Application Rejected",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF991B1B)
                      )
                    }
                    Text(
                      text = if (rejectionReason.isNotBlank()) "Reason: $rejectionReason"
                      else "Your application was not approved. Please review your details and upload clear documents.",
                      fontSize = 12.sp,
                      color = Color(0xFF7F1D1D),
                      modifier = Modifier.testTag("driver_rejection_reason")
                    )
                    if (status == "rejected" && enabled && !showResubmitForm) {
                      Button(
                        onClick = {
                          if (fullName.isBlank()) fullName = appObj.optCleanString("full_name")
                          if (phone.isBlank()) phone = appObj.optCleanString("phone")
                          if (currentAddress.isBlank()) currentAddress = appObj.optCleanString("current_address")
                          val prevVehicle = appObj.optCleanString("vehicle_type")
                          if (prevVehicle in listOf("Bike", "Car")) vehicleType = prevVehicle
                          cnicFrontDoc = DocUploadItem()
                          cnicBackDoc = DocUploadItem()
                          plateDoc = DocUploadItem()
                          showResubmitForm = true
                        },
                        modifier = Modifier
                          .fillMaxWidth()
                          .heightIn(min = 48.dp)
                          .testTag("resubmit_application_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
                      ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Resubmit Application", fontWeight = FontWeight.Bold)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      // NOT ENABLED / ACCESS RESTRICTED NOTICE OR APPLICATION / RESUBMISSION FORM
      if (!enabled) {
        item {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
              .testTag("driver_access_disabled_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Filled.Block, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(32.dp))
              Text("Driver Access Disabled", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
              Text(
                "Driver access is currently disabled for your account by an administrator. Assigned deliveries and customer details are unavailable.",
                fontSize = 12.sp,
                color = JTTextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )
            }
          }
        }
      } else if (application == null || (appStatus == "rejected" && showResubmitForm)) {
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
                  activePickerTarget = DriverPhotoPickerTarget.ApplicationDoc(DriverDocType.CNIC_FRONT)
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
                  activePickerTarget = DriverPhotoPickerTarget.ApplicationDoc(DriverDocType.CNIC_BACK)
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
                  activePickerTarget = DriverPhotoPickerTarget.ApplicationDoc(DriverDocType.VEHICLE_PLATE)
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

              if (!isFormValid) {
                Text(
                  text = "Fill name, phone, address, pin your map location, and upload all 3 verification documents to submit.",
                  fontSize = 11.sp,
                  color = Color(0xFF64748B)
                )
              }

              Button(
                enabled = !busy && isFormValid,
                onClick = {
                  if (busy || !isFormValid) return@Button
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
                        if (locationResult.isFailure) {
                          throw (locationResult.exceptionOrNull() ?: IllegalStateException("Could not save map location."))
                        }
                        showResubmitForm = false
                        message = "Driver application submitted! Admin approval is required."
                        load()
                      } else {
                        error = extractDriverFriendlyError(
                          result.exceptionOrNull(),
                          "Could not submit your driver application. Please verify your details and try again."
                        )
                      }
                    } catch (e: Exception) {
                      error = extractDriverFriendlyError(e, "Could not submit your driver application. Please try again.")
                    } finally {
                      busy = false
                    }
                  }
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .testTag("submit_driver_application_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
              ) {
                Text(
                  if (busy) "Submitting…" else if (showResubmitForm) "Resubmit Driver Application" else "Submit Driver Application",
                  fontWeight = FontWeight.Bold
                )
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
              val closedHistoryCount = deliveries.count {
                it.optString("assignment_status") in setOf("delivered", "cancelled", "failed")
              }
              Text(
                if (selectedTab == DriverBottomNavTab.HISTORY) "$closedHistoryCount completed / closed orders" else "$activeCount active delivery assignments",
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

        items(filteredDeliveries, key = { it.optCleanString("assignment_id") }) { delivery ->
          val assignmentId = delivery.optCleanString("assignment_id")
          val assignmentStatus = delivery.optCleanString("assignment_status").ifBlank { "assigned" }
          val orderNumber = delivery.optCleanString("order_number").ifBlank { "Order number unavailable" }
          val customerName = delivery.optCleanString("customer_name").ifBlank { "Customer" }
          val customerPhone = delivery.optCleanString("customer_phone")
          val addressText = driverAddressText(delivery)
          val isDelivered = assignmentStatus == "delivered" || assignmentStatus == "cancelled" || assignmentStatus == "failed"
          val otpVerified = isDeliveryOtpVerified(delivery)
          val proofSaved = hasDeliveryProofSaved(delivery)
          val isUploadingThisProof = uploadingProofAssignmentId == assignmentId
          val isCompletingThisDelivery = completingAssignmentIds.contains(assignmentId)
          val missingHint = missingCompletionStepsMessage(otpVerified, proofSaved)
          val batchNumber = delivery.optCleanString("delivery_batch_number")
          val failedReasonText = delivery.optCleanString("failed_reason").replace('_', ' ')
          val orderStatusText = delivery.optCleanString("order_status")

          // Format items summary (e.g. "BOLT B155 20000mAh Power Bank × 1")
          val itemsArr = delivery.optJSONArray("items")
          val itemsText = if (itemsArr != null && itemsArr.length() > 0) {
            val list = mutableListOf<String>()
            for (i in 0 until itemsArr.length()) {
              val itm = itemsArr.optJSONObject(i) ?: continue
              list.add("${itm.optCleanString("name").ifBlank { "Product" }} × ${itm.optInt("quantity", 1)}")
            }
            list.joinToString(", ")
          } else {
            "No item details available"
          }

          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
              .shadow(elevation = 2.dp, shape = RoundedCornerShape(22.dp))
              .testTag("driver_delivery_card_$assignmentId"),
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
                Surface(
                  color = when (assignmentStatus) { "delivered" -> Color(0xFFDCFCE7); "cancelled", "failed" -> Color(0xFFFEE2E2); "picked_up" -> Color(0xFFEFF6FF); else -> Color(0xFFFFF7ED) },
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
                        .background(when (assignmentStatus) { "delivered" -> Color(0xFF16A34A); "cancelled", "failed" -> Color(0xFFDC2626); "picked_up" -> Color(0xFF2563EB); else -> Color(0xFFEA580C) })
                    )
                    Text(
                      text = when {
                        assignmentStatus == "delivered" -> "Delivered"
                        assignmentStatus == "cancelled" -> "Cancelled"
                        assignmentStatus == "failed" -> "Delivery Failed" + if (failedReasonText.isNotBlank()) " · $failedReasonText" else ""
                        assignmentStatus == "picked_up" -> "In Transit"
                        orderStatusText.equals("shipped", true) -> "Shipped"
                        orderStatusText.equals("processing", true) -> "Processing"
                        orderStatusText.equals("confirmed", true) -> "Confirmed"
                        orderStatusText.equals("pending", true) -> "Pending"
                        else -> "Active"
                      },
                      fontSize = 11.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = when (assignmentStatus) { "delivered" -> Color(0xFF16A34A); "cancelled", "failed" -> Color(0xFFDC2626); "picked_up" -> Color(0xFF2563EB); else -> Color(0xFFEA580C) }
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
                Column(
                  modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(16.dp))
                    Column {
                      Text("Customer", fontSize = 10.sp, color = Color(0xFF64748B))
                      Text(customerName, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }
                  }

                  if (customerPhone.isNotBlank()) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                      modifier = Modifier.clickable {
                        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$customerPhone"))
                        context.startActivity(dial)
                      }
                    ) {
                      Icon(Icons.Filled.Phone, contentDescription = "Call Customer", tint = Color(0xFF2563EB), modifier = Modifier.size(15.dp))
                      Text(customerPhone, fontSize = 12.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Medium)
                    }
                  }

                  if (!isDelivered) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                }

                // Location preview is only available for active deliveries of approved+enabled drivers.
                if (!isDelivered && isApprovedDriver) {
                  Box(
                    modifier = Modifier
                      .size(width = 110.dp, height = 80.dp)
                      .clip(RoundedCornerShape(14.dp))
                      .background(Color(0xFFE2E8F0))
                      .clickable { onViewCustomerLocation(delivery) }
                  ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                      drawRect(Color(0xFFE8EEF5))
                      drawLine(Color.White, Offset(0f, size.height * 0.4f), Offset(size.width, size.height * 0.35f), strokeWidth = 14f)
                      drawLine(Color.White, Offset(size.width * 0.45f, 0f), Offset(size.width * 0.55f, size.height), strokeWidth = 12f)
                      drawLine(Color(0xFFBFDBFE), Offset(0f, size.height * 0.4f), Offset(size.width, size.height * 0.35f), strokeWidth = 3f)
                      drawRoundRect(Color(0xFFDCFCE7), Offset(size.width * 0.05f, size.height * 0.05f), androidx.compose.ui.geometry.Size(size.width * 0.35f, size.height * 0.28f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f))
                    }

                    Icon(
                      Icons.Filled.Place,
                      contentDescription = null,
                      tint = Color(0xFFDC2626),
                      modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp)
                    )

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
              }

              if (batchNumber.isNotBlank()) {
                Surface(color = Color(0xFFEDE9FE), shape = RoundedCornerShape(8.dp)) {
                  Text("Combined route · $batchNumber", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6D28D9))
                }
              }

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

              // Row 4: Navigation & Primary Action Buttons
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                if (!isDelivered && isApprovedDriver) {
                  Button(
                    onClick = { onViewCustomerLocation(delivery) },
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                      containerColor = Color(0xFFFFEDD5),
                      contentColor = Color(0xFFEA580C)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                  ) {
                    Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                      "View Customer Location",
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold
                    )
                  }
                }

                if (assignmentStatus == "assigned") {
                  Button(
                    enabled = !busy && !actionBusy,
                    onClick = {
                      if (busy || actionBusy) return@Button
                      scope.launch {
                        busy = true
                        error = null
                        val r = SupabaseClient.rpc(
                          "driver_update_delivery_status",
                          JSONObject().put("p_assignment_id", assignmentId).put("p_action", "picked_up").toString(),
                          requireAuth = true
                        )
                        if (r.isSuccess) {
                          message = "Parcel marked as picked up."
                          load(showLoader = false)
                        } else {
                          error = extractDriverFriendlyError(r.exceptionOrNull(), "Could not update pickup status. Please try again.")
                        }
                        busy = false
                      }
                    },
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 48.dp)
                      .testTag("mark_picked_up_button_$assignmentId"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                      containerColor = Color(0xFF16A34A),
                      contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                  ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Picked Up", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  }
                } else if (assignmentStatus == "picked_up") {
                  OutlinedButton(
                    onClick = {
                      actionAssignmentId = assignmentId
                      failureReason = "customer_unavailable"
                      failureNote = ""
                      showFailedDialog = true
                    },
                    enabled = !actionBusy && !busy,
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 48.dp)
                      .testTag("failed_delivery_button_$assignmentId"),
                    shape = RoundedCornerShape(12.dp)
                  ) {
                    Icon(Icons.Filled.ReportProblem, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Failed Delivery", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                  }
                } else {
                  Surface(
                    color = if (assignmentStatus == "cancelled" || assignmentStatus == "failed") Color(0xFFFEE2E2) else Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                      .weight(1f)
                      .height(48.dp)
                  ) {
                    Row(
                      modifier = Modifier.fillMaxSize(),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.Center
                    ) {
                      Icon(
                        if (assignmentStatus == "cancelled" || assignmentStatus == "failed") Icons.Filled.Cancel else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (assignmentStatus == "cancelled" || assignmentStatus == "failed") Color(0xFFDC2626) else Color(0xFF16A34A),
                        modifier = Modifier.size(15.dp)
                      )
                      Spacer(Modifier.width(6.dp))
                      Text(
                        when (assignmentStatus) {
                          "cancelled" -> "Cancelled"
                          "failed" -> "Delivery Failed"
                          else -> "Delivered"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (assignmentStatus == "delivered") Color(0xFF16A34A) else Color(0xFFDC2626)
                      )
                    }
                  }
                }
              }

              // 3-Step Delivery Completion Panel for In-Transit (picked_up) orders
              if (assignmentStatus == "picked_up") {
                Surface(
                  color = Color(0xFFF8FAFC),
                  shape = RoundedCornerShape(14.dp),
                  border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    Text(
                      "Delivery Completion Steps",
                      fontSize = 12.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFF0F172A)
                    )

                    // Step A: Verify customer OTP
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                      ) {
                        Icon(
                          if (otpVerified) Icons.Filled.CheckCircle else Icons.Filled.Password,
                          contentDescription = null,
                          tint = if (otpVerified) Color(0xFF16A34A) else Color(0xFFEA580C),
                          modifier = Modifier.size(18.dp)
                        )
                        Column {
                          Text("1. Customer OTP", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                          Text(
                            if (otpVerified) "OTP Verified" else "Pending customer 6-digit code",
                            fontSize = 10.5.sp,
                            color = if (otpVerified) Color(0xFF16A34A) else Color(0xFF64748B)
                          )
                        }
                      }

                      if (otpVerified) {
                        Surface(
                          color = Color(0xFFDCFCE7),
                          shape = RoundedCornerShape(20.dp),
                          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                          modifier = Modifier.testTag("otp_verified_badge_$assignmentId")
                        ) {
                          Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                          ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(14.dp))
                            Text("OTP Verified", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                          }
                        }
                      } else {
                        OutlinedButton(
                          enabled = !actionBusy && !busy,
                          onClick = {
                            actionAssignmentId = assignmentId
                            otpCode = ""
                            otpDialogError = null
                            showOtpDialog = true
                          },
                          modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("verify_otp_button_$assignmentId"),
                          shape = RoundedCornerShape(10.dp)
                        ) {
                          Text("Verify OTP", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                      }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    // Step B: Upload delivery photo proof (private bucket)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Row(
                          modifier = Modifier.weight(1f),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                          Icon(
                            if (proofSaved) Icons.Filled.CheckCircle else Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            tint = if (proofSaved) Color(0xFF16A34A) else Color(0xFFEA580C),
                            modifier = Modifier.size(18.dp)
                          )
                          Column {
                            Text("2. Delivery Photo Proof", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                            Text(
                              if (proofSaved) "Photo Proof Uploaded (Private)" else "Required before completing delivery",
                              fontSize = 10.5.sp,
                              color = if (proofSaved) Color(0xFF16A34A) else Color(0xFF64748B)
                            )
                          }
                        }

                        if (proofSaved) {
                          Surface(
                            color = Color(0xFFDCFCE7),
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                            modifier = Modifier.testTag("proof_uploaded_badge_$assignmentId")
                          ) {
                            Row(
                              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                              verticalAlignment = Alignment.CenterVertically,
                              horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                              Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(13.dp))
                              Text("Uploaded", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                            }
                          }
                        }
                      }

                      proofPreviews[assignmentId]?.let { previewBmp ->
                        Image(
                          bitmap = previewBmp.asImageBitmap(),
                          contentDescription = "Uploaded delivery proof preview",
                          contentScale = ContentScale.Crop,
                          modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(10.dp))
                        )
                      }

                      OutlinedButton(
                        enabled = !actionBusy && !busy && !isUploadingThisProof,
                        onClick = {
                          val retry = failedProofUploads[assignmentId]
                          if (retry != null) {
                            scope.launch { uploadDeliveryProofFromUri(assignmentId, retry.first, retry.second) }
                          } else {
                            activePickerTarget = DriverPhotoPickerTarget.DeliveryProof(assignmentId)
                            showPickerSheet = true
                          }
                        },
                        modifier = Modifier
                          .fillMaxWidth()
                          .heightIn(min = 48.dp)
                          .testTag("upload_proof_button_$assignmentId"),
                        shape = RoundedCornerShape(10.dp)
                      ) {
                        if (isUploadingThisProof) {
                          CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                          Spacer(Modifier.width(8.dp))
                          Text("Uploading Photo…", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        } else {
                          Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                          Spacer(Modifier.width(6.dp))
                          Text(
                            if (failedProofUploads.containsKey(assignmentId)) "Retry Photo Upload" else if (proofSaved) "Replace Proof Photo" else "Upload Proof Photo",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                          )
                        }
                      }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    // Step C: Separate Complete Delivery button (strictly gated)
                    if (missingHint != null) {
                      Text(
                        text = missingHint,
                        fontSize = 11.sp,
                        color = Color(0xFF92400E),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("completion_requirement_hint_$assignmentId")
                      )
                    }

                    val canCompleteThis = canCompleteDelivery(
                      assignmentStatus = assignmentStatus,
                      otpVerified = otpVerified,
                      proofSaved = proofSaved,
                      isBusy = busy || actionBusy || isUploadingThisProof || isCompletingThisDelivery
                    )

                    Button(
                      enabled = canCompleteThis,
                      onClick = {
                        if (!canCompleteDelivery(assignmentStatus, otpVerified, proofSaved, isBusy = false)) {
                          error = missingCompletionStepsMessage(otpVerified, proofSaved)
                          return@Button
                        }
                        if (completingAssignmentIds.contains(assignmentId) || actionBusy || busy) return@Button
                        scope.launch {
                          completingAssignmentIds = completingAssignmentIds + assignmentId
                          actionBusy = true
                          error = null
                          try {
                            SupabaseClient.rpc(
                              "driver_update_delivery_status",
                              JSONObject()
                                .put("p_assignment_id", assignmentId)
                                .put("p_action", "delivered")
                                .toString(),
                              requireAuth = true
                            ).getOrThrow()
                            proofPreviews = proofPreviews - assignmentId
                            message = "Delivery completed successfully."
                            load(showLoader = false)
                          } catch (e: Exception) {
                            error = extractDriverFriendlyError(e, "Could not complete delivery. Please verify OTP and photo proof.")
                          } finally {
                            completingAssignmentIds = completingAssignmentIds - assignmentId
                            actionBusy = false
                          }
                        }
                      },
                      modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("complete_delivery_button_$assignmentId"),
                      shape = RoundedCornerShape(12.dp),
                      colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF16A34A),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFE2E8F0),
                        disabledContentColor = Color(0xFF64748B)
                      )
                    ) {
                      if (isCompletingThisDelivery) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Completing Delivery…", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                      } else {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Complete Delivery", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                      }
                    }
                  }
                }
              } else if (assignmentStatus == "assigned") {
                OutlinedButton(
                  onClick = {
                    actionAssignmentId = assignmentId
                    failureReason = "customer_unavailable"
                    failureNote = ""
                    showFailedDialog = true
                  },
                  enabled = !actionBusy && !busy,
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                ) {
                  Text("Report Delivery Issue", fontSize = 11.5.sp)
                }
              } else if (assignmentStatus == "delivered" &&
                delivery.optCleanString("payment_method").equals("cod", true) &&
                delivery.isNull("cod_collected_amount")
              ) {
                Button(
                  onClick = {
                    actionAssignmentId = assignmentId
                    codAmount = delivery.optCleanString("order_total")
                    showCodDialog = true
                  },
                  enabled = !actionBusy && !busy,
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                ) {
                  Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Record COD Cash Collected")
                }
              }
            }
          }
        }
      }
      }
    }
  }

  // Step A: Verify customer OTP ONLY (never calls driver_update_delivery_status here)
  if (showOtpDialog) {
    AlertDialog(
      onDismissRequest = { if (!actionBusy) showOtpDialog = false },
      properties = DialogProperties(usePlatformDefaultWidth = false),
      modifier = Modifier
        .padding(horizontal = 24.dp)
        .widthIn(max = 560.dp),
      title = { Text("Verify Customer Delivery OTP", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            "Ask the customer to open JTBazar order tracking and share their 6-digit delivery code. After 5 incorrect attempts the code locks and a new code must be generated.",
            fontSize = 12.sp
          )
          OutlinedTextField(
            value = otpCode,
            onValueChange = {
              otpCode = it.filter(Char::isDigit).take(6)
              otpDialogError = null
            },
            label = { Text("6-digit OTP") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("driver_otp_input")
          )
          otpDialogError?.let { dialogErr ->
            Surface(
              color = MaterialTheme.colorScheme.errorContainer,
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("driver_otp_error")
            ) {
              Text(
                text = dialogErr,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(10.dp)
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          enabled = !actionBusy && otpCode.length == 6,
          onClick = {
            if (actionBusy || otpCode.length != 6) return@Button
            scope.launch {
              actionBusy = true
              otpDialogError = null
              try {
                val rawVerified = SupabaseClient.rpc(
                  "driver_verify_delivery_otp",
                  JSONObject()
                    .put("p_assignment_id", actionAssignmentId)
                    .put("p_code", otpCode)
                    .toString(),
                  requireAuth = true
                ).getOrThrow()
                val verified = rawVerified.trim().equals("true", ignoreCase = true)
                if (!verified) {
                  otpDialogError = "Incorrect or expired OTP. Please check the 6-digit code or ask the customer to generate a new one."
                } else {
                  deliveries = markAssignmentOtpVerifiedInList(deliveries, actionAssignmentId)
                  showOtpDialog = false
                  otpCode = ""
                  otpDialogError = null
                  message = "Customer OTP verified! Upload delivery photo proof (if not done) and tap Complete Delivery."
                  load(showLoader = false)
                }
              } catch (e: Exception) {
                otpDialogError = extractDriverFriendlyError(e, "Could not verify delivery OTP. Please try again.")
              } finally {
                actionBusy = false
              }
            }
          },
          modifier = Modifier.testTag("confirm_verify_otp_button")
        ) {
          Text(if (actionBusy) "Verifying…" else "Verify OTP")
        }
      },
      dismissButton = {
        TextButton(
          enabled = !actionBusy,
          onClick = {
            showOtpDialog = false
            otpDialogError = null
          }
        ) { Text("Cancel") }
      }
    )
  }

  if (showFailedDialog) {
    AlertDialog(
      onDismissRequest = { if (!actionBusy) showFailedDialog = false },
      title = { Text("Why was delivery unsuccessful?") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          listOf(
            "customer_unavailable" to "Customer unavailable",
            "address_issue" to "Address issue",
            "customer_refused" to "Customer refused",
            "delivery_failed" to "Other delivery failure"
          ).forEach { (value, label) ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier
                .fillMaxWidth()
                .clickable { failureReason = value }
            ) {
              RadioButton(selected = failureReason == value, onClick = { failureReason = value })
              Text(label, fontSize = 13.sp)
            }
          }
          OutlinedTextField(
            value = failureNote,
            onValueChange = { failureNote = it.take(1000) },
            label = { Text("Extra note (optional)") },
            minLines = 2
          )
        }
      },
      confirmButton = {
        Button(
          enabled = !actionBusy,
          onClick = {
            if (actionBusy) return@Button
            scope.launch {
              actionBusy = true
              try {
                SupabaseClient.rpc(
                  "driver_mark_delivery_failed",
                  JSONObject()
                    .put("p_assignment_id", actionAssignmentId)
                    .put("p_reason", failureReason)
                    .put("p_note", failureNote)
                    .toString(),
                  requireAuth = true
                ).getOrThrow()
                showFailedDialog = false
                message = "Delivery issue reported to JTBazar."
                load(showLoader = false)
              } catch (e: Exception) {
                error = extractDriverFriendlyError(e, "Could not report delivery issue.")
              } finally {
                actionBusy = false
              }
            }
          }
        ) { Text(if (actionBusy) "Submitting…" else "Submit reason") }
      },
      dismissButton = {
        TextButton(enabled = !actionBusy, onClick = { showFailedDialog = false }) { Text("Cancel") }
      }
    )
  }

  if (showCodDialog) {
    AlertDialog(
      onDismissRequest = { if (!actionBusy) showCodDialog = false },
      title = { Text("Record COD cash") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Enter the amount physically received from the customer.", fontSize = 12.sp)
          OutlinedTextField(
            value = codAmount,
            onValueChange = { codAmount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) },
            label = { Text("Collected amount (PKR)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
          )
        }
      },
      confirmButton = {
        Button(
          enabled = !actionBusy && codAmount.toDoubleOrNull() != null,
          onClick = {
            val parsedAmount = codAmount.toDoubleOrNull() ?: return@Button
            if (actionBusy) return@Button
            scope.launch {
              actionBusy = true
              try {
                SupabaseClient.rpc(
                  "driver_record_cod_collection",
                  JSONObject()
                    .put("p_assignment_id", actionAssignmentId)
                    .put("p_amount", parsedAmount)
                    .toString(),
                  requireAuth = true
                ).getOrThrow()
                showCodDialog = false
                message = "COD collection recorded."
                load(showLoader = false)
              } catch (e: Exception) {
                error = extractDriverFriendlyError(e, "Could not record COD collection.")
              } finally {
                actionBusy = false
              }
            }
          }
        ) { Text(if (actionBusy) "Saving…" else "Save collection") }
      },
      dismissButton = {
        TextButton(enabled = !actionBusy, onClick = { showCodDialog = false }) { Text("Cancel") }
      }
    )
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

  // BOTTOM SHEET: PICK CAMERA OR GALLERY (application documents & delivery photo proof)
  if (showPickerSheet && activePickerTarget != null) {
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
        val title = when (val target = activePickerTarget) {
          is DriverPhotoPickerTarget.ApplicationDoc -> when (target.docType) {
            DriverDocType.CNIC_FRONT -> "CNIC Front Image"
            DriverDocType.CNIC_BACK -> "CNIC Back Image"
            DriverDocType.VEHICLE_PLATE -> "Vehicle Number Plate"
          }
          is DriverPhotoPickerTarget.DeliveryProof -> "Delivery Photo Proof"
          null -> "Photo"
        }
        Text("Select $title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Choose camera to take a photo or select from device gallery (max 8 MB).", fontSize = 12.sp, color = JTTextSecondary)

        Spacer(Modifier.height(4.dp))

        OutlinedButton(
          onClick = {
            showPickerSheet = false
            startCameraCapture()
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("picker_camera_button"),
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
            .height(48.dp)
            .testTag("picker_gallery_button"),
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
