package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.supabase.VendorApplication
import com.example.data.supabase.VendorService
import com.example.data.supabase.SupabaseClient
import com.example.ui.theme.JTOrange
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.osmdroid.util.GeoPoint

@Composable
fun VendorShopSettingsScreen(application: VendorApplication, accountEmail: String, onSaved: suspend () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val currentUser by SupabaseClient.currentUser.collectAsState()
  val resolvedEmail = accountEmail.ifBlank { application.contactEmail }
  var shopName by remember(application.id) { mutableStateOf(application.shopName) }
  var slug by remember(application.id) { mutableStateOf(application.slug) }
  var vendorType by remember(application.id) { mutableStateOf(application.vendorType) }
  var ownerName by remember(application.id) { mutableStateOf(application.ownerFullName) }
  var cnic by remember(application.id) { mutableStateOf(application.cnicNumber) }
  var phone by remember(application.id) { mutableStateOf(application.phone) }
  var description by remember(application.id) { mutableStateOf(application.description) }
  var currentAddress by remember(application.id) { mutableStateOf(application.currentAddress) }
  var completeAddress by remember(application.id) { mutableStateOf(application.completeAddress) }
  var locationText by remember(application.id) { mutableStateOf(application.shopLocationText) }
  var locationLabel by remember(application.id) { mutableStateOf(application.shopLocationText) }
  var latitude by remember(application.id) { mutableStateOf(application.shopLat?.toString().orEmpty()) }
  var longitude by remember(application.id) { mutableStateOf(application.shopLng?.toString().orEmpty()) }
  var showOnMap by remember(application.id) { mutableStateOf(application.showShopOnMap) }
  var shopImageUri by remember(application.id) { mutableStateOf<Uri?>(null) }
  var shopImageUrl by remember(application.id) { mutableStateOf(application.shopImageUrl) }
  var documentPath by remember(application.id) { mutableStateOf(application.documentPath) }
  var frontPath by remember(application.id) { mutableStateOf(application.cnicFrontPath) }
  var backPath by remember(application.id) { mutableStateOf(application.cnicBackPath) }
  var documentKind by remember { mutableStateOf("verification") }
  var mapOpen by remember { mutableStateOf(false) }
  var saving by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }
  var info by remember { mutableStateOf("") }

  val shopPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { shopImageUri = it }
  val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri != null) scope.launch {
      saving = true; errorMessage = ""; info = ""
      try {
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        require(mime in listOf("application/pdf", "image/jpeg", "image/png", "image/webp")) { "Use PDF, JPG, PNG or WebP files." }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Selected file could not be read.")
        require(bytes.size <= 10 * 1024 * 1024) { "Verification files must be 10 MB or smaller." }
        val ext = when (mime) { "application/pdf" -> "pdf"; "image/jpeg" -> "jpg"; "image/png" -> "png"; else -> "webp" }
        val ownerId = currentUser?.id ?: error("Please sign in again.")
        val path = "${ownerId}/${documentKind}-${System.currentTimeMillis()}.$ext"
        val uploaded = VendorService.uploadPrivateDocument(path, bytes, mime)
        when (documentKind) { "cnic-front" -> frontPath = uploaded; "cnic-back" -> backPath = uploaded; else -> documentPath = uploaded }
        info = "Private verification file uploaded securely."
      } catch (e: Exception) { errorMessage = e.message ?: "Verification file upload failed." }
      finally { saving = false }
    }
  }

  Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("Shop settings", style = MaterialTheme.typography.headlineSmall)
    Text("Update the public details and private verification information associated with your seller account.", style = MaterialTheme.typography.bodySmall)
    ShopSettingsField("Shop name *", shopName) { shopName = it }
    ShopSettingsField("Shop URL slug *", slug) { slug = it.lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-') }
    OutlinedTextField(resolvedEmail, {}, label = { Text("Account email *") }, readOnly = true, modifier = Modifier.fillMaxWidth())
    VendorTypeDropdown(vendorType) { vendorType = it }
    ShopSettingsField("Owner's full legal name *", ownerName) { ownerName = it }
    ShopSettingsField("CNIC number *", cnic) { cnic = it.filter(Char::isDigit).take(13) }
    ShopSettingsField("Business phone *", phone) { phone = it }
    ShopSettingsField("Shop description *", description, 4) { description = it }
    ShopSettingsField("Current address *", currentAddress) { currentAddress = it }
    ShopSettingsField("Complete address *", completeAddress, 2) { completeAddress = it }

    Text("Public shop picture", style = MaterialTheme.typography.titleMedium)
    OutlinedButton(onClick = { shopPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
      Text(if (shopImageUri == null && shopImageUrl.isBlank()) "Choose shop picture" else "Change shop picture")
    }
    val previewModel: Any? = shopImageUri ?: shopImageUrl.takeIf { it.isNotBlank() }
    if (previewModel != null) {
      AsyncImage(
        model = previewModel,
        contentDescription = "Shop picture preview",
        modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop
      )
    }
    Text("Shown on your public shop page. JPG, PNG, WebP or AVIF up to 5 MB.", style = MaterialTheme.typography.bodySmall)

    HorizontalDivider()
    Text("Explore map location", style = MaterialTheme.typography.titleMedium)
    ShopSettingsField("Public area label", locationLabel) { locationLabel = it }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
      Checkbox(showOnMap, { showOnMap = it })
      Text("Show my approximate shop location to customers on Explore")
    }
    Text(if (latitude.isNotBlank() && longitude.isNotBlank()) "Map pin saved: $latitude, $longitude" else "A shop map pin is required for administrator verification.", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { mapOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Choose / update shop map pin") }
    ShopSettingsField("Shop location / complete address for admin verification *", locationText, 2) { locationText = it }

    HorizontalDivider()
    Text("Private verification documents", style = MaterialTheme.typography.titleMedium)
    Text("Only authorized JTBazar marketplace administrators can open these files.", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { documentKind = "cnic-front"; documentPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (frontPath.isBlank()) "Upload CNIC front *" else "Replace saved CNIC front") }
    OutlinedButton(onClick = { documentKind = "cnic-back"; documentPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (backPath.isBlank()) "Upload CNIC back *" else "Replace saved CNIC back") }
    OutlinedButton(onClick = { documentKind = "verification"; documentPicker.launch("*/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (documentPath.isBlank()) "Upload identity / business document *" else "Replace saved verification document") }
    Text("CNIC front: ${if (frontPath.isBlank()) "Not uploaded" else "Saved"} • CNIC back: ${if (backPath.isBlank()) "Not uploaded" else "Saved"} • Verification: ${if (documentPath.isBlank()) "Not uploaded" else "Saved"}", style = MaterialTheme.typography.bodySmall)

    if (errorMessage.isNotBlank()) Text(errorMessage, color = MaterialTheme.colorScheme.error)
    if (info.isNotBlank()) Text(info, color = MaterialTheme.colorScheme.primary)
    Button(enabled = !saving, onClick = {
      scope.launch {
        saving = true; errorMessage = ""; info = ""
        try {
          require(shopName.trim().length >= 2 && slug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) { "Enter a valid shop name and URL slug." }
          require(resolvedEmail.isNotBlank() && resolvedEmail.trim().equals(currentUser?.email?.trim(), true)) { "Use the same verified email as your signed-in JTBazar account." }
          require(ownerName.trim().length >= 3 && cnic.matches(Regex("[0-9]{13}"))) { "Enter the owner's legal name and 13-digit CNIC." }
          require(phone.matches(Regex("^(03[0-9]{9}|\\+923[0-9]{9})$"))) { "Enter a valid Pakistani mobile number." }
          require(description.trim().length >= 10 && currentAddress.trim().length >= 3 && completeAddress.trim().length >= 10 && locationText.trim().length >= 10) { "Complete all required shop details." }
          val lat = latitude.toDoubleOrNull() ?: error("Choose the required shop map pin.")
          val lng = longitude.toDoubleOrNull() ?: error("Choose the required shop map pin.")
          require(lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0)) { "Shop map pin is invalid." }
          require(frontPath.isNotBlank() && backPath.isNotBlank() && documentPath.isNotBlank()) { "Upload all three verification documents." }
          if (shopImageUri != null) {
            val uri = shopImageUri!!
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Shop picture could not be read.")
            shopImageUrl = VendorService.uploadShopImage(bytes, mime)
            shopImageUri = null
          }
          VendorService.updateOwnShop(JSONObject()
            .put("shop_name", shopName.trim()).put("slug", slug.trim()).put("contact_email", resolvedEmail.trim())
            .put("vendor_type", vendorType).put("owner_full_name", ownerName.trim()).put("cnic_number", cnic)
            .put("phone", phone.trim()).put("description", description.trim())
            .put("current_address", currentAddress.trim()).put("complete_address", completeAddress.trim())
            .put("shop_location_text", locationText.trim()).put("shop_location_label", locationLabel.trim())
            .put("shop_lat", lat).put("shop_lng", lng).put("show_shop_on_map", showOnMap)
            .put("shop_image_url", shopImageUrl.ifBlank { JSONObject.NULL })
            .put("document_path", documentPath).put("cnic_front_path", frontPath).put("cnic_back_path", backPath))
          info = "Shop settings saved successfully."
          onSaved()
        } catch (e: Exception) { errorMessage = e.message ?: "Shop settings could not be saved." }
        finally { saving = false }
      }
    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) {
      Text(if (saving) "Saving…" else "Save shop settings")
    }
  }

  if (mapOpen) {
    ShopLocationPickerDialog(
      initialLatitude = latitude.toDoubleOrNull(),
      initialLongitude = longitude.toDoubleOrNull(),
      onDismiss = { mapOpen = false },
      onConfirm = { point: GeoPoint ->
        latitude = String.format(java.util.Locale.US, "%.6f", point.latitude)
        longitude = String.format(java.util.Locale.US, "%.6f", point.longitude)
        mapOpen = false
      }
    )
  }
}

@Composable
private fun ShopSettingsField(label: String, value: String, lines: Int = 1, onChange: (String) -> Unit) {
  OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), minLines = lines, singleLine = lines == 1)
}
