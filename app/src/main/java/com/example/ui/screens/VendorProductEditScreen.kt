package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import java.util.Locale

private fun JSONObject.vendorEditCleanString(key: String, fallback: String = ""): String =
  optString(key, fallback).takeUnless { it == "null" } ?: fallback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorProductEditScreen(product: JSONObject, onBack: () -> Unit, onSaved: suspend () -> Unit) {
  BackHandler { onBack() }
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val id = product.vendorEditCleanString("id")
  var categories by remember { mutableStateOf(JSONArray()) }
  var existingImageUrls by remember(id) { mutableStateOf<List<String>>(emptyList()) }
  var imagesLoading by remember(id) { mutableStateOf(true) }
  var imageLoadError by remember(id) { mutableStateOf("") }
  var newImages by remember(id) { mutableStateOf<List<Uri>>(emptyList()) }
  var name by remember(id) { mutableStateOf(product.vendorEditCleanString("name")) }
  var slug by remember(id) { mutableStateOf(product.vendorEditCleanString("slug")) }
  var sku by remember(id) { mutableStateOf(product.vendorEditCleanString("sku")) }
  var category by remember(id) { mutableStateOf(product.vendorEditCleanString("category_id")) }
  var shortDescription by remember(id) { mutableStateOf(product.vendorEditCleanString("short_description")) }
  var description by remember(id) { mutableStateOf(product.vendorEditCleanString("description")) }
  var price by remember(id) {
    mutableStateOf(
      product.vendorEditCleanString("vendor_base_price").ifBlank { product.vendorEditCleanString("price") }
    )
  }
  var discount by remember(id) { mutableStateOf(product.vendorEditCleanString("discount_percent", "0").ifBlank { "0" }) }
  var supplierCost by remember(id) { mutableStateOf(product.vendorEditCleanString("supplier_cost")) }
  var fulfilment by remember(id) { mutableStateOf(product.vendorEditCleanString("fulfillment_expense", "0").ifBlank { "0" }) }
  var minimumProfit by remember(id) { mutableStateOf(product.vendorEditCleanString("minimum_profit", "0").ifBlank { "0" }) }
  var weight by remember(id) { mutableStateOf(product.vendorEditCleanString("weight_grams")) }
  var specs by remember(id) { mutableStateOf((product.optJSONArray("specifications") ?: JSONArray()).toString(2)) }
  var dimensions by remember(id) { mutableStateOf((product.optJSONObject("dimensions") ?: JSONObject()).toString(2)) }
  var seoTitle by remember(id) { mutableStateOf(product.vendorEditCleanString("seo_title")) }
  var seoDescription by remember(id) { mutableStateOf(product.vendorEditCleanString("seo_description")) }
  var foodUnit by remember(id) { mutableStateOf(product.vendorEditCleanString("food_selling_unit", "kg").ifBlank { "kg" }) }
  var foodUnitPrice by remember(id) { mutableStateOf(run {
    val savedCustomerUnitPrice = product.vendorEditCleanString("food_unit_price").toDoubleOrNull()
    val commission = product.vendorEditCleanString("commission_percent", "0").toDoubleOrNull() ?: 0.0
    if (savedCustomerUnitPrice != null && commission >= 0) (savedCustomerUnitPrice / (1.0 + commission / 100.0)).toString() else price
  }) }
  var foodStep by remember(id) { mutableStateOf(product.vendorEditCleanString("food_quantity_increment", "250").ifBlank { "250" }) }
  var foodMinimum by remember(id) { mutableStateOf(product.vendorEditCleanString("food_minimum_quantity", "1").ifBlank { "1" }) }
  var dailyItem by remember(id) { mutableStateOf(product.optBoolean("is_daily_item", false)) }
  var dailyStatus by remember(id) { mutableStateOf(product.vendorEditCleanString("daily_status", "available").ifBlank { "available" }) }
  var dailyQuantity by remember(id) { mutableStateOf(product.vendorEditCleanString("daily_available_quantity", "0").ifBlank { "0" }) }
  var saving by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }
  var message by remember { mutableStateOf("") }

  LaunchedEffect(id) {
    runCatching { VendorService.listCategories() }.onSuccess { categories = it }
    if (id.isNotBlank()) {
      imagesLoading = true
      imageLoadError = ""
      runCatching { VendorService.listProductImageUrls(id) }
        .onSuccess { existingImageUrls = it }
        .onFailure { imageLoadError = it.message ?: "Product images could not be loaded." }
      imagesLoading = false
    } else imagesLoading = false
  }

  val totalImages = existingImageUrls.size + newImages.size
  // Food controls depend only on the currently selected category.
  // Legacy food metadata must not keep food fields active after category change.
  val showFoodFields = remember(categories, category) { categoryTreeContainsFood(categories, category) }
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
            val selectedIsFood = categoryTreeContainsFood(categories, row.optSafeString("id"))
            if (selectedIsFood) {
              if (foodUnit.isBlank()) foodUnit = "kg"
              if (foodStep.isBlank()) foodStep = if (foodUnit == "kg" || foodUnit == "l") "250" else "1"
            } else {
              dailyItem = false
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
    if (imagesLoading) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text("Loading saved images…", style = MaterialTheme.typography.bodySmall)
      }
    }
    if (imageLoadError.isNotBlank()) {
      Text(imageLoadError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    if (existingImageUrls.isNotEmpty() || newImages.isNotEmpty()) {
      Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        existingImageUrls.forEachIndexed { index, url ->
          Column(Modifier.width(104.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AsyncImage(
              model = url,
              contentDescription = "Saved product image ${index + 1}",
              modifier = Modifier.size(104.dp).clip(RoundedCornerShape(12.dp)),
              contentScale = ContentScale.Crop,
              onError = { imageLoadError = "A saved product image could not be displayed. Check its Supabase Storage URL and bucket access." }
            )
            Text(if (index == 0) "Main image" else "Saved image ${index + 1}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
          }
        }
        newImages.forEachIndexed { index, uri ->
          Card(Modifier.width(104.dp), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
              AsyncImage(
                model = uri,
                contentDescription = "New product image ${index + 1}",
                modifier = Modifier.size(90.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
              )
              Text("New ${index + 1}", style = MaterialTheme.typography.labelSmall)
              TextButton(
                enabled = !saving,
                onClick = { newImages = newImages.filterIndexed { i, _ -> i != index } },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
              ) { Text("Remove") }
            }
          }
        }
      }
    } else if (!imagesLoading && imageLoadError.isBlank()) {
      Text("No saved product images yet. Add images to attach them to this listing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
          existingImageUrls = VendorService.listProductImageUrls(id)
          newImages = emptyList()
          message = "Product details and images saved."
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


private fun categoryTreeContainsFood(categories: JSONArray, selectedId: String): Boolean {
  if (selectedId.isBlank()) return false
  val rows = (0 until categories.length()).mapNotNull { categories.optJSONObject(it) }
  val byId = rows.associateBy { it.optSafeString("id").trim().lowercase(Locale.ROOT) }
  var current = byId[selectedId.trim().lowercase(Locale.ROOT)]
  val visited = mutableSetOf<String>()
  while (current != null) {
    val slug = current.optSafeString("slug").trim().lowercase(Locale.ROOT)
    val name = current.optSafeString("name").trim().lowercase(Locale.ROOT)
    val foodFlag = current.opt("is_food_category")?.toString()?.equals("true", true) == true
    if (foodFlag || slug in setOf("food-groceries", "food-and-groceries", "food-grocery", "home-kitchen") ||
      name in setOf("food & groceries", "food and groceries", "home & kitchen")) return true
    val parentId = current.optSafeString("parent_id").trim().lowercase(Locale.ROOT)
    if (parentId.isBlank() || parentId == "null" || !visited.add(parentId)) break
    current = byId[parentId]
  }
  return false
}

@Composable
private fun EditProductField(label: String, value: String, lines: Int = 1, onChange: (String) -> Unit) {
  OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), minLines = lines, singleLine = lines == 1)
}
