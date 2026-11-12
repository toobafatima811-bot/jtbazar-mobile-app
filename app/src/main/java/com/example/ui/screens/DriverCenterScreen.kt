package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.supabase.SupabaseClient
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

data class DocUploadItem(
  val localUri: Uri? = null,
  val localBitmap: Bitmap? = null,
  val storagePath: String? = null,
  val isUploading: Boolean = false,
  val errorMessage: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverCenterScreen(
  onBack: () -> Unit,
  onViewCustomerLocation: (JSONObject) -> Unit = {}
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val user by SupabaseClient.currentUser.collectAsState()

  var enabled by remember { mutableStateOf(false) }
  var application by remember { mutableStateOf<JSONObject?>(null) }
  var deliveries by remember { mutableStateOf(listOf<JSONObject>()) }

  // Form input fields
  var fullName by remember { mutableStateOf(user?.fullName.orEmpty()) }
  var phone by remember { mutableStateOf(user?.phone.orEmpty()) }
  var currentAddress by remember { mutableStateOf("") }
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

  suspend fun uploadDocBytes(docType: DriverDocType, bytes: ByteArray, uri: Uri?, bitmap: Bitmap?) {
    val uid = user?.id ?: return
    val prefix = when (docType) {
      DriverDocType.CNIC_FRONT -> "cnic_front"
      DriverDocType.CNIC_BACK -> "cnic_back"
      DriverDocType.VEHICLE_PLATE -> "vehicle_plate"
    }

    // Set uploading state
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

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("JTBazar Driver", fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFFEA580C)) }

      error?.let { err ->
        item {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(10.dp)
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
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC))
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

      if (!enabled) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder)
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(44.dp))
              Text("Driver Applications Closed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Text(
                "Driver registration is currently disabled by Admin. Please check back later.",
                fontSize = 12.5.sp,
                color = JTTextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )
            }
          }
        }
      } else if (application == null) {
        // DRIVER APPLICATION FORM
        item {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Become a JTBazar Driver", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = JTTextPrimary)
            Text("Fill out the details below and upload clear photos of required documents.", fontSize = 12.5.sp, color = JTTextSecondary)
          }
        }

        // 1. Full Name
        item {
          OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Full Name *") },
            placeholder = { Text("As printed on CNIC") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
          )
        }

        // 2. Phone Number
        item {
          OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Mobile Phone Number *") },
            placeholder = { Text("03001234567") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
          )
        }

        // 3. Current Address
        item {
          OutlinedTextField(
            value = currentAddress,
            onValueChange = { currentAddress = it },
            label = { Text("Current Address *") },
            placeholder = { Text("House #, Street, Area, City") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            shape = RoundedCornerShape(10.dp)
          )
        }

        // 4. Vehicle Type Dropdown: Bike / Car
        item {
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
                },
                leadingIcon = { Icon(Icons.Filled.TwoWheeler, contentDescription = null) }
              )
              DropdownMenuItem(
                text = { Text("Car (Automobile)") },
                onClick = {
                  vehicleType = "Car"
                  isVehicleDropdownExpanded = false
                },
                leadingIcon = { Icon(Icons.Filled.DirectionsCar, contentDescription = null) }
              )
            }
          }
        }

        // DOCUMENT UPLOAD SECTION
        item {
          Spacer(Modifier.height(4.dp))
          Text(
            text = "Required Verification Documents",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = JTTextPrimary
          )
          Text(
            text = "Uploaded documents are private and stored securely for identity verification.",
            fontSize = 11.5.sp,
            color = JTTextSecondary
          )
        }

        // 5. CNIC Front Image
        item {
          DocumentUploadCard(
            title = "CNIC Front Side *",
            subtitle = "Clear, well-lit photo of the front side",
            docState = cnicFrontDoc,
            onChoosePhoto = {
              activeDocForPicker = DriverDocType.CNIC_FRONT
              showPickerSheet = true
            },
            onClear = { cnicFrontDoc = DocUploadItem() }
          )
        }

        // 6. CNIC Back Image
        item {
          DocumentUploadCard(
            title = "CNIC Back Side *",
            subtitle = "Clear photo of the back side showing barcode",
            docState = cnicBackDoc,
            onChoosePhoto = {
              activeDocForPicker = DriverDocType.CNIC_BACK
              showPickerSheet = true
            },
            onClear = { cnicBackDoc = DocUploadItem() }
          )
        }

        // 7. Vehicle Number Plate Image
        item {
          DocumentUploadCard(
            title = "Vehicle Number Plate *",
            subtitle = "Clear rear-side photo showing registration plate",
            docState = plateDoc,
            onChoosePhoto = {
              activeDocForPicker = DriverDocType.VEHICLE_PLATE
              showPickerSheet = true
            },
            onClear = { plateDoc = DocUploadItem() }
          )
        }

        // Submission Button & Validation Checks
        item {
          val isFormValid = fullName.trim().isNotBlank() &&
            phone.trim().isNotBlank() &&
            currentAddress.trim().isNotBlank() &&
            vehicleType.isNotBlank() &&
            !cnicFrontDoc.storagePath.isNullOrBlank() &&
            !cnicBackDoc.storagePath.isNullOrBlank() &&
            !plateDoc.storagePath.isNullOrBlank() &&
            !cnicFrontDoc.isUploading &&
            !cnicBackDoc.isUploading &&
            !plateDoc.isUploading

          if (!isFormValid) {
            val missing = mutableListOf<String>()
            if (fullName.isBlank()) missing.add("Name")
            if (phone.isBlank()) missing.add("Phone")
            if (currentAddress.isBlank()) missing.add("Address")
            if (cnicFrontDoc.storagePath.isNullOrBlank()) missing.add("CNIC Front")
            if (cnicBackDoc.storagePath.isNullOrBlank()) missing.add("CNIC Back")
            if (plateDoc.storagePath.isNullOrBlank()) missing.add("Number Plate")

            Text(
              text = "Missing: ${missing.joinToString(", ")}",
              fontSize = 11.5.sp,
              color = Color(0xFFC2410C),
              fontWeight = FontWeight.Medium
            )
          }

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
                    message = "Driver application submitted successfully! Admin approval is required."
                    load()
                  } else {
                    error = result.exceptionOrNull()?.message ?: "Application submission failed"
                  }
                } catch (e: Exception) {
                  error = e.message ?: "Submission error"
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
            if (busy) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
              Spacer(Modifier.width(8.dp))
              Text("Submitting…", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            } else {
              Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(8.dp))
              Text("Submit Driver Application", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      } else {
        // APPLICATION STATUS CARD (PENDING, APPROVED, REJECTED, SUSPENDED)
        val status = application?.optString("status") ?: "pending"
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder)
          ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("Driver Application", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(
                  color = when (status) {
                    "approved" -> Color(0xFFDCFCE7)
                    "rejected", "suspended" -> Color(0xFFFEE2E2)
                    else -> Color(0xFFFEF3C7)
                  },
                  shape = RoundedCornerShape(50)
                ) {
                  Text(
                    text = status.replaceFirstChar { it.uppercase() },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = when (status) {
                      "approved" -> Color(0xFF166534)
                      "rejected", "suspended" -> Color(0xFF991B1B)
                      else -> Color(0xFF92400E)
                    }
                  )
                }
              }

              application?.optString("full_name")?.takeIf { it.isNotBlank() }?.let {
                Text("Applicant: $it", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
              }
              application?.optString("vehicle_type")?.takeIf { it.isNotBlank() }?.let {
                Text("Vehicle: $it", fontSize = 12.sp, color = JTTextSecondary)
              }
              application?.optString("current_address")?.takeIf { it.isNotBlank() }?.let {
                Text("Address: $it", fontSize = 12.sp, color = JTTextSecondary)
              }

              application?.optString("admin_note")?.takeIf { it.isNotBlank() && it != "null" }?.let { note ->
                Surface(
                  color = Color(0xFFF8FAFC),
                  shape = RoundedCornerShape(8.dp),
                  border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(
                    text = "Admin Note: $note",
                    fontSize = 12.sp,
                    color = JTTextPrimary,
                    modifier = Modifier.padding(10.dp)
                  )
                }
              }
            }
          }
        }

        // APPROVED DRIVER: ASSIGNED DELIVERIES LIST
        if (status == "approved") {
          item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
              Column(Modifier.weight(1f)) {
                Text("My Assigned Deliveries", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${deliveries.size} active delivery assignment${if (deliveries.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = JTTextSecondary)
              }
              IconButton(onClick = { scope.launch { load() } }, enabled = !loading) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh deliveries", tint = Color(0xFFEA580C))
              }
            }
          }

          if (deliveries.isEmpty()) {
            item {
              Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder)
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(36.dp))
                  Text("No Assigned Deliveries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                  Text("Orders assigned to you by Admin will appear here in real-time.", style = MaterialTheme.typography.bodyMedium, color = JTTextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
              }
            }
          }

          items(deliveries, key = { it.optString("assignment_id") }) { delivery ->
            val lat = delivery.optDouble("delivery_lat", Double.NaN)
            val lng = delivery.optDouble("delivery_lng", Double.NaN)
            val assignmentStatus = delivery.optString("assignment_status", "assigned")
            val hasValidCoords = lat.isFinite() && lng.isFinite() && !(lat == 0.0 && lng == 0.0)

            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(18.dp),
              elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
              colors = CardDefaults.cardColors(containerColor = Color.White),
              border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder)
            ) {
              Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Order Number & Status Badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("ORDER", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEA580C), fontWeight = FontWeight.Bold)
                    Text(delivery.optString("order_number"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                  }
                  Surface(
                    color = if (assignmentStatus == "picked_up") Color(0xFFEFF6FF) else if (assignmentStatus == "delivered") Color(0xFFDCFCE7) else Color(0xFFFFF7ED),
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (assignmentStatus == "picked_up") Color(0xFF93C5FD) else if (assignmentStatus == "delivered") Color(0xFF86EFAC) else Color(0xFFFDBA74))
                  ) {
                    Text(
                      text = assignmentStatus.replace('_', ' ').replaceFirstChar { it.uppercase() },
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.Bold,
                      color = if (assignmentStatus == "picked_up") Color(0xFF1D4ED8) else if (assignmentStatus == "delivered") Color(0xFF166534) else Color(0xFFC2410C)
                    )
                  }
                }

                HorizontalDivider(color = JTBorder)

                // Customer Details
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                  Text("CUSTOMER", style = MaterialTheme.typography.labelSmall, color = JTTextSecondary, fontWeight = FontWeight.Bold)
                  Text(delivery.optString("customer_name").ifBlank { "Customer" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF2563EB))
                    Text(delivery.optString("customer_phone"), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF2563EB))
                  }
                }

                // Delivery Address
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFFEA580C))
                    Text("DELIVERY ADDRESS", style = MaterialTheme.typography.labelSmall, color = JTTextSecondary, fontWeight = FontWeight.Bold)
                  }
                  Text(driverAddressText(delivery), style = MaterialTheme.typography.bodyMedium)
                }

                // Order Items
                val itemRows = delivery.optJSONArray("items")
                if (itemRows != null && itemRows.length() > 0) {
                  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ITEMS", style = MaterialTheme.typography.labelSmall, color = JTTextSecondary, fontWeight = FontWeight.Bold)
                    for (index in 0 until itemRows.length()) {
                      val item = itemRows.optJSONObject(index) ?: continue
                      Row(verticalAlignment = Alignment.Top) {
                        Text("•", modifier = Modifier.padding(end = 6.dp), color = Color(0xFFEA580C))
                        Text("${item.optString("name")}  ×  ${item.optInt("quantity", 1)}", style = MaterialTheme.typography.bodyMedium)
                      }
                    }
                  }
                }

                // Dedicated "View Customer Location" Button -> Opens DriverNavigationScreen
                Button(
                  onClick = { onViewCustomerLocation(delivery) },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                  shape = RoundedCornerShape(10.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
                ) {
                  Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(8.dp))
                  Text(
                    text = if (hasValidCoords) "View Customer Location" else "View Customer Location (No GPS)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                  )
                }

                // Status Update Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                  if (assignmentStatus == "assigned") {
                    OutlinedButton(
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
                      modifier = Modifier.weight(1f),
                      shape = RoundedCornerShape(8.dp)
                    ) {
                      Text(if (busy) "Updating…" else "Picked up", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                  }
                  if (assignmentStatus != "delivered") {
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
                      modifier = Modifier.weight(1f),
                      shape = RoundedCornerShape(8.dp),
                      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                    ) {
                      Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                      Spacer(Modifier.width(4.dp))
                      Text(if (busy) "Updating…" else "Delivered", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
        Text("Choose camera to take a fresh photo or select from gallery.", fontSize = 12.sp, color = JTTextSecondary)

        Spacer(Modifier.height(4.dp))

        // Option 1: Take Photo with Camera
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

        // Option 2: Choose from Gallery
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
          Text(title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = JTTextPrimary)
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

      // Preview thumbnail if available
      if (docState.localBitmap != null || docState.localUri != null) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
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

      // Action buttons: "Take / Choose Photo", "Replace", "Clear"
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
