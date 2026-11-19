package com.example.ui.components

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.example.location.AddressReverseGeocoder
import com.example.location.LocationFetchResult
import com.example.location.LocationResolver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.DeliveryAddress
import com.example.data.repository.MarketplaceRepository
import com.example.localization.AppLanguageManager
import com.example.localization.appText
import com.example.ui.theme.*

@Composable
fun JTBazarBrandLogo(modifier: Modifier = Modifier) {
  Image(
    painter = painterResource(id = R.drawable.logo_jtbazar_header),
    contentDescription = appText("JTBazar Logo", "جے ٹی بازار لوگو"),
    contentScale = ContentScale.Fit,
    modifier = modifier
      .height(30.dp)
      .widthIn(max = 135.dp)
  )
}

@Composable
fun JTBazarTopBar(
  title: String? = null,
  showBack: Boolean = false,
  onBackClick: () -> Unit = {},
  onCartClick: () -> Unit = {},
  onAvatarClick: () -> Unit = {},
  onLocationClick: () -> Unit = {},
  cartCount: Int = 2,
  currentLocation: String = appText("Select delivery address", "ترسیل کا پتہ منتخب کریں"),
  showLocationText: Boolean = true,
  modifier: Modifier = Modifier
) {
  Surface(
    color = Color.White,
    tonalElevation = 0.dp,
    shadowElevation = 0.5.dp,
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f, fill = false)
      ) {
        if (showBack) {
          IconButton(
            onClick = onBackClick,
            modifier = Modifier
              .size(38.dp)
              .testTag("back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = appText("Back", "واپس"),
              tint = JTTextPrimary
            )
          }
          Spacer(modifier = Modifier.width(4.dp))
        }

        JTBazarBrandLogo()

      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (title == null) {
          Surface(
            shape = if (showLocationText) RoundedCornerShape(16.dp) else CircleShape,
            color = JTPurpleContainer.copy(alpha = 0.5f),
            modifier = Modifier
              .then(
                if (showLocationText) Modifier.heightIn(min = 48.dp)
                else Modifier.size(48.dp)
              )
              .clickable { onLocationClick() }
              .testTag("location_picker")
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
              modifier = Modifier.padding(horizontal = if (showLocationText) 8.dp else 0.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = if (showLocationText) currentLocation else appText("Select delivery location", "ترسیل کا مقام منتخب کریں"),
                tint = JTPurplePrimary,
                modifier = Modifier.size(19.dp)
              )
              if (showLocationText) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = currentLocation,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = JTTextPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Icon(
                  imageVector = Icons.Filled.KeyboardArrowDown,
                  contentDescription = null,
                  tint = JTTextSecondary,
                  modifier = Modifier.size(14.dp)
                )
              }
            }
          }
        }

        // Cart Icon with Badge
        Box(
          modifier = Modifier
            .clickable(onClick = onCartClick)
            .testTag("cart_icon_badge")
            .padding(4.dp)
        ) {
          Icon(
            imageVector = Icons.Outlined.ShoppingBag,
            contentDescription = appText("Shopping Cart", "شاپنگ کارٹ"),
            tint = JTTextPrimary,
            modifier = Modifier.size(24.dp)
          )
          if (cartCount > 0) {
            Box(
              modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-4).dp)
                .size(17.dp)
                .clip(CircleShape)
                .background(JTPurplePrimary),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "$cartCount",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }

        // User Avatar
        val currentProfile by com.example.data.supabase.SupabaseAuthService.userProfile.collectAsState()
        UserAvatarView(
          avatarUrl = currentProfile?.avatarUrl,
          fullName = currentProfile?.fullName,
          size = 32.dp,
          onClick = onAvatarClick,
          modifier = Modifier.testTag("user_avatar_button")
        )
      }
    }
  }
}

@Composable
fun UserAvatarView(
  avatarUrl: String? = null,
  fullName: String? = null,
  size: androidx.compose.ui.unit.Dp = 32.dp,
  onClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
  Box(
    modifier = modifier
      .size(size)
      .clip(CircleShape)
      .border(1.5.dp, JTPurpleContainerBorder, CircleShape)
      .then(clickModifier),
    contentAlignment = Alignment.Center
  ) {
    val validAvatarUrl = avatarUrl?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    if (validAvatarUrl != null) {
      coil.compose.AsyncImage(
        model = rememberSizedImageRequest(data = validAvatarUrl, widthPx = 128, heightPx = 128),
        contentDescription = "User Avatar",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
    } else if (!fullName.isNullOrBlank()) {
      val initial = fullName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "J"
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(JTPurplePrimary),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = initial,
          color = Color.White,
          fontWeight = FontWeight.Bold,
          fontSize = (size.value * 0.42f).sp
        )
      }
    } else {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(JTSurfaceVariant),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Filled.AccountCircle,
          contentDescription = "Account",
          tint = JTPurplePrimary,
          modifier = Modifier.size((size.value * 0.7f).dp)
        )
      }
    }
  }
}

@Composable
fun QuantityStepper(
  quantity: Int,
  onIncrement: () -> Unit,
  onDecrement: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .background(JTSurfaceVariant, RoundedCornerShape(8.dp))
      .padding(horizontal = 4.dp, vertical = 2.dp)
  ) {
    IconButton(
      onClick = onDecrement,
      modifier = Modifier
        .size(32.dp)
        .testTag("decrement_qty")
    ) {
      Icon(
        imageVector = Icons.Filled.Remove,
        contentDescription = "Decrease",
        modifier = Modifier.size(16.dp),
        tint = JTTextPrimary
      )
    }

    Text(
      text = "$quantity",
      fontWeight = FontWeight.Bold,
      fontSize = 14.sp,
      color = JTTextPrimary,
      modifier = Modifier.padding(horizontal = 10.dp)
    )

    IconButton(
      onClick = onIncrement,
      modifier = Modifier
        .size(32.dp)
        .testTag("increment_qty")
    ) {
      Icon(
        imageVector = Icons.Filled.Add,
        contentDescription = "Increase",
        modifier = Modifier.size(16.dp),
        tint = JTTextPrimary
      )
    }
  }
}

@Composable
fun TrustBadgesRow(modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 12.dp, horizontal = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    TrustItem(icon = Icons.Filled.Security, title = appText("100% Genuine", "100% اصل اشیاء"), modifier = Modifier.weight(1f))
    TrustItem(icon = Icons.Filled.Payment, title = appText("Cash On Delivery", "کیش آن ڈیلیوری"), modifier = Modifier.weight(1f))
    TrustItem(icon = Icons.Filled.HeadsetMic, title = appText("24/7 Helpline", "24/7 ہیلپ لائن"), modifier = Modifier.weight(1f))
  }
}

@Composable
private fun TrustItem(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = JTPurpleContainer.copy(alpha = 0.45f),
    modifier = modifier.heightIn(min = 36.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 3.dp, vertical = 7.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = JTPurplePrimary,
        modifier = Modifier.size(13.dp)
      )
      Spacer(modifier = Modifier.width(3.dp))
      Text(
        text = title,
        fontSize = 8.5.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = JTTextPrimary,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
fun AddressSelectionSheet(
  addresses: List<DeliveryAddress>,
  selectedAddress: DeliveryAddress?,
  onSelect: (DeliveryAddress) -> Unit,
  onDismiss: () -> Unit,
  onAddNewAddress: ((DeliveryAddress) -> Unit)? = null
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val currentProfile by com.example.data.supabase.SupabaseAuthService.userProfile.collectAsState()
  var showNewAddressForm by remember { mutableStateOf(addresses.isEmpty()) }
  var recipientName by remember(currentProfile) {
    mutableStateOf(currentProfile?.fullName ?: "")
  }
  var phone by remember(currentProfile) {
    mutableStateOf(currentProfile?.phone ?: "")
  }
  var addressLine by remember { mutableStateOf("") }
  var area by remember { mutableStateOf("") }
  var city by remember { mutableStateOf("") }
  var label by remember { mutableStateOf("HOME") }
  var customLabel by remember { mutableStateOf("") }
  var latitude by remember { mutableStateOf<Double?>(null) }
  var longitude by remember { mutableStateOf<Double?>(null) }
  var locating by remember { mutableStateOf(false) }
  var locationError by remember { mutableStateOf<String?>(null) }
  var locationStatus by remember { mutableStateOf<String?>(null) }

  suspend fun applyDeviceLocation(location: android.location.Location) {
    latitude = location.latitude
    longitude = location.longitude
    val resolved = AddressReverseGeocoder.resolve(context, location.latitude, location.longitude)
    if (resolved != null) {
      if (resolved.street.isNotBlank()) addressLine = resolved.street
      if (resolved.area.isNotBlank()) area = resolved.area
      if (resolved.city.isNotBlank()) city = resolved.city
      locationError = null
      locationStatus = if (resolved.street.isBlank()) {
        AppLanguageManager.tr(
          "Location detected. Please verify or enter your house/street details.",
          "مقام کی نشاندہی ہو گئی۔ براہ کرم اپنا گھر یا گلی کا پتہ چیک یا درج کریں۔"
        )
      } else {
        AppLanguageManager.tr(
          "Current location detected. Verify details and save.",
          "موجودہ مقام مل گیا۔ تفصیلات کی تصدیق کر کے محفوظ کریں۔"
        )
      }
    } else {
      locationError = null
      locationStatus = AppLanguageManager.tr(
        "GPS coordinates captured. Please enter your street, area and city.",
        "جی پی ایس مقام محفوظ ہو گیا۔ براہ کرم اپنی گلی، علاقہ اور شہر درج کریں۔"
      )
    }
  }

  lateinit var startLocationFetch: () -> Unit

  val locationSettingsLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      startLocationFetch()
    } else {
      locating = false
      locationError = AppLanguageManager.tr(
        "Turn on device location / GPS to detect your current address.",
        "اپنا موجودہ پتہ معلوم کرنے کے لیے ڈیوائس کی لوکیشن / جی پی ایس آن کریں۔"
      )
    }
  }

  startLocationFetch = {
    locating = true
    locationError = null
    locationStatus = null
    scope.launch {
      try {
        when (val result = LocationResolver.fetchCurrentLocation(context)) {
          is LocationFetchResult.Success -> {
            applyDeviceLocation(result.location)
            locating = false
          }
          is LocationFetchResult.NeedsResolution -> {
            runCatching {
              val request = IntentSenderRequest.Builder(result.exception.resolution).build()
              locationSettingsLauncher.launch(request)
            }.onFailure {
              locating = false
              locationError = AppLanguageManager.tr(
                "Turn on device location / GPS and try again.",
                "ڈیوائس کی لوکیشن / جی پی ایس آن کریں اور دوبارہ کوشش کریں۔"
              )
            }
          }
          is LocationFetchResult.Failure -> {
            locating = false
            locationError = result.message
          }
        }
      } catch (_: Exception) {
        locating = false
        locationError = AppLanguageManager.tr(
          "Unable to detect current location. Please try again.",
          "موجودہ مقام معلوم نہیں ہو سکا۔ براہ کرم دوبارہ کوشش کریں۔"
        )
      }
    }
  }

  val locationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    if (!granted) {
      locating = false
      locationError = AppLanguageManager.tr(
        "Location permission was not granted.",
        "مقام کی اجازت نہیں دی گئی۔"
      )
    } else {
      startLocationFetch()
    }
  }

  fun requestCurrentLocation() {
    if (LocationResolver.hasLocationPermission(context)) {
      startLocationFetch()
    } else {
      locationPermissionLauncher.launch(
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
      )
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      if (showNewAddressForm) {
        Button(
          enabled = recipientName.isNotBlank() && phone.isNotBlank() && addressLine.isNotBlank() && city.isNotBlank() && !locating,
          onClick = {
            val cleanName = recipientName.trim()
            val cleanPhone = phone.trim()
            val cleanLine = addressLine.trim()
            val cleanArea = area.trim()
            val cleanCity = city.trim()
            val err = when {
              cleanName.isEmpty() -> AppLanguageManager.tr("Please enter recipient full name.", "براہ کرم وصول کنندہ کا مکمل نام درج کریں۔")
              !com.example.data.supabase.SupabaseApiService.isValidPakistaniMobile(cleanPhone) ->
                AppLanguageManager.tr(
                  "Enter a valid Pakistani mobile number like 03001234567 or +923001234567.",
                  "درست پاکستانی موبائل نمبر درج کریں جیسے 03001234567 یا +923001234567۔"
                )
              cleanLine.length < 3 -> AppLanguageManager.tr("Please enter a complete street address (at least 3 characters).", "براہ کرم مکمل پتہ درج کریں (کم از کم 3 حروف)۔")
              cleanCity.isEmpty() -> AppLanguageManager.tr("Please enter your city.", "براہ کرم اپنا شہر درج کریں۔")
              else -> null
            }
            if (err != null) {
              locationError = err
              return@Button
            }
            locationError = null
            val savedLabel = if (label == "OTHER") customLabel.trim().ifBlank { "OTHER" } else label
            val newAddr = DeliveryAddress(
              id = "addr_${System.currentTimeMillis()}",
              name = cleanName,
              phone = cleanPhone,
              addressLine = cleanLine,
              area = cleanArea,
              city = cleanCity,
              province = "",
              tag = savedLabel,
              isDefault = addresses.isEmpty(),
              latitude = latitude,
              longitude = longitude
            )
            onAddNewAddress?.invoke(newAddr)
            onSelect(newAddr)
            onDismiss()
          },
          colors = ButtonDefaults.buttonColors(containerColor = JTPurplePrimary)
        ) { Text(appText("Save Location", "مقام محفوظ کریں"), fontWeight = FontWeight.Bold) }
      } else {
        TextButton(onClick = onDismiss) { Text(appText("Done", "مکمل"), color = JTPurplePrimary, fontWeight = FontWeight.Bold) }
      }
    },
    dismissButton = {
      if (showNewAddressForm && addresses.isNotEmpty()) {
        TextButton(onClick = { showNewAddressForm = false }) { Text(appText("Cancel", "منسوخ کریں"), color = JTTextSecondary) }
      }
    },
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = JTPurplePrimary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          if (showNewAddressForm) appText("Add Delivery Location", "ترسیل کا نیا مقام شامل کریں")
          else appText("Select Saved Location", "محفوظ شدہ مقام منتخب کریں"),
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp
        )
      }
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
      ) {
        if (showNewAddressForm) {
          OutlinedButton(
            enabled = !locating,
            onClick = { requestCurrentLocation() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("use_current_location_btn")
          ) {
            if (locating) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
              Icon(Icons.Filled.MyLocation, contentDescription = null)
            }
            Spacer(Modifier.width(6.dp))
            Text(
              if (locating) appText("Getting current location...", "موجودہ مقام حاصل کیا جا رہا ہے...")
              else appText("Use Current Location", "موجودہ مقام استعمال کریں")
            )
          }
          if (latitude != null && longitude != null) {
            Text("GPS: ${String.format(Locale.US, "%.6f", latitude)} , ${String.format(Locale.US, "%.6f", longitude)}", fontSize = 11.sp, color = JTTextSecondary)
          }
          locationStatus?.let { Text(it, color = JTPurplePrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
          locationError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
              "HOME" to appText("HOME", "گھر"),
              "OFFICE" to appText("OFFICE", "دفتر"),
              "OTHER" to appText("OTHER", "دیگر")
            ).forEach { (value, display) ->
              FilterChip(selected = label == value, onClick = { label = value }, label = { Text(display, fontSize = 11.sp) })
            }
          }
          if (label == "OTHER") {
            OutlinedTextField(
              value = customLabel,
              onValueChange = { customLabel = it },
              label = { Text(appText("Your location name", "مقام کا نام")) },
              placeholder = { Text(appText("e.g. Shop, Warehouse", "مثلاً دکان، گودام")) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
          }
          OutlinedTextField(value = recipientName, onValueChange = { recipientName = it }, label = { Text(appText("Recipient Full Name", "وصول کنندہ کا مکمل نام")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
          OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(appText("Mobile Phone Number", "موبائل فون نمبر")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
          OutlinedTextField(value = addressLine, onValueChange = { addressLine = it }, label = { Text(appText("House / Street / Building", "مکان / گلی / عمارت")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = area, onValueChange = { area = it }, label = { Text(appText("Area / Colony", "علاقہ / کالونی")) }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text(appText("City", "شہر")) }, modifier = Modifier.weight(1f))
          }
        } else {
          if (addresses.isEmpty()) {
            Text(
              appText("No saved locations yet. Add a delivery location to continue.", "ابھی کوئی محفوظ مقام نہیں۔ جاری رکھنے کے لیے ترسیل کا مقام شامل کریں۔"),
              fontSize = 12.sp,
              color = JTTextSecondary
            )
          } else {
            addresses.forEach { addr ->
              val isSelected = addr.id == selectedAddress?.id
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) JTPurpleContainer.copy(alpha = 0.6f) else JTBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) JTPurplePrimary else JTBorder),
                modifier = Modifier.fillMaxWidth().clickable { onSelect(addr) }
              ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                  Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(addr.tag, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                      Spacer(Modifier.width(6.dp))
                      if (addr.isDefault) Text(appText("DEFAULT", "بنیادی"), color = JTPurplePrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                      text = listOf(addr.area, addr.city).filter { it.isNotBlank() }.joinToString(", "),
                      fontSize = 12.sp,
                      color = JTTextSecondary
                    )
                    Text(addr.addressLine, fontSize = 12.sp, color = JTTextSecondary)
                    if (addr.latitude != null && addr.longitude != null) Text(appText("GPS location saved", "جی پی ایس مقام محفوظ ہے"), fontSize = 10.sp, color = JTPurplePrimary)
                  }
                  if (isSelected) Icon(Icons.Filled.Check, contentDescription = appText("Selected", "منتخب"), tint = JTPurplePrimary)
                }
              }
            }
          }
          OutlinedButton(
            onClick = {
              showNewAddressForm = true
              requestCurrentLocation()
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("quick_use_current_location_btn"),
            shape = RoundedCornerShape(8.dp)
          ) {
            Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(appText("Use Current Location", "موجودہ مقام استعمال کریں"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
          OutlinedButton(
            onClick = { showNewAddressForm = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(8.dp)
          ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(appText("Add New Location", "نیا مقام شامل کریں"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  )
}
