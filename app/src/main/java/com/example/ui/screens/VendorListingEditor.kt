package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.supabase.VendorService
import com.example.ui.components.AppCard
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.VendorAlertBanner
import com.example.ui.components.VendorOutlinedTextField
import com.example.ui.theme.VendorColors
import com.example.ui.theme.VendorShapes
import com.example.ui.theme.VendorSpacing
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
fun VendorListingEditor(
  onCancel: () -> Unit,
  onSaved: suspend (String) -> Unit
) {
  BackHandler { onCancel() }
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
    try {
      categories = VendorService.listCategories()
    } catch (e: Exception) {
      errorMessage = e.message ?: "Categories could not be loaded."
    }
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

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(VendorColors.Background)
  ) {
    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      contentPadding = PaddingValues(VendorSpacing.md),
      verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
    ) {
      item {
        SectionHeader(
          title = "Create product",
          subtitle = "Add product information, pricing, images and stock details."
        )
      }

      if (errorMessage.isNotBlank()) {
        item {
          VendorAlertBanner(
            message = errorMessage,
            isError = true,
            onDismiss = { errorMessage = "" }
          )
        }
      }
      if (message.isNotBlank()) {
        item {
          VendorAlertBanner(
            message = message,
            isError = false,
            onDismiss = { message = "" }
          )
        }
      }

      // Product Information
      item {
        AppCard {
          Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
            SectionHeader(title = "Product information")
            VendorOutlinedTextField(
              value = name,
              onValueChange = {
                name = it
                if (slug.isBlank() || slug == nameToSlug(name.dropLast(1))) slug = nameToSlug(it)
              },
              label = "Product name *",
              leadingIcon = Icons.Rounded.Inventory2
            )
            VendorOutlinedTextField(
              value = slug,
              onValueChange = { slug = it.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9-]+"), "-").trim('-') },
              label = "Slug",
              leadingIcon = Icons.Rounded.Link
            )
            VendorOutlinedTextField(
              value = sku,
              onValueChange = { sku = it },
              label = "SKU",
              leadingIcon = Icons.Rounded.QrCode2
            )

            var categoryExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
              expanded = categoryExpanded,
              onExpandedChange = { categoryExpanded = !categoryExpanded }
            ) {
              val selectedCategory = (0 until categories.length())
                .mapNotNull { categories.optJSONObject(it) }
                .firstOrNull { it.optString("id") == categoryId }
              OutlinedTextField(
                value = selectedCategory?.optString("name") ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Select category *", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Rounded.Category,
                    contentDescription = null,
                    tint = VendorColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                  )
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = VendorShapes.input,
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = VendorColors.PrimaryOrange,
                  focusedLabelColor = VendorColors.PrimaryOrange,
                  unfocusedBorderColor = VendorColors.BorderSubtle,
                  focusedContainerColor = VendorColors.CardWhite,
                  unfocusedContainerColor = VendorColors.CardWhite
                )
              )
              ExposedDropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = { categoryExpanded = false }
              ) {
                for (i in 0 until categories.length()) {
                  val category = categories.optJSONObject(i) ?: continue
                  DropdownMenuItem(
                    text = { Text(category.optString("name")) },
                    onClick = {
                      categoryId = category.optString("id")
                      categoryExpanded = false
                    }
                  )
                }
              }
            }
          }
        }
      }

      if (isFoodCategory) {
        item {
          AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
              SectionHeader(title = "Food & groceries")
              var unitExpanded by remember { mutableStateOf(false) }
              ExposedDropdownMenuBox(
                expanded = unitExpanded,
                onExpandedChange = { unitExpanded = !unitExpanded }
              ) {
                OutlinedTextField(
                  value = foodUnit,
                  onValueChange = {},
                  readOnly = true,
                  label = { Text("Selling unit") },
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Rounded.Restaurant,
                      contentDescription = null,
                      tint = VendorColors.TextSecondary,
                      modifier = Modifier.size(20.dp)
                    )
                  },
                  trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitExpanded) },
                  modifier = Modifier.menuAnchor().fillMaxWidth(),
                  shape = VendorShapes.input
                )
                ExposedDropdownMenu(
                  expanded = unitExpanded,
                  onDismissRequest = { unitExpanded = false }
                ) {
                  listOf("kg", "g", "l", "ml", "piece", "dozen", "pack").forEach { unit ->
                    val label = when (unit) {
                      "kg" -> "Kilogram (kg)"
                      "g" -> "Gram (g)"
                      "l" -> "Litre (L)"
                      "ml" -> "Millilitre (ml)"
                      "piece" -> "Piece"
                      "dozen" -> "Dozen"
                      else -> "Pack"
                    }
                    DropdownMenuItem(
                      text = { Text(label) },
                      onClick = { foodUnit = unit; unitExpanded = false }
                    )
                  }
                }
              }
              VendorOutlinedTextField(
                value = foodUnitPrice,
                onValueChange = { foodUnitPrice = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = "Price per selling unit (PKR)",
                leadingIcon = Icons.Rounded.AttachMoney,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
              )
              VendorOutlinedTextField(
                value = foodQuantityStep,
                onValueChange = { foodQuantityStep = it.filter(Char::isDigit) },
                label = "Customer quantity step (grams / ml / units)",
                leadingIcon = Icons.Rounded.Scale,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
              )
              VendorOutlinedTextField(
                value = foodMinimumSteps,
                onValueChange = { foodMinimumSteps = it.filter(Char::isDigit) },
                label = "Minimum purchase steps",
                leadingIcon = Icons.Rounded.Scale,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
              )
              Surface(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(VendorShapes.input)
                  .clickable { foodDailyItem = !foodDailyItem },
                shape = VendorShapes.input,
                color = VendorColors.Background,
                border = BorderStroke(1.dp, VendorColors.BorderSubtle)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = VendorSpacing.sm, vertical = VendorSpacing.xs),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Checkbox(
                    checked = foodDailyItem,
                    onCheckedChange = { foodDailyItem = it },
                    colors = CheckboxDefaults.colors(checkedColor = VendorColors.PrimaryOrange)
                  )
                  Text("Manage as a daily dish / daily food item", style = MaterialTheme.typography.bodyMedium)
                }
              }
              var dailyStatusExpanded by remember { mutableStateOf(false) }
              ExposedDropdownMenuBox(
                expanded = dailyStatusExpanded,
                onExpandedChange = { dailyStatusExpanded = !dailyStatusExpanded }
              ) {
                val statusLabel = when (foodDailyStatus) {
                  "not_today" -> "Not for today"
                  "coming_soon" -> "Coming soon"
                  else -> "Available today"
                }
                OutlinedTextField(
                  value = statusLabel,
                  onValueChange = {},
                  readOnly = true,
                  label = { Text("Daily availability status") },
                  trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dailyStatusExpanded) },
                  modifier = Modifier.menuAnchor().fillMaxWidth(),
                  shape = VendorShapes.input
                )
                ExposedDropdownMenu(
                  expanded = dailyStatusExpanded,
                  onDismissRequest = { dailyStatusExpanded = false }
                ) {
                  listOf(
                    "available" to "Available today",
                    "not_today" to "Not for today",
                    "coming_soon" to "Coming soon"
                  ).forEach { (value, label) ->
                    DropdownMenuItem(
                      text = { Text(label) },
                      onClick = { foodDailyStatus = value; dailyStatusExpanded = false }
                    )
                  }
                }
              }
              VendorOutlinedTextField(
                value = foodDailyQuantity,
                onValueChange = { foodDailyQuantity = it.filter(Char::isDigit) },
                label = "Today's available quantity",
                leadingIcon = Icons.Rounded.Inventory2,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
              )
            }
          }
        }
      }

      // Pricing & Stock
      item {
        AppCard {
          Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
            SectionHeader(title = "Pricing & stock")
            VendorOutlinedTextField(
              value = price,
              onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' } },
              label = "Selling price (PKR) *",
              leadingIcon = Icons.Rounded.AttachMoney,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            VendorOutlinedTextField(
              value = discount,
              onValueChange = { discount = it.filter { c -> c.isDigit() || c == '.' } },
              label = "Discount (%)",
              leadingIcon = Icons.Rounded.Percent,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            VendorOutlinedTextField(
              value = weight,
              onValueChange = { weight = it.filter(Char::isDigit) },
              label = "Shipping weight (grams)",
              leadingIcon = Icons.Rounded.Scale,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            VendorOutlinedTextField(
              value = supplierCost,
              onValueChange = { supplierCost = it.filter { c -> c.isDigit() || c == '.' } },
              label = "Supplier cost (private)",
              leadingIcon = Icons.Rounded.AttachMoney,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            VendorOutlinedTextField(
              value = fulfilment,
              onValueChange = { fulfilment = it.filter { c -> c.isDigit() || c == '.' } },
              label = "Fulfilment expense",
              leadingIcon = Icons.Rounded.AttachMoney,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            VendorOutlinedTextField(
              value = minimumProfit,
              onValueChange = { minimumProfit = it.filter { c -> c.isDigit() || c == '.' } },
              label = "Minimum profit",
              leadingIcon = Icons.Rounded.AttachMoney,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .clip(VendorShapes.input)
                .clickable { trackInventory = !trackInventory },
              shape = VendorShapes.input,
              color = VendorColors.Background,
              border = BorderStroke(1.dp, VendorColors.BorderSubtle)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = VendorSpacing.sm, vertical = VendorSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Checkbox(
                  checked = trackInventory,
                  onCheckedChange = { trackInventory = it },
                  colors = CheckboxDefaults.colors(checkedColor = VendorColors.PrimaryOrange)
                )
                Text("Track inventory for this listing", style = MaterialTheme.typography.bodyMedium)
              }
            }
            if (trackInventory) {
              VendorOutlinedTextField(
                value = initialStock,
                onValueChange = { initialStock = it.filter(Char::isDigit) },
                label = "Initial stock quantity",
                leadingIcon = Icons.Rounded.Inventory2,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
              )
            }
          }
        }
      }

      // Product Variants
      item {
        AppCard {
          Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
            SectionHeader(
              title = "Product variants",
              subtitle = "Each option can have its own SKU, price and stock.",
              actionLabel = "Add variant",
              actionIcon = Icons.Rounded.Add,
              onActionClick = { variants = variants + ListingVariant() }
            )
            variants.forEachIndexed { index, variant ->
              Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = VendorShapes.input,
                color = VendorColors.Background,
                border = BorderStroke(1.dp, VendorColors.BorderSubtle)
              ) {
                Column(
                  modifier = Modifier.padding(VendorSpacing.sm),
                  verticalArrangement = Arrangement.spacedBy(VendorSpacing.xs)
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = "Variant ${index + 1}",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { variants = variants.filterIndexed { i, _ -> i != index } }) {
                      Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Remove variant",
                        tint = VendorColors.DangerRed,
                        modifier = Modifier.size(18.dp)
                      )
                      Text("Remove", color = VendorColors.DangerRed)
                    }
                  }
                  VendorOutlinedTextField(
                    value = variant.optionName,
                    onValueChange = { v -> variants = variants.toMutableList().also { it[index] = variant.copy(optionName = v) } },
                    label = "Option name",
                    leadingIcon = Icons.Rounded.Style
                  )
                  VendorOutlinedTextField(
                    value = variant.optionValue,
                    onValueChange = { v -> variants = variants.toMutableList().also { it[index] = variant.copy(optionValue = v) } },
                    label = "Option value",
                    leadingIcon = Icons.Rounded.Style
                  )
                  VendorOutlinedTextField(
                    value = variant.sku,
                    onValueChange = { v -> variants = variants.toMutableList().also { it[index] = variant.copy(sku = v) } },
                    label = "Variant SKU",
                    leadingIcon = Icons.Rounded.QrCode2
                  )
                  VendorOutlinedTextField(
                    value = variant.price,
                    onValueChange = { v -> variants = variants.toMutableList().also { it[index] = variant.copy(price = v) } },
                    label = "Price override (PKR)",
                    leadingIcon = Icons.Rounded.AttachMoney,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                  )
                  VendorOutlinedTextField(
                    value = variant.stock,
                    onValueChange = { v -> variants = variants.toMutableList().also { it[index] = variant.copy(stock = v.filter(Char::isDigit)) } },
                    label = "Stock quantity",
                    leadingIcon = Icons.Rounded.Inventory2,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                  )
                }
              }
            }
            Text(
              text = "Variant changes are sent to JTBazar moderation before publication.",
              style = MaterialTheme.typography.bodySmall,
              color = VendorColors.TextSecondary
            )
          }
        }
      }

      // Product Images
      item {
        AppCard {
          Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
            SectionHeader(
              title = "Product images (${images.size}/8)",
              subtitle = "JPG, PNG, WebP or AVIF, max 10 MB each",
              actionLabel = if (images.size < 8) "Select images" else null,
              actionIcon = Icons.Rounded.AddPhotoAlternate,
              onActionClick = { if (images.size < 8) imagePicker.launch("image/*") }
            )
            images.forEachIndexed { index, uri ->
              Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = VendorShapes.input,
                color = VendorColors.Background,
                border = BorderStroke(1.dp, VendorColors.BorderSubtle)
              ) {
                Row(
                  modifier = Modifier.padding(VendorSpacing.xs),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
                ) {
                  AsyncImage(
                    model = uri,
                    contentDescription = "Product image ${index + 1}",
                    modifier = Modifier.size(72.dp).clip(VendorShapes.badge),
                    contentScale = ContentScale.Crop
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text("Product image ${index + 1}", style = MaterialTheme.typography.labelLarge)
                    Text(
                      text = uri.lastPathSegment ?: "Selected image",
                      style = MaterialTheme.typography.bodySmall,
                      color = VendorColors.TextSecondary,
                      maxLines = 1
                    )
                  }
                  TextButton(onClick = { images = images.filterIndexed { i, _ -> i != index } }) {
                    Icon(
                      imageVector = Icons.Rounded.Delete,
                      contentDescription = "Remove image",
                      tint = VendorColors.DangerRed,
                      modifier = Modifier.size(18.dp)
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Description & Search
      item {
        AppCard {
          Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
            SectionHeader(title = "Description & search")
            VendorOutlinedTextField(
              value = shortDescription,
              onValueChange = { shortDescription = it },
              label = "Short description",
              leadingIcon = Icons.Rounded.Description,
              minLines = 2
            )
            VendorOutlinedTextField(
              value = description,
              onValueChange = { description = it },
              label = "Full description",
              leadingIcon = Icons.Rounded.Description,
              minLines = 4
            )
            VendorOutlinedTextField(
              value = specifications,
              onValueChange = { specifications = it },
              label = "Specifications (JSON array)",
              leadingIcon = Icons.Rounded.Settings,
              minLines = 3
            )
            VendorOutlinedTextField(
              value = dimensions,
              onValueChange = { dimensions = it },
              label = "Dimensions (JSON object)",
              leadingIcon = Icons.Rounded.Straighten,
              minLines = 3
            )
            VendorOutlinedTextField(
              value = seoTitle,
              onValueChange = { seoTitle = it },
              label = "SEO title",
              leadingIcon = Icons.Rounded.Search
            )
            VendorOutlinedTextField(
              value = seoDescription,
              onValueChange = { seoDescription = it },
              label = "SEO description",
              leadingIcon = Icons.Rounded.Description,
              minLines = 2
            )
          }
        }
      }
    }

    // Sticky Bottom Actions
    Surface(
      color = VendorColors.CardWhite,
      shadowElevation = 6.dp,
      border = BorderStroke(1.dp, VendorColors.BorderSubtle),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = VendorSpacing.md, vertical = VendorSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
      ) {
        SecondaryButton(
          text = "Cancel",
          icon = Icons.Rounded.Close,
          enabled = !saving,
          onClick = onCancel,
          modifier = Modifier.weight(1f)
        )
        PrimaryButton(
          text = if (saving) "Submitting..." else "Submit listing",
          icon = Icons.Rounded.Check,
          loading = saving,
          enabled = !saving,
          onClick = {
            scope.launch {
              saving = true
              errorMessage = ""
              message = ""
              var createdProductId: String? = null
              try {
                require(name.trim().length >= 2) { "Enter a product name." }
                val baseSlug = slug.ifBlank { "product" }
                val productSlug = "$baseSlug-${UUID.randomUUID().toString().take(6)}"
                require(productSlug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) { "Enter a valid slug." }
                require(categoryId.isNotBlank()) { "Select a category." }
                val basePrice = fieldNumber(price, "selling price")
                require(basePrice > 0) { "Selling price must be greater than zero." }
                val foodUnitPriceValue = if (isFoodCategory) fieldNumber(foodUnitPrice, "food unit price") else 0.0
                if (isFoodCategory) {
                  require(foodUnitPriceValue > 0) { "Enter the food price per selling unit." }
                  require(fieldNumber(foodQuantityStep, "customer quantity step", false) >= 1) {
                    "Customer quantity step must be at least 1."
                  }
                  require(fieldNumber(foodMinimumSteps, "minimum purchase steps", false) >= 1) {
                    "Minimum purchase steps must be at least 1."
                  }
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
                  require(seen.add("${v.optionName.trim().lowercase()}::${v.optionValue.trim().lowercase()}")) {
                    "Duplicate variant option and value."
                  }
                  val stock = fieldNumber(v.stock, "variant stock", false).toInt()
                  val row = JSONObject()
                    .put("option_label", v.optionName.trim())
                    .put("option_value", v.optionValue.trim())
                    .put("sku", if (v.sku.isBlank()) JSONObject.NULL else v.sku.trim())
                    .put("price_override", if (v.price.isBlank()) JSONObject.NULL else fieldNumber(v.price, "variant price"))
                    .put("attributes", JSONObject())
                    .put("quantity", stock)
                  variantJson.put(row)
                }
                val specs = JSONArray(specifications)
                val dims = JSONObject(dimensions)
                val id = VendorService.createProduct(
                  name.trim(),
                  productSlug,
                  description,
                  basePrice.toInt(),
                  if (trackInventory) fieldNumber(initialStock, "initial stock", false).toInt() else 0,
                  categoryId,
                  sku.trim().ifBlank { null },
                  shortDescription,
                  supplierCostValue,
                  fulfilmentValue,
                  minimumProfitValue,
                  discountValue,
                  weight.toIntOrNull(),
                  specs,
                  dims,
                  seoTitle.trim().ifBlank { null },
                  seoDescription.trim().ifBlank { null },
                  trackInventory
                )
                createdProductId = id
                images.forEachIndexed { index, uri ->
                  val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                  val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Image ${index + 1} could not be read from the selected file.")
                  require(bytes.size <= 10 * 1024 * 1024) { "Image ${index + 1} must be 10 MB or smaller." }
                  VendorService.uploadProductImage(id, bytes, mime, name)
                }
                if (isFoodCategory) {
                  VendorService.updateFoodProductDetails(
                    id,
                    foodUnit,
                    foodUnitPriceValue,
                    fieldNumber(foodQuantityStep, "customer quantity step", false).toInt(),
                    fieldNumber(foodMinimumSteps, "minimum purchase steps", false).toInt(),
                    foodDailyItem,
                    foodDailyStatus,
                    fieldNumber(foodDailyQuantity, "today's available quantity", false).toInt()
                  )
                }
                if (variantJson.length() > 0) VendorService.saveVariants(id, variantJson)
                message = "Listing submitted for moderation."
                onSaved(message)
              } catch (e: Exception) {
                val details = generateSequence(e as Throwable?) { it.cause }
                  .mapNotNull { it.message }
                  .joinToString(" ")
                  .lowercase()
                if (createdProductId != null) {
                  onSaved("Product created as a draft, but variants or images could not all be saved. Do not submit it again. Please contact JTBazar support: ${e.message ?: "Additional product data failed."}")
                } else {
                  errorMessage = when {
                    details.contains("products_sku_unique_idx") ||
                      details.contains("product_variants_sku_unique_idx") ||
                      (details.contains("already exists") && details.contains("sku")) ->
                      "This SKU is already being used. Enter a unique SKU for this product or variant."
                    details.contains("products_slug_key") ->
                      "This product URL is already in use. Please change the slug and try again."
                    details.contains("row-level security") || details.contains("accessdenied") ->
                      "Image upload was blocked by storage permissions. Please retry; if it continues, contact JTBazar support."
                    else -> e.message ?: "Listing could not be submitted."
                  }
                }
              } finally {
                saving = false
              }
            }
          },
          modifier = Modifier.weight(1.4f)
        )
      }
    }
  }
}

private fun nameToSlug(value: String): String =
  value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')

private fun categoryTreeContainsFood(categories: JSONArray, selectedId: String): Boolean {
  if (selectedId.isBlank()) return false
  val rows = (0 until categories.length()).mapNotNull { categories.optJSONObject(it) }
  fun isFood(row: JSONObject): Boolean {
    val slug = row.optString("slug").trim().lowercase(Locale.ROOT)
    val name = row.optString("name").trim().lowercase(Locale.ROOT)
    val flag = row.opt("is_food_category")
    return row.optBoolean("is_food_category") ||
      flag?.toString()?.equals("true", true) == true ||
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
