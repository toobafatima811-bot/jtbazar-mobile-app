package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.R
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseRealtimeClient
import com.example.localization.AppLanguageManager
import com.example.localization.appText
import com.example.location.AddressReverseGeocoder
import com.example.location.LocationFetchResult
import com.example.location.LocationResolver
import com.example.location.toReadableFullAddress
import com.example.ui.theme.JTTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

// Express Courier Console design palette
private val DriverBg = Color(0xFFF6F7FB)
private val DriverSurface = Color(0xFFFFFFFF)
private val DriverSurfaceMuted = Color(0xFFF8FAFC)
private val DriverInk = Color(0xFF0F172A)
private val DriverInkSecondary = Color(0xFF475569)
private val DriverInkMuted = Color(0xFF64748B)
private val DriverBorder = Color(0xFFE2E8F0)
private val DriverOrange = Color(0xFFEA580C)
private val DriverOrangeDark = Color(0xFFC2410C)
private val DriverOrangeSoft = Color(0xFFFFF7ED)
private val DriverOrangeBorder = Color(0xFFFED7AA)
private val DriverGreen = Color(0xFF16A34A)
private val DriverGreenDark = Color(0xFF166534)
private val DriverGreenSoft = Color(0xFFDCFCE7)
private val DriverGreenBorder = Color(0xFF86EFAC)
private val DriverBlue = Color(0xFF2563EB)
private val DriverBlueDark = Color(0xFF1D4ED8)
private val DriverBlueSoft = Color(0xFFEFF6FF)
private val DriverBlueBorder = Color(0xFFBFDBFE)
private val DriverRed = Color(0xFFDC2626)
private val DriverRedDark = Color(0xFF991B1B)
private val DriverRedSoft = Color(0xFFFEE2E2)
private val DriverRedBorder = Color(0xFFFCA5A5)
private val DriverAmber = Color(0xFFB45309)
private val DriverAmberSoft = Color(0xFFFEF3C7)
private val DriverAmberBorder = Color(0xFFFDE68A)
private val DriverViolet = Color(0xFF6D28D9)
private val DriverVioletSoft = Color(0xFFEDE9FE)

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
    ?: AppLanguageManager.tr("Saved delivery address", "محفوظ شدہ ترسیل کا پتہ")
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

enum class DriverDeliveryFilter(val label: String, val urduLabel: String) {
  ALL("All", "تمام"),
  PENDING("Pending", "زیرِ التواء"),
  CONFIRMED("Confirmed", "تصدیق شدہ"),
  PARTIALLY_DELIVERED("Partially delivered", "جزوی طور پر ترسیل شدہ"),
  PROCESSING("Processing", "تیاری میں"),
  ACTIVE("Active", "فعال"),
  SHIPPED("Shipped", "روانہ"),
  PICKED_UP("In Transit", "راستے میں"),
  DELIVERED("Delivered", "ترسیل مکمل"),
  CANCELLED("Cancelled", "منسوخ");

  fun localizedLabel(): String = AppLanguageManager.tr(label, urduLabel)
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

@Composable
private fun DriverResponsiveSection(
  modifier: Modifier = Modifier,
  horizontalPadding: Boolean = true,
  content: @Composable () -> Unit
) {
  Box(
    modifier = modifier.fillMaxWidth(),
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .widthIn(max = 640.dp)
        .fillMaxWidth()
        .then(if (horizontalPadding) Modifier.padding(horizontal = 16.dp) else Modifier)
    ) {
      content()
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
  var refreshDriverList: suspend () -> Unit = {}

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
        ?: throw IllegalStateException(
          AppLanguageManager.tr("Please sign in again.", "براہ کرم دوبارہ سائن ان کریں۔")
        )
      val rawBytes = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
      } ?: throw IllegalStateException(
        AppLanguageManager.tr(
          "Could not read the selected delivery photo.",
          "منتخب کردہ ترسیل کی تصویر پڑھی نہیں جا سکی۔"
        )
      )

      if (rawBytes.size > 12 * 1024 * 1024) {
        throw IllegalArgumentException(
          AppLanguageManager.tr(
            "Photo is too large. Please choose a photo under 12 MB.",
            "تصویر بہت بڑی ہے۔ براہ کرم 12 ایم بی سے کم کی تصویر منتخب کریں۔"
          )
        )
      }

      // Normalize every selected image to real JPEG bytes before validation/upload.
      // Android ImageDecoder supports HEIC/HEIF on API 28+; this prevents the
      // original image/heic MIME type from ever reaching Supabase Storage.
      val decodedBitmap = withContext(Dispatchers.Default) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(context.contentResolver, uri)
          ) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
          }
        } else {
          BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
        }
      } ?: throw IllegalArgumentException(
        AppLanguageManager.tr(
          "This photo format is not supported on this device. Please choose a JPG or PNG photo.",
          "اس ڈیوائس پر تصویر کا یہ فارمیٹ معاون نہیں ہے۔ براہ کرم JPG یا PNG تصویر منتخب کریں۔"
        )
      )

      val jpegBytes = withContext(Dispatchers.Default) {
        ByteArrayOutputStream().use { output ->
          if (!decodedBitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
            throw IllegalStateException(
              AppLanguageManager.tr(
                "Could not convert the photo to JPEG.",
                "تصویر کو JPEG میں تبدیل نہیں کیا جا سکا۔"
              )
            )
          }
          output.toByteArray()
        }
      }
      val validated = validateDeliveryProofImage(jpegBytes, "image/jpeg")

      // Never trust a stale UI assignment: the server RPC also verifies that this
      // authenticated, approved driver owns the active assignment.
      val storagePath = buildDeliveryProofPath(uid, assignmentId, validated.extension)
      SupabaseClient.uploadPrivateFile("delivery-proofs", storagePath, validated.bytes, validated.mimeType).getOrThrow()
      try {
        val deliveryForProof = deliveries.firstOrNull { it.optCleanString("assignment_id") == assignmentId } ?: JSONObject()
        val proofRpc = driverDeliveryRpcName(
          deliveryForProof,
          "driver_set_delivery_proof_path",
          "driver_set_manual_order_delivery_proof_path"
        )
        SupabaseClient.rpc(
          proofRpc,
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
      message = AppLanguageManager.tr(
        "Delivery photo proof saved securely. Delivery status updated automatically when OTP is also verified.",
        "ترسیل کی تصویر محفوظ طریقے سے اپ لوڈ ہو گئی۔ او ٹی پی کی تصدیق کے ساتھ ہی ترسیل خودکار طور پر مکمل ہو جائے گی۔"
      )
      refreshDriverList()
    } catch (e: Exception) {
      failedProofUploads = failedProofUploads + (assignmentId to (uri to isCameraCapture))
      error = extractDriverFriendlyError(
        e,
        AppLanguageManager.tr(
          "Could not upload delivery photo proof.",
          "ترسیل کی تصویر اپ لوڈ نہیں ہو سکی۔"
        )
      )
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
      addressLocationStatus = AppLanguageManager.tr(
        "Current location & street address filled. Verify or adjust on map if needed.",
        "موجودہ مقام اور گلی کا پتہ پُر کر دیا گیا ہے۔ اگر ضرورت ہو تو نقشے پر درست کر لیں۔"
      )
    } else {
      addressLocationError = null
      addressLocationStatus = AppLanguageManager.tr(
        "Location coordinates captured. Please enter or verify your street address.",
        "مقام کے کوآرڈینیٹس حاصل ہو گئے ہیں۔ براہ کرم اپنا پتہ درج کریں یا تصدیق کریں۔"
      )
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
      addressLocationError = AppLanguageManager.tr(
        "Turn on device location / GPS to fill your current address.",
        "اپنا موجودہ پتہ پُر کرنے کے لیے ڈیوائس کی لوکیشن / جی پی ایس آن کریں۔"
      )
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
              addressLocationError = AppLanguageManager.tr(
                "Turn on device location / GPS and try again.",
                "ڈیوائس کی لوکیشن / جی پی ایس آن کریں اور دوبارہ کوشش کریں۔"
              )
            }
          }
          is LocationFetchResult.Failure -> {
            locatingAddress = false
            addressLocationError = result.message
          }
        }
      } catch (_: Exception) {
        locatingAddress = false
        addressLocationError = AppLanguageManager.tr(
          "Unable to detect current location. Please try again.",
          "موجودہ مقام معلوم نہیں ہو سکا۔ براہ کرم دوبارہ کوشش کریں۔"
        )
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
      addressLocationError = AppLanguageManager.tr(
        "Location permission denied. Grant permission or pick on map.",
        "لوکیشن کی اجازت نہیں ملی۔ اجازت دیں یا نقشے پر منتخب کریں۔"
      )
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
        errorMessage = AppLanguageManager.tr(
          "Please sign in again before uploading verification documents.",
          "تصدیقی دستاویزات اپ لوڈ کرنے سے پہلے دوبارہ سائن ان کریں۔"
        )
      )
      withContext(Dispatchers.Main) {
        setDocState(failed)
        error = AppLanguageManager.tr(
          "Please sign in again before uploading verification documents.",
          "تصدیقی دستاویزات اپ لوڈ کرنے سے پہلے دوبارہ سائن ان کریں۔"
        )
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
        errorMessage = AppLanguageManager.tr(
          "Choose a document photo up to 8 MB.",
          "زیادہ سے زیادہ 8 ایم بی تک کی دستاویز کی تصویر منتخب کریں۔"
        )
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
          errorMessage = extractDriverFriendlyError(
            err,
            AppLanguageManager.tr(
              "Document upload failed. Please try again.",
              "دستاویز اپ لوڈ ناکام ہو گئی۔ براہ کرم دوبارہ کوشش کریں۔"
            )
          )
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
                  withContext(Dispatchers.Main) {
                    error = AppLanguageManager.tr(
                      "Captured photo could not be decoded. Please try again.",
                      "کیمرے کی تصویر پڑھی نہیں جا سکی۔ براہ کرم دوبارہ کوشش کریں۔"
                    )
                  }
                } else {
                  uploadDocBytes(target.docType, bytes, uri, bmp)
                }
              } else {
                withContext(Dispatchers.Main) {
                  error = AppLanguageManager.tr(
                    "Captured photo was empty. Please try again.",
                    "کیمرے کی تصویر خالی تھی۔ براہ کرم دوبارہ کوشش کریں۔"
                  )
                }
              }
            } catch (e: Exception) {
              withContext(Dispatchers.Main) {
                error = extractDriverFriendlyError(
                  e,
                  AppLanguageManager.tr("Could not read camera photo.", "کیمرے کی تصویر پڑھی نہیں جا سکی۔")
                )
              }
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
                withContext(Dispatchers.Main) {
                  error = AppLanguageManager.tr(
                    "Selected file is not an image. Please choose a JPEG or PNG photo.",
                    "منتخب فائل تصویر نہیں ہے۔ براہ کرم JPEG یا PNG تصویر منتخب کریں۔"
                  )
                }
                return@launch
              }
              val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
              if (bytes != null && bytes.isNotEmpty()) {
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp == null) {
                  withContext(Dispatchers.Main) {
                    error = AppLanguageManager.tr(
                      "Selected file is not a valid image. Please choose a photo.",
                      "منتخب فائل درست تصویر نہیں ہے۔ براہ کرم کوئی تصویر منتخب کریں۔"
                    )
                  }
                } else {
                  uploadDocBytes(target.docType, bytes, uri, bmp)
                }
              } else {
                withContext(Dispatchers.Main) {
                  error = AppLanguageManager.tr(
                    "Selected photo was empty. Please choose another file.",
                    "منتخب تصویر خالی تھی۔ براہ کرم کوئی اور فائل منتخب کریں۔"
                  )
                }
              }
            } catch (e: Exception) {
              withContext(Dispatchers.Main) {
                error = extractDriverFriendlyError(
                  e,
                  AppLanguageManager.tr("Could not read selected photo.", "منتخب تصویر پڑھی نہیں جا سکی۔")
                )
              }
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
      error = extractDriverFriendlyError(
        e,
        AppLanguageManager.tr("Failed to launch camera.", "کیمرہ شروع نہیں ہو سکا۔")
      )
    }
  }

  // Camera permission launcher
  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      launchCameraForTarget()
    } else {
      error = AppLanguageManager.tr(
        "Camera permission is required to take a photo. You can still select an image from your gallery.",
        "تصویر لینے کے لیے کیمرے کی اجازت درکار ہے۔ آپ گیلری سے بھی تصویر منتخب کر سکتے ہیں۔"
      )
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
        JSONArray(SupabaseClient.rpc("driver_list_assigned_orders_v3", requireAuth = true).getOrThrow()).let { rows ->
          (0 until rows.length()).map { rows.getJSONObject(it) }.sortedWith(
            compareBy<JSONObject> { it.optCleanString("assignment_status") in setOf("delivered", "cancelled", "failed") }
              .thenByDescending { it.optCleanString("assigned_at") }
          )
        }
      } else {
        emptyList()
      }
    } catch (e: Exception) {
      if (surfaceErrors) {
        if (deliveries.isEmpty()) deliveries = emptyList()
        error = extractDriverFriendlyError(
          e,
          AppLanguageManager.tr(
            "Could not load driver information. Please try again.",
            "ڈرائیور کی معلومات لوڈ نہیں ہو سکیں۔ براہ کرم دوبارہ کوشش کریں۔"
          )
        )
      }
    } finally {
      loading = false
      loadMutex.unlock()
    }
  }
  refreshDriverList = { load(showLoader = false, surfaceErrors = false) }

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
  val closedHistoryCount = deliveries.count {
    it.optString("assignment_status") in setOf("delivered", "cancelled", "failed")
  }

  val filteredDeliveries = remember(deliveries, selectedFilter, selectedTab) {
    if (selectedTab == DriverBottomNavTab.HISTORY) {
      deliveries.filter { it.optString("assignment_status") in setOf("delivered", "cancelled", "failed") }
    } else {
      when (selectedFilter) {
        DriverDeliveryFilter.ALL -> deliveries
        DriverDeliveryFilter.PENDING -> deliveries.filter { it.optString("order_status").equals("pending", true) }
        DriverDeliveryFilter.CONFIRMED -> deliveries.filter { it.optString("order_status").equals("confirmed", true) }
        DriverDeliveryFilter.PARTIALLY_DELIVERED -> deliveries.filter { it.optString("order_status").equals("partially_delivered", true) }
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
    containerColor = DriverBg,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    bottomBar = {
      if (isApprovedDriver) {
        Surface(
          color = DriverSurface,
          shadowElevation = 12.dp,
          shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
          border = BorderStroke(1.dp, DriverBorder),
          modifier = Modifier.fillMaxWidth()
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .windowInsetsPadding(WindowInsets.navigationBars),
            contentAlignment = Alignment.Center
          ) {
            Row(
              modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceEvenly,
              verticalAlignment = Alignment.CenterVertically
            ) {
              DriverNavTabItem(
                selected = selectedTab == DriverBottomNavTab.DELIVERIES,
                icon = Icons.Filled.LocalShipping,
                label = appText("Deliveries", "ترسیلات"),
                badgeCount = (activeCount + pickedUpCount).takeIf { it > 0 },
                onClick = {
                  selectedTab = DriverBottomNavTab.DELIVERIES
                  selectedFilter = DriverDeliveryFilter.ALL
                },
                modifier = Modifier.weight(1f)
              )
              DriverNavTabItem(
                selected = selectedTab == DriverBottomNavTab.HISTORY,
                icon = Icons.Filled.ReceiptLong,
                label = appText("History", "تاریخچہ"),
                badgeCount = closedHistoryCount.takeIf { it > 0 },
                onClick = { selectedTab = DriverBottomNavTab.HISTORY },
                modifier = Modifier.weight(1f)
              )
              DriverNavTabItem(
                selected = selectedTab == DriverBottomNavTab.PROFILE,
                icon = Icons.Filled.Person,
                label = appText("Profile", "پروفائل"),
                badgeCount = null,
                onClick = onOpenProfile,
                modifier = Modifier.weight(1f)
              )
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
      // 1. EXPRESS COURIER CONSOLE HERO HEADER
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              brush = Brush.verticalGradient(
                colors = listOf(
                  Color(0xFFFB923C),
                  DriverOrange,
                  DriverOrangeDark
                )
              )
            )
        ) {
          // Subtle geometric courier route backdrop
          Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
              color = Color.White.copy(alpha = 0.08f),
              radius = size.width * 0.28f,
              center = Offset(size.width * 0.92f, size.height * 0.15f)
            )
            drawCircle(
              color = Color.White.copy(alpha = 0.06f),
              radius = size.width * 0.2f,
              center = Offset(size.width * 0.08f, size.height * 0.85f)
            )
          }

          Column(
            modifier = Modifier
              .fillMaxWidth()
              .statusBarsPadding()
              .padding(bottom = if (isApprovedDriver) 18.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Column(
              modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                    .clickable { onBack() },
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = appText("Back", "واپس"),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                  )
                }

                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                ) {
                  Text(
                    appText("JTBazar Driver", "جے ٹی بازار ڈرائیور"),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    appText("Express Courier Console", "ایکسپریس کوریئر کنسول"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFFFEDD5),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }

                Box(
                  modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                    .clickable { onOpenNotifications() },
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    Icons.Filled.Notifications,
                    contentDescription = appText("Notifications", "اطلاعات"),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                  )
                  Box(
                    modifier = Modifier
                      .align(Alignment.TopEnd)
                      .padding(top = 10.dp, end = 10.dp)
                      .size(8.dp)
                      .clip(CircleShape)
                      .background(Color(0xFFEF4444))
                      .border(1.dp, Color.White, CircleShape)
                  )
                }
              }

              // Interactive quick stats strip for approved drivers
              if (isApprovedDriver) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  DriverHeroStatTile(
                    label = appText("Active", "فعال"),
                    count = activeCount,
                    icon = Icons.Filled.Schedule,
                    selected = selectedTab == DriverBottomNavTab.DELIVERIES && selectedFilter == DriverDeliveryFilter.ACTIVE,
                    onClick = {
                      selectedTab = DriverBottomNavTab.DELIVERIES
                      selectedFilter = if (selectedFilter == DriverDeliveryFilter.ACTIVE) DriverDeliveryFilter.ALL else DriverDeliveryFilter.ACTIVE
                    },
                    modifier = Modifier.weight(1f)
                  )
                  DriverHeroStatTile(
                    label = appText("In Transit", "راستے میں"),
                    count = pickedUpCount,
                    icon = Icons.Filled.LocalShipping,
                    selected = selectedTab == DriverBottomNavTab.DELIVERIES && selectedFilter == DriverDeliveryFilter.PICKED_UP,
                    onClick = {
                      selectedTab = DriverBottomNavTab.DELIVERIES
                      selectedFilter = if (selectedFilter == DriverDeliveryFilter.PICKED_UP) DriverDeliveryFilter.ALL else DriverDeliveryFilter.PICKED_UP
                    },
                    modifier = Modifier.weight(1f)
                  )
                  DriverHeroStatTile(
                    label = appText("Delivered", "مکمل"),
                    count = deliveredCount,
                    icon = Icons.Filled.CheckCircle,
                    selected = selectedTab == DriverBottomNavTab.DELIVERIES && selectedFilter == DriverDeliveryFilter.DELIVERED,
                    onClick = {
                      selectedTab = DriverBottomNavTab.DELIVERIES
                      selectedFilter = if (selectedFilter == DriverDeliveryFilter.DELIVERED) DriverDeliveryFilter.ALL else DriverDeliveryFilter.DELIVERED
                    },
                    modifier = Modifier.weight(1f)
                  )
                }
              }
            }
          }
        }
      }

      // LOADING / ERROR / SUCCESS ALERTS
      if (loading) {
        item {
          DriverResponsiveSection {
            LinearProgressIndicator(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp)),
              color = DriverOrange,
              trackColor = DriverOrangeBorder.copy(alpha = 0.4f)
            )
          }
        }
      }

      error?.let { err ->
        item {
          DriverResponsiveSection {
            Surface(
              color = MaterialTheme.colorScheme.errorContainer,
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, DriverRedBorder),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(err, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp, modifier = Modifier.weight(1f))
              }
            }
          }
        }
      }

      message?.let { msg ->
        item {
          DriverResponsiveSection {
            Surface(
              color = Color(0xFFF0FDF4),
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, DriverGreenBorder),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = DriverGreen)
                Text(msg, color = DriverGreenDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
              }
            }
          }
        }
      }

      if (!isAuthenticatedSession) {
        item {
          DriverResponsiveSection {
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("driver_signin_required_card"),
              shape = RoundedCornerShape(22.dp),
              colors = CardDefaults.cardColors(containerColor = DriverSurface),
              border = BorderStroke(1.dp, DriverBorder)
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Surface(
                  color = DriverOrangeSoft,
                  shape = CircleShape,
                  modifier = Modifier.size(64.dp)
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = DriverOrange, modifier = Modifier.size(32.dp))
                  }
                }
                Text(
                  appText("Sign In Required", "سائن ان درکار ہے"),
                  fontWeight = FontWeight.Bold,
                  fontSize = 17.sp,
                  color = DriverInk
                )
                Text(
                  appText(
                    "Please sign in to your JTBazar account to apply as a driver or manage assigned deliveries.",
                    "ڈرائیور کے طور پر درخواست دینے یا تفویض کردہ ترسیلات دیکھنے کے لیے براہ کرم اپنے جے ٹی بازار اکاؤنٹ میں سائن ان کریں۔"
                  ),
                  fontSize = 12.5.sp,
                  color = JTTextSecondary,
                  textAlign = TextAlign.Center
                )
                Button(
                  onClick = onBack,
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                  shape = RoundedCornerShape(12.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = DriverOrange)
                ) {
                  Text(appText("Go Back", "واپس جائیں"), fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      } else {
        // 2. DRIVER APPLICATION STATUS CARD
        if (application != null) {
          val appObj = application!!
          val applicantName = appObj.optCleanString("full_name").ifBlank {
            user?.fullName?.takeIf { it.isNotBlank() && !it.equals("null", true) }
              ?: AppLanguageManager.tr("Driver", "ڈرائیور")
          }
          val vehType = appObj.optCleanString("vehicle_type").ifBlank { AppLanguageManager.tr("Vehicle not set", "گاڑی درج نہیں") }
          val addrDisplay = appObj.optCleanString("current_address").ifBlank { AppLanguageManager.tr("Location not set", "مقام درج نہیں") }
          val status = appObj.optCleanString("status").lowercase().ifBlank { "pending" }
          val rejectionReason = extractDriverRejectionReason(appObj)

          item {
            DriverResponsiveSection {
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .shadow(elevation = 4.dp, shape = RoundedCornerShape(22.dp))
                  .testTag("driver_application_status_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = DriverSurface),
                border = BorderStroke(
                  1.dp,
                  when (status) {
                    "approved" -> DriverGreenBorder.copy(alpha = 0.7f)
                    "rejected", "suspended" -> DriverRedBorder.copy(alpha = 0.7f)
                    else -> DriverAmberBorder
                  }
                )
              ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isNarrowHeader = maxWidth < 330.dp
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        // Framed courier avatar ring
                        Box(
                          modifier = Modifier
                            .size(68.dp)
                            .border(
                              2.dp,
                              if (status == "approved" && enabled) DriverGreen else DriverOrangeBorder,
                              CircleShape
                            )
                            .padding(3.dp)
                        ) {
                          Box(
                            modifier = Modifier
                              .fillMaxSize()
                              .clip(CircleShape)
                              .background(DriverBlueSoft),
                            contentAlignment = Alignment.Center
                          ) {
                            Image(
                              painter = painterResource(id = R.drawable.ic_driver_courier),
                              contentDescription = appText("Driver Avatar", "ڈرائیور تصویر"),
                              contentScale = ContentScale.Crop,
                              modifier = Modifier.fillMaxSize()
                            )
                          }
                          if (status == "approved" && enabled) {
                            Box(
                              modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(DriverGreen)
                                .border(1.5.dp, Color.White, CircleShape),
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

                        Spacer(Modifier.width(12.dp))

                        Column(
                          modifier = Modifier.weight(1f),
                          verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                          Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                          ) {
                            Text(
                              appText("Driver Profile", "ڈرائیور پروفائل"),
                              fontSize = 15.5.sp,
                              fontWeight = FontWeight.Bold,
                              color = DriverInk,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis,
                              modifier = Modifier.weight(1f)
                            )
                            if (!isNarrowHeader) {
                              Spacer(Modifier.width(8.dp))
                              DriverStatusPillBadge(status = status)
                            }
                          }

                          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = DriverOrange, modifier = Modifier.size(14.dp))
                            Text(
                              applicantName,
                              fontSize = 12.5.sp,
                              fontWeight = FontWeight.SemiBold,
                              color = DriverInk,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis
                            )
                          }

                          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.TwoWheeler, contentDescription = null, tint = DriverInkMuted, modifier = Modifier.size(14.dp))
                            Text(
                              appText("Vehicle: $vehType", "گاڑی: $vehType"),
                              fontSize = 11.5.sp,
                              color = DriverInkSecondary,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis
                            )
                          }

                          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Place, contentDescription = null, tint = DriverInkMuted, modifier = Modifier.size(14.dp))
                            Text(
                              addrDisplay,
                              fontSize = 11.5.sp,
                              color = DriverInkSecondary,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis
                            )
                          }
                        }
                      }

                      if (isNarrowHeader) {
                        DriverStatusPillBadge(status = status)
                      }
                    }
                  }

                  if (status == "pending") {
                    Surface(
                      color = Color(0xFFFFFBEB),
                      shape = RoundedCornerShape(14.dp),
                      border = BorderStroke(1.dp, DriverAmberBorder),
                      modifier = Modifier
                        .fillMaxWidth()
                        .testTag("driver_status_pending_card")
                    ) {
                      Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                      ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = DriverAmber, modifier = Modifier.size(18.dp))
                        Text(
                          appText(
                            "Your application is under review. Assigned deliveries and customer delivery details remain locked until an administrator approves your account.",
                            "آپ کی درخواست زیرِ غور ہے۔ ایڈمن کی منظوری تک تفویض کردہ ترسیلات اور کسٹمر کی تفصیلات بند رہیں گی۔"
                          ),
                          fontSize = 12.sp,
                          color = Color(0xFF92400E)
                        )
                      }
                    }
                  } else if (status == "rejected" || status == "suspended") {
                    Surface(
                      color = Color(0xFFFEF2F2),
                      shape = RoundedCornerShape(14.dp),
                      border = BorderStroke(1.dp, DriverRedBorder),
                      modifier = Modifier
                        .fillMaxWidth()
                        .testTag("driver_status_rejected_card")
                    ) {
                      Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                      ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                          Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = DriverRed, modifier = Modifier.size(18.dp))
                          Text(
                            if (status == "suspended") appText("Application Suspended", "درخواست معطل کر دی گئی")
                            else appText("Application Rejected", "درخواست مسترد کر دی گئی"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DriverRedDark
                          )
                        }
                        Text(
                          text = if (rejectionReason.isNotBlank()) {
                            appText("Reason: $rejectionReason", "وجہ: $rejectionReason")
                          } else {
                            appText(
                              "Your application was not approved. Please review your details and upload clear documents.",
                              "آپ کی درخواست منظور نہیں ہوئی۔ براہ کرم اپنی تفصیلات چیک کریں اور واضح دستاویزات اپ لوڈ کریں۔"
                            )
                          },
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
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DriverOrange)
                          ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(appText("Resubmit Application", "دوبارہ درخواست جمع کروائیں"), fontWeight = FontWeight.Bold)
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

        // NOT ENABLED / ACCESS RESTRICTED NOTICE OR APPLICATION / RESUBMISSION FORM
        if (!enabled) {
          item {
            DriverResponsiveSection {
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("driver_access_disabled_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = DriverSurface),
                border = BorderStroke(1.dp, DriverRedBorder)
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Surface(color = DriverRedSoft, shape = CircleShape, modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(Icons.Filled.Block, contentDescription = null, tint = DriverRed, modifier = Modifier.size(30.dp))
                    }
                  }
                  Text(
                    appText("Driver Access Disabled", "ڈرائیور رسائی غیر فعال ہے"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp,
                    color = DriverInk
                  )
                  Text(
                    appText(
                      "Driver access is currently disabled for your account by an administrator. Assigned deliveries and customer details are unavailable.",
                      "ایڈمن کی جانب سے آپ کے اکاؤنٹ کے لیے ڈرائیور رسائی فی الحال بند ہے۔ تفویض کردہ ترسیلات اور کسٹمر کی تفصیلات دستیاب نہیں ہیں۔"
                    ),
                    fontSize = 12.sp,
                    color = JTTextSecondary,
                    textAlign = TextAlign.Center
                  )
                }
              }
            }
          }
        } else if (application == null || (appStatus == "rejected" && showResubmitForm)) {
          // 3-STEP ONBOARDING & DRIVER APPLICATION FORM
          item {
            DriverResponsiveSection {
              val step1Done = fullName.trim().isNotBlank() && phone.trim().isNotBlank()
              val step2Done = currentAddress.trim().isNotBlank() && applicationLat != null && applicationLng != null
              val step3Done = !cnicFrontDoc.storagePath.isNullOrBlank() &&
                !cnicBackDoc.storagePath.isNullOrBlank() &&
                !plateDoc.storagePath.isNullOrBlank()

              Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = DriverSurface),
                border = BorderStroke(1.dp, DriverBorder)
              ) {
                Column(
                  modifier = Modifier.padding(18.dp),
                  verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                      appText("Become a JTBazar Driver", "جے ٹی بازار ڈرائیور بنیں"),
                      fontSize = 18.sp,
                      fontWeight = FontWeight.ExtraBold,
                      color = DriverInk
                    )
                    Text(
                      appText(
                        "Enter your details and upload required verification documents.",
                        "اپنی تفصیلات درج کریں اور تصدیقی دستاویزات اپ لوڈ کریں۔"
                      ),
                      fontSize = 12.sp,
                      color = DriverInkMuted
                    )
                  }

                  // Visual 3-Step Onboarding Strip
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    DriverOnboardingStepChip(
                      stepNum = 1,
                      label = appText("Personal", "ذاتی"),
                      done = step1Done,
                      modifier = Modifier.weight(1f)
                    )
                    DriverOnboardingStepChip(
                      stepNum = 2,
                      label = appText("Location", "مقام"),
                      done = step2Done,
                      modifier = Modifier.weight(1f)
                    )
                    DriverOnboardingStepChip(
                      stepNum = 3,
                      label = appText("Documents", "دستاویزات"),
                      done = step3Done,
                      modifier = Modifier.weight(1f)
                    )
                  }

                  HorizontalDivider(color = DriverBorder.copy(alpha = 0.6f))

                  // 1. Full Name
                  OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text(appText("Full Name *", "پورا نام *")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                  )

                  // 2. Phone
                  OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(appText("Mobile Phone Number *", "موبائل فون نمبر *")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                  )

                  // 3. Current Address + auto-detect GPS / map pin
                  OutlinedTextField(
                    value = currentAddress,
                    onValueChange = { currentAddress = it },
                    label = { Text(appText("Current Address *", "موجودہ پتہ *")) },
                    placeholder = {
                      Text(appText("Street, house/shop number, area, city", "گلی، مکان/دکان نمبر، علاقہ، شہر"))
                    },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    supportingText = {
                      Text(
                        if (applicationLat != null && applicationLng != null) {
                          val latFmt = String.format(Locale.US, "%.5f", applicationLat)
                          val lngFmt = String.format(Locale.US, "%.5f", applicationLng)
                          appText("Location pinned ($latFmt, $lngFmt)", "مقام پن ہو گیا ($latFmt, $lngFmt)")
                        } else {
                          appText(
                            "Tap 'Use Current Location' to auto-fill your street address, or pick on map.",
                            "پتہ خودکار پُر کرنے کے لیے 'موجودہ مقام استعمال کریں' پر ٹیپ کریں یا نقشے پر منتخب کریں۔"
                          )
                        }
                      )
                    }
                  )

                  addressLocationStatus?.let { statusMsg ->
                    Surface(
                      color = Color(0xFFF0FDF4),
                      shape = RoundedCornerShape(12.dp),
                      border = BorderStroke(1.dp, DriverGreenBorder),
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                      ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = DriverGreen, modifier = Modifier.size(16.dp))
                        Text(statusMsg, color = DriverGreenDark, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
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
                      .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                      containerColor = DriverOrangeSoft,
                      contentColor = DriverOrangeDark
                    ),
                    border = BorderStroke(1.dp, DriverOrangeBorder)
                  ) {
                    if (locatingAddress) {
                      CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = DriverOrangeDark)
                      Spacer(Modifier.width(8.dp))
                      Text(appText("Detecting current address…", "موجودہ پتہ معلوم کیا جا رہا ہے…"), fontWeight = FontWeight.SemiBold)
                    } else {
                      Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(Modifier.width(8.dp))
                      Text(appText("Use Current Location", "موجودہ مقام استعمال کریں"), fontWeight = FontWeight.Bold)
                    }
                  }

                  OutlinedButton(
                    onClick = { showApplicationLocationPicker = true },
                    enabled = !locatingAddress && !busy,
                    modifier = Modifier
                      .fillMaxWidth()
                      .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DriverOrange),
                    border = BorderStroke(1.dp, DriverOrangeBorder)
                  ) {
                    Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                      if (applicationLat != null && applicationLng != null) {
                        appText("Adjust Location on Map", "نقشے پر مقام درست کریں")
                      } else {
                        appText("Choose Location on Map", "نقشے پر مقام منتخب کریں")
                      },
                      fontWeight = FontWeight.SemiBold
                    )
                  }

                  // 4. Vehicle Type Dropdown
                  ExposedDropdownMenuBox(
                    expanded = isVehicleDropdownExpanded,
                    onExpandedChange = { isVehicleDropdownExpanded = it }
                  ) {
                    OutlinedTextField(
                      value = when (vehicleType) {
                        "Car" -> appText("Car", "کار")
                        else -> appText("Bike", "بائیک")
                      },
                      onValueChange = {},
                      readOnly = true,
                      label = { Text(appText("Vehicle Type *", "گاڑی کی قسم *")) },
                      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isVehicleDropdownExpanded) },
                      modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                      shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                      expanded = isVehicleDropdownExpanded,
                      onDismissRequest = { isVehicleDropdownExpanded = false }
                    ) {
                      DropdownMenuItem(
                        text = { Text(appText("Bike (Motorcycle)", "بائیک (موٹر سائیکل)")) },
                        onClick = {
                          vehicleType = "Bike"
                          isVehicleDropdownExpanded = false
                        }
                      )
                      DropdownMenuItem(
                        text = { Text(appText("Car (Automobile)", "کار (گاڑی)")) },
                        onClick = {
                          vehicleType = "Car"
                          isVehicleDropdownExpanded = false
                        }
                      )
                    }
                  }

                  HorizontalDivider(color = DriverBorder, modifier = Modifier.padding(vertical = 2.dp))

                  Text(
                    appText("Required Verification Documents", "مطلوبہ تصدیقی دستاویزات"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = DriverInk
                  )

                  // 5. CNIC Front
                  DocumentUploadCard(
                    title = appText("CNIC Front Side *", "شناختی کارڈ سامنے والا رخ *"),
                    subtitle = appText("Clear photo of front side", "سامنے والے حصے کی واضح تصویر"),
                    docState = cnicFrontDoc,
                    onChoosePhoto = {
                      activePickerTarget = DriverPhotoPickerTarget.ApplicationDoc(DriverDocType.CNIC_FRONT)
                      showPickerSheet = true
                    },
                    onClear = { cnicFrontDoc = DocUploadItem() }
                  )

                  // 6. CNIC Back
                  DocumentUploadCard(
                    title = appText("CNIC Back Side *", "شناختی کارڈ پچھلا رخ *"),
                    subtitle = appText("Clear photo of back side", "پچھلے حصے کی واضح تصویر"),
                    docState = cnicBackDoc,
                    onChoosePhoto = {
                      activePickerTarget = DriverPhotoPickerTarget.ApplicationDoc(DriverDocType.CNIC_BACK)
                      showPickerSheet = true
                    },
                    onClear = { cnicBackDoc = DocUploadItem() }
                  )

                  // 7. Vehicle Plate
                  DocumentUploadCard(
                    title = appText("Vehicle Number Plate *", "گاڑی کی نمبر پلیٹ *"),
                    subtitle = appText("Clear rear photo of bike/car", "بائیک یا کار کے پچھلے حصے کی واضح تصویر"),
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
                      text = appText(
                        "Fill name, phone, address, pin your map location, and upload all 3 verification documents to submit.",
                        "جمع کروانے کے لیے نام، فون، پتہ درج کریں، نقشے پر مقام پن کریں اور تینوں تصدیقی دستاویزات اپ لوڈ کریں۔"
                      ),
                      fontSize = 11.5.sp,
                      color = DriverInkMuted
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
                            message = AppLanguageManager.tr(
                              "Driver application submitted! Admin approval is required.",
                              "ڈرائیور کی درخواست جمع ہو گئی! ایڈمن کی منظوری درکار ہے۔"
                            )
                            load()
                          } else {
                            error = extractDriverFriendlyError(
                              result.exceptionOrNull(),
                              AppLanguageManager.tr(
                                "Could not submit your driver application. Please verify your details and try again.",
                                "آپ کی ڈرائیور درخواست جمع نہیں ہو سکی۔ براہ کرم تفصیلات چیک کریں اور دوبارہ کوشش کریں۔"
                              )
                            )
                          }
                        } catch (e: Exception) {
                          error = extractDriverFriendlyError(
                            e,
                            AppLanguageManager.tr(
                              "Could not submit your driver application. Please try again.",
                              "آپ کی ڈرائیور درخواست جمع نہیں ہو سکی۔ براہ کرم دوبارہ کوشش کریں۔"
                            )
                          )
                        } finally {
                          busy = false
                        }
                      }
                    },
                    modifier = Modifier
                      .fillMaxWidth()
                      .heightIn(min = 50.dp)
                      .testTag("submit_driver_application_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DriverOrange)
                  ) {
                    Text(
                      when {
                        busy -> appText("Submitting…", "جمع ہو رہا ہے…")
                        showResubmitForm -> appText("Resubmit Driver Application", "ڈرائیور درخواست دوبارہ جمع کروائیں")
                        else -> appText("Submit Driver Application", "ڈرائیور درخواست جمع کروائیں")
                      },
                      fontWeight = FontWeight.Bold
                    )
                  }
                }
              }
            }
          }
        }

        // 3. SECTION HEADER: "My Delivery Batches" / "Delivery History"
        if (isApprovedDriver) {
          item {
            DriverResponsiveSection {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    if (selectedTab == DriverBottomNavTab.HISTORY) {
                      appText("Delivery History", "ترسیل کی تاریخچہ")
                    } else {
                      appText("My Delivery Batches", "میرے ترسیلی بیچز")
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DriverInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    if (selectedTab == DriverBottomNavTab.HISTORY) {
                      appText("$closedHistoryCount completed / closed orders", "$closedHistoryCount مکمل / بند آرڈرز")
                    } else {
                      appText("$activeCount active delivery batches", "$activeCount فعال ترسیلی بیچز")
                    },
                    fontSize = 12.sp,
                    color = DriverInkMuted
                  )
                }

                Spacer(Modifier.width(8.dp))

                Surface(
                  color = DriverBlueSoft,
                  shape = RoundedCornerShape(20.dp),
                  border = BorderStroke(1.dp, DriverBlueBorder),
                  modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { scope.launch { load() } }
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                  ) {
                    Icon(
                      Icons.Filled.Refresh,
                      contentDescription = null,
                      tint = DriverBlue,
                      modifier = Modifier.size(16.dp)
                    )
                    Text(
                      appText("Refresh", "تازہ کریں"),
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      color = DriverBlue
                    )
                  }
                }
              }
            }
          }

          // 4. DELIVERY STATUS FILTER PILLS
          if (selectedTab == DriverBottomNavTab.DELIVERIES) {
            item {
              Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                LazyRow(
                  modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                  contentPadding = PaddingValues(horizontal = 16.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  val filtersWithCounts = listOf(
                    DriverDeliveryFilter.ALL to allCount,
                    DriverDeliveryFilter.ACTIVE to activeCount,
                    DriverDeliveryFilter.PICKED_UP to pickedUpCount,
                    DriverDeliveryFilter.DELIVERED to deliveredCount,
                    DriverDeliveryFilter.PENDING to deliveries.count { it.optString("order_status").equals("pending", true) },
                    DriverDeliveryFilter.CONFIRMED to deliveries.count { it.optString("order_status").equals("confirmed", true) },
                    DriverDeliveryFilter.PROCESSING to deliveries.count { it.optString("order_status").equals("processing", true) },
                    DriverDeliveryFilter.SHIPPED to deliveries.count { it.optString("order_status").equals("shipped", true) },
                    DriverDeliveryFilter.PARTIALLY_DELIVERED to deliveries.count { it.optString("order_status").equals("partially_delivered", true) },
                    DriverDeliveryFilter.CANCELLED to deliveries.count { it.optString("assignment_status") == "cancelled" }
                  )

                  items(filtersWithCounts) { (filter, count) ->
                    val isSelected = selectedFilter == filter && selectedTab != DriverBottomNavTab.HISTORY
                    Surface(
                      color = if (isSelected) DriverOrange else DriverSurface,
                      shape = RoundedCornerShape(20.dp),
                      border = BorderStroke(
                        1.dp,
                        if (isSelected) DriverOrange else DriverBorder
                      ),
                      modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                          selectedFilter = filter
                          selectedTab = DriverBottomNavTab.DELIVERIES
                        }
                    ) {
                      Text(
                        text = "${filter.localizedLabel()} ($count)",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else DriverInkSecondary
                      )
                    }
                  }
                }
              }
            }
          }

          // 5. EMPTY DELIVERIES PLACEHOLDER
          if (filteredDeliveries.isEmpty()) {
            item {
              DriverResponsiveSection {
                Card(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                  shape = RoundedCornerShape(22.dp),
                  colors = CardDefaults.cardColors(containerColor = DriverSurface),
                  border = BorderStroke(1.dp, DriverBorder)
                ) {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    Surface(
                      color = DriverOrangeSoft,
                      shape = CircleShape,
                      modifier = Modifier.size(60.dp)
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = DriverOrange, modifier = Modifier.size(30.dp))
                      }
                    }
                    Text(
                      appText("No Deliveries In This Section", "اس سیکشن میں کوئی ترسیل نہیں"),
                      fontSize = 15.sp,
                      fontWeight = FontWeight.Bold,
                      color = DriverInk,
                      textAlign = TextAlign.Center
                    )
                    Text(
                      appText(
                        "Check other status tabs or refresh for newly assigned orders.",
                        "دیگر اسٹیٹس ٹیبز دیکھیں یا نئے تفویض کردہ آرڈرز کے لیے تازہ کریں۔"
                      ),
                      fontSize = 12.sp,
                      color = DriverInkMuted,
                      textAlign = TextAlign.Center
                    )
                  }
                }
              }
            }
          }

          // 6. DELIVERY CARDS (RESPONSIVE + EXPRESS COURIER DESIGN + MANUAL ORDER SUPPORT)
          items(filteredDeliveries, key = { it.optCleanString("assignment_id") }) { delivery ->
            val assignmentId = delivery.optCleanString("assignment_id")
            val assignmentStatus = delivery.optCleanString("assignment_status").ifBlank { "assigned" }
            val orderNumber = delivery.optCleanString("order_number").ifBlank {
              appText("Order number unavailable", "آرڈر نمبر دستیاب نہیں")
            }
            val customerName = delivery.optCleanString("customer_name").ifBlank {
              appText("Customer", "کسٹمر")
            }
            val customerPhone = delivery.optCleanString("customer_phone")
            val addressText = driverAddressText(delivery)
            val isDelivered = assignmentStatus == "delivered" || assignmentStatus == "cancelled" || assignmentStatus == "failed"
            val otpVerified = isDeliveryOtpVerified(delivery)
            val proofSaved = hasDeliveryProofSaved(delivery)
            val isUploadingThisProof = uploadingProofAssignmentId == assignmentId
            val batchNumber = delivery.optCleanString("delivery_batch_number")
            val failedReasonText = delivery.optCleanString("failed_reason").replace('_', ' ')
            val orderStatusText = delivery.optCleanString("order_status")
            val isManualOrder = isManualOrderDelivery(delivery)
            val paymentMethod = delivery.optCleanString("payment_method")
            val orderTotalRaw = delivery.optCleanString("order_total")

            // Format items summary
            val itemsArr = delivery.optJSONArray("items")
            val activeItems = if (itemsArr == null) emptyList() else (0 until itemsArr.length()).mapNotNull { itemsArr.optJSONObject(it) }.filter { itm ->
              val vendorStatus = itm.optCleanString("vendor_fulfillment_status").lowercase()
              val deliveryStatus = itm.optCleanString("delivery_status").lowercase()
              val itemStatus = itm.optCleanString("status").lowercase()
              vendorStatus != "cancelled" && deliveryStatus != "cancelled" && itemStatus != "cancelled"
            }
            val itemsText = if (activeItems.isNotEmpty()) {
              activeItems.joinToString(", ") { itm ->
                "${itm.optCleanString("name").ifBlank { AppLanguageManager.tr("Product", "پروڈکٹ") }} × ${itm.optInt("quantity", 1)}"
              }
            } else {
              appText("No active items in this delivery", "اس ترسیل میں کوئی فعال شے نہیں")
            }

            val statusAccent = when (assignmentStatus) {
              "delivered" -> DriverGreen
              "cancelled", "failed" -> DriverRed
              "picked_up", "in_transit" -> DriverBlue
              else -> DriverOrange
            }
            val statusTintBg = when (assignmentStatus) {
              "delivered" -> Color(0xFFF0FDF4)
              "cancelled", "failed" -> Color(0xFFFEF2F2)
              "picked_up", "in_transit" -> Color(0xFFEFF6FF)
              else -> Color(0xFFFFF7ED)
            }
            val statusBadgeBg = when (assignmentStatus) {
              "delivered" -> DriverGreenSoft
              "cancelled", "failed" -> DriverRedSoft
              "picked_up", "in_transit" -> DriverBlueSoft
              else -> DriverOrangeSoft
            }
            val statusLabel = when {
              assignmentStatus == "delivered" -> appText("Delivered", "ترسیل مکمل")
              assignmentStatus == "cancelled" -> appText("Cancelled", "منسوخ")
              assignmentStatus == "failed" -> appText("Delivery Failed", "ترسیل ناکام") +
                if (failedReasonText.isNotBlank()) " · $failedReasonText" else ""
              assignmentStatus == "picked_up" || assignmentStatus == "in_transit" -> appText("In Transit", "راستے میں")
              orderStatusText.equals("shipped", true) -> appText("Shipped", "روانہ")
              orderStatusText.equals("processing", true) -> appText("Processing", "تیاری میں")
              orderStatusText.equals("confirmed", true) -> appText("Confirmed", "تصدیق شدہ")
              orderStatusText.equals("pending", true) -> appText("Pending", "زیرِ التواء")
              else -> appText("Active", "فعال")
            }

            DriverResponsiveSection {
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .shadow(elevation = 3.dp, shape = RoundedCornerShape(22.dp))
                  .testTag("driver_delivery_card_$assignmentId"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = DriverSurface),
                border = BorderStroke(1.dp, DriverBorder)
              ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                  // Tinted Top Strip with FlowRow for clean wrapping on all screen widths
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .background(statusTintBg)
                      .padding(horizontal = 16.dp, vertical = 12.dp)
                  ) {
                    FlowRow(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      // Status pill
                      Surface(
                        color = statusBadgeBg,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, statusAccent.copy(alpha = 0.35f))
                      ) {
                        Row(
                          modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                          Box(
                            modifier = Modifier
                              .size(7.dp)
                              .clip(CircleShape)
                              .background(statusAccent)
                          )
                          Text(
                            text = statusLabel,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusAccent
                          )
                        }
                      }

                      // Order # pill
                      Surface(
                        color = DriverSurface,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, DriverBlueBorder)
                      ) {
                        Row(
                          modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                          Icon(
                            Icons.Filled.Inventory2,
                            contentDescription = null,
                            tint = DriverBlueDark,
                            modifier = Modifier.size(13.dp)
                          )
                          Text(
                            appText("Order #$orderNumber", "آرڈر #$orderNumber"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = DriverBlueDark
                          )
                        }
                      }

                      // Manual Order badge
                      if (isManualOrder) {
                        Surface(
                          color = DriverAmberSoft,
                          shape = RoundedCornerShape(20.dp),
                          border = BorderStroke(1.dp, DriverAmberBorder)
                        ) {
                          Text(
                            appText("Manual Order", "دستی آرڈر"),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DriverAmber
                          )
                        }
                      }

                      // COD / Payment badge
                      if (paymentMethod.equals("cod", ignoreCase = true)) {
                        val codDisplay = if (orderTotalRaw.isNotBlank()) "COD · PKR $orderTotalRaw" else "COD"
                        Surface(
                          color = DriverOrangeSoft,
                          shape = RoundedCornerShape(20.dp),
                          border = BorderStroke(1.dp, DriverOrangeBorder)
                        ) {
                          Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                          ) {
                            Icon(Icons.Filled.Payments, contentDescription = null, tint = DriverOrangeDark, modifier = Modifier.size(12.dp))
                            Text(codDisplay, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DriverOrangeDark)
                          }
                        }
                      }

                      // Combined Route batch badge
                      if (batchNumber.isNotBlank()) {
                        Surface(
                          color = DriverVioletSoft,
                          shape = RoundedCornerShape(20.dp),
                          border = BorderStroke(1.dp, DriverViolet.copy(alpha = 0.28f))
                        ) {
                          Text(
                            appText("Batch · $batchNumber", "بیچ · $batchNumber"),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DriverViolet
                          )
                        }
                      }
                    }
                  }

                  HorizontalDivider(color = DriverBorder.copy(alpha = 0.65f))

                  Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                  ) {
                    // Responsive Customer Details + Mini-Map Block
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                      val stackCustomerAndMap = maxWidth < 340.dp || (isDelivered || !isApprovedDriver)

                      @Composable
                      fun CustomerDetailsBlock(mod: Modifier) {
                        Column(
                          modifier = mod,
                          verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                          Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                          ) {
                            Surface(
                              color = DriverSurfaceMuted,
                              shape = CircleShape,
                              border = BorderStroke(1.dp, DriverBorder),
                              modifier = Modifier.size(30.dp)
                            ) {
                              Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = DriverInk, modifier = Modifier.size(16.dp))
                              }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                              Text(appText("Customer", "کسٹمر"), fontSize = 10.sp, color = DriverInkMuted)
                              Text(
                                customerName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = DriverInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                              )
                            }
                          }

                          if (customerPhone.isNotBlank()) {
                            Surface(
                              color = DriverBlueSoft,
                              shape = RoundedCornerShape(10.dp),
                              border = BorderStroke(1.dp, DriverBlueBorder),
                              modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                  val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$customerPhone"))
                                  context.startActivity(dial)
                                }
                            ) {
                              Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                              ) {
                                Icon(
                                  Icons.Filled.Phone,
                                  contentDescription = appText("Call Customer", "کسٹمر کو کال کریں"),
                                  tint = DriverBlue,
                                  modifier = Modifier.size(14.dp)
                                )
                                Text(
                                  customerPhone,
                                  fontSize = 12.sp,
                                  color = DriverBlueDark,
                                  fontWeight = FontWeight.SemiBold,
                                  maxLines = 1,
                                  overflow = TextOverflow.Ellipsis
                                )
                              }
                            }
                          }

                          if (!isDelivered) {
                            Row(
                              verticalAlignment = Alignment.Top,
                              horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                              Icon(
                                Icons.Filled.Place,
                                contentDescription = null,
                                tint = DriverOrange,
                                modifier = Modifier.size(16.dp).padding(top = 1.dp)
                              )
                              Text(
                                addressText,
                                fontSize = 12.sp,
                                color = DriverInkSecondary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                              )
                            }
                          }
                        }
                      }

                      @Composable
                      fun MiniMapPreview(mod: Modifier) {
                        Box(
                          modifier = mod
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, DriverBorder, RoundedCornerShape(14.dp))
                            .background(Color(0xFFE2E8F0))
                            .clickable { onViewCustomerLocation(delivery) }
                        ) {
                          Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(Color(0xFFE8EEF5))
                            drawLine(Color.White, Offset(0f, size.height * 0.42f), Offset(size.width, size.height * 0.34f), strokeWidth = 14f)
                            drawLine(Color.White, Offset(size.width * 0.45f, 0f), Offset(size.width * 0.56f, size.height), strokeWidth = 12f)
                            drawLine(Color(0xFFBFDBFE), Offset(0f, size.height * 0.42f), Offset(size.width, size.height * 0.34f), strokeWidth = 3f)
                            drawRoundRect(
                              Color(0xFFDCFCE7),
                              Offset(size.width * 0.06f, size.height * 0.06f),
                              Size(size.width * 0.34f, size.height * 0.28f),
                              cornerRadius = CornerRadius(6f, 6f)
                            )
                          }
                          Icon(
                            Icons.Filled.Place,
                            contentDescription = null,
                            tint = DriverRed,
                            modifier = Modifier
                              .align(Alignment.Center)
                              .padding(bottom = 12.dp)
                              .size(26.dp)
                          )
                          Surface(
                            color = DriverInk.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                              .align(Alignment.BottomCenter)
                              .padding(horizontal = 6.dp, vertical = 6.dp)
                          ) {
                            Row(
                              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                              verticalAlignment = Alignment.CenterVertically,
                              horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                              Icon(Icons.Filled.Navigation, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                              Text(
                                appText("Map", "نقشہ"),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                              )
                            }
                          }
                        }
                      }

                      if (stackCustomerAndMap) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                          CustomerDetailsBlock(Modifier.fillMaxWidth())
                          if (!isDelivered && isApprovedDriver) {
                            MiniMapPreview(
                              Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                            )
                          }
                        }
                      } else {
                        Row(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalArrangement = Arrangement.spacedBy(12.dp),
                          verticalAlignment = Alignment.CenterVertically
                        ) {
                          CustomerDetailsBlock(Modifier.weight(1f))
                          MiniMapPreview(Modifier.size(width = 108.dp, height = 92.dp))
                        }
                      }
                    }

                    // Items Summary Container
                    Surface(
                      color = DriverSurfaceMuted,
                      shape = RoundedCornerShape(12.dp),
                      border = BorderStroke(1.dp, DriverBorder),
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                      ) {
                        Icon(
                          Icons.Filled.Widgets,
                          contentDescription = null,
                          tint = DriverOrange,
                          modifier = Modifier.size(18.dp).padding(top = 1.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                          Text(appText("Items", "اشیاء"), fontSize = 10.5.sp, color = DriverInkMuted, fontWeight = FontWeight.Medium)
                          Text(
                            itemsText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DriverInk,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                          )
                        }
                      }
                    }

                    // Responsive Action Buttons Row / Stack
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                      val stackButtons = maxWidth < 340.dp

                      @Composable
                      fun LocationButton(mod: Modifier) {
                        Button(
                          onClick = { onViewCustomerLocation(delivery) },
                          modifier = mod.heightIn(min = 48.dp),
                          shape = RoundedCornerShape(12.dp),
                          colors = ButtonDefaults.buttonColors(
                            containerColor = DriverOrangeSoft,
                            contentColor = DriverOrangeDark
                          ),
                          border = BorderStroke(1.dp, DriverOrangeBorder),
                          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                          Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                          Spacer(Modifier.width(6.dp))
                          Text(
                            appText("View Location", "مقام دیکھیں"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                          )
                        }
                      }

                      @Composable
                      fun SecondaryStatusAction(mod: Modifier) {
                        if (assignmentStatus == "assigned") {
                          Button(
                            enabled = !busy && !actionBusy,
                            onClick = {
                              if (busy || actionBusy) return@Button
                              scope.launch {
                                busy = true
                                error = null
                                val pickupRpc = driverDeliveryRpcName(
                                  delivery,
                                  "driver_update_delivery_status",
                                  "driver_update_manual_order_delivery_status"
                                )
                                val r = SupabaseClient.rpc(
                                  pickupRpc,
                                  JSONObject().put("p_assignment_id", assignmentId).put("p_action", "picked_up").toString(),
                                  requireAuth = true
                                )
                                if (r.isSuccess) {
                                  message = AppLanguageManager.tr(
                                    "Parcel marked as picked up.",
                                    "پارسل پک اپ کے طور پر نشان زد ہو گیا۔"
                                  )
                                  load(showLoader = false)
                                } else {
                                  error = extractDriverFriendlyError(
                                    r.exceptionOrNull(),
                                    AppLanguageManager.tr(
                                      "Could not update pickup status. Please try again.",
                                      "پک اپ اسٹیٹس اپ ڈیٹ نہیں ہو سکا۔ براہ کرم دوبارہ کوشش کریں۔"
                                    )
                                  )
                                }
                                busy = false
                              }
                            },
                            modifier = mod
                              .heightIn(min = 48.dp)
                              .testTag("mark_picked_up_button_$assignmentId"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                              containerColor = DriverGreen,
                              contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                          ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                              appText("Picked Up", "پارسل اٹھا لیا"),
                              fontSize = 12.sp,
                              fontWeight = FontWeight.Bold,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis
                            )
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
                            modifier = mod
                              .heightIn(min = 48.dp)
                              .testTag("failed_delivery_button_$assignmentId"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, DriverRedBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DriverRed),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                          ) {
                            Icon(Icons.Filled.ReportProblem, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                              appText("Failed Delivery", "ترسیل ناکام"),
                              fontSize = 12.sp,
                              fontWeight = FontWeight.SemiBold,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis
                            )
                          }
                        } else {
                          Surface(
                            color = if (assignmentStatus == "cancelled" || assignmentStatus == "failed") DriverRedSoft else DriverGreenSoft,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                              1.dp,
                              if (assignmentStatus == "cancelled" || assignmentStatus == "failed") DriverRedBorder else DriverGreenBorder
                            ),
                            modifier = mod.heightIn(min = 48.dp)
                          ) {
                            Row(
                              modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                              verticalAlignment = Alignment.CenterVertically,
                              horizontalArrangement = Arrangement.Center
                            ) {
                              Icon(
                                if (assignmentStatus == "cancelled" || assignmentStatus == "failed") Icons.Filled.Cancel else Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = if (assignmentStatus == "cancelled" || assignmentStatus == "failed") DriverRed else DriverGreen,
                                modifier = Modifier.size(16.dp)
                              )
                              Spacer(Modifier.width(6.dp))
                              Text(
                                when (assignmentStatus) {
                                  "cancelled" -> appText("Cancelled", "منسوخ")
                                  "failed" -> appText("Delivery Failed", "ترسیل ناکام")
                                  else -> appText("Delivered", "ترسیل مکمل")
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (assignmentStatus == "delivered") DriverGreenDark else DriverRedDark
                              )
                            }
                          }
                        }
                      }

                      if (stackButtons) {
                        Column(
                          modifier = Modifier.fillMaxWidth(),
                          verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                          if (!isDelivered && isApprovedDriver) {
                            LocationButton(Modifier.fillMaxWidth())
                          }
                          SecondaryStatusAction(Modifier.fillMaxWidth())
                        }
                      } else {
                        Row(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalArrangement = Arrangement.spacedBy(10.dp),
                          verticalAlignment = Alignment.CenterVertically
                        ) {
                          if (!isDelivered && isApprovedDriver) {
                            LocationButton(Modifier.weight(1f))
                          }
                          SecondaryStatusAction(Modifier.weight(1f))
                        }
                      }
                    }

                    // 2-Step Automatic Delivery Completion Panel (when picked_up)
                    if (assignmentStatus == "picked_up") {
                      val completedSteps = (if (otpVerified) 1 else 0) + (if (proofSaved) 1 else 0)
                      Surface(
                        color = DriverSurfaceMuted,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, DriverBorder),
                        modifier = Modifier.fillMaxWidth()
                      ) {
                        Column(
                          modifier = Modifier.padding(14.dp),
                          verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                          Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                          ) {
                            Text(
                              appText("Delivery Completion Steps", "ترسیل مکمل کرنے کے مراحل"),
                              fontSize = 13.sp,
                              fontWeight = FontWeight.Bold,
                              color = DriverInk,
                              modifier = Modifier.weight(1f)
                            )
                            Surface(
                              color = if (completedSteps == 2) DriverGreenSoft else DriverOrangeSoft,
                              shape = RoundedCornerShape(12.dp)
                            ) {
                              Text(
                                text = "$completedSteps/2",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (completedSteps == 2) DriverGreenDark else DriverOrangeDark
                              )
                            }
                          }

                          LinearProgressIndicator(
                            progress = { completedSteps / 2f },
                            modifier = Modifier
                              .fillMaxWidth()
                              .height(5.dp)
                              .clip(RoundedCornerShape(4.dp)),
                            color = if (completedSteps == 2) DriverGreen else DriverOrange,
                            trackColor = DriverBorder
                          )

                          // Step A: Verify Customer OTP
                          Surface(
                            color = DriverSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (otpVerified) DriverGreenBorder else DriverBorder),
                            modifier = Modifier.fillMaxWidth()
                          ) {
                            BoxWithConstraints(
                              modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                            ) {
                              val stackOtp = maxWidth < 310.dp

                              @Composable
                              fun OtpInfoRow(mod: Modifier) {
                                Row(
                                  modifier = mod,
                                  verticalAlignment = Alignment.CenterVertically,
                                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                  Icon(
                                    if (otpVerified) Icons.Filled.CheckCircle else Icons.Filled.Password,
                                    contentDescription = null,
                                    tint = if (otpVerified) DriverGreen else DriverOrange,
                                    modifier = Modifier.size(19.dp)
                                  )
                                  Column {
                                    Text(
                                      appText("1. Customer OTP", "1. کسٹمر او ٹی پی"),
                                      fontSize = 12.5.sp,
                                      fontWeight = FontWeight.Bold,
                                      color = DriverInk
                                    )
                                    Text(
                                      if (otpVerified) appText("OTP Verified", "او ٹی پی تصدیق شدہ")
                                      else appText("Pending customer 6-digit code", "کسٹمر کا 6 ہندسوں کا کوڈ درکار ہے"),
                                      fontSize = 11.sp,
                                      color = if (otpVerified) DriverGreen else DriverInkMuted
                                    )
                                  }
                                }
                              }

                              @Composable
                              fun OtpActionView(mod: Modifier) {
                                if (otpVerified) {
                                  Surface(
                                    color = DriverGreenSoft,
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, DriverGreenBorder),
                                    modifier = mod.testTag("otp_verified_badge_$assignmentId")
                                  ) {
                                    Row(
                                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                      verticalAlignment = Alignment.CenterVertically,
                                      horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                      Icon(Icons.Filled.Check, contentDescription = null, tint = DriverGreen, modifier = Modifier.size(14.dp))
                                      Text(
                                        appText("OTP Verified", "او ٹی پی تصدیق شدہ"),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DriverGreenDark
                                      )
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
                                    modifier = mod
                                      .heightIn(min = 48.dp)
                                      .testTag("verify_otp_button_$assignmentId"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, DriverOrangeBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DriverOrangeDark)
                                  ) {
                                    Text(
                                      appText("Verify OTP", "او ٹی پی تصدیق"),
                                      fontSize = 11.5.sp,
                                      fontWeight = FontWeight.Bold
                                    )
                                  }
                                }
                              }

                              if (stackOtp) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                  OtpInfoRow(Modifier.fillMaxWidth())
                                  OtpActionView(Modifier.fillMaxWidth())
                                }
                              } else {
                                Row(
                                  modifier = Modifier.fillMaxWidth(),
                                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                                  verticalAlignment = Alignment.CenterVertically
                                ) {
                                  OtpInfoRow(Modifier.weight(1f))
                                  OtpActionView(Modifier)
                                }
                              }
                            }
                          }

                          // Step B: Upload Delivery Photo Proof (private bucket)
                          Surface(
                            color = DriverSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (proofSaved) DriverGreenBorder else DriverBorder),
                            modifier = Modifier.fillMaxWidth()
                          ) {
                            Column(
                              modifier = Modifier.padding(12.dp),
                              verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
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
                                    tint = if (proofSaved) DriverGreen else DriverOrange,
                                    modifier = Modifier.size(19.dp)
                                  )
                                  Column {
                                    Text(
                                      appText("2. Delivery Photo Proof", "2. ترسیل کی تصویری تصدیق"),
                                      fontSize = 12.5.sp,
                                      fontWeight = FontWeight.Bold,
                                      color = DriverInk
                                    )
                                    Text(
                                      if (proofSaved) appText("Photo Proof Uploaded (Private)", "تصویر محفوظ طریقے سے اپ لوڈ ہو گئی")
                                      else appText("Required before completing delivery", "ترسیل مکمل کرنے کے لیے لازمی ہے"),
                                      fontSize = 11.sp,
                                      color = if (proofSaved) DriverGreen else DriverInkMuted
                                    )
                                  }
                                }

                                if (proofSaved) {
                                  Spacer(Modifier.width(8.dp))
                                  Surface(
                                    color = DriverGreenSoft,
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, DriverGreenBorder),
                                    modifier = Modifier.testTag("proof_uploaded_badge_$assignmentId")
                                  ) {
                                    Row(
                                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                      verticalAlignment = Alignment.CenterVertically,
                                      horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                      Icon(Icons.Filled.Check, contentDescription = null, tint = DriverGreen, modifier = Modifier.size(13.dp))
                                      Text(
                                        appText("Uploaded", "اپ لوڈ شدہ"),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DriverGreenDark
                                      )
                                    }
                                  }
                                }
                              }

                              proofPreviews[assignmentId]?.let { previewBmp ->
                                Image(
                                  bitmap = previewBmp.asImageBitmap(),
                                  contentDescription = appText("Uploaded delivery proof preview", "اپ لوڈ کردہ ترسیل کی تصویر"),
                                  contentScale = ContentScale.Crop,
                                  modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
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
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (proofSaved) DriverGreenBorder else DriverOrangeBorder),
                                colors = ButtonDefaults.outlinedButtonColors(
                                  contentColor = if (proofSaved) DriverGreenDark else DriverOrangeDark
                                )
                              ) {
                                if (isUploadingThisProof) {
                                  CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                  Spacer(Modifier.width(8.dp))
                                  Text(appText("Uploading Photo…", "تصویر اپ لوڈ ہو رہی ہے…"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                } else {
                                  Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                  Spacer(Modifier.width(6.dp))
                                  Text(
                                    when {
                                      failedProofUploads.containsKey(assignmentId) -> appText("Retry Photo Upload", "تصویر دوبارہ اپ لوڈ کریں")
                                      proofSaved -> appText("Replace Proof Photo", "تصویر تبدیل کریں")
                                      else -> appText("Upload Proof Photo", "ترسیل کی تصویر اپ لوڈ کریں")
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                  )
                                }
                              }
                            }
                          }

                          // Automatic completion status banner
                          Surface(
                            color = if (otpVerified && proofSaved) DriverGreenSoft else DriverSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (otpVerified && proofSaved) DriverGreenBorder else DriverBorder),
                            modifier = Modifier.fillMaxWidth()
                          ) {
                            Text(
                              text = if (otpVerified && proofSaved) {
                                appText(
                                  "OTP and photo verified. Delivery is completed automatically.",
                                  "او ٹی پی اور تصویر دونوں کی تصدیق ہو گئی۔ ترسیل خودکار طور پر مکمل ہو چکی ہے۔"
                                )
                              } else {
                                appText(
                                  "Delivery will complete automatically when both OTP verification and photo proof are saved.",
                                  "او ٹی پی کی تصدیق اور ترسیل کی تصویر محفوظ ہونے پر ترسیل خودکار طور پر مکمل ہو جائے گی۔"
                                )
                              },
                              fontSize = 11.5.sp,
                              color = if (otpVerified && proofSaved) DriverGreenDark else DriverInkMuted,
                              fontWeight = FontWeight.Medium,
                              modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("automatic_completion_status_$assignmentId")
                            )
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
                          .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DriverBorder)
                      ) {
                        Text(appText("Report Delivery Issue", "ترسیل کا مسئلہ رپورٹ کریں"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                          .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DriverOrange)
                      ) {
                        Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(appText("Record COD Cash Collected", "وصول شدہ کیش آن ڈیلیوری رقم درج کریں"), fontWeight = FontWeight.Bold)
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

  // Step A: Verify customer OTP ONLY (never calls driver_update_delivery_status here)
  if (showOtpDialog) {
    AlertDialog(
      onDismissRequest = { if (!actionBusy) showOtpDialog = false },
      properties = DialogProperties(usePlatformDefaultWidth = false),
      modifier = Modifier
        .padding(horizontal = 24.dp)
        .widthIn(max = 560.dp),
      title = {
        Text(
          appText("Verify Customer Delivery OTP", "کسٹمر ڈیلیوری او ٹی پی کی تصدیق کریں"),
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          val otpDelivery = deliveries.firstOrNull { it.optCleanString("assignment_id") == actionAssignmentId } ?: JSONObject()
          Text(
            if (isManualOrderDelivery(otpDelivery)) {
              appText(
                "Ask the customer for the 6-digit delivery code shared by JTBazar admin. After 5 incorrect attempts, ask admin to generate a new code.",
                "کسٹمر سے جے ٹی بازار ایڈمن کا فراہم کردہ 6 ہندسوں کا ڈیلیوری کوڈ پوچھیں۔ 5 غلط کوششوں کے بعد ایڈمن سے نیا کوڈ حاصل کریں۔"
              )
            } else {
              appText(
                "Ask the customer to open JTBazar order tracking and share their 6-digit delivery code. After 5 incorrect attempts the code locks and a new code must be generated.",
                "کسٹمر سے جے ٹی بازار آرڈر ٹریکنگ کھول کر اپنا 6 ہندسوں کا ڈیلیوری کوڈ بتانے کو کہیں۔ 5 غلط کوششوں کے بعد کوڈ لاک ہو جائے گا اور نیا کوڈ بنانا ہوگا۔"
              )
            },
            fontSize = 12.sp,
            color = DriverInkSecondary
          )
          OutlinedTextField(
            value = otpCode,
            onValueChange = {
              otpCode = it.filter(Char::isDigit).take(6)
              otpDialogError = null
            },
            label = { Text(appText("6-digit OTP", "6 ہندسوں کا او ٹی پی")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("driver_otp_input")
          )
          otpDialogError?.let { dialogErr ->
            Surface(
              color = MaterialTheme.colorScheme.errorContainer,
              shape = RoundedCornerShape(10.dp),
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
                val otpDelivery = deliveries.firstOrNull { it.optCleanString("assignment_id") == actionAssignmentId } ?: JSONObject()
                val otpRpc = driverDeliveryRpcName(
                  otpDelivery,
                  "driver_verify_delivery_otp",
                  "driver_verify_manual_order_delivery_otp"
                )
                val rawVerified = SupabaseClient.rpc(
                  otpRpc,
                  JSONObject()
                    .put("p_assignment_id", actionAssignmentId)
                    .put("p_code", otpCode)
                    .toString(),
                  requireAuth = true
                ).getOrThrow()
                val verified = rawVerified.trim().equals("true", ignoreCase = true)
                if (!verified) {
                  otpDialogError = AppLanguageManager.tr(
                    "Incorrect or expired OTP. Please check the 6-digit code or ask the customer to generate a new one.",
                    "غلط یا میعاد ختم او ٹی پی۔ براہ کرم 6 ہندسوں کا کوڈ چیک کریں یا کسٹمر سے نیا کوڈ حاصل کریں۔"
                  )
                } else {
                  deliveries = markAssignmentOtpVerifiedInList(deliveries, actionAssignmentId)
                  showOtpDialog = false
                  otpCode = ""
                  otpDialogError = null
                  message = AppLanguageManager.tr(
                    "Customer OTP verified. Delivery will complete automatically once the photo proof is saved.",
                    "کسٹمر او ٹی پی کی تصدیق ہو گئی۔ ترسیل کی تصویر محفوظ ہوتے ہی ترسیل خودکار طور پر مکمل ہو جائے گی۔"
                  )
                  load(showLoader = false)
                }
              } catch (e: Exception) {
                otpDialogError = extractDriverFriendlyError(
                  e,
                  AppLanguageManager.tr(
                    "Could not verify delivery OTP. Please try again.",
                    "ڈیلیوری او ٹی پی کی تصدیق نہیں ہو سکی۔ براہ کرم دوبارہ کوشش کریں۔"
                  )
                )
              } finally {
                actionBusy = false
              }
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = DriverOrange),
          modifier = Modifier.testTag("confirm_verify_otp_button")
        ) {
          Text(
            if (actionBusy) appText("Verifying…", "تصدیق ہو رہی ہے…")
            else appText("Verify OTP", "او ٹی پی تصدیق کریں")
          )
        }
      },
      dismissButton = {
        TextButton(
          enabled = !actionBusy,
          onClick = {
            showOtpDialog = false
            otpDialogError = null
          }
        ) { Text(appText("Cancel", "منسوخ کریں")) }
      }
    )
  }

  if (showFailedDialog) {
    AlertDialog(
      onDismissRequest = { if (!actionBusy) showFailedDialog = false },
      properties = DialogProperties(usePlatformDefaultWidth = false),
      modifier = Modifier
        .padding(horizontal = 24.dp)
        .widthIn(max = 560.dp),
      title = { Text(appText("Why was delivery unsuccessful?", "ترسیل مکمل کیوں نہ ہو سکی؟"), fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(
            "customer_unavailable" to appText("Customer unavailable", "کسٹمر دستیاب نہیں"),
            "address_issue" to appText("Address issue", "پتے کا مسئلہ"),
            "customer_refused" to appText("Customer refused", "کسٹمر نے انکار کر دیا"),
            "delivery_failed" to appText("Other delivery failure", "ترسیل میں کوئی اور مسئلہ")
          ).forEach { (value, label) ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { failureReason = value }
                .padding(vertical = 2.dp)
            ) {
              RadioButton(selected = failureReason == value, onClick = { failureReason = value })
              Text(label, fontSize = 13.sp)
            }
          }
          OutlinedTextField(
            value = failureNote,
            onValueChange = { failureNote = it.take(1000) },
            label = { Text(appText("Extra note (optional)", "اضافی نوٹ (اختیاری)")) },
            minLines = 2,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
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
                val failedDelivery = deliveries.firstOrNull { it.optCleanString("assignment_id") == actionAssignmentId } ?: JSONObject()
                val failedRpc = driverDeliveryRpcName(
                  failedDelivery,
                  "driver_mark_delivery_failed",
                  "driver_mark_manual_order_delivery_failed"
                )
                SupabaseClient.rpc(
                  failedRpc,
                  JSONObject()
                    .put("p_assignment_id", actionAssignmentId)
                    .put("p_reason", failureReason)
                    .put("p_note", failureNote)
                    .toString(),
                  requireAuth = true
                ).getOrThrow()
                showFailedDialog = false
                message = AppLanguageManager.tr(
                  "Delivery issue reported to JTBazar.",
                  "ترسیل کا مسئلہ جے ٹی بازار کو رپورٹ کر دیا گیا ہے۔"
                )
                load(showLoader = false)
              } catch (e: Exception) {
                error = extractDriverFriendlyError(
                  e,
                  AppLanguageManager.tr("Could not report delivery issue.", "ترسیل کا مسئلہ رپورٹ نہیں ہو سکا۔")
                )
              } finally {
                actionBusy = false
              }
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = DriverRed)
        ) {
          Text(
            if (actionBusy) appText("Submitting…", "جمع ہو رہا ہے…")
            else appText("Submit reason", "وجہ جمع کروائیں")
          )
        }
      },
      dismissButton = {
        TextButton(enabled = !actionBusy, onClick = { showFailedDialog = false }) {
          Text(appText("Cancel", "منسوخ کریں"))
        }
      }
    )
  }

  if (showCodDialog) {
    AlertDialog(
      onDismissRequest = { if (!actionBusy) showCodDialog = false },
      properties = DialogProperties(usePlatformDefaultWidth = false),
      modifier = Modifier
        .padding(horizontal = 24.dp)
        .widthIn(max = 560.dp),
      title = { Text(appText("Record COD cash", "کیش آن ڈیلیوری رقم درج کریں"), fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            appText(
              "Enter the amount physically received from the customer.",
              "کسٹمر سے نقد وصول کی گئی رقم درج کریں۔"
            ),
            fontSize = 12.sp,
            color = DriverInkSecondary
          )
          OutlinedTextField(
            value = codAmount,
            onValueChange = { codAmount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) },
            label = { Text(appText("Collected amount (PKR)", "وصول شدہ رقم (PKR)")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
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
                val codDelivery = deliveries.firstOrNull { it.optCleanString("assignment_id") == actionAssignmentId } ?: JSONObject()
                val codRpc = driverDeliveryRpcName(
                  codDelivery,
                  "driver_record_cod_collection",
                  "driver_record_manual_order_cod_collection"
                )
                SupabaseClient.rpc(
                  codRpc,
                  JSONObject()
                    .put("p_assignment_id", actionAssignmentId)
                    .put("p_amount", parsedAmount)
                    .toString(),
                  requireAuth = true
                ).getOrThrow()
                showCodDialog = false
                message = AppLanguageManager.tr("COD collection recorded.", "کیش آن ڈیلیوری وصولی درج ہو گئی۔")
                load(showLoader = false)
              } catch (e: Exception) {
                error = extractDriverFriendlyError(
                  e,
                  AppLanguageManager.tr("Could not record COD collection.", "کیش وصولی درج نہیں ہو سکی۔")
                )
              } finally {
                actionBusy = false
              }
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = DriverOrange)
        ) {
          Text(
            if (actionBusy) appText("Saving…", "محفوظ ہو رہا ہے…")
            else appText("Save collection", "رقم محفوظ کریں")
          )
        }
      },
      dismissButton = {
        TextButton(enabled = !actionBusy, onClick = { showCodDialog = false }) {
          Text(appText("Cancel", "منسوخ کریں"))
        }
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
        addressLocationStatus = if (address.isNotBlank()) {
          currentAddress = address
          AppLanguageManager.tr(
            "Map location selected and street address filled.",
            "نقشے پر مقام منتخب ہو گیا اور گلی کا پتہ پُر کر دیا گیا۔"
          )
        } else {
          AppLanguageManager.tr(
            "Map location pinned. Verify your street address above.",
            "نقشے پر مقام پن ہو گیا۔ اوپر اپنا پتہ تصدیق کریں۔"
          )
        }
        showApplicationLocationPicker = false
      }
    )
  }

  // BOTTOM SHEET: PICK CAMERA OR GALLERY
  if (showPickerSheet && activePickerTarget != null) {
    ModalBottomSheet(
      onDismissRequest = { showPickerSheet = false },
      containerColor = DriverSurface
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .windowInsetsPadding(WindowInsets.navigationBars)
          .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        val title = when (val target = activePickerTarget) {
          is DriverPhotoPickerTarget.ApplicationDoc -> when (target.docType) {
            DriverDocType.CNIC_FRONT -> appText("CNIC Front Image", "شناختی کارڈ سامنے کی تصویر")
            DriverDocType.CNIC_BACK -> appText("CNIC Back Image", "شناختی کارڈ پچھلی تصویر")
            DriverDocType.VEHICLE_PLATE -> appText("Vehicle Number Plate", "گاڑی کی نمبر پلیٹ")
          }
          is DriverPhotoPickerTarget.DeliveryProof -> appText("Delivery Photo Proof", "ترسیل کی تصویری تصدیق")
          null -> appText("Photo", "تصویر")
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = DriverInk)
        Text(
          appText(
            "Choose camera to take a photo or select from device gallery (max 8 MB).",
            "تصویر لینے کے لیے کیمرہ یا ڈیوائس کی گیلری سے منتخب کریں (زیادہ سے زیادہ 8 ایم بی)۔"
          ),
          fontSize = 12.sp,
          color = JTTextSecondary
        )

        Spacer(Modifier.height(2.dp))

        OutlinedButton(
          onClick = {
            showPickerSheet = false
            startCameraCapture()
          },
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .testTag("picker_camera_button"),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, DriverOrangeBorder)
        ) {
          Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = DriverOrange)
          Spacer(Modifier.width(10.dp))
          Text(appText("Take Photo with Camera", "کیمرے سے تصویر لیں"), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = DriverInk)
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
            .heightIn(min = 50.dp)
            .testTag("picker_gallery_button"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = DriverOrange)
        ) {
          Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
          Spacer(Modifier.width(10.dp))
          Text(appText("Choose from Gallery", "گیلری سے منتخب کریں"), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(10.dp))
      }
    }
  }
}

@Composable
private fun DriverHeroStatTile(
  label: String,
  count: Int,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    color = if (selected) Color.White else Color.White.copy(alpha = 0.16f),
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.28f)),
    modifier = modifier
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick)
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
      ) {
        Icon(
          icon,
          contentDescription = null,
          tint = if (selected) DriverOrange else Color.White,
          modifier = Modifier.size(14.dp)
        )
        Text(
          text = count.toString(),
          fontSize = 17.sp,
          fontWeight = FontWeight.ExtraBold,
          color = if (selected) DriverInk else Color.White
        )
      }
      Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) DriverInkSecondary else Color(0xFFFFEDD5),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun DriverNavTabItem(
  selected: Boolean,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  badgeCount: Int?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .padding(vertical = 4.dp)
  ) {
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(16.dp))
        .background(if (selected) DriverOrangeSoft else Color.Transparent)
        .padding(horizontal = 16.dp, vertical = 5.dp),
      contentAlignment = Alignment.Center
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Icon(
          icon,
          contentDescription = label,
          tint = if (selected) DriverOrange else Color(0xFF94A3B8),
          modifier = Modifier.size(22.dp)
        )
        if (badgeCount != null && badgeCount > 0) {
          Surface(
            color = if (selected) DriverOrange else Color(0xFFCBD5E1),
            shape = CircleShape
          ) {
            Text(
              text = badgeCount.toString(),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = if (selected) Color.White else DriverInk
            )
          }
        }
      }
    }
    Spacer(Modifier.height(2.dp))
    Text(
      text = label,
      fontSize = 11.sp,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      color = if (selected) DriverOrange else Color(0xFF64748B),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun DriverStatusPillBadge(status: String) {
  Surface(
    color = when (status) {
      "approved" -> DriverGreenSoft
      "rejected", "suspended" -> DriverRedSoft
      else -> DriverAmberSoft
    },
    shape = RoundedCornerShape(20.dp),
    border = BorderStroke(
      1.dp,
      when (status) {
        "approved" -> DriverGreenBorder
        "rejected", "suspended" -> DriverRedBorder
        else -> DriverAmberBorder
      }
    ),
    modifier = Modifier.testTag("driver_application_status_badge")
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      if (status == "approved") {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = DriverGreen, modifier = Modifier.size(14.dp))
      }
      Text(
        text = when (status) {
          "approved" -> appText("Approved", "منظور شدہ")
          "rejected" -> appText("Rejected", "مسترد")
          "suspended" -> appText("Suspended", "معطل")
          else -> appText("Pending", "زیرِ التواء")
        },
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        color = when (status) {
          "approved" -> DriverGreen
          "rejected", "suspended" -> DriverRedDark
          else -> DriverAmber
        }
      )
    }
  }
}

@Composable
private fun DriverOnboardingStepChip(
  stepNum: Int,
  label: String,
  done: Boolean,
  modifier: Modifier = Modifier
) {
  Surface(
    color = if (done) DriverGreenSoft else DriverSurfaceMuted,
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, if (done) DriverGreenBorder else DriverBorder),
    modifier = modifier
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
      Box(
        modifier = Modifier
          .size(18.dp)
          .clip(CircleShape)
          .background(if (done) DriverGreen else DriverOrange),
        contentAlignment = Alignment.Center
      ) {
        if (done) {
          Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
        } else {
          Text("$stepNum", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
      }
      Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (done) DriverGreenDark else DriverInkSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

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
      locationError = AppLanguageManager.tr(
        "Turn on device location / GPS to detect your current location.",
        "موجودہ مقام معلوم کرنے کے لیے ڈیوائس کی لوکیشن / جی پی ایس آن کریں۔"
      )
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
                locationError = AppLanguageManager.tr(
                  "Turn on device location / GPS and try again.",
                  "ڈیوائس کی لوکیشن / جی پی ایس آن کریں اور دوبارہ کوشش کریں۔"
                )
              }
            }
            is LocationFetchResult.Failure -> {
              isLocating = false
              locationError = result.message
            }
          }
        } catch (_: Exception) {
          isLocating = false
          locationError = AppLanguageManager.tr(
            "Could not get current location. Move the map pin manually.",
            "موجودہ مقام حاصل نہیں ہو سکا۔ نقشے کا پن خود حرکت دیں۔"
          )
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
    else locationError = AppLanguageManager.tr(
      "Location permission denied. You can still select a point on the map.",
      "لوکیشن کی اجازت نہیں ملی۔ آپ اب بھی نقشے پر مقام منتخب کر سکتے ہیں۔"
    )
  }

  LaunchedEffect(Unit) {
    if (!hasInitialPoint && hasLocationPermission) {
      centerOnCurrentLocation()
    } else if (hasInitialPoint) {
      val resolved = AddressReverseGeocoder.resolve(context, selectedPoint.latitude, selectedPoint.longitude)
      previewAddress = resolved?.toReadableFullAddress().orEmpty()
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .padding(16.dp)
        .widthIn(max = 600.dp)
        .fillMaxWidth()
        .heightIn(max = 680.dp),
      shape = RoundedCornerShape(22.dp),
      color = DriverSurface
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
        Text(
          appText("Select Your Current Location", "اپنا موجودہ مقام منتخب کریں"),
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = DriverInk
        )
        Text(
          appText(
            "Move the map until the pin is on your exact location, or tap Use My Current Location.",
            "نقشے کو اس وقت تک حرکت دیں جب تک پن آپ کے درست مقام پر نہ آ جائے، یا موجودہ مقام استعمال کریں۔"
          ),
          fontSize = 12.sp,
          color = DriverInkMuted
        )
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .heightIn(min = 220.dp, max = 380.dp)
            .fillMaxHeight(0.45f)
            .clip(RoundedCornerShape(16.dp))
        ) {
          AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
              Configuration.getInstance().userAgentValue = ctx.packageName
              Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
              MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                isTilesScaledToDpi = true
                controller.setZoom(if (hasInitialPoint) 16.5 else 3.0)
                controller.setCenter(selectedPoint)
                mapView = this
              }
            },
            update = { map -> mapView = map }
          )
          Icon(
            Icons.Filled.LocationOn,
            contentDescription = appText("Selected map point", "منتخب کردہ نقشہ مقام"),
            tint = DriverOrange,
            modifier = Modifier.align(Alignment.Center).size(42.dp)
          )
          Surface(
            color = Color.White.copy(alpha = 0.94f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
          ) {
            Text(
              text = if (isLocating) appText("Finding your location…", "مقام تلاش کیا جا رہا ہے…")
              else appText("Pin location", "مقام پن کریں"),
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = DriverOrange,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
          }
        }
        if (previewAddress.isNotBlank()) {
          Surface(
            color = DriverSurfaceMuted,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, DriverBorder),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = previewAddress,
              fontSize = 11.5.sp,
              color = DriverInkSecondary,
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
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
          enabled = !isLocating && !isResolvingAddress,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = DriverOrange)
        ) {
          if (isLocating) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
          else Icon(Icons.Filled.MyLocation, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text(
            if (isLocating) appText("Finding location…", "مقام تلاش کیا جا رہا ہے…")
            else appText("Use My Current Location", "میرا موجودہ مقام استعمال کریں")
          )
        }
        locationError?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedButton(
            onClick = onDismiss,
            enabled = !isResolvingAddress,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
          ) { Text(appText("Cancel", "منسوخ کریں")) }
          Button(
            onClick = {
              val point = mapView?.mapCenter ?: selectedPoint
              val lat = point.latitude
              val lng = point.longitude
              if (lat == 0.0 && lng == 0.0) {
                locationError = AppLanguageManager.tr(
                  "Select your actual location using GPS or move the map to the correct point.",
                  "جی پی ایس سے اپنا درست مقام منتخب کریں یا نقشے کو درست جگہ پر لے جائیں۔"
                )
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
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DriverOrange)
          ) {
            if (isResolvingAddress) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
              Spacer(Modifier.width(6.dp))
              Text(appText("Saving…", "محفوظ ہو رہا ہے…"))
            } else {
              Text(appText("Confirm Location", "مقام کی تصدیق کریں"))
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
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (docState.storagePath != null) Color(0xFFF0FDF4) else Color(0xFFFFFBF7)
    ),
    border = BorderStroke(
      1.dp,
      if (docState.storagePath != null) DriverGreenBorder else DriverOrangeBorder
    )
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = DriverInk)
          Text(subtitle, fontSize = 11.sp, color = JTTextSecondary)
        }
        if (docState.storagePath != null) {
          Spacer(Modifier.width(8.dp))
          Surface(
            color = DriverGreenSoft,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, DriverGreenBorder)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = DriverGreen, modifier = Modifier.size(14.dp))
              Text(appText("Uploaded", "اپ لوڈ شدہ"), color = DriverGreenDark, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      AnimatedVisibility(visible = docState.localBitmap != null || docState.localUri != null) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(124.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFE2E8F0))
        ) {
          if (docState.localBitmap != null) {
            Image(
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
                .background(Color.Black.copy(alpha = 0.55f)),
              contentAlignment = Alignment.Center
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.height(6.dp))
                Text(appText("Uploading securely…", "محفوظ طریقے سے اپ لوڈ ہو رہا ہے…"), color = Color.White, fontSize = 11.sp)
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
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DriverOrange)
          ) {
            Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(appText("Select / Take Photo", "تصویر منتخب کریں / لیں"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          }
        } else {
          OutlinedButton(
            onClick = onChoosePhoto,
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp),
            shape = RoundedCornerShape(10.dp)
          ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(appText("Replace", "تبدیل کریں"), fontSize = 11.5.sp)
          }
          OutlinedButton(
            onClick = onClear,
            modifier = Modifier.heightIn(min = 48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
          ) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(appText("Remove", "ہٹائیں"), fontSize = 11.5.sp)
          }
        }
      }
    }
  }
}
