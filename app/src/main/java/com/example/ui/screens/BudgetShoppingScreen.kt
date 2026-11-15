package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Product
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

@Composable
fun BudgetShoppingScreen(
  onProductClick: (Product) -> Unit,
  onCartClick: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  listState: LazyListState = rememberLazyListState(),
  paginator: CatalogPaginator? = null
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val activePaginator = paginator ?: remember(scope) {
    MarketplaceRepository.createCatalogPaginator(scope)
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

  var selectedBudget by remember { mutableIntStateOf(1000) }
  var customBudgetInput by remember { mutableStateOf("") }
  var isCustomBudget by remember { mutableStateOf(false) }
  var debouncedBudget by remember { mutableIntStateOf(1000) }

  val activeBudget = if (isCustomBudget) {
    (customBudgetInput.toIntOrNull() ?: 1000).coerceAtLeast(1)
  } else {
    selectedBudget
  }

  LaunchedEffect(activeBudget, isCustomBudget) {
    if (isCustomBudget) {
      delay(350L)
    }
    debouncedBudget = activeBudget
  }

  LaunchedEffect(debouncedBudget) {
    val targetQuery = CatalogQuery(
      maxPrice = debouncedBudget,
      sortBy = ProductSortOption.PRICE_LOW_HIGH
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

  Scaffold(
    containerColor = Color.White,
    topBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp).testTag("budget_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = JTTextPrimary
            )
          }
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Budget Shopping",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = JTTextPrimary
          )
        }

        IconButton(
          onClick = onCartClick,
          modifier = Modifier.size(48.dp).testTag("budget_cart_button")
        ) {
          BadgedBox(
            badge = {
              if (cartCount > 0) {
                Badge(containerColor = JTPurplePrimary) {
                  Text("$cartCount", color = Color.White)
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
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      Surface(
        color = JTSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "Find products within your budget",
            fontSize = 13.sp,
            color = JTTextSecondary
          )
          Spacer(modifier = Modifier.height(10.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            listOf(500, 1000, 2000).forEach { amount ->
              FilterChip(
                selected = !isCustomBudget && selectedBudget == amount,
                onClick = {
                  selectedBudget = amount
                  isCustomBudget = false
                },
                label = {
                  Text(
                    text = "Under Rs. $amount",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                  )
                },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = JTOrange,
                  selectedLabelColor = Color.White
                )
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            OutlinedTextField(
              value = customBudgetInput,
              onValueChange = {
                if (it.all { char -> char.isDigit() }) {
                  customBudgetInput = it
                  if (it.isNotBlank()) isCustomBudget = true
                }
              },
              placeholder = { Text("Custom PKR budget", fontSize = 12.sp) },
              prefix = { Text("Rs. ", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 50.dp)
                .testTag("budget_custom_input")
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (isCustomBudget && customBudgetInput.isNotBlank()) {
              Button(
                onClick = {
                  val parsed = (customBudgetInput.toIntOrNull() ?: 1000).coerceAtLeast(1)
                  debouncedBudget = parsed
                  activePaginator.resetAndLoad(
                    CatalogQuery(maxPrice = parsed, sortBy = ProductSortOption.PRICE_LOW_HIGH)
                  )
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = JTPurplePrimary),
                modifier = Modifier.height(50.dp)
              ) {
                Text("Apply", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }

          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Showing items up to Rs. ${String.format("%,d", debouncedBudget)} (Delivery calculated at checkout)",
            fontSize = 11.sp,
            color = JTTextSecondary
          )
        }
      }

      when {
        feedState.isInitialLoading && feedState.items.isEmpty() -> {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp)
          ) {
            item { ProductSkeletonGrid(columns = productGridColumns, rows = 3) }
          }
        }
        feedState.initialError != null && feedState.items.isEmpty() -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FriendlyErrorState(
              title = "Products load nahi ho sake",
              message = feedState.initialError ?: "Please check your connection and try again.",
              onRetry = { activePaginator.retry() }
            )
          }
        }
        feedState.items.isEmpty() -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FriendlyEmptyState(
              title = "No products found under Rs. ${String.format("%,d", debouncedBudget)}",
              message = "Try increasing your budget or exploring full marketplace categories.",
              actionLabel = if (debouncedBudget < 2000) "Try Under Rs. 2,000" else null,
              onAction = if (debouncedBudget < 2000) {
                {
                  selectedBudget = 2000
                  isCustomBudget = false
                }
              } else null
            )
          }
        }
        else -> {
          val chunkedProducts = feedState.items.chunked(productGridColumns)
          LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            items(
              items = chunkedProducts,
              key = { row -> row.joinToString("_") { it.id } }
            ) { rowProducts ->
              ProductGridRow(
                rowItems = rowProducts,
                columns = productGridColumns,
                onProductClick = onProductClick,
                onAddToCart = { product ->
                  MarketplaceRepository.addToCart(product, 1)
                  if (MarketplaceRepository.canAddProductToCart(product)) {
                    Toast.makeText(context, "Added to cart!", Toast.LENGTH_SHORT).show()
                  }
                },
                onToggleWishlist = { product ->
                  MarketplaceRepository.toggleWishlist(product.id, product)
                }
              )
            }

            item(key = "budget_pagination_footer") {
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
}
