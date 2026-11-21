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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.supabase.VendorPublishOutcome
import com.example.data.supabase.VendorService
import com.example.localization.AppLanguageManager
import com.example.localization.appText
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
  allowDirectPublish: Boolean = false,
  onCancel: () -> Unit,
  onSaved: suspend (VendorPublishOutcome) -> Unit
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
      errorMessage = e.message ?: AppLanguageManager.tr(
        "Categories could not be loaded.",
        "کیٹیگریز لوڈ نہیں ہو سکیں۔"
      )
    }
  }

  val isFoodCategory = remember(categories, categoryId) { categoryTreeContainsFood(categories, categoryId) }

  val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selected ->
    val remaining = (8 - images.size).coerceAtLeast(0)
    images = (images + selected.take(remaining)).distinct().take(8)
    if (selected.size > remaining) {
      errorMessage = AppLanguageManager.tr("You can add up to 8 images total.", "آپ کل 8 تصاویر تک شامل کر سکتے ہیں۔")
    }
  }

  fun fieldNumber(value: String, labelEn: String, labelUr: String, allowDecimal: Boolean = true): Double {
    val label = AppLanguageManager.tr(labelEn, labelUr)
    val parsed = value.toDoubleOrNull()
      ?: throw IllegalArgumentException(AppLanguageManager.tr("Enter a valid $label.", "درست $label درج کریں۔"))
    require(parsed >= 0) { AppLanguageManager.tr("$label cannot be negative.", "$label منفی نہیں ہو سکتا۔") }
    if (!allowDecimal) require(parsed % 1.0 == 0.0) {
      AppLanguageManager.tr("$label must be a whole number.", "$label مکمل عدد ہونا چاہیے۔")
    }
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
          title = appText("Create product", "نیا پروڈکٹ بنائیں"),
          subtitle = appText(
            "Add product information, pricing, images and stock details.",
            "پروڈکٹ کی معلومات، قیمت، تصاویر اور اسٹاک کی تفصیلات شامل کریں۔"
          )
        )
      }

      item {
        VendorAlertBanner(
          message = if (allowDirectPublish) {
            appText(
              "Direct publishing is enabled by admin. Eligible products publish live after submission checks.",
              "ایڈمن کی جانب سے براہ راست اشاعت فعال ہے۔ اہل مصنوعات جمع کرانے کی جانچ کے بعد فوراً شائع ہو جائیں گی۔"
            )
          } else {
            appText(
              "New products and edits go through JTBazar admin moderation before going live.",
              "نئی مصنوعات اور تبدیلیاں لائیو ہونے سے پہلے جے ٹی بازار ایڈمن کی جانچ سے گزرتی ہیں۔"
            )
          },
          isError = false,
          modifier = Modifier.testTag("listing_publishing_mode_banner")
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
            SectionHeader(title = appText("Product information", "پروڈکٹ کی معلومات"))
            VendorOutlinedTextField(
              value = name,
              onValueChange = {
                name = it
                if (slug.isBlank() || slug == nameToSlug(name.dropLast(1))) slug = nameToSlug(it)
              },
              label = appText("Product name *", "پروڈکٹ کا نام *"),
              leadingIcon = Icons.Rounded.Inventory2
            )
            VendorOutlinedTextField(
              value = slug,
              onValueChange = { slug = it.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9-]+"), "-").trim('-') },
              label = appText("Slug", "سلگ"),
              leadingIcon = Icons.Rounded.Link
            )
            VendorOutlinedTextField(
              value = sku,
              onValueChange = { sku = it },
              label = appText("SKU", "ایس کے یو (SKU)"),
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
                label = { Text(appText("Select category *", "کیٹیگری منتخب کریں *"), style = MaterialTheme.typography.bodySmall) },
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
              SectionHeader(title = appText("Food & groceries", "کھانا اور گروسری"))
              var unitExpanded by remember { mutableStateOf(false) }
              ExposedDropdownMenuBox(
                expanded = unitExpanded,
                onExpandedChange = { unitExpanded = !unitExpanded }
              ) {
                val foodUnitLabel = when (foodUnit) {
                  "kg" -> appText("Kilogram (kg)", "کلوگرام (kg)")
                  "g" -> appText("Gram (g)", "گرام (g)")
                  "l" -> appText("Litre (L)", "لیٹر (L)")
                  "ml" -> appText("Millilitre (ml)", "ملی لیٹر (ml)")
                  "piece" -> appText("Piece", "عدد")
                  "dozen" -> appText("Dozen", "درجن")
                  else -> appText("Pack", "پیک")
                }
                OutlinedTextField(
                  value = foodUnitLabel,
                  onValueChange = {},
                  readOnly = true,
                  label = { Text(appText("Selling unit", "فروخت کی اکائی")) },
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
                      "kg" -> appText("Kilogram (kg)", "کلوگرام (kg)")
                      "g" -> appText("Gram (g)", "گرام (g)")
                      "l" -> appText("Litre (L)", "لیٹر (L)")
                      "ml" -> appText("Millilitre (ml)", "ملی لیٹر (ml)")
                      "piece" -> appText("Piece", "عدد")
                      "dozen" -> appText("Dozen", "درجن")
                      else -> appText("Pack", "پیک")
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
                label = appText("Price per selling unit (PKR)", "فی اکائی قیمت (روپے)"),
                leadingIcon = Icons.Rounded.AttachMoney,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
              )
              VendorOutlinedTextField(
                value = foodQuantityStep,
                onValueChange = { foodQuantityStep = it.filter(Char::isDigit) },
                label = appText("Customer quantity step (grams / ml / units)", "خریدار کی مقدار کا مرحلہ (گرام / ملی لیٹر / اکائی)"),
                leadingIcon = Icons.Rounded.Scale,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
              )
              VendorOutlinedTextField(
                value = foodMinimumSteps,
                onValueChange = { foodMinimumSteps = it.filter(Char::isDigit) },
                label = appText("Minimum purchase steps", "کم از کم خریداری کے مراحل"),
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
                  Text(
                    appText("Manage as a daily dish / daily food item", "روزانہ کی ڈش / خوراک کے طور پر منظم کریں"),
                    style = MaterialTheme.typography.bodyMedium
                  )
                }
              }
              var dailyStatusExpanded by remember { mutableStateOf(false) }
              ExposedDropdownMenuBox(
                expanded = dailyStatusExpanded,
                onExpandedChange = { dailyStatusExpanded = !dailyStatusExpanded }
              ) {
                val statusLabel = when (foodDailyStatus) {
                  "not_today" -> appText("Not for today", "آج کے لیے نہیں")
                  "coming_soon" -> appText("Coming soon", "جلد آ رہا ہے")
                  else -> appText("Available today", "آج دستیاب")
                }
                OutlinedTextField(
                  value = statusLabel,
                  onValueChange = {},
                  readOnly = true,
                  label = { Text(appText("Daily availability status", "روزانہ دستیابی کی حیثیت")) },
                  trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dailyStatusExpanded) },
                  modifier = Modifier.menuAnchor().fillMaxWidth(),
                  shape = VendorShapes.input
                )
                ExposedDropdownMenu(
                  expanded = dailyStatusExpanded,
                  onDismissRequest = { dailyStatusExpanded = false }
                ) {
                  listOf(
                    "available" to appText("Available today", "آج دستیاب"),
                    "not_today" to appText("Not for today", "آج کے لیے نہیں"),
                    "coming_soon" to appText("Coming soon", "جلد آ رہا ہے")
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
                label = appText("Today's available quantity", "آج دستیاب مقدار"),
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
            SectionHeader(title = appText("Pricing & stock", "قیمت اور اسٹاک"))
            VendorOutlinedTextField(
              value = price,
              onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' } },
              label = appText("Selling price (PKR) *", "فروخت کی قیمت (روپے) *"),
              leadingIcon = Icons.Rounded.AttachMoney,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            VendorOutlinedTextField(
              value = discount,
              onValueChange = { discount = it.filter { c -> c.isDigit() || c == '.' } },
              label = appText("Discount (%)", "رعایت (%)"),
              leadingIcon = Icons.Rounded.Percent,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            VendorOutlinedTextField(
              value = weight,
              onValueChange = { weight = it.filter(Char::isDigit) },
              label = appText("Shipping weight (grams)", "شپنگ وزن (گرام)"),
              leadingIcon = Icons.Rounded.Scale,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            VendorOutlinedTextField(
              value = supplierCost,
              onValueChange = { supplierCost = it.filter { c -> c.isDigit() || c == '.' } },
              label = appText("Supplier cost (private)", "سپلائر لاگت (نجی)"),
              leadingIcon = Icons.Rounded.AttachMoney,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            VendorOutlinedTextField(
              value = fulfilment,
              onValueChange = { fulfilment = it.filter { c -> c.isDigit() || c == '.' } },
              label = appText("Fulfilment expense", "پیکنگ و ترسیل خرچ"),
              leadingIcon = Icons.Rounded.AttachMoney,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            VendorOutlinedTextField(
              value = minimumProfit,
              onValueChange = { minimumProfit = it.filter { c -> c.isDigit() || c == '.' } },
              label = appText("Minimum profit", "کم از کم منافع"),
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
                Text(
                  appText("Track inventory for this listing", "اس پروڈکٹ کا اسٹاک ٹریک کریں"),
                  style = MaterialTheme.typography.bodyMedium
                )
              }
            }
            if (trackInventory) {
              VendorOutlinedTextField(
                value = initialStock,
                onValueChange = { initialStock = it.filter(Char::isDigit) },
                label = appText("Initial stock quantity", "ابتدائی اسٹاک مقدار"),
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
              title = appText("Product variants", "پروڈکٹ ویرینٹس"),
              subtitle = appText(
                "Each option can have its own SKU, price and stock.",
                "ہر آپشن کا اپنا SKU، قیمت اور اسٹاک ہو سکتا ہے۔"
              ),
              actionLabel = appText("Add variant", "ویرینٹ شامل کریں"),
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
                      text = appText("Variant ${index + 1}", "ویرینٹ ${index + 1}"),
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { variants = variants.filterIndexed { i, _ -> i != index } }) {
                      Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = appText("Remove variant", "ویرینٹ ہٹائیں"),
                        tint = VendorColors.DangerRed,
                        modifier = Modifier.size(18.dp)
                      )
                      Text(appText("Remove", "ہٹائیں"), color = VendorColors.DangerRed)
                    }
                  }
                  VendorOutlinedTextField(
                    value = variant.optionName,
                    onValueChange = { v -> variants = variants.toMutableList().also { it[index] = variant.copy(optionName = v) } },
                    label = appText("Option name", "آپشن کا نام"),
                    leadingIcon = Icons.Rounded.Style
                  )
                  VendorOutlinedTextField(
                    value = variant.optionValue,
                    onValueChange = { v -> variants = variants.toMutableList().also { it[index] = variant.copy(optionValue = v) } },
                    label = appText("Option value", "آپشن کی قدر"),
                    leadingIcon = Icons.Rounded.Style
                  )
                  VendorOutlinedTextField(
                    value = variant.sku,
                    onValueChange = { v -> variants = variants.toMutableList().also { it[index] = variant.copy(sku = v) } },
                    label = appText("Variant SKU", "ویرینٹ SKU"),
                    leadingIcon = Icons.Rounded.QrCode2
                  )
                  VendorOutlinedTextField(
                    value = variant.price,
                    onValueChange = { v -> variants = variants.toMutableList().also { it[index] = variant.copy(price = v) } },
                    label = appText("Price override (PKR)", "مخصوص قیمت (روپے)"),
                    leadingIcon = Icons.Rounded.AttachMoney,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                  )
                  VendorOutlinedTextField(
                    value = variant.stock,
                    onValueChange = { v -> variants = variants.toMutableList().also { it[index] = variant.copy(stock = v.filter(Char::isDigit)) } },
                    label = appText("Stock quantity", "اسٹاک مقدار"),
                    leadingIcon = Icons.Rounded.Inventory2,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                  )
                }
              }
            }
            Text(
              text = if (allowDirectPublish) {
                appText(
                  "Eligible products with direct publishing enabled are finalized immediately after saving.",
                  "براہ راست اشاعت فعال ہونے پر اہل مصنوعات محفوظ ہونے کے بعد فوراً شائع کر دی جاتی ہیں۔"
                )
              } else {
                appText(
                  "Variant changes are sent to JTBazar moderation before publication.",
                  "ویرینٹ کی تبدیلیاں شائع ہونے سے پہلے جے ٹی بازار کی جانچ کے لیے بھیجی جاتی ہیں۔"
                )
              },
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
              title = appText("Product images (${images.size}/8)", "پروڈکٹ تصاویر (${images.size}/8)"),
              subtitle = appText("JPG, PNG, WebP or AVIF, max 10 MB each", "JPG، PNG، WebP یا AVIF، زیادہ سے زیادہ 10 MB"),
              actionLabel = if (images.size < 8) appText("Select images", "تصاویر منتخب کریں") else null,
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
                    contentDescription = appText("Product image ${index + 1}", "پروڈکٹ تصویر ${index + 1}"),
                    modifier = Modifier.size(72.dp).clip(VendorShapes.badge),
                    contentScale = ContentScale.Crop
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      appText("Product image ${index + 1}", "پروڈکٹ تصویر ${index + 1}"),
                      style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                      text = uri.lastPathSegment ?: appText("Selected image", "منتخب کردہ تصویر"),
                      style = MaterialTheme.typography.bodySmall,
                      color = VendorColors.TextSecondary,
                      maxLines = 1
                    )
                  }
                  TextButton(onClick = { images = images.filterIndexed { i, _ -> i != index } }) {
                    Icon(
                      imageVector = Icons.Rounded.Delete,
                      contentDescription = appText("Remove image", "تصویر ہٹائیں"),
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
            SectionHeader(title = appText("Description & search", "تفصیل اور تلاش"))
            VendorOutlinedTextField(
              value = shortDescription,
              onValueChange = { shortDescription = it },
              label = appText("Short description", "مختصر تفصیل"),
              leadingIcon = Icons.Rounded.Description,
              minLines = 2
            )
            VendorOutlinedTextField(
              value = description,
              onValueChange = { description = it },
              label = appText("Full description", "مکمل تفصیل"),
              leadingIcon = Icons.Rounded.Description,
              minLines = 4
            )
            VendorOutlinedTextField(
              value = specifications,
              onValueChange = { specifications = it },
              label = appText("Specifications (JSON array)", "خصوصیات (JSON array)"),
              leadingIcon = Icons.Rounded.Settings,
              minLines = 3
            )
            VendorOutlinedTextField(
              value = dimensions,
              onValueChange = { dimensions = it },
              label = appText("Dimensions (JSON object)", "پیمائش (JSON object)"),
              leadingIcon = Icons.Rounded.Straighten,
              minLines = 3
            )
            VendorOutlinedTextField(
              value = seoTitle,
              onValueChange = { seoTitle = it },
              label = appText("SEO title", "ایس ای او عنوان"),
              leadingIcon = Icons.Rounded.Search
            )
            VendorOutlinedTextField(
              value = seoDescription,
              onValueChange = { seoDescription = it },
              label = appText("SEO description", "ایس ای او تفصیل"),
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
          text = appText("Cancel", "منسوخ کریں"),
          icon = Icons.Rounded.Close,
          enabled = !saving,
          onClick = onCancel,
          modifier = Modifier.weight(1f)
        )
        PrimaryButton(
          text = if (saving) appText("Submitting...", "جمع ہو رہا ہے...") else appText("Submit listing", "لسٹنگ جمع کرائیں"),
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
                require(name.trim().length >= 2) {
                  AppLanguageManager.tr("Enter a product name.", "پروڈکٹ کا نام درج کریں۔")
                }
                val baseSlug = slug.ifBlank { "product" }
                val productSlug = "$baseSlug-${UUID.randomUUID().toString().take(6)}"
                require(productSlug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) {
                  AppLanguageManager.tr("Enter a valid slug.", "درست سلگ درج کریں۔")
                }
                require(categoryId.isNotBlank()) {
                  AppLanguageManager.tr("Select a category.", "کیٹیگری منتخب کریں۔")
                }
                val basePrice = fieldNumber(price, "selling price", "فروخت کی قیمت")
                require(basePrice > 0) {
                  AppLanguageManager.tr("Selling price must be greater than zero.", "فروخت کی قیمت صفر سے زیادہ ہونی چاہیے۔")
                }
                val foodUnitPriceValue = if (isFoodCategory) {
                  fieldNumber(foodUnitPrice, "food unit price", "خوراک فی اکائی قیمت")
                } else 0.0
                if (isFoodCategory) {
                  require(foodUnitPriceValue > 0) {
                    AppLanguageManager.tr("Enter the food price per selling unit.", "فی اکائی خوراک کی قیمت درج کریں۔")
                  }
                  require(fieldNumber(foodQuantityStep, "customer quantity step", "خریدار کی مقدار کا مرحلہ", false) >= 1) {
                    AppLanguageManager.tr("Customer quantity step must be at least 1.", "خریدار کی مقدار کا مرحلہ کم از کم 1 ہونا چاہیے۔")
                  }
                  require(fieldNumber(foodMinimumSteps, "minimum purchase steps", "کم از کم خریداری کے مراحل", false) >= 1) {
                    AppLanguageManager.tr("Minimum purchase steps must be at least 1.", "کم از کم خریداری کے مراحل کم از کم 1 ہونے چاہئیں۔")
                  }
                  fieldNumber(foodDailyQuantity, "today's available quantity", "آج دستیاب مقدار", false)
                }
                val discountValue = fieldNumber(discount, "discount", "رعایت")
                require(discountValue <= 100) {
                  AppLanguageManager.tr("Discount cannot exceed 100%.", "رعایت 100% سے زیادہ نہیں ہو سکتی۔")
                }
                val supplierCostValue = supplierCost.trim().takeIf { it.isNotEmpty() }
                  ?.let { fieldNumber(it, "supplier cost", "سپلائر لاگت") }
                val fulfilmentValue = fieldNumber(fulfilment, "fulfilment expense", "پیکنگ و ترسیل خرچ")
                val minimumProfitValue = fieldNumber(minimumProfit, "minimum profit", "کم از کم منافع")
                require(basePrice >= (supplierCostValue ?: 0.0) + fulfilmentValue + minimumProfitValue) {
                  AppLanguageManager.tr(
                    "Selling price must cover supplier cost, fulfilment expense and minimum profit.",
                    "فروخت کی قیمت میں سپلائر لاگت، پیکنگ خرچ اور کم از کم منافع پورا ہونا چاہیے۔"
                  )
                }
                val variantJson = JSONArray()
                val seen = mutableSetOf<String>()
                variants.forEach { v ->
                  require(v.optionName.isNotBlank() && v.optionValue.isNotBlank()) {
                    AppLanguageManager.tr("Complete every variant option.", "ہر ویرینٹ آپشن مکمل کریں۔")
                  }
                  require(seen.add("${v.optionName.trim().lowercase()}::${v.optionValue.trim().lowercase()}")) {
                    AppLanguageManager.tr("Duplicate variant option and value.", "ویرینٹ آپشن اور قدر دہرائی گئی ہے۔")
                  }
                  val stock = fieldNumber(v.stock, "variant stock", "ویرینٹ اسٹاک", false).toInt()
                  val row = JSONObject()
                    .put("option_label", v.optionName.trim())
                    .put("option_value", v.optionValue.trim())
                    .put("sku", if (v.sku.isBlank()) JSONObject.NULL else v.sku.trim())
                    .put(
                      "price_override",
                      if (v.price.isBlank()) JSONObject.NULL else fieldNumber(v.price, "variant price", "ویرینٹ قیمت")
                    )
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
                  if (trackInventory) fieldNumber(initialStock, "initial stock", "ابتدائی اسٹاک", false).toInt() else 0,
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
                    ?: error(
                      AppLanguageManager.tr(
                        "Image ${index + 1} could not be read from the selected file.",
                        "منتخب کردہ فائل سے تصویر ${index + 1} پڑھی نہیں جا سکی۔"
                      )
                    )
                  require(bytes.size <= 10 * 1024 * 1024) {
                    AppLanguageManager.tr("Image ${index + 1} must be 10 MB or smaller.", "تصویر ${index + 1} کا سائز 10 MB یا اس سے کم ہونا چاہیے۔")
                  }
                  VendorService.uploadProductImage(id, bytes, mime, name)
                }
                if (isFoodCategory) {
                  VendorService.updateFoodProductDetails(
                    id,
                    foodUnit,
                    foodUnitPriceValue,
                    fieldNumber(foodQuantityStep, "customer quantity step", "خریدار کی مقدار کا مرحلہ", false).toInt(),
                    fieldNumber(foodMinimumSteps, "minimum purchase steps", "کم از کم خریداری کے مراحل", false).toInt(),
                    foodDailyItem,
                    foodDailyStatus,
                    fieldNumber(foodDailyQuantity, "today's available quantity", "آج دستیاب مقدار", false).toInt()
                  )
                }
                if (variantJson.length() > 0) VendorService.saveVariants(id, variantJson)
                val outcome = VendorService.finalizeProductSubmission(
                  productId = id,
                  allowDirectPublish = allowDirectPublish,
                  isEdit = false
                )
                if (outcome.finalizationError != null) {
                  errorMessage = outcome.finalizationError
                } else {
                  message = outcome.message
                }
                onSaved(outcome)
              } catch (e: Exception) {
                val details = generateSequence(e as Throwable?) { it.cause }
                  .mapNotNull { it.message }
                  .joinToString(" ")
                  .lowercase()
                if (createdProductId != null) {
                  onSaved(
                    VendorPublishOutcome(
                      productId = createdProductId,
                      moderationStatus = "pending_review",
                      publicationStatus = "draft",
                      publishedImmediately = false,
                      message = AppLanguageManager.tr(
                        "Product created as a draft, but variants or images could not all be saved. Do not submit it again. Please contact JTBazar support: ${e.message ?: "Additional product data failed."}",
                        "پروڈکٹ مسودے کے طور پر بن گئی، لیکن تمام ویرینٹس یا تصاویر محفوظ نہیں ہو سکیں۔ دوبارہ جمع نہ کرائیں۔ براہ کرم جے ٹی بازار سپورٹ سے رابطہ کریں: ${e.message ?: "اضافی معلومات محفوظ نہیں ہو سکیں۔"}"
                      ),
                      finalizationError = null
                    )
                  )
                } else {
                  errorMessage = when {
                    details.contains("products_sku_unique_idx") ||
                      details.contains("product_variants_sku_unique_idx") ||
                      (details.contains("already exists") && details.contains("sku")) ->
                      AppLanguageManager.tr(
                        "This SKU is already being used. Enter a unique SKU for this product or variant.",
                        "یہ SKU پہلے سے استعمال ہو رہا ہے۔ اس پروڈکٹ یا ویرینٹ کے لیے نیا SKU درج کریں۔"
                      )
                    details.contains("products_slug_key") ->
                      AppLanguageManager.tr(
                        "This product URL is already in use. Please change the slug and try again.",
                        "یہ پروڈکٹ یو آر ایل پہلے سے استعمال میں ہے۔ براہ کرم سلگ تبدیل کر کے دوبارہ کوشش کریں۔"
                      )
                    details.contains("row-level security") || details.contains("accessdenied") ->
                      AppLanguageManager.tr(
                        "Image upload was blocked by storage permissions. Please retry; if it continues, contact JTBazar support.",
                        "اسٹوریج اجازت کی وجہ سے تصویر اپلوڈ نہ ہو سکی۔ براہ کرم دوبارہ کوشش کریں یا جے ٹی بازار سپورٹ سے رابطہ کریں۔"
                      )
                    else -> e.message ?: AppLanguageManager.tr("Listing could not be submitted.", "لسٹنگ جمع نہیں ہو سکی۔")
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
