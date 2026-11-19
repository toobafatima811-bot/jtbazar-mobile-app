package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.CategoryItem
import com.example.data.model.Product
import com.example.data.repository.CatalogPaginator
import com.example.data.repository.CatalogQuery
import com.example.data.repository.FoodCategoryResolver
import com.example.data.repository.MarketplaceRepository
import com.example.data.repository.ProductSortOption
import com.example.localization.AppLanguageManager
import com.example.localization.appText
import com.example.ui.components.FriendlyEmptyState
import com.example.ui.components.FriendlyErrorState
import com.example.ui.components.PaginationFooter
import com.example.ui.components.ProductSkeletonGrid
import com.example.ui.components.rememberAdaptiveColumnCount
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Native category listing with server-side pagination (12 items/page),
 * debounced server search, sorting, infinite scroll, and Food-only category banner + subcategory strip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryProductsScreen(
  category: CategoryItem,
  onBack: () -> Unit,
  onProductClick: (Product) -> Unit,
  onCartClick: () -> Unit,
  modifier: Modifier = Modifier,
  listState: LazyListState = rememberLazyListState(),
  paginator: CatalogPaginator? = null,
  allCategoriesOverride: List<CategoryItem>? = null
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val activePaginator = paginator ?: remember(category.id, scope) {
    MarketplaceRepository.createCatalogPaginator(scope)
  }

  val productGridColumns = rememberAdaptiveColumnCount(
    minCellWidth = 138.dp,
    horizontalPadding = 32.dp,
    spacing = 12.dp,
    maxColumns = 4
  )
  val repoCategories by MarketplaceRepository.categories.collectAsState()
  val allCategories = allCategoriesOverride ?: repoCategories
  val cartCount by MarketplaceRepository.cartCount.collectAsState()
  val wishlistedIds by MarketplaceRepository.wishlistedIds.collectAsState()
  val feedState by activePaginator.state.collectAsState()

  val isFood = remember(category, allCategories) {
    FoodCategoryResolver.isFoodCategory(category, allCategories)
  }
  val foodSubcategories = remember(category, allCategories, isFood) {
    if (isFood) FoodCategoryResolver.getFoodSubcategories(category, allCategories) else emptyList()
  }
  val nonFoodSubcategories = remember(category.id, allCategories, isFood) {
    if (!isFood) {
      allCategories.filter { it.parentId == category.id }.sortedBy { it.displayOrder }
    } else emptyList()
  }

  var searchVisible by remember(category.id) { mutableStateOf(false) }
  var searchQuery by remember(category.id) { mutableStateOf("") }
  var debouncedSearch by remember(category.id) { mutableStateOf("") }
  var selectedSort by remember(category.id) { mutableStateOf(ProductSortOption.NEWEST) }
  var selectedSubcategoryId by remember(category.id) { mutableStateOf<String?>(null) }

  LaunchedEffect(searchQuery) {
    delay(350L)
    debouncedSearch = searchQuery.trim()
  }

  val targetCategoryIds = remember(category.id, selectedSubcategoryId, allCategories) {
    val rootId = selectedSubcategoryId ?: category.id
    FoodCategoryResolver.collectDescendantIds(rootId, allCategories)
  }

  LaunchedEffect(category.id, targetCategoryIds, debouncedSearch, selectedSort) {
    val targetQuery = CatalogQuery(
      categoryIds = targetCategoryIds,
      searchQuery = debouncedSearch,
      sortBy = selectedSort
    )
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

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = Color.White,
    topBar = {
      Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        TopAppBar(
          colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
          title = {
            Text(
              text = category.title,
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
              modifier = Modifier.size(48.dp).testTag("category_back_button")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = appText("Back", "واپس"),
                tint = JTTextPrimary
              )
            }
          },
          actions = {
            IconButton(
              onClick = { searchVisible = !searchVisible },
              modifier = Modifier.size(48.dp).testTag("category_search_toggle")
            ) {
              Icon(
                imageVector = if (searchVisible) Icons.Rounded.Close else Icons.Filled.Search,
                contentDescription = if (searchVisible) appText("Close search", "تلاش بند کریں") else appText("Search collection", "کلیکشن میں تلاش کریں"),
                tint = JTTextPrimary
              )
            }
            IconButton(
              onClick = onCartClick,
              modifier = Modifier.size(48.dp).testTag("category_cart_button")
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
                  contentDescription = appText("Cart", "ٹوکری"),
                  tint = JTTextPrimary
                )
              }
            }
          }
        )

        if (searchVisible) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(appText("Search in ${category.title}...", "${category.title} میں تلاش کریں..."), fontSize = 13.sp) },
            leadingIcon = {
              Icon(Icons.Filled.Search, contentDescription = null, tint = JTOrange)
            },
            trailingIcon = {
              if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { searchQuery = "" }) {
                  Icon(Icons.Rounded.Close, contentDescription = appText("Clear search", "تلاش صاف کریں"))
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 6.dp)
              .testTag("category_search_input")
          )
        }
      }
    }
  ) { padding ->
    LazyColumn(
      state = listState,
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
      contentPadding = PaddingValues(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      if (isFood) {
        item(key = "food_hero_banner") {
          FoodHeroBanner(
            onShopNow = {
              selectedSubcategoryId = null
              scope.launch {
                val targetIndex = if (foodSubcategories.isNotEmpty()) 3 else 2
                if (listState.layoutInfo.totalItemsCount > targetIndex) {
                  listState.animateScrollToItem(targetIndex)
                }
              }
            }
          )
        }

        if (foodSubcategories.isNotEmpty()) {
          item(key = "food_subcategory_strip") {
            FoodSubcategoryStrip(
              subcategories = foodSubcategories,
              allCategories = allCategories,
              selectedSubcategoryId = selectedSubcategoryId,
              onSelect = { selectedSubcategoryId = it }
            )
          }
        }
      } else {
        if (category.sampleItems.isNotBlank() || category.badge == "Coming soon") {
          item(key = "category_intro") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
              if (category.sampleItems.isNotBlank()) {
                Text(category.sampleItems, color = JTTextSecondary, fontSize = 13.sp)
              }
              if (category.badge == "Coming soon") {
                Spacer(Modifier.height(4.dp))
                Text(appText("Coming soon", "جلد آ رہا ہے"), color = JTOrangeDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
              }
            }
          }
        }

        if (nonFoodSubcategories.isNotEmpty()) {
          item(key = "non_food_subcategory_strip") {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              FilterChip(
                selected = selectedSubcategoryId == null,
                onClick = { selectedSubcategoryId = null },
                label = { Text(appText("All", "سب"), fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = JTOrange,
                  selectedLabelColor = Color.White
                )
              )
              nonFoodSubcategories.forEach { child ->
                FilterChip(
                  selected = selectedSubcategoryId == child.id,
                  onClick = { selectedSubcategoryId = child.id },
                  label = { Text(child.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = JTOrange,
                    selectedLabelColor = Color.White
                  )
                )
              }
            }
          }
        }
      }

      item(key = "category_sort_row") {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          ProductSortOption.entries.forEach { option ->
            val selected = selectedSort == option
            FilterChip(
              selected = selected,
              onClick = { selectedSort = option },
              label = {
                Text(
                  text = option.localizedLabel(),
                  fontSize = 12.sp,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = JTOrangeContainer,
                selectedLabelColor = JTOrangeDark
              ),
              modifier = Modifier.testTag("category_sort_${option.name.lowercase()}")
            )
          }
        }
      }

      when {
        feedState.isInitialLoading && feedState.items.isEmpty() -> {
          item(key = "category_skeleton") {
            ProductSkeletonGrid(columns = productGridColumns, rows = 3)
          }
        }
        feedState.initialError != null && feedState.items.isEmpty() -> {
          item(key = "category_error") {
            FriendlyErrorState(
              title = appText("Could not load products", "مصنوعات لوڈ نہیں ہو سکیں"),
              message = feedState.initialError ?: appText("Please check your connection and try again.", "براہ کرم اپنا انٹرنیٹ کنکشن چیک کریں اور دوبارہ کوشش کریں۔"),
              onRetry = { activePaginator.retry() }
            )
          }
        }
        feedState.items.isEmpty() -> {
          item(key = "category_empty") {
            FriendlyEmptyState(
              title = if (debouncedSearch.isNotBlank()) appText("No matching products", "کوئی مماثل مصنوعات نہیں ملیں") else appText("No products in ${category.title} yet", "${category.title} میں ابھی کوئی مصنوعات نہیں ہیں"),
              message = if (debouncedSearch.isNotBlank()) {
                appText("Try another search word or clear filters.", "کوئی اور لفظ تلاش کریں یا فلٹرز صاف کریں۔")
              } else {
                appText("Fresh stock is being updated. Check back soon or explore another category.", "نیا اسٹاک اپ ڈیٹ کیا جا رہا ہے۔ جلد دوبارہ دیکھیں یا کوئی اور زمرہ دیکھیں۔")
              },
              actionLabel = if (debouncedSearch.isNotBlank() || selectedSubcategoryId != null) appText("Show All Items", "تمام اشیاء دکھائیں") else null,
              onAction = if (debouncedSearch.isNotBlank() || selectedSubcategoryId != null) {
                {
                  searchQuery = ""
                  selectedSubcategoryId = null
                }
              } else null
            )
          }
        }
        else -> {
          items(
            items = chunkedProductRows,
            key = { row -> "cat_${row.firstOrNull()?.id ?: row.hashCode()}" },
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
                    Toast.makeText(context, AppLanguageManager.tr("Added to cart!", "ٹوکری میں شامل ہو گیا!"), Toast.LENGTH_SHORT).show()
                  }
                },
                onToggleWishlist = { product ->
                  MarketplaceRepository.toggleWishlist(product.id, product)
                }
              )
            }
          }

          item(key = "category_pagination_footer") {
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
private fun FoodHeroBanner(
  onShopNow: () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    shadowElevation = 2.dp,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .testTag("food_hero_banner")
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          Brush.horizontalGradient(
            colors = listOf(
              Color(0xFFF0F9D8),
              Color(0xFFE4F4BC)
            )
          )
        )
        .padding(start = 18.dp, top = 16.dp, bottom = 16.dp, end = 12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text(
            text = appText("Fresh & Healthy", "تازہ اور صحت بخش"),
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF224B0C),
            lineHeight = 26.sp
          )
          Text(
            text = appText("Food at Your Doorstep", "خوراک آپ کی دہلیز پر"),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF335E18)
          )
          Spacer(modifier = Modifier.height(6.dp))
          Button(
            onClick = onShopNow,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFF4D8B16),
              contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier
              .heightIn(min = 36.dp)
              .testTag("food_hero_shop_now")
          ) {
            Text(
              text = appText("Shop Now", "ابھی خریدیں"),
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
              contentDescription = null,
              modifier = Modifier.size(15.dp)
            )
          }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
          modifier = Modifier
            .size(104.dp)
            .clip(CircleShape)
            .background(Color(0xFFCDE998).copy(alpha = 0.65f)),
          contentAlignment = Alignment.Center
        ) {
          Image(
            painter = painterResource(id = R.drawable.thumb_dry_fruits),
            contentDescription = appText("Fresh and healthy food selection", "تازہ اور صحت بخش خوراک کا انتخاب"),
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .size(92.dp)
              .clip(CircleShape)
          )
        }
      }
    }
  }
}

@Composable
private fun FoodSubcategoryStrip(
  subcategories: List<CategoryItem>,
  allCategories: List<CategoryItem>,
  selectedSubcategoryId: String?,
  onSelect: (String?) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    val allSelected = selectedSubcategoryId == null
    Surface(
      shape = RoundedCornerShape(14.dp),
      color = if (allSelected) JTOrange else Color.White,
      border = if (allSelected) null else BorderStroke(1.dp, JTBorder),
      shadowElevation = if (allSelected) 2.dp else 1.dp,
      modifier = Modifier
        .width(72.dp)
        .heightIn(min = 84.dp)
        .clickable { onSelect(null) }
        .testTag("food_subcat_all")
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Icon(
          imageVector = Icons.Rounded.ShoppingBasket,
          contentDescription = appText("All Food", "تمام خوراک"),
          tint = if (allSelected) Color.White else JTOrangeDark,
          modifier = Modifier.size(30.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = appText("All Food", "تمام خوراک"),
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = if (allSelected) Color.White else JTTextPrimary,
          textAlign = TextAlign.Center,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    subcategories.forEach { child ->
      val selected = selectedSubcategoryId == child.id
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) JTOrange else JTBorder),
        shadowElevation = 1.dp,
        modifier = Modifier
          .width(72.dp)
          .heightIn(min = 84.dp)
          .clickable { onSelect(child.id) }
          .testTag("food_subcat_${child.id}")
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          CategoryVisualThumbnail(
            category = child,
            allCategories = allCategories,
            modifier = Modifier
              .size(44.dp)
              .clip(RoundedCornerShape(10.dp))
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = child.title,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) JTOrangeDark else JTTextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}
