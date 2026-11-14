package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
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

private fun JSONObject.vendorEditCleanString(key: String, fallback: String = ""): String =
  optString(key, fallback).takeUnless { it == "null" } ?: fallback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorProductEditScreen(
  product: JSONObject,
  onBack: () -> Unit,
  onSaved: suspend () -> Unit
) {
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
  var foodUnitPrice by remember(id) {
    mutableStateOf(
      run {
        val savedCustomerUnitPrice = product.vendorEditCleanString("food_unit_price").toDoubleOrNull()
        val commission = product.vendorEditCleanString("commission_percent", "0").toDoubleOrNull() ?: 0.0
        if (savedCustomerUnitPrice != null && commission >= 0) {
          (savedCustomerUnitPrice / (1.0 + commission / 100.0)).toString()
        } else {
          price
        }
      }
    )
  }
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
    } else {
      imagesLoading = false
    }
  }

  val totalImages = existingImageUrls.size + newImages.size
  val showFoodFields = remember(categories, category) { categoryTreeContainsFood(categories, category) }
  val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selected ->
    val remaining = (8 - existingImageUrls.size - newImages.size).coerceAtLeast(0)
    newImages = (newImages + selected.take(remaining)).distinct().take((8 - existingImageUrls.size).coerceAtLeast(0))
    if (selected.size > remaining) errorMessage = "A product can have up to 8 images total."
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
          title = "Edit product",
          subtitle = "Changes are sent through JTBazar moderation. Stock is managed in Inventory.",
          actionLabel = "Back",
          actionIcon = Icons.AutoMirrored.Rounded.ArrowBack,
          onActionClick = { if (!saving) onBack() }
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

      // Product Details
      item {
        AppCard {
          Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
            SectionHeader(title = "Product details")
            VendorOutlinedTextField(
              value = name,
              onValueChange = { name = it },
              label = "Product name *",
              leadingIcon = Icons.Rounded.Inventory2
            )
            VendorOutlinedTextField(
              value = slug,
              onValueChange = { slug = it.lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-') },
              label = "URL slug *",
              leadingIcon = Icons.Rounded.Link
            )
            VendorOutlinedTextField(
              value = sku,
              onValueChange = { sku = it },
              label = "SKU",
              leadingIcon = Icons.Rounded.QrCode2
            )

            var categoryExpanded by remember(id) { mutableStateOf(false) }
            val selectedCategoryName = (0 until categories.length()).mapNotNull { categories.optJSONObject(it) }
              .firstOrNull { it.optSafeString("id") == category }?.optSafeString("name")
              ?: if (category.isBlank()) "Select category" else category
            ExposedDropdownMenuBox(
              expanded = categoryExpanded,
              onExpandedChange = { categoryExpanded = !categoryExpanded }
            ) {
              OutlinedTextField(
                value = selectedCategoryName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Category", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Rounded.Category,
                    contentDescription = null,
                    tint = VendorColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                  )
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
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
                  val row = categories.optJSONObject(i) ?: continue
                  DropdownMenuItem(
                    text = { Text(row.optSafeString("name")) },
                    onClick = {
                      category = row.optSafeString("id")
                      val selectedIsFood = categoryTreeContainsFood(categories, row.optSafeString("id"))
                      if (selectedIsFood) {
                        if (foodUnit.isBlank()) foodUnit = "kg"
                        if (foodStep.isBlank()) foodStep = if (foodUnit == "kg" || foodUnit == "l") "250" else "1"
                      } else {
                        dailyItem = false
                      }
                      categoryExpanded = false
                    }
                  )
                }
              }
            }
          }
        }
      }

      // Product Images
      item {
        AppCard {
          Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
            SectionHeader(
              title = "Product images ($totalImages/8)",
              subtitle = "Up to 8 images, max 10 MB each",
              actionLabel = if (totalImages < 8) "Add images" else null,
              actionIcon = Icons.Rounded.AddPhotoAlternate,
              onActionClick = { if (totalImages < 8 && !saving) imagePicker.launch("image/*") }
            )
            if (imagesLoading) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
              ) {
                CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  color = VendorColors.PrimaryOrange,
                  strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(VendorSpacing.xs))
                Text("Loading saved images...", style = MaterialTheme.typography.bodySmall)
              }
            }
            if (imageLoadError.isNotBlank()) {
              VendorAlertBanner(message = imageLoadError, isError = true)
            }
            if (existingImageUrls.isNotEmpty() || newImages.isNotEmpty()) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
              ) {
                existingImageUrls.forEachIndexed { index, url ->
                  Column(
                    modifier = Modifier.width(104.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    AsyncImage(
                      model = url,
                      contentDescription = "Saved product image ${index + 1}",
                      modifier = Modifier
                        .size(104.dp)
                        .clip(VendorShapes.input),
                      contentScale = ContentScale.Crop,
                      onError = {
                        imageLoadError = "A saved product image could not be displayed."
                      }
                    )
                    Text(
                      text = if (index == 0) "Main image" else "Saved ${index + 1}",
                      style = MaterialTheme.typography.labelSmall,
                      color = VendorColors.TextSecondary,
                      maxLines = 1
                    )
                  }
                }
                newImages.forEachIndexed { index, uri ->
                  Surface(
                    modifier = Modifier.width(108.dp),
                    shape = VendorShapes.input,
                    color = VendorColors.Background,
                    border = BorderStroke(1.dp, VendorColors.BorderSubtle)
                  ) {
                    Column(
                      modifier = Modifier.padding(6.dp),
                      verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                      AsyncImage(
                        model = uri,
                        contentDescription = "New product image ${index + 1}",
                        modifier = Modifier
                          .size(94.dp)
                          .clip(VendorShapes.badge),
                        contentScale = ContentScale.Crop
                      )
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                        Text("New ${index + 1}", style = MaterialTheme.typography.labelSmall)
                        TextButton(
                          enabled = !saving,
                          onClick = { newImages = newImages.filterIndexed { i, _ -> i != index } },
                          contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                          Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Remove image",
                            tint = VendorColors.DangerRed,
                            modifier = Modifier.size(16.dp)
                          )
                        }
                      }
                    }
                  }
                }
              }
            } else if (!imagesLoading && imageLoadError.isBlank()) {
              Text(
                text = "No saved product images yet. Tap Add images above.",
                style = MaterialTheme.typography.bodySmall,
                color = VendorColors.TextSecondary
              )
            }
          }
        }
      }

      // Description
      item {
        AppCard {
          Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
            SectionHeader(title = "Description")
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
          }
        }
      }

      // Pricing & Margin
      item {
        AppCard {
          Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
            SectionHeader(title = "Pricing & margin")
            VendorOutlinedTextField(
              value = price,
              onValueChange = { price = it.filter { ch -> ch.isDigit() || ch == '.' } },
              label = "Vendor base price (PKR) *",
              leadingIcon = Icons.Rounded.AttachMoney,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            VendorOutlinedTextField(
              value = discount,
              onValueChange = { discount = it.filter { ch -> ch.isDigit() || ch == '.' } },
              label = "Discount (%)",
              leadingIcon = Icons.Rounded.Percent,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            VendorOutlinedTextField(
              value = supplierCost,
              onValueChange = { supplierCost = it.filter { ch -> ch.isDigit() || ch == '.' } },
              label = "Supplier cost (private PKR)",
              leadingIcon = Icons.Rounded.AttachMoney,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            VendorOutlinedTextField(
              value = fulfilment,
              onValueChange = { fulfilment = it.filter { ch -> ch.isDigit() || ch == '.' } },
              label = "Fulfilment expense (PKR)",
              leadingIcon = Icons.Rounded.AttachMoney,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            VendorOutlinedTextField(
              value = minimumProfit,
              onValueChange = { minimumProfit = it.filter { ch -> ch.isDigit() || ch == '.' } },
              label = "Minimum profit (PKR)",
              leadingIcon = Icons.Rounded.AttachMoney,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            VendorOutlinedTextField(
              value = weight,
              onValueChange = { weight = it.filter(Char::isDigit) },
              label = "Shipping weight (grams)",
              leadingIcon = Icons.Rounded.Scale,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
          }
        }
      }

      // Food & Groceries (conditional)
      if (showFoodFields) {
        item {
          AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
              SectionHeader(title = "Food & groceries")
              var unitExpanded by remember(id) { mutableStateOf(false) }
              val units = listOf(
                "kg" to "Kilogram (kg)",
                "g" to "Gram (g)",
                "l" to "Litre (L)",
                "ml" to "Millilitre (ml)",
                "piece" to "Piece",
                "dozen" to "Dozen",
                "pack" to "Pack"
              )
              ExposedDropdownMenuBox(
                expanded = unitExpanded,
                onExpandedChange = { unitExpanded = !unitExpanded }
              ) {
                OutlinedTextField(
                  value = units.firstOrNull { it.first == foodUnit }?.second ?: "Kilogram (kg)",
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
                  modifier = Modifier.fillMaxWidth().menuAnchor(),
                  shape = VendorShapes.input
                )
                ExposedDropdownMenu(
                  expanded = unitExpanded,
                  onDismissRequest = { unitExpanded = false }
                ) {
                  units.forEach { (value, label) ->
                    DropdownMenuItem(
                      text = { Text(label) },
                      onClick = {
                        foodUnit = value
                        foodStep = if (value == "kg" || value == "l") "250" else if (value == "g" || value == "ml") "100" else "1"
                        unitExpanded = false
                      }
                    )
                  }
                }
              }
              VendorOutlinedTextField(
                value = foodUnitPrice,
                onValueChange = { foodUnitPrice = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = "Price per $foodUnit (PKR)",
                leadingIcon = Icons.Rounded.AttachMoney,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
              )
              VendorOutlinedTextField(
                value = foodStep,
                onValueChange = { foodStep = it.filter(Char::isDigit) },
                label = "Customer quantity step (${if (foodUnit == "kg") "grams" else if (foodUnit == "l") "ml" else "units"})",
                leadingIcon = Icons.Rounded.Scale,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = "For kg/litre the step is grams/ml; for dozen, piece and pack it is units."
              )
              VendorOutlinedTextField(
                value = foodMinimum,
                onValueChange = { foodMinimum = it.filter(Char::isDigit) },
                label = "Minimum purchase steps",
                leadingIcon = Icons.Rounded.Scale,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
              )
              Surface(
                modifier = Modifier.fillMaxWidth().clip(VendorShapes.input).clickable { dailyItem = !dailyItem },
                shape = VendorShapes.input,
                color = VendorColors.Background,
                border = BorderStroke(1.dp, VendorColors.BorderSubtle)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = VendorSpacing.sm, vertical = VendorSpacing.xs),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(VendorSpacing.xs)
                ) {
                  Checkbox(
                    checked = dailyItem,
                    onCheckedChange = { dailyItem = it },
                    colors = CheckboxDefaults.colors(checkedColor = VendorColors.PrimaryOrange)
                  )
                  Text(
                    text = "Manage as a daily dish / daily food item",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                  )
                }
              }
              var statusExpanded by remember(id) { mutableStateOf(false) }
              val statuses = listOf(
                "available" to "Available today",
                "not_today" to "Not for today",
                "coming_soon" to "Coming soon"
              )
              ExposedDropdownMenuBox(
                expanded = statusExpanded,
                onExpandedChange = { statusExpanded = !statusExpanded }
              ) {
                OutlinedTextField(
                  value = statuses.firstOrNull { it.first == dailyStatus }?.second ?: "Available today",
                  onValueChange = {},
                  readOnly = true,
                  label = { Text("Daily availability status") },
                  trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(statusExpanded) },
                  modifier = Modifier.fillMaxWidth().menuAnchor(),
                  shape = VendorShapes.input
                )
                ExposedDropdownMenu(
                  expanded = statusExpanded,
                  onDismissRequest = { statusExpanded = false }
                ) {
                  statuses.forEach { (value, label) ->
                    DropdownMenuItem(
                      text = { Text(label) },
                      onClick = { dailyStatus = value; statusExpanded = false }
                    )
                  }
                }
              }
              VendorOutlinedTextField(
                value = dailyQuantity,
                onValueChange = { dailyQuantity = it.filter(Char::isDigit) },
                label = "Today's available quantity",
                leadingIcon = Icons.Rounded.Inventory2,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
              )
            }
          }
        }
      }

      // Attributes & SEO
      item {
        AppCard {
          Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
            SectionHeader(title = "Attributes & search appearance")
            VendorOutlinedTextField(
              value = specs,
              onValueChange = { specs = it },
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
            HorizontalDivider(color = VendorColors.Divider)
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

    // Sticky Footer Actions
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
          onClick = onBack,
          modifier = Modifier.weight(1f)
        )
        PrimaryButton(
          text = if (saving) "Saving..." else "Save product",
          icon = Icons.Rounded.Save,
          loading = saving,
          enabled = !saving,
          onClick = {
            scope.launch {
              saving = true
              errorMessage = ""
              message = ""
              try {
                val basePrice = (if (showFoodFields) foodUnitPrice else price).toDoubleOrNull()
                  ?: error(if (showFoodFields) "Enter a valid food unit price." else "Enter a valid price.")
                val discountValue = discount.toDoubleOrNull() ?: error("Enter a valid discount.")
                require(discountValue in 0.0..100.0) { "Discount must be between 0 and 100%." }
                val payload = JSONObject()
                  .put("p_category_id", category.ifBlank { JSONObject.NULL })
                  .put("p_name", name.trim())
                  .put("p_slug", slug.trim())
                  .put("p_sku", sku.trim().ifBlank { JSONObject.NULL })
                  .put("p_short_description", shortDescription.trim())
                  .put("p_description", description.trim())
                  .put("p_price", basePrice)
                  .put("p_supplier_cost", supplierCost.toDoubleOrNull() ?: JSONObject.NULL)
                  .put("p_fulfillment_expense", fulfilment.toDoubleOrNull() ?: error("Enter valid fulfilment expense."))
                  .put("p_minimum_profit", minimumProfit.toDoubleOrNull() ?: error("Enter valid minimum profit."))
                  .put("p_discount_percent", discountValue)
                  .put("p_weight_grams", weight.toIntOrNull() ?: JSONObject.NULL)
                  .put("p_specifications", JSONArray(specs))
                  .put("p_dimensions", JSONObject(dimensions))
                  .put("p_seo_title", seoTitle.trim().ifBlank { JSONObject.NULL })
                  .put("p_seo_description", seoDescription.trim().ifBlank { JSONObject.NULL })
                VendorService.updateProductFull(id, payload)
                if (showFoodFields) {
                  val step = foodStep.toIntOrNull() ?: error("Enter a valid customer quantity step.")
                  val minimum = foodMinimum.toIntOrNull() ?: error("Enter valid minimum purchase steps.")
                  val quantity = dailyQuantity.toIntOrNull() ?: error("Enter a valid daily available quantity.")
                  require(step > 0 && minimum > 0 && quantity >= 0) { "Check food quantity settings." }
                  VendorService.updateFoodProductDetails(
                    id, foodUnit, basePrice, step, minimum, dailyItem, dailyStatus, quantity
                  )
                }
                newImages.forEach { uri ->
                  val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                  val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Image could not be read.")
                  require(bytes.size <= 10 * 1024 * 1024) { "Each image must be 10 MB or smaller." }
                  VendorService.uploadProductImage(id, bytes, mime, name.trim())
                }
                existingImageUrls = VendorService.listProductImageUrls(id)
                newImages = emptyList()
                message = "Product details and images saved."
                onSaved()
              } catch (e: Exception) {
                errorMessage = e.message ?: "Product could not be updated."
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
      name in setOf("food & groceries", "food and groceries", "home & kitchen")
    ) {
      return true
    }
    val parentId = current.optSafeString("parent_id").trim().lowercase(Locale.ROOT)
    if (parentId.isBlank() || parentId == "null" || !visited.add(parentId)) break
    current = byId[parentId]
  }
  return false
}
