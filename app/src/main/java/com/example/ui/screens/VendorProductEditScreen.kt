package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.supabase.VendorService
import com.example.ui.theme.JTOrange
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.optCleanString(key: String, fallback: String = ""): String =
  optString(key, fallback).takeUnless { it == "null" } ?: fallback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorProductEditScreen(product: JSONObject, onBack: () -> Unit, onSaved: suspend () -> Unit) {
  BackHandler { onBack() }
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val id = product.optCleanString("id")
  var categories by remember { mutableStateOf(JSONArray()) }
  var existingImageUrls by remember(id) { mutableStateOf<List<String>>(emptyList()) }
  var newImages by remember(id) { mutableStateOf<List<Uri>>(emptyList()) }
  var name by remember(id) { mutableStateOf(product.optCleanString("name")) }
  var slug by remember(id) { mutableStateOf(product.optCleanString("slug")) }
  var sku by remember(id) { mutableStateOf(product.optCleanString("sku")) }
  var category by remember(id) { mutableStateOf(product.optCleanString("category_id")) }
  var shortDescription by remember(id) { mutableStateOf(product.optCleanString("short_description")) }
  var description by remember(id) { mutableStateOf(product.optCleanString("description")) }
  var price by remember(id) {
    mutableStateOf(
      product.optCleanString("vendor_base_price").ifBlank { product.optCleanString("price") }
    )
  }
  var discount by remember(id) { mutableStateOf(product.optCleanString("discount_percent", "0").ifBlank { "0" }) }
  var supplierCost by remember(id) { mutableStateOf(product.optCleanString("supplier_cost")) }
  var fulfilment by remember(id) { mutableStateOf(product.optCleanString("fulfillment_expense", "0").ifBlank { "0" }) }
  var minimumProfit by remember(id) { mutableStateOf(product.optCleanString("minimum_profit", "0").ifBlank { "0" }) }
  var weight by remember(id) { mutableStateOf(product.optCleanString("weight_grams")) }
  var specs by remember(id) { mutableStateOf((product.optJSONArray("specifications") ?: JSONArray()).toString(2)) }
  var dimensions by remember(id) { mutableStateOf((product.optJSONObject("dimensions") ?: JSONObject()).toString(2)) }
  var seoTitle by remember(id) { mutableStateOf(product.optCleanString("seo_title")) }
  var seoDescription by remember(id) { mutableStateOf(product.optCleanString("seo_description")) }
  var foodUnit by remember(id) { mutableStateOf(product.optCleanString("food_selling_unit", "kg").ifBlank { "kg" }) }
  var foodUnitPrice by remember(id) { mutableStateOf(run {
    val savedCustomerUnitPrice = product.optCleanString("food_unit_price").toDoubleOrNull()
    val commission = product.optCleanString("commission_percent", "0").toDoubleOrNull() ?: 0.0
    if (savedCustomerUnitPrice != null && commission >= 0) (savedCustomerUnitPrice / (1.0 + commission / 100.0)).toString() else price
  }) }
  var foodStep by remember(id) { mutableStateOf(product.optCleanString("food_quantity_increment", "250").ifBlank { "250" }) }
  var foodMinimum by remember(id) { mutableStateOf(product.optCleanString("food_minimum_quantity", "1").ifBlank { "1" }) }
  var dailyItem by remember(id) { mutableStateOf(product.optBoolean("is_daily_item", false)) }
  var dailyStatus by remember(id) { mutableStateOf(product.optCleanString("daily_status", "available").ifBlank { "available" }) }
  var dailyQuantity by remember(id) { mutableStateOf(product.optCleanString("daily_available_quantity", "0").ifBlank { "0" }) }
  var saving by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }
  var message by remember { mutableStateOf("") }

  LaunchedEffect(id) {
    runCatching { VendorService.listCategories() }.onSuccess { categories = it }
    if (id.isNotBlank()) {
      runCatching { VendorService.listProductImageUrls(id) }.onSuccess { existingImageUrls = it }
    }
  }

  val totalImages = existingImageUrls.size + newImages.size
  fun isFoodCategory(categoryId: String): Boolean {
    var currentId: String? = categoryId.takeIf { it.isNotBlank() }
    val visited = mutableSetOf<String>()
    while (currentId != null && visited.add(currentId)) {
      val row = (0 until categories.length()).mapNotNull { categories.optJSONObject(it) }
        .firstOrNull { it.optSafeString("id") == currentId } ?: return false
      if (row.optBoolean("is_food_category") || row.optSafeString("is_food_category").equals("true", true) ||
        row.optSafeString("slug").lowercase() in setOf("food-groceries", "food-and-groceries", "food-grocery") ||
        row.optSafeString("name").trim().lowercase() in setOf("food & groceries", "food and groceries", "home & kitchen")) return true
      currentId = row.optSafeString("parent_id").takeIf { it.isNotBlank() && it != "null" }
    }
    return false
  }
  val showFoodFields = isFoodCategory(category) || product.optCleanString("food_selling_unit").isNotBlank() || product.optBoolean("is_daily_item")
  val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selected ->
    val remaining = (8 - existingImageUrls.size - newImages.size).coerceAtLeast(0)
    newImages = (newImages + selected.take(remaining)).distinct().take((8 - existingImageUrls.size).coerceAtLeast(0))
    if (selected.size > remaining) errorMessage = "A product can have up to 8 images total."
  }

  Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("Edit product", style = MaterialTheme.typography.headlineSmall)
      TextButton(onClick = onBack, enabled = !saving) { Text("Back") }
    }
    Text("Changes are sent through JTBazar product moderation. Stock remains managed separately in Inventory.", style = MaterialTheme.typography.bodySmall)
    EditProductField("Product name", name) { name = it }
    EditProductField("URL slug", slug) { slug = it.lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-') }
    EditProductField("SKU", sku) { sku = it }

    var categoryExpanded by remember(id) { mutableStateOf(false) }
    val selectedCategoryName = (0 until categories.length()).mapNotNull { categories.optJSONObject(it) }
      .firstOrNull { it.optSafeString("id") == category }?.optSafeString("name")
      ?: if (category.isBlank()) "Select category" else category
    ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = !categoryExpanded }) {
      OutlinedTextField(
        value = selectedCategoryName,
        onValueChange = {},
        readOnly = true,
        label = { Text("Category") },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
        modifier = Modifier.fillMaxWidth().menuAnchor()
      )
      ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
        for (i in 0 until categories.length()) {
          val row = categories.optJSONObject(i) ?: continue
          DropdownMenuItem(text = { Text(row.optSafeString("name")) }, onClick = {
            category = row.optSafeString("id")
            if (row.optBoolean("is_food_category") || row.optSafeString("slug") == "food-groceries") {
              if (foodUnit.isBlank()) foodUnit = "kg"
              if (foodStep.isBlank()) foodStep = if (foodUnit == "kg" || foodUnit == "l") "250" else "1"
            }
            categoryExpanded = false
          })
        }
      }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("Product images ($totalImages/8)", style = MaterialTheme.typography.titleMedium)
      TextButton(enabled = totalImages < 8 && !saving, onClick = { imagePicker.launch("image/*") }) { Text("Add images") }
    }
    if (existingImageUrls.isNotEmpty() || newImages.isNotEmpty()) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        existingImageUrls.take(4).forEach { url ->
          AsyncImage(
            model = url,
            contentDescription = name,
            modifier = Modifier.size(68.dp).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
          )
        }
      }
      newImages.forEachIndexed { index, uri ->
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
          Row(Modifier.padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(
              model = uri,
              contentDescription = "New product image ${index + 1}",
              modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
              contentScale = ContentScale.Crop
            )
            Column(Modifier.weight(1f)) {
              Text("New image ${index + 1}", style = MaterialTheme.typography.labelLarge)
              TextButton(enabled = !saving, onClick = { newImages = newImages.filterIndexed { i, _ -> i != index } }) { Text("Remove") }
            }
          }
        }
      }
    }

    EditProductField("Short description", shortDescription, 2) { shortDescription = it }
    EditProductField("Full description", description, 4) { description = it }
    EditProductField("Vendor base price (PKR)", price) { price = it.filter { ch -> ch.isDigit() || ch == '.' } }
    EditProductField("Discount (%)", discount) { discount = it.filter { ch -> ch.isDigit() || ch == '.' } }
    EditProductField("Supplier cost (private PKR)", supplierCost) { supplierCost = it.filter { ch -> ch.isDigit() || ch == '.' } }
    EditProductField("Fulfilment expense (PKR)", fulfilment) { fulfilment = it.filter { ch -> ch.isDigit() || ch == '.' } }
    EditProductField("Minimum profit (PKR)", minimumProfit) { minimumProfit = it.filter { ch -> ch.isDigit() || ch == '.' } }
    EditProductField("Shipping weight (grams)", weight) { weight = it.filter(Char::isDigit) }
    EditProductField("Specifications (JSON array)", specs, 3) { specs = it }
    EditProductField("Dimensions (JSON object)", dimensions, 3) { dimensions = it }
    EditProductField("SEO title", seoTitle) { seoTitle = it }
    EditProductField("SEO description", seoDescription, 2) { seoDescription = it }
    if (showFoodFields) {
      HorizontalDivider()
      Text("Food & groceries", style = MaterialTheme.typography.titleMedium)
      var unitExpanded by remember(id) { mutableStateOf(false) }
      val units = listOf("kg" to "Kilogram (kg)", "g" to "Gram (g)", "l" to "Litre (L)", "ml" to "Millilitre (ml)", "piece" to "Piece", "dozen" to "Dozen", "pack" to "Pack")
      ExposedDropdownMenuBox(expanded = unitExpanded, onExpandedChange = { unitExpanded = !unitExpanded }) {
        OutlinedTextField(value = units.firstOrNull { it.first == foodUnit }?.second ?: "Kilogram (kg)", onValueChange = {}, readOnly = true, label = { Text("Selling unit") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
        ExposedDropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
          units.forEach { (value, label) -> DropdownMenuItem(text = { Text(label) }, onClick = {
            foodUnit = value
            foodStep = if (value == "kg" || value == "l") "250" else if (value == "g" || value == "ml") "100" else "1"
            unitExpanded = false
          }) }
        }
      }
      EditProductField("Price per ${foodUnit} (PKR)", foodUnitPrice) { foodUnitPrice = it.filter { ch -> ch.isDigit() || ch == '.' } }
      EditProductField("Customer quantity step (${if (foodUnit == "kg") "grams" else if (foodUnit == "l") "ml" else "units"})", foodStep) { foodStep = it.filter(Char::isDigit) }
      Text("For kg/litre the step is grams/ml; for dozen, piece and pack it is units.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      EditProductField("Minimum purchase steps", foodMinimum) { foodMinimum = it.filter(Char::isDigit) }
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().clickable { dailyItem = !dailyItem }.padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Checkbox(checked = dailyItem, onCheckedChange = { dailyItem = it })
          Text(
            "Manage as a daily dish / daily food item",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
          )
        }
      }
      var statusExpanded by remember(id) { mutableStateOf(false) }
      val statuses = listOf("available" to "Available today", "not_today" to "Not for today", "coming_soon" to "Coming soon")
      ExposedDropdownMenuBox(expanded = statusExpanded, onExpandedChange = { statusExpanded = !statusExpanded }) {
        OutlinedTextField(value = statuses.firstOrNull { it.first == dailyStatus }?.second ?: "Available today", onValueChange = {}, readOnly = true, label = { Text("Daily availability status") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(statusExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
        ExposedDropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
          statuses.forEach { (value, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { dailyStatus = value; statusExpanded = false }) }
        }
      }
      EditProductField("Today's available quantity", dailyQuantity) { dailyQuantity = it.filter(Char::isDigit) }
    }
    if (errorMessage.isNotBlank()) Text(errorMessage, color = MaterialTheme.colorScheme.error)
    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
    Button(enabled = !saving, onClick = {
      scope.launch {
        saving = true; errorMessage = ""; message = ""
        try {
          val basePrice = (if (showFoodFields) foodUnitPrice else price).toDoubleOrNull() ?: error(if (showFoodFields) "Enter a valid food unit price." else "Enter a valid price.")
          val discountValue = discount.toDoubleOrNull() ?: error("Enter a valid discount.")
          require(discountValue in 0.0..100.0) { "Discount must be between 0 and 100%." }
          val payload = JSONObject()
            .put("p_category_id", category.ifBlank { JSONObject.NULL })
            .put("p_name", name.trim()).put("p_slug", slug.trim())
            .put("p_sku", sku.trim().ifBlank { JSONObject.NULL })
            .put("p_short_description", shortDescription.trim())
            .put("p_description", description.trim()).put("p_price", basePrice)
            .put("p_supplier_cost", supplierCost.toDoubleOrNull() ?: JSONObject.NULL)
            .put("p_fulfillment_expense", fulfilment.toDoubleOrNull() ?: error("Enter valid fulfilment expense."))
            .put("p_minimum_profit", minimumProfit.toDoubleOrNull() ?: error("Enter valid minimum profit."))
            .put("p_discount_percent", discountValue)
            .put("p_weight_grams", weight.toIntOrNull() ?: JSONObject.NULL)
            .put("p_specifications", JSONArray(specs)).put("p_dimensions", JSONObject(dimensions))
            .put("p_seo_title", seoTitle.trim().ifBlank { JSONObject.NULL })
            .put("p_seo_description", seoDescription.trim().ifBlank { JSONObject.NULL })
          VendorService.updateProductFull(id, payload)
          if (showFoodFields) {
            val step = foodStep.toIntOrNull() ?: error("Enter a valid customer quantity step.")
            val minimum = foodMinimum.toIntOrNull() ?: error("Enter valid minimum purchase steps.")
            val quantity = dailyQuantity.toIntOrNull() ?: error("Enter a valid daily available quantity.")
            require(step > 0 && minimum > 0 && quantity >= 0) { "Check food quantity settings." }
            VendorService.updateFoodProductDetails(id, foodUnit, basePrice, step, minimum, dailyItem, dailyStatus, quantity)
          }
          newImages.forEach { uri ->
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Image could not be read.")
            require(bytes.size <= 10 * 1024 * 1024) { "Each image must be 10 MB or smaller." }
            VendorService.uploadProductImage(id, bytes, mime, name.trim())
          }
          message = "Product details saved."
          onSaved()
        } catch (e: Exception) { errorMessage = e.message ?: "Product could not be updated." }
        finally { saving = false }
      }
    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) {
      Text(if (saving) "Saving…" else "Save product")
    }
    OutlinedButton(onClick = onBack, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
  }
}

private fun JSONObject.optSafeString(key: String, fallback: String = ""): String =
  optString(key, fallback).takeUnless { it == "null" } ?: fallback

@Composable
private fun EditProductField(label: String, value: String, lines: Int = 1, onChange: (String) -> Unit) {
  OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), minLines = lines, singleLine = lines == 1)
}
