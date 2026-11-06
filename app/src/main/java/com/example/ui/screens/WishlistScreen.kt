package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Product
import com.example.data.repository.MarketplaceRepository
import com.example.ui.components.JTBazarTopBar
import com.example.ui.theme.*

@Composable
fun WishlistScreen(
  onProductClick: (Product) -> Unit,
  onCartClick: () -> Unit,
  onAccountClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val products by MarketplaceRepository.products.collectAsState()
  val wishlisted = products.filter { it.isWishlisted }
  val cartCount by MarketplaceRepository.cartCount.collectAsState()
  val currentLocation by MarketplaceRepository.currentLocation.collectAsState()

  Scaffold(
    containerColor = Color.White,
    topBar = {
      JTBazarTopBar(
        title = "My Wishlist",
        onCartClick = onCartClick,
        onAvatarClick = onAccountClick,
        cartCount = cartCount,
        currentLocation = currentLocation
      )
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    if (wishlisted.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(24.dp)
        ) {
          Box(
            modifier = Modifier
              .size(64.dp)
              .clip(RoundedCornerShape(20.dp))
              .background(JTRedContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Filled.Favorite,
              contentDescription = null,
              tint = JTRed,
              modifier = Modifier.size(32.dp)
            )
          }
          Spacer(modifier = Modifier.height(14.dp))
          Text(
            text = "Your Wishlist is Empty",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = JTTextPrimary
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Tap the heart icon on any craft or fresh grocery item to save it for later.",
            fontSize = 12.sp,
            color = JTTextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        items(wishlisted) { product ->
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = JTCardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onProductClick(product) }
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(72.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(JTSurfaceVariant)
              ) {
                Image(
                  painter = painterResource(id = product.drawableRes),
                  contentDescription = product.title,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
              }

              Spacer(modifier = Modifier.width(12.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = product.title,
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = JTTextPrimary,
                  maxLines = 2
                )
                Text(
                  text = product.vendor,
                  fontSize = 11.sp,
                  color = JTTextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Rs. ${String.format("%,d", product.price)}",
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 14.sp,
                  color = JTPurplePrimary
                )
              }

              Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                IconButton(
                  onClick = { MarketplaceRepository.toggleWishlist(product.id) },
                  modifier = Modifier.size(32.dp)
                ) {
                  Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Remove",
                    tint = JTRed,
                    modifier = Modifier.size(18.dp)
                  )
                }

                Button(
                  onClick = {
                    MarketplaceRepository.addToCart(product)
                  },
                  shape = RoundedCornerShape(8.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = JTPurplePrimary),
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                  modifier = Modifier.height(30.dp)
                ) {
                  Icon(
                    imageVector = Icons.Outlined.ShoppingBag,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Add", fontSize = 11.sp)
                }
              }
            }
          }
        }
      }
    }
  }
}
