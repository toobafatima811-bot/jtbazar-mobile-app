package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import coil.compose.AsyncImage
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Product
import com.example.data.repository.MarketplaceRepository
import com.example.ui.components.AddressSelectionSheet
import com.example.ui.components.JTBazarTopBar
import com.example.ui.components.TrustBadgesRow
import com.example.ui.theme.*

@Composable
fun HomeScreen(
  onProductClick: (Product) -> Unit,
  onExploreCategoriesClick: () -> Unit,
  onCartClick: () -> Unit,
  onAccountClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val products by MarketplaceRepository.products.collectAsState()
  val categories by MarketplaceRepository.categories.collectAsState()
  val isLoading by MarketplaceRepository.isLoading.collectAsState()
  val cartCount by MarketplaceRepository.cartCount.collectAsState()
  val currentLocation by MarketplaceRepository.currentLocation.collectAsState()
  val addresses by MarketplaceRepository.addresses.collectAsState()
  val selectedAddress by MarketplaceRepository.selectedAddress.collectAsState()

  var showAddressDialog by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var isSearching by remember { mutableStateOf(false) }

  if (showAddressDialog) {
    AddressSelectionSheet(
      addresses = addresses,
      selectedAddress = selectedAddress,
      onSelect = {
        MarketplaceRepository.selectAddress(it)
        showAddressDialog = false
      },
      onDismiss = { showAddressDialog = false },
      onAddNewAddress = { MarketplaceRepository.addOrUpdateAddress(it) }
    )
  }

  Scaffold(
    containerColor = Color.White,
    topBar = {
      JTBazarTopBar(
        onCartClick = onCartClick,
        onAvatarClick = onAccountClick,
        onLocationClick = { showAddressDialog = true },
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
      contentPadding = PaddingValues(bottom = 24.dp)
    ) {
      // 1. Search Bar with Mic & QR code
      item {
        SearchInputBar(
          query = searchQuery,
          onQueryChange = {
            searchQuery = it
            isSearching = it.isNotBlank()
          },
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
        )
      }

      // 2. Instant Delivery Banner
      item {
        InstantDeliveryBanner(
          area = currentLocation,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
        )
      }

      // Homepage promotions will render only after live promotions are fetched from Supabase.

      // 4. Shop by Category (Horizontal Row)
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Shop by Category",
              fontWeight = FontWeight.Bold,
              fontSize = 17.sp,
              color = JTTextPrimary
            )
            Text(
              text = "Explore All",
              fontWeight = FontWeight.SemiBold,
              fontSize = 13.sp,
              color = JTPurplePrimary,
              modifier = Modifier
                .clickable { onExploreCategoriesClick() }
                .testTag("explore_all_categories_button")
                .padding(4.dp)
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          if (categories.isEmpty()) {
            Text(
              text = "No active collections available",
              color = JTTextSecondary,
              fontSize = 12.sp,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
          } else {
            LazyRow(
              contentPadding = PaddingValues(horizontal = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              items(categories, key = { it.id }) { category ->
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier
                    .width(76.dp)
                    .clickable { onExploreCategoriesClick() }
                ) {
                  Box(
                    modifier = Modifier
                      .size(58.dp)
                      .clip(RoundedCornerShape(16.dp))
                      .background(JTSurfaceVariant),
                    contentAlignment = Alignment.Center
                  ) {
                    if (!category.imageUrl.isNullOrBlank()) {
                      AsyncImage(
                        model = category.imageUrl,
                        contentDescription = category.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                      )
                    } else {
                      Image(
                        painter = painterResource(id = category.drawableRes),
                        contentDescription = category.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                      )
                    }
                  }
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = category.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = JTTextPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 13.sp,
                    maxLines = 2
                  )
                  if (!category.badge.isNullOrBlank()) {
                    Text(category.badge, fontSize = 9.sp, color = JTTextSecondary)
                  }
                }
              }
            }
          }
        }
      }

      // 5. Popular Products Section Header
      item {
        Spacer(modifier = Modifier.height(18.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(JTRedContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                tint = JTRed,
                modifier = Modifier.size(18.dp)
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = "Popular Products",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = JTTextPrimary
              )

            }
          }

          Text(
            text = "View All >",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = JTPurplePrimary,
            modifier = Modifier
              .clickable { onExploreCategoriesClick() }
              .padding(4.dp)
          )
        }
      }

      // 6. 2x2 Grid of Popular Products
      item {
        Spacer(modifier = Modifier.height(10.dp))
        val displayProducts = if (searchQuery.isBlank()) {
          products
        } else {
          products.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
              it.category.contains(searchQuery, ignoreCase = true) ||
              it.vendor.contains(searchQuery, ignoreCase = true)
          }
        }

        if (isLoading) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              CircularProgressIndicator(color = JTPurplePrimary, modifier = Modifier.size(36.dp))
              Spacer(modifier = Modifier.height(10.dp))
              Text("Loading live JTBazar catalog...", fontSize = 12.sp, color = JTTextSecondary)
            }
          }
        } else if (displayProducts.isEmpty()) {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = JTSurfaceVariant,
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 8.dp)
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(
                imageVector = Icons.Filled.ShoppingBag,
                contentDescription = null,
                tint = JTPurplePrimary,
                modifier = Modifier.size(36.dp)
              )
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = if (searchQuery.isNotBlank()) "No matching products found" else "No live products available",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = JTTextPrimary
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = if (searchQuery.isNotBlank()) "Try searching for another keyword or browse categories." else "Please check your network connection or tap retry.",
                fontSize = 12.sp,
                color = JTTextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )
              Spacer(modifier = Modifier.height(14.dp))
              Button(
                onClick = { MarketplaceRepository.refreshLiveCatalog() },
                colors = ButtonDefaults.buttonColors(containerColor = JTPurplePrimary),
                shape = RoundedCornerShape(8.dp)
              ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Refresh Catalog", fontWeight = FontWeight.Bold)
              }
            }
          }
        } else {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            val chunked = displayProducts.chunked(2)
            chunked.forEach { rowItems ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                rowItems.forEach { product ->
                  Box(modifier = Modifier.weight(1f)) {
                    ProductCard(
                      product = product,
                      onProductClick = { onProductClick(product) },
                      onAddToCart = {
                        MarketplaceRepository.addToCart(product)
                      },
                      onToggleWishlist = {
                        MarketplaceRepository.toggleWishlist(product.id)
                      }
                    )
                  }
                }
                if (rowItems.size == 1) {
                  Spacer(modifier = Modifier.weight(1f))
                }
              }
            }
          }
        }
      }

      // 7. Lahore Special: Authentic Homemade Kitchens in Your Town
      item {
        Spacer(modifier = Modifier.height(20.dp))
        LahoreSpecialKitchenCard(
          onBrowse = { onExploreCategoriesClick() },
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        )
      }

      // 8. Trust Badges
      item {
        Spacer(modifier = Modifier.height(8.dp))
        TrustBadgesRow()
      }
    }
  }
}

@Composable
fun SearchInputBar(
  query: String,
  onQueryChange: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(24.dp),
    color = JTSurfaceVariant,
    modifier = modifier.height(48.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 14.dp)
    ) {
      Icon(
        imageVector = Icons.Filled.Search,
        contentDescription = "Search",
        tint = JTPurplePrimary,
        modifier = Modifier.size(20.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Box(modifier = Modifier.weight(1f)) {
        if (query.isEmpty()) {
          Text(
            text = "Search products, brands and sho...",
            color = JTTextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        androidx.compose.foundation.text.BasicTextField(
          value = query,
          onValueChange = onQueryChange,
          singleLine = true,
          textStyle = androidx.compose.ui.text.TextStyle(
            color = JTTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
          ),
          modifier = Modifier.fillMaxWidth().testTag("search_text_input")
        )
      }

      IconButton(
        onClick = { /* Voice Search */ },
        modifier = Modifier.size(28.dp)
      ) {
        Icon(
          imageVector = Icons.Filled.Mic,
          contentDescription = "Voice Search",
          tint = JTTextSecondary,
          modifier = Modifier.size(19.dp)
        )
      }

      Spacer(modifier = Modifier.width(2.dp))

      IconButton(
        onClick = { /* QR Scanner */ },
        modifier = Modifier.size(28.dp)
      ) {
        Icon(
          imageVector = Icons.Filled.QrCodeScanner,
          contentDescription = "Scan",
          tint = JTTextSecondary,
          modifier = Modifier.size(19.dp)
        )
      }
    }
  }
}

@Composable
fun InstantDeliveryBanner(
  area: String,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = JTOrangeContainer,
    border = androidx.compose.foundation.BorderStroke(1.dp, JTOrangeBorder),
    modifier = modifier
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
      Box(
        modifier = Modifier
          .size(28.dp)
          .clip(CircleShape)
          .background(JTOrange),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Filled.ElectricBolt,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(16.dp)
        )
      }

      Spacer(modifier = Modifier.width(10.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Instant delivery to $area",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = JTTextPrimary
        )
        Text(
          text = "Centralized delivery by JTBazar",
          fontSize = 10.sp,
          color = JTTextSecondary
        )
      }

      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White
      ) {
        Text(
          text = "25 Mins",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = JTOrangeDark,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
      }
    }
  }
}

@Composable
fun GrandBachatPromoCard(
  onShopNow: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    modifier = modifier
      .fillMaxWidth()
      .height(170.dp)
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          brush = Brush.linearGradient(
            colors = listOf(
              Color(0xFFC2410C),
              Color(0xFFEA580C),
              Color(0xFFF97316)
            )
          )
        )
        .padding(16.dp)
    ) {
      // Star / decorative art background
      Box(
        modifier = Modifier
          .size(120.dp)
          .align(Alignment.CenterEnd)
          .offset(x = 10.dp, y = 10.dp)
      ) {
        Icon(
          imageVector = Icons.Filled.Star,
          contentDescription = null,
          tint = Color.White.copy(alpha = 0.2f),
          modifier = Modifier.fillMaxSize()
        )
      }

      Column(
        modifier = Modifier
          .fillMaxHeight()
          .fillMaxWidth(0.72f),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color.White.copy(alpha = 0.25f)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.AutoAwesome,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "GRAND BACHAT OFFER",
              fontSize = 10.sp,
              fontWeight = FontWeight.ExtraBold,
              color = Color.White
            )
          }
        }

        Column {
          Text(
            text = "JTBazar Super Savings\nMela",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            lineHeight = 22.sp
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Up to 40% OFF on fresh groceries, spices & home essentials",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.9f),
            lineHeight = 14.sp
          )
        }

        Button(
          onClick = onShopNow,
          shape = RoundedCornerShape(20.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = JTOrangeDark
          ),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
          modifier = Modifier
            .height(34.dp)
            .testTag("shop_now_promo_button")
        ) {
          Text(
            text = "Shop Now",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.width(4.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
          )
        }
      }

      // Carousel dots indicator at bottom right
      Row(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .width(16.dp)
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White)
        )
        Box(
          modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.4f))
        )
        Box(
          modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.4f))
        )
      }
    }
  }
}

@Composable
fun ProductCard(
  product: Product,
  onProductClick: () -> Unit,
  onAddToCart: () -> Unit,
  onToggleWishlist: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = JTCardBg),
    border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onProductClick)
      .testTag("product_card_${product.id}")
  ) {
    Column {
      // Image Box with Badge & Heart
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(145.dp)
          .background(JTSurfaceVariant)
      ) {
        if (!product.imageUrl.isNullOrBlank()) {
          AsyncImage(
            model = product.imageUrl,
            contentDescription = product.title,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = product.drawableRes),
            error = painterResource(id = product.drawableRes),
            modifier = Modifier.fillMaxSize()
          )
        } else {
          Image(
            painter = painterResource(id = product.drawableRes),
            contentDescription = product.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        }

        // Discount tag top left
        if (product.badge != null) {
          Surface(
            shape = RoundedCornerShape(bottomEnd = 10.dp, topStart = 16.dp),
            color = JTRed,
            modifier = Modifier.align(Alignment.TopStart)
          ) {
            Text(
              text = product.badge,
              color = Color.White,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
            )
          }
        }

        // Heart favorite toggle top right
        Surface(
          shape = CircleShape,
          color = Color.White.copy(alpha = 0.85f),
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .size(28.dp)
            .clickable(onClick = onToggleWishlist)
            .testTag("wishlist_toggle_${product.id}")
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (product.isWishlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
              contentDescription = "Wishlist",
              tint = if (product.isWishlisted) JTRed else JTTextSecondary,
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }

      // Card Content
      Column(
        modifier = Modifier.padding(10.dp)
      ) {
        // Tag badge (e.g. In Stock, Vendor: Karigar Leather, Verified Food Seller)
        val tagText = product.inStockText.ifBlank { product.category }
        val tagBg = JTPurpleContainer
        val tagColor = JTPurplePrimary

        Surface(
          shape = RoundedCornerShape(6.dp),
          color = tagBg,
          modifier = Modifier.padding(bottom = 4.dp)
        ) {
          Text(
            text = tagText,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = tagColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }

        Text(
          text = product.title,
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          color = JTTextPrimary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          lineHeight = 16.sp,
          modifier = Modifier.height(34.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Rating
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = JTAmber,
            modifier = Modifier.size(12.dp)
          )
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = "${product.rating} (${if (product.reviewCount > 999) "${product.reviewCount / 1000}k" else "${product.reviewCount}"})",
            fontSize = 11.sp,
            color = JTTextSecondary,
            fontWeight = FontWeight.Medium
          )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Price Row with (+) Add Button
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Bottom
        ) {
          Column {
            Text(
              text = "Rs. ${String.format("%,d", product.price)}",
              fontWeight = FontWeight.ExtraBold,
              fontSize = 14.sp,
              color = JTPurplePrimary
            )
            if (product.originalPrice > product.price) {
              Text(
                text = "Rs. ${String.format("%,d", product.originalPrice)}",
                fontSize = 11.sp,
                color = JTTextTertiary,
                textDecoration = TextDecoration.LineThrough
              )
            }
          }

          // Add to cart button
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = JTOrange,
            modifier = Modifier
              .size(28.dp)
              .clickable(onClick = onAddToCart)
              .testTag("add_to_cart_${product.id}")
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun LahoreSpecialKitchenCard(
  onBrowse: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9ED)),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(16.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFFDE68A)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Filled.SoupKitchen,
            contentDescription = null,
            tint = JTAmberDark,
            modifier = Modifier.size(22.dp)
          )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Text(
            text = "LAHORE SPECIAL",
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = JTAmberDark,
            letterSpacing = 0.5.sp
          )
          Text(
            text = "Authentic Homemade Kitchens in Your Town",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = JTTextPrimary
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = "Fresh Ghar Ka Khana delivered piping hot from neighborhood home chefs directly to your doorstep.",
        fontSize = 12.sp,
        color = JTTextSecondary,
        lineHeight = 16.sp
      )

      Spacer(modifier = Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(JTPurplePrimary)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "48 active home chefs nearby",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = JTTextPrimary
          )
        }

        Button(
          onClick = onBrowse,
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = JTOrange,
            contentColor = Color.White
          ),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier
            .height(34.dp)
            .testTag("browse_home_chefs_button")
        ) {
          Text(
            text = "Browse Home Chefs",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.width(4.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(13.dp)
          )
        }
      }
    }
  }
}
