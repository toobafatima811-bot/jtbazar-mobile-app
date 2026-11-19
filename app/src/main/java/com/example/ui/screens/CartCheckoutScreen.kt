package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.CartItem
import com.example.data.model.DeliveryAddress
import com.example.data.model.OrderRecord
import com.example.data.repository.MarketplaceRepository
import com.example.data.supabase.AppErrorMapper
import com.example.data.supabase.ErrorContext
import com.example.data.supabase.SupabaseAuthService
import com.example.data.supabase.SupabaseClient
import com.example.ui.components.JTBazarTopBar
import com.example.ui.components.QuantityStepper
import com.example.ui.components.rememberSizedImageRequest
import com.example.ui.theme.*
import com.example.location.AddressReverseGeocoder
import com.example.location.LocationFetchResult
import com.example.location.LocationResolver
import com.example.localization.AppLanguageManager
import com.example.localization.appText
import kotlinx.coroutines.launch

enum class CartCheckoutStage {
  CART,
  CHECKOUT,
  CONFIRMATION
}

@Composable
fun CartCheckoutScreen(
  onBackClick: () -> Unit,
  onOrderPlaced: () -> Unit,
  onAccountClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val cartItems by MarketplaceRepository.cartItems.collectAsState()
  val cartCount by MarketplaceRepository.cartCount.collectAsState()
  val currentLocation by MarketplaceRepository.currentLocation.collectAsState()
  val addresses by MarketplaceRepository.addresses.collectAsState()
  val selectedAddress by MarketplaceRepository.selectedAddress.collectAsState()
  val configuredDeliveryFee by MarketplaceRepository.deliveryFee.collectAsState()
  val freeDeliveryEnabled by MarketplaceRepository.freeDeliveryEnabled.collectAsState()
  val homepagePromotions by MarketplaceRepository.homepagePromotions.collectAsState()
  val codEnabled by MarketplaceRepository.codEnabled.collectAsState()
  val isPlacingOrder by MarketplaceRepository.isPlacingOrder.collectAsState()
  val currentUser by SupabaseClient.currentUser.collectAsState()
  val userProfile by SupabaseAuthService.userProfile.collectAsState()

  var couponCodeInput by remember { mutableStateOf("") }
  var appliedCouponCode by remember { mutableStateOf<String?>(null) }
  var appliedDiscountPercent by remember { mutableStateOf(0.0) }
  var couponMessage by remember { mutableStateOf<String?>(null) }
  var couponIsError by remember { mutableStateOf(false) }

  var stage by remember { mutableStateOf(CartCheckoutStage.CART) }
  var confirmedOrder by remember { mutableStateOf<OrderRecord?>(null) }
  var orderErrorMessage by remember { mutableStateOf<String?>(null) }

  // Checkout Form States
  val initialAddr = selectedAddress ?: addresses.firstOrNull { it.isDefault } ?: addresses.firstOrNull()
  var selectedSavedAddressId by remember { mutableStateOf(initialAddr?.id) }
  var hasUserEditedAddress by remember { mutableStateOf(false) }

  var recipientName by remember {
    mutableStateOf(initialAddr?.name ?: userProfile?.fullName ?: currentUser?.fullName ?: "")
  }
  var recipientPhone by remember {
    mutableStateOf(initialAddr?.phone ?: userProfile?.phone ?: currentUser?.phone ?: "")
  }
  var city by remember {
    mutableStateOf(initialAddr?.city ?: userProfile?.city ?: "")
  }
  var streetAddress by remember {
    mutableStateOf(initialAddr?.addressLine ?: "")
  }
  var areaLocality by remember {
    mutableStateOf(initialAddr?.area ?: "")
  }
  var customerEmail by remember(currentUser, userProfile) {
    mutableStateOf(currentUser?.email ?: userProfile?.email ?: "")
  }
  var deliveryInstructions by remember { mutableStateOf("") }
  var saveAddressToAccount by remember { mutableStateOf(true) }

  // Location Pin State
  var deliveryLat by remember { mutableStateOf(initialAddr?.latitude) }
  var deliveryLng by remember { mutableStateOf(initialAddr?.longitude) }
  var locationCapturedAt by remember { mutableStateOf<String?>(null) }
  var isPinConfirmed by remember { mutableStateOf(initialAddr?.latitude != null && initialAddr?.longitude != null) }

  // Populate defaults once saved addresses finish loading if user hasn't typed yet
  LaunchedEffect(addresses, selectedAddress, userProfile) {
    if (!hasUserEditedAddress && streetAddress.isBlank()) {
      val fallback = selectedAddress ?: addresses.firstOrNull { it.isDefault } ?: addresses.firstOrNull()
      if (fallback != null) {
        selectedSavedAddressId = fallback.id
        recipientName = fallback.name.ifBlank { recipientName }
        recipientPhone = fallback.phone.ifBlank { recipientPhone }
        city = fallback.city.ifBlank { city }
        streetAddress = fallback.addressLine
        areaLocality = fallback.area
        deliveryLat = fallback.latitude
        deliveryLng = fallback.longitude
        isPinConfirmed = fallback.latitude != null && fallback.longitude != null
      } else if (recipientName.isBlank() || recipientPhone.isBlank() || city.isBlank()) {
        if (recipientName.isBlank()) recipientName = userProfile?.fullName ?: currentUser?.fullName ?: ""
        if (recipientPhone.isBlank()) recipientPhone = userProfile?.phone ?: currentUser?.phone ?: ""
        if (city.isBlank()) city = userProfile?.city ?: ""
      }
    }
  }

  var isLocatingPin by remember { mutableStateOf(false) }

  suspend fun applyCheckoutLocation(loc: android.location.Location) {
    deliveryLat = loc.latitude
    deliveryLng = loc.longitude
    locationCapturedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
      timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.format(java.util.Date())
    isPinConfirmed = true
    val resolved = AddressReverseGeocoder.resolve(context, loc.latitude, loc.longitude)
    if (resolved != null) {
      if (resolved.street.isNotBlank()) streetAddress = resolved.street
      if (resolved.area.isNotBlank()) areaLocality = resolved.area
      if (resolved.city.isNotBlank()) city = resolved.city
      Toast.makeText(
        context,
        if (resolved.street.isNotBlank()) AppLanguageManager.tr("Location and street detected. Please verify your house number.", "مقام اور گلی کا پتہ مل گیا۔ براہ کرم اپنا گھر نمبر تصدیق کریں۔")
        else AppLanguageManager.tr("Location captured. Please enter your street and house number.", "مقام محفوظ ہو گیا۔ براہ کرم اپنی گلی اور گھر نمبر درج کریں۔"),
        Toast.LENGTH_LONG
      ).show()
    } else {
      Toast.makeText(
        context,
        AppLanguageManager.tr("Location captured. Please enter your street and house number.", "مقام محفوظ ہو گیا۔ براہ کرم اپنی گلی اور گھر نمبر درج کریں۔"),
        Toast.LENGTH_LONG
      ).show()
    }
  }

  lateinit var startCheckoutLocationFetch: () -> Unit

  val locationSettingsLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      startCheckoutLocationFetch()
    } else {
      isLocatingPin = false
      Toast.makeText(
        context,
        AppLanguageManager.tr("Turn on device location / GPS to confirm your delivery pin.", "ڈیلیوری پن کی تصدیق کے لیے ڈیوائس لوکیشن / GPS آن کریں۔"),
        Toast.LENGTH_LONG
      ).show()
    }
  }

  startCheckoutLocationFetch = {
    isLocatingPin = true
    coroutineScope.launch {
      try {
        when (val result = LocationResolver.fetchCurrentLocation(context)) {
          is LocationFetchResult.Success -> {
            applyCheckoutLocation(result.location)
            isLocatingPin = false
          }
          is LocationFetchResult.NeedsResolution -> {
            runCatching {
              locationSettingsLauncher.launch(IntentSenderRequest.Builder(result.exception.resolution).build())
            }.onFailure {
              isLocatingPin = false
              Toast.makeText(
                context,
                AppLanguageManager.tr("Turn on device location / GPS and try again.", "ڈیوائس لوکیشن / GPS آن کر کے دوبارہ کوشش کریں۔"),
                Toast.LENGTH_LONG
              ).show()
            }
          }
          is LocationFetchResult.Failure -> {
            isLocatingPin = false
            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
          }
        }
      } catch (_: Exception) {
        isLocatingPin = false
        Toast.makeText(
          context,
          AppLanguageManager.tr("Unable to get your current location. Please try again.", "آپ کا موجودہ مقام حاصل نہیں ہو سکا۔ براہ کرم دوبارہ کوشش کریں۔"),
          Toast.LENGTH_LONG
        ).show()
      }
    }
  }

  val locationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { perms ->
    val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true || perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    if (granted) {
      startCheckoutLocationFetch()
    } else {
      Toast.makeText(
        context,
        AppLanguageManager.tr("Location permission is required to confirm your delivery pin.", "ڈیلیوری پن کی تصدیق کے لیے لوکیشن کی اجازت درکار ہے۔"),
        Toast.LENGTH_SHORT
      ).show()
    }
  }

  fun requestCheckoutLocationPin() {
    if (isLocatingPin) return
    if (LocationResolver.hasLocationPermission(context)) {
      startCheckoutLocationFetch()
    } else {
      locationPermissionLauncher.launch(
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
      )
    }
  }

  // Server-aligned Calculations with Free Delivery and Coupon Discounts
  val subtotal = cartItems.sumOf { it.totalPrice }
  val deliveryCharges = if (subtotal == 0 || freeDeliveryEnabled) 0 else configuredDeliveryFee
  val discountAmount = if (appliedDiscountPercent > 0) (subtotal * (appliedDiscountPercent / 100.0)).toInt() else 0
  val grandTotal = (subtotal - discountAmount + deliveryCharges).coerceAtLeast(0)

  fun applyCoupon() {
    val clean = couponCodeInput.trim()
    if (clean.isBlank()) {
      couponMessage = AppLanguageManager.tr("Please enter a coupon code.", "براہ کرم کوپن کوڈ درج کریں۔")
      couponIsError = true
      return
    }
    val promo = homepagePromotions.firstOrNull {
      !it.couponCode.isNullOrBlank() && it.couponCode.equals(clean, ignoreCase = true)
    }
    if (promo != null && (promo.discountPercent ?: 0.0) > 0.0) {
      appliedCouponCode = promo.couponCode
      appliedDiscountPercent = promo.discountPercent ?: 0.0
      val pct = (promo.discountPercent ?: 0.0).toInt()
      couponMessage = AppLanguageManager.tr("Coupon applied! $pct% off discount.", "کوپن لاگو ہو گیا! $pct% رعایت۔")
      couponIsError = false
    } else {
      couponMessage = AppLanguageManager.tr("Invalid or expired coupon code.", "غلط یا میعاد ختم کوپن کوڈ۔")
      couponIsError = true
    }
  }

  Scaffold(
    containerColor = Color.White,
    topBar = {
      JTBazarTopBar(
        title = when (stage) {
          CartCheckoutStage.CART -> appText("Shopping Cart", "خریداری کارٹ")
          CartCheckoutStage.CHECKOUT -> appText("Checkout & Delivery", "چیک آؤٹ اور ڈیلیوری")
          CartCheckoutStage.CONFIRMATION -> appText("Order Confirmation", "آرڈر کی تصدیق")
        },
        showBack = stage != CartCheckoutStage.CONFIRMATION,
        onBackClick = {
          if (stage == CartCheckoutStage.CHECKOUT) {
            stage = CartCheckoutStage.CART
          } else {
            onBackClick()
          }
        },
        onCartClick = { stage = CartCheckoutStage.CART },
        onAvatarClick = onAccountClick,
        cartCount = cartCount,
        currentLocation = currentLocation
      )
    },
    bottomBar = {
      if (stage == CartCheckoutStage.CART && cartItems.isNotEmpty()) {
        Surface(
          color = Color.White,
          shadowElevation = 8.dp,
          border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
          modifier = Modifier.fillMaxWidth().navigationBarsPadding()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column {
              Text(appText("TOTAL (INCL. DELIVERY)", "کل رقم (ڈیلیوری سمیت)"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = JTTextSecondary)
              Text(
                text = "PKR ${String.format("%,d", grandTotal)}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = JTPurplePrimary
              )
            }
            Button(
              onClick = { stage = CartCheckoutStage.CHECKOUT },
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White),
              modifier = Modifier.height(44.dp).testTag("cart_proceed_checkout_btn")
            ) {
              Text(appText("Proceed to Checkout", "چیک آؤٹ پر جائیں"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
              Spacer(modifier = Modifier.width(4.dp))
              Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
            }
          }
        }
      }
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      when (stage) {
        CartCheckoutStage.CART -> {
          CartView(
            items = cartItems,
            subtotal = subtotal,
            discountAmount = discountAmount,
            deliveryFee = deliveryCharges,
            grandTotal = grandTotal,
            couponCodeInput = couponCodeInput,
            onCouponCodeChange = { couponCodeInput = it },
            onApplyCoupon = { applyCoupon() },
            couponMessage = couponMessage,
            couponIsError = couponIsError,
            onClearCart = { MarketplaceRepository.clearCart() },
            onContinueShopping = onBackClick,
            onProceedToCheckout = { stage = CartCheckoutStage.CHECKOUT }
          )
        }

        CartCheckoutStage.CHECKOUT -> {
          CheckoutView(
            items = cartItems,
            subtotal = subtotal,
            discountAmount = discountAmount,
            deliveryFee = deliveryCharges,
            grandTotal = grandTotal,
            couponCodeInput = couponCodeInput,
            onCouponCodeChange = { couponCodeInput = it },
            onApplyCoupon = { applyCoupon() },
            couponMessage = couponMessage,
            couponIsError = couponIsError,
            recipientName = recipientName,
            onRecipientNameChange = { hasUserEditedAddress = true; recipientName = it },
            recipientPhone = recipientPhone,
            onRecipientPhoneChange = { hasUserEditedAddress = true; recipientPhone = it },
            city = city,
            onCityChange = { hasUserEditedAddress = true; city = it },
            streetAddress = streetAddress,
            onStreetAddressChange = { hasUserEditedAddress = true; streetAddress = it },
            areaLocality = areaLocality,
            onAreaLocalityChange = { hasUserEditedAddress = true; areaLocality = it },
            customerEmail = customerEmail,
            onCustomerEmailChange = { customerEmail = it },
            deliveryInstructions = deliveryInstructions,
            onDeliveryInstructionsChange = { deliveryInstructions = it },
            saveAddressToAccount = saveAddressToAccount,
            onSaveAddressToAccountChange = { saveAddressToAccount = it },
            savedAddresses = addresses,
            selectedSavedAddressId = selectedSavedAddressId,
            onSelectSavedAddress = { addr ->
              hasUserEditedAddress = false
              selectedSavedAddressId = addr.id
              recipientName = addr.name
              recipientPhone = addr.phone
              city = addr.city
              streetAddress = addr.addressLine
              areaLocality = addr.area
              deliveryLat = addr.latitude
              deliveryLng = addr.longitude
              isPinConfirmed = addr.latitude != null && addr.longitude != null
              MarketplaceRepository.selectAddress(addr)
            },
            onUseNewAddress = {
              hasUserEditedAddress = true
              selectedSavedAddressId = null
              streetAddress = ""
              areaLocality = ""
              deliveryLat = null
              deliveryLng = null
              isPinConfirmed = false
              saveAddressToAccount = true
            },
            deliveryLat = deliveryLat,
            deliveryLng = deliveryLng,
            isPinConfirmed = isPinConfirmed,
            onRequestPin = { requestCheckoutLocationPin() },
            onChangePin = {
              isPinConfirmed = false
              deliveryLat = null
              deliveryLng = null
            },
            isPlacingOrder = isPlacingOrder,
            orderErrorMessage = orderErrorMessage,
            onPlaceOrder = {
              orderErrorMessage = null
              if (recipientName.isBlank() || recipientPhone.isBlank() || city.isBlank() || streetAddress.isBlank()) {
                Toast.makeText(context, AppLanguageManager.tr("Please complete all required delivery fields.", "براہ کرم تمام ضروری ڈیلیوری خانے مکمل کریں۔"), Toast.LENGTH_SHORT).show()
                return@CheckoutView
              }
              if (streetAddress.trim().length < 3) {
                Toast.makeText(context, AppLanguageManager.tr("Please enter a complete street address (at least 3 characters).", "براہ کرم مکمل گلی کا پتہ درج کریں (کم از کم 3 حروف)۔"), Toast.LENGTH_LONG).show()
                return@CheckoutView
              }
              if (!com.example.data.supabase.SupabaseApiService.isValidPakistaniMobile(recipientPhone)) {
                Toast.makeText(context, AppLanguageManager.tr("Enter a valid Pakistani mobile number like 03001234567 or +923001234567.", "درست پاکستانی موبائل نمبر درج کریں جیسے 03001234567 یا +923001234567۔"), Toast.LENGTH_LONG).show()
                return@CheckoutView
              }
              if (customerEmail.isNotBlank() && (customerEmail.trim().length > 254 || !android.util.Patterns.EMAIL_ADDRESS.matcher(customerEmail.trim()).matches())) {
                Toast.makeText(context, AppLanguageManager.tr("Enter a valid email address.", "درست ای میل پتہ درج کریں۔"), Toast.LENGTH_LONG).show()
                return@CheckoutView
              }
              if (!codEnabled) {
                Toast.makeText(context, AppLanguageManager.tr("Cash on delivery is currently unavailable.", "کیش آن ڈیلیوری فی الحال دستیاب نہیں ہے۔"), Toast.LENGTH_LONG).show()
                return@CheckoutView
              }
              val unavailableCartItem = cartItems.firstOrNull { item ->
                (!item.product.availabilityKnown || !item.product.isAvailable) ||
                  (item.product.foodSellingUnit != null && item.product.dailyStatus != null && item.product.dailyStatus != "available") ||
                  (item.product.variantIds.isNotEmpty() && item.variantId.isNullOrBlank()) ||
                  (!item.variantId.isNullOrBlank() && item.product.variantAvailability[item.variantId] != true)
              }
              if (unavailableCartItem != null) {
                Toast.makeText(
                  context,
                  AppLanguageManager.tr(
                    "${unavailableCartItem.product.title} stock or selected option could not be verified. Refresh the catalog or remove it from your cart.",
                    "${unavailableCartItem.product.title} کا اسٹاک یا منتخب کردہ آپشن تصدیق نہیں ہو سکا۔ کیٹلاگ ریفریش کریں یا اسے کارٹ سے ہٹائیں۔"
                  ),
                  Toast.LENGTH_LONG
                ).show()
                return@CheckoutView
              }
              if (deliveryLat == null || deliveryLng == null || !isPinConfirmed) {
                Toast.makeText(context, AppLanguageManager.tr("Add your current location pin to confirm the 5 km food delivery area.", "5 کلومیٹر فوڈ ڈیلیوری ایریا کی تصدیق کے لیے اپنا موجودہ لوکیشن پن شامل کریں۔"), Toast.LENGTH_LONG).show()
                return@CheckoutView
              }

              val matchedSaved = addresses.firstOrNull { it.id == selectedSavedAddressId }
              val matchesExistingCore = matchedSaved != null &&
                matchedSaved.name.trim() == recipientName.trim() &&
                matchedSaved.phone.replace(Regex("[\\s-]+"), "") == recipientPhone.trim().replace(Regex("[\\s-]+"), "") &&
                matchedSaved.addressLine.trim() == streetAddress.trim() &&
                matchedSaved.area.trim() == areaLocality.trim() &&
                matchedSaved.city.trim() == city.trim()

              val targetId = if (matchesExistingCore) matchedSaved!!.id else "addr_${System.currentTimeMillis()}"
              val activeAddr = DeliveryAddress(
                id = targetId,
                name = recipientName.trim(),
                phone = recipientPhone.trim(),
                addressLine = streetAddress.trim(),
                area = areaLocality.trim(),
                city = city.trim(),
                province = if (matchesExistingCore) matchedSaved!!.province else "",
                tag = if (matchesExistingCore) matchedSaved!!.tag else "HOME",
                isDefault = if (matchesExistingCore) matchedSaved!!.isDefault else addresses.isEmpty(),
                latitude = deliveryLat,
                longitude = deliveryLng
              )
              MarketplaceRepository.selectAddress(activeAddr)
              val needsSaveToAccount = saveAddressToAccount && SupabaseClient.isAuthenticated &&
                (!matchesExistingCore || matchedSaved?.latitude != deliveryLat || matchedSaved?.longitude != deliveryLng)
              if (needsSaveToAccount) {
                MarketplaceRepository.addOrUpdateAddress(activeAddr) { saveRes ->
                  saveRes.onSuccess { persisted ->
                    selectedSavedAddressId = persisted.id
                  }
                }
              }

              MarketplaceRepository.placeCurrentOrder(
                deliveryNote = deliveryInstructions.trim(),
                deliveryLat = deliveryLat,
                deliveryLng = deliveryLng,
                deliveryLocationName = if (deliveryLat != null) listOf(areaLocality, city).filter { it.isNotBlank() }.joinToString(", ") else null,
                deliveryLocationCapturedAt = locationCapturedAt
              ) { result ->
                if (result.isSuccess) {
                  confirmedOrder = result.getOrNull()
                  stage = CartCheckoutStage.CONFIRMATION
                } else {
                  val err = result.exceptionOrNull()?.message ?: "Order placement failed"
                  orderErrorMessage = readableOrderError(err)
                }
              }
            }
          )
        }

        CartCheckoutStage.CONFIRMATION -> {
          val order = confirmedOrder
          OrderConfirmationView(
            order = order,
            onViewTracking = onOrderPlaced,
            onContinueShopping = {
              stage = CartCheckoutStage.CART
              onBackClick()
            }
          )
        }
      }
    }
  }
}

private fun readableOrderError(raw: String): String {
  val normalized = raw.replace("\\/", "/")
  val lower = normalized.lowercase()
  if (AppErrorMapper.isVendorShopClosedMessage(lower)) {
    return AppLanguageManager.tr(
      "This vendor shop is currently closed and is not accepting orders right now. Please check the shop's opening hours and try again later.",
      "یہ دکان اس وقت بند ہے اور فی الحال آرڈر قبول نہیں کر رہی۔ براہ کرم دکان کے کھلنے کے اوقات دیکھیں اور بعد میں دوبارہ کوشش کریں۔"
    )
  }
  if (lower.contains("jtbazar is currently closed") ||
      lower.contains("not accepting new customer orders") ||
      lower.contains("currently closed")) {
    return AppLanguageManager.tr(
      "JTBazar is currently closed and is not accepting new orders. Please try again during store opening hours.",
      "جے ٹی بازار اس وقت بند ہے اور نئے آرڈرز قبول نہیں کر رہا۔ براہ کرم اسٹور کے کھلنے کے اوقات میں دوبارہ کوشش کریں۔"
    )
  }
  return AppErrorMapper.sanitizeMessage(normalized, ErrorContext.ORDER_PLACE)
}

// ----------------------------------------------------
// 1. CART VIEW
// ----------------------------------------------------
@Composable
private fun CartView(
  items: List<CartItem>,
  subtotal: Int,
  discountAmount: Int = 0,
  deliveryFee: Int,
  grandTotal: Int,
  couponCodeInput: String = "",
  onCouponCodeChange: (String) -> Unit = {},
  onApplyCoupon: () -> Unit = {},
  couponMessage: String? = null,
  couponIsError: Boolean = false,
  onClearCart: () -> Unit,
  onContinueShopping: () -> Unit,
  onProceedToCheckout: () -> Unit
) {
  if (items.isEmpty()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Surface(
        shape = RoundedCornerShape(24.dp),
        color = JTSurfaceVariant,
        modifier = Modifier.size(80.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(Icons.Filled.ShoppingBag, contentDescription = null, tint = JTPurplePrimary, modifier = Modifier.size(40.dp))
        }
      }
      Spacer(modifier = Modifier.height(16.dp))
      Text(appText("Your Cart is Empty", "آپ کا کارٹ خالی ہے"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary)
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = appText(
          "Explore handcrafted Multani pottery, Pure Honey, and Fresh Kitchen essentials.",
          "ہاتھ سے بنے ملتانی برتن، خالص شہد اور تازہ کچن ضروریات دیکھیں۔"
        ),
        fontSize = 13.sp,
        color = JTTextSecondary,
        textAlign = TextAlign.Center
      )
      Spacer(modifier = Modifier.height(20.dp))
      Button(
        onClick = onContinueShopping,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White)
      ) {
        Text(appText("Start Shopping", "خریداری شروع کریں"), fontWeight = FontWeight.Bold)
      }
    }
  } else {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            appText("${items.size} item(s) in cart", "کارٹ میں ${items.size} آئٹم"),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = JTTextPrimary
          )
          TextButton(onClick = onClearCart, modifier = Modifier.testTag("clear_cart_btn")) {
            Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(appText("Clear Cart", "کارٹ خالی کریں"), fontSize = 12.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
          }
        }
      }

      items(items, key = { it.id }) { item ->
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = JTCardBg,
          border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
          modifier = Modifier.fillMaxWidth().testTag("cart_item_${item.id}")
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(JTSurfaceVariant),
              contentAlignment = Alignment.Center
            ) {
              val remoteUrl = item.product.imageUrl?.takeIf { it.isNotBlank() }
              when {
                remoteUrl != null -> {
                  AsyncImage(
                    model = rememberSizedImageRequest(data = remoteUrl, widthPx = 220, heightPx = 220),
                    contentDescription = item.product.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                  )
                }
                item.product.drawableRes != 0 -> {
                  Image(
                    painter = painterResource(id = item.product.drawableRes),
                    contentDescription = item.product.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                  )
                }
                else -> {
                  Icon(
                    imageVector = Icons.Outlined.ShoppingBag,
                    contentDescription = item.product.title,
                    tint = JTTextTertiary,
                    modifier = Modifier.size(26.dp)
                  )
                }
              }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = item.product.title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = JTTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )

              if (!item.selectedSize.isNullOrBlank() || !item.selectedPalette.isNullOrBlank()) {
                Text(
                  text = listOfNotNull(item.selectedSize, item.selectedPalette).joinToString(" • "),
                  fontSize = 11.sp,
                  color = JTPurplePrimary,
                  fontWeight = FontWeight.SemiBold
                )
              }

              Text(
                text = "PKR ${String.format("%,d", item.product.price)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = JTTextPrimary
              )

              Spacer(modifier = Modifier.height(6.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                QuantityStepper(
                  quantity = item.quantity,
                  onIncrement = { MarketplaceRepository.adjustQuantity(item.id, 1) },
                  onDecrement = { MarketplaceRepository.adjustQuantity(item.id, -1) }
                )

                IconButton(
                  onClick = { MarketplaceRepository.removeFromCart(item.id) },
                  modifier = Modifier.size(28.dp).testTag("cart_item_delete_${item.id}")
                ) {
                  Icon(Icons.Outlined.Delete, contentDescription = appText("Remove", "ہٹائیں"), tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
              }
            }
          }
        }
      }

      // Coupon Input Card
      item {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = JTCardBg,
          border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(appText("Have a Promo / Coupon Code?", "کیا آپ کے پاس پرومو / کوپن کوڈ ہے؟"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedTextField(
                value = couponCodeInput,
                onValueChange = onCouponCodeChange,
                placeholder = { Text(appText("e.g. JTSALE", "مثلاً JTSALE"), fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f).height(48.dp)
              )
              Button(
                onClick = onApplyCoupon,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = JTPurplePrimary),
                modifier = Modifier.height(48.dp)
              ) {
                Text(appText("Apply", "لاگو کریں"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
            if (!couponMessage.isNullOrBlank()) {
              Text(
                text = couponMessage,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (couponIsError) Color(0xFFC53030) else Color(0xFF03543F)
              )
            }
          }
        }
      }

      // Order Summary Card
      item {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = JTSurfaceVariant,
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(appText("Order Summary", "آرڈر کا خلاصہ"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = JTTextPrimary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(appText("Subtotal", "ذیلی کل"), fontSize = 13.sp, color = JTTextSecondary)
              Text("PKR ${String.format("%,d", subtotal)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = JTTextPrimary)
            }
            if (discountAmount > 0) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(appText("Coupon Discount", "کوپن رعایت"), fontSize = 13.sp, color = Color(0xFF03543F), fontWeight = FontWeight.SemiBold)
                Text("-PKR ${String.format("%,d", discountAmount)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF03543F))
              }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(appText("Delivery Charges", "ڈیلیوری چارجز"), fontSize = 13.sp, color = JTTextSecondary)
              Text(
                if (deliveryFee == 0) appText("Free", "مفت") else "PKR ${String.format("%,d", deliveryFee)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (deliveryFee == 0) Color(0xFF03543F) else JTTextPrimary
              )
            }
            HorizontalDivider(color = Color.LightGray)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(appText("Estimated Total", "تخمینی کل"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary)
              Text("PKR ${String.format("%,d", grandTotal)}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = JTPurplePrimary)
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(60.dp))
      }
    }
  }
}

// ----------------------------------------------------
// 2. CHECKOUT VIEW (DEDICATED FULL SCREEN)
// ----------------------------------------------------
@Composable
private fun CheckoutView(
  items: List<CartItem>,
  subtotal: Int,
  discountAmount: Int = 0,
  deliveryFee: Int,
  grandTotal: Int,
  couponCodeInput: String = "",
  onCouponCodeChange: (String) -> Unit = {},
  onApplyCoupon: () -> Unit = {},
  couponMessage: String? = null,
  couponIsError: Boolean = false,
  recipientName: String,
  onRecipientNameChange: (String) -> Unit,
  recipientPhone: String,
  onRecipientPhoneChange: (String) -> Unit,
  city: String,
  onCityChange: (String) -> Unit,
  streetAddress: String,
  onStreetAddressChange: (String) -> Unit,
  areaLocality: String,
  onAreaLocalityChange: (String) -> Unit,
  customerEmail: String,
  onCustomerEmailChange: (String) -> Unit,
  deliveryInstructions: String,
  onDeliveryInstructionsChange: (String) -> Unit,
  saveAddressToAccount: Boolean,
  onSaveAddressToAccountChange: (Boolean) -> Unit,
  savedAddresses: List<DeliveryAddress>,
  selectedSavedAddressId: String? = null,
  onSelectSavedAddress: (DeliveryAddress) -> Unit,
  onUseNewAddress: () -> Unit = {},
  deliveryLat: Double?,
  deliveryLng: Double?,
  isPinConfirmed: Boolean,
  onRequestPin: () -> Unit,
  onChangePin: () -> Unit,
  isPlacingOrder: Boolean,
  orderErrorMessage: String? = null,
  onPlaceOrder: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    if (!orderErrorMessage.isNullOrBlank()) {
      Card(
        modifier = Modifier.fillMaxWidth().testTag("checkout_order_error"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F0)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF2B8B5))
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.Top
        ) {
          Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Color(0xFFB42318))
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(appText("Order could not be placed", "آرڈر مکمل نہیں ہو سکا"), fontWeight = FontWeight.Bold, color = Color(0xFF912018))
            Text(orderErrorMessage, color = Color(0xFF7A271A), fontSize = 13.sp)
          }
        }
      }
    }

    // Saved Address Dropdown Selector
    if (savedAddresses.isNotEmpty()) {
      SavedAddressDropdown(
        savedAddresses = savedAddresses,
        selectedAddressId = selectedSavedAddressId,
        currentStreetAddress = streetAddress,
        onSelectSavedAddress = onSelectSavedAddress,
        onUseNewAddress = onUseNewAddress
      )
    }

    Text(appText("Delivery Details", "ڈیلیوری کی تفصیلات"), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = JTTextPrimary)

    // Keep the save-address choice prominent near the start of checkout.
    Card(
      modifier = Modifier.fillMaxWidth().testTag("checkout_save_address_card"),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7F2)),
      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD7C2))
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().clickable { onSaveAddressToAccountChange(!saveAddressToAccount) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Checkbox(
          checked = saveAddressToAccount,
          onCheckedChange = onSaveAddressToAccountChange,
          colors = CheckboxDefaults.colors(checkedColor = JTOrange)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(appText("Save this address for next time", "یہ پتہ آئندہ کے لیے محفوظ کریں"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary)
          Text(
            if (SupabaseClient.isAuthenticated) appText(
              "This address will be added to your saved addresses when you place the order.",
              "آرڈر دینے پر یہ پتہ آپ کے محفوظ کردہ پتوں میں شامل کر دیا جائے گا۔"
            ) else appText(
              "Sign in to your JTBazar account to save this address for future orders.",
              "مستقبل کے آرڈرز کے لیے یہ پتہ محفوظ کرنے کے واسطے اپنے جے ٹی بازار اکاؤنٹ میں سائن ان کریں۔"
            ),
            fontSize = 11.sp,
            color = JTTextSecondary
          )
        }
      }
    }

    // Required fields: Full Name, Mobile, City, Street Address
    OutlinedTextField(
      value = recipientName,
      onValueChange = onRecipientNameChange,
      label = { Text(appText("Full Name *", "پورا نام *")) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("checkout_name_input")
    )

    OutlinedTextField(
      value = recipientPhone,
      onValueChange = onRecipientPhoneChange,
      label = { Text(appText("Mobile Number (for COD verification) *", "موبائل نمبر (COD تصدیق کے لیے) *")) },
      placeholder = { Text("0300 1234567") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("checkout_phone_input")
    )

    OutlinedTextField(
      value = city,
      onValueChange = onCityChange,
      label = { Text(appText("City *", "شہر *")) },
      placeholder = { Text(appText("e.g. Lahore, Karachi, Islamabad", "مثلاً لاہور، کراچی، اسلام آباد")) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("checkout_city_input")
    )

    OutlinedTextField(
      value = streetAddress,
      onValueChange = onStreetAddressChange,
      label = { Text(appText("Full Street Address *", "مکمل گلی کا پتہ *")) },
      placeholder = { Text(appText("House / Flat #, Street #, Building", "گھر / فلیٹ نمبر، گلی نمبر، عمارت")) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("checkout_address_input")
    )

    OutlinedTextField(
      value = areaLocality,
      onValueChange = onAreaLocalityChange,
      label = { Text(appText("Area / Locality / Landmark", "علاقہ / نشانی")) },
      placeholder = { Text(appText("e.g. Gulberg III, DHA Phase 5", "مثلاً گلبرگ، ڈی ایچ اے")) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("checkout_area_input")
    )

    OutlinedTextField(
      value = customerEmail,
      onValueChange = onCustomerEmailChange,
      label = { Text(appText("Email Address (for order receipt)", "ای میل پتہ (آرڈر رسید کے لیے)")) },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("checkout_email_input")
    )

    OutlinedTextField(
      value = deliveryInstructions,
      onValueChange = onDeliveryInstructionsChange,
      label = { Text(appText("Delivery Instructions (Optional)", "ڈیلیوری ہدایات (اختیاری)")) },
      placeholder = { Text(appText("e.g. Leave parcel at security gate, call on arrival", "مثلاً سیکیورٹی گیٹ پر پارسل رکھیں، پہنچنے پر کال کریں")) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("checkout_instructions_input")
    )

    // Location Pin Section (GPS)
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = if (isPinConfirmed) Color(0xFFDEF7EC) else JTSurfaceVariant,
      border = androidx.compose.foundation.BorderStroke(1.dp, if (isPinConfirmed) Color(0xFF0E9F6E) else JTBorder),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.LocationOn,
              contentDescription = null,
              tint = if (isPinConfirmed) Color(0xFF03543F) else JTOrange,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (isPinConfirmed) appText("Delivery Location Confirmed", "ڈیلیوری مقام کی تصدیق ہو گئی")
              else appText("Delivery Location Pin", "ڈیلیوری لوکیشن پن"),
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = if (isPinConfirmed) Color(0xFF03543F) else JTTextPrimary
            )
          }

          if (isPinConfirmed) {
            Text(
              text = appText("Change Pin", "پن تبدیل کریں"),
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = JTOrange,
              modifier = Modifier.clickable(onClick = onChangePin)
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (isPinConfirmed && deliveryLat != null && deliveryLng != null) {
          Text(
            text = appText(
              "Your delivery pin is saved securely for JTBazar delivery.",
              "جے ٹی بازار ڈیلیوری کے لیے آپ کا لوکیشن پن محفوظ کر لیا گیا ہے۔"
            ),
            fontSize = 11.sp,
            color = Color(0xFF03543F)
          )
        } else {
          Text(
            text = appText(
              "Confirm your delivery location pin. It is required for every order so the driver and Admin can find your address accurately.",
              "اپنے ڈیلیوری لوکیشن پن کی تصدیق کریں۔ ہر آرڈر کے لیے یہ ضروری ہے تاکہ ڈرائیور آپ کا پتہ درست تلاش کر سکے۔"
            ),
            fontSize = 11.sp,
            color = JTTextSecondary
          )
          Spacer(modifier = Modifier.height(8.dp))
          Button(
            onClick = onRequestPin,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = JTPurplePrimary),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(34.dp).testTag("capture_location_pin_btn")
          ) {
            Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(appText("Confirm My Delivery Location", "اپنے ڈیلیوری مقام کی تصدیق کریں"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // Payment Method: Cash on Delivery (COD)
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = Color(0xFFEFF6FF),
      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF93C5FD)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(Icons.Filled.Payment, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Text(appText("Payment Method: Cash on Delivery (COD)", "ادائیگی کا طریقہ: کیش آن ڈیلیوری (COD)"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E40AF))
          Text(appText("Pay with cash when your parcel is delivered to your doorstep.", "جب پارسل آپ کے دروازے پر پہنچے تو نقد ادائیگی کریں۔"), fontSize = 11.sp, color = Color(0xFF1E3A8A))
        }
      }
    }

    // Coupon Card in Checkout
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = JTCardBg,
      border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(appText("Have a Coupon Code?", "کیا آپ کے پاس کوپن کوڈ ہے؟"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = couponCodeInput,
            onValueChange = onCouponCodeChange,
            placeholder = { Text(appText("e.g. JTSALE", "مثلاً JTSALE"), fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f).height(48.dp)
          )
          Button(
            onClick = onApplyCoupon,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = JTPurplePrimary),
            modifier = Modifier.height(48.dp)
          ) {
            Text(appText("Apply", "لاگو کریں"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
        if (!couponMessage.isNullOrBlank()) {
          Text(
            text = couponMessage,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (couponIsError) Color(0xFFC53030) else Color(0xFF03543F)
          )
        }
      }
    }

    // Order Totals Summary
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = JTSurfaceVariant,
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(appText("Items Subtotal (${items.size})", "آئٹمز ذیلی کل (${items.size})"), fontSize = 12.sp, color = JTTextSecondary)
          Text("PKR ${String.format("%,d", subtotal)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        if (discountAmount > 0) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(appText("Coupon Discount", "کوپن رعایت"), fontSize = 12.sp, color = Color(0xFF03543F), fontWeight = FontWeight.SemiBold)
            Text("-PKR ${String.format("%,d", discountAmount)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF03543F))
          }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(appText("Delivery Charges", "ڈیلیوری چارجز"), fontSize = 12.sp, color = JTTextSecondary)
          Text(
            if (deliveryFee == 0) appText("Free", "مفت") else "PKR ${String.format("%,d", deliveryFee)}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (deliveryFee == 0) Color(0xFF03543F) else JTTextPrimary
          )
        }
        HorizontalDivider(color = Color.LightGray)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(appText("Total Payable on Delivery", "ڈیلیوری پر قابل ادائیگی کل"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
          Text("PKR ${String.format("%,d", grandTotal)}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = JTPurplePrimary)
        }
      }
    }

    // Place Order Button
    Button(
      onClick = onPlaceOrder,
      enabled = !isPlacingOrder && recipientName.isNotBlank() && recipientPhone.isNotBlank() && city.isNotBlank() && streetAddress.isNotBlank(),
      shape = RoundedCornerShape(12.dp),
      colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White),
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag("checkout_place_order_btn")
    ) {
      if (isPlacingOrder) {
        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(appText("Validating & Placing Order...", "تصدیق اور آرڈر دیا جا رہا ہے..."), fontWeight = FontWeight.Bold)
      } else {
        Text(appText("Place Cash on Delivery Order", "کیش آن ڈیلیوری آرڈر دیں"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
      }
    }

    Spacer(modifier = Modifier.height(24.dp))
  }
}

// ----------------------------------------------------
// 3. ORDER CONFIRMATION VIEW
// ----------------------------------------------------
@Composable
private fun OrderConfirmationView(
  order: OrderRecord?,
  onViewTracking: () -> Unit,
  onContinueShopping: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Surface(
      shape = RoundedCornerShape(32.dp),
      color = Color(0xFFDEF7EC),
      modifier = Modifier.size(90.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF0E9F6E), modifier = Modifier.size(54.dp))
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Text(appText("Thank You! Order Placed", "شکریہ! آرڈر موصول ہو گیا"), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary)
    Spacer(modifier = Modifier.height(6.dp))

    if (order != null) {
      Text(
        text = appText("Order Number: ${order.orderNumber}", "آرڈر نمبر: ${order.orderNumber}"),
        fontSize = 15.sp,
        fontWeight = FontWeight.ExtraBold,
        color = JTPurplePrimary
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = appText(
          "Total: PKR ${String.format("%,d", order.grandTotal)} • Cash on Delivery",
          "کل رقم: PKR ${String.format("%,d", order.grandTotal)} • کیش آن ڈیلیوری"
        ),
        fontSize = 13.sp,
        color = JTTextSecondary
      )
    }

    Spacer(modifier = Modifier.height(10.dp))
    Text(
      text = appText(
        "Our verified vendor and delivery partners have been notified. Your items will be dispatched promptly.",
        "ہمارے تصدیق شدہ فروش اور ڈیلیوری پارٹنرز کو اطلاع دے دی گئی ہے۔ آپ کی اشیاء جلد روانہ کر دی جائیں گی۔"
      ),
      fontSize = 12.sp,
      color = JTTextSecondary,
      textAlign = TextAlign.Center,
      lineHeight = 16.sp
    )

    Spacer(modifier = Modifier.height(28.dp))

    Button(
      onClick = onViewTracking,
      shape = RoundedCornerShape(12.dp),
      colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White),
      modifier = Modifier.fillMaxWidth().height(46.dp).testTag("confirm_track_order_btn")
    ) {
      Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null)
      Spacer(modifier = Modifier.width(8.dp))
      Text(appText("Track Order Status", "آرڈر کی صورتحال دیکھیں"), fontWeight = FontWeight.Bold)
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
      onClick = onContinueShopping,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().height(46.dp).testTag("confirm_continue_shopping_btn")
    ) {
      Text(appText("Continue Shopping", "خریداری جاری رکھیں"), fontWeight = FontWeight.Bold)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedAddressDropdown(
  savedAddresses: List<DeliveryAddress>,
  selectedAddressId: String?,
  currentStreetAddress: String,
  onSelectSavedAddress: (DeliveryAddress) -> Unit,
  onUseNewAddress: () -> Unit,
  modifier: Modifier = Modifier
) {
  var expanded by remember { mutableStateOf(false) }

  val activeSaved = savedAddresses.firstOrNull { it.id == selectedAddressId }
    ?: savedAddresses.firstOrNull {
      currentStreetAddress.isNotBlank() && it.addressLine.equals(currentStreetAddress.trim(), ignoreCase = true)
    }

  val displayLabel = if (activeSaved != null) {
    val locationParts = listOf(activeSaved.addressLine, activeSaved.area, activeSaved.city)
      .filter { it.isNotBlank() }
      .joinToString(", ")
    "${activeSaved.name} • $locationParts"
  } else {
    appText("Use a New Delivery Address", "نیا ڈیلیوری پتہ استعمال کریں")
  }

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = JTPurpleContainer.copy(alpha = 0.35f),
    border = androidx.compose.foundation.BorderStroke(1.dp, JTPurplePrimary.copy(alpha = 0.35f)),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = null,
            tint = JTPurplePrimary,
            modifier = Modifier.size(18.dp)
          )
          Text(
            text = appText("Saved Delivery Addresses", "محفوظ شدہ ڈیلیوری پتے"),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = JTTextPrimary
          )
        }
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = JTPurplePrimary.copy(alpha = 0.12f)
        ) {
          Text(
            text = appText("${savedAddresses.size} saved", "${savedAddresses.size} محفوظ"),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = JTPurplePrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }
      }

      ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
      ) {
        OutlinedTextField(
          value = displayLabel,
          onValueChange = {},
          readOnly = true,
          singleLine = true,
          label = { Text(appText("Choose Saved Address", "محفوظ پتہ منتخب کریں")) },
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = JTPurplePrimary,
            unfocusedBorderColor = JTBorder
          ),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .menuAnchor()
            .testTag("checkout_saved_address_dropdown")
        )

        ExposedDropdownMenu(
          expanded = expanded,
          onDismissRequest = { expanded = false },
          modifier = Modifier.background(Color.White)
        ) {
          savedAddresses.forEach { addr ->
            val isSelected = addr.id == activeSaved?.id
            val fullLine = listOf(addr.addressLine, addr.area, addr.city)
              .filter { it.isNotBlank() }
              .joinToString(", ")

            DropdownMenuItem(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(if (isSelected) JTPurpleContainer.copy(alpha = 0.45f) else Color.White)
                .testTag("saved_address_option_${addr.id}"),
              leadingIcon = {
                Icon(
                  imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.Home,
                  contentDescription = null,
                  tint = if (isSelected) JTPurplePrimary else JTTextSecondary,
                  modifier = Modifier.size(20.dp)
                )
              },
              text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Text(
                      text = addr.name,
                      fontWeight = FontWeight.Bold,
                      fontSize = 13.sp,
                      color = JTTextPrimary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    if (addr.isDefault) {
                      Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFDEF7EC)
                      ) {
                        Text(
                          text = appText("DEFAULT", "بنیادی"),
                          fontSize = 9.sp,
                          fontWeight = FontWeight.Bold,
                          color = Color(0xFF03543F),
                          modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                      }
                    }
                    if (addr.latitude != null && addr.longitude != null) {
                      Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = JTPurpleContainer
                      ) {
                        Text(
                          text = appText("GPS", "جی پی ایس"),
                          fontSize = 9.sp,
                          fontWeight = FontWeight.Bold,
                          color = JTPurplePrimary,
                          modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                      }
                    }
                  }
                  Text(
                    text = fullLine,
                    fontSize = 11.5.sp,
                    color = JTTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = addr.phone,
                    fontSize = 11.sp,
                    color = JTTextSecondary
                  )
                }
              },
              contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
              onClick = {
                expanded = false
                onSelectSavedAddress(addr)
              }
            )
          }

          HorizontalDivider(color = JTBorder)

          DropdownMenuItem(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("saved_address_option_new"),
            leadingIcon = {
              Icon(
                imageVector = Icons.Filled.AddLocationAlt,
                contentDescription = null,
                tint = JTOrange,
                modifier = Modifier.size(20.dp)
              )
            },
            text = {
              Text(
                text = appText("Use a New Delivery Address", "نیا ڈیلیوری پتہ استعمال کریں"),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = JTOrange
              )
            },
            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
            onClick = {
              expanded = false
              onUseNewAddress()
            }
          )
        }
      }
    }
  }
}
