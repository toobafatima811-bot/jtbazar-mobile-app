package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.R
import com.example.data.model.CategoryItem
import com.example.data.model.Product
import com.example.data.repository.CampaignPopupManager
import com.example.data.repository.MarketplaceRepository
import com.example.data.supabase.SupabaseAuthService
import com.example.data.supabase.GlobalLoadingState
import com.example.ui.components.CampaignLaunchPopupDialog
import com.example.ui.components.JTBazarPullRefresh
import com.example.ui.screens.*
import com.example.ui.theme.*
import org.json.JSONObject

data class NotificationNavigation(
  val id: String? = null,
  val kind: String? = null,
  val relatedOrderId: String? = null,
  val link: String? = null
)

enum class AppScreen {
  SPLASH,
  HOME,
  CATEGORIES,
  CATEGORY_PRODUCTS,
  DISCOVER,
  WISHLIST,
  ACCOUNT,
  PRODUCT_DETAIL,
  CART_CHECKOUT,
  ORDER_TRACKING,
  LOGIN,
  REGISTER,
  EMAIL_OTP,
  LOGIN_OTP,
  FORGOT_PASSWORD,
  RESET_PASSWORD,
  MY_PROFILE,
  MY_ORDERS,
  MY_ADDRESSES,
  NOTIFICATIONS,
  RETURNS_REFUNDS,
  CUSTOMER_SUPPORT,
  ACCOUNT_SETTINGS,
  SECURITY_PASSWORD,
  BUDGET_SHOPPING,
  VENDOR_CENTER,
  DRIVER_CENTER,
  DRIVER_NAVIGATION,
  PUBLIC_SHOPS
}

@Composable
fun MainApp(notificationTap: NotificationNavigation? = null) {
  val isCatalogLoading by MarketplaceRepository.isLoading.collectAsState()
  val isPlacingOrder by MarketplaceRepository.isPlacingOrder.collectAsState()
  val products by MarketplaceRepository.products.collectAsState()
  val isGlobalLoading by GlobalLoadingState.active.collectAsState()
  val showGlobalLoader = isGlobalLoading || isPlacingOrder || (isCatalogLoading && products.isEmpty())
  val lifecycleOwner = LocalLifecycleOwner.current
  LaunchedEffect(lifecycleOwner) {
    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
      while (true) {
        delay(60_000)
        MarketplaceRepository.refreshLiveCatalog(showLoading = false, refreshUserData = false)
      }
    }
  }

  val accountScrollState = rememberScrollState()
  var currentScreen by remember { mutableStateOf(AppScreen.SPLASH) }
  var isPullRefreshing by remember { mutableStateOf(false) }
  val pullRefreshScope = rememberCoroutineScope()
  var previousScreen by remember { mutableStateOf(AppScreen.HOME) }
  var selectedProduct by remember { mutableStateOf<Product?>(null) }
  var selectedPublicShopId by remember { mutableStateOf<String?>(null) }
  var selectedCategory by remember { mutableStateOf<CategoryItem?>(null) }
  var selectedDeliveryForNavigation by remember { mutableStateOf<JSONObject?>(null) }

  LaunchedEffect(notificationTap?.id, notificationTap?.kind, notificationTap?.link) {
    val tap = notificationTap ?: return@LaunchedEffect
    if (!tap.id.isNullOrBlank()) MarketplaceRepository.markNotificationRead(tap.id)
    when {
      tap.kind.equals("order", ignoreCase = true) || !tap.relatedOrderId.isNullOrBlank() -> currentScreen = AppScreen.MY_ORDERS
      tap.kind.equals("account", ignoreCase = true) || tap.link?.contains("account", true) == true -> currentScreen = AppScreen.ACCOUNT
      else -> currentScreen = AppScreen.HOME
    }
  }

  // Auth navigation state
  var pendingAuthEmail by remember { mutableStateOf("") }
  var pendingFullName by remember { mutableStateOf("") }

  val googleOAuthStatus by SupabaseAuthService.googleOAuthStatus.collectAsState()
  LaunchedEffect(googleOAuthStatus) {
    if (googleOAuthStatus == "success") {
      currentScreen = AppScreen.ACCOUNT
      SupabaseAuthService.clearGoogleOAuthStatus()
    }
  }

  fun navigateTo(screen: AppScreen) {
    previousScreen = currentScreen
    currentScreen = screen
  }

  fun navigateBack() {
    when (currentScreen) {
      AppScreen.PRODUCT_DETAIL -> currentScreen = previousScreen
      AppScreen.CATEGORY_PRODUCTS -> currentScreen = AppScreen.CATEGORIES
      AppScreen.CART_CHECKOUT -> currentScreen = AppScreen.HOME
      AppScreen.ORDER_TRACKING -> currentScreen = AppScreen.HOME
      AppScreen.SPLASH -> currentScreen = AppScreen.HOME
      AppScreen.LOGIN, AppScreen.REGISTER, AppScreen.FORGOT_PASSWORD -> currentScreen = AppScreen.ACCOUNT
      AppScreen.EMAIL_OTP, AppScreen.LOGIN_OTP, AppScreen.RESET_PASSWORD -> currentScreen = AppScreen.LOGIN
      AppScreen.MY_PROFILE, AppScreen.NOTIFICATIONS -> currentScreen = if (previousScreen == AppScreen.DRIVER_CENTER) AppScreen.DRIVER_CENTER else AppScreen.ACCOUNT
      AppScreen.MY_ORDERS, AppScreen.MY_ADDRESSES, AppScreen.RETURNS_REFUNDS, AppScreen.CUSTOMER_SUPPORT,
      AppScreen.ACCOUNT_SETTINGS, AppScreen.SECURITY_PASSWORD -> currentScreen = AppScreen.ACCOUNT
      AppScreen.BUDGET_SHOPPING -> currentScreen = previousScreen
      AppScreen.VENDOR_CENTER, AppScreen.DRIVER_CENTER -> currentScreen = AppScreen.ACCOUNT
      AppScreen.DRIVER_NAVIGATION -> currentScreen = AppScreen.DRIVER_CENTER
      AppScreen.PUBLIC_SHOPS -> currentScreen = previousScreen
      else -> {
        if (currentScreen != AppScreen.HOME) {
          currentScreen = AppScreen.HOME
        }
      }
    }
  }

  BackHandler(enabled = currentScreen != AppScreen.HOME && currentScreen != AppScreen.SPLASH) {
    navigateBack()
  }

  val isBottomBarVisible = currentScreen in listOf(
    AppScreen.HOME,
    AppScreen.CATEGORIES,
    AppScreen.DISCOVER,
    AppScreen.WISHLIST,
    AppScreen.ACCOUNT
  )

  Scaffold(
    containerColor = Color.White,
    bottomBar = {
      if (isBottomBarVisible) {
        NavigationBar(
          containerColor = Color.White,
          tonalElevation = 6.dp,
          modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
        ) {
          // 1. Home
          NavigationBarItem(
            selected = currentScreen == AppScreen.HOME,
            onClick = { currentScreen = AppScreen.HOME },
            icon = {
              Icon(
                imageVector = if (currentScreen == AppScreen.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                contentDescription = "Home"
              )
            },
            label = {
              Text(
                text = "Home",
                fontSize = 10.sp,
                fontWeight = if (currentScreen == AppScreen.HOME) FontWeight.Bold else FontWeight.Normal
              )
            },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = JTOrange,
              selectedTextColor = JTOrangeDark,
              indicatorColor = JTOrangeContainer,
              unselectedIconColor = JTTextSecondary,
              unselectedTextColor = JTTextSecondary
            ),
            modifier = Modifier.testTag("nav_home")
          )

          // 2. Categories
          NavigationBarItem(
            selected = currentScreen == AppScreen.CATEGORIES,
            onClick = { currentScreen = AppScreen.CATEGORIES },
            icon = {
              Icon(
                imageVector = if (currentScreen == AppScreen.CATEGORIES) Icons.Filled.GridView else Icons.Outlined.GridView,
                contentDescription = "Categories"
              )
            },
            label = {
              Text(
                text = "Categories",
                fontSize = 10.sp,
                fontWeight = if (currentScreen == AppScreen.CATEGORIES) FontWeight.Bold else FontWeight.Normal
              )
            },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = JTOrange,
              selectedTextColor = JTOrangeDark,
              indicatorColor = JTOrangeContainer,
              unselectedIconColor = JTTextSecondary,
              unselectedTextColor = JTTextSecondary
            ),
            modifier = Modifier.testTag("nav_categories")
          )

          // 3. Discover
          NavigationBarItem(
            selected = currentScreen == AppScreen.DISCOVER,
            onClick = { currentScreen = AppScreen.DISCOVER },
            icon = {
              Icon(
                imageVector = if (currentScreen == AppScreen.DISCOVER) Icons.Filled.Explore else Icons.Outlined.Explore,
                contentDescription = "Discover"
              )
            },
            label = {
              Text(
                text = "Discover",
                fontSize = 10.sp,
                fontWeight = if (currentScreen == AppScreen.DISCOVER) FontWeight.Bold else FontWeight.Normal
              )
            },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = JTOrange,
              selectedTextColor = JTOrangeDark,
              indicatorColor = JTOrangeContainer,
              unselectedIconColor = JTTextSecondary,
              unselectedTextColor = JTTextSecondary
            ),
            modifier = Modifier.testTag("nav_discover")
          )

          // 4. Wishlist
          NavigationBarItem(
            selected = currentScreen == AppScreen.WISHLIST,
            onClick = { currentScreen = AppScreen.WISHLIST },
            icon = {
              Icon(
                imageVector = if (currentScreen == AppScreen.WISHLIST) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Wishlist"
              )
            },
            label = {
              Text(
                text = "Wishlist",
                fontSize = 10.sp,
                fontWeight = if (currentScreen == AppScreen.WISHLIST) FontWeight.Bold else FontWeight.Normal
              )
            },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = JTOrange,
              selectedTextColor = JTOrangeDark,
              indicatorColor = JTOrangeContainer,
              unselectedIconColor = JTTextSecondary,
              unselectedTextColor = JTTextSecondary
            ),
            modifier = Modifier.testTag("nav_wishlist")
          )

          // 5. Account
          NavigationBarItem(
            selected = currentScreen == AppScreen.ACCOUNT,
            onClick = { currentScreen = AppScreen.ACCOUNT },
            icon = {
              Icon(
                imageVector = if (currentScreen == AppScreen.ACCOUNT) Icons.Filled.Person else Icons.Outlined.Person,
                contentDescription = "Account"
              )
            },
            label = {
              Text(
                text = "Account",
                fontSize = 10.sp,
                fontWeight = if (currentScreen == AppScreen.ACCOUNT) FontWeight.Bold else FontWeight.Normal
              )
            },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = JTOrange,
              selectedTextColor = JTOrangeDark,
              indicatorColor = JTOrangeContainer,
              unselectedIconColor = JTTextSecondary,
              unselectedTextColor = JTTextSecondary
            ),
            modifier = Modifier.testTag("nav_account")
          )
        }
      }
    },
    modifier = Modifier.fillMaxSize()
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      JTBazarPullRefresh(
        isRefreshing = isPullRefreshing,
        enabled = currentScreen in setOf(
          AppScreen.HOME, AppScreen.CATEGORIES, AppScreen.CATEGORY_PRODUCTS,
          AppScreen.DISCOVER, AppScreen.WISHLIST, AppScreen.ACCOUNT,
          AppScreen.CART_CHECKOUT, AppScreen.ORDER_TRACKING, AppScreen.MY_ORDERS,
          AppScreen.NOTIFICATIONS, AppScreen.RETURNS_REFUNDS, AppScreen.PUBLIC_SHOPS,
          AppScreen.VENDOR_CENTER, AppScreen.DRIVER_CENTER
        ),
        onRefresh = {
          if (!isPullRefreshing) {
            pullRefreshScope.launch {
              isPullRefreshing = true
              try {
                MarketplaceRepository.refreshLiveCatalogSuspending(
                  showLoading = false,
                  refreshUserData = true
                )
                MarketplaceRepository.refreshShops()
              } finally {
                isPullRefreshing = false
              }
            }
          }
        },
        modifier = Modifier.fillMaxSize()
      ) {
      when (currentScreen) {
        AppScreen.SPLASH -> {
          SplashScreen(
            onFinish = { currentScreen = AppScreen.HOME }
          )
        }

        AppScreen.HOME -> {
          HomeScreen(
            onProductClick = { product ->
              selectedProduct = product
              MarketplaceRepository.selectProduct(product)
              navigateTo(AppScreen.PRODUCT_DETAIL)
            },
            onExploreCategoriesClick = { currentScreen = AppScreen.CATEGORIES },
            onCartClick = { navigateTo(AppScreen.CART_CHECKOUT) },
            onAccountClick = { currentScreen = AppScreen.ACCOUNT },
            onBudgetClick = { navigateTo(AppScreen.BUDGET_SHOPPING) },
            onExploreShopsClick = { navigateTo(AppScreen.PUBLIC_SHOPS) }
          )
        }

        AppScreen.CATEGORIES -> {
          CategoriesScreen(
            onCategorySelect = { category ->
              selectedCategory = category
              navigateTo(AppScreen.CATEGORY_PRODUCTS)
            },
            onCartClick = { navigateTo(AppScreen.CART_CHECKOUT) },
            onAccountClick = { currentScreen = AppScreen.ACCOUNT }
          )
        }

        AppScreen.CATEGORY_PRODUCTS -> {
          val category = selectedCategory
          if (category != null) {
            CategoryProductsScreen(
              category = category,
              onBack = { navigateBack() },
              onProductClick = { product ->
                selectedProduct = product
                MarketplaceRepository.selectProduct(product)
                navigateTo(AppScreen.PRODUCT_DETAIL)
              },
              onCartClick = { navigateTo(AppScreen.CART_CHECKOUT) }
            )
          } else {
            navigateBack()
          }
        }

        AppScreen.DISCOVER -> {
          DiscoverScreen(
            onProductClick = { product ->
              selectedProduct = product
              MarketplaceRepository.selectProduct(product)
              navigateTo(AppScreen.PRODUCT_DETAIL)
            },
            onCartClick = { navigateTo(AppScreen.CART_CHECKOUT) },
            onAccountClick = { currentScreen = AppScreen.ACCOUNT }
          )
        }

        AppScreen.WISHLIST -> {
          WishlistScreen(
            onProductClick = { product ->
              selectedProduct = product
              MarketplaceRepository.selectProduct(product)
              navigateTo(AppScreen.PRODUCT_DETAIL)
            },
            onCartClick = { navigateTo(AppScreen.CART_CHECKOUT) },
            onAccountClick = { currentScreen = AppScreen.ACCOUNT }
          )
        }

        AppScreen.ACCOUNT -> {
          AccountScreen(
            onNavigateToLogin = { navigateTo(AppScreen.LOGIN) },
            onNavigateToRegister = { navigateTo(AppScreen.REGISTER) },
            onNavigateToProfile = { navigateTo(AppScreen.MY_PROFILE) },
            onNavigateToOrders = { navigateTo(AppScreen.MY_ORDERS) },
            onNavigateToAddresses = { navigateTo(AppScreen.MY_ADDRESSES) },
            onNavigateToWishlist = { currentScreen = AppScreen.WISHLIST },
            onNavigateToNotifications = { navigateTo(AppScreen.NOTIFICATIONS) },
            onNavigateToReturns = { navigateTo(AppScreen.RETURNS_REFUNDS) },
            onNavigateToSupport = { navigateTo(AppScreen.CUSTOMER_SUPPORT) },
            onNavigateToSettings = { navigateTo(AppScreen.ACCOUNT_SETTINGS) },
            onNavigateToSecurity = { navigateTo(AppScreen.SECURITY_PASSWORD) },
            onNavigateToBudget = { navigateTo(AppScreen.BUDGET_SHOPPING) },
            onNavigateToVendorCenter = { navigateTo(AppScreen.VENDOR_CENTER) },
            onNavigateToDriver = { navigateTo(AppScreen.DRIVER_CENTER) },
            onCartClick = { navigateTo(AppScreen.CART_CHECKOUT) },
            scrollState = accountScrollState
          )
        }

        AppScreen.PRODUCT_DETAIL -> {
          val activeProd = selectedProduct ?: MarketplaceRepository.selectedProduct.collectAsState().value
          if (activeProd != null) {
            ProductDetailScreen(
              product = activeProd,
              onBackClick = { navigateBack() },
              onCartClick = { navigateTo(AppScreen.CART_CHECKOUT) },
              onBuyNowClick = { navigateTo(AppScreen.CART_CHECKOUT) },
              onAccountClick = { currentScreen = AppScreen.ACCOUNT },
              onVisitShop = { shopId ->
                selectedPublicShopId = shopId
                navigateTo(AppScreen.PUBLIC_SHOPS)
              }
            )
          } else {
            navigateBack()
          }
        }

        AppScreen.CART_CHECKOUT -> {
          CartCheckoutScreen(
            onBackClick = { navigateBack() },
            onOrderPlaced = { navigateTo(AppScreen.ORDER_TRACKING) },
            onAccountClick = { currentScreen = AppScreen.ACCOUNT }
          )
        }

        AppScreen.ORDER_TRACKING -> {
          OrderTrackingScreen(
            onBackClick = { navigateBack() },
            onContinueShopping = { currentScreen = AppScreen.HOME },
            onAccountClick = { currentScreen = AppScreen.ACCOUNT }
          )
        }

        // AUTHENTICATION SCREENS
        AppScreen.LOGIN -> {
          LoginScreen(
            onNavigateToRegister = { navigateTo(AppScreen.REGISTER) },
            onNavigateToForgotPassword = { navigateTo(AppScreen.FORGOT_PASSWORD) },
            onLoginOtpRequired = { email ->
              pendingAuthEmail = email
              navigateTo(AppScreen.LOGIN_OTP)
            },
            onLoginSuccess = { currentScreen = AppScreen.ACCOUNT },
            onBack = { navigateBack() }
          )
        }

        AppScreen.REGISTER -> {
          RegistrationScreen(
            onNavigateToLogin = { navigateTo(AppScreen.LOGIN) },
            onOtpRequired = { email, fullName ->
              pendingAuthEmail = email
              pendingFullName = fullName
              navigateTo(AppScreen.EMAIL_OTP)
            },
            onBack = { navigateBack() }
          )
        }

        AppScreen.EMAIL_OTP -> {
          EmailOtpVerificationScreen(
            email = pendingAuthEmail,
            fullName = pendingFullName,
            onVerified = { currentScreen = AppScreen.HOME },
            onBack = { navigateBack() }
          )
        }

        AppScreen.LOGIN_OTP -> {
          LoginOtpVerificationScreen(
            email = pendingAuthEmail,
            onVerified = { currentScreen = AppScreen.ACCOUNT },
            onBack = { navigateBack() }
          )
        }

        AppScreen.FORGOT_PASSWORD -> {
          ForgotPasswordScreen(
            onNavigateToLogin = { navigateTo(AppScreen.LOGIN) },
            onNavigateToResetPassword = { email ->
              pendingAuthEmail = email
              navigateTo(AppScreen.RESET_PASSWORD)
            },
            onBack = { navigateBack() }
          )
        }

        AppScreen.RESET_PASSWORD -> {
          ResetPasswordScreen(
            email = pendingAuthEmail,
            onResetSuccess = { navigateTo(AppScreen.LOGIN) },
            onBack = { navigateBack() }
          )
        }

        // DEDICATED ACCOUNT SUB-SCREENS
        AppScreen.MY_PROFILE -> {
          MyProfileScreen(onBack = { navigateBack() })
        }

        AppScreen.MY_ORDERS -> {
          MyOrdersScreen(
            onOrderClick = { ord -> navigateTo(AppScreen.ORDER_TRACKING) },
            onExploreProducts = { currentScreen = AppScreen.HOME },
            onBack = { navigateBack() }
          )
        }

        AppScreen.MY_ADDRESSES -> {
          MyAddressesScreen(onBack = { navigateBack() })
        }

        AppScreen.NOTIFICATIONS -> {
          NotificationsScreen(
            onBack = { navigateBack() },
            onNotificationClick = { notification ->
              when (notification.type.lowercase()) {
                "order" -> {
                  val order = notification.relatedOrderId?.let { relatedId ->
                    MarketplaceRepository.orders.value.firstOrNull { it.orderId == relatedId }
                  }
                  if (order != null) {
                    MarketplaceRepository.selectOrder(order)
                    navigateTo(AppScreen.ORDER_TRACKING)
                  } else {
                    navigateTo(AppScreen.MY_ORDERS)
                  }
                }
                "account" -> {
                  currentScreen = AppScreen.ACCOUNT
                }
                "store", "product", "promotion" -> {
                  currentScreen = AppScreen.HOME
                }
                else -> currentScreen = AppScreen.HOME
              }
            }
          )
        }

        AppScreen.RETURNS_REFUNDS -> {
          ReturnsRefundsScreen(onBack = { navigateBack() })
        }

        AppScreen.CUSTOMER_SUPPORT -> {
          CustomerSupportScreen(onBack = { navigateBack() })
        }

        AppScreen.ACCOUNT_SETTINGS -> {
          AccountSettingsScreen(
            onNavigateToSecurity = { navigateTo(AppScreen.SECURITY_PASSWORD) },
            onBack = { navigateBack() }
          )
        }

        AppScreen.SECURITY_PASSWORD -> {
          SecurityPasswordScreen(onBack = { navigateBack() })
        }

        AppScreen.PUBLIC_SHOPS -> {
          PublicShopsScreen(
            onBack = { selectedPublicShopId = null; navigateBack() },
            initialShopId = selectedPublicShopId,
            onProductClick = { product ->
              selectedProduct = product
              MarketplaceRepository.selectProduct(product)
              navigateTo(AppScreen.PRODUCT_DETAIL)
            }
          )
        }

        AppScreen.VENDOR_CENTER -> {
          VendorCenterScreen(onBack = { navigateBack() })
        }

        AppScreen.DRIVER_CENTER -> {
          DriverCenterScreen(
            onBack = { navigateBack() },
            onOpenProfile = { navigateTo(AppScreen.MY_PROFILE) },
            onOpenNotifications = { navigateTo(AppScreen.NOTIFICATIONS) },
            onViewCustomerLocation = { delivery ->
              selectedDeliveryForNavigation = delivery
              navigateTo(AppScreen.DRIVER_NAVIGATION)
            }
          )
        }

        AppScreen.DRIVER_NAVIGATION -> {
          val delivery = selectedDeliveryForNavigation
          if (delivery != null) {
            val lat = delivery.optDouble("delivery_lat", Double.NaN)
            val lng = delivery.optDouble("delivery_lng", Double.NaN)
            val orderNumber = delivery.optString("order_number")
            val customerName = delivery.optString("customer_name")
            val customerPhone = delivery.optString("customer_phone")
            val address = driverAddressText(delivery)
            val assignmentStatus = delivery.optString("assignment_status", "assigned")
            val assignmentId = delivery.optString("assignment_id")

            DriverNavigationScreen(
              orderNumber = orderNumber,
              customerName = customerName,
              customerPhone = customerPhone,
              deliveryAddress = address,
              customerLat = lat,
              customerLng = lng,
              assignmentStatus = assignmentStatus,
              assignmentId = assignmentId,
              onStatusUpdated = {
                // Return to Driver Center on delivery completion if desired or stay
              },
              onBack = { navigateBack() }
            )
          } else {
            navigateBack()
          }
        }

        AppScreen.BUDGET_SHOPPING -> {
          BudgetShoppingScreen(
            onProductClick = { product ->
              selectedProduct = product
              MarketplaceRepository.selectProduct(product)
              navigateTo(AppScreen.PRODUCT_DETAIL)
            },
            onCartClick = { navigateTo(AppScreen.CART_CHECKOUT) },
            onBack = { navigateBack() }
          )
        }
      }
      }

      val activeCampaignPopup by CampaignPopupManager.activePopupCampaign.collectAsState()
      if (activeCampaignPopup != null && currentScreen != AppScreen.SPLASH) {
        CampaignLaunchPopupDialog(
          campaign = activeCampaignPopup!!,
          onDismiss = { CampaignPopupManager.dismiss() }
        )
      }

      if (showGlobalLoader && currentScreen != AppScreen.SPLASH) {
        GlobalLoadingOverlay()
      }
    }
  }
}

@Composable
private fun GlobalLoadingOverlay() {
  val context = LocalContext.current
  val gifImageLoader = remember(context) {
    ImageLoader.Builder(context)
      .components { add(GifDecoder.Factory()) }
      .build()
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.White.copy(alpha = 0.96f))
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = {}
      ),
    contentAlignment = Alignment.Center
  ) {
    AsyncImage(
      model = R.drawable.jtbazar_mascot_loader,
      imageLoader = gifImageLoader,
      contentDescription = "JTBazar loading",
      contentScale = ContentScale.Fit,
      modifier = Modifier.size(190.dp)
    )
  }
}
