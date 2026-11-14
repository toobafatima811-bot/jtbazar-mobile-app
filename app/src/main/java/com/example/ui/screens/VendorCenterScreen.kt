package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.VendorApplication
import com.example.data.supabase.VendorService
import com.example.ui.theme.JTOrange
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

@Composable
fun VendorCenterScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val user by SupabaseClient.currentUser.collectAsState()
  var application by remember { mutableStateOf<VendorApplication?>(null) }
  var isVendor by remember { mutableStateOf(false) }
  var products by remember { mutableStateOf(JSONArray()) }
  var orders by remember { mutableStateOf(JSONArray()) }
  var ordersSearch by remember { mutableStateOf("") }
  var ordersStatus by remember { mutableStateOf("all") }
  var ordersPage by remember { mutableIntStateOf(0) }
  var ordersTotal by remember { mutableIntStateOf(0) }
  var loading by remember { mutableStateOf(true) }
  var hasLoadedData by remember { mutableStateOf(false) }
  var saving by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }
  var actionErrorMessage by remember { mutableStateOf("") }
  var info by remember { mutableStateOf("") }
  var shopName by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf(user?.phone.orEmpty()) }
  var ownerName by remember { mutableStateOf(user?.fullName.orEmpty()) }
  var cnic by remember { mutableStateOf("") }
  var vendorType by remember { mutableStateOf("individual_seller") }
  var currentAddress by remember { mutableStateOf("") }
  var fullAddress by remember { mutableStateOf("") }
  var shopLocation by remember { mutableStateOf("") }
  var latitude by remember { mutableStateOf("") }
  var longitude by remember { mutableStateOf("") }
  var showMapPicker by remember { mutableStateOf(false) }
  var verificationPath by remember { mutableStateOf<String?>(null) }
  var frontPath by remember { mutableStateOf<String?>(null) }
  var backPath by remember { mutableStateOf<String?>(null) }
  var showProductForm by remember { mutableStateOf(false) }
  var vendorTab by remember { mutableStateOf("overview") }

  fun refresh() {
    scope.launch {
      loading = !hasLoadedData
      errorMessage = ""
      try {
        application = VendorService.getOwnApplication()
        isVendor = VendorService.isVendor()
        if (isVendor && application?.status == "approved") {
          products = VendorService.listProducts()
          orders = VendorService.listOrders(ordersPage, ordersSearch, ordersStatus)
          ordersTotal = orders.optJSONObject(0)?.optInt("total_orders", 0) ?: 0
        }
        hasLoadedData = true
        actionErrorMessage = ""
      } catch (e: Exception) {
        val message = vendorLoadErrorMessage(e)
        if (hasLoadedData) actionErrorMessage = message else errorMessage = message
      } finally { loading = false }
    }
  }
  LaunchedEffect(user?.id) { refresh() }

  suspend fun upload(uri: Uri, side: String): String {
    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
      ?: throw IllegalStateException("Selected file could not be read.")
    require(bytes.size <= 10 * 1024 * 1024) { "Each verification file must be 10 MB or smaller." }
    require(mime in listOf("application/pdf", "image/jpeg", "image/png", "image/webp")) { "Use PDF, JPG, PNG or WebP files." }
    val ext = when (mime) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; else -> "pdf" }
    val path = "${user?.id}/$side-${System.currentTimeMillis()}.$ext"
    return VendorService.uploadPrivateDocument(path, bytes, mime)
  }

  var pendingDoc by remember { mutableStateOf("verification") }
  val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri != null) scope.launch {
      saving = true; actionErrorMessage = ""; info = ""
      try {
        val path = upload(uri, pendingDoc)
        when (pendingDoc) { "verification" -> verificationPath = path; "cnic-front" -> frontPath = path; "cnic-back" -> backPath = path }
        info = "Document uploaded securely."
      } catch (e: Exception) { actionErrorMessage = e.message ?: "Document upload failed." }
      finally { saving = false }
    }
  }

  Column(Modifier.fillMaxSize().background(Color.White)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
      Column(Modifier.weight(1f)) {
        Text("JTBazar Vendor Center", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Your shop, products and pickup preparation", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
      }
      Icon(Icons.Default.Storefront, contentDescription = null, tint = JTOrange)
    }
    HorizontalDivider()
    if (loading) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = JTOrange) }
    } else {
      Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (errorMessage.isNotBlank()) Text(errorMessage, color = MaterialTheme.colorScheme.error)
        if (actionErrorMessage.isNotBlank()) Text(actionErrorMessage, color = MaterialTheme.colorScheme.error)
        if (info.isNotBlank()) Text(info, color = Color(0xFF16803C))
        when {
          errorMessage.isNotBlank() -> {
            Text("Connection problem", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            Text("No application or shop data was submitted. Check your internet connection and try again.", color = Color.Gray)
            Button(onClick = { refresh() }, enabled = !loading, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) {
              Text("Retry connection")
            }
          }
          application?.vendorLocked == true -> {
            Text("Vendor account restricted", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            StatusPanel(application!!)
            Text(application?.lockReason ?: "Vendor tools are temporarily unavailable. Contact JTBazar support for details.", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { refresh() }) { Text("Refresh status") }
          }
          isVendor && application?.status == "approved" -> {
            VendorDashboardHero(
              shopName = application?.shopName.orEmpty(),
              onRefresh = { refresh() },
              refreshing = loading
            )
            VendorDashboardTabs(
              selected = vendorTab,
              onSelect = { vendorTab = it }
            )
            when (vendorTab) {
              "overview" -> {
                Text("Your business at a glance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF17243A))
                Text("A live snapshot of your JTBazar shop activity.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF718096))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  VendorMetricCard("Total listings", products.length().toString(), "Products in your shop", Icons.Default.ShoppingBag, Modifier.weight(1f))
                  VendorMetricCard("Order items", ordersTotal.toString(), "Across your fulfilment", Icons.Default.ReceiptLong, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  VendorMetricCard("Current page", orders.length().toString(), "Items to prepare", Icons.Default.LocalShipping, Modifier.weight(1f))
                  VendorMetricCard("Shop status", "Active", "Approved seller", Icons.Default.TrendingUp, Modifier.weight(1f))
                }
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(20.dp),
                  colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4EC))
                ) {
                  Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).background(Color.White, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                      Icon(Icons.Default.Add, null, tint = JTOrange, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                      Text("Grow your shop", color = Color(0xFF17243A), fontWeight = FontWeight.Bold)
                      Text("Add a new product listing and make it available to JTBazar customers.", color = Color(0xFF66758A), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { vendorTab = "listings"; showProductForm = true }) {
                      Icon(Icons.Default.ChevronRight, contentDescription = "Create listing", tint = JTOrange)
                    }
                  }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text("Recent listings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF17243A))
                  Spacer(Modifier.weight(1f))
                  TextButton(onClick = { vendorTab = "listings" }) { Text("View all", color = JTOrange) }
                }
                if (products.length() == 0) {
                  VendorEmptyState("No products listed yet", "Your product listings will appear here once you add them.", "Add your first product") {
                    vendorTab = "listings"; showProductForm = true
                  }
                } else {
                  for (i in 0 until minOf(products.length(), 4)) {
                    val product = products.optJSONObject(i) ?: continue
                    VendorCompactProductCard(product)
                  }
                }
              }
              "listings" -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Inventory2, null, tint = JTOrange)
              Spacer(Modifier.width(8.dp)); Text("Your products", fontWeight = FontWeight.Bold)
              Spacer(Modifier.weight(1f))
              TextButton(onClick = { showProductForm = !showProductForm }) { Icon(Icons.Default.Add, null); Text("Add product") }
            }
            if (showProductForm) {
              VendorListingEditor(
                onCancel = { showProductForm = false },
                onSaved = {
                  showProductForm = false
                  info = "Listing submitted for JTBazar moderation."
                  try { products = VendorService.listProducts() }
                  catch (e: Exception) { actionErrorMessage = e.message ?: "Listing was submitted, but the product list could not refresh." }
                }
              )
            }
            if (products.length() == 0) Text("No products yet. Add your first product.")
            for (i in 0 until products.length()) {
              val p = products.optJSONObject(i) ?: continue
              val id = p.optString("id")
              val name = p.optString("name", "Product")
              val hidden = p.optBoolean("vendor_hidden", p.optBoolean("is_hidden", false))
              Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Text(name, fontWeight = FontWeight.SemiBold)
                  Text("PKR ${p.optString("price","0")}  •  ${p.optString("publication_status", p.optString("status","draft"))}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    val approvedForVisibility = p.optString("moderation_status").equals("approved", ignoreCase = true)
                    Text(if (hidden) "Hidden" else if (approvedForVisibility) "Visible" else "Awaiting approval", color = if (hidden) Color.Gray else if (approvedForVisibility) Color(0xFF16803C) else Color(0xFF9A6700))
                    Spacer(Modifier.weight(1f))
                    if (approvedForVisibility) {
                      TextButton(onClick = {
                        scope.launch { saving = true; actionErrorMessage = ""; try { VendorService.setProductVisibility(id, !hidden); products = VendorService.listProducts() } catch (e: Exception) { actionErrorMessage = e.message ?: "Visibility update failed." } finally { saving = false } }
                      }, enabled = !saving) { Text(if (hidden) "Show" else "Hide") }
                    } else {
                      Text("After moderation", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    }
                  }
                }
              }
            }

              }
              "inventory" -> VendorInventoryPanel()
              "fulfilment" -> {
            Text("Vendor orders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = ordersSearch, onValueChange = { ordersSearch = it }, label = { Text("Search order number or product") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              listOf("all", "pending", "delivered", "not_delivered").forEach { status ->
                TextButton(onClick = {
                  ordersStatus = status; ordersPage = 0
                  scope.launch {
                    saving = true
                    try { orders = VendorService.listOrders(0, ordersSearch, status); ordersTotal = orders.optJSONObject(0)?.optInt("total_orders", 0) ?: 0 }
                    catch (e: Exception) { actionErrorMessage = e.message ?: "Orders could not be filtered." }
                    finally { saving = false }
                  }
                }) { Text(if (ordersStatus == status) "• ${status.replace('_', ' ')}" else status.replace('_', ' ')) }
              }
            }
            Button(enabled = !saving, onClick = {
              scope.launch {
                saving = true
                try { ordersPage = 0; orders = VendorService.listOrders(0, ordersSearch, ordersStatus); ordersTotal = orders.optJSONObject(0)?.optInt("total_orders", 0) ?: 0 }
                catch (e: Exception) { actionErrorMessage = e.message ?: "Orders could not be searched." }
                finally { saving = false }
              }
            }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) { Text("Search orders") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
              Text("Page ${ordersPage + 1} • ${ordersTotal} orders", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
              Row {
                TextButton(enabled = !saving && ordersPage > 0, onClick = {
                  scope.launch { saving = true; try { ordersPage--; orders = VendorService.listOrders(ordersPage, ordersSearch, ordersStatus) } catch (e: Exception) { actionErrorMessage = e.message ?: "Previous page failed." } finally { saving = false } }
                }) { Text("Previous") }
                TextButton(enabled = !saving && (ordersPage + 1) * 12 < ordersTotal, onClick = {
                  scope.launch { saving = true; try { ordersPage++; orders = VendorService.listOrders(ordersPage, ordersSearch, ordersStatus) } catch (e: Exception) { actionErrorMessage = e.message ?: "Next page failed." } finally { saving = false } }
                }) { Text("Next") }
              }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.LocalShipping, null, tint = JTOrange); Spacer(Modifier.width(8.dp))
              Text("Pickup preparation", fontWeight = FontWeight.Bold)
            }
            Text("Customer contact, payment, delivery and order fulfilment remain managed by JTBazar. You only prepare your own items for pickup.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            if (orders.length() == 0) Text("No vendor order items yet.")
            for (i in 0 until orders.length()) {
              val o = orders.optJSONObject(i) ?: continue
              val orderId = o.optString("order_id")
              Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                  Text(o.optString("product_name_snapshot", o.optString("product_name","Order item")), fontWeight = FontWeight.SemiBold)
                  Text("Order ${o.optString("order_number","")}  •  Qty: ${o.optString("quantity","1")}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                  Text("Status: ${o.optString("vendor_fulfillment_status", o.optString("status","processing"))}", style = MaterialTheme.typography.bodySmall)
                  Button(enabled = !saving && orderId.isNotBlank() && o.optString("vendor_fulfillment_status", o.optString("status")) != "ready_for_pickup", onClick = {
                    scope.launch { saving = true; try { VendorService.markOrderReady(orderId); info = "Marked ready for JTBazar pickup."; orders = VendorService.listOrders(ordersPage, ordersSearch, ordersStatus); ordersTotal = orders.optJSONObject(0)?.optInt("total_orders", 0) ?: 0 } catch (e: Exception) { actionErrorMessage = e.message ?: "Could not update order." } finally { saving = false } }
                  }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) { Text("Ready for pickup") }
                }
              }
            }

              }
              else -> VendorAdvancedPanel(application!!, products) { refresh() }
            }

            OutlinedButton(onClick = { refresh() }) { Text("Refresh dashboard") }
          }
          application != null -> {
            if (application?.status.equals("pending", ignoreCase = true)) {
              PendingVendorApplicationScreen(
                application = application!!,
                onRefresh = { refresh() },
                refreshing = loading
              )
            } else {
              Text("Seller application", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
              StatusPanel(application!!)
              if (application?.status == "rejected") Text("Reason: ${application?.rejectionReason ?: "Please review your application."}", color = MaterialTheme.colorScheme.error)
              if (application?.status == "rejected") VendorApplicationResubmissionPanel(application!!) { application = it; info = "Application resubmitted." }
              OutlinedButton(onClick = { refresh() }) { Text("Refresh application status") }
            }
          }
          else -> {
            Text("Become a JTBazar Vendor", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Submit your shop and identity details. All verification files are stored privately for JTBazar review.", color = Color.Gray)
            VendorField("Shop name", shopName) { shopName = it }
            VendorField("Shop description (at least 10 characters)", description, minLines = 3) { description = it }
            VendorField("Owner full legal name", ownerName) { ownerName = it }
            VendorTypeDropdown(selectedType = vendorType, onTypeSelected = { vendorType = it })
            VendorField("CNIC number (13 digits, no dashes)", cnic) { cnic = it.filter(Char::isDigit).take(13) }
            VendorField("Contact phone (03XXXXXXXXX)", phone) { phone = it }
            VendorField("Current address", currentAddress) { currentAddress = it }
            VendorField("Complete address", fullAddress, minLines = 2) { fullAddress = it }
            VendorField("Shop location / landmark details", shopLocation, minLines = 2) { shopLocation = it }
            Text("Shop location on map", fontWeight = FontWeight.SemiBold)
            Text(
              if (latitude.isNotBlank() && longitude.isNotBlank()) "Selected: $latitude, $longitude" else "Choose your shop location on the map. You can use your current location or move the map pin.",
              color = Color.Gray,
              style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = { showMapPicker = true }, modifier = Modifier.fillMaxWidth()) {
              Icon(Icons.Default.Storefront, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text(if (latitude.isNotBlank() && longitude.isNotBlank()) "Change shop location on map" else "Choose shop location on map")
            }
            Text("Verification documents", fontWeight = FontWeight.Bold)
            DocumentButton("Identity / business verification document", verificationPath != null) { pendingDoc = "verification"; documentPicker.launch("*/*") }
            DocumentButton("CNIC front image", frontPath != null) { pendingDoc = "cnic-front"; documentPicker.launch("image/*") }
            DocumentButton("CNIC back image", backPath != null) { pendingDoc = "cnic-back"; documentPicker.launch("image/*") }
            Button(enabled = !saving, onClick = {
              scope.launch {
                saving = true; actionErrorMessage = ""; info = ""
                try {
                  val u = user ?: error("Please sign in again.")
                  val normalizedPhone = normalizePakistaniPhone(phone)
                  require(shopName.trim().length in 2..160) { "Shop name must be between 2 and 160 characters." }
                  require(description.trim().length in 10..2000) { "Shop description must be between 10 and 2,000 characters." }
                  require(ownerName.trim().length >= 3) { "Enter the owner's full legal name." }
                  require(cnic.matches(Regex("^[0-9]{13}$"))) { "Enter a 13-digit CNIC number without dashes." }
                  require(normalizedPhone.matches(Regex("^(03[0-9]{9}|\\+923[0-9]{9})$"))) { "Enter a valid Pakistani phone number, e.g. 03001234567." }
                  require(currentAddress.trim().length >= 3 && fullAddress.trim().length >= 10 && shopLocation.trim().length >= 10) { "Complete your current address, full address and shop landmark (at least 10 characters)." }
                  val lat = latitude.toDoubleOrNull() ?: error("Choose your shop location on the map.")
                  val lng = longitude.toDoubleOrNull() ?: error("Choose your shop location on the map.")
                  require(lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0)) { "Map coordinates are invalid. Select your actual shop location." }
                  require(!verificationPath.isNullOrBlank() && !frontPath.isNullOrBlank() && !backPath.isNullOrBlank()) { "Upload all three verification documents before submitting." }
                  require(u.email?.isNotBlank() == true) { "Your account email is missing. Sign in again." }
                  val slugBase = shopName.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-').take(120).trim('-').ifBlank { "shop" }
                  val slug = "$slugBase-${java.util.UUID.randomUUID().toString().take(8)}"
                  val body = JSONObject().put("user_id", u.id).put("shop_name", shopName.trim()).put("slug", slug)
                    .put("description", description.trim().take(2000)).put("current_address", currentAddress.trim()).put("complete_address", fullAddress.trim())
                    .put("shop_location_label", currentAddress.trim().take(120)).put("contact_email", u.email).put("phone", normalizedPhone)
                    .put("document_path", verificationPath).put("cnic_front_path", frontPath).put("cnic_back_path", backPath)
                    .put("vendor_type", vendorType.trim()).put("owner_full_name", ownerName.trim()).put("cnic_number", cnic)
                    .put("shop_location_text", shopLocation.trim()).put("shop_lat", lat).put("shop_lng", lng).put("show_shop_on_map", true)
                  application = VendorService.submitApplication(body)
                  info = "Application submitted successfully. JTBazar admin review is pending."
                } catch (e: Exception) { actionErrorMessage = e.message ?: "Application could not be submitted." }
                finally { saving = false }
              }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) {
              if (saving) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White) else Text("Submit vendor application")
            }
            Text("Your application will remain pending until JTBazar admin approves it.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
          }
        }
      }
    }
  }
  if (showMapPicker) {
    ShopLocationPickerDialog(
      initialLatitude = latitude.toDoubleOrNull(),
      initialLongitude = longitude.toDoubleOrNull(),
      onDismiss = { showMapPicker = false },
      onConfirm = { point ->
        latitude = String.format(Locale.US, "%.6f", point.latitude)
        longitude = String.format(Locale.US, "%.6f", point.longitude)
        showMapPicker = false
      }
    )
  }
}

@Composable
private fun ShopLocationPickerDialog(
  initialLatitude: Double?,
  initialLongitude: Double?,
  onDismiss: () -> Unit,
  onConfirm: (GeoPoint) -> Unit
) {
  val context = LocalContext.current
  val fusedLocation = remember { LocationServices.getFusedLocationProviderClient(context) }
  val startPoint = remember {
    if (initialLatitude != null && initialLongitude != null &&
      initialLatitude in -90.0..90.0 && initialLongitude in -180.0..180.0
    ) GeoPoint(initialLatitude, initialLongitude) else GeoPoint(0.0, 0.0)
  }
  var selectedPoint by remember { mutableStateOf(startPoint) }
  var hasSelectedPoint by remember { mutableStateOf(initialLatitude != null && initialLongitude != null && initialLatitude in -90.0..90.0 && initialLongitude in -180.0..180.0 && !(initialLatitude == 0.0 && initialLongitude == 0.0)) }
  var mapView by remember { mutableStateOf<MapView?>(null) }
  var locationMessage by remember { mutableStateOf("Move the map and tap your shop location to place the pin.") }

  fun placePin(point: GeoPoint) {
    selectedPoint = point
    hasSelectedPoint = point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0 && !(point.latitude == 0.0 && point.longitude == 0.0)
    mapView?.let { map ->
      map.overlays.filterIsInstance<Marker>().forEach { map.overlays.remove(it) }
      val marker = Marker(map).apply {
        position = point
        title = "Shop location"
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
      }
      map.overlays.add(marker)
      map.controller.animateTo(point)
      map.invalidate()
    }
  }

  val locationPermission = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    if (granted) {
      try {
        fusedLocation.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
          .addOnSuccessListener { location: Location? ->
            if (location != null) {
              placePin(GeoPoint(location.latitude, location.longitude))
              locationMessage = "Current device location selected. Adjust the pin if needed."
            } else locationMessage = "Current location unavailable. Move the map and tap your shop location."
          }
          .addOnFailureListener { locationMessage = it.message ?: "Could not get current location." }
      } catch (_: SecurityException) {
        locationMessage = "Location permission is required to use current location."
      }
    } else locationMessage = "Location permission denied. You can still select a location manually on the map."
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp)) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Choose shop location", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Drag the map and tap the exact shop location. The pin will be saved automatically.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        AndroidView(
          modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp).aspectRatio(1.05f),
          factory = { ctx ->
            Configuration.getInstance().userAgentValue = ctx.packageName
            MapView(ctx).apply {
              setTileSource(TileSourceFactory.MAPNIK)
              setMultiTouchControls(true)
              controller.setZoom(if (hasSelectedPoint) 16.0 else 2.5)
              controller.setCenter(startPoint)
              val receiver = object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                  placePin(point)
                  locationMessage = "Location pin selected."
                  return true
                }
                override fun longPressHelper(point: GeoPoint): Boolean = false
              }
              overlays.add(MapEventsOverlay(receiver))
              mapView = this
              if (initialLatitude != null && initialLongitude != null) placePin(startPoint)
            }
          },
          update = { map -> mapView = map }
        )
        Text(locationMessage, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
          onClick = {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fine || coarse) {
              try {
                fusedLocation.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
                  .addOnSuccessListener { location ->
                    if (location != null) {
                      placePin(GeoPoint(location.latitude, location.longitude))
                      locationMessage = "Current device location selected. Adjust the pin if needed."
                    } else locationMessage = "Current location unavailable. Move the map and tap your shop location."
                  }
                  .addOnFailureListener { locationMessage = it.message ?: "Could not get current location." }
              } catch (_: SecurityException) { locationMessage = "Location permission is required." }
            } else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
          },
          modifier = Modifier.fillMaxWidth()
        ) { Text("Use my current location") }
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
          Button(onClick = { onConfirm(selectedPoint) }, enabled = hasSelectedPoint, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) { Text("Use this location") }
        }
      }
    }
  }
  DisposableEffect(Unit) {
    onDispose { mapView?.onDetach(); mapView = null }
  }
}

@Composable
private fun VendorField(label: String, value: String, minLines: Int = 1, onChange: (String) -> Unit) {
  OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), minLines = minLines, singleLine = minLines == 1)
}
@Composable
private fun DocumentButton(label: String, uploaded: Boolean, onClick: () -> Unit) {
  OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(if (uploaded) "✓ $label uploaded" else "Upload $label") }
}

@Composable
private fun VendorDashboardHero(shopName: String, onRefresh: () -> Unit, refreshing: Boolean) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF17243A))
  ) {
    Box(Modifier.fillMaxWidth()) {
      Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(color = Color(0x33FFFFFF), shape = RoundedCornerShape(50)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
              Box(Modifier.size(7.dp).background(Color(0xFF58D68D), RoundedCornerShape(50)))
              Spacer(Modifier.width(7.dp))
              Text("SELLER ACCOUNT ACTIVE", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
          }
          Spacer(Modifier.weight(1f))
          IconButton(onClick = onRefresh, enabled = !refreshing) {
            if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            else Icon(Icons.Default.Refresh, contentDescription = "Refresh dashboard", tint = Color.White)
          }
        }
        Text("Welcome to your shop", color = Color(0xFFFFC49E), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(shopName.ifBlank { "Vendor Dashboard" }, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text("Manage products, prepare orders and keep your business moving.", color = Color(0xFFD5DDEA), style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}

@Composable
private fun VendorDashboardTabs(selected: String, onSelect: (String) -> Unit) {
  val tabs = listOf(
    "overview" to ("Overview" to Icons.Default.Storefront),
    "listings" to ("Products" to Icons.Default.ShoppingBag),
    "inventory" to ("Inventory" to Icons.Default.Inventory2),
    "fulfilment" to ("Orders" to Icons.Default.LocalShipping),
    "tools" to ("More tools" to Icons.Default.TrendingUp)
  )
  ScrollableTabRow(
    selectedTabIndex = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0),
    edgePadding = 0.dp,
    containerColor = Color.White,
    contentColor = JTOrange,
    indicator = {},
    divider = {}
  ) {
    tabs.forEach { item ->
      val active = selected == item.first
      Surface(
        onClick = { onSelect(item.first) },
        shape = RoundedCornerShape(14.dp),
        color = if (active) Color(0xFFFFEEE4) else Color(0xFFF5F7FA),
        modifier = Modifier.padding(end = 8.dp)
      ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
          Icon(item.second.second, null, tint = if (active) JTOrange else Color(0xFF718096), modifier = Modifier.size(17.dp))
          Text(item.second.first, color = if (active) JTOrange else Color(0xFF526176), fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, style = MaterialTheme.typography.labelLarge)
        }
      }
    }
  }
}

@Composable
private fun VendorMetricCard(title: String, value: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE9EDF3))
  ) {
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
      Box(Modifier.size(36.dp).background(Color(0xFFFFF0E7), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = JTOrange, modifier = Modifier.size(19.dp))
      }
      Text(value, color = Color(0xFF17243A), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
      Text(title, color = Color(0xFF26354A), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      Text(detail, color = Color(0xFF8290A2), style = MaterialTheme.typography.labelSmall)
    }
  }
}

@Composable
private fun VendorEmptyState(title: String, description: String, action: String, onAction: () -> Unit) {
  Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F9FC))) {
    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon(Icons.Default.Inventory2, null, tint = Color(0xFF9AA7B8), modifier = Modifier.size(34.dp))
      Text(title, color = Color(0xFF25344A), fontWeight = FontWeight.Bold)
      Text(description, color = Color(0xFF718096), style = MaterialTheme.typography.bodySmall)
      TextButton(onClick = onAction) { Text(action, color = JTOrange, fontWeight = FontWeight.Bold) }
    }
  }
}

@Composable
private fun VendorCompactProductCard(product: JSONObject) {
  Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE9EDF3))) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Box(Modifier.size(46.dp).background(Color(0xFFFFF0E7), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Inventory2, null, tint = JTOrange)
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(product.optString("name", "Product"), color = Color(0xFF25344A), fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text("PKR ${product.optString("price", "0")}", color = Color(0xFF718096), style = MaterialTheme.typography.bodySmall)
      }
      Surface(color = Color(0xFFEAF8F0), shape = RoundedCornerShape(50)) {
        Text(product.optString("publication_status", product.optString("status", "draft")).replace('_', ' '), modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Color(0xFF187548), style = MaterialTheme.typography.labelSmall)
      }
    }
  }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
  Card(modifier) { Column(Modifier.padding(16.dp)) { Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(label, color = Color.Gray) } }
}

@Composable
private fun PendingVendorApplicationScreen(
  application: VendorApplication,
  onRefresh: () -> Unit,
  refreshing: Boolean
) {
  val navy = Color(0xFF14233D)
  val muted = Color(0xFF68778D)
  val orange = Color(0xFFF36B16)
  val submittedDate = remember(application.createdAt) {
    runCatching {
      java.time.Instant.parse(application.createdAt)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))
    }.getOrElse { "Recently" }
  }

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("Seller application", color = orange, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
      Text(application.shopName, color = navy, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
      Text("Your application is in process. Our marketplace team is verifying your shop details.", color = muted, style = MaterialTheme.typography.bodyMedium)
    }

    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8F3)),
      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE2D1))
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Box(
          modifier = Modifier.size(76.dp).background(Color(0xFFFFE8D9), RoundedCornerShape(24.dp)),
          contentAlignment = Alignment.Center
        ) {
          Text("◷", color = orange, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        }
        Surface(color = Color(0xFFFFEAD7), shape = RoundedCornerShape(50)) {
          Text("PENDING REVIEW", modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp), color = Color(0xFF9A4B0C), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Text("Application Under Review", color = navy, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
          "Thank you for applying to become a JTBazar seller. Your application has been received and is currently being verified by our marketplace team.",
          color = muted,
          style = MaterialTheme.typography.bodyMedium
        )
      }
    }

    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8ECF2))
    ) {
      Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Application progress", color = navy, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        VendorProgressStep("✓", "Application received", "Your shop details have been submitted successfully.", true, Color(0xFF16803C), navy, muted)
        VendorProgressStep("◷", "Verification in progress", "Our marketplace team is reviewing your shop information.", true, orange, navy, muted)
        VendorProgressStep("▣", "Seller access", "We’ll email you when your seller dashboard is ready.", false, muted, navy, muted)
      }
    }

    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F7FC))
    ) {
      Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.Top) {
        Text("ⓘ", color = Color(0xFF35679C), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
          "If we need any additional information or your application is not approved, we will notify you by email.",
          color = Color(0xFF42566F),
          style = MaterialTheme.typography.bodyMedium
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth().background(Color(0xFFF8F9FB), RoundedCornerShape(12.dp)).padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text("Submitted", color = muted, style = MaterialTheme.typography.bodyMedium)
      Text(submittedDate, color = navy, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }

    Button(
      onClick = onRefresh,
      enabled = !refreshing,
      modifier = Modifier.fillMaxWidth(),
      colors = ButtonDefaults.buttonColors(containerColor = orange),
      shape = RoundedCornerShape(12.dp)
    ) {
      if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
      else Text("Refresh application status", fontWeight = FontWeight.Bold)
    }
    Text(
      "Your seller tools will become available here once your application is approved.",
      modifier = Modifier.fillMaxWidth(),
      color = muted,
      style = MaterialTheme.typography.bodySmall
    )
  }
}

@Composable
private fun VendorProgressStep(
  symbol: String,
  title: String,
  description: String,
  active: Boolean,
  symbolColor: Color,
  titleColor: Color,
  mutedColor: Color
) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
    Box(
      modifier = Modifier.size(34.dp).background(if (active) Color(0xFFFFF0E7) else Color(0xFFF0F2F5), RoundedCornerShape(50)),
      contentAlignment = Alignment.Center
    ) {
      Text(symbol, color = symbolColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(title, color = titleColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
      Text(description, color = mutedColor, style = MaterialTheme.typography.bodySmall)
    }
  }
}

@Composable
private fun StatusPanel(app: VendorApplication) {
  val color = when (app.status.lowercase()) { "approved" -> Color(0xFF16803C); "rejected" -> Color(0xFFB91C1C); else -> Color(0xFFB7791F) }
  Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(app.shopName, fontWeight = FontWeight.Bold); Text("Application status: ${app.status.uppercase()}", color = color, fontWeight = FontWeight.Bold) } }
}


private fun normalizePakistaniPhone(input: String): String {
  var value = input.trim().replace(Regex("[\\s()\\-]"), "")
  if (value.startsWith("0092")) value = "+92" + value.removePrefix("0092")
  if (value.startsWith("92") && !value.startsWith("+92")) value = "+$value"
  if (value.startsWith("+920")) value = "+92" + value.removePrefix("+920")
  if (value.matches(Regex("^3[0-9]{9}$"))) value = "0$value"
  return value
}

private fun vendorLoadErrorMessage(error: Throwable): String {
  val details = generateSequence(error) { it.cause }
    .mapNotNull { it.message }
    .joinToString(" ")
    .lowercase()
  return when {
    details.contains("unable to resolve host") || details.contains("unknownhostexception") || details.contains("no address associated with hostname") ->
      "JTBazar server ka address abhi resolve nahi ho raha. Ye DNS ya internet network ka masla ho sakta hai. Wi-Fi/mobile data check karein aur dobara koshish karein."
    details.contains("timeout") || details.contains("timed out") ->
      "JTBazar server se response nahi mila. Internet connection check karke dobara koshish karein."
    details.contains("failed to connect") || details.contains("network is unreachable") ->
      "Internet connection available nahi lag raha. Network check karke dobara koshish karein."
    else -> "Vendor information load nahi ho saki. Dobara koshish karein."
  }
}
