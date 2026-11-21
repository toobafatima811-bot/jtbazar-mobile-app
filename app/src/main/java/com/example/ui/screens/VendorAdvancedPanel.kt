package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.supabase.VendorApplication
import com.example.data.supabase.VendorBusinessType
import com.example.data.supabase.VendorService
import com.example.localization.AppLanguageManager
import com.example.localization.appText
import com.example.ui.components.vendorGenericStatusBadgeSpec
import com.example.ui.theme.JTOrange
import com.example.ui.theme.VendorColors
import com.example.ui.theme.VendorShapes
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint

@OptIn(ExperimentalMaterial3Api::class)
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
  var productCategories by remember { mutableStateOf(JSONArray()) }

  fun reload() {
    scope.launch {
      errorMessage = ""
      val failures = mutableListOf<String>()
      try { cases = VendorService.listCases() } catch (_: Exception) {
        failures.add(AppLanguageManager.tr("Cases could not be loaded.", "کیسز لوڈ نہیں ہو سکے۔"))
      }
      try { tickets = VendorService.listSupportTickets() } catch (_: Exception) {
        failures.add(AppLanguageManager.tr("Support tickets could not be loaded.", "سپورٹ ٹکٹس لوڈ نہیں ہو سکے۔"))
      }
      try { inventoryHistory = VendorService.listInventoryMovements() } catch (_: Exception) {
        failures.add(AppLanguageManager.tr("Inventory history could not be loaded.", "انوینٹری ہسٹری لوڈ نہیں ہو سکی۔"))
      }
      if (failures.isNotEmpty()) errorMessage = failures.joinToString("\n")
    }
  }
  LaunchedEffect(application.id) {
    reload()
    try { productCategories = VendorService.listCategories() }
    catch (e: Exception) {
      errorMessage = e.message ?: AppLanguageManager.tr("Product categories could not be loaded.", "پروڈکٹ کیٹیگریز لوڈ نہیں ہو سکیں۔")
    }
  }
  LaunchedEffect(selectedSupportTicketId) {
    val ticketId = selectedSupportTicketId ?: return@LaunchedEffect
    while (true) {
      try { supportMessages = VendorService.listSupportMessages(ticketId) }
      catch (e: Exception) {
        errorMessage = e.message ?: AppLanguageManager.tr("Support conversation could not be refreshed.", "سپورٹ گفتگو ریفریش نہیں ہو سکی۔")
      }
      delay(10_000)
    }
  }

  val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    val productId = imageProductId
    if (uri != null && productId != null) scope.launch {
      saving = true; errorMessage = ""; message = ""
      try {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: error(AppLanguageManager.tr("Image could not be read.", "تصویر پڑھی نہیں جا سکی۔"))
        VendorService.uploadProductImage(productId, bytes, mime, "JTBazar product image")
        message = AppLanguageManager.tr("Product image uploaded successfully.", "پروڈکٹ تصویر کامیابی سے اپلوڈ ہو گئی۔")
        onRefresh()
      } catch (e: Exception) {
        errorMessage = e.message ?: AppLanguageManager.tr("Image upload failed.", "تصویر اپلوڈ نہیں ہو سکی۔")
      }
      finally { saving = false; imageProductId = null }
    }
  }


  val shopImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    if (uri != null) scope.launch {
      saving = true; errorMessage = ""; message = ""
      try {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: error(AppLanguageManager.tr("Shop image could not be read.", "دکان کی تصویر پڑھی نہیں جا سکی۔"))
        shopImageUrl = VendorService.uploadShopImage(bytes, mime)
        VendorService.updateOwnShop(JSONObject().put("shop_image_url", shopImageUrl))
        message = AppLanguageManager.tr("Shop image updated.", "دکان کی تصویر اپڈیٹ ہو گئی۔")
      } catch (e: Exception) {
        errorMessage = e.message ?: AppLanguageManager.tr("Shop image upload failed.", "دکان کی تصویر اپلوڈ نہیں ہو سکی۔")
      }
      finally { saving = false }
    }
  }

  if (deletionProductId != null) {
    AlertDialog(
      onDismissRequest = { deletionProductId = null },
      title = { Text(appText("Request product deletion", "پروڈکٹ حذف کرنے کی درخواست")) },
      text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          appText(
            "This sends a deletion request to JTBazar admin. It does not delete the product immediately.",
            "اس سے جے ٹی بازار ایڈمن کو حذف کرنے کی درخواست بھیجی جاتی ہے۔ پروڈکٹ فوراً حذف نہیں ہوتی۔"
          )
        )
        OutlinedTextField(
          value = deletionReason,
          onValueChange = { deletionReason = it },
          label = { Text(appText("Reason", "وجہ")) },
          minLines = 2
        )
      } },
      confirmButton = { TextButton(enabled = !saving, onClick = {
        val id = deletionProductId ?: return@TextButton
        scope.launch {
          saving = true
          try {
            VendorService.requestProductDeletion(id, deletionReason)
            deletionReason = ""; deletionProductId = null
            message = AppLanguageManager.tr("Deletion request submitted for admin review.", "حذف کرنے کی درخواست ایڈمن کے جائزے کے لیے بھیج دی گئی۔")
          } catch (e: Exception) {
            errorMessage = e.message ?: AppLanguageManager.tr("Deletion request failed.", "حذف کرنے کی درخواست ناکام رہی۔")
          }
          finally { saving = false }
        }
      }) { Text(appText("Submit request", "درخواست بھیجیں")) } },
      dismissButton = { TextButton(onClick = { deletionProductId = null }) { Text(appText("Cancel", "منسوخ کریں")) } }
    )
  }

  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (errorMessage.isNotBlank()) Text(errorMessage, color = MaterialTheme.colorScheme.error)
    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
    OutlinedButton(onClick = { shopOpen = !shopOpen }, modifier = Modifier.fillMaxWidth()) {
      Text(if (shopOpen) appText("Close shop settings", "دکان کی ترتیبات بند کریں") else appText("Edit shop details", "دکان کی تفصیلات میں ترمیم"))
    }
    if (shopOpen) {
      VendorAdvancedField(appText("Shop name", "دکان کا نام"), shopName) { shopName = it }
      VendorAdvancedField(appText("Shop URL slug", "دکان کا یو آر ایل سلگ"), slug) { slug = it }
      VendorAdvancedField(appText("Shop description", "دکان کی تفصیل"), description, 3) { description = it }
      VendorAdvancedField(appText("Business phone", "کاروباری فون"), phone) { phone = it }
      VendorAdvancedField(appText("Current address", "موجودہ پتہ"), currentAddress) { currentAddress = it }
      VendorAdvancedField(appText("Complete address", "مکمل پتہ"), completeAddress, 2) { completeAddress = it }
      VendorAdvancedField(appText("Shop location / verification address", "دکان کا مقام / تصدیقی پتہ"), locationText, 2) { locationText = it }
      OutlinedButton(enabled = !saving, onClick = { shopImagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
        Text(if (shopImageUrl.isBlank()) appText("Upload shop picture", "دکان کی تصویر اپلوڈ کریں") else appText("Change shop picture", "دکان کی تصویر تبدیل کریں"))
      }
      Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = showShopOnMap, onCheckedChange = { showShopOnMap = it })
        Text(appText("Show shop on public map", "عوامی نقشے پر دکان دکھائیں"))
      }
      if (showShopOnMap) {
        Text(
          if (shopLat.isNotBlank() && shopLng.isNotBlank()) appText("Pinned shop location: $shopLat, $shopLng", "نشان زدہ دکان کا مقام: $shopLat, $shopLng")
          else appText("Choose the exact shop location on the map.", "نقشے پر دکان کا درست مقام منتخب کریں۔"),
          style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(enabled = !saving, onClick = { showShopMapPicker = true }, modifier = Modifier.fillMaxWidth()) {
          Text(if (shopLat.isNotBlank() && shopLng.isNotBlank()) appText("Change shop location on map", "نقشے پر دکان کا مقام تبدیل کریں") else appText("Choose shop location on map", "نقشے پر دکان کا مقام منتخب کریں"))
        }
      }
      Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = shopIsOpen, onCheckedChange = { shopIsOpen = it })
        Text(appText("Shop is open for daily food orders", "دکان روزانہ کھانے کے آرڈرز کے لیے کھلی ہے"))
      }
      VendorAdvancedField(appText("Minimum order value (PKR)", "کم از کم آرڈر رقم (PKR)"), minimumOrderValue) { minimumOrderValue = it.filter(Char::isDigit).take(9) }
      Button(enabled = !saving, onClick = {
        scope.launch {
          saving = true; errorMessage = ""; message = ""
          try {
            require(shopName.trim().length >= 2) { AppLanguageManager.tr("Enter a valid shop name.", "درست دکان کا نام درج کریں۔") }
            require(slug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) { AppLanguageManager.tr("Use a valid URL slug.", "درست یو آر ایل سلگ استعمال کریں۔") }
            require(description.trim().length >= 10 && currentAddress.trim().length >= 3 && completeAddress.trim().length >= 10 && locationText.trim().length >= 10) {
              AppLanguageManager.tr("Complete all shop details.", "دکان کی تمام تفصیلات مکمل کریں۔")
            }
            require(phone.matches(Regex("^(03[0-9]{9}|\\+923[0-9]{9})$"))) { AppLanguageManager.tr("Enter a valid Pakistani mobile number.", "درست پاکستانی موبائل نمبر درج کریں۔") }
            VendorService.updateOwnShop(JSONObject().put("shop_name", shopName.trim()).put("slug", slug.trim())
              .put("description", description.trim()).put("phone", phone.trim()).put("current_address", currentAddress.trim())
              .put("complete_address", completeAddress.trim()).put("shop_location_text", locationText.trim())
              .put("shop_image_url", if (shopImageUrl.isBlank()) JSONObject.NULL else shopImageUrl)
              .put("show_shop_on_map", showShopOnMap)
              .put("shop_lat", if (showShopOnMap) shopLat.toDoubleOrNull() ?: error(AppLanguageManager.tr("Enter a valid shop latitude.", "دکان کا درست عرض البلد درج کریں۔")) else JSONObject.NULL)
              .put("shop_lng", if (showShopOnMap) shopLng.toDoubleOrNull() ?: error(AppLanguageManager.tr("Enter a valid shop longitude.", "دکان کا درست طول البلد درج کریں۔")) else JSONObject.NULL)
              .put("shop_location_label", locationText.trim())
              .put("shop_is_open", shopIsOpen)
              .put("minimum_order_value", minimumOrderValue.toIntOrNull() ?: error(AppLanguageManager.tr("Enter a valid minimum order value.", "کم از کم آرڈر کی درست رقم درج کریں۔"))))
            message = AppLanguageManager.tr("Shop details updated.", "دکان کی تفصیلات اپڈیٹ ہو گئیں۔"); shopOpen = false
          } catch (e: Exception) { errorMessage = e.message ?: AppLanguageManager.tr("Shop details could not be saved.", "دکان کی تفصیلات محفوظ نہیں ہو سکیں۔") }
          finally { saving = false }
        }
      }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange), modifier = Modifier.fillMaxWidth()) { Text(appText("Save shop details", "دکان کی تفصیلات محفوظ کریں")) }
    }

    Text(appText("Product images, variants and inventory", "پروڈکٹ کی تصاویر، اقسام اور انوینٹری"), style = MaterialTheme.typography.titleMedium)
    for (i in 0 until products.length()) {
      val product = products.optJSONObject(i) ?: continue
      val id = product.optString("id")
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          var editing by remember(id) { mutableStateOf(false) }
          var editName by remember(id) { mutableStateOf(product.optString("name", "").takeUnless { it == "null" }.orEmpty()) }
          var editSlug by remember(id) { mutableStateOf(product.optString("slug", "").takeUnless { it == "null" }.orEmpty()) }
          var editSku by remember(id) { mutableStateOf(product.optString("sku", "").takeUnless { it == "null" }.orEmpty()) }
          var editCategory by remember(id) { mutableStateOf(product.optString("category_id", "").takeUnless { it == "null" }.orEmpty()) }
          var editShortDescription by remember(id) { mutableStateOf(product.optString("short_description", "").takeUnless { it == "null" }.orEmpty()) }
          var editDescription by remember(id) { mutableStateOf(product.optString("description", "").takeUnless { it == "null" }.orEmpty()) }
          var editPrice by remember(id) { mutableStateOf(product.optString("vendor_base_price", product.optString("price", "")).takeUnless { it == "null" }.orEmpty()) }
          var editDiscount by remember(id) { mutableStateOf(product.optString("discount_percent", "0").takeUnless { it == "null" } ?: "0") }
          var editSupplierCost by remember(id) { mutableStateOf(if (product.isNull("supplier_cost")) "" else product.optString("supplier_cost").takeUnless { it == "null" }.orEmpty()) }
          var editFulfilment by remember(id) { mutableStateOf(product.optString("fulfillment_expense", "0").takeUnless { it == "null" } ?: "0") }
          var editMinimumProfit by remember(id) { mutableStateOf(product.optString("minimum_profit", "0").takeUnless { it == "null" } ?: "0") }
          var editWeight by remember(id) { mutableStateOf(if (product.isNull("weight_grams")) "" else product.optString("weight_grams").takeUnless { it == "null" }.orEmpty()) }
          var editSpecifications by remember(id) { mutableStateOf((product.optJSONArray("specifications") ?: JSONArray()).toString(2)) }
          var editDimensions by remember(id) { mutableStateOf((product.optJSONObject("dimensions") ?: JSONObject()).toString(2)) }
          var editSeoTitle by remember(id) { mutableStateOf(product.optString("seo_title", "").takeUnless { it == "null" }.orEmpty()) }
          var editSeoDescription by remember(id) { mutableStateOf(product.optString("seo_description", "").takeUnless { it == "null" }.orEmpty()) }
          var editFoodUnit by remember(id) { mutableStateOf(product.optString("food_selling_unit", "kg").ifBlank { "kg" }) }
          var editFoodUnitPrice by remember(id) { mutableStateOf(product.optString("food_unit_price", product.optString("vendor_base_price", product.optString("price", ""))).takeUnless { it == "null" }.orEmpty()) }
          var editFoodStep by remember(id) { mutableStateOf(product.optInt("food_quantity_increment", 250).toString()) }
          var editFoodMinimum by remember(id) { mutableStateOf(product.optInt("food_minimum_quantity", 1).toString()) }
          var editDailyItem by remember(id) { mutableStateOf(product.optBoolean("is_daily_item", false)) }
          var editDailyStatus by remember(id) { mutableStateOf(product.optString("daily_status", "available")) }
          var editDailyQuantity by remember(id) { mutableStateOf(product.optInt("daily_available_quantity", 0).toString()) }
          val editIsFoodCategory = run {
            var categoryId: String? = editCategory.takeIf { it.isNotBlank() }
            var isFood = false
            val visited = mutableSetOf<String>()
            while (categoryId != null && visited.add(categoryId)) {
              val currentCategory = (0 until productCategories.length()).mapNotNull { productCategories.optJSONObject(it) }
                .firstOrNull { it.optString("id") == categoryId }
              if (currentCategory == null) break
              if (currentCategory.optBoolean("is_food_category") || currentCategory.optString("is_food_category").equals("true", true) ||
                currentCategory.optString("slug").lowercase() in setOf("food-groceries", "food-and-groceries", "food-grocery") ||
                currentCategory.optString("name").trim().lowercase() in setOf("food & groceries", "food and groceries", "home & kitchen")) {
                isFood = true
                break
              }
              categoryId = currentCategory.optString("parent_id").takeIf { it.isNotBlank() && it != "null" }
            }
            isFood || product.optString("food_selling_unit").isNotBlank() || product.optBoolean("is_daily_item")
          }
          Text(product.optString("name", appText("Product", "پروڈکٹ")), style = MaterialTheme.typography.titleSmall)
          val statusDisplay = when (product.optString("status", "draft").lowercase()) {
            "approved" -> appText("Approved", "منظور شدہ")
            "pending" -> appText("Pending review", "جائزہ زیر التواء")
            "rejected" -> appText("Rejected", "مسترد")
            "archived" -> appText("Archived", "محفوظ شدہ")
            else -> appText("Draft", "ڈرافٹ")
          }
          Text(appText("Status: $statusDisplay • PKR ${product.optString("price", "0")}", "حالت: $statusDisplay • PKR ${product.optString("price", "0")}"))
          TextButton(onClick = { editing = !editing }) { Text(if (editing) appText("Close product editor", "پروڈکٹ ایڈیٹر بند کریں") else appText("Edit product details", "پروڈکٹ کی تفصیلات میں ترمیم")) }
          if (editing) {
            VendorAdvancedField(appText("Product name", "پروڈکٹ کا نام"), editName) { editName = it }
            VendorAdvancedField(appText("Product URL slug", "پروڈکٹ یو آر ایل سلگ"), editSlug) { editSlug = it.lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-') }
            VendorAdvancedField(appText("Product SKU", "پروڈکٹ SKU"), editSku) { editSku = it }
            var categoryExpanded by remember(id) { mutableStateOf(false) }
            val selectedCategoryName = (0 until productCategories.length()).mapNotNull { productCategories.optJSONObject(it) }
              .firstOrNull { it.optString("id") == editCategory }?.optString("name") ?: appText("Select category", "کیٹیگری منتخب کریں")
            ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = !categoryExpanded }) {
              OutlinedTextField(
                value = selectedCategoryName,
                onValueChange = {},
                readOnly = true,
                label = { Text(appText("Category", "کیٹیگری")) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
              )
              ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                for (categoryIndex in 0 until productCategories.length()) {
                  val category = productCategories.optJSONObject(categoryIndex) ?: continue
                  DropdownMenuItem(text = { Text(category.optString("name")) }, onClick = {
                    editCategory = category.optString("id")
                    if (category.optBoolean("is_food_category") || category.optString("slug") == "food-groceries") {
                      if (editFoodUnit.isBlank()) editFoodUnit = "kg"
                      if (editFoodStep.isBlank()) editFoodStep = if (editFoodUnit == "kg" || editFoodUnit == "l") "250" else "1"
                    }
                    categoryExpanded = false
                  })
                }
              }
            }
            VendorAdvancedField(appText("Short description", "مختصر تفصیل"), editShortDescription, 2) { editShortDescription = it }
            VendorAdvancedField(appText("Full description", "مکمل تفصیل"), editDescription, 4) { editDescription = it }
            VendorAdvancedField(appText("Vendor base price (PKR)", "وینڈر کی بنیادی قیمت (PKR)"), editPrice) { editPrice = it.filter { ch -> ch.isDigit() || ch == '.' } }
            VendorAdvancedField(appText("Discount (%)", "رعایت (%)"), editDiscount) { editDiscount = it.filter { ch -> ch.isDigit() || ch == '.' } }
            VendorAdvancedField(appText("Supplier cost (private PKR)", "سپلائر لاگت (نجی PKR)"), editSupplierCost) { editSupplierCost = it.filter { ch -> ch.isDigit() || ch == '.' } }
            VendorAdvancedField(appText("Fulfilment expense (PKR)", "پیکنگ و ترسیل لاگت (PKR)"), editFulfilment) { editFulfilment = it.filter { ch -> ch.isDigit() || ch == '.' } }
            VendorAdvancedField(appText("Minimum profit (PKR)", "کم از کم منافع (PKR)"), editMinimumProfit) { editMinimumProfit = it.filter { ch -> ch.isDigit() || ch == '.' } }
            VendorAdvancedField(appText("Shipping weight (grams)", "شپنگ وزن (گرام)"), editWeight) { editWeight = it.filter(Char::isDigit) }
            VendorAdvancedField(appText("Specifications (JSON array)", "تفصیلات (JSON array)"), editSpecifications, 3) { editSpecifications = it }
            VendorAdvancedField(appText("Dimensions (JSON object)", "پیمائش (JSON object)"), editDimensions, 3) { editDimensions = it }
            VendorAdvancedField(appText("SEO title", "SEO عنوان"), editSeoTitle) { editSeoTitle = it }
            VendorAdvancedField(appText("SEO description", "SEO تفصیل"), editSeoDescription, 2) { editSeoDescription = it }
            if (editIsFoodCategory) {
              HorizontalDivider()
              Text(appText("Food & groceries", "کھانا اور گروسری"), style = MaterialTheme.typography.titleMedium)
              Text(appText("Food selling unit", "کھانے کی فروخت کی اکائی"), style = MaterialTheme.typography.titleSmall)
              var foodUnitExpanded by remember(id) { mutableStateOf(false) }
              val foodUnits = listOf(
                "kg" to appText("Kilogram (kg)", "کلوگرام (kg)"),
                "g" to appText("Gram (g)", "گرام (g)"),
                "l" to appText("Litre (L)", "لیٹر (L)"),
                "ml" to appText("Millilitre (ml)", "ملی لیٹر (ml)"),
                "piece" to appText("Piece", "عدد"),
                "dozen" to appText("Dozen", "درجن"),
                "pack" to appText("Pack", "پیک")
              )
              ExposedDropdownMenuBox(expanded = foodUnitExpanded, onExpandedChange = { foodUnitExpanded = !foodUnitExpanded }) {
                OutlinedTextField(value = foodUnits.firstOrNull { it.first == editFoodUnit }?.second ?: appText("Kilogram (kg)", "کلوگرام (kg)"), onValueChange = {}, readOnly = true, label = { Text(appText("Selling unit", "فروخت کی اکائی")) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(foodUnitExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                ExposedDropdownMenu(expanded = foodUnitExpanded, onDismissRequest = { foodUnitExpanded = false }) {
                  foodUnits.forEach { (value, label) -> DropdownMenuItem(text = { Text(label) }, onClick = {
                    editFoodUnit = value
                    editFoodStep = if (value == "kg" || value == "l") "250" else if (value == "g" || value == "ml") "100" else "1"
                    foodUnitExpanded = false
                  }) }
                }
              }
              VendorAdvancedField(appText("Price per ${editFoodUnit} (PKR)", "فی ${editFoodUnit} قیمت (PKR)"), editFoodUnitPrice) { editFoodUnitPrice = it.filter { ch -> ch.isDigit() || ch == '.' } }
              val stepUnit = when (editFoodUnit) {
                "kg" -> appText("grams", "گرام")
                "l" -> appText("ml", "ملی لیٹر")
                else -> appText("units", "اکائیاں")
              }
              VendorAdvancedField(appText("Customer quantity step ($stepUnit)", "کسٹمر مقدار کا مرحلہ ($stepUnit)"), editFoodStep) { editFoodStep = it.filter(Char::isDigit) }
              Text(appText("For kg/litre the step is grams/ml; for dozen, piece and pack it is units.", "کلو/لیٹر کے لیے مرحلہ گرام/ملی لیٹر میں ہے؛ درجن، عدد اور پیک کے لیے اکائیوں میں ہے۔"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
              VendorAdvancedField(appText("Minimum purchase steps", "کم از کم خریداری مراحل"), editFoodMinimum) { editFoodMinimum = it.filter(Char::isDigit) }
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().clickable { editDailyItem = !editDailyItem }.padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Checkbox(checked = editDailyItem, onCheckedChange = { editDailyItem = it })
          Text(
            appText("Manage as a daily dish / daily food item", "روزانہ ڈش / روزانہ کھانے کی چیز کے طور پر منظم کریں"),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
          )
        }
      }
              var dailyStatusExpanded by remember(id) { mutableStateOf(false) }
              val dailyStatuses = listOf(
                "available" to appText("Available today", "آج دستیاب ہے"),
                "not_today" to appText("Not for today", "آج کے لیے نہیں"),
                "coming_soon" to appText("Coming soon", "جلد آ رہا ہے")
              )
              ExposedDropdownMenuBox(expanded = dailyStatusExpanded, onExpandedChange = { dailyStatusExpanded = !dailyStatusExpanded }) {
                OutlinedTextField(value = dailyStatuses.firstOrNull { it.first == editDailyStatus }?.second ?: appText("Available today", "آج دستیاب ہے"), onValueChange = {}, readOnly = true, label = { Text(appText("Daily availability status", "روزانہ دستیابی کی حیثیت")) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dailyStatusExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                ExposedDropdownMenu(expanded = dailyStatusExpanded, onDismissRequest = { dailyStatusExpanded = false }) {
                  dailyStatuses.forEach { (value, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { editDailyStatus = value; dailyStatusExpanded = false }) }
                }
              }
              VendorAdvancedField(appText("Today's available quantity", "آج کی دستیاب مقدار"), editDailyQuantity) { editDailyQuantity = it.filter(Char::isDigit) }
            }
            Button(enabled = !saving, onClick = {
              scope.launch {
                saving = true
                try {
                  val priceInput = if (editIsFoodCategory) editFoodUnitPrice else editPrice
                  val price = priceInput.toDoubleOrNull() ?: error(
                    if (editIsFoodCategory) AppLanguageManager.tr("Enter a valid food unit price.", "کھانے کی اکائی کی درست قیمت درج کریں۔")
                    else AppLanguageManager.tr("Enter a valid vendor base price.", "وینڈر کی درست بنیادی قیمت درج کریں۔")
                  )
                  val discount = editDiscount.toDoubleOrNull() ?: error(AppLanguageManager.tr("Enter a valid discount.", "درست رعایت درج کریں۔"))
                  require(discount in 0.0..100.0) { AppLanguageManager.tr("Discount must be between 0 and 100%.", "رعایت 0 سے 100% کے درمیان ہونی چاہیے۔") }
                  val specs = JSONArray(editSpecifications)
                  val dimensions = JSONObject(editDimensions)
                  val payload = JSONObject()
                    .put("p_category_id", editCategory.ifBlank { JSONObject.NULL })
                    .put("p_name", editName.trim()).put("p_slug", editSlug.trim())
                    .put("p_sku", editSku.trim().ifBlank { JSONObject.NULL })
                    .put("p_short_description", editShortDescription.trim())
                    .put("p_description", editDescription.trim()).put("p_price", price)
                    .put("p_supplier_cost", editSupplierCost.toDoubleOrNull() ?: JSONObject.NULL)
                    .put("p_fulfillment_expense", editFulfilment.toDoubleOrNull() ?: error(AppLanguageManager.tr("Enter valid fulfilment expense.", "درست پیکنگ و ترسیل لاگت درج کریں۔")))
                    .put("p_minimum_profit", editMinimumProfit.toDoubleOrNull() ?: error(AppLanguageManager.tr("Enter valid minimum profit.", "کم از کم درست منافع درج کریں۔")))
                    .put("p_discount_percent", discount)
                    .put("p_weight_grams", editWeight.toIntOrNull() ?: JSONObject.NULL)
                    .put("p_specifications", specs).put("p_dimensions", dimensions)
                    .put("p_seo_title", editSeoTitle.trim().ifBlank { JSONObject.NULL })
                    .put("p_seo_description", editSeoDescription.trim().ifBlank { JSONObject.NULL })
                  VendorService.updateProductFull(id, payload)
                  if (editIsFoodCategory) {
                    val unitPrice = editFoodUnitPrice.toDoubleOrNull() ?: error(AppLanguageManager.tr("Enter a valid food unit price.", "کھانے کی اکائی کی درست قیمت درج کریں۔"))
                    val step = editFoodStep.toIntOrNull() ?: error(AppLanguageManager.tr("Enter a valid customer quantity step.", "کسٹمر مقدار کا درست مرحلہ درج کریں۔"))
                    val minimum = editFoodMinimum.toIntOrNull() ?: error(AppLanguageManager.tr("Enter valid minimum purchase steps.", "کم از کم خریداری کے درست مراحل درج کریں۔"))
                    val dailyQty = editDailyQuantity.toIntOrNull() ?: error(AppLanguageManager.tr("Enter a valid daily available quantity.", "روزانہ دستیاب درست مقدار درج کریں۔"))
                    if (step < 1 || minimum < 1 || dailyQty < 0 || unitPrice < 0) error(AppLanguageManager.tr("Check food unit price and quantity values.", "کھانے کی قیمت اور مقدار کی قدریں چیک کریں۔"))
                    VendorService.updateFoodProductDetails(id, editFoodUnit, unitPrice, step, minimum, editDailyItem, editDailyStatus, dailyQty)
                  }
                  val outcome = VendorService.finalizeProductSubmission(
                    productId = id,
                    allowDirectPublish = application.allowProductPublishingWithoutReview,
                    isEdit = true
                  )
                  if (outcome.finalizationError != null) {
                    errorMessage = outcome.finalizationError
                  } else {
                    message = outcome.message
                  }
                  editing = false
                  onRefresh()
                } catch (e: Exception) { errorMessage = e.message ?: AppLanguageManager.tr("Product update failed.", "پروڈکٹ اپڈیٹ ناکام رہا۔") }
                finally { saving = false }
              }
            }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) { Text(appText("Save all product changes", "پروڈکٹ کی تمام تبدیلیاں محفوظ کریں")) }
          }
          var dailyItem by remember(id) { mutableStateOf(product.optBoolean("is_daily_item", false)) }
          var dailyQuantity by remember(id) { mutableStateOf(product.optInt("daily_available_quantity", 0).toString()) }
          Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = dailyItem, onCheckedChange = { dailyItem = it })
            Text(appText("Daily food item", "روزانہ کھانے کی چیز"))
          }
          if (dailyItem) {
            VendorAdvancedField(appText("Available quantity today", "آج دستیاب مقدار"), dailyQuantity) { dailyQuantity = it.filter(Char::isDigit).take(8) }
            Button(enabled = !saving, onClick = {
              scope.launch {
                saving = true
                try {
                  VendorService.setDailyAvailability(id, true, dailyQuantity.toIntOrNull() ?: error(AppLanguageManager.tr("Enter today's available quantity.", "آج کی دستیاب مقدار درج کریں۔")), 1, minimumOrderValue.toIntOrNull(), shopIsOpen)
                  message = AppLanguageManager.tr("Today's food availability updated.", "آج کے کھانے کی دستیابی اپڈیٹ ہو گئی۔")
                  onRefresh()
                } catch (e: Exception) { errorMessage = e.message ?: AppLanguageManager.tr("Daily availability could not be updated.", "روزانہ دستیابی اپڈیٹ نہیں ہو سکی۔") }
                finally { saving = false }
              }
            }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) { Text(appText("Save today's availability", "آج کی دستیابی محفوظ کریں")) }
          } else if (product.optBoolean("is_daily_item", false)) {
            TextButton(enabled = !saving, onClick = {
              scope.launch {
                saving = true
                try { VendorService.setDailyAvailability(id, false, 0); message = AppLanguageManager.tr("Daily availability disabled.", "روزانہ دستیابی بند کر دی گئی۔"); onRefresh() }
                catch (e: Exception) { errorMessage = e.message ?: AppLanguageManager.tr("Availability setting could not be changed.", "دستیابی کی ترتیب تبدیل نہیں ہو سکی۔") }
                finally { saving = false }
              }
            }) { Text(appText("Disable daily availability", "روزانہ دستیابی بند کریں")) }
          }
          OutlinedButton(enabled = !saving, onClick = { imageProductId = id; imagePicker.launch("image/*") }) { Text(appText("Upload product image", "پروڈکٹ کی تصویر اپلوڈ کریں")) }
          TextButton(enabled = !saving, onClick = { deletionProductId = id; deletionReason = "" }) { Text(appText("Request product deletion", "پروڈکٹ حذف کرنے کی درخواست")) }
          var stock by remember(id) { mutableStateOf(product.optString("stock_quantity", product.optString("quantity", "0"))) }
          Row {
            OutlinedTextField(value = stock, onValueChange = { stock = it.filter(Char::isDigit).take(8) },
              label = { Text(appText("Stock quantity", "اسٹاک مقدار")) }, modifier = Modifier.weight(1f), singleLine = true)
            Spacer(Modifier.width(8.dp))
            Button(enabled = !saving, onClick = {
              scope.launch {
                saving = true
                try {
                  val qty = stock.toIntOrNull() ?: error(AppLanguageManager.tr("Enter a valid stock quantity.", "اسٹاک کی درست مقدار درج کریں۔"))
                  VendorService.setInventory(id, qty); message = AppLanguageManager.tr("Inventory updated.", "انوینٹری اپڈیٹ ہو گئی۔")
                } catch (e: Exception) { errorMessage = e.message ?: AppLanguageManager.tr("Inventory update failed.", "انوینٹری اپڈیٹ ناکام رہا۔") }
                finally { saving = false }
              }
            }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) { Text(appText("Save", "محفوظ کریں")) }
          }
          TextButton(onClick = {
            if (variantProductId == id) variantProductId = null
            else {
              variantProductId = id
              selectedVariantId = null
              variantValue = ""; variantPrice = ""; variantStock = "0"
              scope.launch { try { variantRows = VendorService.listVariants(id) } catch (e: Exception) { errorMessage = e.message ?: AppLanguageManager.tr("Variants could not be loaded.", "اقسام لوڈ نہیں ہو سکیں۔") } }
            }
          }) { Text(if (variantProductId == id) appText("Close variant editor", "اقسام ایڈیٹر بند کریں") else appText("Manage variants (add / edit / delete)", "اقسام کا انتظام کریں (شامل / ترمیم / حذف)")) }
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
                    } catch (e: Exception) { errorMessage = e.message ?: AppLanguageManager.tr("Variant stock could not be loaded.", "قسم کا اسٹاک لوڈ نہیں ہو سکا۔") }
                  }
                }) { Text(appText("Edit", "ترمیم")) }
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
                      message = AppLanguageManager.tr("Variant deleted. Product changes may require review.", "قسم حذف کر دی گئی۔ پروڈکٹ کی تبدیلیوں کے لیے جائزہ درکار ہو سکتا ہے۔")
                    } catch (e: Exception) { errorMessage = e.message ?: AppLanguageManager.tr("Variant could not be deleted.", "قسم حذف نہیں ہو سکی۔") }
                    finally { saving = false }
                  }
                }) { Text(appText("Delete", "حذف کریں")) }
              }
            }
            VendorAdvancedField(appText("Option name (Size, Colour, etc.)", "آپشن کا نام (سائز، رنگ وغیرہ)"), variantLabel) { variantLabel = it }
            VendorAdvancedField(appText("Option value", "آپشن کی قدر"), variantValue) { variantValue = it }
            VendorAdvancedField(appText("Price override (optional PKR)", "متبادل قیمت (اختیاری PKR)"), variantPrice) { variantPrice = it }
            VendorAdvancedField(appText("Variant stock", "قسم کا اسٹاک"), variantStock) { variantStock = it.filter(Char::isDigit) }
            Button(enabled = !saving, onClick = {
              scope.launch {
                saving = true; errorMessage = ""
                try {
                  require(variantLabel.isNotBlank() && variantValue.isNotBlank()) { AppLanguageManager.tr("Enter a variant option and value.", "قسم کا آپشن اور قدر درج کریں۔") }
                  val qty = variantStock.toIntOrNull() ?: error(AppLanguageManager.tr("Enter valid variant stock.", "قسم کا درست اسٹاک درج کریں۔"))
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
                  VendorService.saveVariants(id, rows)
                  message = if (selectedVariantId == null) AppLanguageManager.tr("Variant added.", "قسم شامل کر دی گئی۔") else AppLanguageManager.tr("Variant updated.", "قسم اپڈیٹ ہو گئی۔")
                  selectedVariantId = null; variantProductId = null
                } catch (e: Exception) { errorMessage = e.message ?: AppLanguageManager.tr("Variant could not be saved.", "قسم محفوظ نہیں ہو سکی۔") }
                finally { saving = false }
              }
            }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) { Text(appText("Save variant", "قسم محفوظ کریں")) }
          }
        }
      }
    }

    Text(appText("Product deletion requests", "پروڈکٹ حذف کرنے کی درخواستیں"), style = MaterialTheme.typography.titleMedium)
    Text(
      appText(
        "Deletion requests are sent to JTBazar admin for review. Products are not deleted directly from the vendor app.",
        "حذف کرنے کی درخواستیں جائزے کے لیے جے ٹی بازار ایڈمن کو بھیجی جاتی ہیں۔ پروڈکٹس براہ راست وینڈر ایپ سے حذف نہیں ہوتیں۔"
      ),
      style = MaterialTheme.typography.bodySmall
    )

    Text(appText("Inventory history", "انوینٹری ہسٹری"), style = MaterialTheme.typography.titleMedium)
    if (inventoryHistory.length() == 0) Text(appText("No inventory movements recorded yet.", "ابھی تک اسٹاک میں کوئی تبدیلی ریکارڈ نہیں ہوئی۔"))
    for (i in 0 until minOf(inventoryHistory.length(), 30)) {
      val movement = inventoryHistory.optJSONObject(i) ?: continue
      val productName = movement.optJSONObject("products")?.optString("name") ?: appText("Product", "پروڈکٹ")
      val delta = movement.optInt("quantity_delta")
      val movementLabel = when (movement.optString("movement_type").lowercase()) {
        "restock" -> appText("Restock", "اسٹاک شامل")
        "adjustment" -> appText("Adjustment", "ترمیم")
        "sale", "order_deduction" -> appText("Order deduction", "آرڈر کٹوتی")
        "return" -> appText("Return", "واپسی")
        "initial" -> appText("Initial stock", "ابتدائی اسٹاک")
        else -> movement.optString("movement_type")
      }
      val note = movement.optString("note").takeUnless { it == "null" }.orEmpty()
      Text(listOf("$productName", "${if (delta > 0) "+" else ""}$delta", movementLabel, note).filter { it.isNotBlank() }.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
    }

    Text(appText("Customer cases and complaints", "کسٹمر کیسز اور شکایات"), style = MaterialTheme.typography.titleMedium)
    if (cases.length() == 0) Text(appText("No marketplace cases found.", "کوئی مارکیٹ پلیس کیس نہیں ملا۔"))
    for (i in 0 until cases.length()) {
      val item = cases.optJSONObject(i) ?: continue
      val caseTypeRaw = item.optString("case_type", "customer_case")
      val caseTypeLabel = when (caseTypeRaw.lowercase()) {
        "return" -> appText("Return", "واپسی")
        "refund" -> appText("Refund", "رقم واپسی")
        "complaint" -> appText("Complaint", "شکایت")
        "damage", "damaged" -> appText("Damaged item", "خراب شے")
        "missing", "missing_item" -> appText("Missing item", "گمشدہ شے")
        "customer_case" -> appText("Customer case", "کسٹمر کیس")
        else -> caseTypeRaw.replace('_', ' ')
      }
      val caseStatusLabel = vendorGenericStatusBadgeSpec(item.optString("status", "open")).label
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
          Text("$caseTypeLabel • $caseStatusLabel")
          Text(item.optString("reason", appText("No reason supplied.", "کوئی وجہ فراہم نہیں کی گئی۔")))
          if (item.optString("seller_response").isNotBlank()) {
            Text(appText("Your response: ${item.optString("seller_response")}", "آپ کا جواب: ${item.optString("seller_response")}"))
          }
          OutlinedTextField(
            value = responseText,
            onValueChange = { responseText = it },
            label = { Text(appText("Reply to case", "کیس کا جواب دیں")) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
          )
          Button(enabled = !saving, onClick = {
            scope.launch {
              saving = true
              try {
                VendorService.respondToCase(item.optString("id"), responseText)
                responseText = ""
                cases = VendorService.listCases()
                message = AppLanguageManager.tr("Case response sent.", "کیس کا جواب بھیج دیا گیا۔")
              } catch (e: Exception) {
                errorMessage = e.message ?: AppLanguageManager.tr("Case response failed.", "کیس کا جواب بھیجا نہیں جا سکا۔")
              }
              finally { saving = false }
            }
          }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) {
            Text(appText("Send response", "جواب بھیجیں"))
          }
        }
      }
    }

    Text(appText("Vendor support", "وینڈر سپورٹ"), style = MaterialTheme.typography.titleMedium)
    VendorAdvancedField(appText("Ticket subject", "ٹکٹ کا موضوع"), subject) { subject = it }
    VendorAdvancedField(appText("Describe your issue", "اپنا مسئلہ بیان کریں"), ticketMessage, 3) { ticketMessage = it }
    Button(enabled = !saving, onClick = {
      scope.launch {
        saving = true
        try {
          VendorService.createSupportTicket(subject, ticketMessage)
          subject = ""; ticketMessage = ""; tickets = VendorService.listSupportTickets()
          message = AppLanguageManager.tr("Support ticket created.", "سپورٹ ٹکٹ بن گیا۔")
        } catch (e: Exception) {
          errorMessage = e.message ?: AppLanguageManager.tr("Support ticket could not be created.", "سپورٹ ٹکٹ نہیں بن سکا۔")
        }
        finally { saving = false }
      }
    }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange), modifier = Modifier.fillMaxWidth()) {
      Text(appText("Create support ticket", "سپورٹ ٹکٹ بنائیں"))
    }
    for (i in 0 until tickets.length()) {
      val ticket = tickets.optJSONObject(i) ?: continue
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
          Text("${ticket.optString("subject")} • ${vendorGenericStatusBadgeSpec(ticket.optString("status", "open")).label}")
          Text(ticket.optString("message"), style = MaterialTheme.typography.bodySmall)
          if (ticket.optString("admin_response").isNotBlank()) {
            Text(
              appText("Admin reply: ${ticket.optString("admin_response")}", "ایڈمن کا جواب: ${ticket.optString("admin_response")}")
            )
          }
          TextButton(enabled = !saving, onClick = {
            val id = ticket.optString("id")
            selectedSupportTicketId = id
            scope.launch {
              try { supportMessages = VendorService.listSupportMessages(id) }
              catch (e: Exception) {
                errorMessage = e.message ?: AppLanguageManager.tr("Support conversation could not be loaded.", "سپورٹ گفتگو لوڈ نہیں ہو سکی۔")
              }
            }
          }) {
            Text(
              if (selectedSupportTicketId == ticket.optString("id")) appText("Refresh conversation", "گفتگو ریفریش کریں")
              else appText("Open conversation", "گفتگو کھولیں")
            )
          }
          if (selectedSupportTicketId == ticket.optString("id")) {
            Text(
              appText("Conversation • updates automatically", "گفتگو • خودکار طور پر اپڈیٹ ہوتی ہے"),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary
            )
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
              Column(Modifier.padding(10.dp)) {
                Text(appText("You • Ticket created", "آپ • ٹکٹ بنایا گیا"), style = MaterialTheme.typography.labelSmall)
                Text(ticket.optString("message"), style = MaterialTheme.typography.bodyMedium)
                Text(ticket.optString("created_at"), style = MaterialTheme.typography.labelSmall)
              }
            }
            if (supportMessages.length() == 0 && ticket.optString("admin_response").isNotBlank()) {
              Text("${appText("JTBazar Support", "جے ٹی بازار سپورٹ")}: ${ticket.optString("admin_response")}", style = MaterialTheme.typography.bodyMedium)
            }
            for (m in 0 until supportMessages.length()) {
              val msg = supportMessages.optJSONObject(m) ?: continue
              Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = if (msg.optString("sender_role") == "seller") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
              )) {
                Column(Modifier.padding(10.dp)) {
                  Text(
                    if (msg.optString("sender_role") == "admin") appText("JTBazar Support", "جے ٹی بازار سپورٹ")
                    else appText("You", "آپ"),
                    style = MaterialTheme.typography.labelSmall
                  )
                  Text(msg.optString("message"), style = MaterialTheme.typography.bodyMedium)
                  Text(msg.optString("created_at"), style = MaterialTheme.typography.labelSmall)
                }
              }
            }
            if (ticket.optString("status").equals("resolved", true)) {
              Text(
                appText(
                  "This ticket is resolved. Create a new support ticket if you need further help.",
                  "یہ ٹکٹ حل ہو چکا ہے۔ مزید مدد کے لیے نیا سپورٹ ٹکٹ بنائیں۔"
                ),
                color = MaterialTheme.colorScheme.secondary
              )
            } else {
              OutlinedTextField(
                value = supportReply,
                onValueChange = { supportReply = it.take(4000) },
                label = { Text(appText("Write your message", "اپنا پیغام لکھیں")) },
                placeholder = { Text(appText("Write your message…", "اپنا پیغام لکھیں…")) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
              )
              Button(enabled = !saving && supportReply.isNotBlank(), onClick = {
                scope.launch {
                  saving = true
                  try {
                    VendorService.sendSupportMessage(ticket.optString("id"), supportReply)
                    supportReply = ""
                    supportMessages = VendorService.listSupportMessages(ticket.optString("id"))
                    message = AppLanguageManager.tr("Support reply sent.", "سپورٹ جواب بھیج دیا گیا۔")
                  } catch (e: Exception) {
                    errorMessage = e.message ?: AppLanguageManager.tr("Support reply could not be sent.", "سپورٹ جواب بھیجا نہیں جا سکا۔")
                  }
                  finally { saving = false }
                }
              }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) {
                Text(appText("Send", "بھیجیں"))
              }
            }
          }
        }
      }
    }
    OutlinedButton(onClick = { reload() }, modifier = Modifier.fillMaxWidth()) {
      Text(appText("Refresh vendor tools", "وینڈر ٹولز ریفریش کریں"))
    }
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
  var businessType by remember { mutableStateOf(application.businessType) }
  var cnic by remember { mutableStateOf(application.cnicNumber) }
  var phone by remember { mutableStateOf(application.phone) }
  var currentAddress by remember { mutableStateOf(application.currentAddress) }
  var completeAddress by remember { mutableStateOf(application.completeAddress) }
  var locationText by remember { mutableStateOf(application.shopLocationText) }
  var saving by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }
  var message by remember { mutableStateOf("") }
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    VendorAdvancedField(appText("Shop name", "دکان کا نام"), shopName) { shopName = it }
    VendorAdvancedField(appText("Shop URL slug", "دکان کا یو آر ایل سلگ"), slug) { slug = it }
    VendorAdvancedField(appText("Shop description", "دکان کی تفصیل"), description, 3) { description = it }
    VendorAdvancedField(appText("Owner full legal name", "مالک کا مکمل قانونی نام"), ownerName) { ownerName = it }
    VendorTypeDropdown(selectedType = vendorType, onTypeSelected = { vendorType = it })
    BusinessTypeDropdown(
      selectedType = businessType,
      onTypeSelected = { businessType = it },
      modifier = Modifier.testTag("resubmit_business_type_dropdown")
    )
    VendorAdvancedField(appText("CNIC number (13 digits)", "شناختی کارڈ نمبر (13 ہندسے)"), cnic) { cnic = it.filter(Char::isDigit).take(13) }
    VendorAdvancedField(appText("Business phone", "کاروباری فون"), phone) { phone = it }
    VendorAdvancedField(appText("Current address", "موجودہ پتہ"), currentAddress) { currentAddress = it }
    VendorAdvancedField(appText("Complete address", "مکمل پتہ"), completeAddress, 2) { completeAddress = it }
    VendorAdvancedField(appText("Shop location / verification address", "دکان کا مقام / تصدیقی پتہ"), locationText, 2) { locationText = it }
    if (errorMessage.isNotBlank()) Text(errorMessage, color = MaterialTheme.colorScheme.error)
    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
    Button(enabled = !saving, onClick = {
      scope.launch {
        saving = true; errorMessage = ""; message = ""
        try {
          require(shopName.trim().length >= 2 && description.trim().length >= 10 && ownerName.trim().length >= 3) {
            AppLanguageManager.tr("Complete shop and owner details.", "دکان اور مالک کی تفصیلات مکمل کریں۔")
          }
          require(VendorBusinessType.isValid(businessType)) {
            AppLanguageManager.tr("Select what type of business you operate.", "آپ کس قسم کا کاروبار کرتے ہیں، یہ منتخب کریں۔")
          }
          require(slug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) {
            AppLanguageManager.tr("Enter a valid URL slug.", "درست یو آر ایل سلگ درج کریں۔")
          }
          require(cnic.length == 13 && phone.matches(Regex("^(03[0-9]{9}|\\+923[0-9]{9})$"))) {
            AppLanguageManager.tr("Check CNIC and Pakistani mobile number.", "شناختی کارڈ اور پاکستانی موبائل نمبر چیک کریں۔")
          }
          require(currentAddress.trim().length >= 3 && completeAddress.trim().length >= 10 && locationText.trim().length >= 10) {
            AppLanguageManager.tr("Complete all address fields.", "تمام پتہ کے خانے مکمل کریں۔")
          }
          VendorService.updateOwnShop(JSONObject().put("shop_name", shopName.trim()).put("slug", slug.trim())
            .put("description", description.trim()).put("owner_full_name", ownerName.trim())
            .put("vendor_type", vendorType).put("business_type", businessType.trim()).put("cnic_number", cnic)
            .put("phone", phone.trim()).put("current_address", currentAddress.trim())
            .put("complete_address", completeAddress.trim()).put("shop_location_text", locationText.trim()))
          val updated = VendorService.resubmitApplication()
          message = AppLanguageManager.tr("Application resubmitted for review.", "درخواست نظرثانی کے لیے دوبارہ جمع کرا دی گئی۔")
          onResubmitted(updated)
        } catch (e: Exception) {
          errorMessage = e.message ?: AppLanguageManager.tr("Application could not be resubmitted.", "درخواست دوبارہ جمع نہیں ہو سکی۔")
        }
        finally { saving = false }
      }
    }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange), modifier = Modifier.fillMaxWidth()) {
      Text(if (saving) appText("Submitting...", "جمع ہو رہا ہے...") else appText("Save and resubmit application", "محفوظ کریں اور درخواست دوبارہ جمع کرائیں"))
    }
  }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorTypeDropdown(
  selectedType: String,
  onTypeSelected: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val types = listOf(
    "individual_seller" to appText("Individual Seller", "انفرادی فروخت کنندہ"),
    "registered_business" to appText("Registered Business", "رجسٹرڈ کاروبار"),
    "sole_proprietor" to appText("Sole Proprietor", "واحد ملکیت"),
    "partnership" to appText("Partnership", "شراکت داری"),
    "company" to appText("Company", "کمپنی")
  )
  var expanded by remember { mutableStateOf(false) }
  val selectedLabel = types.firstOrNull { it.first == selectedType }?.second
    ?: appText("Select legal business structure", "کاروبار کی قانونی حیثیت منتخب کریں")

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = !expanded },
    modifier = modifier.fillMaxWidth()
  ) {
    OutlinedTextField(
      value = selectedLabel,
      onValueChange = {},
      readOnly = true,
      label = { Text(appText("Legal business structure *", "کاروبار کی قانونی حیثیت *")) },
      placeholder = { Text(appText("Select legal business structure", "کاروبار کی قانونی حیثیت منتخب کریں")) },
      leadingIcon = {
        Icon(
          imageVector = Icons.Rounded.Badge,
          contentDescription = null,
          tint = VendorColors.TextSecondary,
          modifier = Modifier.size(20.dp)
        )
      },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier.fillMaxWidth().menuAnchor().testTag("vendor_type_dropdown"),
      singleLine = true,
      shape = VendorShapes.input,
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = VendorColors.PrimaryOrange,
        focusedLabelColor = VendorColors.PrimaryOrange,
        unfocusedBorderColor = VendorColors.BorderSubtle,
        unfocusedLabelColor = VendorColors.TextSecondary,
        focusedContainerColor = VendorColors.CardWhite,
        unfocusedContainerColor = VendorColors.CardWhite
      )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessTypeDropdown(
  selectedType: String,
  onTypeSelected: (String) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  supportingText: String? = null
) {
  var expanded by remember { mutableStateOf(false) }
  val selectedOption = VendorBusinessType.fromKey(selectedType)
  val selectedLabel = selectedOption?.localizedLabel().orEmpty()

  ExposedDropdownMenuBox(
    expanded = expanded && enabled,
    onExpandedChange = { if (enabled) expanded = !expanded },
    modifier = modifier.fillMaxWidth()
  ) {
    OutlinedTextField(
      value = selectedLabel,
      onValueChange = {},
      readOnly = true,
      enabled = enabled,
      label = { Text(appText("What type of business do you operate? *", "آپ کس قسم کا کاروبار کرتے ہیں؟ *")) },
      placeholder = { Text(appText("Select business category", "کاروبار کی قسم منتخب کریں")) },
      leadingIcon = {
        Icon(
          imageVector = Icons.Rounded.Storefront,
          contentDescription = null,
          tint = VendorColors.TextSecondary,
          modifier = Modifier.size(20.dp)
        )
      },
      trailingIcon = {
        if (enabled) {
          ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }
      },
      supportingText = supportingText?.takeIf { it.isNotBlank() }?.let { note ->
        { Text(text = note, color = VendorColors.TextSecondary) }
      },
      modifier = Modifier
        .fillMaxWidth()
        .menuAnchor()
        .testTag("vendor_business_type_dropdown"),
      singleLine = true,
      shape = VendorShapes.input,
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = VendorColors.PrimaryOrange,
        focusedLabelColor = VendorColors.PrimaryOrange,
        unfocusedBorderColor = VendorColors.BorderSubtle,
        unfocusedLabelColor = VendorColors.TextSecondary,
        disabledBorderColor = VendorColors.BorderSubtle,
        disabledTextColor = VendorColors.TextPrimary,
        disabledLabelColor = VendorColors.TextSecondary,
        disabledLeadingIconColor = VendorColors.TextSecondary,
        disabledContainerColor = VendorColors.Background,
        focusedContainerColor = VendorColors.CardWhite,
        unfocusedContainerColor = VendorColors.CardWhite
      )
    )
    if (enabled) {
      ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
      ) {
        VendorBusinessType.entries.forEach { option ->
          DropdownMenuItem(
            text = { Text(option.localizedLabel()) },
            onClick = {
              onTypeSelected(option.key)
              expanded = false
            },
            modifier = Modifier.testTag("business_type_option_${option.key}"),
            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
          )
        }
      }
    }
  }
}

@Composable
private fun VendorAdvancedField(label: String, value: String, lines: Int = 1, onChange: (String) -> Unit) {
  OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(label) },
    modifier = Modifier.fillMaxWidth(),
    minLines = lines,
    singleLine = lines == 1,
    shape = VendorShapes.input,
    colors = OutlinedTextFieldDefaults.colors(
      focusedBorderColor = VendorColors.PrimaryOrange,
      focusedLabelColor = VendorColors.PrimaryOrange,
      unfocusedBorderColor = VendorColors.BorderSubtle,
      unfocusedLabelColor = VendorColors.TextSecondary,
      focusedContainerColor = VendorColors.CardWhite,
      unfocusedContainerColor = VendorColors.CardWhite
    )
  )
}
