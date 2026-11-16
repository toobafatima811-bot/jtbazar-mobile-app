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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import coil.compose.AsyncImage
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
import android.content.Intent
import android.net.Uri
import com.example.R
import com.example.data.model.CategoryItem
import com.example.data.model.Product
import com.example.data.repository.BannerDestination
import com.example.data.repository.CampaignPopupManager
import com.example.data.repository.CatalogPaginator
import com.example.data.repository.MarketplaceRepository
import com.example.data.repository.PromotionCampaignFilter
import com.example.data.supabase.AppErrorMapper
import com.example.data.supabase.ErrorContext
import com.example.data.supabase.SupabaseApiService
import com.example.data.supabase.SupabaseAuthService
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.GlobalLoadingState
import com.example.data.supabase.NetworkConnectivity
import com.example.ui.components.CampaignLaunchPopupDialog
import com.example.ui.components.JTBazarImageLoader
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
  COLLECTION_PRODUCTS,
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
  val hasInternet by NetworkConnectivity.isOnline.collectAsState()
  val context = LocalContext.current
  DisposableEffect(context) {
    NetworkConnectivity.start(context)
    onDispose { NetworkConnectivity.stop(context) }
  }
  // Keep routine POST/RPC operations from covering the whole screen. Forms and
  // action buttons own their local progress indicators; only checkout and the
  // initial empty-catalog load use the blocking app-wide loader.
  val showGlobalLoader = hasInternet != false &&
    (isPlacingOrder || (isCatalogLoading && products.isEmpty()))
  val lifecycleOwner = LocalLifecycleOwner.current
  LaunchedEffect(lifecycleOwner) {
    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
      MarketplaceRepository.startRealtimeCampaignSync()
      MarketplaceRepository.refreshPromotionsSuspending()
      val promoPoller = launch {
        while (true) {
          delay(90_000L)
          MarketplaceRepository.refreshPromotionsSuspending()
        }
      }
      val catalogPoller = launch {
        while (true) {
          delay(180_000L)
          MarketplaceRepository.refreshLiveCatalogSuspending(showLoading = false, refreshUserData = false)
        }
      }
      try {
        promoPoller.join()
        catalogPoller.join()
      } finally {
        MarketplaceRepository.stopRealtimeCampaignSync()
      }
    }
  }

  val accountScrollState = rememberScrollState()
  val homeListState = rememberLazyListState()
  val categoryListStates = remember { mutableStateMapOf<String, LazyListState>() }
  val categoryPaginators = remember { mutableStateMapOf<String, CatalogPaginator>() }
  val recentCategoryIds = remember { mutableStateListOf<String>() }
  val budgetListState = rememberLazyListState()
  val snackbarHostState = remember { SnackbarHostState() }
  val repoError by MarketplaceRepository.errorMessage.collectAsState()
  LaunchedEffect(repoError) {
    val msg = repoError
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(
        message = AppErrorMapper.sanitizeMessage(msg, ErrorContext.GENERAL),
        duration = SnackbarDuration.Short
      )
      MarketplaceRepository.clearErrorMessage()
    }
  }

  var currentScreen by remember { mutableStateOf(AppScreen.SPLASH) }
  var isPullRefreshing by remember { mutableStateOf(false) }
  val pullRefreshScope = rememberCoroutineScope()
  val backStack = remember { mutableStateListOf<AppScreen>() }
  var selectedProduct by remember { mutableStateOf<Product?>(null) }
  var selectedPublicShopId by remember { mutableStateOf<String?>(null) }
  var selectedCategory by remember { mutableStateOf<CategoryItem?>(null) }
  var selectedCollectionTarget by remember { mutableStateOf<HomeCollectionTarget?>(null) }
  val collectionListStates = remember { mutableStateMapOf<String, LazyListState>() }
  val collectionPaginators = remember { mutableStateMapOf<String, CatalogPaginator>() }
  var selectedDeliveryForNavigation by remember { mutableStateOf<JSONObject?>(null) }

  LaunchedEffect(notificationTap?.id, notificationTap?.kind, notificationTap?.link) {
    val tap = notificationTap ?: return@LaunchedEffect
    if (!tap.id.isNullOrBlank()) MarketplaceRepository.markNotificationRead(tap.id)
    backStack.clear()
    selectedPublicShopId = null
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
      backStack.clear()
      selectedPublicShopId = null
      currentScreen = AppScreen.ACCOUNT
      SupabaseAuthService.clearGoogleOAuthStatus()
    }
  }

  fun navigateTo(screen: AppScreen) {
    if (currentScreen != screen && currentScreen != AppScreen.SPLASH) {
      backStack.add(currentScreen)
    }
    currentScreen = screen
  }

  fun switchRootTab(screen: AppScreen) {
    backStack.clear()
    selectedPublicShopId = null
    currentScreen = screen
  }

  fun popBack(fallback: AppScreen = AppScreen.HOME): AppScreen {
    return if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex) else fallback
  }

  fun navigateBack() {
    when (currentScreen) {
      AppScreen.PUBLIC_SHOPS -> {
        selectedPublicShopId = null
        currentScreen = popBack(AppScreen.HOME)
      }
      AppScreen.PRODUCT_DETAIL, AppScreen.BUDGET_SHOPPING, AppScreen.COLLECTION_PRODUCTS, AppScreen.CART_CHECKOUT, AppScreen.ORDER_TRACKING -> {
        currentScreen = popBack(AppScreen.HOME)
      }
      AppScreen.CATEGORY_PRODUCTS -> {
        currentScreen = popBack(AppScreen.CATEGORIES)
      }
      AppScreen.SPLASH -> {
        backStack.clear()
        currentScreen = AppScreen.HOME
      }
      AppScreen.LOGIN, AppScreen.REGISTER, AppScreen.FORGOT_PASSWORD -> {
        backStack.clear()
        currentScreen = AppScreen.ACCOUNT
      }
      AppScreen.EMAIL_OTP, AppScreen.LOGIN_OTP, AppScreen.RESET_PASSWORD -> {
        currentScreen = AppScreen.LOGIN
      }
      AppScreen.MY_PROFILE, AppScreen.NOTIFICATIONS, AppScreen.SECURITY_PASSWORD -> {
        currentScreen = popBack(AppScreen.ACCOUNT)
      }
      AppScreen.MY_ORDERS, AppScreen.MY_ADDRESSES, AppScreen.RETURNS_REFUNDS, AppScreen.CUSTOMER_SUPPORT,
      AppScreen.ACCOUNT_SETTINGS, AppScreen.VENDOR_CENTER, AppScreen.DRIVER_CENTER -> {
        backStack.clear()
        currentScreen = AppScreen.ACCOUNT
      }
      AppScreen.DRIVER_NAVIGATION -> {
        currentScreen = popBack(AppScreen.DRIVER_CENTER)
      }
      else -> {
        if (currentScreen != AppScreen.HOME) {
          backStack.clear()
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
    snackbarHost = {
      SnackbarHost(hostState = snackbarHostState) { data ->
        Snackbar(
          snackbarData = data,
          containerColor = JTNavyPrimary,
          contentColor = Color.White,
          actionColor = JTOrangeLight
        )
      }
    },
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
            onClick = { switchRootTab(AppScreen.HOME) },
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
            onClick = { switchRootTab(AppScreen.CATEGORIES) },
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
            onClick = { switchRootTab(AppScreen.DISCOVER) },
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
            onClick = { switchRootTab(AppScreen.WISHLIST) },
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
            onClick = { switchRootTab(AppScreen.ACCOUNT) },
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
      Column(modifier = Modifier.fillMaxSize()) {
        if (hasInternet == false) {
          Surface(
            modifier = Modifier.fillMaxWidth().testTag("offline_connection_banner"),
            color = Color(0xFFFFF1F0),
            shadowElevation = 4.dp
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Icon(Icons.Filled.WifiOff, contentDescription = null, tint = Color(0xFFB42318))
              Column(modifier = Modifier.weight(1f)) {
                Text("No internet connection", color = Color(0xFF912018), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Check your Wi-Fi or mobile data, then retry.", color = Color(0xFF7A271A), fontSize = 11.sp)
              }
              TextButton(
                onClick = {
                  pullRefreshScope.launch {
                    MarketplaceRepository.refreshLiveCatalogSuspending(showLoading = false, refreshUserData = true)
                    MarketplaceRepository.refreshShops()
                  }
                }
              ) {
                Text("Retry", color = Color(0xFFB42318), fontWeight = FontWeight.Bold)
              }
            }
          }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      JTBazarPullRefresh(
        isRefreshing = isPullRefreshing,
        enabled = currentScreen in setOf(
          AppScreen.HOME, AppScreen.CATEGORIES, AppScreen.CATEGORY_PRODUCTS,
          AppScreen.COLLECTION_PRODUCTS, AppScreen.DISCOVER, AppScreen.WISHLIST, AppScreen.ACCOUNT,
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
            listState = homeListState,
            onProductClick = { product ->
              selectedProduct = product
              MarketplaceRepository.selectProduct(product)
              navigateTo(AppScreen.PRODUCT_DETAIL)
            },
            onCategoryClick = { cat ->
              selectedCategory = cat
              navigateTo(AppScreen.CATEGORY_PRODUCTS)
            },
            onExploreCategoriesClick = { switchRootTab(AppScreen.CATEGORIES) },
            onCartClick = { navigateTo(AppScreen.CART_CHECKOUT) },
            onAccountClick = { switchRootTab(AppScreen.ACCOUNT) },
            onBudgetClick = { navigateTo(AppScreen.BUDGET_SHOPPING) },
            onExploreShopsClick = {
              selectedPublicShopId = null
              navigateTo(AppScreen.PUBLIC_SHOPS)
            },
            onShopClick = { shop ->
              selectedPublicShopId = shop.id
              navigateTo(AppScreen.PUBLIC_SHOPS)
            },
            onSeeAllDealsClick = { activeCampaign ->
              selectedCollectionTarget = HomeCollectionTarget.Deal(activeCampaign)
              navigateTo(AppScreen.COLLECTION_PRODUCTS)
            },
            onSeeAllPopularClick = { isFeaturedOnly ->
              selectedCollectionTarget = HomeCollectionTarget.Popular(isFeaturedOnly)
              navigateTo(AppScreen.COLLECTION_PRODUCTS)
            },
            onSeeAllNewArrivalsClick = {
              selectedCollectionTarget = HomeCollectionTarget.NewArrivals
              navigateTo(AppScreen.COLLECTION_PRODUCTS)
            },
            onBannerDestinationClick = { rawDestination ->
              when (val dest = PromotionCampaignFilter.resolveDestination(rawDestination)) {
                is BannerDestination.Home -> switchRootTab(AppScreen.HOME)
                is BannerDestination.Categories -> switchRootTab(AppScreen.CATEGORIES)
                is BannerDestination.CategoryByQuery -> {
                  val q = dest.query.trim().lowercase().replace('-', ' ')
                  val matched = MarketplaceRepository.categories.value.firstOrNull { cat ->
                    cat.id.equals(dest.query, ignoreCase = true) ||
                      cat.title.trim().lowercase() == q ||
                      cat.title.trim().lowercase().replace(' ', '-') == dest.query.trim().lowercase() ||
                      cat.slug.equals(dest.query.trim(), ignoreCase = true)
                  }
                  if (matched != null) {
                    selectedCategory = matched
                    navigateTo(AppScreen.CATEGORY_PRODUCTS)
                  } else {
                    switchRootTab(AppScreen.CATEGORIES)
                  }
                }
                is BannerDestination.ProductById -> {
                  val matched = products.firstOrNull { p ->
                    p.id.equals(dest.productId, ignoreCase = true) ||
                      p.title.trim().lowercase().replace(' ', '-') == dest.productId.trim().lowercase()
                  }
                  if (matched != null) {
                    selectedProduct = matched
                    MarketplaceRepository.selectProduct(matched)
                    navigateTo(AppScreen.PRODUCT_DETAIL)
                  } else {
                    pullRefreshScope.launch {
                      SupabaseApiService.fetchProductById(dest.productId)
                        .onSuccess { dto ->
                          val mapped = MarketplaceRepository.mapDtoToProduct(dto)
                          selectedProduct = mapped
                          MarketplaceRepository.selectProduct(mapped)
                          navigateTo(AppScreen.PRODUCT_DETAIL)
                        }
                        .onFailure {
                          switchRootTab(AppScreen.CATEGORIES)
                        }
                    }
                  }
                }
                is BannerDestination.ShopsList -> {
                  selectedPublicShopId = null
                  navigateTo(AppScreen.PUBLIC_SHOPS)
                }
                is BannerDestination.ShopByQuery -> {
                  val q = dest.query.trim().lowercase().replace('-', ' ')
                  val matched = MarketplaceRepository.shops.value.firstOrNull { s ->
                    s.id.equals(dest.query, ignoreCase = true) ||
                      s.name.trim().lowercase() == q ||
                      s.name.trim().lowercase().replace(' ', '-') == dest.query.trim().lowercase()
                  }
                  selectedPublicShopId = matched?.id
                  navigateTo(AppScreen.PUBLIC_SHOPS)
                }
                is BannerDestination.BudgetShopping -> navigateTo(AppScreen.BUDGET_SHOPPING)
                is BannerDestination.Discover -> switchRootTab(AppScreen.DISCOVER)
                is BannerDestination.Wishlist -> switchRootTab(AppScreen.WISHLIST)
                is BannerDestination.CartCheckout -> navigateTo(AppScreen.CART_CHECKOUT)
                is BannerDestination.VendorCenter -> {
                  if (SupabaseClient.isAuthenticated) navigateTo(AppScreen.VENDOR_CENTER)
                  else switchRootTab(AppScreen.ACCOUNT)
                }
                is BannerDestination.Account -> switchRootTab(AppScreen.ACCOUNT)
                is BannerDestination.ExternalUrl -> {
                  runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(dest.url))) }
                }
                null -> Unit
              }
            }
          )
        }

        AppScreen.CATEGORIES -> {
          CategoriesScreen(
            onCategorySelect = { category ->
              selectedCategory = category
              navigateTo(AppScreen.CATEGORY_PRODUCTS)
            },
            onCartClick = { navigateTo(AppScreen.CART_CHECKOUT) },
            onAccountClick = { switchRootTab(AppScreen.ACCOUNT) }
          )
        }

        AppScreen.CATEGORY_PRODUCTS -> {
          val category = selectedCategory
          if (category != null) {
            LaunchedEffect(category.id) {
              recentCategoryIds.remove(category.id)
              recentCategoryIds.add(category.id)
              while (recentCategoryIds.size > 4) {
                val evictedId = recentCategoryIds.removeAt(0)
                categoryPaginators.remove(evictedId)?.clear()
                categoryListStates.remove(evictedId)
              }
            }
            val catScrollState = categoryListStates.getOrPut(category.id) { LazyListState() }
            val catPaginator = categoryPaginators.getOrPut(category.id) {
              MarketplaceRepository.createCatalogPaginator(pullRefreshScope, ErrorContext.PRODUCT_LOAD)
            }
            CategoryProductsScreen(
              category = category,
              listState = catScrollState,
              paginator = catPaginator,
              onBack = { navigateBack() },
              onProductClick = { product ->
                selectedProduct = product
                MarketplaceRepository.selectProduct(product)
                navigateTo(AppScreen.PRODUCT_DETAIL)
              },
              onCartClick = { navigateTo(AppScreen.CART_CHECKOUT) }
            )
          } else {
            LaunchedEffect(Unit) { navigateBack() }
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
            onAccountClick = { switchRootTab(AppScreen.ACCOUNT) },
            onOpenShop = { shopId ->
              selectedPublicShopId = shopId
              navigateTo(AppScreen.PUBLIC_SHOPS)
            }
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
            onAccountClick = { switchRootTab(AppScreen.ACCOUNT) }
          )
        }

        AppScreen.ACCOUNT -> {
          AccountScreen(
            onNavigateToLogin = { navigateTo(AppScreen.LOGIN) },
            onNavigateToRegister = { navigateTo(AppScreen.REGISTER) },
            onNavigateToProfile = { navigateTo(AppScreen.MY_PROFILE) },
            onNavigateToOrders = { navigateTo(AppScreen.MY_ORDERS) },
            onNavigateToAddresses = { navigateTo(AppScreen.MY_ADDRESSES) },
            onNavigateToWishlist = { switchRootTab(AppScreen.WISHLIST) },
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
              onAccountClick = { switchRootTab(AppScreen.ACCOUNT) },
              onVisitShop = { shopId ->
                selectedPublicShopId = shopId
                navigateTo(AppScreen.PUBLIC_SHOPS)
              }
            )
          } else {
            LaunchedEffect(Unit) { navigateBack() }
          }
        }

        AppScreen.CART_CHECKOUT -> {
          CartCheckoutScreen(
            onBackClick = { navigateBack() },
            onOrderPlaced = {
              backStack.clear()
              currentScreen = AppScreen.ORDER_TRACKING
            },
            onAccountClick = { switchRootTab(AppScreen.ACCOUNT) }
          )
        }

        AppScreen.ORDER_TRACKING -> {
          OrderTrackingScreen(
            onBackClick = { navigateBack() },
            onContinueShopping = { switchRootTab(AppScreen.HOME) },
            onAccountClick = { switchRootTab(AppScreen.ACCOUNT) }
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
            onLoginSuccess = { switchRootTab(AppScreen.ACCOUNT) },
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
            onVerified = { switchRootTab(AppScreen.HOME) },
            onBack = { navigateBack() }
          )
        }

        AppScreen.LOGIN_OTP -> {
          LoginOtpVerificationScreen(
            email = pendingAuthEmail,
            onVerified = { switchRootTab(AppScreen.ACCOUNT) },
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
            onExploreProducts = { switchRootTab(AppScreen.HOME) },
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
                  switchRootTab(AppScreen.ACCOUNT)
                }
                "store", "product", "promotion" -> {
                  switchRootTab(AppScreen.HOME)
                }
                else -> switchRootTab(AppScreen.HOME)
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
            onBack = { navigateBack() },
            initialShopId = selectedPublicShopId,
            onShopSelected = { selectedPublicShopId = it },
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
            LaunchedEffect(Unit) { navigateBack() }
          }
        }

        AppScreen.BUDGET_SHOPPING -> {
          BudgetShoppingScreen(
            listState = budgetListState,
            onProductClick = { product ->
              selectedProduct = product
              MarketplaceRepository.selectProduct(product)
              navigateTo(AppScreen.PRODUCT_DETAIL)
            },
            onCartClick = { navigateTo(AppScreen.CART_CHECKOUT) },
            onBack = { navigateBack() }
          )
        }

        AppScreen.COLLECTION_PRODUCTS -> {
          val target = selectedCollectionTarget
          if (target != null) {
            val cacheKey = when (target) {
              is HomeCollectionTarget.Deal -> "${target.key}_${target.activeCampaign.campaign.productIds.hashCode()}"
              is HomeCollectionTarget.Popular,
              is HomeCollectionTarget.NewArrivals -> target.key
            }
            LaunchedEffect(cacheKey) {
              if (collectionPaginators.size > 4) {
                val oldestKey = collectionPaginators.keys.firstOrNull { it != cacheKey }
                if (oldestKey != null) {
                  collectionPaginators.remove(oldestKey)?.clear()
                  collectionListStates.remove(oldestKey)
                }
              }
            }
            val colScrollState = collectionListStates.getOrPut(cacheKey) { LazyListState() }
            val colPaginator = collectionPaginators.getOrPut(cacheKey) {
              when (target) {
                is HomeCollectionTarget.Deal -> MarketplaceRepository.createCampaignPaginator(pullRefreshScope, target.activeCampaign)
                is HomeCollectionTarget.Popular,
                is HomeCollectionTarget.NewArrivals -> MarketplaceRepository.createCatalogPaginator(pullRefreshScope, ErrorContext.PRODUCT_LOAD)
              }
            }
            CollectionProductsScreen(
              target = target,
              listState = colScrollState,
              paginatorOverride = colPaginator,
              onBack = { navigateBack() },
              onProductClick = { product ->
                selectedProduct = product
                MarketplaceRepository.selectProduct(product)
                navigateTo(AppScreen.PRODUCT_DETAIL)
              },
              onCartClick = { navigateTo(AppScreen.CART_CHECKOUT) }
            )
          } else {
            LaunchedEffect(Unit) { navigateBack() }
          }
        }
      }
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
  val gifImageLoader = remember(context) { JTBazarImageLoader.get(context) }

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
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      AsyncImage(
        model = R.drawable.jtbazar_mascot_loader,
        imageLoader = gifImageLoader,
        contentDescription = "JTBazar loading",
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(160.dp)
      )
      CircularProgressIndicator(
        modifier = Modifier.size(34.dp),
        color = JTOrange,
        strokeWidth = 3.dp
      )
      Text("Please wait, loading...", color = JTTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
  }
}
