package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Product
import com.example.data.repository.ActiveProductCampaign
import com.example.data.repository.CatalogPaginator
import com.example.data.repository.CatalogQuery
import com.example.data.repository.MarketplaceRepository
import com.example.data.repository.ProductSortOption
import com.example.ui.components.FriendlyEmptyState
import com.example.ui.components.FriendlyErrorState
import com.example.ui.components.PaginationFooter
import com.example.ui.components.ProductSkeletonGrid
import com.example.ui.components.rememberAdaptiveColumnCount
import com.example.ui.theme.*
import kotlinx.coroutines.delay

sealed interface HomeCollectionTarget {
  val key: String

  data class Deal(val activeCampaign: ActiveProductCampaign) : HomeCollectionTarget {
    override val key: String get() = "deal_${activeCampaign.campaign.id}"
  }

  data class Popular(val isFeaturedOnly: Boolean) : HomeCollectionTarget {
    override val key: String get() = "popular_$isFeaturedOnly"
  }

  data object NewArrivals : HomeCollectionTarget {
    override val key: String get() = "new_arrivals"
  }
}

/**
 * Dedicated paginated product listing screen for Home "See All" sections:
 * - Active Deals (all campaign products, 12 per page with countdown banner)
 * - Popular Products (12 per page, search + sort)
 * - New Arrivals (12 per page, newest-first, search + sort)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionProductsScreen(
  target: HomeCollectionTarget,
  onBack: () -> Unit,
  onProductClick: (Product) -> Unit,
  onCartClick: () -> Unit,
  modifier: Modifier = Modifier,
  listState: LazyListState = rememberLazyListState(),
  paginatorOverride: CatalogPaginator? = null
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val activePaginator = paginatorOverride ?: remember(target.key, scope) {
    when (target) {
      is HomeCollectionTarget.Deal -> MarketplaceRepository.createCampaignPaginator(scope, target.activeCampaign)
      is HomeCollectionTarget.Popular,
      is HomeCollectionTarget.NewArrivals -> MarketplaceRepository.createCatalogPaginator(scope)
    }
  }

  val productGridColumns = rememberAdaptiveColumnCount(
    minCellWidth = 138.dp,
    horizontalPadding = 32.dp,
    spacing = 12.dp,
    maxColumns = 4
  )
  val cartCount by MarketplaceRepository.cartCount.collectAsState()
  val wishlistedIds by MarketplaceRepository.wishlistedIds.collectAsState()
  val feedState by activePaginator.state.collectAsState()

  val isDeal = target is HomeCollectionTarget.Deal
  var searchVisible by remember(target.key) { mutableStateOf(false) }
  var searchQuery by remember(target.key) { mutableStateOf("") }
  var debouncedSearch by remember(target.key) { mutableStateOf("") }
  var selectedSort by remember(target.key) { mutableStateOf(ProductSortOption.NEWEST) }

  LaunchedEffect(searchQuery) {
    delay(350L)
    debouncedSearch = searchQuery.trim()
  }

  LaunchedEffect(target.key, debouncedSearch, selectedSort) {
    val targetQuery = when (target) {
      is HomeCollectionTarget.Deal -> CatalogQuery()
      is HomeCollectionTarget.Popular -> CatalogQuery(
        isFeatured = if (target.isFeaturedOnly) true else null,
        searchQuery = debouncedSearch,
        sortBy = selectedSort
      )
      is HomeCollectionTarget.NewArrivals -> CatalogQuery(
        searchQuery = debouncedSearch,
        sortBy = selectedSort
      )
    }
    if (!feedState.hasLoadedOnce || feedState.query != targetQuery) {
      activePaginator.resetAndLoad(targetQuery)
    }
  }

  LaunchedEffect(wishlistedIds) {
    activePaginator.syncWishlistStates(wishlistedIds)
  }

  val shouldLoadMore by remember(listState, feedState.hasMore, feedState.isInitialLoading, feedState.isLoadingMore, feedState.paginationError) {
    derivedStateOf {
      val info = listState.layoutInfo
      val totalItems = info.totalItemsCount
      val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
      totalItems > 0 &&
        feedState.hasLoadedOnce &&
        feedState.hasMore &&
        !feedState.isInitialLoading &&
        !feedState.isLoadingMore &&
        feedState.paginationError == null &&
        lastVisible >= totalItems - 4
    }
  }

  LaunchedEffect(shouldLoadMore) {
    if (shouldLoadMore) {
      activePaginator.loadNextPage()
    }
  }

  val chunkedProductRows = remember(feedState.items, productGridColumns) {
    feedState.items.chunked(productGridColumns)
  }

  val screenTitle = when (target) {
    is HomeCollectionTarget.Deal -> target.activeCampaign.campaign.displayTitle.ifBlank { target.activeCampaign.campaign.name }
    is HomeCollectionTarget.Popular -> "Popular Products"
    is HomeCollectionTarget.NewArrivals -> "New Arrivals"
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = Color.White,
    topBar = {
      Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        TopAppBar(
          colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
          title = {
            Text(
              text = screenTitle,
              fontWeight = FontWeight.Bold,
              fontSize = 19.sp,
              color = JTTextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          },
          navigationIcon = {
            IconButton(
              onClick = onBack,
              modifier = Modifier.size(48.dp).testTag("collection_back_button")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = JTTextPrimary
              )
            }
          },
          actions = {
            if (!isDeal) {
              IconButton(
                onClick = { searchVisible = !searchVisible },
                modifier = Modifier.size(48.dp).testTag("collection_search_toggle")
              ) {
                Icon(
                  imageVector = if (searchVisible) Icons.Rounded.Close else Icons.Filled.Search,
                  contentDescription = if (searchVisible) "Close search" else "Search products",
                  tint = JTTextPrimary
                )
              }
            }
            IconButton(
              onClick = onCartClick,
              modifier = Modifier.size(48.dp).testTag("collection_cart_button")
            ) {
              BadgedBox(
                badge = {
                  if (cartCount > 0) {
                    Badge(containerColor = JTRed) {
                      Text("$cartCount", color = Color.White, fontSize = 10.sp)
                    }
                  }
                }
              ) {
                Icon(
                  imageVector = Icons.Filled.ShoppingBag,
                  contentDescription = "Cart",
                  tint = JTTextPrimary
                )
              }
            }
          }
        )

        if (!isDeal && searchVisible) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search in $screenTitle...", fontSize = 13.sp) },
            leadingIcon = {
              Icon(Icons.Filled.Search, contentDescription = null, tint = JTOrange)
            },
            trailingIcon = {
              if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { searchQuery = "" }) {
                  Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 6.dp)
              .testTag("collection_search_input")
          )
        }
      }
    }
  ) { padding ->
    LazyColumn(
      state = listState,
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .testTag("collection_lazy_column"),
      contentPadding = PaddingValues(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      when (target) {
        is HomeCollectionTarget.Deal -> {
          item(key = "deal_hero_header") {
            DealCollectionHeaderBanner(activeCampaign = target.activeCampaign)
          }
        }
        is HomeCollectionTarget.Popular -> {
          item(key = "popular_collection_header") {
            CollectionAccentHeader(
              badge = "FEATURED PICKS",
              title = "Most Popular on JTBazar",
              subtitle = "Top customer favorites across trusted Pakistani sellers",
              icon = Icons.Filled.LocalFireDepartment,
              accentColor = JTRed,
              accentContainer = JTRedContainer
            )
          }
          item(key = "collection_sort_bar") {
            CollectionSortBar(
              selectedSort = selectedSort,
              onSortSelected = { selectedSort = it }
            )
          }
        }
        is HomeCollectionTarget.NewArrivals -> {
          item(key = "new_arrivals_collection_header") {
            CollectionAccentHeader(
              badge = "JUST LANDED",
              title = "Fresh New Arrivals",
              subtitle = "Latest verified listings added by our vendors",
              icon = Icons.Filled.NewReleases,
              accentColor = JTOrangeDark,
              accentContainer = JTOrangeContainer
            )
          }
          item(key = "collection_sort_bar") {
            CollectionSortBar(
              selectedSort = selectedSort,
              onSortSelected = { selectedSort = it }
            )
          }
        }
      }

      when {
        feedState.isInitialLoading && feedState.items.isEmpty() -> {
          item(key = "collection_skeleton") {
            ProductSkeletonGrid(columns = productGridColumns, rows = 3)
          }
        }
        feedState.initialError != null && feedState.items.isEmpty() -> {
          item(key = "collection_error") {
            FriendlyErrorState(
              title = "Products load nahi ho sake",
              message = feedState.initialError ?: "Please check your connection and try again.",
              onRetry = { activePaginator.retry() }
            )
          }
        }
        feedState.items.isEmpty() -> {
          item(key = "collection_empty") {
            FriendlyEmptyState(
              title = if (debouncedSearch.isNotBlank()) "No matching products" else "No products available yet",
              message = if (debouncedSearch.isNotBlank()) {
                "Try another search keyword or clear filters."
              } else {
                "Fresh inventory is being updated. Please check back soon."
              },
              actionLabel = if (debouncedSearch.isNotBlank()) "Clear Search" else null,
              onAction = if (debouncedSearch.isNotBlank()) {
                { searchQuery = "" }
              } else null
            )
          }
        }
        else -> {
          items(
            items = chunkedProductRows,
            key = { row -> "${target.key}_${row.firstOrNull()?.id ?: row.hashCode()}" },
            contentType = { "product_row" }
          ) { rowItems ->
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
              ProductGridRow(
                rowItems = rowItems,
                columns = productGridColumns,
                onProductClick = onProductClick,
                onAddToCart = { product ->
                  MarketplaceRepository.addToCart(product)
                  if (MarketplaceRepository.canAddProductToCart(product)) {
                    Toast.makeText(context, "Added to cart!", Toast.LENGTH_SHORT).show()
                  }
                },
                onToggleWishlist = { product ->
                  MarketplaceRepository.toggleWishlist(product.id, product)
                },
                campaignStyle = isDeal
              )
            }
          }

          item(key = "collection_pagination_footer") {
            PaginationFooter(
              isLoadingMore = feedState.isLoadingMore,
              paginationError = feedState.paginationError,
              hasMore = feedState.hasMore,
              itemCount = feedState.items.size,
              onRetry = { activePaginator.retry() }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DealCollectionHeaderBanner(activeCampaign: ActiveProductCampaign) {
  val campaign = activeCampaign.campaign
  val title = campaign.displayTitle.ifBlank { campaign.name }
  val dealLabel = when (campaign.campaignType.lowercase()) {
    "bogo" -> "Buy ${campaign.buyQuantity}, Get ${campaign.getQuantity}"
    "bundle" -> "Bundle price deal"
    "flash_sale", "seasonal", "custom" -> "${campaign.discountPercent.toInt()}% off"
    else -> "Special offer"
  }
  val description = campaign.description?.takeIf { it.isNotBlank() }
    ?: "Limited-time deal on ${campaign.productIds.size} selected items. Grab yours before stock runs out!"

  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val isCompact = maxWidth < 520.dp
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
        .shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x337A286F), spotColor = Color(0x337A286F))
        .clip(RoundedCornerShape(20.dp))
        .background(Brush.horizontalGradient(listOf(Color(0xFF43206F), Color(0xFF792B80), Color(0xFFE85A2A))))
        .testTag("deal_collection_banner")
    ) {
      Box(Modifier.align(Alignment.TopEnd).offset(x = 28.dp, y = (-45).dp).size(150.dp).border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape))
      if (isCompact) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          DealHeaderCopy(title, description, dealLabel, campaign.productIds.size)
          CampaignCountdownHost(campaign.id, campaign.endsAt)
        }
      } else {
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Box(Modifier.weight(1f)) { DealHeaderCopy(title, description, dealLabel, campaign.productIds.size) }
          CampaignCountdownHost(campaign.id, campaign.endsAt, Modifier.width(210.dp))
        }
      }
    }
  }
}

@Composable
private fun DealHeaderCopy(title: String, description: String, dealLabel: String, totalItems: Int) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Surface(
      color = Color.White.copy(alpha = 0.16f),
      shape = RoundedCornerShape(20.dp),
      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
    ) {
      Text(
        text = "$dealLabel • $totalItems ITEMS",
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
      )
    }
    Text(
      text = title,
      color = Color.White,
      fontSize = 22.sp,
      fontWeight = FontWeight.ExtraBold,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis
    )
    Text(
      text = description,
      color = Color.White.copy(alpha = 0.88f),
      fontSize = 12.sp,
      lineHeight = 16.sp,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun CollectionAccentHeader(
  badge: String,
  title: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  accentColor: Color,
  accentContainer: Color
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = accentContainer,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 4.dp)
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(Color.White),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(badge, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = accentColor, letterSpacing = 0.8.sp)
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary)
        Text(subtitle, fontSize = 11.5.sp, color = JTTextSecondary)
      }
    }
  }
}

@Composable
private fun CollectionSortBar(
  selectedSort: ProductSortOption,
  onSortSelected: (ProductSortOption) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    ProductSortOption.entries.forEach { option ->
      val isSelected = option == selectedSort
      FilterChip(
        selected = isSelected,
        onClick = { onSortSelected(option) },
        label = {
          Text(
            text = option.label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
          )
        },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = JTOrange,
          selectedLabelColor = Color.White,
          containerColor = JTSurfaceVariant,
          labelColor = JTTextPrimary
        ),
        border = null,
        modifier = Modifier.testTag("collection_sort_${option.name.lowercase()}")
      )
    }
  }
}
