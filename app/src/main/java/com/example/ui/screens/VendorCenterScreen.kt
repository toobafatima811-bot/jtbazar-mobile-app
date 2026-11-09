package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
  var saving by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }
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
  var verificationPath by remember { mutableStateOf<String?>(null) }
  var frontPath by remember { mutableStateOf<String?>(null) }
  var backPath by remember { mutableStateOf<String?>(null) }
  var productName by remember { mutableStateOf("") }
  var productDescription by remember { mutableStateOf("") }
  var productPrice by remember { mutableStateOf("") }
  var productStock by remember { mutableStateOf("1") }
  var showProductForm by remember { mutableStateOf(false) }

  fun refresh() {
    scope.launch {
      loading = true
      errorMessage = ""
      try {
        application = VendorService.getOwnApplication()
        isVendor = VendorService.isVendor()
        if (isVendor && application?.status == "approved") {
          products = VendorService.listProducts()
          orders = VendorService.listOrders(ordersPage, ordersSearch, ordersStatus)
          ordersTotal = orders.optJSONObject(0)?.optInt("total_orders", 0) ?: 0
        }
      } catch (e: Exception) { errorMessage = e.message ?: "Vendor information could not be loaded." }
      finally { loading = false }
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
      saving = true; errorMessage = ""; info = ""
      try {
        val path = upload(uri, pendingDoc)
        when (pendingDoc) { "verification" -> verificationPath = path; "cnic-front" -> frontPath = path; "cnic-back" -> backPath = path }
        info = "Document uploaded securely."
      } catch (e: Exception) { errorMessage = e.message ?: "Document upload failed." }
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
        if (info.isNotBlank()) Text(info, color = Color(0xFF16803C))
        when {
          application?.vendorLocked == true -> {
            Text("Vendor account restricted", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            StatusPanel(application!!)
            Text(application?.lockReason ?: "Vendor tools are temporarily unavailable. Contact JTBazar support for details.", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { refresh() }) { Text("Refresh status") }
          }
          isVendor && application?.status == "approved" -> {
            Text("Vendor Dashboard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              SummaryTile("Products", products.length().toString(), Modifier.weight(1f))
              SummaryTile("Order items", orders.length().toString(), Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Inventory2, null, tint = JTOrange)
              Spacer(Modifier.width(8.dp)); Text("Your products", fontWeight = FontWeight.Bold)
              Spacer(Modifier.weight(1f))
              TextButton(onClick = { showProductForm = !showProductForm }) { Icon(Icons.Default.Add, null); Text("Add product") }
            }
            if (showProductForm) {
              VendorField("Product name", productName) { productName = it }
              VendorField("Short and full description", productDescription, minLines = 3) { productDescription = it }
              VendorField("Price (PKR)", productPrice) { productPrice = it }
              VendorField("Initial stock", productStock) { productStock = it }
              Button(enabled = !saving, onClick = {
                scope.launch {
                  saving = true; errorMessage = ""; info = ""
                  try {
                    val name = productName.trim()
                    require(name.length >= 2) { "Enter a product name." }
                    val price = productPrice.toIntOrNull() ?: error("Enter a valid price.")
                    val stock = productStock.toIntOrNull() ?: error("Enter a valid stock quantity.")
                    require(price > 0 && stock >= 0) { "Price must be above zero and stock cannot be negative." }
                    val slug = name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-') + "-" + System.currentTimeMillis().toString().takeLast(5)
                    VendorService.createProduct(name, slug, productDescription, price, stock)
                    productName = ""; productDescription = ""; productPrice = ""; productStock = "1"; showProductForm = false
                    info = "Product submitted. It will follow JTBazar's product review and publishing rules."
                    products = VendorService.listProducts()
                  } catch (e: Exception) { errorMessage = e.message ?: "Product could not be created." }
                  finally { saving = false }
                }
              }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) { Text("Submit product") }
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
                    Text(if (hidden) "Hidden" else "Visible", color = if (hidden) Color.Gray else Color(0xFF16803C))
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                      scope.launch { saving = true; try { VendorService.setProductVisibility(id, !hidden); products = VendorService.listProducts() } catch (e: Exception) { errorMessage = e.message ?: "Visibility update failed." } finally { saving = false } }
                    }) { Text(if (hidden) "Show" else "Hide") }
                  }
                }
              }
            }
            Text("Vendor orders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = ordersSearch, onValueChange = { ordersSearch = it }, label = { Text("Search order number or product") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              listOf("all", "pending", "delivered", "not_delivered").forEach { status ->
                TextButton(onClick = {
                  ordersStatus = status; ordersPage = 0
                  scope.launch {
                    saving = true
                    try { orders = VendorService.listOrders(0, ordersSearch, status); ordersTotal = orders.optJSONObject(0)?.optInt("total_orders", 0) ?: 0 }
                    catch (e: Exception) { errorMessage = e.message ?: "Orders could not be filtered." }
                    finally { saving = false }
                  }
                }) { Text(if (ordersStatus == status) "• ${status.replace('_', ' ')}" else status.replace('_', ' ')) }
              }
            }
            Button(enabled = !saving, onClick = {
              scope.launch {
                saving = true
                try { ordersPage = 0; orders = VendorService.listOrders(0, ordersSearch, ordersStatus); ordersTotal = orders.optJSONObject(0)?.optInt("total_orders", 0) ?: 0 }
                catch (e: Exception) { errorMessage = e.message ?: "Orders could not be searched." }
                finally { saving = false }
              }
            }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) { Text("Search orders") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
              Text("Page ${ordersPage + 1} • ${ordersTotal} orders", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
              Row {
                TextButton(enabled = !saving && ordersPage > 0, onClick = {
                  scope.launch { saving = true; try { ordersPage--; orders = VendorService.listOrders(ordersPage, ordersSearch, ordersStatus) } catch (e: Exception) { errorMessage = e.message ?: "Previous page failed." } finally { saving = false } }
                }) { Text("Previous") }
                TextButton(enabled = !saving && (ordersPage + 1) * 12 < ordersTotal, onClick = {
                  scope.launch { saving = true; try { ordersPage++; orders = VendorService.listOrders(ordersPage, ordersSearch, ordersStatus) } catch (e: Exception) { errorMessage = e.message ?: "Next page failed." } finally { saving = false } }
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
                    scope.launch { saving = true; try { VendorService.markOrderReady(orderId); info = "Marked ready for JTBazar pickup."; orders = VendorService.listOrders(ordersPage, ordersSearch, ordersStatus); ordersTotal = orders.optJSONObject(0)?.optInt("total_orders", 0) ?: 0 } catch (e: Exception) { errorMessage = e.message ?: "Could not update order." } finally { saving = false } }
                  }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) { Text("Ready for pickup") }
                }
              }
            }
            VendorAdvancedPanel(application!!, products) { refresh() }
            OutlinedButton(onClick = { refresh() }) { Text("Refresh dashboard") }
          }
          application != null -> {
            Text("Seller application", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            StatusPanel(application!!)
            Text("Your application is connected to the JTBazar website. Once approved, vendor tools will unlock here.", color = Color.Gray)
            if (application?.status == "rejected") Text("Reason: ${application?.rejectionReason ?: "Please review your application."}", color = MaterialTheme.colorScheme.error)
            if (application?.status == "rejected") VendorApplicationResubmissionPanel(application!!) { application = it; info = "Application resubmitted." }
            OutlinedButton(onClick = { refresh() }) { Text("Refresh application status") }
          }
          else -> {
            Text("Become a JTBazar Vendor", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Submit your shop and identity details. All verification files are stored privately for JTBazar review.", color = Color.Gray)
            VendorField("Shop name", shopName) { shopName = it }
            VendorField("Shop description (at least 10 characters)", description, minLines = 3) { description = it }
            VendorField("Owner full legal name", ownerName) { ownerName = it }
            VendorField("Vendor type (individual_seller / registered_business / sole_proprietor / partnership / company)", vendorType) { vendorType = it }
            VendorField("CNIC number (13 digits, no dashes)", cnic) { cnic = it.filter(Char::isDigit).take(13) }
            VendorField("Contact phone (03XXXXXXXXX)", phone) { phone = it }
            VendorField("Current address", currentAddress) { currentAddress = it }
            VendorField("Complete address", fullAddress, minLines = 2) { fullAddress = it }
            VendorField("Shop location / landmark details", shopLocation, minLines = 2) { shopLocation = it }
            VendorField("Shop latitude (map pin)", latitude) { latitude = it }
            VendorField("Shop longitude (map pin)", longitude) { longitude = it }
            Text("Verification documents", fontWeight = FontWeight.Bold)
            DocumentButton("Identity / business verification document", verificationPath != null) { pendingDoc = "verification"; documentPicker.launch("*/*") }
            DocumentButton("CNIC front image", frontPath != null) { pendingDoc = "cnic-front"; documentPicker.launch("image/*") }
            DocumentButton("CNIC back image", backPath != null) { pendingDoc = "cnic-back"; documentPicker.launch("image/*") }
            Button(enabled = !saving, onClick = {
              scope.launch {
                saving = true; errorMessage = ""; info = ""
                try {
                  val u = user ?: error("Please sign in again.")
                  require(shopName.trim().length >= 2) { "Enter a valid shop name." }
                  require(description.trim().length >= 10) { "Shop description must be at least 10 characters." }
                  require(ownerName.trim().length >= 3) { "Enter the owner's full legal name." }
                  require(cnic.length == 13) { "Enter a 13-digit CNIC number." }
                  require(phone.matches(Regex("^(03[0-9]{9}|\\+923[0-9]{9})$"))) { "Enter a valid Pakistani phone number." }
                  require(currentAddress.trim().length >= 3 && fullAddress.trim().length >= 10 && shopLocation.trim().length >= 10) { "Complete all shop address fields." }
                  val lat = latitude.toDoubleOrNull() ?: error("Enter a valid latitude.")
                  val lng = longitude.toDoubleOrNull() ?: error("Enter a valid longitude.")
                  require(lat in -90.0..90.0 && lng in -180.0..180.0) { "Map coordinates are invalid." }
                  require(verificationPath != null && frontPath != null && backPath != null) { "Upload all three verification documents." }
                  require(u.email?.isNotBlank() == true) { "Your account email is missing." }
                  val slug = shopName.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-') + "-" + System.currentTimeMillis().toString().takeLast(5)
                  val body = JSONObject().put("user_id", u.id).put("shop_name", shopName.trim()).put("slug", slug)
                    .put("description", description.trim()).put("current_address", currentAddress.trim()).put("complete_address", fullAddress.trim())
                    .put("shop_location_label", currentAddress.trim()).put("contact_email", u.email).put("phone", phone.trim())
                    .put("document_path", verificationPath).put("cnic_front_path", frontPath).put("cnic_back_path", backPath)
                    .put("vendor_type", vendorType.trim()).put("owner_full_name", ownerName.trim()).put("cnic_number", cnic)
                    .put("shop_location_text", shopLocation.trim()).put("shop_lat", lat).put("shop_lng", lng).put("show_shop_on_map", true)
                  application = VendorService.submitApplication(body)
                  info = "Application submitted successfully. JTBazar admin review is pending."
                } catch (e: Exception) { errorMessage = e.message ?: "Application could not be submitted." }
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
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
  Card(modifier) { Column(Modifier.padding(16.dp)) { Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(label, color = Color.Gray) } }
}
@Composable
private fun StatusPanel(app: VendorApplication) {
  val color = when (app.status.lowercase()) { "approved" -> Color(0xFF16803C); "rejected" -> Color(0xFFB91C1C); else -> Color(0xFFB7791F) }
  Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(app.shopName, fontWeight = FontWeight.Bold); Text("Application status: ${app.status.uppercase()}", color = color, fontWeight = FontWeight.Bold) } }
}
