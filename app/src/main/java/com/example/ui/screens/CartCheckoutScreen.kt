package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.example.data.supabase.SupabaseAuthService
import com.example.data.supabase.SupabaseClient
import com.example.ui.components.JTBazarTopBar
import com.example.ui.components.QuantityStepper
import com.example.ui.theme.*
import com.example.location.AddressReverseGeocoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
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

  // Checkout Form States
  var recipientName by remember(userProfile, selectedAddress) {
    mutableStateOf(selectedAddress?.name ?: userProfile?.fullName ?: currentUser?.fullName ?: "")
  }
  var recipientPhone by remember(userProfile, selectedAddress) {
    mutableStateOf(selectedAddress?.phone ?: userProfile?.phone ?: currentUser?.phone ?: "")
  }
  var city by remember(userProfile, selectedAddress) {
    mutableStateOf(selectedAddress?.city ?: userProfile?.city ?: "")
  }
  var streetAddress by remember(selectedAddress) {
    mutableStateOf(selectedAddress?.addressLine ?: "")
  }
  var areaLocality by remember(selectedAddress) {
    mutableStateOf(selectedAddress?.area ?: "")
  }
  var customerEmail by remember(currentUser, userProfile) {
    mutableStateOf(currentUser?.email ?: userProfile?.email ?: "")
  }
  var deliveryInstructions by remember { mutableStateOf("") }
  var saveAddressToAccount by remember { mutableStateOf(true) }

  // Location Pin State
  var deliveryLat by remember(selectedAddress) { mutableStateOf(selectedAddress?.latitude) }
  var deliveryLng by remember(selectedAddress) { mutableStateOf(selectedAddress?.longitude) }
  var locationCapturedAt by remember { mutableStateOf<String?>(null) }
  var isPinConfirmed by remember(selectedAddress) { mutableStateOf(selectedAddress?.latitude != null && selectedAddress.longitude != null) }

  val locationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { perms ->
    val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true || perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    if (granted) {
      try {
        LocationServices.getFusedLocationProviderClient(context)
          .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
          .addOnSuccessListener { loc ->
            if (loc == null) {
              Toast.makeText(context, "Could not get a fresh GPS fix. Turn on location and try again.", Toast.LENGTH_LONG).show()
            } else {
              deliveryLat = loc.latitude
              deliveryLng = loc.longitude
              locationCapturedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
              }.format(java.util.Date())
              isPinConfirmed = true
              coroutineScope.launch {
                val resolved = AddressReverseGeocoder.resolve(context, loc.latitude, loc.longitude)
                if (resolved != null) {
                  if (resolved.street.isNotBlank()) streetAddress = resolved.street
                  if (resolved.area.isNotBlank()) areaLocality = resolved.area
                  if (resolved.city.isNotBlank()) city = resolved.city
                  Toast.makeText(
                    context,
                    if (resolved.street.isNotBlank()) "Location and street detected. Please verify your house number."
                    else "Location captured. Please enter your street and house number.",
                    Toast.LENGTH_LONG
                  ).show()
                } else {
                  Toast.makeText(context, "Location captured. Please enter your street and house number.", Toast.LENGTH_LONG).show()
                }
              }
            }
          }
          .addOnFailureListener {
            Toast.makeText(context, "Unable to get your current location. Please try again.", Toast.LENGTH_LONG).show()
          }
      } catch (_: SecurityException) {
        Toast.makeText(context, "Location permission is required.", Toast.LENGTH_LONG).show()
      }
    } else {
      Toast.makeText(context, "Location permission is required to confirm your delivery pin.", Toast.LENGTH_SHORT).show()
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
      couponMessage = "Please enter a coupon code."
      couponIsError = true
      return
    }
    val promo = homepagePromotions.firstOrNull {
      !it.couponCode.isNullOrBlank() && it.couponCode.equals(clean, ignoreCase = true)
    }
    if (promo != null && (promo.discountPercent ?: 0.0) > 0.0) {
      appliedCouponCode = promo.couponCode
      appliedDiscountPercent = promo.discountPercent ?: 0.0
      couponMessage = "Coupon applied! ${(promo.discountPercent ?: 0.0).toInt()}% off discount."
      couponIsError = false
    } else {
      couponMessage = "Invalid or expired coupon code."
      couponIsError = true
    }
  }

  Scaffold(
    containerColor = Color.White,
    topBar = {
      JTBazarTopBar(
        title = when (stage) {
          CartCheckoutStage.CART -> "Shopping Cart"
          CartCheckoutStage.CHECKOUT -> "Checkout & Delivery"
          CartCheckoutStage.CONFIRMATION -> "Order Confirmation"
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
              Text("TOTAL (INCL. DELIVERY)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = JTTextSecondary)
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
              Text("Proceed to Checkout", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
            onRecipientNameChange = { recipientName = it },
            recipientPhone = recipientPhone,
            onRecipientPhoneChange = { recipientPhone = it },
            city = city,
            onCityChange = { city = it },
            streetAddress = streetAddress,
            onStreetAddressChange = { streetAddress = it },
            areaLocality = areaLocality,
            onAreaLocalityChange = { areaLocality = it },
            customerEmail = customerEmail,
            onCustomerEmailChange = { customerEmail = it },
            deliveryInstructions = deliveryInstructions,
            onDeliveryInstructionsChange = { deliveryInstructions = it },
            saveAddressToAccount = saveAddressToAccount,
            onSaveAddressToAccountChange = { saveAddressToAccount = it },
            savedAddresses = addresses,
            onSelectSavedAddress = { addr ->
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
            deliveryLat = deliveryLat,
            deliveryLng = deliveryLng,
            isPinConfirmed = isPinConfirmed,
            onRequestPin = {
              locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
              )
            },
            onChangePin = {
              isPinConfirmed = false
              deliveryLat = null
              deliveryLng = null
            },
            isPlacingOrder = isPlacingOrder,
            onPlaceOrder = {
              if (recipientName.isBlank() || recipientPhone.isBlank() || city.isBlank() || streetAddress.isBlank()) {
                Toast.makeText(context, "Please complete all required delivery fields.", Toast.LENGTH_SHORT).show()
                return@CheckoutView
              }
              if (!Regex("^(03[0-9]{9}|\\+923[0-9]{9})$").matches(recipientPhone.trim())) {
                Toast.makeText(context, "Enter exactly 03001234567 or +923001234567.", Toast.LENGTH_LONG).show()
                return@CheckoutView
              }
              if (customerEmail.isNotBlank() && (customerEmail.trim().length > 254 || !android.util.Patterns.EMAIL_ADDRESS.matcher(customerEmail.trim()).matches())) {
                Toast.makeText(context, "Enter a valid email address.", Toast.LENGTH_LONG).show()
                return@CheckoutView
              }
              if (!codEnabled) {
                Toast.makeText(context, "Cash on delivery is currently unavailable.", Toast.LENGTH_LONG).show()
                return@CheckoutView
              }
              val unavailableCartItem = cartItems.firstOrNull { item ->
                (!item.product.availabilityKnown || !item.product.isAvailable) ||
                  (item.product.foodSellingUnit != null && item.product.dailyStatus != null && item.product.dailyStatus != "available") ||
                  (item.product.variantIds.isNotEmpty() && item.variantId.isNullOrBlank()) ||
                  (!item.variantId.isNullOrBlank() && item.product.variantAvailability[item.variantId] != true)
              }
              if (unavailableCartItem != null) {
                Toast.makeText(context, unavailableCartItem.product.title + " stock or selected option could not be verified. Refresh the catalog or remove it from your cart.", Toast.LENGTH_LONG).show()
                return@CheckoutView
              }
              if (deliveryLat == null || deliveryLng == null || !isPinConfirmed) {
                Toast.makeText(context, "Add your current location pin to confirm the 5 km food delivery area.", Toast.LENGTH_LONG).show()
                return@CheckoutView
              }

              // Update/Save Address
              val activeAddr = DeliveryAddress(
                id = selectedAddress?.id ?: "addr_${System.currentTimeMillis()}",
                name = recipientName.trim(),
                phone = recipientPhone.trim(),
                addressLine = streetAddress.trim(),
                area = areaLocality.trim(),
                city = city.trim(),
                province = selectedAddress?.province.orEmpty(),
                tag = selectedAddress?.tag ?: "HOME",
                isDefault = selectedAddress?.isDefault ?: addresses.isEmpty(),
                latitude = deliveryLat,
                longitude = deliveryLng
              )
              MarketplaceRepository.selectAddress(activeAddr)
              if (saveAddressToAccount && SupabaseClient.isAuthenticated) {
                MarketplaceRepository.addOrUpdateAddress(activeAddr)
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
                  Toast.makeText(context, err, Toast.LENGTH_LONG).show()
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
      Text("Your Cart is Empty", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary)
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "Explore handcrafted Multani pottery, Pure Honey, and Fresh Kitchen essentials.",
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
        Text("Start Shopping", fontWeight = FontWeight.Bold)
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
          Text("${items.size} item(s) in cart", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary)
          TextButton(onClick = onClearCart, modifier = Modifier.testTag("clear_cart_btn")) {
            Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Clear Cart", fontSize = 12.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
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
                .background(JTSurfaceVariant)
            ) {
              // Use Coil for remote and bundled images consistently. This avoids
              // Compose painterResource decoding failures for unsupported assets.
              AsyncImage(
                model = item.product.imageUrl?.takeIf { it.isNotBlank() }
                  ?: R.drawable.ic_launcher_background,
                contentDescription = item.product.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
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
                  Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = Color.Gray, modifier = Modifier.size(18.dp))
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
            Text("Have a Promo / Coupon Code?", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedTextField(
                value = couponCodeInput,
                onValueChange = onCouponCodeChange,
                placeholder = { Text("e.g. JTSALE", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f).height(48.dp)
              )
              Button(
                onClick = onApplyCoupon,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = JTPurplePrimary),
                modifier = Modifier.height(48.dp)
              ) {
                Text("Apply", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            Text("Order Summary", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = JTTextPrimary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Subtotal", fontSize = 13.sp, color = JTTextSecondary)
              Text("PKR ${String.format("%,d", subtotal)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = JTTextPrimary)
            }
            if (discountAmount > 0) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Coupon Discount", fontSize = 13.sp, color = Color(0xFF03543F), fontWeight = FontWeight.SemiBold)
                Text("-PKR ${String.format("%,d", discountAmount)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF03543F))
              }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Delivery Charges", fontSize = 13.sp, color = JTTextSecondary)
              Text(
                if (deliveryFee == 0) "Free" else "PKR ${String.format("%,d", deliveryFee)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (deliveryFee == 0) Color(0xFF03543F) else JTTextPrimary
              )
            }
            HorizontalDivider(color = Color.LightGray)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Estimated Total", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary)
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
  onSelectSavedAddress: (DeliveryAddress) -> Unit,
  deliveryLat: Double?,
  deliveryLng: Double?,
  isPinConfirmed: Boolean,
  onRequestPin: () -> Unit,
  onChangePin: () -> Unit,
  isPlacingOrder: Boolean,
  onPlaceOrder: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Saved Address Quick Picker
    if (savedAddresses.isNotEmpty()) {
      Text("Select Saved Address", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = JTTextPrimary)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        savedAddresses.take(3).forEach { addr ->
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (streetAddress == addr.addressLine) JTPurpleContainer else JTSurfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (streetAddress == addr.addressLine) JTPurplePrimary else Color.Transparent),
            modifier = Modifier.clickable { onSelectSavedAddress(addr) }
          ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
              Text(addr.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
              Text(addr.tag, fontSize = 10.sp, color = JTPurplePrimary, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }

    Text("Delivery Details", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = JTTextPrimary)

    // Required fields: Full Name, Mobile, City, Street Address
    OutlinedTextField(
      value = recipientName,
      onValueChange = onRecipientNameChange,
      label = { Text("Full Name *") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("checkout_name_input")
    )

    OutlinedTextField(
      value = recipientPhone,
      onValueChange = onRecipientPhoneChange,
      label = { Text("Mobile Number (for COD verification) *") },
      placeholder = { Text("0300 1234567") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("checkout_phone_input")
    )

    OutlinedTextField(
      value = city,
      onValueChange = onCityChange,
      label = { Text("City *") },
      placeholder = { Text("e.g. Lahore, Karachi, Islamabad") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("checkout_city_input")
    )

    OutlinedTextField(
      value = streetAddress,
      onValueChange = onStreetAddressChange,
      label = { Text("Full Street Address *") },
      placeholder = { Text("House / Flat #, Street #, Building") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("checkout_address_input")
    )

    OutlinedTextField(
      value = areaLocality,
      onValueChange = onAreaLocalityChange,
      label = { Text("Area / Locality / Landmark") },
      placeholder = { Text("e.g. Gulberg III, DHA Phase 5") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("checkout_area_input")
    )

    OutlinedTextField(
      value = customerEmail,
      onValueChange = onCustomerEmailChange,
      label = { Text("Email Address (for order receipt)") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("checkout_email_input")
    )

    OutlinedTextField(
      value = deliveryInstructions,
      onValueChange = onDeliveryInstructionsChange,
      label = { Text("Delivery Instructions (Optional)") },
      placeholder = { Text("e.g. Leave parcel at security gate, call on arrival") },
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
              text = if (isPinConfirmed) "Delivery Location Confirmed" else "Delivery Location Pin",
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = if (isPinConfirmed) Color(0xFF03543F) else JTTextPrimary
            )
          }

          if (isPinConfirmed) {
            Text(
              text = "Change Pin",
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
            text = "Your delivery pin is saved securely for JTBazar delivery.",
            fontSize = 11.sp,
            color = Color(0xFF03543F)
          )
        } else {
          Text(
            text = "Confirm your delivery location pin. It is required for every order so the driver and Admin can find your address accurately.",
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
            Text("Confirm My Delivery Location", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // Save Address Checkbox
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.clickable { onSaveAddressToAccountChange(!saveAddressToAccount) }
    ) {
      Checkbox(
        checked = saveAddressToAccount,
        onCheckedChange = onSaveAddressToAccountChange,
        colors = CheckboxDefaults.colors(checkedColor = JTOrange)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text("Save this address to my account for future orders", fontSize = 12.sp, color = JTTextPrimary)
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
          Text("Payment Method: Cash on Delivery (COD)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E40AF))
          Text("Pay with cash when your parcel is delivered to your doorstep.", fontSize = 11.sp, color = Color(0xFF1E3A8A))
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
        Text("Have a Coupon Code?", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = couponCodeInput,
            onValueChange = onCouponCodeChange,
            placeholder = { Text("e.g. JTSALE", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f).height(48.dp)
          )
          Button(
            onClick = onApplyCoupon,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = JTPurplePrimary),
            modifier = Modifier.height(48.dp)
          ) {
            Text("Apply", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
          Text("Items Subtotal (${items.size})", fontSize = 12.sp, color = JTTextSecondary)
          Text("PKR ${String.format("%,d", subtotal)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        if (discountAmount > 0) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Coupon Discount", fontSize = 12.sp, color = Color(0xFF03543F), fontWeight = FontWeight.SemiBold)
            Text("-PKR ${String.format("%,d", discountAmount)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF03543F))
          }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Delivery Charges", fontSize = 12.sp, color = JTTextSecondary)
          Text(
            if (deliveryFee == 0) "Free" else "PKR ${String.format("%,d", deliveryFee)}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (deliveryFee == 0) Color(0xFF03543F) else JTTextPrimary
          )
        }
        HorizontalDivider(color = Color.LightGray)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Total Payable on Delivery", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
        Text("Validating & Placing Order...", fontWeight = FontWeight.Bold)
      } else {
        Text("Place Cash on Delivery Order", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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

    Text("Thank You! Order Placed", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary)
    Spacer(modifier = Modifier.height(6.dp))

    if (order != null) {
      Text(
        text = "Order Number: ${order.orderNumber}",
        fontSize = 15.sp,
        fontWeight = FontWeight.ExtraBold,
        color = JTPurplePrimary
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "Total: PKR ${String.format("%,d", order.grandTotal)} • Cash on Delivery",
        fontSize = 13.sp,
        color = JTTextSecondary
      )
    }

    Spacer(modifier = Modifier.height(10.dp))
    Text(
      text = "Our verified vendor and delivery partners have been notified. Your items will be dispatched promptly.",
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
      Text("Track Order Status", fontWeight = FontWeight.Bold)
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
      onClick = onContinueShopping,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().height(46.dp).testTag("confirm_continue_shopping_btn")
    ) {
      Text("Continue Shopping", fontWeight = FontWeight.Bold)
    }
  }
}
