package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PinDrop
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.repository.MarketplaceRepository
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.VendorApplication
import com.example.data.supabase.VendorService
import com.example.data.supabase.WeeklyShopHours
import com.example.localization.AppLanguageManager
import com.example.localization.appText
import com.example.ui.components.AppCard
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.components.StatusTone
import com.example.ui.components.VendorAlertBanner
import com.example.ui.components.VendorOutlinedTextField
import com.example.ui.theme.VendorCenterTheme
import com.example.ui.theme.VendorColors
import com.example.ui.theme.VendorShapes
import com.example.ui.theme.VendorSpacing
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.util.Locale

@Composable
fun VendorShopSettingsScreen(
  application: VendorApplication,
  accountEmail: String,
  onSaved: suspend () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val currentUser by SupabaseClient.currentUser.collectAsState()
  val resolvedEmail = accountEmail.ifBlank { application.contactEmail }
  var shopName by remember(application.id) { mutableStateOf(application.shopName) }
  var slug by remember(application.id) { mutableStateOf(application.slug) }
  var vendorType by remember(application.id) { mutableStateOf(application.vendorType) }
  var ownerName by remember(application.id) { mutableStateOf(application.ownerFullName) }
  var cnic by remember(application.id) { mutableStateOf(application.cnicNumber) }
  var phone by remember(application.id) { mutableStateOf(application.phone) }
  var description by remember(application.id) { mutableStateOf(application.description) }
  var currentAddress by remember(application.id) { mutableStateOf(application.currentAddress) }
  var completeAddress by remember(application.id) { mutableStateOf(application.completeAddress) }
  var locationText by remember(application.id) { mutableStateOf(application.shopLocationText) }
  var locationLabel by remember(application.id) { mutableStateOf(application.shopLocationText) }
  var latitude by remember(application.id) { mutableStateOf(application.shopLat?.toString().orEmpty()) }
  var longitude by remember(application.id) { mutableStateOf(application.shopLng?.toString().orEmpty()) }
  var showOnMap by remember(application.id) { mutableStateOf(application.showShopOnMap) }
  var shopImageUri by remember(application.id) { mutableStateOf<Uri?>(null) }
  var shopImageUrl by remember(application.id) { mutableStateOf(application.shopImageUrl) }
  var shopIsOpen by remember(application.id, application.shopIsOpen) { mutableStateOf(application.shopIsOpen) }
  var weeklyHours by remember(application.id, application.shopHours) { mutableStateOf(application.shopHours) }
  var documentPath by remember(application.id) { mutableStateOf(application.documentPath) }
  var frontPath by remember(application.id) { mutableStateOf(application.cnicFrontPath) }
  var backPath by remember(application.id) { mutableStateOf(application.cnicBackPath) }
  var documentKind by remember { mutableStateOf("verification") }
  var mapOpen by remember { mutableStateOf(false) }
  var saving by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }
  var info by remember { mutableStateOf("") }

  val shopPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
    shopImageUri = it
  }
  val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri != null) scope.launch {
      saving = true
      errorMessage = ""
      info = ""
      try {
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        require(mime in listOf("application/pdf", "image/jpeg", "image/png", "image/webp")) {
          AppLanguageManager.tr("Use PDF, JPG, PNG or WebP files.", "PDF، JPG، PNG یا WebP فائلیں استعمال کریں۔")
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: error(AppLanguageManager.tr("Selected file could not be read.", "منتخب کردہ فائل پڑھی نہیں جا سکی۔"))
        require(bytes.size <= 10 * 1024 * 1024) {
          AppLanguageManager.tr("Verification files must be 10 MB or smaller.", "تصدیقی فائلیں 10 MB یا اس سے کم ہونی چاہئیں۔")
        }
        val ext = when (mime) {
          "application/pdf" -> "pdf"
          "image/jpeg" -> "jpg"
          "image/png" -> "png"
          else -> "webp"
        }
        val ownerId = currentUser?.id ?: error(AppLanguageManager.tr("Please sign in again.", "براہ کرم دوبارہ سائن ان کریں۔"))
        val path = "$ownerId/$documentKind-${System.currentTimeMillis()}.$ext"
        val uploaded = VendorService.uploadPrivateDocument(path, bytes, mime)
        when (documentKind) {
          "cnic-front" -> frontPath = uploaded
          "cnic-back" -> backPath = uploaded
          else -> documentPath = uploaded
        }
        info = AppLanguageManager.tr(
          "Private verification file uploaded securely.",
          "نجی تصدیقی فائل محفوظ طریقے سے اپلوڈ ہو گئی۔"
        )
      } catch (e: Exception) {
        errorMessage = e.message ?: AppLanguageManager.tr(
          "Verification file upload failed.",
          "تصدیقی فائل اپلوڈ نہیں ہو سکی۔"
        )
      } finally {
        saving = false
      }
    }
  }

  val uiState = VendorShopSettingsUiState(
    shopName = shopName,
    slug = slug,
    accountEmail = resolvedEmail,
    vendorType = vendorType,
    businessType = application.businessType,
    ownerFullName = ownerName,
    cnicNumber = cnic,
    phone = phone,
    description = description,
    currentAddress = currentAddress,
    completeAddress = completeAddress,
    locationLabel = locationLabel,
    locationText = locationText,
    showOnMap = showOnMap,
    latitude = latitude,
    longitude = longitude,
    shopImageUrl = shopImageUrl,
    hasLocalShopImage = shopImageUri != null,
    shopIsOpen = shopIsOpen,
    weeklyHours = weeklyHours,
    frontPath = frontPath,
    backPath = backPath,
    documentPath = documentPath,
    isSaving = saving,
    errorMessage = errorMessage,
    infoMessage = info
  )

  val previewModel: Any? = shopImageUri ?: shopImageUrl.takeIf { it.isNotBlank() }

  VendorShopSettingsContent(
    uiState = uiState,
    previewImageModel = previewModel,
    onShopNameChange = { shopName = it },
    onSlugChange = { slug = it.lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-') },
    onVendorTypeChange = { vendorType = it },
    onOwnerNameChange = { ownerName = it },
    onCnicChange = { cnic = it.filter(Char::isDigit).take(13) },
    onPhoneChange = { phone = it },
    onDescriptionChange = { description = it },
    onCurrentAddressChange = { currentAddress = it },
    onCompleteAddressChange = { completeAddress = it },
    onLocationLabelChange = { locationLabel = it },
    onShowOnMapChange = { showOnMap = it },
    onOpenMapPicker = { mapOpen = true },
    onLocationTextChange = { locationText = it },
    onPickShopImage = { shopPicker.launch("image/*") },
    onShopIsOpenChange = { shopIsOpen = it },
    onWeeklyHoursChange = { weeklyHours = it },
    onUploadCnicFront = {
      documentKind = "cnic-front"
      documentPicker.launch("image/*")
    },
    onUploadCnicBack = {
      documentKind = "cnic-back"
      documentPicker.launch("image/*")
    },
    onUploadVerificationDoc = {
      documentKind = "verification"
      documentPicker.launch("*/*")
    },
    onSaveClick = {
      scope.launch {
        saving = true
        errorMessage = ""
        info = ""
        try {
          require(shopName.trim().length >= 2 && slug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) {
            AppLanguageManager.tr("Enter a valid shop name and URL slug.", "درست دکان کا نام اور URL سلگ درج کریں۔")
          }
          require(resolvedEmail.isNotBlank() && resolvedEmail.trim().equals(currentUser?.email?.trim(), true)) {
            AppLanguageManager.tr(
              "Use the same verified email as your signed-in JTBazar account.",
              "اپنے سائن ان شدہ جے ٹی بازار اکاؤنٹ والا تصدیق شدہ ای میل استعمال کریں۔"
            )
          }
          require(ownerName.trim().length >= 3 && cnic.matches(Regex("[0-9]{13}"))) {
            AppLanguageManager.tr("Enter the owner's legal name and 13-digit CNIC.", "مالک کا قانونی نام اور 13 ہندسوں کا شناختی کارڈ نمبر درج کریں۔")
          }
          require(phone.matches(Regex("^(03[0-9]{9}|\\+923[0-9]{9})$"))) {
            AppLanguageManager.tr("Enter a valid Pakistani mobile number.", "درست پاکستانی موبائل نمبر درج کریں۔")
          }
          require(
            description.trim().length >= 10 &&
              currentAddress.trim().length >= 3 &&
              completeAddress.trim().length >= 10 &&
              locationText.trim().length >= 10
          ) {
            AppLanguageManager.tr("Complete all required shop details.", "دکان کی تمام ضروری تفصیلات مکمل کریں۔")
          }
          val lat = latitude.toDoubleOrNull()
            ?: error(AppLanguageManager.tr("Choose the required shop map pin.", "دکان کا ضروری میپ پن منتخب کریں۔"))
          val lng = longitude.toDoubleOrNull()
            ?: error(AppLanguageManager.tr("Choose the required shop map pin.", "دکان کا ضروری میپ پن منتخب کریں۔"))
          require(lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0)) {
            AppLanguageManager.tr("Shop map pin is invalid.", "دکان کا میپ پن درست نہیں ہے۔")
          }
          require(frontPath.isNotBlank() && backPath.isNotBlank() && documentPath.isNotBlank()) {
            AppLanguageManager.tr("Upload all three verification documents.", "تینوں تصدیقی دستاویزات اپلوڈ کریں۔")
          }
          val invalidDays = weeklyHours.invalidDays()
          require(invalidDays.isEmpty()) {
            val dayNames = invalidDays.joinToString(", ") { it.localizedName() }
            AppLanguageManager.tr(
              "Closing time must be after opening time for: $dayNames.",
              "بند ہونے کا وقت کھلنے کے وقت کے بعد ہونا چاہیے: $dayNames۔"
            )
          }
          if (shopImageUri != null) {
            val uri = shopImageUri!!
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
              ?: error(AppLanguageManager.tr("Shop picture could not be read.", "دکان کی تصویر پڑھی نہیں جا سکی۔"))
            shopImageUrl = VendorService.uploadShopImage(bytes, mime)
            shopImageUri = null
          }
          VendorService.updateOwnShop(
            JSONObject()
              .put("shop_name", shopName.trim())
              .put("slug", slug.trim())
              .put("contact_email", resolvedEmail.trim())
              .put("vendor_type", vendorType)
              .put("owner_full_name", ownerName.trim())
              .put("cnic_number", cnic)
              .put("phone", phone.trim())
              .put("description", description.trim())
              .put("current_address", currentAddress.trim())
              .put("complete_address", completeAddress.trim())
              .put("shop_location_text", locationText.trim())
              .put("shop_location_label", locationLabel.trim())
              .put("shop_lat", lat)
              .put("shop_lng", lng)
              .put("show_shop_on_map", showOnMap)
              .put("shop_is_open", shopIsOpen)
              .put("shop_hours", weeklyHours.toJson())
              .put("shop_image_url", shopImageUrl.ifBlank { JSONObject.NULL })
              .put("document_path", documentPath)
              .put("cnic_front_path", frontPath)
              .put("cnic_back_path", backPath)
          )
          MarketplaceRepository.refreshShops(force = true)
          info = AppLanguageManager.tr("Shop settings saved successfully.", "دکان کی ترتیبات کامیابی سے محفوظ ہو گئیں۔")
          onSaved()
        } catch (e: Exception) {
          errorMessage = e.message ?: AppLanguageManager.tr(
            "Shop settings could not be saved.",
            "دکان کی ترتیبات محفوظ نہیں ہو سکیں۔"
          )
        } finally {
          saving = false
        }
      }
    },
    onDismissAlert = {
      errorMessage = ""
      info = ""
    }
  )

  if (mapOpen) {
    ShopLocationPickerDialog(
      initialLatitude = latitude.toDoubleOrNull(),
      initialLongitude = longitude.toDoubleOrNull(),
      onDismiss = { mapOpen = false },
      onConfirm = { point: GeoPoint ->
        latitude = String.format(Locale.US, "%.6f", point.latitude)
        longitude = String.format(Locale.US, "%.6f", point.longitude)
        mapOpen = false
      }
    )
  }
}

@Composable
fun VendorShopSettingsContent(
  uiState: VendorShopSettingsUiState,
  previewImageModel: Any?,
  onShopNameChange: (String) -> Unit,
  onSlugChange: (String) -> Unit,
  onVendorTypeChange: (String) -> Unit,
  onOwnerNameChange: (String) -> Unit,
  onCnicChange: (String) -> Unit,
  onPhoneChange: (String) -> Unit,
  onDescriptionChange: (String) -> Unit,
  onCurrentAddressChange: (String) -> Unit,
  onCompleteAddressChange: (String) -> Unit,
  onLocationLabelChange: (String) -> Unit,
  onShowOnMapChange: (Boolean) -> Unit,
  onOpenMapPicker: () -> Unit,
  onLocationTextChange: (String) -> Unit,
  onPickShopImage: () -> Unit,
  onShopIsOpenChange: (Boolean) -> Unit = {},
  onWeeklyHoursChange: (WeeklyShopHours) -> Unit = {},
  onUploadCnicFront: () -> Unit,
  onUploadCnicBack: () -> Unit,
  onUploadVerificationDoc: () -> Unit,
  onSaveClick: () -> Unit,
  onDismissAlert: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(VendorColors.Background)
  ) {
    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .testTag("vendor_shop_settings_list"),
      contentPadding = PaddingValues(VendorSpacing.md),
      verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
    ) {
      item {
        SectionHeader(
          title = appText("Shop settings", "دکان کی ترتیبات"),
          subtitle = appText(
            "Update public storefront details and private verification records.",
            "عوامی دکان کی تفصیلات اور نجی تصدیقی ریکارڈز اپڈیٹ کریں۔"
          )
        )
      }

      if (uiState.errorMessage.isNotBlank()) {
        item {
          VendorAlertBanner(
            message = uiState.errorMessage,
            isError = true,
            onDismiss = onDismissAlert
          )
        }
      }

      if (uiState.infoMessage.isNotBlank()) {
        item {
          VendorAlertBanner(
            message = uiState.infoMessage,
            isError = false,
            onDismiss = onDismissAlert
          )
        }
      }

      // Section 1: Public Shop Details
      item {
        AppCard {
          Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
            SectionHeader(
              title = appText("Shop profile", "دکان کا پروفائل"),
              subtitle = appText("Visible to JTBazar shoppers", "جے ٹی بازار کے خریداروں کو نظر آئے گا")
            )
            VendorOutlinedTextField(
              value = uiState.shopName,
              onValueChange = onShopNameChange,
              label = appText("Shop name *", "دکان کا نام *"),
              leadingIcon = Icons.Rounded.Storefront
            )
            VendorOutlinedTextField(
              value = uiState.slug,
              onValueChange = onSlugChange,
              label = appText("Shop URL slug *", "دکان کا یو آر ایل سلگ *"),
              leadingIcon = Icons.Rounded.Link
            )
            VendorOutlinedTextField(
              value = uiState.accountEmail,
              onValueChange = {},
              label = appText("Account email *", "اکاؤنٹ ای میل *"),
              leadingIcon = Icons.Rounded.Email,
              readOnly = true,
              enabled = false
            )
            VendorTypeDropdown(
              selectedType = uiState.vendorType,
              onTypeSelected = onVendorTypeChange
            )
            BusinessTypeDropdown(
              selectedType = uiState.businessType,
              onTypeSelected = {},
              enabled = false,
              supportingText = appText(
                "Contact marketplace support if your business category needs to change.",
                "کاروبار کی قسم تبدیل کرنے کے لیے مارکیٹ پلیس سپورٹ سے رابطہ کریں۔"
              ),
              modifier = Modifier.testTag("shop_settings_business_type_dropdown")
            )
            VendorOutlinedTextField(
              value = uiState.phone,
              onValueChange = onPhoneChange,
              label = appText("Business phone *", "کاروباری فون نمبر *"),
              leadingIcon = Icons.Rounded.Phone,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            VendorOutlinedTextField(
              value = uiState.description,
              onValueChange = onDescriptionChange,
              label = appText("Shop description *", "دکان کی تفصیل *"),
              leadingIcon = Icons.Rounded.Description,
              minLines = 3
            )
          }
        }
      }

      // Section 1B: Shop Availability & Weekly Opening Hours
      item(key = "shop_availability_and_hours") {
        VendorShopAvailabilityAndHoursSection(
          shopIsOpen = uiState.shopIsOpen,
          weeklyHours = uiState.weeklyHours,
          enabled = !uiState.isSaving,
          onShopIsOpenChange = onShopIsOpenChange,
          onWeeklyHoursChange = onWeeklyHoursChange
        )
      }

      // Section 2: Owner & Address Details
      item {
        AppCard {
          Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
            SectionHeader(
              title = appText("Owner & addresses", "مالک اور پتے"),
              subtitle = appText(
                "Registered business owner and pickup addresses",
                "رجسٹرڈ کاروباری مالک اور پک اپ کے پتے"
              )
            )
            VendorOutlinedTextField(
              value = uiState.ownerFullName,
              onValueChange = onOwnerNameChange,
              label = appText("Owner's full legal name *", "مالک کا مکمل قانونی نام *"),
              leadingIcon = Icons.Rounded.Person
            )
            VendorOutlinedTextField(
              value = uiState.cnicNumber,
              onValueChange = onCnicChange,
              label = appText("CNIC number (13 digits) *", "شناختی کارڈ نمبر (13 ہندسے) *"),
              leadingIcon = Icons.Rounded.CreditCard,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            VendorOutlinedTextField(
              value = uiState.currentAddress,
              onValueChange = onCurrentAddressChange,
              label = appText("Current address *", "موجودہ پتہ *"),
              leadingIcon = Icons.Rounded.LocationOn
            )
            VendorOutlinedTextField(
              value = uiState.completeAddress,
              onValueChange = onCompleteAddressChange,
              label = appText("Complete address *", "مکمل پتہ *"),
              leadingIcon = Icons.Rounded.Home,
              minLines = 2
            )
          }
        }
      }

      // Section 3: Public Shop Picture
      item {
        AppCard {
          Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
            SectionHeader(
              title = appText("Public shop picture", "دکان کی عوامی تصویر"),
              subtitle = appText("JPG, PNG, WebP or AVIF up to 5 MB", "JPG، PNG، WebP یا AVIF زیادہ سے زیادہ 5 MB")
            )
            if (previewImageModel != null) {
              AsyncImage(
                model = previewImageModel,
                contentDescription = appText("Shop picture preview", "دکان کی تصویر کا پیش نظارہ"),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(180.dp)
                  .clip(VendorShapes.input),
                contentScale = ContentScale.Crop
              )
            } else {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(120.dp)
                  .clip(VendorShapes.input)
                  .background(VendorColors.Background),
                contentAlignment = Alignment.Center
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(VendorSpacing.xxs)
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = null,
                    tint = VendorColors.TextSecondary,
                    modifier = Modifier.size(28.dp)
                  )
                  Text(
                    text = appText("No shop picture selected", "دکان کی کوئی تصویر منتخب نہیں"),
                    style = MaterialTheme.typography.bodySmall,
                    color = VendorColors.TextSecondary
                  )
                }
              }
            }
            SecondaryButton(
              text = if (!uiState.hasLocalShopImage && uiState.shopImageUrl.isBlank()) {
                appText("Choose shop picture", "دکان کی تصویر منتخب کریں")
              } else {
                appText("Change shop picture", "دکان کی تصویر تبدیل کریں")
              },
              icon = Icons.Rounded.Image,
              onClick = onPickShopImage,
              modifier = Modifier.fillMaxWidth()
            )
          }
        }
      }

      // Section 4: Explore Map Location
      item {
        AppCard {
          Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
            SectionHeader(
              title = appText("Explore map location", "ایکسپلور نقشے کا مقام"),
              subtitle = appText(
                "Pin location for verification & Explore discovery",
                "تصدیق اور ایکسپلور میں دریافت کے لیے مقام پن کریں"
              )
            )
            VendorOutlinedTextField(
              value = uiState.locationLabel,
              onValueChange = onLocationLabelChange,
              label = appText("Public area label", "علاقے کا عوامی نام"),
              leadingIcon = Icons.Rounded.PinDrop
            )
            Surface(
              shape = VendorShapes.input,
              color = VendorColors.Background,
              border = BorderStroke(1.dp, VendorColors.BorderSubtle),
              modifier = Modifier
                .fillMaxWidth()
                .clip(VendorShapes.input)
                .clickable { onShowOnMapChange(!uiState.showOnMap) }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = VendorSpacing.sm, vertical = VendorSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Checkbox(
                  checked = uiState.showOnMap,
                  onCheckedChange = onShowOnMapChange,
                  colors = CheckboxDefaults.colors(checkedColor = VendorColors.PrimaryOrange)
                )
                Text(
                  text = appText(
                    "Show my approximate shop location on Explore",
                    "ایکسپلور پر میری دکان کا قریبی مقام دکھائیں"
                  ),
                  style = MaterialTheme.typography.bodyMedium,
                  color = VendorColors.TextPrimary,
                  modifier = Modifier.weight(1f)
                )
              }
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = if (uiState.latitude.isNotBlank() && uiState.longitude.isNotBlank()) {
                  appText(
                    "Pinned: ${uiState.latitude}, ${uiState.longitude}",
                    "پن شدہ: ${uiState.latitude}, ${uiState.longitude}"
                  )
                } else {
                  appText("No map pin saved yet", "ابھی کوئی میپ پن محفوظ نہیں")
                },
                style = MaterialTheme.typography.bodySmall,
                color = VendorColors.TextSecondary,
                modifier = Modifier.weight(1f)
              )
              StatusBadge(
                text = if (uiState.latitude.isNotBlank() && uiState.longitude.isNotBlank()) {
                  appText("Pin saved", "پن محفوظ")
                } else {
                  appText("Required", "ضروری")
                },
                tone = if (uiState.latitude.isNotBlank() && uiState.longitude.isNotBlank()) {
                  StatusTone.SUCCESS
                } else {
                  StatusTone.WARNING
                }
              )
            }
            SecondaryButton(
              text = appText("Choose / update shop map pin", "دکان کا میپ پن منتخب یا اپڈیٹ کریں"),
              icon = Icons.Rounded.Map,
              onClick = onOpenMapPicker,
              modifier = Modifier.fillMaxWidth()
            )
            VendorOutlinedTextField(
              value = uiState.locationText,
              onValueChange = onLocationTextChange,
              label = appText("Shop location / landmark for verification *", "تصدیق کے لیے دکان کا مقام یا نشانی *"),
              leadingIcon = Icons.Rounded.LocationCity,
              minLines = 2
            )
          }
        }
      }

      // Section 5: Private Verification Documents
      item {
        AppCard(
          borderColor = VendorColors.NavyHeader.copy(alpha = 0.2f)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(VendorSpacing.xs)
            ) {
              Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = VendorColors.NavyHeader,
                modifier = Modifier.size(20.dp)
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = appText("Private verification documents", "نجی تصدیقی دستاویزات"),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = VendorColors.TextPrimary
                )
                Text(
                  text = appText(
                    "Only authorized JTBazar administrators can open these files.",
                    "صرف مجاز جے ٹی بازار منتظمین ہی یہ فائلیں دیکھ سکتے ہیں۔"
                  ),
                  style = MaterialTheme.typography.bodySmall,
                  color = VendorColors.TextSecondary
                )
              }
            }

            HorizontalDivider(color = VendorColors.Divider)

            VerificationDocRow(
              title = appText("CNIC front *", "شناختی کارڈ کا اگلا رخ *"),
              uploaded = uiState.frontPath.isNotBlank(),
              buttonLabel = if (uiState.frontPath.isBlank()) {
                appText("Upload CNIC front", "شناختی کارڈ کا اگلا رخ اپلوڈ کریں")
              } else {
                appText("Replace CNIC front", "شناختی کارڈ کا اگلا رخ تبدیل کریں")
              },
              enabled = !uiState.isSaving,
              onClick = onUploadCnicFront
            )
            VerificationDocRow(
              title = appText("CNIC back *", "شناختی کارڈ کا پچھلا رخ *"),
              uploaded = uiState.backPath.isNotBlank(),
              buttonLabel = if (uiState.backPath.isBlank()) {
                appText("Upload CNIC back", "شناختی کارڈ کا پچھلا رخ اپلوڈ کریں")
              } else {
                appText("Replace CNIC back", "شناختی کارڈ کا پچھلا رخ تبدیل کریں")
              },
              enabled = !uiState.isSaving,
              onClick = onUploadCnicBack
            )
            VerificationDocRow(
              title = appText("Identity / business document *", "شناخت / کاروباری دستاویز *"),
              uploaded = uiState.documentPath.isNotBlank(),
              buttonLabel = if (uiState.documentPath.isBlank()) {
                appText("Upload verification doc", "تصدیقی دستاویز اپلوڈ کریں")
              } else {
                appText("Replace verification doc", "تصدیقی دستاویز تبدیل کریں")
              },
              enabled = !uiState.isSaving,
              onClick = onUploadVerificationDoc
            )
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(VendorSpacing.xs))
      }
    }

    // Sticky Save Footer
    Surface(
      color = VendorColors.CardWhite,
      shadowElevation = 6.dp,
      border = BorderStroke(1.dp, VendorColors.BorderSubtle),
      modifier = Modifier.fillMaxWidth()
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = VendorSpacing.md, vertical = VendorSpacing.sm)
      ) {
        PrimaryButton(
          text = if (uiState.isSaving) appText("Saving...", "محفوظ ہو رہا ہے...") else appText("Save shop settings", "دکان کی ترتیبات محفوظ کریں"),
          icon = Icons.Rounded.Save,
          loading = uiState.isSaving,
          enabled = !uiState.isSaving,
          onClick = onSaveClick,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("vendor_shop_settings_save_button")
        )
      }
    }
  }
}

@Composable
private fun VerificationDocRow(
  title: String,
  uploaded: Boolean,
  buttonLabel: String,
  enabled: Boolean,
  onClick: () -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.xs)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = VendorColors.TextPrimary,
        modifier = Modifier.weight(1f)
      )
      StatusBadge(
        text = if (uploaded) appText("Saved", "محفوظ") else appText("Not uploaded", "اپلوڈ نہیں ہوئی"),
        tone = if (uploaded) StatusTone.SUCCESS else StatusTone.WARNING,
        icon = if (uploaded) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber
      )
    }
    SecondaryButton(
      text = buttonLabel,
      icon = Icons.Rounded.UploadFile,
      enabled = enabled,
      onClick = onClick,
      modifier = Modifier.fillMaxWidth()
    )
  }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun VendorShopSettingsContentPreview() {
  VendorCenterTheme {
    VendorShopSettingsContent(
      uiState = VendorShopSettingsUiState(
        shopName = "Multan Craft Heritage",
        slug = "multan-craft-heritage",
        accountEmail = "seller@jtbazar.pk",
        vendorType = "registered_business",
        ownerFullName = "Muhammad Usman",
        cnicNumber = "3520212345678",
        phone = "03001234567",
        description = "Authentic hand-glazed Multani blue pottery and artisan kitchenware.",
        currentAddress = "Gulberg III, Lahore",
        completeAddress = "Shop 14, Main Boulevard, Gulberg III, Lahore",
        locationLabel = "Gulberg III",
        locationText = "Near Liberty Market Roundabout",
        latitude = "31.511200",
        longitude = "74.344100",
        frontPath = "saved",
        backPath = "saved",
        documentPath = ""
      ),
      previewImageModel = null,
      onShopNameChange = {},
      onSlugChange = {},
      onVendorTypeChange = {},
      onOwnerNameChange = {},
      onCnicChange = {},
      onPhoneChange = {},
      onDescriptionChange = {},
      onCurrentAddressChange = {},
      onCompleteAddressChange = {},
      onLocationLabelChange = {},
      onShowOnMapChange = {},
      onOpenMapPicker = {},
      onLocationTextChange = {},
      onPickShopImage = {},
      onUploadCnicFront = {},
      onUploadCnicBack = {},
      onUploadVerificationDoc = {},
      onSaveClick = {},
      onDismissAlert = {}
    )
  }
}
