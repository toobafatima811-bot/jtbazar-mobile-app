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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Style
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private fun JSONObject.vendorEditCleanString(key: String, fallback: String = ""): String =
  optString(key, fallback).takeUnless { it == "null" } ?: fallback

private data class EditableProductVariant(
  val id: String? = null,
  val optionLabel: String = "Size",
  val optionValue: String = "",
  val sku: String = "",
  val priceOverride: String = "",
  val stock: String = "0",
  val imageUrl: String? = null,
  val localImageUri: Uri? = null,
  val attributesJson: String = "{}"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorProductEditScreen(
  product: JSONObject,
  allowDirectPublish: Boolean = false,
  onBack: () -> Unit,
  onSaved: suspend (VendorPublishOutcome) -> Unit
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
  var variants by remember(id) { mutableStateOf<List<EditableProductVariant>>(emptyList()) }
  var variantsLoading by remember(id) { mutableStateOf(true) }
  var variantsLoadedSuccessfully by remember(id) { mutableStateOf(false) }
  var variantsLoadError by remember(id) { mutableStateOf("") }
  var variantsDirty by remember(id) { mutableStateOf(false) }
  var activeVariantImageIndex by remember(id) { mutableStateOf<Int?>(null) }
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
        .onFailure {
          imageLoadError = it.message ?: AppLanguageManager.tr(
            "Product images could not be loaded.",
            "پروڈکٹ تصاویر لوڈ نہیں ہو سکیں۔"
          )
        }
      imagesLoading = false

      variantsLoading = true
      variantsLoadError = ""
      runCatching {
        val rawVariants = VendorService.listVariants(id)
        val inventoryRows = runCatching { VendorService.listInventory() }.getOrDefault(JSONArray())
        val stockByVariantId = mutableMapOf<String, Int>()
        for (j in 0 until inventoryRows.length()) {
          val inv = inventoryRows.optJSONObject(j) ?: continue
          val vid = inv.vendorEditCleanString("variant_id")
          if (vid.isNotBlank()) {
            stockByVariantId[vid] = inv.optInt("quantity", 0)
          }
        }
        (0 until rawVariants.length()).mapNotNull { idx ->
          val row = rawVariants.optJSONObject(idx) ?: return@mapNotNull null
          val vid = row.vendorEditCleanString("id").ifBlank { null }
          val priceStr = if (row.isNull("price_override")) "" else row.vendorEditCleanString("price_override")
          val imgUrl = row.vendorEditCleanString("image_url").ifBlank { null }
          val attrs = row.optJSONObject("attributes")?.toString() ?: "{}"
          EditableProductVariant(
            id = vid,
            optionLabel = row.vendorEditCleanString("option_label", "Option").ifBlank { "Option" },
            optionValue = row.vendorEditCleanString("option_value"),
            sku = row.vendorEditCleanString("sku"),
            priceOverride = priceStr,
            stock = (vid?.let { stockByVariantId[it] } ?: 0).toString(),
            imageUrl = imgUrl,
            localImageUri = null,
            attributesJson = attrs
          )
        }
      }.onSuccess { loaded ->
        variants = loaded
        variantsLoadedSuccessfully = true
        variantsDirty = false
      }.onFailure { err ->
        variantsLoadedSuccessfully = false
        variantsLoadError = err.message ?: AppLanguageManager.tr(
          "Product variants could not be loaded.",
          "پروڈکٹ ویرینٹس لوڈ نہیں ہو سکے۔"
        )
      }
      variantsLoading = false
    } else {
      imagesLoading = false
      variantsLoading = false
      variantsLoadedSuccessfully = true
    }
  }

  val totalImages = existingImageUrls.size + newImages.size
  val showFoodFields = remember(categories, category) { categoryTreeContainsFood(categories, category) }
  val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selected ->
    val remaining = (8 - existingImageUrls.size - newImages.size).coerceAtLeast(0)
    newImages = (newImages + selected.take(remaining)).distinct().take((8 - existingImageUrls.size).coerceAtLeast(0))
    if (selected.size > remaining) {
      errorMessage = AppLanguageManager.tr(
        "A product can have up to 8 images total.",
        "ایک پروڈکٹ کی زیادہ سے زیادہ 8 تصاویر ہو سکتی ہیں۔"
      )
    }
  }
  val variantImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedUri ->
    val idx = activeVariantImageIndex
    if (selectedUri != null && idx != null && idx in variants.indices) {
      variants = variants.toMutableList().also {
        it[idx] = it[idx].copy(localImageUri = selectedUri)
      }
      variantsDirty = true
    }
    activeVariantImageIndex = null
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
          title = appText("Edit product", "پروڈکٹ میں ترمیم کریں"),
          subtitle = if (allowDirectPublish) {
            appText(
              "Direct publishing is enabled for this shop. Stock is managed in Inventory.",
              "اس دکان کے لیے براہ راست اشاعت فعال ہے۔ اسٹاک کا انتظام انوینٹری میں ہوتا ہے۔"
            )
          } else {
            appText(
              "Changes are sent through JTBazar moderation. Stock is managed in Inventory.",
              "تبدیلیاں جے ٹی بازار کی جانچ کے لیے بھیجی جاتی ہیں۔ اسٹاک کا انتظام انوینٹری میں ہوتا ہے۔"
            )
          },
          actionLabel = appText("Back", "واپس"),
          actionIcon = Icons.AutoMirrored.Rounded.ArrowBack,
          onActionClick = { if (!saving) onBack() }
        )
      }

      item {
        VendorAlertBanner(
          message = if (allowDirectPublish) {
            appText(
              "Direct publishing is enabled by admin. Eligible updates publish live after verification.",
              "ایڈمن کی جانب سے براہ راست اشاعت فعال ہے۔ اہل تبدیلیاں تصدیق کے بعد فوراً شائع ہو جائیں گی۔"
            )
          } else {
            appText(
              "Product updates remain pending until reviewed by JTBazar moderation.",
              "جے ٹی بازار کی جانچ مکمل ہونے تک پروڈکٹ کی تبدیلیاں زیر التواء رہتی ہیں۔"
            )
          },
          isError = false,
          modifier = Modifier.testTag("edit_publishing_mode_banner")
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
            SectionHeader(title = appText("Product details", "پروڈکٹ کی تفصیلات"))
            VendorOutlinedTextField(
              value = name,
              onValueChange = { name = it },
              label = appText("Product name *", "پروڈکٹ کا نام *"),
              leadingIcon = Icons.Rounded.Inventory2
            )
            VendorOutlinedTextField(
              value = slug,
              onValueChange = { slug = it.lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-') },
              label = appText("URL slug *", "یو آر ایل سلگ *"),
              leadingIcon = Icons.Rounded.Link
            )
            VendorOutlinedTextField(
              value = sku,
              onValueChange = { sku = it },
              label = appText("SKU", "ایس کے یو (SKU)"),
              leadingIcon = Icons.Rounded.QrCode2
            )

            var categoryExpanded by remember(id) { mutableStateOf(false) }
            val selectedCategoryName = (0 until categories.length()).mapNotNull { categories.optJSONObject(it) }
              .firstOrNull { it.optSafeString("id") == category }?.optSafeString("name")
              ?: if (category.isBlank()) appText("Select category", "کیٹیگری منتخب کریں") else category
            ExposedDropdownMenuBox(
              expanded = categoryExpanded,
              onExpandedChange = { categoryExpanded = !categoryExpanded }
            ) {
              OutlinedTextField(
                value = selectedCategoryName,
                onValueChange = {},
                readOnly = true,
                label = { Text(appText("Category", "کیٹیگری"), style = MaterialTheme.typography.bodySmall) },
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
              title = appText("Product images ($totalImages/8)", "پروڈکٹ تصاویر ($totalImages/8)"),
              subtitle = appText("Up to 8 images, max 10 MB each", "زیادہ سے زیادہ 8 تصاویر، ہر ایک زیادہ سے زیادہ 10 MB"),
              actionLabel = if (totalImages < 8) appText("Add images", "تصاویر شامل کریں") else null,
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
                Text(
                  appText("Loading saved images...", "محفوظ تصاویر لوڈ ہو رہی ہیں..."),
                  style = MaterialTheme.typography.bodySmall
                )
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
                      contentDescription = appText("Saved product image ${index + 1}", "محفوظ پروڈکٹ تصویر ${index + 1}"),
                      modifier = Modifier
                        .size(104.dp)
                        .clip(VendorShapes.input),
                      contentScale = ContentScale.Crop,
                      onError = {
                        imageLoadError = AppLanguageManager.tr(
                          "A saved product image could not be displayed.",
                          "محفوظ پروڈکٹ تصویر دکھائی نہیں جا سکی۔"
                        )
                      }
                    )
                    Text(
                      text = if (index == 0) appText("Main image", "مرکزی تصویر") else appText("Saved ${index + 1}", "محفوظ ${index + 1}"),
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
                        contentDescription = appText("New product image ${index + 1}", "نئی پروڈکٹ تصویر ${index + 1}"),
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
                        Text(appText("New ${index + 1}", "نئی ${index + 1}"), style = MaterialTheme.typography.labelSmall)
                        TextButton(
                          enabled = !saving,
                          onClick = { newImages = newImages.filterIndexed { i, _ -> i != index } },
                          contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                          Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = appText("Remove image", "تصویر ہٹائیں"),
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
                text = appText("No saved product images yet. Tap Add images above.", "ابھی کوئی پروڈکٹ تصویر محفوظ نہیں۔ اوپر تصاویر شامل کریں پر ٹیپ کریں۔"),
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
            SectionHeader(title = appText("Description", "تفصیل"))
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
          }
        }
      }

      // Pricing & Margin
      item {
        AppCard {
          Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
            SectionHeader(title = appText("Pricing & margin", "قیمت اور منافع"))
            VendorOutlinedTextField(
              value = price,
              onValueChange = { price = it.filter { ch -> ch.isDigit() || ch == '.' } },
              label = appText("Vendor base price (PKR) *", "فروشندہ کی بنیادی قیمت (روپے) *"),
              leadingIcon = Icons.Rounded.AttachMoney,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            VendorOutlinedTextField(
              value = discount,
              onValueChange = { discount = it.filter { ch -> ch.isDigit() || ch == '.' } },
              label = appText("Discount (%)", "رعایت (%)"),
              leadingIcon = Icons.Rounded.Percent,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            VendorOutlinedTextField(
              value = supplierCost,
              onValueChange = { supplierCost = it.filter { ch -> ch.isDigit() || ch == '.' } },
              label = appText("Supplier cost (private PKR)", "سپلائر لاگت (نجی روپے)"),
              leadingIcon = Icons.Rounded.AttachMoney,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            VendorOutlinedTextField(
              value = fulfilment,
              onValueChange = { fulfilment = it.filter { ch -> ch.isDigit() || ch == '.' } },
              label = appText("Fulfilment expense (PKR)", "پیکنگ و ترسیل خرچ (روپے)"),
              leadingIcon = Icons.Rounded.AttachMoney,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            VendorOutlinedTextField(
              value = minimumProfit,
              onValueChange = { minimumProfit = it.filter { ch -> ch.isDigit() || ch == '.' } },
              label = appText("Minimum profit (PKR)", "کم از کم منافع (روپے)"),
              leadingIcon = Icons.Rounded.AttachMoney,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            VendorOutlinedTextField(
              value = weight,
              onValueChange = { weight = it.filter(Char::isDigit) },
              label = appText("Shipping weight (grams)", "شپنگ وزن (گرام)"),
              leadingIcon = Icons.Rounded.Scale,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
          }
        }
      }

      // Product Variants
      item {
        AppCard {
          Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
            SectionHeader(
              title = appText("Product variants (${variants.size})", "پروڈکٹ ویرینٹس (${variants.size})"),
              subtitle = appText(
                "View, add, edit or remove variants with dedicated price, stock and image.",
                "الگ قیمت، اسٹاک اور تصویر کے ساتھ ویرینٹس دیکھیں، شامل کریں، ترمیم کریں یا ہٹائیں۔"
              ),
              actionLabel = appText("Add variant", "ویرینٹ شامل کریں"),
              actionIcon = Icons.Rounded.Add,
              onActionClick = {
                if (!saving) {
                  variants = variants + EditableProductVariant()
                  variantsDirty = true
                }
              }
            )
            if (variantsLoading) {
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
                Text(
                  appText("Loading existing variants...", "موجودہ ویرینٹس لوڈ ہو رہے ہیں..."),
                  style = MaterialTheme.typography.bodySmall
                )
              }
            }
            if (variantsLoadError.isNotBlank()) {
              VendorAlertBanner(message = variantsLoadError, isError = true)
            }
            if (!variantsLoading && variants.isEmpty() && variantsLoadError.isBlank()) {
              Text(
                text = appText(
                  "No variants added yet. Tap Add variant if this product has colours, sizes, weights or packs.",
                  "ابھی کوئی ویرینٹ شامل نہیں۔ اگر اس پروڈکٹ کے رنگ، سائز، وزن یا پیک ہیں تو ویرینٹ شامل کریں۔"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = VendorColors.TextSecondary
              )
            }
            variants.forEachIndexed { index, variant ->
              Surface(
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("vendor_edit_variant_card_$index"),
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
                    TextButton(
                      enabled = !saving,
                      onClick = {
                        variants = variants.filterIndexed { i, _ -> i != index }
                        variantsDirty = true
                      }
                    ) {
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
                    value = variant.optionLabel,
                    onValueChange = { v ->
                      variants = variants.toMutableList().also { it[index] = variant.copy(optionLabel = v) }
                      variantsDirty = true
                    },
                    label = appText("Option name (e.g. Size, Color, Pack)", "آپشن کا نام (مثلاً سائز، رنگ، پیک)"),
                    leadingIcon = Icons.Rounded.Style
                  )
                  VendorOutlinedTextField(
                    value = variant.optionValue,
                    onValueChange = { v ->
                      variants = variants.toMutableList().also { it[index] = variant.copy(optionValue = v) }
                      variantsDirty = true
                    },
                    label = appText("Option value *", "آپشن کی قدر *"),
                    leadingIcon = Icons.Rounded.Style
                  )
                  VendorOutlinedTextField(
                    value = variant.sku,
                    onValueChange = { v ->
                      variants = variants.toMutableList().also { it[index] = variant.copy(sku = v) }
                      variantsDirty = true
                    },
                    label = appText("Variant SKU", "ویرینٹ SKU"),
                    leadingIcon = Icons.Rounded.QrCode2
                  )
                  VendorOutlinedTextField(
                    value = variant.priceOverride,
                    onValueChange = { v ->
                      val clean = v.filter { ch -> ch.isDigit() || ch == '.' }
                      variants = variants.toMutableList().also { it[index] = variant.copy(priceOverride = clean) }
                      variantsDirty = true
                    },
                    label = appText("Price override (optional PKR)", "مخصوص قیمت (اختیاری روپے)"),
                    leadingIcon = Icons.Rounded.AttachMoney,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                  )
                  VendorOutlinedTextField(
                    value = variant.stock,
                    onValueChange = { v ->
                      variants = variants.toMutableList().also { it[index] = variant.copy(stock = v.filter(Char::isDigit)) }
                      variantsDirty = true
                    },
                    label = appText("Variant stock quantity", "ویرینٹ اسٹاک مقدار"),
                    leadingIcon = Icons.Rounded.Inventory2,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                  )
                  val previewModel: Any? = variant.localImageUri ?: variant.imageUrl?.takeIf { it.isNotBlank() }
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
                  ) {
                    if (previewModel != null) {
                      AsyncImage(
                        model = previewModel,
                        contentDescription = appText("Variant image", "ویرینٹ تصویر"),
                        modifier = Modifier
                          .size(56.dp)
                          .clip(VendorShapes.badge),
                        contentScale = ContentScale.Crop
                      )
                    } else {
                      Surface(
                        modifier = Modifier.size(56.dp),
                        shape = VendorShapes.badge,
                        color = VendorColors.CardWhite,
                        border = BorderStroke(1.dp, VendorColors.BorderSubtle)
                      ) {
                        Icon(
                          imageVector = Icons.Rounded.Image,
                          contentDescription = null,
                          tint = VendorColors.TextSecondary,
                          modifier = Modifier.padding(14.dp)
                        )
                      }
                    }
                    Row(
                      modifier = Modifier.weight(1f),
                      horizontalArrangement = Arrangement.spacedBy(VendorSpacing.xs)
                    ) {
                      TextButton(
                        enabled = !saving,
                        onClick = {
                          activeVariantImageIndex = index
                          variantImagePicker.launch("image/*")
                        },
                        modifier = Modifier.testTag("vendor_edit_variant_pick_image_$index")
                      ) {
                        Icon(
                          imageVector = Icons.Rounded.AddPhotoAlternate,
                          contentDescription = null,
                          modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                          if (previewModel == null) appText("Variant image", "ویرینٹ تصویر")
                          else appText("Change image", "تصویر تبدیل کریں")
                        )
                      }
                      if (previewModel != null) {
                        TextButton(
                          enabled = !saving,
                          onClick = {
                            variants = variants.toMutableList().also {
                              it[index] = variant.copy(imageUrl = null, localImageUri = null)
                            }
                            variantsDirty = true
                          }
                        ) {
                          Text(appText("Clear", "ہٹائیں"), color = VendorColors.DangerRed)
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Food & Groceries (conditional)
      if (showFoodFields) {
        item {
          AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
              SectionHeader(title = appText("Food & groceries", "کھانا اور گروسری"))
              var unitExpanded by remember(id) { mutableStateOf(false) }
              val units = listOf(
                "kg" to appText("Kilogram (kg)", "کلوگرام (kg)"),
                "g" to appText("Gram (g)", "گرام (g)"),
                "l" to appText("Litre (L)", "لیٹر (L)"),
                "ml" to appText("Millilitre (ml)", "ملی لیٹر (ml)"),
                "piece" to appText("Piece", "عدد"),
                "dozen" to appText("Dozen", "درجن"),
                "pack" to appText("Pack", "پیک")
              )
              ExposedDropdownMenuBox(
                expanded = unitExpanded,
                onExpandedChange = { unitExpanded = !unitExpanded }
              ) {
                OutlinedTextField(
                  value = units.firstOrNull { it.first == foodUnit }?.second ?: appText("Kilogram (kg)", "کلوگرام (kg)"),
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
                label = appText("Price per $foodUnit (PKR)", "فی $foodUnit قیمت (روپے)"),
                leadingIcon = Icons.Rounded.AttachMoney,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
              )
              VendorOutlinedTextField(
                value = foodStep,
                onValueChange = { foodStep = it.filter(Char::isDigit) },
                label = appText(
                  "Customer quantity step (${if (foodUnit == "kg") "grams" else if (foodUnit == "l") "ml" else "units"})",
                  "خریدار کی مقدار کا مرحلہ (${if (foodUnit == "kg") "گرام" else if (foodUnit == "l") "ملی لیٹر" else "اکائیاں"})"
                ),
                leadingIcon = Icons.Rounded.Scale,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = appText(
                  "For kg/litre the step is grams/ml; for dozen, piece and pack it is units.",
                  "کلو/لیٹر کے لیے مرحلہ گرام/ملی لیٹر میں اور درجن، عدد اور پیک کے لیے اکائیوں میں ہوتا ہے۔"
                )
              )
              VendorOutlinedTextField(
                value = foodMinimum,
                onValueChange = { foodMinimum = it.filter(Char::isDigit) },
                label = appText("Minimum purchase steps", "کم از کم خریداری کے مراحل"),
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
                    text = appText("Manage as a daily dish / daily food item", "روزانہ کی ڈش / خوراک کے طور پر منظم کریں"),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                  )
                }
              }
              var statusExpanded by remember(id) { mutableStateOf(false) }
              val statuses = listOf(
                "available" to appText("Available today", "آج دستیاب"),
                "not_today" to appText("Not for today", "آج کے لیے نہیں"),
                "coming_soon" to appText("Coming soon", "جلد آ رہا ہے")
              )
              ExposedDropdownMenuBox(
                expanded = statusExpanded,
                onExpandedChange = { statusExpanded = !statusExpanded }
              ) {
                OutlinedTextField(
                  value = statuses.firstOrNull { it.first == dailyStatus }?.second ?: appText("Available today", "آج دستیاب"),
                  onValueChange = {},
                  readOnly = true,
                  label = { Text(appText("Daily availability status", "روزانہ دستیابی کی حیثیت")) },
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
                label = appText("Today's available quantity", "آج دستیاب مقدار"),
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
            SectionHeader(title = appText("Attributes & search appearance", "خصوصیات اور تلاش میں ظاہری شکل"))
            VendorOutlinedTextField(
              value = specs,
              onValueChange = { specs = it },
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
            HorizontalDivider(color = VendorColors.Divider)
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
          text = appText("Cancel", "منسوخ کریں"),
          icon = Icons.Rounded.Close,
          enabled = !saving,
          onClick = onBack,
          modifier = Modifier.weight(1f)
        )
        PrimaryButton(
          text = if (saving) appText("Saving...", "محفوظ ہو رہا ہے...") else appText("Save product", "پروڈکٹ محفوظ کریں"),
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
                  ?: error(
                    if (showFoodFields) AppLanguageManager.tr("Enter a valid food unit price.", "درست فی اکائی خوراک قیمت درج کریں۔")
                    else AppLanguageManager.tr("Enter a valid price.", "درست قیمت درج کریں۔")
                  )
                val discountValue = discount.toDoubleOrNull()
                  ?: error(AppLanguageManager.tr("Enter a valid discount.", "درست رعایت درج کریں۔"))
                require(discountValue in 0.0..100.0) {
                  AppLanguageManager.tr("Discount must be between 0 and 100%.", "رعایت 0 سے 100% کے درمیان ہونی چاہیے۔")
                }
                val payload = JSONObject()
                  .put("p_category_id", category.ifBlank { JSONObject.NULL })
                  .put("p_name", name.trim())
                  .put("p_slug", slug.trim())
                  .put("p_sku", sku.trim().ifBlank { JSONObject.NULL })
                  .put("p_short_description", shortDescription.trim())
                  .put("p_description", description.trim())
                  .put("p_price", basePrice)
                  .put("p_supplier_cost", supplierCost.toDoubleOrNull() ?: JSONObject.NULL)
                  .put(
                    "p_fulfillment_expense",
                    fulfilment.toDoubleOrNull() ?: error(AppLanguageManager.tr("Enter valid fulfilment expense.", "درست پیکنگ و ترسیل خرچ درج کریں۔"))
                  )
                  .put(
                    "p_minimum_profit",
                    minimumProfit.toDoubleOrNull() ?: error(AppLanguageManager.tr("Enter valid minimum profit.", "درست کم از کم منافع درج کریں۔"))
                  )
                  .put("p_discount_percent", discountValue)
                  .put("p_weight_grams", weight.toIntOrNull() ?: JSONObject.NULL)
                  .put("p_specifications", JSONArray(specs))
                  .put("p_dimensions", JSONObject(dimensions))
                  .put("p_seo_title", seoTitle.trim().ifBlank { JSONObject.NULL })
                  .put("p_seo_description", seoDescription.trim().ifBlank { JSONObject.NULL })
                VendorService.updateProductFull(id, payload)
                if (showFoodFields) {
                  val step = foodStep.toIntOrNull()
                    ?: error(AppLanguageManager.tr("Enter a valid customer quantity step.", "درست مقدار کا مرحلہ درج کریں۔"))
                  val minimum = foodMinimum.toIntOrNull()
                    ?: error(AppLanguageManager.tr("Enter valid minimum purchase steps.", "درست کم از کم خریداری مراحل درج کریں۔"))
                  val quantity = dailyQuantity.toIntOrNull()
                    ?: error(AppLanguageManager.tr("Enter a valid daily available quantity.", "آج دستیاب درست مقدار درج کریں۔"))
                  require(step > 0 && minimum > 0 && quantity >= 0) {
                    AppLanguageManager.tr("Check food quantity settings.", "خوراک کی مقدار کی ترتیبات چیک کریں۔")
                  }
                  VendorService.updateFoodProductDetails(
                    id, foodUnit, basePrice, step, minimum, dailyItem, dailyStatus, quantity
                  )
                }
                newImages.forEach { uri ->
                  val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                  val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error(AppLanguageManager.tr("Image could not be read.", "تصویر پڑھی نہیں جا سکی۔"))
                  require(bytes.size <= 10 * 1024 * 1024) {
                    AppLanguageManager.tr("Each image must be 10 MB or smaller.", "ہر تصویر 10 MB یا اس سے کم ہونی چاہیے۔")
                  }
                  VendorService.uploadProductImage(id, bytes, mime, name.trim())
                }
                existingImageUrls = VendorService.listProductImageUrls(id)
                newImages = emptyList()
                if (variantsDirty || variants.any { it.localImageUri != null }) {
                  if (!variantsLoadedSuccessfully && variantsLoadError.isNotBlank() && variants.isEmpty()) {
                    error(AppLanguageManager.tr("Existing variants failed to load; reload before saving variants.", "موجودہ ویرینٹس لوڈ نہیں ہو سکے؛ ویرینٹس محفوظ کرنے سے پہلے دوبارہ لوڈ کریں۔"))
                  }
                  val seenPairs = mutableSetOf<String>()
                  val variantJson = JSONArray()
                  for (v in variants) {
                    val labelClean = v.optionLabel.trim()
                    val valueClean = v.optionValue.trim()
                    require(labelClean.isNotBlank() && valueClean.isNotBlank()) {
                      AppLanguageManager.tr("Complete every variant option name and value.", "ہر ویرینٹ آپشن کا نام اور قدر مکمل کریں۔")
                    }
                    require(seenPairs.add("${labelClean.lowercase(Locale.ROOT)}::${valueClean.lowercase(Locale.ROOT)}")) {
                      AppLanguageManager.tr("Duplicate variant option: $labelClean ($valueClean).", "دہرایا گیا ویرینٹ آپشن: $labelClean ($valueClean)۔")
                    }
                    val stockQty = v.stock.toIntOrNull()
                      ?: error(AppLanguageManager.tr("Enter valid stock for variant $valueClean.", "ویرینٹ $valueClean کے لیے درست اسٹاک درج کریں۔"))
                    require(stockQty >= 0) {
                      AppLanguageManager.tr("Variant stock cannot be negative.", "ویرینٹ اسٹاک منفی نہیں ہو سکتا۔")
                    }
                    val overridePrice: Any = if (v.priceOverride.isBlank()) {
                      JSONObject.NULL
                    } else {
                      val parsedPrice = v.priceOverride.toDoubleOrNull()
                        ?: error(AppLanguageManager.tr("Enter valid price for variant $valueClean.", "ویرینٹ $valueClean کے لیے درست قیمت درج کریں۔"))
                      require(parsedPrice > 0.0) {
                        AppLanguageManager.tr("Variant price override must be greater than zero.", "ویرینٹ کی مخصوص قیمت صفر سے زیادہ ہونی چاہیے۔")
                      }
                      parsedPrice
                    }
                    val resolvedVariantImageUrl = if (v.localImageUri != null) {
                      val mime = context.contentResolver.getType(v.localImageUri) ?: "image/jpeg"
                      val bytes = context.contentResolver.openInputStream(v.localImageUri)?.use { it.readBytes() }
                        ?: error(AppLanguageManager.tr("Variant image could not be read.", "ویرینٹ تصویر پڑھی نہیں جا سکی۔"))
                      VendorService.uploadVariantImage(id, bytes, mime)
                    } else {
                      v.imageUrl?.takeIf { it.isNotBlank() }
                    }
                    val attrsObj = runCatching { JSONObject(v.attributesJson) }.getOrDefault(JSONObject())
                    val row = JSONObject()
                      .put("id", v.id ?: JSONObject.NULL)
                      .put("option_label", labelClean)
                      .put("option_value", valueClean)
                      .put("sku", v.sku.trim().ifBlank { JSONObject.NULL })
                      .put("price_override", overridePrice)
                      .put("image_url", resolvedVariantImageUrl ?: JSONObject.NULL)
                      .put("attributes", attrsObj)
                      .put("quantity", stockQty)
                    variantJson.put(row)
                  }
                  VendorService.saveVariants(id, variantJson)
                  val reloaded = VendorService.listVariants(id)
                  val invAfter = runCatching { VendorService.listInventory() }.getOrDefault(JSONArray())
                  val stockAfter = mutableMapOf<String, Int>()
                  for (j in 0 until invAfter.length()) {
                    val inv = invAfter.optJSONObject(j) ?: continue
                    val vid = inv.vendorEditCleanString("variant_id")
                    if (vid.isNotBlank()) stockAfter[vid] = inv.optInt("quantity", 0)
                  }
                  variants = (0 until reloaded.length()).mapNotNull { idx ->
                    val row = reloaded.optJSONObject(idx) ?: return@mapNotNull null
                    val vid = row.vendorEditCleanString("id").ifBlank { null }
                    EditableProductVariant(
                      id = vid,
                      optionLabel = row.vendorEditCleanString("option_label", "Option"),
                      optionValue = row.vendorEditCleanString("option_value"),
                      sku = row.vendorEditCleanString("sku"),
                      priceOverride = if (row.isNull("price_override")) "" else row.vendorEditCleanString("price_override"),
                      stock = (vid?.let { stockAfter[it] } ?: 0).toString(),
                      imageUrl = row.vendorEditCleanString("image_url").ifBlank { null },
                      localImageUri = null,
                      attributesJson = row.optJSONObject("attributes")?.toString() ?: "{}"
                    )
                  }
                  variantsDirty = false
                }
                val outcome = VendorService.finalizeProductSubmission(
                  productId = id,
                  allowDirectPublish = allowDirectPublish,
                  isEdit = true
                )
                if (outcome.finalizationError != null) {
                  errorMessage = outcome.finalizationError
                } else {
                  message = outcome.message
                }
                onSaved(outcome)
              } catch (e: Exception) {
                errorMessage = e.message ?: AppLanguageManager.tr("Product could not be updated.", "پروڈکٹ اپڈیٹ نہیں ہو سکی۔")
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
