package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.CategoryItem
import com.example.data.model.Product
import com.example.data.model.ShopRecord
import com.example.data.repository.FoodCategoryResolver
import com.example.data.repository.MarketplaceRepository
import com.example.data.supabase.AppErrorMapper
import com.example.data.supabase.ErrorContext
import com.example.data.supabase.SellerReviewService
import com.example.data.supabase.ShopScheduleInfo
import com.example.data.supabase.SupabaseApiService
import com.example.data.supabase.SupabaseCategoryDto
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.VendorShopCatalogFilter
import com.example.data.supabase.WeeklyShopHours
import com.example.localization.AppLanguageManager
import com.example.localization.appText
import com.example.ui.components.rememberAdaptiveColumnCount
import com.example.ui.components.rememberSizedImageRequest
import com.example.ui.theme.JTBorder
import com.example.ui.theme.JTRed
import com.example.ui.theme.JTRedContainer
import com.example.ui.theme.JTTextPrimary
import com.example.ui.theme.JTTextSecondary
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.Instant

@Composable
fun PublicShopsScreen(
  onBack: () -> Unit,
  onProductClick: (Product) -> Unit,
  initialShopId: String? = null,
  onShopSelected: (String?) -> Unit = {}
) {
  val shops by MarketplaceRepository.shops.collectAsState()
  val products by MarketplaceRepository.products.collectAsState()
  val categories by MarketplaceRepository.categories.collectAsState()
  var selectedShop by remember { mutableStateOf<ShopRecord?>(null) }

  val productsBySeller = remember(products) {
    products.groupBy { it.sellerId.orEmpty() }
  }
  val productsByVendorName = remember(products) {
    products.filter { it.sellerId.isNullOrBlank() }.groupBy { it.vendor.trim().lowercase() }
  }
  fun productsForShop(shop: ShopRecord): List<Product> {
    return productsBySeller[shop.id] ?: productsByVendorName[shop.name.trim().lowercase()].orEmpty()
  }

  LaunchedEffect(Unit) {
    MarketplaceRepository.refreshShops(force = shops.isEmpty())
  }
  LaunchedEffect(shops, initialShopId) {
    if (!initialShopId.isNullOrBlank()) {
      shops.firstOrNull { it.id == initialShopId }?.let { selectedShop = it }
    }
  }

  val exitShopDetail = {
    selectedShop = null
    onShopSelected(null)
  }

  if (selectedShop != null) {
    BackHandler { exitShopDetail() }
    PublicShopDetailScreen(
      shop = selectedShop!!,
      fallbackProducts = productsForShop(selectedShop!!),
      cachedCategories = categories,
      onBack = exitShopDetail,
      onProductClick = onProductClick
    )
  } else {
    Column(Modifier.fillMaxSize()) {
      Row(
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier
            .size(48.dp)
            .testTag("public_shops_back_button")
        ) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = appText("Back", "واپس"))
        }
        Column(Modifier.weight(1f)) {
          Text(
            appText("Trusted JTBazar Shops", "جے ٹی بازار کی مستند دکانیں"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
          Text(
            appText("Browse approved sellers and their published products", "منظور شدہ فروخت کنندگان اور ان کی مصنوعات دیکھیں"),
            style = MaterialTheme.typography.bodySmall
          )
        }
        IconButton(
          onClick = { MarketplaceRepository.refreshShops(force = true) },
          modifier = Modifier
            .size(48.dp)
            .testTag("public_shops_refresh_button")
        ) {
          Icon(Icons.Rounded.Refresh, contentDescription = appText("Refresh shops", "دکانیں تازہ کریں"))
        }
      }
      HorizontalDivider()
      if (shops.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(24.dp)
          ) {
            Text(appText("No public shops are available right now.", "فی الحال کوئی عوامی دکانیں دستیاب نہیں ہیں۔"))
            OutlinedButton(onClick = { MarketplaceRepository.refreshShops(force = true) }) {
              Text(appText("Refresh", "تازہ کریں"))
            }
          }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          items(shops, key = { it.id }, contentType = { "shop_card" }) { shop ->
            val shopProductCount = remember(productsBySeller, productsByVendorName, shop.id, shop.name, shop.productCount) {
              productsForShop(shop).size.coerceAtLeast(shop.productCount)
            }
            val listScheduleStatus = remember(shop.id, shop.shopIsOpen, shop.weeklyHoursRaw) {
              if (shop.shopIsOpen != null || !shop.weeklyHoursRaw.isNullOrBlank()) {
                ShopScheduleInfo(
                  sellerId = shop.id,
                  shopIsOpen = shop.shopIsOpen ?: true,
                  weeklyHours = WeeklyShopHours.parse(shop.weeklyHoursRaw)
                ).evaluateAt()
              } else {
                null
              }
            }
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  selectedShop = shop
                  onShopSelected(shop.id)
                }
                .testTag("public_shop_card_${shop.id}"),
              shape = RoundedCornerShape(14.dp)
            ) {
              Row(
                Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                if (!shop.logoUrl.isNullOrBlank()) {
                  AsyncImage(
                    model = rememberSizedImageRequest(data = shop.logoUrl, widthPx = 180, heightPx = 180),
                    contentDescription = shop.name,
                    modifier = Modifier
                      .size(58.dp)
                      .clip(RoundedCornerShape(12.dp))
                  )
                } else {
                  Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Text(shop.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                    if (listScheduleStatus != null) {
                      PublicShopStatusPill(status = listScheduleStatus)
                    }
                  }
                  Text(
                    (if (shop.isVerified) appText("Verified seller", "تصدیق شدہ فروخت کنندہ") else appText("Seller", "فروخت کنندہ")) +
                      " • " + appText("$shopProductCount products", "$shopProductCount مصنوعات"),
                    style = MaterialTheme.typography.bodySmall
                  )
                  if (shop.city.isNotBlank()) Text(shop.city, style = MaterialTheme.typography.bodySmall)
                }
                Text(appText("View", "دیکھیں"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }
      }
    }
  }
}

private fun mapCategoryDtosToItems(dtos: List<SupabaseCategoryDto>): List<CategoryItem> {
  val drafts = dtos.map { dto ->
    CategoryItem(
      id = dto.id,
      title = dto.title,
      countText = dto.countText,
      sampleItems = dto.sampleItems,
      drawableRes = 0,
      imageUrl = dto.imageUrl,
      badge = dto.badge,
      slug = dto.slug,
      parentId = dto.parentId,
      isFoodCategory = dto.isFoodCategory,
      displayOrder = dto.displayOrder
    )
  }
  return drafts.map { item ->
    item.copy(drawableRes = FoodCategoryResolver.resolveFoodDrawableOrNull(item, drafts) ?: 0)
  }
}

@Composable
internal fun PublicShopDetailScreen(
  shop: ShopRecord,
  fallbackProducts: List<Product>,
  cachedCategories: List<CategoryItem> = emptyList(),
  onBack: () -> Unit,
  onProductClick: (Product) -> Unit,
  nowProvider: () -> Instant = { Instant.now() },
  scheduleRefreshIntervalMs: Long = 30_000L
) {
  val user by SupabaseClient.currentUser.collectAsState()
  val wishlistedIds by MarketplaceRepository.wishlistedIds.collectAsState()
  val scope = rememberCoroutineScope()
  val productColumns = rememberAdaptiveColumnCount(minCellWidth = 150.dp, horizontalPadding = 32.dp, spacing = 12.dp)

  var currentInstant by remember(shop.id) { mutableStateOf(nowProvider()) }
  var scheduleInfo by remember(shop.id) {
    mutableStateOf(
      ShopScheduleInfo(
        sellerId = shop.id,
        shopIsOpen = shop.shopIsOpen ?: true,
        weeklyHours = WeeklyShopHours.parse(shop.weeklyHoursRaw)
      )
    )
  }
  var isRefreshingSchedule by remember(shop.id) { mutableStateOf(true) }
  var scheduleError by remember(shop.id) { mutableStateOf<String?>(null) }

  var vendorProducts by remember(shop.id) {
    mutableStateOf(VendorShopCatalogFilter.filterActiveVendorProducts(fallbackProducts))
  }
  var isLoadingProducts by remember(shop.id) { mutableStateOf(true) }
  var productsError by remember(shop.id) { mutableStateOf<String?>(null) }

  var shopCategoriesCatalog by remember(shop.id) { mutableStateOf(cachedCategories) }
  var selectedCategoryId by remember(shop.id) { mutableStateOf<String?>(null) }

  var sellerReviews by remember(shop.id) { mutableStateOf(JSONArray()) }
  var reviewRating by remember(shop.id) { mutableIntStateOf(5) }
  var reviewComment by remember(shop.id) { mutableStateOf("") }
  var reviewOpen by remember(shop.id) { mutableStateOf(false) }
  var reviewMessage by remember(shop.id) { mutableStateOf<String?>(null) }
  var reviewSubmitting by remember(shop.id) { mutableStateOf(false) }

  val categoryOptions = remember(vendorProducts, shopCategoriesCatalog) {
    VendorShopCatalogFilter.buildCategoriesForProducts(vendorProducts, shopCategoriesCatalog)
  }

  LaunchedEffect(categoryOptions) {
    if (selectedCategoryId != null && categoryOptions.none { it.id == selectedCategoryId }) {
      selectedCategoryId = null
    }
  }

  val filteredProducts = remember(vendorProducts, selectedCategoryId, shopCategoriesCatalog, wishlistedIds) {
    VendorShopCatalogFilter
      .filterProductsByCategory(vendorProducts, selectedCategoryId, shopCategoriesCatalog)
      .map { prod -> if (prod.isWishlisted == (prod.id in wishlistedIds)) prod else prod.copy(isWishlisted = prod.id in wishlistedIds) }
  }

  val scheduleStatus = remember(scheduleInfo, currentInstant) {
    scheduleInfo.evaluateAt(currentInstant)
  }

  suspend fun refreshShopData() {
    isRefreshingSchedule = true
    if (vendorProducts.isEmpty()) isLoadingProducts = true
    productsError = null
    scheduleError = null

    coroutineScope {
      val scheduleDeferred = async { SupabaseApiService.fetchPublicShopSchedule(shop.id) }
      val productsDeferred = async { SupabaseApiService.fetchVendorPublicProducts(shop.id) }
      val categoriesDeferred = async { SupabaseApiService.fetchCategories(forceRefresh = true) }
      val reviewsDeferred = async { runCatching { SellerReviewService.listApproved(shop.id) }.getOrNull() }

      categoriesDeferred.await()
        .onSuccess { dtoList ->
          if (dtoList.isNotEmpty()) {
            shopCategoriesCatalog = mapCategoryDtosToItems(dtoList)
          }
        }

      scheduleDeferred.await()
        .onSuccess { info ->
          scheduleInfo = info
          scheduleError = null
        }
        .onFailure { err ->
          scheduleError = AppErrorMapper.toCustomerMessage(err, ErrorContext.GENERAL)
        }

      productsDeferred.await()
        .onSuccess { dtoList ->
          val active = VendorShopCatalogFilter.filterActiveVendorProducts(
            dtoList.map { MarketplaceRepository.mapDtoToProduct(it, wishlistedIds) }
          )
          vendorProducts = active
          productsError = null
        }
        .onFailure { err ->
          productsError = AppErrorMapper.toCustomerMessage(err, ErrorContext.PRODUCT_LOAD)
        }

      reviewsDeferred.await()?.let { sellerReviews = it }
    }

    currentInstant = nowProvider()
    isRefreshingSchedule = false
    isLoadingProducts = false
  }

  LaunchedEffect(shop.id) {
    refreshShopData()
    while (isActive) {
      delay(scheduleRefreshIntervalMs.coerceAtLeast(5_000L))
      currentInstant = nowProvider()
      SupabaseApiService.fetchPublicShopSchedule(shop.id)
        .onSuccess { info ->
          scheduleInfo = info
          scheduleError = null
        }
    }
  }

  Column(Modifier.fillMaxSize()) {
    Row(
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(
        onClick = onBack,
        modifier = Modifier
          .size(48.dp)
          .testTag("public_shop_detail_back_button")
      ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = appText("Back to shops", "دکانوں پر واپس"))
      }
      Text(
        text = appText("Shop details", "دکان کی تفصیلات"),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1f)
      )
      IconButton(
        onClick = { scope.launch { refreshShopData() } },
        enabled = !isRefreshingSchedule,
        modifier = Modifier
          .size(48.dp)
          .testTag("public_shop_refresh_button")
      ) {
        if (isRefreshingSchedule) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
          Icon(Icons.Rounded.Refresh, contentDescription = appText("Refresh shop", "دکان تازہ کریں"))
        }
      }
    }
    HorizontalDivider()

    val chunkedProducts = remember(filteredProducts, productColumns) {
      filteredProducts.chunked(productColumns)
    }

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .testTag("public_shop_detail_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      item(key = "shop_detail_header") {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (!shop.logoUrl.isNullOrBlank()) {
                AsyncImage(
                  model = rememberSizedImageRequest(data = shop.logoUrl, widthPx = 180, heightPx = 180),
                  contentDescription = shop.name,
                  modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                )
              } else {
                Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(64.dp))
              }
              Spacer(Modifier.width(12.dp))
              Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Text(
                    text = shop.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                  )
                  PublicShopStatusPill(
                    status = scheduleStatus,
                    modifier = Modifier.testTag("public_shop_open_status_badge")
                  )
                }
                Text(
                  text = scheduleStatus.detailLabel,
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.Medium,
                  color = JTTextSecondary,
                  modifier = Modifier.testTag("public_shop_status_detail")
                )
                val productCountLabel = vendorProducts.size.coerceAtLeast(shop.productCount)
                Text(
                  (if (shop.isVerified) appText("Verified seller", "تصدیق شدہ فروخت کنندہ") else appText("Seller", "فروخت کنندہ")) +
                    " • " + appText("$productCountLabel products", "$productCountLabel مصنوعات"),
                  style = MaterialTheme.typography.bodySmall
                )
                if (shop.city.isNotBlank()) Text(shop.city, style = MaterialTheme.typography.bodySmall)
              }
            }
            if (shop.description.isNotBlank()) Text(shop.description, style = MaterialTheme.typography.bodyMedium)
          }
        }
      }

      if (!scheduleStatus.isOpen) {
        item(key = "shop_closed_notice") {
          PublicShopClosedBanner(status = scheduleStatus)
        }
      }

      item(key = "shop_weekly_hours") {
        PublicShopWeeklyHoursCard(
          weeklyHours = scheduleInfo.weeklyHours,
          status = scheduleStatus,
          isRefreshingSchedule = isRefreshingSchedule,
          scheduleError = scheduleError,
          onRetrySchedule = { scope.launch { refreshShopData() } }
        )
      }

      if (categoryOptions.isNotEmpty()) {
        item(key = "shop_categories_filter") {
          PublicShopCategoryFilterStrip(
            totalActiveCount = vendorProducts.size,
            categories = categoryOptions,
            selectedCategoryId = selectedCategoryId,
            onSelectCategory = { selectedCategoryId = it }
          )
        }
      }

      item(key = "shop_products_header") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = appText(
              "Shop products (${filteredProducts.size})",
              "دکان کی مصنوعات (${filteredProducts.size})"
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          if (selectedCategoryId != null) {
            TextButton(onClick = { selectedCategoryId = null }) {
              Text(appText("Show all", "سب دکھائیں"))
            }
          }
        }
      }

      if (!productsError.isNullOrBlank()) {
        item(key = "shop_products_error") {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = JTRedContainer,
            border = BorderStroke(1.dp, JTRed.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text(
                text = productsError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = JTTextPrimary,
                modifier = Modifier.weight(1f)
              )
              OutlinedButton(
                onClick = { scope.launch { refreshShopData() } },
                modifier = Modifier
                  .heightIn(min = 40.dp)
                  .testTag("public_shop_products_retry")
              ) {
                Text(appText("Retry", "دوبارہ کوشش"))
              }
            }
          }
        }
      }

      if (isLoadingProducts && filteredProducts.isEmpty()) {
        item(key = "shop_products_loading") {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator()
          }
        }
      } else if (filteredProducts.isEmpty()) {
        item(key = "shop_products_empty") {
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, JTBorder),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("public_shop_products_empty")
          ) {
            Text(
              text = if (selectedCategoryId != null) {
                appText(
                  "No active products are currently available in this category.",
                  "اس زمرے میں فی الحال کوئی فعال مصنوعات دستیاب نہیں ہیں۔"
                )
              } else {
                appText(
                  "No published products from this shop are currently available.",
                  "اس دکان کی کوئی شائع شدہ مصنوعات فی الحال دستیاب نہیں ہیں۔"
                )
              },
              style = MaterialTheme.typography.bodyMedium,
              color = JTTextSecondary,
              modifier = Modifier.padding(16.dp)
            )
          }
        }
      } else {
        items(
          items = chunkedProducts,
          key = { row -> "shop_prod_${row.firstOrNull()?.id ?: row.hashCode()}" },
          contentType = { "product_row" }
        ) { productRow ->
          ProductGridRow(
            rowItems = productRow,
            columns = productColumns,
            onProductClick = onProductClick,
            onAddToCart = { MarketplaceRepository.addToCart(it) },
            onToggleWishlist = { MarketplaceRepository.toggleWishlist(it.id, it) }
          )
        }
      }

      item(key = "shop_reviews_header") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            appText("Customer reviews (${sellerReviews.length()})", "صارفین کے تبصرے (${sellerReviews.length()})"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          for (reviewIndex in 0 until sellerReviews.length()) {
            val review = sellerReviews.optJSONObject(reviewIndex) ?: continue
            Card(modifier = Modifier.fillMaxWidth()) {
              Text(
                "★ ${review.optInt("rating")}/5 • ${review.optString("comment")}",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
              )
            }
          }
          if (user != null) {
            TextButton(onClick = { reviewOpen = !reviewOpen }) {
              Text(if (reviewOpen) appText("Close review", "تبصرہ بند کریں") else appText("Write a seller review", "فروخت کنندہ پر تبصرہ لکھیں"))
            }
            if (reviewOpen) {
              OutlinedTextField(
                value = reviewRating.toString(),
                onValueChange = { reviewRating = it.toIntOrNull()?.coerceIn(1, 5) ?: reviewRating },
                label = { Text(appText("Rating (1–5)", "ریٹنگ (1 تا 5)")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )
              OutlinedTextField(
                value = reviewComment,
                onValueChange = { reviewComment = it },
                label = { Text(appText("Your review", "آپ کا تبصرہ")) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
              )
              Button(
                onClick = {
                  scope.launch {
                    try {
                      reviewSubmitting = true
                      reviewMessage = null
                      SellerReviewService.submitForDeliveredOrder(shop.id, reviewRating, reviewComment)
                      sellerReviews = SellerReviewService.listApproved(shop.id)
                      reviewComment = ""
                      reviewOpen = false
                      reviewMessage = AppLanguageManager.tr("Thank you! Your review has been submitted.", "شکریہ! آپ کا تبصرہ جمع ہو گیا ہے۔")
                    } catch (error: Exception) {
                      reviewMessage = AppErrorMapper.toCustomerMessage(error, ErrorContext.REVIEWS)
                    } finally {
                      reviewSubmitting = false
                    }
                  }
                },
                enabled = reviewComment.trim().length >= 3 && !reviewSubmitting
              ) {
                if (reviewSubmitting) {
                  CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                  Spacer(Modifier.width(8.dp))
                }
                Text(if (reviewSubmitting) appText("Submitting...", "جمع ہو رہا ہے...") else appText("Submit review", "تبصرہ جمع کرائیں"))
              }
              reviewMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            }
          } else {
            Text(
              appText(
                "Sign in and place a delivered order from this shop to leave a review.",
                "تبصرہ لکھنے کے لیے سائن ان کریں اور اس دکان سے مکمل آرڈر دیں۔"
              ),
              style = MaterialTheme.typography.bodySmall
            )
          }
        }
      }
    }
  }
}
