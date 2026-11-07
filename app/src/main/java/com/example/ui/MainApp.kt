package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CategoryItem
import com.example.data.model.Product
import com.example.data.repository.MarketplaceRepository
import com.example.ui.screens.*
import com.example.ui.theme.*

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
  ORDER_TRACKING
}

@Composable
fun MainApp() {
  // Start the live Supabase catalog fetch as soon as the app opens.
  // Without this call, the product/category StateFlows stay empty forever.
  LaunchedEffect(Unit) {
    MarketplaceRepository.refreshLiveCatalog()
  }

  var currentScreen by remember { mutableStateOf(AppScreen.SPLASH) }
  var previousScreen by remember { mutableStateOf(AppScreen.HOME) }
  var selectedProduct by remember { mutableStateOf<Product?>(null) }
  var selectedCategory by remember { mutableStateOf<CategoryItem?>(null) }

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
      else -> {
        if (currentScreen != AppScreen.HOME) {
          currentScreen = AppScreen.HOME
        }
      }
    }
  }

  // Handle system back button gracefully
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
      when (currentScreen) {
        AppScreen.SPLASH -> {
          SplashScreen(
            onFinish = {
              currentScreen = AppScreen.HOME
            }
          )
        }

        AppScreen.HOME -> {
          HomeScreen(
            onProductClick = { product ->
              selectedProduct = product
              MarketplaceRepository.selectProduct(product)
              navigateTo(AppScreen.PRODUCT_DETAIL)
            },
            onExploreCategoriesClick = {
              currentScreen = AppScreen.CATEGORIES
            },
            onCartClick = {
              navigateTo(AppScreen.CART_CHECKOUT)
            },
            onAccountClick = {
              currentScreen = AppScreen.ACCOUNT
            }
          )
        }

        AppScreen.CATEGORIES -> {
          CategoriesScreen(
            onCategorySelect = { category ->
              selectedCategory = category
              navigateTo(AppScreen.CATEGORY_PRODUCTS)
            },
            onCartClick = {
              navigateTo(AppScreen.CART_CHECKOUT)
            },
            onAccountClick = {
              currentScreen = AppScreen.ACCOUNT
            }
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
            onCartClick = {
              navigateTo(AppScreen.CART_CHECKOUT)
            },
            onAccountClick = {
              currentScreen = AppScreen.ACCOUNT
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
            onCartClick = {
              navigateTo(AppScreen.CART_CHECKOUT)
            },
            onAccountClick = {
              currentScreen = AppScreen.ACCOUNT
            }
          )
        }

        AppScreen.ACCOUNT -> {
          AccountScreen(
            onViewOrderTracking = {
              navigateTo(AppScreen.ORDER_TRACKING)
            },
            onCartClick = {
              navigateTo(AppScreen.CART_CHECKOUT)
            },
            onShowSplash = {
              navigateTo(AppScreen.SPLASH)
            }
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
              onAccountClick = { currentScreen = AppScreen.ACCOUNT }
            )
          } else {
            navigateBack()
          }
        }

        AppScreen.CART_CHECKOUT -> {
          CartCheckoutScreen(
            onBackClick = { navigateBack() },
            onOrderPlaced = {
              navigateTo(AppScreen.ORDER_TRACKING)
            },
            onAccountClick = { currentScreen = AppScreen.ACCOUNT }
          )
        }

        AppScreen.ORDER_TRACKING -> {
          OrderTrackingScreen(
            onBackClick = { navigateBack() },
            onContinueShopping = {
              currentScreen = AppScreen.HOME
            },
            onAccountClick = { currentScreen = AppScreen.ACCOUNT }
          )
        }
      }
    }
  }
}
