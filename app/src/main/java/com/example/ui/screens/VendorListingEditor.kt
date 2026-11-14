package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import java.util.UUID

private data class ListingVariant(
  val optionName: String = "Size",
  val optionValue: String = "",
  val sku: String = "",
  val price: String = "",
  val stock: String = "0"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorListingEditor(onCancel: () -> Unit, onSaved: suspend (String) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var categories by remember { mutableStateOf(JSONArray()) }
  var categoryId by remember { mutableStateOf("") }
  var name by remember { mutableStateOf("") }
  var slug by remember { mutableStateOf("") }
  var sku by remember { mutableStateOf("") }
  var price by remember { mutableStateOf("") }
  var discount by remember { mutableStateOf("0") }
  var weight by remember { mutableStateOf("") }
  var supplierCost by remember { mutableStateOf("") }
  var fulfilment by remember { mutableStateOf("0") }
  var minimumProfit by remember { mutableStateOf("0") }
  var shortDescription by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var specifications by remember { mutableStateOf("[]") }
  var dimensions by remember { mutableStateOf("{}") }
  var seoTitle by remember { mutableStateOf("") }
  var seoDescription by remember { mutableStateOf("") }
  var trackInventory by remember { mutableStateOf(true) }
  var initialStock by remember { mutableStateOf("0") }
  var foodUnit by remember { mutableStateOf("kg") }
  var foodUnitPrice by remember { mutableStateOf("") }
  var foodQuantityStep by remember { mutableStateOf("250") }
  var foodMinimumSteps by remember { mutableStateOf("1") }
  var foodDailyItem by remember { mutableStateOf(false) }
  var foodDailyStatus by remember { mutableStateOf("available") }
  var foodDailyQuantity by remember { mutableStateOf("0") }
  var variants by remember { mutableStateOf(listOf<ListingVariant>()) }
  var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
  var saving by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }
  var message by remember { mutableStateOf("") }

  LaunchedEffect(Unit) {
    try { categories = VendorService.listCategories() }
    catch (e: Exception) { errorMessage = e.message ?: "Categories could not be loaded." }
  }

  val isFoodCategory = remember(categories, categoryId) { categoryTreeContainsFood(categories, categoryId) }

  val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selected ->
    val remaining = (8 - images.size).coerceAtLeast(0)
    images = (images + selected.take(remaining)).distinct().take(8)
    if (selected.size > remaining) errorMessage = "You can add up to 8 images total."
  }

  fun fieldNumber(value: String, label: String, allowDecimal: Boolean = true): Double {
    val parsed = value.toDoubleOrNull() ?: throw IllegalArgumentException("Enter a valid $label.")
    require(parsed >= 0) { "$label cannot be negative." }
    if (!allowDecimal) require(parsed % 1.0 == 0.0) { "$label must be a whole number." }
    return parsed
  }

  Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.70f), RoundedCornerShape(26.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("Create product", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF17243A), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    Text("Add product information, pricing, images and stock details.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF718096))
    Text("PRODUCT INFORMATION", color = JTOrange, style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    Text("Product name", style = MaterialTheme.typography.labelLarge)
    OutlinedTextField(name, { name = it; if (slug.isBlank() || slug == nameToSlug(name.dropLast(1))) slug = nameToSlug(it) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    ListingField("Slug", slug) { slug = it.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9-]+"), "-").trim('-') }
    ListingField("SKU", sku) { sku = it }
    Text("CATEGORY", color = JTOrange, style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    Text("Category *", style = MaterialTheme.typography.labelLarge)
    var categoryExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = !categoryExpanded }) {
      val selectedCategory = (0 until categories.length()).mapNotNull { categories.optJSONObject(it) }.firstOrNull { it.optString("id") == categoryId }
      OutlinedTextField(selectedCategory?.optString("name") ?: "", {}, readOnly = true, label = { Text("Select category") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
      ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
        for (i in 0 until categories.length()) {
          val category = categories.optJSONObject(i) ?: continue
          DropdownMenuItem(text = { Text(category.optString("name")) }, onClick = { categoryId = category.optString("id"); categoryExpanded = false })
        }
      }
    }
    if (isFoodCategory) {
      Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.72f)), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.95f)), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Food & groceries", style = MaterialTheme.typography.titleMedium)
          var unitExpanded by remember { mutableStateOf(false) }
          ExposedDropdownMenuBox(expanded = unitExpanded, onExpandedChange = { unitExpanded = !unitExpanded }) {
            OutlinedTextField(foodUnit, {}, readOnly = true, label = { Text("Selling unit") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
            ExposedDropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
              listOf("kg", "g", "l", "ml", "piece", "dozen", "pack").forEach { unit ->
                val label = when(unit) { "kg" -> "Kilogram (kg)"; "g" -> "Gram (g)"; "l" -> "Litre (L)"; "ml" -> "Millilitre (ml)"; "piece" -> "Piece"; "dozen" -> "Dozen"; else -> "Pack" }
                DropdownMenuItem(text = { Text(label) }, onClick = { foodUnit = unit; unitExpanded = false })
              }
            }
          }
          ListingField("Price per selling unit (PKR)", foodUnitPrice) { foodUnitPrice = it.filter { ch -> ch.isDigit() || ch == '.' } }
          ListingField("Customer quantity step (grams / ml / units)", foodQuantityStep) { foodQuantityStep = it.filter(Char::isDigit) }
          ListingField("Minimum purchase steps", foodMinimumSteps) { foodMinimumSteps = it.filter(Char::isDigit) }
          Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = foodDailyItem, onCheckedChange = { foodDailyItem = it })
            Text("Manage as a daily dish / daily food item")
          }
          var dailyStatusExpanded by remember { mutableStateOf(false) }
          ExposedDropdownMenuBox(expanded = dailyStatusExpanded, onExpandedChange = { dailyStatusExpanded = !dailyStatusExpanded }) {
            val statusLabel = when(foodDailyStatus) { "not_today" -> "Not for today"; "coming_soon" -> "Coming soon"; else -> "Available today" }
            OutlinedTextField(statusLabel, {}, readOnly = true, label = { Text("Daily availability status") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dailyStatusExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
            ExposedDropdownMenu(expanded = dailyStatusExpanded, onDismissRequest = { dailyStatusExpanded = false }) {
              listOf("available" to "Available today", "not_today" to "Not for today", "coming_soon" to "Coming soon").forEach { (value,label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { foodDailyStatus = value; dailyStatusExpanded = false })
              }
            }
          }
          ListingField("Today's available quantity", foodDailyQuantity) { foodDailyQuantity = it.filter(Char::isDigit) }
        }
      }
    }

    Text("PRICING & STOCK", color = JTOrange, style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    ListingField("Selling price (PKR) *", price) { price = it.filter { c -> c.isDigit() || c == '.' } }
    ListingField("Discount (%)", discount) { discount = it.filter { c -> c.isDigit() || c == '.' } }
    ListingField("Shipping weight (grams)", weight) { weight = it.filter(Char::isDigit) }
    ListingField("Supplier cost (private)", supplierCost) { supplierCost = it.filter { c -> c.isDigit() || c == '.' } }
    ListingField("Fulfilment expense", fulfilment) { fulfilment = it.filter { c -> c.isDigit() || c == '.' } }
    ListingField("Minimum profit", minimumProfit) { minimumProfit = it.filter { c -> c.isDigit() || c == '.' } }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Column(Modifier.weight(1f)) {
        Text("PRODUCT VARIANTS", color = JTOrange, style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text("Product variants", style = MaterialTheme.typography.titleMedium)
        Text("Each option can have its own SKU, price and stock.", style = MaterialTheme.typography.bodySmall)
      }
      TextButton(onClick = { variants = variants + ListingVariant() }) { Text("Add variant") }
    }
    variants.forEachIndexed { index, variant ->
      Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.72f)), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.95f)), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          ListingField("Option name", variant.optionName) { v -> variants = variants.toMutableList().also { it[index] = variant.copy(optionName = v) } }
          ListingField("Option value", variant.optionValue) { v -> variants = variants.toMutableList().also { it[index] = variant.copy(optionValue = v) } }
          ListingField("Variant SKU", variant.sku) { v -> variants = variants.toMutableList().also { it[index] = variant.copy(sku = v) } }
          ListingField("Price override (PKR) — blank uses base price", variant.price) { v -> variants = variants.toMutableList().also { it[index] = variant.copy(price = v) } }
          ListingField("Stock quantity", variant.stock) { v -> variants = variants.toMutableList().also { it[index] = variant.copy(stock = v.filter(Char::isDigit)) } }
          TextButton(onClick = { variants = variants.filterIndexed { i, _ -> i != index } }) { Text("Remove variant") }
        }
      }
    }
    Text("Variant changes are sent to JTBazar moderation before publication. Supplier costs and fulfilment details remain private.", style = MaterialTheme.typography.bodySmall)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("PRODUCT IMAGES", color = JTOrange, style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
      Text("Product images (${images.size}/8)", style = MaterialTheme.typography.titleMedium)
      TextButton(enabled = images.size < 8, onClick = { imagePicker.launch("image/*") }) { Text("Select images") }
    }
    images.forEachIndexed { index, uri ->
      Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          AsyncImage(
            model = uri,
            contentDescription = "Product image ${index + 1}",
            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
          )
          Column(Modifier.weight(1f)) {
            Text("Product image ${index + 1}", style = MaterialTheme.typography.labelLarge)
            Text(uri.lastPathSegment ?: "Selected image", style = MaterialTheme.typography.bodySmall, maxLines = 2)
            TextButton(onClick = { images = images.filterIndexed { i, _ -> i != index } }) { Text("Remove image") }
          }
        }
      }
    }
    Text("Select multiple JPG, PNG, WebP or AVIF images · maximum 10 MB each · up to 8 images total.", style = MaterialTheme.typography.bodySmall)

    Text("DESCRIPTION & SEARCH", color = JTOrange, style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    ListingField("Short description", shortDescription, 2) { shortDescription = it }
    ListingField("Full description", description, 4) { description = it }
    ListingField("Specifications (JSON array)", specifications, 3) { specifications = it }
    ListingField("Dimensions (JSON object)", dimensions, 3) { dimensions = it }
    ListingField("SEO title", seoTitle) { seoTitle = it }
    ListingField("SEO description", seoDescription, 2) { seoDescription = it }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
      Checkbox(trackInventory, { trackInventory = it })
      Text("Track inventory for this listing")
    }
    if (trackInventory) ListingField("Initial stock quantity", initialStock) { initialStock = it.filter(Char::isDigit) }
    if (errorMessage.isNotBlank()) Text(errorMessage, color = MaterialTheme.colorScheme.error)
    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
    Button(enabled = !saving, onClick = {
      scope.launch {
        saving = true; errorMessage = ""; message = ""
        var createdProductId: String? = null
        try {
          require(name.trim().length >= 2) { "Enter a product name." }
          val baseSlug = slug.ifBlank { "product" }
          // Marketplace product slugs are globally unique across all sellers.
          val productSlug = "$baseSlug-${UUID.randomUUID().toString().take(6)}"
          require(productSlug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) { "Enter a valid slug." }
          require(categoryId.isNotBlank()) { "Select a category." }
          val basePrice = fieldNumber(price, "selling price")
          require(basePrice > 0) { "Selling price must be greater than zero." }
          val foodUnitPriceValue = if (isFoodCategory) fieldNumber(foodUnitPrice, "food unit price") else 0.0
          if (isFoodCategory) {
            require(foodUnitPriceValue > 0) { "Enter the food price per selling unit." }
            require(fieldNumber(foodQuantityStep, "customer quantity step", false) >= 1) { "Customer quantity step must be at least 1." }
            require(fieldNumber(foodMinimumSteps, "minimum purchase steps", false) >= 1) { "Minimum purchase steps must be at least 1." }
            fieldNumber(foodDailyQuantity, "today's available quantity", false)
          }
          val discountValue = fieldNumber(discount, "discount")
          require(discountValue <= 100) { "Discount cannot exceed 100%." }
          val supplierCostValue = supplierCost.trim().takeIf { it.isNotEmpty() }?.let { fieldNumber(it, "supplier cost") }
          val fulfilmentValue = fieldNumber(fulfilment, "fulfilment expense")
          val minimumProfitValue = fieldNumber(minimumProfit, "minimum profit")
          require(basePrice >= (supplierCostValue ?: 0.0) + fulfilmentValue + minimumProfitValue) {
            "Selling price must cover supplier cost, fulfilment expense and minimum profit."
          }
          val variantJson = JSONArray()
          val seen = mutableSetOf<String>()
          variants.forEach { v ->
            require(v.optionName.isNotBlank() && v.optionValue.isNotBlank()) { "Complete every variant option." }
            require(seen.add("${v.optionName.trim().lowercase()}::${v.optionValue.trim().lowercase()}")) { "Duplicate variant option and value." }
            val stock = fieldNumber(v.stock, "variant stock", false).toInt()
            val row = JSONObject().put("option_label", v.optionName.trim()).put("option_value", v.optionValue.trim())
              .put("sku", if (v.sku.isBlank()) JSONObject.NULL else v.sku.trim())
              .put("price_override", if (v.price.isBlank()) JSONObject.NULL else fieldNumber(v.price, "variant price"))
              .put("attributes", JSONObject()).put("quantity", stock)
            variantJson.put(row)
          }
          val specs = JSONArray(specifications)
          val dims = JSONObject(dimensions)
          val id = VendorService.createProduct(
            name.trim(), productSlug, description, basePrice.toInt(),
            if (trackInventory) fieldNumber(initialStock, "initial stock", false).toInt() else 0,
            categoryId, sku.trim().ifBlank { null }, shortDescription, supplierCostValue,
            fulfilmentValue, minimumProfitValue,
            discountValue, weight.toIntOrNull(), specs, dims, seoTitle.trim().ifBlank { null },
            seoDescription.trim().ifBlank { null }, trackInventory
          )
          createdProductId = id
          // Save the selected images immediately after product creation. This keeps image
          // persistence independent from optional food/variant metadata calls.
          images.forEachIndexed { index, uri ->
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
              ?: error("Image ${index + 1} could not be read from the selected file.")
            require(bytes.size <= 10 * 1024 * 1024) { "Image ${index + 1} must be 10 MB or smaller." }
            VendorService.uploadProductImage(id, bytes, mime, name)
          }
          if (isFoodCategory) {
            VendorService.updateFoodProductDetails(
              id, foodUnit, foodUnitPriceValue,
              fieldNumber(foodQuantityStep, "customer quantity step", false).toInt(),
              fieldNumber(foodMinimumSteps, "minimum purchase steps", false).toInt(),
              foodDailyItem, foodDailyStatus,
              fieldNumber(foodDailyQuantity, "today's available quantity", false).toInt()
            )
          }
          if (variantJson.length() > 0) VendorService.saveVariants(id, variantJson)
          message = "Listing submitted for moderation."
          onSaved(message)
        } catch (e: Exception) {
          val details = generateSequence(e as Throwable?) { it.cause }.mapNotNull { it.message }.joinToString(" ").lowercase()
          if (createdProductId != null) {
            onSaved("Product created as a draft, but variants or images could not all be saved. Do not submit it again. Please contact JTBazar support: ${e.message ?: "Additional product data failed."}")
          } else errorMessage = when {
            details.contains("products_sku_unique_idx") || details.contains("product_variants_sku_unique_idx") || (details.contains("already exists") && details.contains("sku")) -> "This SKU is already being used. Enter a unique SKU for this product or variant."
            details.contains("products_slug_key") -> "This product URL is already in use. Please change the slug and try again."
            details.contains("row-level security") || details.contains("accessdenied") -> "Image upload was blocked by storage permissions. Please retry; if it continues, contact JTBazar support."
            else -> e.message ?: "Listing could not be submitted."
          }
        }
        finally { saving = false }
      }
    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) {
      Text(if (saving) "Submitting..." else "Submit listing")
    }
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth(), enabled = !saving) { Text("Cancel") }
  }
}

private fun nameToSlug(value: String): String = value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')

@Composable
private fun ListingField(label: String, value: String, lines: Int = 1, onChange: (String) -> Unit) {
  OutlinedTextField(value, onChange, label = { Text(label, style = MaterialTheme.typography.bodySmall) }, modifier = Modifier.fillMaxWidth(), minLines = lines, singleLine = lines == 1, shape = RoundedCornerShape(12.dp), textStyle = MaterialTheme.typography.bodyMedium, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JTOrange, focusedLabelColor = JTOrange, unfocusedBorderColor = Color(0xFFD7DFEA), unfocusedContainerColor = Color.White.copy(alpha = 0.72f), focusedContainerColor = Color.White.copy(alpha = 0.90f)))
}


private fun categoryTreeContainsFood(categories: JSONArray, selectedId: String): Boolean {
  if (selectedId.isBlank()) return false
  val rows = (0 until categories.length()).mapNotNull { categories.optJSONObject(it) }
  fun isFood(row: JSONObject): Boolean {
    val slug = row.optString("slug").trim().lowercase(Locale.ROOT)
    val name = row.optString("name").trim().lowercase(Locale.ROOT)
    val flag = row.opt("is_food_category")
    return row.optBoolean("is_food_category") || flag?.toString()?.equals("true", true) == true ||
      slug in setOf("food-groceries", "food-and-groceries", "food-grocery") ||
      name in setOf("food & groceries", "food and groceries", "home & kitchen")
  }
  val byId = rows.associateBy { it.optString("id").trim().lowercase(Locale.ROOT) }
  var current = byId[selectedId.trim().lowercase(Locale.ROOT)]
  val visited = mutableSetOf<String>()
  while (current != null) {
    if (isFood(current)) return true
    val parentId = current.optString("parent_id").trim().lowercase(Locale.ROOT)
    if (parentId.isBlank() || parentId == "null" || !visited.add(parentId)) break
    current = byId[parentId]
  }
  return false
}
