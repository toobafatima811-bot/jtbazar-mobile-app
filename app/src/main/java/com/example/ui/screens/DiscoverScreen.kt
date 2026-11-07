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
        title = "Discover Lahore Bazar",
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
      // 1. Artisan Spotlight
      item {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = JTPurplePrimary,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = JTAmberContainer
            ) {
              Text(
                text = "ARTISAN HERITAGE",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = JTAmberDark,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Multan Kashigari Blue Pottery",
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
            Text(
              text = "Generations of potters turning indigenous Indus clay into hand-glazed heirloom ceramics.",
              fontSize = 11.sp,
              color = Color.White.copy(alpha = 0.85f),
              lineHeight = 15.sp
            )
          }
        }
      }

      // 2. Lahore Food Heritage
      item {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = Color(0xFFFFF9ED),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFDE68A)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Filled.Fastfood,
                contentDescription = null,
                tint = JTAmberDark,
                modifier = Modifier.size(24.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "Lahori Street Food & Nashta",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = JTTextPrimary
              )
              Text(
                text = "Piping hot Halwa Puri, Nihari & fresh parathas from Gulberg & Anarkali vendors.",
                fontSize = 11.sp,
                color = JTTextSecondary,
                lineHeight = 14.sp
              )
            }
          }
        }
      }

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
                  placeholder = painterResource(id = prod.drawableRes),
                  error = painterResource(id = prod.drawableRes),
                  modifier = Modifier.fillMaxSize()
                )
              } else {
                Image(
                  painter = painterResource(id = prod.drawableRes),
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
