package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.supabase.VendorApplication
import com.example.data.supabase.VendorService
import com.example.ui.theme.JTOrange
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun VendorAdvancedPanel(application: VendorApplication, products: JSONArray, onRefresh: suspend () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var saving by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }
  var message by remember { mutableStateOf("") }
  var shopOpen by remember { mutableStateOf(false) }
  var shopName by remember { mutableStateOf(application.shopName) }
  var slug by remember { mutableStateOf(application.slug) }
  var description by remember { mutableStateOf(application.description) }
  var phone by remember { mutableStateOf(application.phone) }
  var currentAddress by remember { mutableStateOf(application.currentAddress) }
  var completeAddress by remember { mutableStateOf(application.completeAddress) }
  var locationText by remember { mutableStateOf(application.shopLocationText) }
  var imageProductId by remember { mutableStateOf<String?>(null) }
  var variantProductId by remember { mutableStateOf<String?>(null) }
  var variantLabel by remember { mutableStateOf("Size") }
  var variantValue by remember { mutableStateOf("") }
  var variantPrice by remember { mutableStateOf("") }
  var variantStock by remember { mutableStateOf("0") }
  var cases by remember { mutableStateOf(JSONArray()) }
  var tickets by remember { mutableStateOf(JSONArray()) }
  var subject by remember { mutableStateOf("") }
  var ticketMessage by remember { mutableStateOf("") }
  var responseText by remember { mutableStateOf("") }

  fun reload() {
    scope.launch {
      try { cases = VendorService.listCases(); tickets = VendorService.listSupportTickets() }
      catch (e: Exception) { errorMessage = e.message ?: "Vendor information could not be loaded." }
    }
  }
  LaunchedEffect(application.id) { reload() }

  val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    val productId = imageProductId
    if (uri != null && productId != null) scope.launch {
      saving = true; errorMessage = ""; message = ""
      try {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Image could not be read.")
        VendorService.uploadProductImage(productId, bytes, mime, "JTBazar product image")
        message = "Product image uploaded successfully."
        onRefresh()
      } catch (e: Exception) { errorMessage = e.message ?: "Image upload failed." }
      finally { saving = false; imageProductId = null }
    }
  }

  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (errorMessage.isNotBlank()) Text(errorMessage, color = MaterialTheme.colorScheme.error)
    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
    OutlinedButton(onClick = { shopOpen = !shopOpen }, modifier = Modifier.fillMaxWidth()) {
      Text(if (shopOpen) "Close shop settings" else "Edit shop details")
    }
    if (shopOpen) {
      VendorAdvancedField("Shop name", shopName) { shopName = it }
      VendorAdvancedField("Shop URL slug", slug) { slug = it }
      VendorAdvancedField("Shop description", description, 3) { description = it }
      VendorAdvancedField("Business phone", phone) { phone = it }
      VendorAdvancedField("Current address", currentAddress) { currentAddress = it }
      VendorAdvancedField("Complete address", completeAddress, 2) { completeAddress = it }
      VendorAdvancedField("Shop location / verification address", locationText, 2) { locationText = it }
      Button(enabled = !saving, onClick = {
        scope.launch {
          saving = true; errorMessage = ""; message = ""
          try {
            require(shopName.trim().length >= 2) { "Enter a valid shop name." }
            require(slug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) { "Use a valid URL slug." }
            require(description.trim().length >= 10 && currentAddress.trim().length >= 3 && completeAddress.trim().length >= 10 && locationText.trim().length >= 10) { "Complete all shop details." }
            require(phone.matches(Regex("^(03[0-9]{9}|\\+923[0-9]{9})$"))) { "Enter a valid Pakistani mobile number." }
            VendorService.updateOwnShop(JSONObject().put("shop_name", shopName.trim()).put("slug", slug.trim())
              .put("description", description.trim()).put("phone", phone.trim()).put("current_address", currentAddress.trim())
              .put("complete_address", completeAddress.trim()).put("shop_location_text", locationText.trim()))
            message = "Shop details updated."; shopOpen = false
          } catch (e: Exception) { errorMessage = e.message ?: "Shop details could not be saved." }
          finally { saving = false }
        }
      }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange), modifier = Modifier.fillMaxWidth()) { Text("Save shop details") }
    }

    Text("Product images, variants and inventory", style = MaterialTheme.typography.titleMedium)
    for (i in 0 until products.length()) {
      val product = products.optJSONObject(i) ?: continue
      val id = product.optString("id")
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(product.optString("name", "Product"), style = MaterialTheme.typography.titleSmall)
          Text("Status: ${product.optString("status", "draft")} • PKR ${product.optString("price", "0")}")
          OutlinedButton(enabled = !saving, onClick = { imageProductId = id; imagePicker.launch("image/*") }) { Text("Upload product image") }
          var stock by remember(id) { mutableStateOf(product.optString("stock_quantity", product.optString("quantity", "0"))) }
          Row {
            OutlinedTextField(value = stock, onValueChange = { stock = it.filter(Char::isDigit).take(8) },
              label = { Text("Stock quantity") }, modifier = Modifier.weight(1f), singleLine = true)
            Spacer(Modifier.width(8.dp))
            Button(enabled = !saving, onClick = {
              scope.launch {
                saving = true
                try {
                  val qty = stock.toIntOrNull() ?: error("Enter a valid stock quantity.")
                  VendorService.setInventory(id, qty); message = "Inventory updated."
                } catch (e: Exception) { errorMessage = e.message ?: "Inventory update failed." }
                finally { saving = false }
              }
            }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) { Text("Save") }
          }
          TextButton(onClick = {
            variantProductId = if (variantProductId == id) null else id
            variantValue = ""; variantPrice = ""; variantStock = "0"
          }) { Text(if (variantProductId == id) "Close variant editor" else "Add product variant") }
          if (variantProductId == id) {
            VendorAdvancedField("Option name (Size, Colour, etc.)", variantLabel) { variantLabel = it }
            VendorAdvancedField("Option value", variantValue) { variantValue = it }
            VendorAdvancedField("Price override (optional PKR)", variantPrice) { variantPrice = it }
            VendorAdvancedField("Variant stock", variantStock) { variantStock = it.filter(Char::isDigit) }
            Button(enabled = !saving, onClick = {
              scope.launch {
                saving = true; errorMessage = ""
                try {
                  require(variantLabel.isNotBlank() && variantValue.isNotBlank()) { "Enter a variant option and value." }
                  val qty = variantStock.toIntOrNull() ?: error("Enter valid variant stock.")
                  val existing = VendorService.listVariants(id)
                  val inventory = VendorService.listInventory()
                  val rows = JSONArray()
                  for (n in 0 until existing.length()) {
                    val old = existing.getJSONObject(n)
                    var oldQty = 0
                    for (j in 0 until inventory.length()) {
                      val inv = inventory.optJSONObject(j) ?: continue
                      if (inv.optString("variant_id") == old.optString("id")) { oldQty = inv.optInt("quantity", 0); break }
                    }
                    rows.put(JSONObject().put("id", old.optString("id")).put("option_label", old.optString("option_label"))
                      .put("option_value", old.optString("option_value")).put("sku", old.opt("sku") ?: JSONObject.NULL)
                      .put("price_override", old.opt("price_override") ?: JSONObject.NULL)
                      .put("attributes", old.optJSONObject("attributes") ?: JSONObject()).put("quantity", oldQty))
                  }
                  rows.put(JSONObject().put("id", JSONObject.NULL).put("option_label", variantLabel.trim())
                    .put("option_value", variantValue.trim()).put("sku", JSONObject.NULL)
                    .put("price_override", variantPrice.toIntOrNull() ?: JSONObject.NULL)
                    .put("attributes", JSONObject()).put("quantity", qty))
                  VendorService.saveVariants(id, rows); message = "Variant saved."; variantProductId = null
                } catch (e: Exception) { errorMessage = e.message ?: "Variant could not be saved." }
                finally { saving = false }
              }
            }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) { Text("Save variant") }
          }
        }
      }
    }

    Text("Customer cases and complaints", style = MaterialTheme.typography.titleMedium)
    if (cases.length() == 0) Text("No marketplace cases found.")
    for (i in 0 until cases.length()) {
      val item = cases.optJSONObject(i) ?: continue
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
          Text("${item.optString("case_type", "Case")} • ${item.optString("status", "open")}")
          Text(item.optString("reason", "No reason supplied."))
          if (item.optString("seller_response").isNotBlank()) Text("Your response: ${item.optString("seller_response")}")
          OutlinedTextField(value = responseText, onValueChange = { responseText = it }, label = { Text("Reply to case") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
          Button(enabled = !saving, onClick = {
            scope.launch {
              saving = true
              try { VendorService.respondToCase(item.optString("id"), responseText); responseText = ""; cases = VendorService.listCases(); message = "Case response sent." }
              catch (e: Exception) { errorMessage = e.message ?: "Case response failed." }
              finally { saving = false }
            }
          }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) { Text("Send response") }
        }
      }
    }

    Text("Vendor support", style = MaterialTheme.typography.titleMedium)
    VendorAdvancedField("Ticket subject", subject) { subject = it }
    VendorAdvancedField("Describe your issue", ticketMessage, 3) { ticketMessage = it }
    Button(enabled = !saving, onClick = {
      scope.launch {
        saving = true
        try { VendorService.createSupportTicket(subject, ticketMessage); subject = ""; ticketMessage = ""; tickets = VendorService.listSupportTickets(); message = "Support ticket created." }
        catch (e: Exception) { errorMessage = e.message ?: "Support ticket could not be created." }
        finally { saving = false }
      }
    }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange), modifier = Modifier.fillMaxWidth()) { Text("Create support ticket") }
    for (i in 0 until tickets.length()) {
      val ticket = tickets.optJSONObject(i) ?: continue
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
          Text("${ticket.optString("subject")} • ${ticket.optString("status", "open")}")
          Text(ticket.optString("message"), style = MaterialTheme.typography.bodySmall)
          if (ticket.optString("admin_reply").isNotBlank()) Text("Admin reply: ${ticket.optString("admin_reply")}")
        }
      }
    }
    OutlinedButton(onClick = { reload() }, modifier = Modifier.fillMaxWidth()) { Text("Refresh vendor tools") }
  }
}

@Composable
fun VendorApplicationResubmissionPanel(application: VendorApplication, onResubmitted: (VendorApplication) -> Unit) {
  val scope = rememberCoroutineScope()
  var shopName by remember { mutableStateOf(application.shopName) }
  var slug by remember { mutableStateOf(application.slug) }
  var description by remember { mutableStateOf(application.description) }
  var ownerName by remember { mutableStateOf(application.ownerFullName) }
  var cnic by remember { mutableStateOf(application.cnicNumber) }
  var phone by remember { mutableStateOf(application.phone) }
  var currentAddress by remember { mutableStateOf(application.currentAddress) }
  var completeAddress by remember { mutableStateOf(application.completeAddress) }
  var locationText by remember { mutableStateOf(application.shopLocationText) }
  var saving by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }
  var message by remember { mutableStateOf("") }
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    VendorAdvancedField("Shop name", shopName) { shopName = it }
    VendorAdvancedField("Shop URL slug", slug) { slug = it }
    VendorAdvancedField("Shop description", description, 3) { description = it }
    VendorAdvancedField("Owner full legal name", ownerName) { ownerName = it }
    VendorAdvancedField("CNIC number (13 digits)", cnic) { cnic = it.filter(Char::isDigit).take(13) }
    VendorAdvancedField("Business phone", phone) { phone = it }
    VendorAdvancedField("Current address", currentAddress) { currentAddress = it }
    VendorAdvancedField("Complete address", completeAddress, 2) { completeAddress = it }
    VendorAdvancedField("Shop location / verification address", locationText, 2) { locationText = it }
    if (errorMessage.isNotBlank()) Text(errorMessage, color = MaterialTheme.colorScheme.error)
    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
    Button(enabled = !saving, onClick = {
      scope.launch {
        saving = true; errorMessage = ""; message = ""
        try {
          require(shopName.trim().length >= 2 && description.trim().length >= 10 && ownerName.trim().length >= 3) { "Complete shop and owner details." }
          require(slug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) { "Enter a valid URL slug." }
          require(cnic.length == 13 && phone.matches(Regex("^(03[0-9]{9}|\\+923[0-9]{9})$"))) { "Check CNIC and Pakistani mobile number." }
          require(currentAddress.trim().length >= 3 && completeAddress.trim().length >= 10 && locationText.trim().length >= 10) { "Complete all address fields." }
          VendorService.updateOwnShop(JSONObject().put("shop_name", shopName.trim()).put("slug", slug.trim())
            .put("description", description.trim()).put("owner_full_name", ownerName.trim()).put("cnic_number", cnic)
            .put("phone", phone.trim()).put("current_address", currentAddress.trim())
            .put("complete_address", completeAddress.trim()).put("shop_location_text", locationText.trim()))
          val updated = VendorService.resubmitApplication()
          message = "Application resubmitted for review."
          onResubmitted(updated)
        } catch (e: Exception) { errorMessage = e.message ?: "Application could not be resubmitted." }
        finally { saving = false }
      }
    }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange), modifier = Modifier.fillMaxWidth()) {
      Text(if (saving) "Submitting..." else "Save and resubmit application")
    }
  }
}

@Composable
private fun VendorAdvancedField(label: String, value: String, lines: Int = 1, onChange: (String) -> Unit) {
  OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), minLines = lines, singleLine = lines == 1)
}
