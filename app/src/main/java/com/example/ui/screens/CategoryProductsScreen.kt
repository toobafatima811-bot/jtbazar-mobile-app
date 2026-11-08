package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.CategoryItem
import com.example.data.model.Product
import com.example.data.repository.MarketplaceRepository
import com.example.ui.theme.*

/** Native category listing: shows only products assigned to the selected live category. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryProductsScreen(
  category: CategoryItem,
  onBack: () -> Unit,
  onProductClick: (Product) -> Unit,
  onCartClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val products by MarketplaceRepository.products.collectAsState()
  val cartCount by MarketplaceRepository.cartCount.collectAsState()
  val isLoading by MarketplaceRepository.isLoading.collectAsState()
  val error by MarketplaceRepository.errorMessage.collectAsState()
  val categoryProducts = remember(products, category.id) {
    products.filter { it.categoryId == category.id }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = androidx.compose.ui.graphics.Color.White,
    topBar = {
      TopAppBar(
        title = { Text(category.title, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          TextButton(onClick = onCartClick) { Text("Cart ($cartCount)") }
        }
      )
    }
  ) { padding ->
    when {
      isLoading && categoryProducts.isEmpty() -> Box(
        Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center
      ) { CircularProgressIndicator(color = JTOrange) }
      error != null && categoryProducts.isEmpty() -> Column(
        Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text("Could not load this collection", fontWeight = FontWeight.Bold)
        Text(error.orEmpty(), color = JTTextSecondary, fontSize = 12.sp)
        TextButton(onClick = { MarketplaceRepository.refreshLiveCatalog() }) { Text("Retry") }
      }
      categoryProducts.isEmpty() -> Box(
        Modifier.fillMaxSize().padding(padding).padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Text("No published products in this collection yet.", color = JTTextSecondary)
      }
      else -> LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        item {
          Text(category.sampleItems, color = JTTextSecondary, fontSize = 13.sp)
          if (category.badge == "Coming soon") {
            Text("Coming soon", color = JTOrangeDark, fontWeight = FontWeight.Bold)
          }
        }
        items(categoryProducts, key = { it.id }) { product ->
          Card(
            modifier = Modifier.fillMaxWidth().clickable { onProductClick(product) },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = JTCardBg)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              AsyncImage(
                model = product.imageUrl?.takeIf { it.isNotBlank() }
                  ?: com.example.R.drawable.ic_launcher_background,
                contentDescription = product.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(92.dp)
              )
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(product.title, fontWeight = FontWeight.SemiBold, color = JTTextPrimary, maxLines = 2)
                Text(product.vendor, color = JTTextSecondary, fontSize = 11.sp)
                Text("PKR ${product.price}", color = JTOrangeDark, fontWeight = FontWeight.Bold)
                Text(product.inStockText, color = JTTextSecondary, fontSize = 10.sp)
              }
            }
          }
        }
      }
    }
  }
}
