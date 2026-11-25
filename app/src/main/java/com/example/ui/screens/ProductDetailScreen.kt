package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.model.Product
import com.example.data.model.ProductVariant
import com.example.data.repository.MarketplaceRepository
import com.example.localization.AppLanguageManager
import com.example.localization.appText
import com.example.ui.components.AddressSelectionSheet
import com.example.ui.components.JTBazarTopBar
import com.example.ui.components.QuantityStepper
import com.example.ui.components.localizedStockBadgeText
import com.example.ui.components.rememberSizedImageRequest
import com.example.ui.theme.*
import com.example.util.ProductShareHelper

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
  val selectedLiveProduct by MarketplaceRepository.selectedProduct.collectAsState()
  val liveProduct = liveProducts.firstOrNull { it.id == product.id }
    ?: selectedLiveProduct?.takeIf { it.id == product.id }
    ?: product
  val currentLocation by MarketplaceRepository.currentLocation.collectAsState()
  val addresses by MarketplaceRepository.addresses.collectAsState()
  val selectedAddress by MarketplaceRepository.selectedAddress.collectAsState()
  val publicShops by MarketplaceRepository.shops.collectAsState()
  val activeCampaigns by MarketplaceRepository.activeProductCampaigns.collectAsState()
  val productCampaign = remember(activeCampaigns, product.id) {
    activeCampaigns.firstOrNull { entry -> entry.products.any { it.id == product.id } }?.campaign
  }
  val campaignEndMillis = remember(productCampaign?.endsAt) {
    productCampaign?.endsAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
  }
  BackHandler { onBackClick() }
  val sellerShop = remember(publicShops, liveProduct.sellerId) {
    publicShops.firstOrNull { it.id == liveProduct.sellerId }
  }

  val shareScope = rememberCoroutineScope()
  var showAddressDialog by remember { mutableStateOf(false) }
  var quantity by remember { mutableIntStateOf(1) }
  // Never auto-select the first variant; customer must explicitly choose before cart/buy.
  var selectedVariantId by remember(product.id) { mutableStateOf<String?>(null) }
  var selectedPaletteName by remember(product.id) { mutableStateOf<String?>(null) }
  var selectedSizeIndex by remember(product.id) { mutableStateOf<Int?>(null) }
  var galleryOverrideUrl by remember(product.id) { mutableStateOf<String?>(null) }

  // Accordion states
  var craftStoryExpanded by remember { mutableStateOf(true) }
  var reviewsExpanded by remember { mutableStateOf(false) }

  var showWriteReviewDialog by remember { mutableStateOf(false) }
  var reviewRating by remember { mutableIntStateOf(5) }
  var reviewTitle by remember { mutableStateOf("") }
  var reviewComment by remember { mutableStateOf("") }
  val allReviews by MarketplaceRepository.reviews.collectAsState()
  val productReviews = remember(allReviews, product.id) {
    allReviews.filter { it.productId == product.id }
  }

  LaunchedEffect(product.id) {
    MarketplaceRepository.selectProduct(product)
    if (publicShops.isEmpty()) {
      MarketplaceRepository.refreshShops()
    }
  }

  if (showWriteReviewDialog) {
    AlertDialog(
      onDismissRequest = { showWriteReviewDialog = false },
      title = { Text(appText("Write a Product Review", "پروڈکٹ کا جائزہ لکھیں"), fontWeight = FontWeight.Bold, fontSize = 16.sp) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(appText("Rating", "درجہ بندی"), fontSize = 12.sp, color = JTTextSecondary)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 1..5) {
              IconButton(onClick = { reviewRating = i }, modifier = Modifier.size(32.dp)) {
                Icon(
                  imageVector = Icons.Filled.Star,
                  contentDescription = appText("$i Stars", "$i ستارے"),
                  tint = if (i <= reviewRating) JTAmber else Color(0xFFD1D5DB),
                  modifier = Modifier.size(24.dp)
                )
              }
            }
          }
          OutlinedTextField(
            value = reviewTitle,
            onValueChange = { reviewTitle = it },
            label = { Text(appText("Headline / Title", "عنوان")) },
            placeholder = { Text(appText("e.g. Excellent handcraft quality!", "مثلاً بہترین معیار!")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = reviewComment,
            onValueChange = { reviewComment = it },
            label = { Text(appText("Your Review Details", "آپ کے جائزے کی تفصیل")) },
            placeholder = { Text(appText("Describe what you liked about this item...", "بتائیں کہ آپ کو اس چیز میں کیا پسند آیا...")) },
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
                title = reviewTitle.ifBlank { AppLanguageManager.tr("Verified Review", "تصدیق شدہ جائزہ") },
                comment = reviewComment
              )
              showWriteReviewDialog = false
              reviewTitle = ""
              reviewComment = ""
              Toast.makeText(
                context,
                AppLanguageManager.tr(
                  "Review request checked against your delivered orders.",
                  "آپ کے پہنچائے گئے آرڈرز کے مطابق جائزے کی درخواست چیک کر لی گئی۔"
                ),
                Toast.LENGTH_SHORT
              ).show()
            } else {
              Toast.makeText(
                context,
                AppLanguageManager.tr("Please enter your review details", "براہ کرم اپنے جائزے کی تفصیل درج کریں"),
                Toast.LENGTH_SHORT
              ).show()
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = JTOrange)
        ) {
          Text(appText("Submit Review", "جائزہ جمع کرائیں"))
        }
      },
      dismissButton = {
        TextButton(onClick = { showWriteReviewDialog = false }) {
          Text(appText("Cancel", "منسوخ کریں"), color = JTTextSecondary)
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

  val selectedVariant: ProductVariant? = remember(liveProduct.variants, selectedVariantId) {
    selectedVariantId?.let { vid -> liveProduct.variants.firstOrNull { it.id == vid } }
  }
  val legacySelectedSize = remember(liveProduct.sizes, selectedSizeIndex) {
    selectedSizeIndex?.let { idx -> liveProduct.sizes.getOrNull(idx)?.title }
  }
  val resolvedPaletteForCart = remember(selectedVariant, selectedPaletteName) {
    selectedVariant?.takeIf {
      it.optionLabel.contains("color", true) || it.optionLabel.contains("colour", true)
    }?.optionValue ?: selectedPaletteName
  }
  val resolvedSizeForCart = remember(selectedVariant, legacySelectedSize) {
    selectedVariant?.takeIf {
      !it.optionLabel.contains("color", true) && !it.optionLabel.contains("colour", true)
    }?.optionValue ?: legacySelectedSize
  }
  val unitPrice = selectedVariant?.priceOverride ?: liveProduct.price
  val totalPrice = unitPrice * quantity
  val activeImageUrl = remember(galleryOverrideUrl, selectedVariant, liveProduct.imageUrl) {
    galleryOverrideUrl?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
      ?: selectedVariant?.imageUrl?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
      ?: liveProduct.imageUrl?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
  }
  val galleryImages = remember(liveProduct.imageUrl, liveProduct.imageUrls, liveProduct.variants) {
    buildList {
      liveProduct.imageUrl?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }?.let { add(it) }
      addAll(liveProduct.imageUrls.map { it.trim() }.filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) })
      addAll(liveProduct.variants.mapNotNull { it.imageUrl?.trim()?.takeIf { url -> url.isNotBlank() && !url.equals("null", ignoreCase = true) } })
    }.distinct()
  }
  val hasVariants = liveProduct.variants.isNotEmpty() || liveProduct.variantIds.isNotEmpty()
  val canPurchase = MarketplaceRepository.canAddProductToCart(
    product = liveProduct,
    palette = resolvedPaletteForCart,
    size = resolvedSizeForCart,
    variantId = selectedVariant?.id
  )

  Scaffold(
    containerColor = Color.White,
    topBar = {
      JTBazarTopBar(
        title = appText("Product Details", "پروڈکٹ کی تفصیلات"),
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
        border = BorderStroke(1.dp, JTBorder),
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
              text = appText("Total Price", "کل قیمت"),
              fontSize = 11.sp,
              color = JTTextSecondary
            )
            Text(
              text = "Rs. ${String.format("%,d", totalPrice)}",
              fontWeight = FontWeight.ExtraBold,
              fontSize = 18.sp,
              color = JTPurplePrimary,
              modifier = Modifier.testTag("product_detail_total_price")
            )
          }

          if (!canPurchase) {
            val purchaseBlockedReason = when {
              !liveProduct.availabilityKnown -> appText("Availability not verified", "دستیابی کی تصدیق نہیں ہو سکی")
              !liveProduct.isAvailable -> appText("Out of Stock", "اسٹاک ختم")
              hasVariants && selectedVariant == null && resolvedSizeForCart.isNullOrBlank() && resolvedPaletteForCart.isNullOrBlank() ->
                appText("Select a variant", "ایک ویرینٹ منتخب کریں")
              else -> appText("Selected option unavailable", "منتخب کردہ آپشن دستیاب نہیں")
            }
            Surface(
              color = Color(0xFFFFE4E6),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.testTag("purchase_blocked_banner")
            ) {
              Text(
                purchaseBlockedReason,
                color = Color(0xFFBE123C),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
              )
            }
          } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(
                onClick = {
                  MarketplaceRepository.addToCart(
                    product = liveProduct,
                    quantity = quantity,
                    palette = resolvedPaletteForCart,
                    size = resolvedSizeForCart,
                    variantId = selectedVariant?.id
                  )
                  Toast.makeText(context, AppLanguageManager.tr("Added to cart!", "کارٹ میں شامل کر دیا گیا!"), Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = JTPurpleContainer, contentColor = JTPurplePrimary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("add_to_cart_bottom_button")
              ) {
                Icon(Icons.Outlined.ShoppingBag, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(appText("Add to Cart", "کارٹ میں شامل کریں"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
              Button(
                onClick = {
                  MarketplaceRepository.addToCart(
                    product = liveProduct,
                    quantity = quantity,
                    palette = resolvedPaletteForCart,
                    size = resolvedSizeForCart,
                    variantId = selectedVariant?.id
                  )
                  onBuyNowClick()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("buy_now_bottom_button")
              ) {
                Icon(Icons.Filled.ElectricBolt, contentDescription = null, tint = JTAmber, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(appText("Buy Now", "ابھی خریدیں"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
        .padding(paddingValues)
        .testTag("product_detail_lazy_list"),
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
                ProductShareHelper.shareProduct(
                  context = context,
                  product = liveProduct,
                  selectedVariant = selectedVariant,
                  scope = shareScope
                )
              },
              modifier = Modifier
                .size(48.dp)
                .testTag("product_share_button")
            ) {
              Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = appText("Share", "شیئر کریں"),
                tint = JTTextPrimary,
                modifier = Modifier.size(20.dp)
              )
            }

            IconButton(
              onClick = {
                MarketplaceRepository.toggleWishlist(liveProduct.id, liveProduct)
              },
              modifier = Modifier.size(48.dp)
            ) {
              Icon(
                imageVector = if (liveProduct.isWishlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = appText("Favorite", "پسندیدہ"),
                tint = if (liveProduct.isWishlisted) JTRed else JTTextPrimary,
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
            .background(JTSurfaceVariant),
          contentAlignment = Alignment.Center
        ) {
          when {
            activeImageUrl != null -> {
              key(activeImageUrl) {
                AsyncImage(
                  model = rememberSizedImageRequest(data = activeImageUrl, widthPx = 900, heightPx = 900),
                  contentDescription = selectedVariant?.let { "${liveProduct.title} - ${it.optionValue}" } ?: liveProduct.title,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier
                    .fillMaxSize()
                    .testTag("product_hero_image")
                )
              }
            }
            liveProduct.drawableRes != 0 -> {
              Image(
                painter = painterResource(id = liveProduct.drawableRes),
                contentDescription = liveProduct.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            }
            else -> {
              Icon(
                imageVector = Icons.Outlined.ShoppingBag,
                contentDescription = liveProduct.title,
                tint = JTTextTertiary,
                modifier = Modifier.size(64.dp)
              )
            }
          }

          if (liveProduct.discountPercent > 0) {
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
                  text = appText("${liveProduct.discountPercent}% OFF", "${liveProduct.discountPercent}% رعایت"),
                  color = Color.White,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
              }
            }
          }
        }

        if (galleryImages.size > 1) {
          Spacer(modifier = Modifier.height(10.dp))
          LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(galleryImages, key = { it }) { thumbUrl ->
              val isCurrent = activeImageUrl == thumbUrl
              Box(
                modifier = Modifier
                  .size(60.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .border(
                    width = if (isCurrent) 2.dp else 1.dp,
                    color = if (isCurrent) JTOrange else JTBorder,
                    shape = RoundedCornerShape(10.dp)
                  )
                  .clickable { galleryOverrideUrl = thumbUrl }
              ) {
                AsyncImage(
                  model = rememberSizedImageRequest(data = thumbUrl, widthPx = 180, heightPx = 180),
                  contentDescription = liveProduct.title,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
              }
            }
          }
        }
      }

      if (campaignEndMillis != null) {
        item(key = "deal_timer") {
          ProductDetailDealTimer(
            title = productCampaign?.displayTitle?.ifBlank { appText("Flash Deal ends in", "فلیش ڈیل ختم ہونے میں") }
              ?: appText("Flash Deal ends in", "فلیش ڈیل ختم ہونے میں"),
            campaignEndMillis = campaignEndMillis
          )
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
                text = if (liveProduct.reviewCount > 0) {
                  val score = String.format(java.util.Locale.US, "%.1f", liveProduct.rating)
                  appText("$score (${liveProduct.reviewCount} reviews)", "$score (${liveProduct.reviewCount} جائزے)")
                } else {
                  appText("No reviews yet", "ابھی تک کوئی جائزہ نہیں")
                },
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
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = liveProduct.title,
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold,
                  color = JTTextPrimary,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                  Text(
                    text = "Rs. ${String.format("%,d", unitPrice)}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = JTPurplePrimary,
                    modifier = Modifier.testTag("product_detail_unit_price")
                  )
                  if (liveProduct.originalPrice > unitPrice) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = "Rs. ${String.format("%,d", liveProduct.originalPrice)}",
                      fontSize = 14.sp,
                      color = JTTextTertiary,
                      textDecoration = TextDecoration.LineThrough,
                      modifier = Modifier.padding(bottom = 2.dp)
                    )
                  }
                }
              }

              Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, JTBorder)
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                  val stockDotColor = when {
                    selectedVariant != null && !selectedVariant.isAvailable -> JTRed
                    !liveProduct.isAvailable -> JTRed
                    else -> JTAmber
                  }
                  val stockText = when {
                    selectedVariant != null -> {
                      if (selectedVariant.isAvailable && liveProduct.isAvailable) {
                        appText("In Stock", "اسٹاک میں دستیاب")
                      } else {
                        appText("Out of Stock", "اسٹاک ختم")
                      }
                    }
                    else -> localizedStockBadgeText(liveProduct.inStockText)
                  }
                  Box(
                    modifier = Modifier
                      .size(7.dp)
                      .clip(CircleShape)
                      .background(stockDotColor)
                  )
                  Spacer(modifier = Modifier.width(5.dp))
                  Text(
                    text = stockText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = JTTextPrimary,
                    modifier = Modifier.testTag("product_detail_stock_badge")
                  )
                }
              }
            }

            if (liveProduct.savingsText != null && selectedVariant?.priceOverride == null) {
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
                  text = liveProduct.savingsText,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Medium,
                  color = JTAmberDark
                )
              }
            }
          }
        }
      }

      // Variants are placed directly below title & price so options can be selected immediately.

      // 8. Product Variants (All option types; starts unselected)
      if (liveProduct.variants.isNotEmpty()) {
        item(key = "product_variants_section") {
          Spacer(modifier = Modifier.height(16.dp))
          val groupedVariants = remember(liveProduct.variants) {
            liveProduct.variants.groupBy { it.optionLabel.ifBlank { "Option" } }
          }
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
              .testTag("product_variants_section"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = appText("SELECT VARIANT OPTION", "ویرینٹ آپشن منتخب کریں"),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = JTTextPrimary,
                letterSpacing = 0.5.sp
              )
              Text(
                text = selectedVariant?.let { "${it.optionLabel}: ${it.optionValue}" }
                  ?: appText("Required before adding to cart", "کارٹ میں شامل کرنے سے پہلے انتخاب ضروری ہے"),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (selectedVariant != null) JTPurplePrimary else JTOrangeDark
              )
            }

            groupedVariants.forEach { (groupLabel, groupVariants) ->
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  text = groupLabel,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = JTTextSecondary
                )
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  groupVariants.forEach { variant ->
                    val isSelected = selectedVariantId == variant.id
                    val variantPrice = variant.priceOverride ?: liveProduct.price
                    Surface(
                      shape = RoundedCornerShape(12.dp),
                      color = when {
                        isSelected -> JTOrangeContainer
                        !variant.isAvailable -> JTSurfaceVariant.copy(alpha = 0.55f)
                        else -> JTCardBg
                      },
                      border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = when {
                          isSelected -> JTOrange
                          !variant.isAvailable -> JTBorder.copy(alpha = 0.6f)
                          else -> JTBorder
                        }
                      ),
                      modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                          selectedVariantId = if (isSelected) null else variant.id
                          galleryOverrideUrl = null
                        }
                        .testTag("variant_option_${variant.id}")
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                      ) {
                        if (!variant.imageUrl.isNullOrBlank()) {
                          AsyncImage(
                            model = rememberSizedImageRequest(data = variant.imageUrl, widthPx = 120, heightPx = 120),
                            contentDescription = variant.optionValue,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                              .size(32.dp)
                              .clip(RoundedCornerShape(8.dp))
                          )
                        }
                        Column {
                          Text(
                            text = variant.optionValue,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (!variant.isAvailable) JTTextTertiary else JTTextPrimary
                          )
                          Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                          ) {
                            if (variant.priceOverride != null) {
                              Text(
                                text = "Rs. ${String.format("%,d", variantPrice)}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) JTOrangeDark else JTPurplePrimary
                              )
                            }
                            if (!variant.isAvailable) {
                              Text(
                                text = appText("Out of stock", "ختم"),
                                fontSize = 10.sp,
                                color = JTRed,
                                fontWeight = FontWeight.SemiBold
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
        }
      } else {
        // Legacy fallback selectors when product_variants rows aren't present, still starting unselected
        if (liveProduct.palettes.isNotEmpty()) {
          item {
            Spacer(modifier = Modifier.height(16.dp))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = appText("SELECT COLOUR / PATTERN", "رنگ / پیٹرن منتخب کریں"),
                  fontSize = 11.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = JTTextPrimary,
                  letterSpacing = 0.5.sp
                )
                Text(
                  text = selectedPaletteName ?: appText("Choose one", "ایک منتخب کریں"),
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (selectedPaletteName != null) JTPurplePrimary else JTOrangeDark
                )
              }

              Spacer(modifier = Modifier.height(8.dp))

              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                liveProduct.palettes.forEach { palette ->
                  val isSelected = palette.name == selectedPaletteName
                  Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) JTPurplePrimary else JTSurfaceVariant,
                    border = if (isSelected) null else BorderStroke(1.dp, JTBorder),
                    modifier = Modifier
                      .heightIn(min = 48.dp)
                      .clickable { selectedPaletteName = if (isSelected) null else palette.name }
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

        if (liveProduct.sizes.isNotEmpty()) {
          item {
            Spacer(modifier = Modifier.height(16.dp))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = appText("SELECT OPTION", "آپشن منتخب کریں"),
                  fontSize = 11.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = JTTextPrimary,
                  letterSpacing = 0.5.sp
                )
                Text(
                  text = legacySelectedSize ?: appText("Choose one", "ایک منتخب کریں"),
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (legacySelectedSize != null) JTPurplePrimary else JTOrangeDark
                )
              }

              Spacer(modifier = Modifier.height(8.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                liveProduct.sizes.forEachIndexed { index, size ->
                  val isSelected = index == selectedSizeIndex
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) JTPurpleContainer.copy(alpha = 0.4f) else JTCardBg,
                    border = if (isSelected) BorderStroke(1.5.dp, JTPurplePrimary) else BorderStroke(1.dp, JTBorder),
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 48.dp)
                      .clickable { selectedSizeIndex = if (isSelected) null else index }
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
                text = appText("Quantity", "تعداد"),
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
      if (liveProduct.craftStory.isNotBlank() || liveProduct.highlights.isNotEmpty()) item(key = "product_description_section") {
        Spacer(modifier = Modifier.height(18.dp))

        // Accordion 1: Product Highlights & Craft Story
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = JTCardBg,
          border = BorderStroke(1.dp, JTBorder),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("product_description_section")
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
                  text = appText("Product Highlights & Craft Story", "پروڈکٹ کی خصوصیات اور تفصیل"),
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
                if (liveProduct.craftStory.isNotBlank()) Text(
                  text = liveProduct.craftStory,
                  fontSize = 11.sp,
                  color = JTTextSecondary,
                  lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                liveProduct.highlights.forEach { highlight ->
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
      }

      // Vendor summary card shown at the end of the product description without shop description or location.
      if (liveProduct.sellerId != null && sellerShop != null) {
        item(key = "product_vendor_section") {
          Spacer(modifier = Modifier.height(14.dp))
          Surface(
            shape = RoundedCornerShape(18.dp),
            color = JTCardBg,
            border = BorderStroke(1.dp, JTBorder),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
              .testTag("product_vendor_section")
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              if (!sellerShop.logoUrl.isNullOrBlank()) {
                AsyncImage(
                  model = rememberSizedImageRequest(data = sellerShop.logoUrl, widthPx = 160, heightPx = 160),
                  contentDescription = sellerShop.name,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                )
              } else {
                Box(
                  modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(JTSurfaceVariant),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Filled.Storefront, contentDescription = null, tint = JTTextSecondary)
                }
              }
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = appText("Sold by", "فروخت کنندہ"),
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Medium,
                  color = JTTextSecondary
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = sellerShop.name,
                    fontWeight = FontWeight.Bold,
                    color = JTTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                  )
                  if (sellerShop.isVerified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                      imageVector = Icons.Filled.Verified,
                      contentDescription = appText("Verified shop", "تصدیق شدہ دکان"),
                      tint = JTOrange,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              }
              Spacer(Modifier.width(8.dp))
              TextButton(
                onClick = { onVisitShop(sellerShop.id) },
                modifier = Modifier.heightIn(min = 48.dp)
              ) {
                Text(appText("Visit Shop", "دکان دیکھیں"), color = JTOrange, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      // Customer reviews from Supabase - independent card
      item {
        Spacer(modifier = Modifier.height(12.dp))
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
                  text = appText("Customer Reviews (${productReviews.size})", "صارفین کے جائزے (${productReviews.size})"),
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
                    text = appText("${productReviews.size} Verified Customer Reviews", "${productReviews.size} تصدیق شدہ صارفین کے جائزے"),
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
                    Text(appText("Write a Review", "جائزہ لکھیں"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = JTPurplePrimary)
                  }
                }

                // Render real reviews from backend
                if (productReviews.isEmpty()) {
                  Text(
                    text = appText(
                      "No reviews yet. Verified customers can leave a review after receiving their order.",
                      "ابھی تک کوئی جائزہ نہیں۔ تصدیق شدہ صارفین آرڈر موصول ہونے کے بعد جائزہ لکھ سکتے ہیں۔"
                    ),
                    fontSize = 12.sp,
                    color = JTTextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                  )
                } else {
                  productReviews.forEach { rev ->
                    ReviewSnippet(
                      name = rev.userName.ifBlank { appText("Customer", "صارف") },
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

@Composable
private fun ProductDetailDealTimer(
  title: String,
  campaignEndMillis: Long
) {
  var remainingSeconds by remember(campaignEndMillis) {
    mutableLongStateOf(((campaignEndMillis - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L))
  }
  LaunchedEffect(campaignEndMillis) {
    while (true) {
      val rem = ((campaignEndMillis - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
      remainingSeconds = rem
      if (rem == 0L) break
      kotlinx.coroutines.delay(1000L)
    }
  }
  if (remainingSeconds <= 0L) return

  val days = remainingSeconds / 86400L
  val hours = (remainingSeconds % 86400L) / 3600L
  val minutes = (remainingSeconds % 3600L) / 60L
  val seconds = remainingSeconds % 60L
  Surface(
    color = Color(0xFFFFF1F0),
    shape = RoundedCornerShape(14.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD5D1)),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
  ) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, fontWeight = FontWeight.Bold, color = Color(0xFFB42318), fontSize = 14.sp)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val timerParts = if (days > 0) listOf(days, hours, minutes, seconds) else listOf(hours, minutes, seconds)
        val labels = if (days > 0) {
          listOf(
            appText("DAYS", "دن"),
            appText("HRS", "گھنٹے"),
            appText("MIN", "منٹ"),
            appText("SEC", "سیکنڈ")
          )
        } else {
          listOf(
            appText("HRS", "گھنٹے"),
            appText("MIN", "منٹ"),
            appText("SEC", "سیکنڈ")
          )
        }
        timerParts.forEachIndexed { index, value ->
          Surface(color = Color(0xFFB42318), shape = RoundedCornerShape(8.dp)) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
              Text(value.toString().padStart(2, '0'), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
              Text(labels[index], color = Color.White, fontSize = 9.sp)
            }
          }
        }
      }
    }
  }
}
