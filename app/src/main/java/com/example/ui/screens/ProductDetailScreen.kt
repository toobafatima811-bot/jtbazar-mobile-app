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
  onVisitShop: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val cartCount by MarketplaceRepository.cartCount.collectAsState()
  val liveProducts by MarketplaceRepository.products.collectAsState()
  val liveProduct = liveProducts.firstOrNull { it.id == product.id } ?: product
  val currentLocation by MarketplaceRepository.currentLocation.collectAsState()
  val addresses by MarketplaceRepository.addresses.collectAsState()
  val selectedAddress by MarketplaceRepository.selectedAddress.collectAsState()
  val publicShops by MarketplaceRepository.shops.collectAsState()
  val sellerShop = publicShops.firstOrNull { it.id == liveProduct.sellerId }

  var showAddressDialog by remember { mutableStateOf(false) }
  var quantity by remember { mutableIntStateOf(1) }
  var selectedPaletteName by remember {
    mutableStateOf(product.palettes.firstOrNull()?.name ?: "")
  }
  var selectedSizeIndex by remember { mutableIntStateOf(0) }

  // Accordion states
  var craftStoryExpanded by remember { mutableStateOf(true) }
  var reviewsExpanded by remember { mutableStateOf(false) }

  var showWriteReviewDialog by remember { mutableStateOf(false) }
  var reviewRating by remember { mutableIntStateOf(5) }
  var reviewTitle by remember { mutableStateOf("") }
  var reviewComment by remember { mutableStateOf("") }
  val allReviews by MarketplaceRepository.reviews.collectAsState()
  val productReviews = allReviews.filter { it.productId == product.id }

  LaunchedEffect(product.id) {
    MarketplaceRepository.selectProduct(product)
    MarketplaceRepository.refreshShops()
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

  val unitPrice = liveProduct.price
  val totalPrice = unitPrice * quantity
  val selectedSize = product.sizes.getOrNull(selectedSizeIndex)?.title
  val canPurchase = MarketplaceRepository.canAddProductToCart(liveProduct, selectedPaletteName, selectedSize)

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

          if (!canPurchase) {
            val purchaseBlockedReason = when {
              !liveProduct.availabilityKnown -> "Availability not verified"
              !liveProduct.isAvailable -> "Out of Stock"
              liveProduct.variantIds.isNotEmpty() && selectedSize.isNullOrBlank() && selectedPaletteName.isNullOrBlank() -> "Select an option"
              else -> "Selected option unavailable"
            }
            Surface(color = Color(0xFFFFE4E6), shape = RoundedCornerShape(10.dp)) {
              Text(purchaseBlockedReason, color = Color(0xFFBE123C), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
          } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(
                onClick = {
                  MarketplaceRepository.addToCart(liveProduct, quantity, selectedPaletteName, selectedSize)
                  if (MarketplaceRepository.canAddProductToCart(liveProduct, selectedPaletteName, selectedSize)) {
                    Toast.makeText(context, "Added to cart!", Toast.LENGTH_SHORT).show()
                  }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = JTPurpleContainer, contentColor = JTPurplePrimary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.testTag("add_to_cart_bottom_button")
              ) {
                Icon(Icons.Outlined.ShoppingBag, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add to Cart", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
              Button(
                onClick = {
                  MarketplaceRepository.addToCart(liveProduct, quantity, selectedPaletteName, selectedSize)
                  if (MarketplaceRepository.canAddProductToCart(liveProduct, selectedPaletteName, selectedSize)) onBuyNowClick()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.testTag("buy_now_bottom_button")
              ) {
                Icon(Icons.Filled.ElectricBolt, contentDescription = null, tint = JTAmber, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Buy Now", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
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
          Text(text = liveProduct.category, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = JTTextSecondary, modifier = Modifier.weight(1f), maxLines = 1)

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
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(JTSurfaceVariant)
        ) {
          // Load remote and bundled fallback images with Coil to avoid eager
          // painterResource() calls during composition.
          AsyncImage(
            model = product.imageUrl?.takeIf { it.isNotBlank() } ?: product.drawableRes,
            contentDescription = product.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )

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
                text = if (product.reviewCount > 0) String.format(java.util.Locale.US, "%.1f", product.rating) + " (" + product.reviewCount + " reviews)" else "No reviews yet",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = JTAmberDark
              )
            }
          }
        }
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

      // Seller information is loaded from the live public shop directory.
      if (liveProduct.sellerId != null && sellerShop != null) {
        item {
          Spacer(modifier = Modifier.height(14.dp))
          Surface(
            shape = RoundedCornerShape(18.dp),
            color = JTCardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!sellerShop.logoUrl.isNullOrBlank()) {
                  AsyncImage(
                    model = sellerShop.logoUrl,
                    contentDescription = sellerShop.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                  )
                } else {
                  Box(
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(JTSurfaceVariant),
                    contentAlignment = Alignment.Center
                  ) { Icon(Icons.Filled.Storefront, contentDescription = null, tint = JTTextSecondary) }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sellerShop.name, fontWeight = FontWeight.Bold, color = JTTextPrimary, maxLines = 1)
                    if (sellerShop.isVerified) {
                      Spacer(Modifier.width(4.dp))
                      Icon(Icons.Filled.Verified, contentDescription = "Verified shop", tint = JTOrange, modifier = Modifier.size(16.dp))
                    }
                  }
                  if (sellerShop.city.isNotBlank()) {
                    Text(sellerShop.city, style = MaterialTheme.typography.bodySmall, color = JTTextSecondary)
                  }
                }
                TextButton(onClick = { onVisitShop(sellerShop.id) }) { Text("Visit Shop", color = JTOrange) }
              }
              if (sellerShop.address.isNotBlank()) {
                HorizontalDivider(color = JTBorder)
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Filled.LocationOn, contentDescription = null, tint = JTOrange, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(8.dp))
                  Text(sellerShop.address, style = MaterialTheme.typography.bodySmall, color = JTTextSecondary)
                }
              }
              if (sellerShop.description.isNotBlank()) {
                Text(sellerShop.description, style = MaterialTheme.typography.bodySmall, color = JTTextSecondary)
              }
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
                text = "Quantity",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = JTTextPrimary
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

      // 11. Product details and real customer reviews
      if (product.craftStory.isNotBlank() || product.highlights.isNotEmpty()) item {
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
                if (product.craftStory.isNotBlank()) Text(
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

        // Customer reviews from Supabase
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
                  text = "Customer Reviews (" + productReviews.size + ")",
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
                      name = rev.userName.ifBlank { "Customer" },
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
