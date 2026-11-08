package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.Product
import com.example.data.repository.MarketplaceRepository
import com.example.ui.theme.*

@Composable
fun BudgetShoppingScreen(
  onProductClick: (Product) -> Unit,
  onCartClick: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val products by MarketplaceRepository.products.collectAsState()
  val cartCount by MarketplaceRepository.cartCount.collectAsState()

  var selectedBudget by remember { mutableIntStateOf(1000) }
  var customBudgetInput by remember { mutableStateOf("") }
  var isCustomBudget by remember { mutableStateOf(false) }

  val activeBudget = if (isCustomBudget) {
    customBudgetInput.toIntOrNull() ?: 1000
  } else {
    selectedBudget
  }

  val filteredProducts = remember(products, activeBudget) {
    products.filter { it.price <= activeBudget }
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
            modifier = Modifier.testTag("budget_back_button")
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
          modifier = Modifier.testTag("budget_cart_button")
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
      // Budget Selector Header
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

          // Preset Chips: PKR 500, PKR 1,000, PKR 2,000
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

          // Custom Budget input
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
                .height(50.dp)
                .testTag("budget_custom_input")
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (isCustomBudget && customBudgetInput.isNotBlank()) {
              Button(
                onClick = { /* trigger filter */ },
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
            text = "Showing items up to Rs. ${String.format("%,d", activeBudget)} (Delivery calculated at checkout)",
            fontSize = 11.sp,
            color = JTTextSecondary
          )
        }
      }

      if (filteredProducts.isEmpty()) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Icon(Icons.Filled.SearchOff, contentDescription = null, tint = JTTextSecondary, modifier = Modifier.size(40.dp))
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "No products found under Rs. ${String.format("%,d", activeBudget)}",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = JTTextPrimary
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Try increasing your budget or exploring full marketplace categories.",
            fontSize = 12.sp,
            color = JTTextSecondary
          )
        }
      } else {
        LazyVerticalGrid(
          columns = GridCells.Fixed(2),
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          items(filteredProducts, key = { it.id }) { product ->
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = JTCardBg,
              border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onProductClick(product) }
                .testTag("budget_product_${product.id}")
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(JTSurfaceVariant)
                ) {
                  if (!product.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                      model = product.imageUrl,
                      contentDescription = product.title,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize()
                    )
                  } else {
                    Image(
                      painter = painterResource(id = R.drawable.ic_launcher_background),
                      contentDescription = product.title,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize()
                    )
                  }

                  // Wishlist Icon button
                  IconButton(
                    onClick = { MarketplaceRepository.toggleWishlist(product.id) },
                    modifier = Modifier
                      .align(Alignment.TopEnd)
                      .padding(4.dp)
                      .size(32.dp)
                      .background(Color.White.copy(alpha = 0.85f), CircleShape)
                  ) {
                    Icon(
                      imageVector = if (product.isWishlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                      contentDescription = "Wishlist",
                      tint = if (product.isWishlisted) Color.Red else JTTextSecondary,
                      modifier = Modifier.size(18.dp)
                    )
                  }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                  text = product.title,
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                  color = JTTextPrimary,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis
                )

                Text(
                  text = product.vendor,
                  fontSize = 10.sp,
                  color = JTTextSecondary,
                  maxLines = 1
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "Rs. ${String.format("%,d", product.price)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = JTPurplePrimary
                  )

                  IconButton(
                    onClick = {
                      MarketplaceRepository.addToCart(product, 1)
                      Toast.makeText(context, "Added to cart!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                      .size(30.dp)
                      .background(JTOrange, RoundedCornerShape(8.dp))
                  ) {
                    Icon(
                      imageVector = Icons.Filled.AddShoppingCart,
                      contentDescription = "Add to Cart",
                      tint = Color.White,
                      modifier = Modifier.size(16.dp)
                    )
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
