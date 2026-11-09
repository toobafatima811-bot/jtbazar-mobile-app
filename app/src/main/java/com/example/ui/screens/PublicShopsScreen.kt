package com.example.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Product
import com.example.data.model.ShopRecord
import com.example.data.repository.MarketplaceRepository
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SellerReviewService
import org.json.JSONArray
import kotlinx.coroutines.launch

@Composable
fun PublicShopsScreen(onBack: () -> Unit, onProductClick: (Product) -> Unit) {
  val shops by MarketplaceRepository.shops.collectAsState()
  val products by MarketplaceRepository.products.collectAsState()
  val orders by MarketplaceRepository.orders.collectAsState()
  val user by SupabaseClient.currentUser.collectAsState()
  val scope = rememberCoroutineScope()
  var selectedShopId by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) { MarketplaceRepository.refreshShops() }

  Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
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
        items(shops, key = { it.id }) { shop ->
          val shopProducts = remember(products, shop.id, shop.name) {
            products.filter { product ->
              product.sellerId == shop.id || (product.sellerId.isNullOrBlank() && product.vendor.equals(shop.name, ignoreCase = true))
            }
          }
          var sellerReviews by remember(shop.id) { mutableStateOf(JSONArray()) }
          var reviewRating by remember(shop.id) { mutableIntStateOf(5) }
          var reviewComment by remember(shop.id) { mutableStateOf("") }
          var reviewOpen by remember(shop.id) { mutableStateOf(false) }
          LaunchedEffect(shop.id, selectedShopId) {
            if (selectedShopId == shop.id) {
              try { sellerReviews = SellerReviewService.listApproved(shop.id) } catch (_: Exception) { sellerReviews = JSONArray() }
            }
          }
          Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                selectedShopId = if (selectedShopId == shop.id) null else shop.id
              }) {
                if (!shop.logoUrl.isNullOrBlank()) {
                  AsyncImage(model = shop.logoUrl, contentDescription = shop.name, modifier = Modifier.size(58.dp))
                } else {
                  Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                  Text(shop.name, fontWeight = FontWeight.Bold)
                  Text("Verified seller • ${shopProducts.size.coerceAtLeast(shop.productCount)} products", style = MaterialTheme.typography.bodySmall)
                  if (shop.city.isNotBlank()) Text(shop.city, style = MaterialTheme.typography.bodySmall)
                }
                Text(if (selectedShopId == shop.id) "Hide" else "View", color = MaterialTheme.colorScheme.primary)
              }
              if (selectedShopId == shop.id) {
                Text(shop.description, style = MaterialTheme.typography.bodySmall)
                Text("Customer reviews (${sellerReviews.length()})", fontWeight = FontWeight.Bold)
                for (reviewIndex in 0 until sellerReviews.length()) {
                  val review = sellerReviews.optJSONObject(reviewIndex) ?: continue
                  Text("★ ${review.optInt("rating")}/5 • ${review.optString("comment")}", style = MaterialTheme.typography.bodySmall)
                }
                if (user != null) {
                  TextButton(onClick = { reviewOpen = !reviewOpen }) { Text(if (reviewOpen) "Close review" else "Write a seller review") }
                  if (reviewOpen) {
                    OutlinedTextField(value = reviewRating.toString(), onValueChange = { reviewRating = it.toIntOrNull()?.coerceIn(1, 5) ?: reviewRating }, label = { Text("Rating (1–5)") }, singleLine = true)
                    OutlinedTextField(value = reviewComment, onValueChange = { reviewComment = it }, label = { Text("Your review") }, minLines = 2)
                    Button(onClick = {
                      scope.launch {
                        try {
                          val deliveredIds = orders.filter { it.orderStatus.equals("DELIVERED", true) }.map { it.orderId }
                          SellerReviewService.submitForDeliveredOrder(shop.id, reviewRating, reviewComment, deliveredIds)
                          sellerReviews = SellerReviewService.listApproved(shop.id)
                          reviewComment = ""; reviewOpen = false
                        } catch (e: Exception) { /* Keep the form open so the customer can correct or retry. */ }
                      }
                    }) { Text("Submit review") }
                  }
                } else {
                  Text("Sign in and place a delivered order from this shop to leave a review.", style = MaterialTheme.typography.bodySmall)
                }
                if (shopProducts.isEmpty()) {
                  Text("No published products from this shop are currently loaded.", style = MaterialTheme.typography.bodySmall)
                } else {
                  shopProducts.forEach { product ->
                    Row(
                      Modifier.fillMaxWidth().clickable { onProductClick(product) }.padding(vertical = 6.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      AsyncImage(model = product.imageUrl, contentDescription = product.title, modifier = Modifier.size(52.dp))
                      Spacer(Modifier.width(10.dp))
                      Column(Modifier.weight(1f)) {
                        Text(product.title, fontWeight = FontWeight.SemiBold)
                        Text("PKR ${product.price}", style = MaterialTheme.typography.bodySmall)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
