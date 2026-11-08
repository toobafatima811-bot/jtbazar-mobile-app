package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import coil.compose.AsyncImage
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ShoppingBag
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Product
import com.example.data.repository.MarketplaceRepository
import com.example.ui.components.AddressSelectionSheet
import com.example.ui.components.JTBazarTopBar
import com.example.ui.components.QuantityStepper
import com.example.ui.theme.*

@Composable
fun ProductDetailScreen(
  product: Product,
  onBackClick: () -> Unit,
  onCartClick: () -> Unit,
  onBuyNowClick: () -> Unit,
  onAccountClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val cartCount by MarketplaceRepository.cartCount.collectAsState()
  val currentLocation by MarketplaceRepository.currentLocation.collectAsState()
  val addresses by MarketplaceRepository.addresses.collectAsState()
  val selectedAddress by MarketplaceRepository.selectedAddress.collectAsState()

  var showAddressDialog by remember { mutableStateOf(false) }
  var quantity by remember { mutableIntStateOf(1) }
  var selectedPaletteName by remember {
    mutableStateOf(product.palettes.firstOrNull()?.name ?: "")
  }
  var selectedSizeIndex by remember { mutableIntStateOf(0) }

  // Accordion states
  var craftStoryExpanded by remember { mutableStateOf(true) }
  var deliveryGuaranteeExpanded by remember { mutableStateOf(false) }
  var reviewsExpanded by remember { mutableStateOf(false) }

  var showWriteReviewDialog by remember { mutableStateOf(false) }
  var reviewRating by remember { mutableIntStateOf(5) }
  var reviewTitle by remember { mutableStateOf("") }
  var reviewComment by remember { mutableStateOf("") }
  val allReviews by MarketplaceRepository.reviews.collectAsState()
  val productReviews = allReviews.filter { it.productId == product.id }

  LaunchedEffect(product.id) {
    MarketplaceRepository.selectProduct(product)
  }

  if (showWriteReviewDialog) {
    AlertDialog(
      onDismissRequest = { showWriteReviewDialog = false },
      title = { Text("Write a Product Review", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Rating", fontSize = 12.sp, color = JTTextSecondary)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 1..5) {
              IconButton(onClick = { reviewRating = i }, modifier = Modifier.size(32.dp)) {
                Icon(
                  imageVector = Icons.Filled.Star,
                  contentDescription = "$i Stars",
                  tint = if (i <= reviewRating) JTAmber else Color(0xFFD1D5DB),
                  modifier = Modifier.size(24.dp)
                )
              }
            }
          }
          OutlinedTextField(
            value = reviewTitle,
            onValueChange = { reviewTitle = it },
            label = { Text("Headline / Title") },
            placeholder = { Text("e.g. Excellent handcraft quality!") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = reviewComment,
            onValueChange = { reviewComment = it },
            label = { Text("Your Review Details") },
            placeholder = { Text("Describe what you liked about this item...") },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (reviewComment.isNotBlank()) {
              MarketplaceRepository.submitProductReview(
                productId = product.id,
                rating = reviewRating,
                title = reviewTitle.ifBlank { "Verified Review" },
                comment = reviewComment
              )
              showWriteReviewDialog = false
              reviewTitle = ""
              reviewComment = ""
              Toast.makeText(context, "Review request checked against your delivered orders.", Toast.LENGTH_SHORT).show()
            } else {
              Toast.makeText(context, "Please enter your review details", Toast.LENGTH_SHORT).show()
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = JTOrange)
        ) {
          Text("Submit Review")
        }
      },
      dismissButton = {
        TextButton(onClick = { showWriteReviewDialog = false }) {
          Text("Cancel", color = JTTextSecondary)
        }
      }
    )
  }

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

  val unitPrice = product.price
  val totalPrice = unitPrice * quantity

  Scaffold(
    containerColor = Color.White,
    topBar = {
      JTBazarTopBar(
        title = "Product Details",
        showBack = true,
        onBackClick = onBackClick,
        onCartClick = onCartClick,
        onAvatarClick = onAccountClick,
        onLocationClick = { showAddressDialog = true },
        cartCount = cartCount,
        currentLocation = currentLocation
      )
    },
    bottomBar = {
      // Sticky Bottom Action Bar
      Surface(
        color = Color.White,
        tonalElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column {
            Text(
              text = "Total Price",
              fontSize = 11.sp,
              color = JTTextSecondary
            )
            Text(
              text = "Rs. ${String.format("%,d", totalPrice)}",
              fontWeight = FontWeight.ExtraBold,
              fontSize = 18.sp,
              color = JTPurplePrimary
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Add to Cart
            Button(
              onClick = {
                MarketplaceRepository.addToCart(
                  product = product,
                  quantity = quantity,
                  palette = selectedPaletteName,
                  size = product.sizes.getOrNull(selectedSizeIndex)?.title
                )
                Toast.makeText(context, "Added to cart!", Toast.LENGTH_SHORT).show()
              },
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = JTPurpleContainer,
                contentColor = JTPurplePrimary
              ),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
              modifier = Modifier.testTag("add_to_cart_bottom_button")
            ) {
              Icon(
                imageVector = Icons.Outlined.ShoppingBag,
                contentDescription = null,
                modifier = Modifier.size(17.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Add to Cart", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            // Buy Now
            Button(
              onClick = {
                MarketplaceRepository.addToCart(
                  product = product,
                  quantity = quantity,
                  palette = selectedPaletteName,
                  size = product.sizes.getOrNull(selectedSizeIndex)?.title
                )
                onBuyNowClick()
              },
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = JTOrange,
                contentColor = Color.White
              ),
              contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
              modifier = Modifier.testTag("buy_now_bottom_button")
            ) {
              Icon(
                imageVector = Icons.Filled.ElectricBolt,
                contentDescription = null,
                tint = JTAmber,
                modifier = Modifier.size(17.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Buy Now", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
          }
        }
      }
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentPadding = PaddingValues(bottom = 24.dp)
    ) {
      // 1. Sub Header with Collection Chip, Share and Wishlist
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = JTPurpleContainer
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Verified,
                contentDescription = null,
                tint = JTPurplePrimary,
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Artisan Collection",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = JTPurplePrimary
              )
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
              onClick = {
                Toast.makeText(context, "Sharing link to ${product.title}", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier.size(36.dp)
            ) {
              Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = "Share",
                tint = JTTextPrimary,
                modifier = Modifier.size(20.dp)
              )
            }

            IconButton(
              onClick = {
                MarketplaceRepository.toggleWishlist(product.id)
              },
              modifier = Modifier.size(36.dp)
            ) {
              Icon(
                imageVector = if (product.isWishlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (product.isWishlisted) JTRed else JTTextPrimary,
                modifier = Modifier.size(20.dp)
              )
            }
          }
        }
      }

      // 2. Hero Image Carousel with Badges
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(290.dp)
            .clip(RoundedCornerShape(20.dp))
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

          // Discount Tag
          Column(
            modifier = Modifier
              .align(Alignment.TopStart)
              .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = JTRed
            ) {
              Text(
                text = "${product.discountPercent}% OFF",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
              )
            }

            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color.White.copy(alpha = 0.9f)
            ) {
              Text(
                text = "Lead-Free Safe",
                color = JTTextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
              )
            }
          }

          // Carousel Indicators at bottom center
          Row(
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = 12.dp)
              .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
              .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .width(18.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White)
            )
            Box(
              modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.5f))
            )
            Box(
              modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.5f))
            )
            Box(
              modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.5f))
            )
          }
        }
      }

      // 3. Rating & Shipping Badges
      item {
        Spacer(modifier = Modifier.height(14.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = JTAmberContainer
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = JTAmberDark,
                modifier = Modifier.size(13.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "${product.rating} (${product.reviewCount} verified reviews)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = JTAmberDark
              )
            }
          }

          Surface(
            shape = RoundedCornerShape(8.dp),
            color = JTPurpleContainer
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.LocalShipping,
                contentDescription = null,
                tint = JTPurplePrimary,
                modifier = Modifier.size(13.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Free Shipping",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = JTPurplePrimary
              )
            }
          }
        }
      }

      // 4. Product Title
      item {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = product.title,
          fontWeight = FontWeight.ExtraBold,
          fontSize = 19.sp,
          color = JTTextPrimary,
          lineHeight = 24.sp,
          modifier = Modifier.padding(horizontal = 16.dp)
        )
      }

      // 5. Price & In-Stock Card
      item {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = JTPurpleSurface,
          border = androidx.compose.foundation.BorderStroke(1.dp, JTPurpleContainerBorder),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.Bottom) {
                Text(
                  text = "Rs. ${String.format("%,d", product.price)}",
                  fontSize = 24.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = JTPurplePrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Rs. ${String.format("%,d", product.originalPrice)}",
                  fontSize = 14.sp,
                  color = JTTextTertiary,
                  textDecoration = TextDecoration.LineThrough,
                  modifier = Modifier.padding(bottom = 2.dp)
                )
              }

              Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder)
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                  Box(
                    modifier = Modifier
                      .size(7.dp)
                      .clip(CircleShape)
                      .background(JTAmber)
                  )
                  Spacer(modifier = Modifier.width(5.dp))
                  Text(
                    text = product.inStockText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = JTTextPrimary
                  )
                }
              }
            }

            if (product.savingsText != null) {
              Spacer(modifier = Modifier.height(8.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Filled.CardGiftcard,
                  contentDescription = null,
                  tint = JTAmberDark,
                  modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = product.savingsText,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Medium,
                  color = JTAmberDark
                )
              }
            }
          }
        }
      }

      // 6. JTBazar Fresh & Pantry Note
      item {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = Color(0xFFFFF9ED),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
          ) {
            Box(
              modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFDE68A)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Filled.Storefront,
                contentDescription = null,
                tint = JTAmberDark,
                modifier = Modifier.size(20.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "JTBazar Fresh & Pantry Note",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = JTTextPrimary
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "Also Available: Food & pantry items can be ordered by standard units (kg, gram, dozen, pack) with direct Cash on Delivery across Pakistan.",
                fontSize = 11.sp,
                color = JTTextSecondary,
                lineHeight = 15.sp
              )
            }
          }
        }
      }

      // 7. Vendor Card (Multan Craft Heritage)
      item {
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = JTCardBg,
          border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(JTPurpleContainer),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = product.vendorAvatarText,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = JTPurplePrimary
                  )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = product.vendor,
                      fontSize = 14.sp,
                      fontWeight = FontWeight.Bold,
                      color = JTTextPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                      imageVector = Icons.Filled.Verified,
                      contentDescription = "Verified",
                      tint = JTPurplePrimary,
                      modifier = Modifier.size(15.dp)
                    )
                  }
                  Text(
                    text = "${product.vendorRating}  •  ${product.vendorSales}",
                    fontSize = 11.sp,
                    color = JTTextSecondary
                  )
                }
              }

              Surface(
                shape = RoundedCornerShape(10.dp),
                color = JTPurpleContainer,
                modifier = Modifier.clickable {
                  Toast.makeText(context, "Visiting ${product.vendor}", Toast.LENGTH_SHORT).show()
                }
              ) {
                Text(
                  text = "Visit\nShop",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = JTPurplePrimary,
                  textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = JTBorder)
            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.LocalShipping,
                contentDescription = null,
                tint = JTPurplePrimary,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Delivery to ",
                fontSize = 11.sp,
                color = JTTextSecondary
              )
              Text(
                text = currentLocation,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = JTTextPrimary
              )
              Text(
                text = " in 2–3 business days",
                fontSize = 11.sp,
                color = JTTextSecondary
              )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.Payments,
                contentDescription = null,
                tint = JTAmberDark,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Cash on Delivery (COD) & Online Bank Transfer Supported",
                fontSize = 11.sp,
                color = JTTextSecondary
              )
            }
          }
        }
      }

      // 8. Select Palette Pattern
      if (product.palettes.isNotEmpty()) {
        item {
          Spacer(modifier = Modifier.height(16.dp))
          Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "SELECT PALETTE PATTERN",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = JTTextPrimary,
                letterSpacing = 0.5.sp
              )
              Text(
                text = selectedPaletteName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = JTPurplePrimary
              )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              product.palettes.forEach { palette ->
                val isSelected = palette.name == selectedPaletteName
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = if (isSelected) JTPurplePrimary else JTSurfaceVariant,
                  border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
                  modifier = Modifier
                    .clickable { selectedPaletteName = palette.name }
                    .testTag("palette_${palette.name}")
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                  ) {
                    Box(
                      modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(palette.colorHex))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = palette.name.replace("Royal ", ""),
                      fontSize = 12.sp,
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                      color = if (isSelected) Color.White else JTTextPrimary
                    )
                  }
                }
              }
            }
          }
        }
      }

      // 9. Plate Diameter / Size selector
      if (product.sizes.isNotEmpty()) {
        item {
          Spacer(modifier = Modifier.height(16.dp))
          Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "PLATE DIAMETER",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = JTTextPrimary,
                letterSpacing = 0.5.sp
              )
              Text(
                text = product.sizes.getOrNull(selectedSizeIndex)?.title ?: "",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = JTPurplePrimary
              )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              product.sizes.forEachIndexed { index, size ->
                val isSelected = index == selectedSizeIndex
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = if (isSelected) JTPurpleContainer.copy(alpha = 0.4f) else JTCardBg,
                  border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, JTPurplePrimary) else androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
                  modifier = Modifier
                    .weight(1f)
                    .clickable { selectedSizeIndex = index }
                    .testTag("size_option_$index")
                ) {
                  Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                      text = size.title,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      color = if (isSelected) JTPurplePrimary else JTTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = size.subtitle,
                      fontSize = 10.sp,
                      color = JTTextSecondary,
                      lineHeight = 12.sp
                    )
                  }
                }
              }
            }
          }
        }
      }

      // 10. Quantity (Sets) Stepper
      item {
        Spacer(modifier = Modifier.height(16.dp))
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = JTSurfaceVariant.copy(alpha = 0.6f),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "Quantity (Sets)",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = JTTextPrimary
              )
              Text(
                text = "2 plates per ordered set",
                fontSize = 11.sp,
                color = JTTextSecondary
              )
            }

            QuantityStepper(
              quantity = quantity,
              onIncrement = { quantity++ },
              onDecrement = { if (quantity > 1) quantity-- }
            )
          }
        }
      }

      // 11. Accordion Sections
      item {
        Spacer(modifier = Modifier.height(18.dp))

        // Accordion 1: Product Highlights & Craft Story
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = JTCardBg,
          border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { craftStoryExpanded = !craftStoryExpanded },
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Filled.ColorLens,
                  contentDescription = null,
                  tint = JTPurplePrimary,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Product Highlights & Craft Story",
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = JTTextPrimary
                )
              }
              Icon(
                imageVector = if (craftStoryExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = JTTextSecondary
              )
            }

            AnimatedVisibility(visible = craftStoryExpanded) {
              Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                  text = product.craftStory,
                  fontSize = 11.sp,
                  color = JTTextSecondary,
                  lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                product.highlights.forEach { highlight ->
                  Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                  ) {
                    Icon(
                      imageVector = Icons.Filled.CheckCircleOutline,
                      contentDescription = null,
                      tint = JTPurplePrimary,
                      modifier = Modifier
                        .size(15.dp)
                        .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = highlight,
                      fontSize = 11.sp,
                      color = JTTextPrimary,
                      lineHeight = 15.sp
                    )
                  }
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Accordion 2: Nationwide Delivery & 7-Day Guarantee
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = JTCardBg,
          border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { deliveryGuaranteeExpanded = !deliveryGuaranteeExpanded },
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Filled.LocalShipping,
                  contentDescription = null,
                  tint = JTPurplePrimary,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Nationwide Delivery & 7-Day Guarantee",
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = JTTextPrimary
                )
              }
              Icon(
                imageVector = if (deliveryGuaranteeExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = JTTextSecondary
              )
            }

            AnimatedVisibility(visible = deliveryGuaranteeExpanded) {
              Column(modifier = Modifier.padding(top = 10.dp)) {
                Text(
                  text = "Safe transit assured with custom foam packaging for ceramic fragile shipments. Easy 7-day hassle-free replacement or refund guarantee on damaged goods.",
                  fontSize = 11.sp,
                  color = JTTextSecondary,
                  lineHeight = 16.sp
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Accordion 3: Shopper Reviews (128)
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = JTCardBg,
          border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { reviewsExpanded = !reviewsExpanded },
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Filled.RateReview,
                  contentDescription = null,
                  tint = JTAmberDark,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Shopper Reviews (${product.reviewCount})",
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = JTTextPrimary
                )
              }
              Icon(
                imageVector = if (reviewsExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = JTTextSecondary
              )
            }

            AnimatedVisibility(visible = reviewsExpanded) {
              Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "${productReviews.size} Verified Customer Reviews",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = JTTextSecondary
                  )
                  TextButton(
                    onClick = { showWriteReviewDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                  ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = JTPurplePrimary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Write a Review", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = JTPurplePrimary)
                  }
                }

                // Render real reviews from backend
                if (productReviews.isEmpty()) {
                  Text(
                    text = "No reviews yet. Verified customers can leave a review after receiving their order.",
                    fontSize = 12.sp,
                    color = JTTextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                  )
                } else {
                  productReviews.forEach { rev ->
                    ReviewSnippet(
                      name = rev.userName.ifBlank { "Verified Buyer" },
                      comment = rev.comment,
                      rating = rev.rating
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

@Composable
private fun ReviewSnippet(
  name: String,
  comment: String,
  rating: Int
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = JTSurfaceVariant,
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(text = name, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = JTTextPrimary)
        Row {
          repeat(rating) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = JTAmber, modifier = Modifier.size(11.dp))
          }
        }
      }
      Spacer(modifier = Modifier.height(3.dp))
      Text(text = comment, fontSize = 10.sp, color = JTTextSecondary)
    }
  }
}
