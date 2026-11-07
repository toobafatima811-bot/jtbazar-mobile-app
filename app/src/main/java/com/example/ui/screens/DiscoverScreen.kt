package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.Product
import com.example.data.repository.MarketplaceRepository
import com.example.ui.components.JTBazarTopBar
import com.example.ui.theme.*

@Composable
fun DiscoverScreen(
  onProductClick: (Product) -> Unit,
  onCartClick: () -> Unit,
  onAccountClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val cartCount by MarketplaceRepository.cartCount.collectAsState()
  val currentLocation by MarketplaceRepository.currentLocation.collectAsState()
  val products by MarketplaceRepository.products.collectAsState()

  Scaffold(
    containerColor = Color.White,
    topBar = {
      JTBazarTopBar(
        title = "Discover",
        onCartClick = onCartClick,
        onAvatarClick = onAccountClick,
        cartCount = cartCount,
        currentLocation = currentLocation
      )
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 3. Featured Pick
      item {
        Text(
          text = "Featured Handcrafted Deals",
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = JTTextPrimary
        )
      }

      if (products.isEmpty()) {
        item {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = JTSurfaceVariant,
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 24.dp)
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(Icons.Filled.Search, contentDescription = null, tint = JTPurplePrimary, modifier = Modifier.size(36.dp))
              Spacer(modifier = Modifier.height(10.dp))
              Text("No products discovered", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = JTTextPrimary)
              Spacer(modifier = Modifier.height(4.dp))
              Text("Live items from verified Pakistani vendors will appear here.", fontSize = 12.sp, color = JTTextSecondary)
            }
          }
        }
      }

      items(products) { prod ->
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = JTCardBg,
          border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onProductClick(prod) }
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(JTSurfaceVariant)
            ) {
              if (!prod.imageUrl.isNullOrBlank()) {
                coil.compose.AsyncImage(
                  model = prod.imageUrl,
                  contentDescription = prod.title,
                  contentScale = ContentScale.Crop,
                  placeholder = painterResource(id = com.example.R.drawable.ic_launcher_background),
                  error = painterResource(id = com.example.R.drawable.ic_launcher_background),
                  modifier = Modifier.fillMaxSize()
                )
              } else {
                Image(
                  painter = painterResource(id = com.example.R.drawable.ic_launcher_background),
                  contentDescription = prod.title,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
              }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = prod.title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = JTTextPrimary,
                maxLines = 1
              )
              Text(
                text = prod.vendor,
                fontSize = 10.sp,
                color = JTTextSecondary
              )
              Text(
                text = "Rs. ${String.format("%,d", prod.price)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = JTPurplePrimary
              )
            }
            Icon(
              imageVector = Icons.Filled.ChevronRight,
              contentDescription = null,
              tint = JTTextSecondary
            )
          }
        }
      }
    }
  }
}
