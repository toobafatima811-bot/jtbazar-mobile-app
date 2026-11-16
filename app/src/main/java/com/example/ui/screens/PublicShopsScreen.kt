package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Product
import com.example.data.model.ShopRecord
import com.example.data.repository.MarketplaceRepository
import com.example.ui.components.rememberAdaptiveColumnCount
import com.example.ui.components.rememberSizedImageRequest
import com.example.data.supabase.AppErrorMapper
import com.example.data.supabase.ErrorContext
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SellerReviewService
import org.json.JSONArray
import kotlinx.coroutines.launch

@Composable
fun PublicShopsScreen(
  onBack: () -> Unit,
  onProductClick: (Product) -> Unit,
  initialShopId: String? = null,
  onShopSelected: (String?) -> Unit = {}
) {
  val shops by MarketplaceRepository.shops.collectAsState()
  val products by MarketplaceRepository.products.collectAsState()
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
    if (shops.isEmpty()) MarketplaceRepository.refreshShops()
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
      products = productsForShop(selectedShop!!),
      onBack = exitShopDetail,
      onProductClick = onProductClick
    )
  } else {
    Column(Modifier.fillMaxSize()) {
      Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.size(48.dp).testTag("public_shops_back_button")
        ) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(Modifier.weight(1f)) {
          Text("Trusted JTBazar Shops", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
          Text("Browse approved sellers and their published products", style = MaterialTheme.typography.bodySmall)
        }
      }
      HorizontalDivider()
      if (shops.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("No public shops are available right now.")
        }
      } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          items(shops, key = { it.id }, contentType = { "shop_card" }) { shop ->
            val shopProductCount = remember(productsBySeller, productsByVendorName, shop.id, shop.name, shop.productCount) {
              productsForShop(shop).size.coerceAtLeast(shop.productCount)
            }
            Card(
              modifier = Modifier.fillMaxWidth().clickable {
                selectedShop = shop
                onShopSelected(shop.id)
              },
              shape = RoundedCornerShape(14.dp)
            ) {
              Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                if (!shop.logoUrl.isNullOrBlank()) {
                  AsyncImage(
                    model = rememberSizedImageRequest(data = shop.logoUrl, widthPx = 180, heightPx = 180),
                    contentDescription = shop.name,
                    modifier = Modifier.size(58.dp)
                  )
                } else {
                  Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                  Text(shop.name, fontWeight = FontWeight.Bold)
                  Text((if (shop.isVerified) "Verified seller" else "Seller") + " • " + shopProductCount + " products", style = MaterialTheme.typography.bodySmall)
                  if (shop.city.isNotBlank()) Text(shop.city, style = MaterialTheme.typography.bodySmall)
                }
                Text("View", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PublicShopDetailScreen(
  shop: ShopRecord,
  products: List<Product>,
  onBack: () -> Unit,
  onProductClick: (Product) -> Unit
) {
  val user by SupabaseClient.currentUser.collectAsState()
  val orders by MarketplaceRepository.orders.collectAsState()
  val scope = rememberCoroutineScope()
  val productColumns = rememberAdaptiveColumnCount(minCellWidth = 150.dp, horizontalPadding = 32.dp, spacing = 12.dp)
  var sellerReviews by remember(shop.id) { mutableStateOf(JSONArray()) }
  var reviewRating by remember(shop.id) { mutableIntStateOf(5) }
  var reviewComment by remember(shop.id) { mutableStateOf("") }
  var reviewOpen by remember(shop.id) { mutableStateOf(false) }
  var reviewMessage by remember(shop.id) { mutableStateOf<String?>(null) }
  var reviewSubmitting by remember(shop.id) { mutableStateOf(false) }

  LaunchedEffect(shop.id) {
    sellerReviews = try { SellerReviewService.listApproved(shop.id) } catch (_: Exception) { JSONArray() }
  }

  Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(
        onClick = onBack,
        modifier = Modifier.size(48.dp).testTag("public_shop_detail_back_button")
      ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to shops")
      }
      Text("Shop details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
    HorizontalDivider()
    val chunkedProducts = remember(products, productColumns) {
      products.chunked(productColumns)
    }
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
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
                  modifier = Modifier.size(72.dp)
                )
              } else {
                Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(64.dp))
              }
              Spacer(Modifier.width(12.dp))
              Column(Modifier.weight(1f)) {
                Text(shop.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text((if (shop.isVerified) "Verified seller" else "Seller") + " • " + products.size.coerceAtLeast(shop.productCount) + " products", style = MaterialTheme.typography.bodySmall)
                if (shop.city.isNotBlank()) Text(shop.city, style = MaterialTheme.typography.bodySmall)
              }
            }
            if (shop.description.isNotBlank()) Text(shop.description, style = MaterialTheme.typography.bodyMedium)
          }
        }
      }

      item(key = "shop_reviews_header") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Customer reviews (${sellerReviews.length()})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          for (reviewIndex in 0 until sellerReviews.length()) {
            val review = sellerReviews.optJSONObject(reviewIndex) ?: continue
            Card(modifier = Modifier.fillMaxWidth()) {
              Text("★ ${review.optInt("rating")}/5 • ${review.optString("comment")}", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
          }
          if (user != null) {
            TextButton(onClick = { reviewOpen = !reviewOpen }) {
              Text(if (reviewOpen) "Close review" else "Write a seller review")
            }
            if (reviewOpen) {
              OutlinedTextField(
                value = reviewRating.toString(),
                onValueChange = { reviewRating = it.toIntOrNull()?.coerceIn(1, 5) ?: reviewRating },
                label = { Text("Rating (1–5)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )
              OutlinedTextField(
                value = reviewComment,
                onValueChange = { reviewComment = it },
                label = { Text("Your review") },
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
                      reviewMessage = "Thank you! Your review has been submitted."
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
                Text(if (reviewSubmitting) "Submitting..." else "Submit review")
              }
              reviewMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            }
          } else {
            Text("Sign in and place a delivered order from this shop to leave a review.", style = MaterialTheme.typography.bodySmall)
          }
        }
      }

      item(key = "shop_products_header") {
        Text("Shop products (${products.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }

      if (products.isEmpty()) {
        item(key = "shop_products_empty") {
          Text("No published products from this shop are currently available.")
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
    }
  }
}
