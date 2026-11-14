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
import org.osmdroid.util.GeoPoint

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
  var shopImageUrl by remember { mutableStateOf(application.shopImageUrl) }
  var showShopOnMap by remember { mutableStateOf(application.showShopOnMap) }
  var showShopMapPicker by remember { mutableStateOf(false) }
  var shopLat by remember { mutableStateOf(application.shopLat?.toString().orEmpty()) }
  var shopLng by remember { mutableStateOf(application.shopLng?.toString().orEmpty()) }
  var shopIsOpen by remember { mutableStateOf(application.shopIsOpen) }
  var minimumOrderValue by remember { mutableStateOf(application.minimumOrderValue.toString()) }
  var deletionProductId by remember { mutableStateOf<String?>(null) }
  var deletionReason by remember { mutableStateOf("") }
  var deletionRequests by remember { mutableStateOf(JSONArray()) }
  var inventoryHistory by remember { mutableStateOf(JSONArray()) }
  var imageProductId by remember { mutableStateOf<String?>(null) }
  var variantProductId by remember { mutableStateOf<String?>(null) }
  var variantRows by remember { mutableStateOf(JSONArray()) }
  var selectedVariantId by remember { mutableStateOf<String?>(null) }
  var variantLabel by remember { mutableStateOf("Size") }
  var variantValue by remember { mutableStateOf("") }
  var variantPrice by remember { mutableStateOf("") }
  var variantStock by remember { mutableStateOf("0") }
  var cases by remember { mutableStateOf(JSONArray()) }
  var tickets by remember { mutableStateOf(JSONArray()) }
  var subject by remember { mutableStateOf("") }
  var ticketMessage by remember { mutableStateOf("") }
  var responseText by remember { mutableStateOf("") }
  var selectedSupportTicketId by remember { mutableStateOf<String?>(null) }
  var supportMessages by remember { mutableStateOf(JSONArray()) }
  var supportReply by remember { mutableStateOf("") }

  fun reload() {
    scope.launch {
      errorMessage = ""
      val failures = mutableListOf<String>()
      try { cases = VendorService.listCases() } catch (_: Exception) { failures.add("Cases could not be loaded.") }
      try { tickets = VendorService.listSupportTickets() } catch (_: Exception) { failures.add("Support tickets could not be loaded.") }
      try { inventoryHistory = VendorService.listInventoryMovements() } catch (_: Exception) { failures.add("Inventory history could not be loaded.") }
      if (failures.isNotEmpty()) errorMessage = failures.joinToString("\n")
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


  val shopImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    if (uri != null) scope.launch {
      saving = true; errorMessage = ""; message = ""
      try {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Shop image could not be read.")
        shopImageUrl = VendorService.uploadShopImage(bytes, mime)
        VendorService.updateOwnShop(JSONObject().put("shop_image_url", shopImageUrl))
        message = "Shop image updated."
      } catch (e: Exception) { errorMessage = e.message ?: "Shop image upload failed." }
      finally { saving = false }
    }
  }

  if (deletionProductId != null) {
    AlertDialog(
      onDismissRequest = { deletionProductId = null },
      title = { Text("Request product deletion") },
      text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("This sends a deletion request to JTBazar admin. It does not delete the product immediately.")
        OutlinedTextField(value = deletionReason, onValueChange = { deletionReason = it }, label = { Text("Reason") }, minLines = 2)
      } },
      confirmButton = { TextButton(enabled = !saving, onClick = {
        val id = deletionProductId ?: return@TextButton
        scope.launch {
          saving = true
          try {
            VendorService.requestProductDeletion(id, deletionReason)
            deletionReason = ""; deletionProductId = null
            message = "Deletion request submitted for admin review."
          } catch (e: Exception) { errorMessage = e.message ?: "Deletion request failed." }
          finally { saving = false }
        }
      }) { Text("Submit request") } },
      dismissButton = { TextButton(onClick = { deletionProductId = null }) { Text("Cancel") } }
    )
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
      OutlinedButton(enabled = !saving, onClick = { shopImagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (shopImageUrl.isBlank()) "Upload shop picture" else "Change shop picture") }
      Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = showShopOnMap, onCheckedChange = { showShopOnMap = it })
        Text("Show shop on public map")
      }
      if (showShopOnMap) {
        Text(
          if (shopLat.isNotBlank() && shopLng.isNotBlank()) "Pinned shop location: $shopLat, $shopLng" else "Choose the exact shop location on the map.",
          style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(enabled = !saving, onClick = { showShopMapPicker = true }, modifier = Modifier.fillMaxWidth()) {
          Text(if (shopLat.isNotBlank() && shopLng.isNotBlank()) "Change shop location on map" else "Choose shop location on map")
        }
      }
      Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = shopIsOpen, onCheckedChange = { shopIsOpen = it })
        Text("Shop is open for daily food orders")
      }
      VendorAdvancedField("Minimum order value (PKR)", minimumOrderValue) { minimumOrderValue = it.filter(Char::isDigit).take(9) }
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
              .put("complete_address", completeAddress.trim()).put("shop_location_text", locationText.trim())
              .put("shop_image_url", if (shopImageUrl.isBlank()) JSONObject.NULL else shopImageUrl)
              .put("show_shop_on_map", showShopOnMap)
              .put("shop_lat", if (showShopOnMap) shopLat.toDoubleOrNull() ?: error("Enter a valid shop latitude.") else JSONObject.NULL)
              .put("shop_lng", if (showShopOnMap) shopLng.toDoubleOrNull() ?: error("Enter a valid shop longitude.") else JSONObject.NULL)
              .put("shop_location_label", locationText.trim())
              .put("shop_is_open", shopIsOpen)
              .put("minimum_order_value", minimumOrderValue.toIntOrNull() ?: error("Enter a valid minimum order value.")))
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
          var editing by remember(id) { mutableStateOf(false) }
          var editName by remember(id) { mutableStateOf(product.optString("name", "")) }
          var editSlug by remember(id) { mutableStateOf(product.optString("slug", "")) }
          var editSku by remember(id) { mutableStateOf(product.optString("sku", "")) }
          var editCategory by remember(id) { mutableStateOf(product.optString("category_id", "")) }
          var editShortDescription by remember(id) { mutableStateOf(product.optString("short_description", "")) }
          var editDescription by remember(id) { mutableStateOf(product.optString("description", "")) }
          var editPrice by remember(id) { mutableStateOf(product.optString("vendor_base_price", product.optString("price", ""))) }
          var editDiscount by remember(id) { mutableStateOf(product.optString("discount_percent", "0")) }
          var editSupplierCost by remember(id) { mutableStateOf(if (product.isNull("supplier_cost")) "" else product.optString("supplier_cost")) }
          var editFulfilment by remember(id) { mutableStateOf(product.optString("fulfillment_expense", "0")) }
          var editMinimumProfit by remember(id) { mutableStateOf(product.optString("minimum_profit", "0")) }
          var editWeight by remember(id) { mutableStateOf(if (product.isNull("weight_grams")) "" else product.optString("weight_grams")) }
          var editSpecifications by remember(id) { mutableStateOf((product.optJSONArray("specifications") ?: JSONArray()).toString(2)) }
          var editDimensions by remember(id) { mutableStateOf((product.optJSONObject("dimensions") ?: JSONObject()).toString(2)) }
          var editSeoTitle by remember(id) { mutableStateOf(product.optString("seo_title", "")) }
          var editSeoDescription by remember(id) { mutableStateOf(product.optString("seo_description", "")) }
          var editStock by remember(id) { mutableStateOf(product.optString("stock_quantity", product.optString("quantity", "0"))) }
          Text(product.optString("name", "Product"), style = MaterialTheme.typography.titleSmall)
          Text("Status: ${product.optString("status", "draft")} • PKR ${product.optString("price", "0")}")
          TextButton(onClick = { editing = !editing }) { Text(if (editing) "Close product editor" else "Edit product details") }
          if (editing) {
            VendorAdvancedField("Product name", editName) { editName = it }
            VendorAdvancedField("Product URL slug", editSlug) { editSlug = it.lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-') }
            VendorAdvancedField("Product SKU", editSku) { editSku = it }
            VendorAdvancedField("Category ID", editCategory) { editCategory = it }
            VendorAdvancedField("Short description", editShortDescription, 2) { editShortDescription = it }
            VendorAdvancedField("Full description", editDescription, 4) { editDescription = it }
            VendorAdvancedField("Vendor base price (PKR)", editPrice) { editPrice = it.filter { ch -> ch.isDigit() || ch == '.' } }
            VendorAdvancedField("Discount (%)", editDiscount) { editDiscount = it.filter { ch -> ch.isDigit() || ch == '.' } }
            VendorAdvancedField("Supplier cost (private PKR)", editSupplierCost) { editSupplierCost = it.filter { ch -> ch.isDigit() || ch == '.' } }
            VendorAdvancedField("Fulfilment expense (PKR)", editFulfilment) { editFulfilment = it.filter { ch -> ch.isDigit() || ch == '.' } }
            VendorAdvancedField("Minimum profit (PKR)", editMinimumProfit) { editMinimumProfit = it.filter { ch -> ch.isDigit() || ch == '.' } }
            VendorAdvancedField("Shipping weight (grams)", editWeight) { editWeight = it.filter(Char::isDigit) }
            VendorAdvancedField("Specifications (JSON array)", editSpecifications, 3) { editSpecifications = it }
            VendorAdvancedField("Dimensions (JSON object)", editDimensions, 3) { editDimensions = it }
            VendorAdvancedField("SEO title", editSeoTitle) { editSeoTitle = it }
            VendorAdvancedField("SEO description", editSeoDescription, 2) { editSeoDescription = it }
            VendorAdvancedField("Stock quantity", editStock) { editStock = it.filter(Char::isDigit) }
            Button(enabled = !saving, onClick = {
              scope.launch {
                saving = true
                try {
                  val price = editPrice.toDoubleOrNull() ?: error("Enter a valid vendor base price.")
                  val discount = editDiscount.toDoubleOrNull() ?: error("Enter a valid discount.")
                  require(discount in 0.0..100.0) { "Discount must be between 0 and 100%." }
                  val specs = JSONArray(editSpecifications)
                  val dimensions = JSONObject(editDimensions)
                  val payload = JSONObject()
                    .put("p_category_id", editCategory.ifBlank { JSONObject.NULL })
                    .put("p_name", editName.trim()).put("p_slug", editSlug.trim())
                    .put("p_sku", editSku.trim().ifBlank { JSONObject.NULL })
                    .put("p_short_description", editShortDescription.trim())
                    .put("p_description", editDescription.trim()).put("p_price", price)
                    .put("p_supplier_cost", editSupplierCost.toDoubleOrNull() ?: JSONObject.NULL)
                    .put("p_fulfillment_expense", editFulfilment.toDoubleOrNull() ?: error("Enter valid fulfilment expense."))
                    .put("p_minimum_profit", editMinimumProfit.toDoubleOrNull() ?: error("Enter valid minimum profit."))
                    .put("p_discount_percent", discount)
                    .put("p_weight_grams", editWeight.toIntOrNull() ?: JSONObject.NULL)
                    .put("p_specifications", specs).put("p_dimensions", dimensions)
                    .put("p_seo_title", editSeoTitle.trim().ifBlank { JSONObject.NULL })
                    .put("p_seo_description", editSeoDescription.trim().ifBlank { JSONObject.NULL })
                  VendorService.updateProductFull(id, payload)
                  VendorService.setInventory(id, editStock.toIntOrNull() ?: error("Enter valid stock quantity."))
                  message = "Product details saved. JTBazar moderation rules still apply."
                  editing = false
                  onRefresh()
                } catch (e: Exception) { errorMessage = e.message ?: "Product update failed." }
                finally { saving = false }
              }
            }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) { Text("Save all product changes") }
          }
          var dailyItem by remember(id) { mutableStateOf(product.optBoolean("is_daily_item", false)) }
          var dailyQuantity by remember(id) { mutableStateOf(product.optInt("daily_available_quantity", 0).toString()) }
          Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = dailyItem, onCheckedChange = { dailyItem = it })
            Text("Daily food item")
          }
          if (dailyItem) {
            VendorAdvancedField("Available quantity today", dailyQuantity) { dailyQuantity = it.filter(Char::isDigit).take(8) }
            Button(enabled = !saving, onClick = {
              scope.launch {
                saving = true
                try {
                  VendorService.setDailyAvailability(id, true, dailyQuantity.toIntOrNull() ?: error("Enter today's available quantity."), 1, minimumOrderValue.toIntOrNull(), shopIsOpen)
                  message = "Today's food availability updated."
                  onRefresh()
                } catch (e: Exception) { errorMessage = e.message ?: "Daily availability could not be updated." }
                finally { saving = false }
              }
            }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) { Text("Save today's availability") }
          } else if (product.optBoolean("is_daily_item", false)) {
            TextButton(enabled = !saving, onClick = {
              scope.launch {
                saving = true
                try { VendorService.setDailyAvailability(id, false, 0); message = "Daily availability disabled."; onRefresh() }
                catch (e: Exception) { errorMessage = e.message ?: "Availability setting could not be changed." }
                finally { saving = false }
              }
            }) { Text("Disable daily availability") }
          }
          OutlinedButton(enabled = !saving, onClick = { imageProductId = id; imagePicker.launch("image/*") }) { Text("Upload product image") }
          TextButton(enabled = !saving, onClick = { deletionProductId = id; deletionReason = "" }) { Text("Request product deletion") }
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
            if (variantProductId == id) variantProductId = null
            else {
              variantProductId = id
              selectedVariantId = null
              variantValue = ""; variantPrice = ""; variantStock = "0"
              scope.launch { try { variantRows = VendorService.listVariants(id) } catch (e: Exception) { errorMessage = e.message ?: "Variants could not be loaded." } }
            }
          }) { Text(if (variantProductId == id) "Close variant editor" else "Manage variants (add / edit / delete)") }
          if (variantProductId == id) {
            for (v in 0 until variantRows.length()) {
              val row = variantRows.optJSONObject(v) ?: continue
              Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("${row.optString("option_label")}: ${row.optString("option_value")}", modifier = Modifier.weight(1f))
                TextButton(onClick = {
                  selectedVariantId = row.optString("id")
                  variantLabel = row.optString("option_label")
                  variantValue = row.optString("option_value")
                  variantPrice = if (row.isNull("price_override")) "" else row.optString("price_override")
                  scope.launch {
                    try {
                      val inventory = VendorService.listInventory()
                      var quantity = 0
                      for (m in 0 until inventory.length()) {
                        val inv = inventory.optJSONObject(m) ?: continue
                        if (inv.optString("variant_id") == row.optString("id")) { quantity = inv.optInt("quantity"); break }
                      }
                      variantStock = quantity.toString()
                    } catch (e: Exception) { errorMessage = e.message ?: "Variant stock could not be loaded." }
                  }
                }) { Text("Edit") }
                TextButton(enabled = !saving, onClick = {
                  scope.launch {
                    saving = true
                    try {
                      val inventory = VendorService.listInventory()
                      val keep = JSONArray()
                      for (k in 0 until variantRows.length()) {
                        val candidate = variantRows.getJSONObject(k)
                        if (candidate.optString("id") == row.optString("id")) continue
                        var qty = 0
                        for (m in 0 until inventory.length()) {
                          val inv = inventory.optJSONObject(m) ?: continue
                          if (inv.optString("variant_id") == candidate.optString("id")) { qty = inv.optInt("quantity"); break }
                        }
                        keep.put(JSONObject().put("id", candidate.optString("id"))
                          .put("option_label", candidate.optString("option_label")).put("option_value", candidate.optString("option_value"))
                          .put("sku", candidate.opt("sku") ?: JSONObject.NULL).put("price_override", candidate.opt("price_override") ?: JSONObject.NULL)
                          .put("attributes", candidate.optJSONObject("attributes") ?: JSONObject()).put("quantity", qty))
                      }
                      VendorService.saveVariants(id, keep)
                      variantRows = VendorService.listVariants(id)
                      message = "Variant deleted. Product changes may require review."
                    } catch (e: Exception) { errorMessage = e.message ?: "Variant could not be deleted." }
                    finally { saving = false }
                  }
                }) { Text("Delete") }
              }
            }
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
                    val editingThis = selectedVariantId == old.optString("id")
                    rows.put(JSONObject().put("id", old.optString("id"))
                      .put("option_label", if (editingThis) variantLabel.trim() else old.optString("option_label"))
                      .put("option_value", if (editingThis) variantValue.trim() else old.optString("option_value"))
                      .put("sku", old.opt("sku") ?: JSONObject.NULL)
                      .put("price_override", if (editingThis) variantPrice.toIntOrNull() ?: JSONObject.NULL else old.opt("price_override") ?: JSONObject.NULL)
                      .put("attributes", old.optJSONObject("attributes") ?: JSONObject()).put("quantity", if (editingThis) qty else oldQty))
                  }
                  if (selectedVariantId == null) rows.put(JSONObject().put("id", JSONObject.NULL).put("option_label", variantLabel.trim())
                    .put("option_value", variantValue.trim()).put("sku", JSONObject.NULL)
                    .put("price_override", variantPrice.toIntOrNull() ?: JSONObject.NULL)
                    .put("attributes", JSONObject()).put("quantity", qty))
                  VendorService.saveVariants(id, rows); message = if (selectedVariantId == null) "Variant added." else "Variant updated."; selectedVariantId = null; variantProductId = null
                } catch (e: Exception) { errorMessage = e.message ?: "Variant could not be saved." }
                finally { saving = false }
              }
            }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) { Text("Save variant") }
          }
        }
      }
    }

    Text("Product deletion requests", style = MaterialTheme.typography.titleMedium)
    Text("Deletion requests are sent to JTBazar admin for review. Products are not deleted directly from the vendor app.", style = MaterialTheme.typography.bodySmall)

    Text("Inventory history", style = MaterialTheme.typography.titleMedium)
    if (inventoryHistory.length() == 0) Text("No inventory movements recorded yet.")
    for (i in 0 until minOf(inventoryHistory.length(), 30)) {
      val movement = inventoryHistory.optJSONObject(i) ?: continue
      val productName = movement.optJSONObject("products")?.optString("name") ?: "Product"
      val delta = movement.optInt("quantity_delta")
      Text("$productName • ${if (delta > 0) "+" else ""}$delta • ${movement.optString("movement_type")} • ${movement.optString("note")}", style = MaterialTheme.typography.bodySmall)
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
          if (ticket.optString("admin_response").isNotBlank()) Text("Admin reply: ${ticket.optString("admin_response")}")
          TextButton(enabled = !saving, onClick = {
            val id = ticket.optString("id")
            selectedSupportTicketId = id
            scope.launch {
              try { supportMessages = VendorService.listSupportMessages(id) }
              catch (e: Exception) { errorMessage = e.message ?: "Support conversation could not be loaded." }
            }
          }) { Text(if (selectedSupportTicketId == ticket.optString("id")) "Refresh conversation" else "Open conversation") }
          if (selectedSupportTicketId == ticket.optString("id")) {
            for (m in 0 until supportMessages.length()) {
              val msg = supportMessages.optJSONObject(m) ?: continue
              Text("${msg.optString("sender_role", "support").replaceFirstChar { it.uppercase() }}: ${msg.optString("message")}", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(value = supportReply, onValueChange = { supportReply = it.take(4000) }, label = { Text("Reply to support") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Button(enabled = !saving && supportReply.isNotBlank(), onClick = {
              scope.launch {
                saving = true
                try {
                  VendorService.sendSupportMessage(ticket.optString("id"), supportReply)
                  supportReply = ""
                  supportMessages = VendorService.listSupportMessages(ticket.optString("id"))
                  message = "Support reply sent."
                } catch (e: Exception) { errorMessage = e.message ?: "Support reply could not be sent." }
                finally { saving = false }
              }
            }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) { Text("Send reply") }
          }
        }
      }
    }
    OutlinedButton(onClick = { reload() }, modifier = Modifier.fillMaxWidth()) { Text("Refresh vendor tools") }
  }
  if (showShopMapPicker) {
    ShopLocationPickerDialog(
      initialLatitude = shopLat.toDoubleOrNull(),
      initialLongitude = shopLng.toDoubleOrNull(),
      onDismiss = { showShopMapPicker = false },
      onConfirm = { point: GeoPoint ->
        shopLat = String.format(java.util.Locale.US, "%.6f", point.latitude)
        shopLng = String.format(java.util.Locale.US, "%.6f", point.longitude)
        showShopMapPicker = false
      }
    )
  }
}

@Composable
fun VendorApplicationResubmissionPanel(application: VendorApplication, onResubmitted: (VendorApplication) -> Unit) {
  val scope = rememberCoroutineScope()
  var shopName by remember { mutableStateOf(application.shopName) }
  var slug by remember { mutableStateOf(application.slug) }
  var description by remember { mutableStateOf(application.description) }
  var ownerName by remember { mutableStateOf(application.ownerFullName) }
  var vendorType by remember { mutableStateOf(application.vendorType) }
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
    VendorTypeDropdown(selectedType = vendorType, onTypeSelected = { vendorType = it })
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
            .put("description", description.trim()).put("owner_full_name", ownerName.trim()).put("vendor_type", vendorType).put("cnic_number", cnic)
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorTypeDropdown(selectedType: String, onTypeSelected: (String) -> Unit) {
  val types = listOf(
    "individual_seller" to "Individual Seller",
    "registered_business" to "Registered Business",
    "sole_proprietor" to "Sole Proprietor",
    "partnership" to "Partnership",
    "company" to "Company"
  )
  var expanded by remember { mutableStateOf(false) }
  val selectedLabel = types.firstOrNull { it.first == selectedType }?.second ?: "Select vendor type"

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = !expanded },
    modifier = Modifier.fillMaxWidth()
  ) {
    OutlinedTextField(
      value = selectedLabel,
      onValueChange = {},
      readOnly = true,
      label = { Text("Vendor type") },
      placeholder = { Text("Select vendor type") },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier.fillMaxWidth().menuAnchor(),
      singleLine = true
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false }
    ) {
      types.forEach { (value, label) ->
        DropdownMenuItem(
          text = { Text(label) },
          onClick = {
            onTypeSelected(value)
            expanded = false
          },
          contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
        )
      }
    }
  }
}

@Composable
private fun VendorAdvancedField(label: String, value: String, lines: Int = 1, onChange: (String) -> Unit) {
  OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), minLines = lines, singleLine = lines == 1)
}
