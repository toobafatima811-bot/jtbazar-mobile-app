package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import coil.compose.AsyncImage
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalContext
import com.example.R
import com.example.data.model.CategoryItem
import com.example.data.model.Product
import com.example.data.model.ShopRecord
import com.example.data.repository.CatalogQuery
import com.example.data.repository.FoodCategoryResolver
import com.example.data.repository.MarketplaceRepository
import com.example.data.repository.ActiveProductCampaign
import com.example.data.repository.BannerPlacement
import com.example.data.repository.PromotionCampaignFilter
import com.example.ui.components.AddressSelectionSheet
import com.example.ui.components.FriendlyEmptyState
import com.example.ui.components.FriendlyErrorState
import com.example.ui.components.JTBazarTopBar
import com.example.ui.components.MarketplaceHero
import com.example.ui.components.PaginationFooter
import com.example.ui.components.ProductSkeletonGrid
import com.example.ui.components.PromotionBannerSection
import com.example.ui.components.rememberAdaptiveColumnCount
import com.example.ui.components.rememberSizedImageRequest
import com.example.ui.components.TrustBadgesRow
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
  listState: LazyListState = rememberLazyListState(),
  onProductClick: (Product) -> Unit,
  onCategoryClick: (CategoryItem) -> Unit = { _ -> },
  onExploreCategoriesClick: () -> Unit,
  onCartClick: () -> Unit,
  onAccountClick: () -> Unit,
  onBudgetClick: () -> Unit = {},
  onExploreShopsClick: () -> Unit = {},
  onBannerDestinationClick: ((String?) -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val screenContext = LocalContext.current
  val productGridColumns = rememberAdaptiveColumnCount(minCellWidth = 138.dp, horizontalPadding = 32.dp, spacing = 12.dp, maxColumns = 4)
  val products by MarketplaceRepository.products.collectAsState()
  val trustedShops by MarketplaceRepository.shops.collectAsState()
  val categories by MarketplaceRepository.categories.collectAsState()
  val homepageHeroes by MarketplaceRepository.homepageHeroes.collectAsState()
  val homepagePromotions by MarketplaceRepository.homepagePromotions.collectAsState()
  val activeProductCampaigns by MarketplaceRepository.activeProductCampaigns.collectAsState()
  val isLoading by MarketplaceRepository.isLoading.collectAsState()
  val cartCount by MarketplaceRepository.cartCount.collectAsState()
  val currentLocation by MarketplaceRepository.currentLocation.collectAsState()
  val addresses by MarketplaceRepository.addresses.collectAsState()
  val selectedAddress by MarketplaceRepository.selectedAddress.collectAsState()

  val promotionsNowMillis by produceState(initialValue = System.currentTimeMillis()) {
    while (true) {
      delay(15_000L)
      value = System.currentTimeMillis()
    }
  }

  val handleBannerClick: (String?) -> Unit = { rawDest ->
    if (onBannerDestinationClick != null) {
      onBannerDestinationClick(rawDest)
    } else if (!rawDest.isNullOrBlank()) {
      runCatching {
        val uri = if (rawDest.startsWith("http")) Uri.parse(rawDest)
        else Uri.parse("https://jtbazar.vercel.app" + if (rawDest.startsWith("/")) rawDest else "/$rawDest")
        screenContext.startActivity(Intent(Intent.ACTION_VIEW, uri))
      }
    }
  }

  fun promotionsFor(placement: BannerPlacement) =
    PromotionCampaignFilter.filterForPlacement(homepagePromotions, placement, promotionsNowMillis)

  var showAddressDialog by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { MarketplaceRepository.refreshShops() }
  var searchQuery by remember { mutableStateOf("") }
  val homePaginator = MarketplaceRepository.homePaginator
  val feedState by homePaginator.state.collectAsState()

  LaunchedEffect(searchQuery) {
    val trimmed = searchQuery.trim()
    if (trimmed.isNotEmpty()) {
      delay(350L)
      homePaginator.resetAndLoad(CatalogQuery(searchQuery = trimmed))
    } else if (!feedState.hasLoadedOnce || feedState.query.searchQuery.isNotBlank()) {
      homePaginator.resetAndLoad(CatalogQuery())
    }
  }

  val shouldLoadMore by remember {
    derivedStateOf {
      val layoutInfo = listState.layoutInfo
      val totalItems = layoutInfo.totalItemsCount
      val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      totalItems > 0 && lastVisible >= totalItems - 4
    }
  }
  LaunchedEffect(shouldLoadMore, feedState.hasMore, feedState.isLoadingMore, feedState.isInitialLoading, feedState.paginationError) {
    if (shouldLoadMore &&
      !feedState.isInitialLoading &&
      !feedState.isLoadingMore &&
      feedState.hasMore &&
      feedState.paginationError == null
    ) {
      homePaginator.loadNextPage()
    }
  }

  val voiceSearchLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    if (result.resultCode == android.app.Activity.RESULT_OK) {
      val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
      if (spoken.isNotBlank()) { searchQuery = spoken }
    }
  }

  if (showAddressDialog) {
    AddressSelectionSheet(
      addresses = addresses,
      selectedAddress = selectedAddress,
      onSelect = {
        MarketplaceRepository.selectAddress(it)
        showAddressDialog = false
      },
      onDismiss = { showAddressDialog = false },
      onAddNewAddress = { MarketplaceRepository.addOrUpdateAddress(it) }
    )
  }

  var sellerStatus by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(Unit) {
    runCatching {
      val app = com.example.data.supabase.VendorService.getOwnApplication()
      sellerStatus = app?.status
    }
  }

  Scaffold(
    containerColor = Color(0xFFFCFAF7),
    topBar = {
      JTBazarTopBar(
        onCartClick = onCartClick,
        onAvatarClick = onAccountClick,
        onLocationClick = { showAddressDialog = true },
        cartCount = cartCount,
        currentLocation = currentLocation,
        showLocationText = false
      )
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .background(Color(0xFFFCFAF7))
    ) {
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("home_lazy_column"),
        contentPadding = PaddingValues(bottom = 28.dp)
      ) {
      item {
        PromotionBannerSection(
          placement = BannerPlacement.TOP_PROMO,
          promotions = promotionsFor(BannerPlacement.TOP_PROMO),
          onDestinationClick = handleBannerClick
        )
      }
      // 1. Search Bar with voice search
      item(key = "home_search_bar") {
        SearchInputBar(
          query = searchQuery,
          onQueryChange = { searchQuery = it },
          onVoiceSearch = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
              putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
              putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-PK")
              putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a product name")
            }
            runCatching { voiceSearchLauncher.launch(intent) }
          },
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
        )
      }

      if (searchQuery.isBlank()) {

        // Admin-managed hero banner placement in the hero area
        item {
          PromotionBannerSection(
            placement = BannerPlacement.HERO_BANNER,
            promotions = promotionsFor(BannerPlacement.HERO_BANNER),
            onDestinationClick = handleBannerClick
          )
        }

        // Website Parity: Full Homepage Hero Section
        item {
          val heroProduct = products.firstOrNull { !it.imageUrl.isNullOrBlank() }
          val heroShop = trustedShops.firstOrNull { !it.logoUrl.isNullOrBlank() }
          MarketplaceHero(
            heroes = homepageHeroes,
            featuredProduct = heroProduct,
            featuredShop = heroShop,
            sellerStatus = sellerStatus,
            onPrimaryCtaClick = { dest ->
              when {
                dest.isNullOrBlank() || dest.startsWith("/shop") -> onExploreCategoriesClick()
                dest.startsWith("/vendor") -> onAccountClick()
                else -> onExploreCategoriesClick()
              }
            },
            onSecondaryCtaClick = { _ ->
              onAccountClick()
            },
            onProductClick = onProductClick,
            onShopClick = { _ -> onExploreShopsClick() }
          )
        }

        item {
          OutlinedButton(
            onClick = onExploreShopsClick,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
          ) {
            Icon(Icons.Filled.Storefront, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Browse Trusted Shops")
          }
        }
      }

      // 4. Shop by Category (Horizontal Row)
      if (searchQuery.isBlank()) {
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Shop by Category",
              fontWeight = FontWeight.Bold,
              fontSize = 17.sp,
              color = JTTextPrimary
            )
            Text(
              text = "Explore All",
              fontWeight = FontWeight.SemiBold,
              fontSize = 13.sp,
              color = JTPurplePrimary,
              modifier = Modifier
                .clickable { onExploreCategoriesClick() }
                .testTag("explore_all_categories_button")
                .padding(4.dp)
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          if (categories.isEmpty()) {
            Text(
              text = "No active collections available",
              color = JTTextSecondary,
              fontSize = 12.sp,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
          } else {
            LazyRow(
              contentPadding = PaddingValues(horizontal = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              items(categories, key = { it.id }) { category ->
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier
                    .width(76.dp)
                    .clickable { onCategoryClick(category) }
                    .testTag("home_category_chip_${category.id}")
                ) {
                  CategoryVisualThumbnail(
                    category = category,
                    allCategories = categories,
                    modifier = Modifier
                      .size(58.dp)
                      .clip(RoundedCornerShape(16.dp))
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = category.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = JTTextPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 13.sp,
                    maxLines = 2
                  )
                  if (!category.badge.isNullOrBlank()) {
                    Text(category.badge, fontSize = 9.sp, color = JTTextSecondary)
                  }
                }
              }
            }
          }
        }
      }

      }

      // 4b. Category Feature Banner connected directly with Shop by Category
      if (searchQuery.isBlank()) {
        item {
          PromotionBannerSection(
            placement = BannerPlacement.CATEGORY_FEATURE,
            promotions = promotionsFor(BannerPlacement.CATEGORY_FEATURE),
            onDestinationClick = handleBannerClick
          )
        }
      }

      // Become a Seller Banner + Approved vendor shops on the home page.
      if (searchQuery.isBlank()) {
        item {
          PromotionBannerSection(
            placement = BannerPlacement.SELLER_BANNER,
            promotions = promotionsFor(BannerPlacement.SELLER_BANNER),
            onDestinationClick = handleBannerClick
          )
        }
        item {
          Column(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Storefront, contentDescription = null, tint = JTOrange, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(7.dp))
                Text("Trusted Vendor Shops", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = JTTextPrimary)
              }
              Text(
                "View All",
                color = JTPurplePrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onExploreShopsClick() }.padding(4.dp)
              )
            }
            if (trustedShops.isEmpty()) {
              Text(
                "Trusted shops will appear here when vendors are approved.",
                fontSize = 12.sp,
                color = JTTextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
              )
            } else {
              LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                items(trustedShops, key = { it.id }) { shop ->
                  Card(
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.width(148.dp).clickable { onExploreShopsClick() }
                  ) {
                    Column(
                      modifier = Modifier.fillMaxWidth().padding(10.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                      verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                      if (!shop.logoUrl.isNullOrBlank()) {
                        AsyncImage(
                          model = shop.logoUrl,
                          contentDescription = shop.name,
                          contentScale = ContentScale.Crop,
                          modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                        )
                      } else {
                        Box(
                          modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(JTSurfaceVariant),
                          contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Storefront, contentDescription = null, tint = JTTextSecondary, modifier = Modifier.size(30.dp)) }
                      }
                      Text(shop.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                      Text(
                        "${shop.productCount} products",
                        fontSize = 10.sp,
                        color = JTTextSecondary
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }


      // Active product deals synced from the same Supabase campaign RPC as the website.
      if (searchQuery.isBlank() && activeProductCampaigns.isNotEmpty()) {
        items(activeProductCampaigns, key = { "deal_${it.campaign.id}" }) { activeCampaign ->
          ProductCampaignSection(
            activeCampaign = activeCampaign,
            productGridColumns = productGridColumns,
            onProductClick = onProductClick,
            onAddToCart = { MarketplaceRepository.addToCart(it) },
            onToggleWishlist = { productId -> MarketplaceRepository.toggleWishlist(productId) }
          )
        }
      }

      if (searchQuery.isBlank()) {
        item {
          PromotionBannerSection(
            placement = BannerPlacement.ABOVE_POPULAR_PRODUCTS,
            promotions = promotionsFor(BannerPlacement.ABOVE_POPULAR_PRODUCTS),
            onDestinationClick = handleBannerClick
          )
        }
      }

      if (searchQuery.isNotBlank()) {
        item(key = "search_header") {
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = "Search Results",
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp,
              color = JTTextPrimary
            )
            TextButton(onClick = { searchQuery = "" }) {
              Text("Clear", color = JTOrange, fontWeight = FontWeight.SemiBold)
            }
          }
        }

        when {
          feedState.isInitialLoading && feedState.items.isEmpty() -> {
            item(key = "search_skeleton") {
              ProductSkeletonGrid(columns = productGridColumns, rows = 3)
            }
          }
          feedState.initialError != null && feedState.items.isEmpty() -> {
            item(key = "search_error") {
              FriendlyErrorState(
                title = "Search failed",
                message = feedState.initialError ?: "Products load nahi ho sake. Please try again.",
                onRetry = { homePaginator.retry() }
              )
            }
          }
          feedState.items.isEmpty() -> {
            item(key = "search_empty") {
              FriendlyEmptyState(
                title = "No matching products found",
                message = "Try searching with another product name or browse our categories.",
                actionLabel = "Clear Search",
                onAction = { searchQuery = "" }
              )
            }
          }
          else -> {
            val chunkedSearch = feedState.items.chunked(productGridColumns)
            items(
              items = chunkedSearch,
              key = { row -> "search_${row.firstOrNull()?.id ?: row.hashCode()}" }
            ) { rowItems ->
              Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                ProductGridRow(
                  rowItems = rowItems,
                  columns = productGridColumns,
                  onProductClick = onProductClick,
                  onAddToCart = { MarketplaceRepository.addToCart(it) },
                  onToggleWishlist = { MarketplaceRepository.toggleWishlist(it.id, it) }
                )
              }
            }
            item(key = "search_pagination_footer") {
              PaginationFooter(
                isLoadingMore = feedState.isLoadingMore,
                paginationError = feedState.paginationError,
                hasMore = feedState.hasMore,
                itemCount = feedState.items.size,
                onRetry = { homePaginator.retry() }
              )
            }
          }
        }
      } else {
        // 5. Popular Products Section Header
        item(key = "popular_header") {
          Spacer(modifier = Modifier.height(18.dp))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(28.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(JTRedContainer),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.LocalFireDepartment,
                  contentDescription = null,
                  tint = JTRed,
                  modifier = Modifier.size(18.dp)
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Popular Products",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = JTTextPrimary
              )
            }

            Text(
              text = "View All >",
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
              color = JTPurplePrimary,
              modifier = Modifier
                .clickable { onExploreCategoriesClick() }
                .padding(4.dp)
            )
          }
        }

        // 6. Grid of Popular Products (first 4 items)
        item(key = "popular_grid") {
          Spacer(modifier = Modifier.height(10.dp))
          val homeProducts = products.filter { it.isFeatured }.ifEmpty { products }
          val displayProducts = homeProducts.take(4)

          when {
            isLoading && products.isEmpty() -> {
              ProductSkeletonGrid(columns = productGridColumns, rows = 2)
            }
            displayProducts.isEmpty() -> {
              FriendlyErrorState(
                title = "Products load nahi ho sake",
                message = "Please check your internet connection and tap retry.",
                actionLabel = "Refresh Catalog",
                onRetry = { MarketplaceRepository.refreshLiveCatalog() }
              )
            }
            else -> {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                displayProducts.chunked(productGridColumns).forEach { rowItems ->
                  ProductGridRow(
                    rowItems = rowItems,
                    columns = productGridColumns,
                    onProductClick = onProductClick,
                    onAddToCart = { MarketplaceRepository.addToCart(it) },
                    onToggleWishlist = { MarketplaceRepository.toggleWishlist(it.id, it) }
                  )
                }
              }
            }
          }
        }

        item(key = "product_strip_banner") {
          PromotionBannerSection(
            placement = BannerPlacement.PRODUCT_STRIP,
            promotions = promotionsFor(BannerPlacement.PRODUCT_STRIP),
            onDestinationClick = handleBannerClick
          )
        }
      }

      // New arrivals: only shown when not actively searching.
      if (searchQuery.isBlank()) {
        item(key = "new_arrivals_section") {
          val newArrivals = products.take(4)
          Column(modifier = Modifier.fillMaxWidth().padding(top = 22.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text("NEW ARRIVALS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = JTOrangeDark)
                Text("Fresh additions to the store.", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary)
                Text("Recently added live listings.", fontSize = 11.sp, color = JTTextSecondary)
              }
              Text(
                "Browse catalog  →",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = JTOrangeDark,
                modifier = Modifier.clickable { onExploreCategoriesClick() }.padding(4.dp)
              )
            }
            Spacer(Modifier.height(10.dp))
            if (newArrivals.isNotEmpty()) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                newArrivals.chunked(productGridColumns).forEach { rowItems ->
                  ProductGridRow(
                    rowItems = rowItems,
                    columns = productGridColumns,
                    onProductClick = onProductClick,
                    onAddToCart = { MarketplaceRepository.addToCart(it) },
                    onToggleWishlist = { MarketplaceRepository.toggleWishlist(it.id, it) }
                  )
                }
              }
            }
          }
        }

        item(key = "below_new_arrivals_banners") {
          PromotionBannerSection(
            placement = BannerPlacement.BELOW_NEW_ARRIVALS,
            promotions = promotionsFor(BannerPlacement.BELOW_NEW_ARRIVALS),
            onDestinationClick = handleBannerClick
          )
        }
        item(key = "offer_section_banners") {
          PromotionBannerSection(
            placement = BannerPlacement.OFFER_SECTION,
            promotions = promotionsFor(BannerPlacement.OFFER_SECTION),
            onDestinationClick = handleBannerClick
          )
        }

        // 8. Trust Badges
        item(key = "trust_badges") {
          Spacer(modifier = Modifier.height(8.dp))
          TrustBadgesRow()
        }

        // 9. Budget Shopping Callout Banner
        item(key = "budget_banner") {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFFEF3C7),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 8.dp)
              .clickable { onBudgetClick() }
              .testTag("home_budget_banner")
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(42.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(Color(0xFFFDE68A)),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = JTAmberDark, modifier = Modifier.size(24.dp))
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text("Budget Shopping Corner", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF92400E))
                Text("Explore products under Rs. 500, 1,000 & 2,000", fontSize = 11.sp, color = Color(0xFFB45309))
              }
              Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF92400E), modifier = Modifier.size(16.dp))
            }
          }
        }

        // 10. Social Media Links Section
        item(key = "social_media_section") {
          SocialMediaSection()
        }

        // 11. Infinite Scroll Paginated Full Catalog (12 items per page)
        item(key = "all_products_header") {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 8.dp)
          ) {
            Text("EXPLORE JTBazar", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = JTOrangeDark)
            Text("More Products For You", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary)
          }
        }

        if (feedState.isInitialLoading && feedState.items.isEmpty()) {
          item(key = "home_feed_skeleton") {
            ProductSkeletonGrid(columns = productGridColumns, rows = 2)
          }
        } else if (feedState.initialError != null && feedState.items.isEmpty()) {
          item(key = "home_feed_error") {
            FriendlyErrorState(
              title = "Products load nahi ho sake",
              message = feedState.initialError ?: "Please check your connection and try again.",
              onRetry = { homePaginator.retry() }
            )
          }
        } else {
          val chunkedAll = feedState.items.chunked(productGridColumns)
          items(
            items = chunkedAll,
            key = { row -> "all_${row.firstOrNull()?.id ?: row.hashCode()}" }
          ) { rowItems ->
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
              ProductGridRow(
                rowItems = rowItems,
                columns = productGridColumns,
                onProductClick = onProductClick,
                onAddToCart = { MarketplaceRepository.addToCart(it) },
                onToggleWishlist = { MarketplaceRepository.toggleWishlist(it.id, it) }
              )
            }
          }
          item(key = "home_feed_pagination_footer") {
            PaginationFooter(
              isLoadingMore = feedState.isLoadingMore,
              paginationError = feedState.paginationError,
              hasMore = feedState.hasMore,
              itemCount = feedState.items.size,
              onRetry = { homePaginator.retry() }
            )
          }
        }
      }
    }
    }
  }
}

@Composable
fun ProductGridRow(
  rowItems: List<Product>,
  columns: Int,
  onProductClick: (Product) -> Unit,
  onAddToCart: (Product) -> Unit,
  onToggleWishlist: (Product) -> Unit,
  campaignStyle: Boolean = false
) {
  Row(
    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    rowItems.forEach { product ->
      Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
        ProductCard(
          product = product,
          onProductClick = { onProductClick(product) },
          onAddToCart = { onAddToCart(product) },
          onToggleWishlist = { onToggleWishlist(product) },
          modifier = Modifier.fillMaxHeight(),
          campaignStyle = campaignStyle
        )
      }
    }
    repeat((columns - rowItems.size).coerceAtLeast(0)) {
      Spacer(modifier = Modifier.weight(1f))
    }
  }
}

@Composable
fun CategoryVisualThumbnail(
  category: CategoryItem,
  allCategories: List<CategoryItem>,
  modifier: Modifier = Modifier
) {
  val remoteUrl = category.imageUrl?.takeIf { it.isNotBlank() }
  val foodDrawable = if (remoteUrl == null) {
    FoodCategoryResolver.resolveFoodDrawableOrNull(category, allCategories)
  } else {
    null
  }
  Box(
    modifier = modifier.background(JTSurfaceVariant),
    contentAlignment = Alignment.Center
  ) {
    when {
      remoteUrl != null -> {
        AsyncImage(
          model = rememberSizedImageRequest(data = remoteUrl, widthPx = 320, heightPx = 320),
          contentDescription = category.title,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      }
      foodDrawable != null -> {
        Image(
          painter = painterResource(id = foodDrawable),
          contentDescription = category.title,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      }
      else -> {
        val (icon, tint, bg) = resolveNonFoodCategoryVisual(category.title, category.slug)
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(bg),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = icon,
            contentDescription = category.title,
            tint = tint,
            modifier = Modifier.size(28.dp)
          )
        }
      }
    }
  }
}

private fun resolveNonFoodCategoryVisual(
  title: String,
  slug: String
): Triple<androidx.compose.ui.graphics.vector.ImageVector, Color, Color> {
  val key = "$title $slug".lowercase()
  return when {
    key.contains("electronic") || key.contains("phone") || key.contains("tech") || key.contains("gadget") ->
      Triple(Icons.Rounded.Devices, Color(0xFF1D4ED8), Color(0xFFEFF6FF))
    key.contains("fashion") || key.contains("cloth") || key.contains("apparel") || key.contains("bag") ->
      Triple(Icons.Rounded.Checkroom, Color(0xFF9333EA), Color(0xFFFAF5FF))
    key.contains("beauty") || key.contains("cosmetic") || key.contains("skin") ->
      Triple(Icons.Rounded.Face, Color(0xFFDB2777), Color(0xFFFDF2F8))
    key.contains("home") || key.contains("living") || key.contains("furniture") || key.contains("decor") ->
      Triple(Icons.Rounded.Home, Color(0xFF0D9488), Color(0xFFF0FDFA))
    key.contains("book") || key.contains("station") ->
      Triple(Icons.Rounded.MenuBook, Color(0xFFD97706), Color(0xFFFFFBEB))
    key.contains("sport") || key.contains("fitness") ->
      Triple(Icons.Rounded.SportsSoccer, Color(0xFF16A34A), Color(0xFFF0FDF4))
    key.contains("auto") || key.contains("car") || key.contains("bike") ->
      Triple(Icons.Rounded.DirectionsCar, Color(0xFF475569), Color(0xFFF1F5F9))
    key.contains("food") || key.contains("grocer") ->
      Triple(Icons.Rounded.Restaurant, JTOrangeDark, JTOrangeContainer)
    else ->
      Triple(Icons.Rounded.Category, JTOrangeDark, JTOrangeContainer)
  }
}

@Composable
fun SearchInputBar(
  query: String,
  onQueryChange: (String) -> Unit,
  onVoiceSearch: () -> Unit = {},
  modifier: Modifier = Modifier
 ) {
  val keyboardController = LocalSoftwareKeyboardController.current
  Surface(
    shape = RoundedCornerShape(24.dp),
    color = JTSurfaceVariant,
    modifier = modifier.height(48.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 14.dp)
    ) {
      Icon(
        imageVector = Icons.Filled.Search,
        contentDescription = "Search",
        tint = JTPurplePrimary,
        modifier = Modifier.size(20.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Box(modifier = Modifier.weight(1f)) {
        if (query.isEmpty()) {
          Text(
            text = "Search products, brands and sho...",
            color = JTTextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        androidx.compose.foundation.text.BasicTextField(
          value = query,
          onValueChange = onQueryChange,
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
          textStyle = androidx.compose.ui.text.TextStyle(
            color = JTTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
          ),
          modifier = Modifier.fillMaxWidth().testTag("search_text_input")
        )
      }

      IconButton(
        onClick = onVoiceSearch,
        modifier = Modifier.size(28.dp)
      ) {
        Icon(
          imageVector = Icons.Filled.Mic,
          contentDescription = "Voice Search",
          tint = JTTextSecondary,
          modifier = Modifier.size(19.dp)
        )
      }


    }
  }
}


@Composable
fun InstantDeliveryBanner(
  area: String,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = JTOrangeContainer,
    border = androidx.compose.foundation.BorderStroke(1.dp, JTOrangeBorder),
    modifier = modifier
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
      Box(
        modifier = Modifier
          .size(28.dp)
          .clip(CircleShape)
          .background(JTOrange),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Filled.ElectricBolt,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(16.dp)
        )
      }

      Spacer(modifier = Modifier.width(10.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Instant delivery to $area",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = JTTextPrimary
        )
        Text(
          text = "Centralized delivery by JTBazar",
          fontSize = 10.sp,
          color = JTTextSecondary
        )
      }

      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White
      ) {
        Text(
          text = "25 Mins",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = JTOrangeDark,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
      }
    }
  }
}

@Composable
fun GrandBachatPromoCard(
  onShopNow: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    modifier = modifier
      .fillMaxWidth()
      .height(170.dp)
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          brush = Brush.linearGradient(
            colors = listOf(
              Color(0xFFC2410C),
              Color(0xFFEA580C),
              Color(0xFFF97316)
            )
          )
        )
        .padding(16.dp)
    ) {
      // Star / decorative art background
      Box(
        modifier = Modifier
          .size(120.dp)
          .align(Alignment.CenterEnd)
          .offset(x = 10.dp, y = 10.dp)
      ) {
        Icon(
          imageVector = Icons.Filled.Star,
          contentDescription = null,
          tint = Color.White.copy(alpha = 0.2f),
          modifier = Modifier.fillMaxSize()
        )
      }

      Column(
        modifier = Modifier
          .fillMaxHeight()
          .fillMaxWidth(0.84f),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color.White.copy(alpha = 0.25f)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.AutoAwesome,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "GRAND BACHAT OFFER",
              fontSize = 10.sp,
              fontWeight = FontWeight.ExtraBold,
              color = Color.White
            )
          }
        }

        Column {
          Text(
            text = "JTBazar Super Savings\nMela",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            lineHeight = 22.sp
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Up to 40% OFF on fresh groceries, spices & home essentials",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.9f),
            lineHeight = 14.sp
          )
        }

        Button(
          onClick = onShopNow,
          shape = RoundedCornerShape(20.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = JTOrangeDark
          ),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
          modifier = Modifier
            .height(34.dp)
            .testTag("shop_now_promo_button")
        ) {
          Text(
            text = "Shop Now",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.width(4.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
          )
        }
      }

      // Carousel dots indicator at bottom right
      Row(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .width(16.dp)
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White)
        )
        Box(
          modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.4f))
        )
        Box(
          modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.4f))
        )
      }
    }
  }
}

@Composable
fun ProductCard(
  product: Product,
  onProductClick: () -> Unit,
  onAddToCart: () -> Unit,
  onToggleWishlist: () -> Unit,
  modifier: Modifier = Modifier,
  campaignStyle: Boolean = false
) {
  val cartItems by MarketplaceRepository.cartItems.collectAsState()
  val hasVariants = product.palettes.isNotEmpty() || product.sizes.isNotEmpty()
  val isInCart = cartItems.any { it.product.id == product.id }

  val cardRadius = if (campaignStyle) 14.dp else 16.dp
  Card(
    shape = RoundedCornerShape(cardRadius),
    colors = CardDefaults.cardColors(containerColor = if (campaignStyle) Color(0xFFFFF3E7) else JTCardBg),
    border = androidx.compose.foundation.BorderStroke(1.dp, if (campaignStyle) Color(0xFFF4B27E) else JTBorder),
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onProductClick)
      .testTag("product_card_${product.id}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(cardRadius))
    ) {
      // Image Box with Badge & Heart
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(1.08f)
          .background(if (campaignStyle) Color(0xFFFFF7ED) else JTSurfaceVariant),
        contentAlignment = Alignment.Center
      ) {
        val remoteUrl = product.imageUrl?.takeIf { it.isNotBlank() }
        if (remoteUrl != null) {
          AsyncImage(
            model = rememberSizedImageRequest(data = remoteUrl, widthPx = 420, heightPx = 420),
            contentDescription = product.title,
            contentScale = if (campaignStyle) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier.fillMaxSize().then(if (campaignStyle) Modifier.padding(8.dp) else Modifier)
          )
        } else if (product.drawableRes != 0) {
          Image(
            painter = painterResource(id = product.drawableRes),
            contentDescription = product.title,
            contentScale = if (campaignStyle) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier.fillMaxSize().then(if (campaignStyle) Modifier.padding(8.dp) else Modifier)
          )
        } else {
          Icon(
            imageVector = Icons.Outlined.ShoppingBag,
            contentDescription = product.title,
            tint = JTTextTertiary,
            modifier = Modifier.size(36.dp)
          )
        }

        // Discount tag top left
        if (product.badge != null) {
          Surface(
            shape = RoundedCornerShape(bottomEnd = 10.dp, topStart = cardRadius),
            color = JTRed,
            modifier = Modifier.align(Alignment.TopStart)
          ) {
            Text(
              text = product.badge,
              color = Color.White,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
            )
          }
        }

        // Heart favorite toggle top right
        Surface(
          shape = CircleShape,
          color = Color.White.copy(alpha = 0.90f),
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .size(30.dp)
            .clickable(onClick = onToggleWishlist)
            .testTag("wishlist_toggle_${product.id}")
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (product.isWishlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
              contentDescription = "Wishlist",
              tint = if (product.isWishlisted) JTRed else JTTextSecondary,
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }

      // Card Content with uniform height slots and bottom-aligned price row
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(if (campaignStyle) Color(0xFFFFF3E7) else Color.Transparent)
          .padding(horizontal = if (campaignStyle) 12.dp else 10.dp, vertical = if (campaignStyle) 10.dp else 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          // Tag badge slot with fixed 20dp height
          val tagText = product.inStockText.ifBlank { product.category }
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(20.dp),
            contentAlignment = Alignment.CenterStart
          ) {
            if (tagText.isNotBlank()) {
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = JTPurpleContainer
              ) {
                Text(
                  text = tagText,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  color = JTPurplePrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(4.dp))

          // Product title with uniform 2-line height across all cards
          Text(
            text = product.title,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            color = if (campaignStyle) Color(0xFF542C27) else JTTextPrimary,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp,
            modifier = Modifier.fillMaxWidth()
          )

          if (!product.foodSellingUnit.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = product.foodSellingUnit,
              fontSize = 11.sp,
              color = JTTextSecondary,
              fontWeight = FontWeight.Medium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          Spacer(modifier = Modifier.height(4.dp))

          // Rating row with uniform 18dp min height
          Row(
            modifier = Modifier.heightIn(min = 18.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Filled.Star,
              contentDescription = null,
              tint = JTAmber,
              modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = "${product.rating} (${if (product.reviewCount > 999) "${product.reviewCount / 1000}k" else "${product.reviewCount}"})",
              fontSize = 11.sp,
              color = JTTextSecondary,
              fontWeight = FontWeight.Medium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Price Row with Add to Cart / Out of Stock
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
          ) {
            Text(
              text = "Rs. ${String.format("%,d", product.price)}",
              fontWeight = FontWeight.ExtraBold,
              fontSize = 12.5.sp,
              lineHeight = 15.sp,
              color = if (campaignStyle) Color(0xFF9A3412) else JTPurplePrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            if (product.originalPrice > product.price) {
              Text(
                text = "Rs. ${String.format("%,d", product.originalPrice)}",
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = JTTextTertiary,
                textDecoration = TextDecoration.LineThrough,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            } else {
              Spacer(Modifier.height(14.dp))
            }
          }

          // Button
          val dailyState = product.dailyStatus?.trim()?.lowercase()
          val isOutOfStock = (product.availabilityKnown && !product.isAvailable) ||
            dailyState in setOf("out_of_stock", "out-of-stock", "unavailable", "not_available", "not available", "sold_out", "sold out", "closed")

          if (isOutOfStock) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color(0xFFFFE4E6),
              modifier = Modifier.testTag("out_of_stock_${product.id}")
            ) {
              Text(
                text = "Out of Stock",
                color = Color(0xFFBE123C),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
              )
            }
          } else {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (isInCart) Color(0xFF16A34A) else if (campaignStyle) Color(0xFFE85D20) else JTOrange,
              modifier = Modifier
                .height(30.dp)
                .width(if (hasVariants) 68.dp else 30.dp)
                .clickable(onClick = { if (hasVariants) onProductClick() else onAddToCart() })
                .testTag("add_to_cart_${product.id}")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = if (hasVariants) 7.dp else 0.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = if (isInCart) Icons.Filled.Check else if (hasVariants) Icons.Filled.Tune else Icons.Filled.Add,
                  contentDescription = if (isInCart) "Added to cart" else if (hasVariants) "Choose options" else "Add to cart",
                  tint = Color.White,
                  modifier = Modifier.size(17.dp)
                )
                if (hasVariants) {
                  Spacer(Modifier.width(3.dp))
                  Text("Choose", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun SocialMediaSection(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = "Connect with JTBazar",
      fontSize = 14.sp,
      fontWeight = FontWeight.Bold,
      color = JTTextPrimary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = "Follow our official channels for live updates, vendor stories & deals",
      fontSize = 11.sp,
      color = JTTextSecondary,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))

    Row(
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Official supplied brand marks
      Box(
        modifier = Modifier
          .size(50.dp)
          .shadow(3.dp, CircleShape)
          .clip(CircleShape)
          .background(
            Brush.linearGradient(
              colors = listOf(
                Color(0xFF405DE6),
                Color(0xFFC13584),
                Color(0xFFFD1D1D),
                Color(0xFFFFDC80)
              )
            )
          )
          .clickable {
            runCatching {
              context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/jtbazarofficial/")))
            }
          }
          .testTag("social_instagram"),
        contentAlignment = Alignment.Center
      ) {
        // Use a VectorDrawable directly. Compose painterResource cannot render LayerDrawable.
        Image(
          painter = painterResource(R.drawable.ic_instagram_camera),
          contentDescription = "Instagram",
          modifier = Modifier.fillMaxSize().padding(5.dp)
        )
      }

      Surface(
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 3.dp,
        modifier = Modifier.size(50.dp).clickable {
          context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/profile.php?id=61595825413366")))
        }.testTag("social_facebook")
      ) {
        Image(painterResource(R.drawable.social_facebook), "Facebook", modifier = Modifier.fillMaxSize())
      }

      Surface(
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 3.dp,
        modifier = Modifier.size(50.dp).clickable {
          context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/@jtbazarofficial")))
        }.testTag("social_tiktok")
      ) {
        Image(painterResource(R.drawable.social_tiktok), "TikTok", modifier = Modifier.fillMaxSize())
      }
    }

    Spacer(modifier = Modifier.height(14.dp))
    Text(
      text = "© 2026 JTBazar • Authentic Marketplace Pakistan",
      fontSize = 10.sp,
      color = JTTextSecondary.copy(alpha = 0.7f)
    )
  }
}



@Composable
private fun ProductCampaignSection(
  activeCampaign: ActiveProductCampaign,
  productGridColumns: Int = 2,
  onProductClick: (Product) -> Unit,
  onAddToCart: (Product) -> Unit,
  onToggleWishlist: (String) -> Unit
) {
  val campaign = activeCampaign.campaign
  val title = campaign.displayTitle.ifBlank { campaign.name }
  val dealLabel = when (campaign.campaignType.lowercase()) {
    "bogo" -> "Buy ${campaign.buyQuantity}, Get ${campaign.getQuantity}"
    "bundle" -> "Bundle price deal"
    "flash_sale", "seasonal", "custom" -> "${campaign.discountPercent.toInt()}% off"
    else -> "Special offer"
  }
  val campaignTypeLabel = campaign.campaignType.replace('_', ' ').uppercase()
  var remainingSeconds by remember(campaign.id, campaign.endsAt) { mutableLongStateOf(-1L) }

  LaunchedEffect(campaign.id, campaign.endsAt) {
    val expiry = campaign.endsAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
    if (expiry == null) {
      remainingSeconds = -1L
    } else {
      while (true) {
        val seconds = ((expiry - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
        remainingSeconds = seconds
        if (seconds == 0L) break
        kotlinx.coroutines.delay(1000L)
      }
    }
  }
  if (remainingSeconds == 0L) return

  val description = campaign.description?.takeIf { it.isNotBlank() }
    ?: "Limited-time deal on selected products. Shop now before the offer ends!"
  val dealColumns = productGridColumns.coerceAtLeast(2)

  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val isCompact = maxWidth < 520.dp
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 28.dp)) {
      Box(
        Modifier.fillMaxWidth()
          .shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x337A286F), spotColor = Color(0x337A286F))
          .clip(RoundedCornerShape(20.dp))
          .background(Brush.horizontalGradient(listOf(Color(0xFF43206F), Color(0xFF792B80), Color(0xFFE85A2A))))
      ) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 28.dp, y = (-45).dp).size(170.dp).border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape))
        Text("%", Modifier.align(Alignment.TopEnd).offset(x = (-92).dp, y = (-18).dp), color = Color.White.copy(alpha = 0.13f), fontSize = 78.sp, fontWeight = FontWeight.Black)
        Box(Modifier.align(Alignment.BottomEnd).offset(x = 25.dp, y = 55.dp).size(150.dp).border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape))
        if (isCompact) {
          Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            CampaignBannerCopy(title, description, dealLabel, activeCampaign.products.size, campaignTypeLabel)
            CampaignCountdownPanel(remainingSeconds)
          }
        } else {
          Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.weight(1f)) { CampaignBannerCopy(title, description, dealLabel, activeCampaign.products.size, campaignTypeLabel) }
            CampaignCountdownPanel(remainingSeconds, Modifier.width(210.dp))
          }
        }
      }
      Spacer(Modifier.height(14.dp))
      Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text("HANDPICKED FOR YOU", color = Color(0xFFC45B22), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
          Text("Shop this deal", color = JTTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (!isCompact) Text("Limited-time prices · While stocks last", color = JTTextSecondary, fontSize = 10.sp)
      }
      activeCampaign.products.chunked(dealColumns).forEach { rowProducts ->
        Row(
          Modifier.fillMaxWidth().height(IntrinsicSize.Max),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          rowProducts.forEach { product ->
            Box(Modifier.weight(1f).fillMaxHeight()) {
              ProductCard(
                product = if (product.badge == null) product.copy(badge = dealLabel) else product,
                onProductClick = { onProductClick(product) },
                onAddToCart = { onAddToCart(product) },
                onToggleWishlist = { onToggleWishlist(product.id) },
                modifier = Modifier.fillMaxHeight(),
                campaignStyle = true
              )
            }
          }
          repeat((dealColumns - rowProducts.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(12.dp))
      }
    }
  }
}

@Composable
private fun CampaignBannerCopy(title: String, description: String, dealLabel: String, productCount: Int, campaignTypeLabel: String) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFFD16B)))
      Text(campaignTypeLabel, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
      Surface(color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))) {
        Text("SPECIAL OFFER", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
      }
    }
    Text(title, color = Color.White, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Text(description, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(2.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(dealLabel, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
      Text("$productCount deal item${if (productCount == 1) "" else "s"} selected", color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp)
    }
  }
}

@Composable
private fun CampaignCountdownPanel(remainingSeconds: Long, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.clip(RoundedCornerShape(15.dp)).background(Color(0x663D2345))
      .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(15.dp))
      .padding(horizontal = 12.dp, vertical = 13.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(9.dp)
  ) {
    Text("OFFER ENDS IN", color = Color.White.copy(alpha = 0.86f), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
    if (remainingSeconds < 0) {
      Text("Limited-time offer", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
      return@Column
    }
    val days = remainingSeconds / 86400L
    val hours = if (remainingSeconds < 0) 0L else (remainingSeconds % 86400L) / 3600L
    val minutes = if (remainingSeconds < 0) 0L else (remainingSeconds % 3600L) / 60L
    val seconds = if (remainingSeconds < 0) 0L else remainingSeconds % 60L
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
      CountdownTimeUnit(days.toString().padStart(2, '0'), "DAYS", Modifier.weight(1f))
      CountdownTimeUnit(hours.toString().padStart(2, '0'), "HRS", Modifier.weight(1f))
      CountdownTimeUnit(minutes.toString().padStart(2, '0'), "MIN", Modifier.weight(1f))
      CountdownTimeUnit(seconds.toString().padStart(2, '0'), "SEC", Modifier.weight(1f))
    }
    Text(if (remainingSeconds < 0) "Limited-time offer" else "Don't miss your deal", color = Color.White.copy(alpha = 0.82f), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
  }
}

@Composable
private fun CountdownTimeUnit(value: String, label: String, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.18f))
      .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp)).padding(vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(value, color = Color.White, fontSize = 19.sp, lineHeight = 21.sp, fontWeight = FontWeight.ExtraBold)
    Text(label, color = Color.White.copy(alpha = 0.88f), fontSize = 7.sp, fontWeight = FontWeight.Bold)
  }
}


