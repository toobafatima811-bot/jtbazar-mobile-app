package com.example.ui.screens

import androidx.compose.animation.core.*
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
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Product
import com.example.data.model.ShopRecord
import com.example.data.repository.MarketplaceRepository
import com.example.data.repository.ActiveProductCampaign
import com.example.ui.components.AddressSelectionSheet
import com.example.ui.components.JTBazarTopBar
import com.example.ui.components.MarketplaceHero
import com.example.ui.components.TrustBadgesRow
import com.example.ui.theme.*

@Composable
fun HomeScreen(
  onProductClick: (Product) -> Unit,
  onExploreCategoriesClick: () -> Unit,
  onCartClick: () -> Unit,
  onAccountClick: () -> Unit,
  onBudgetClick: () -> Unit = {},
  onExploreShopsClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val products by MarketplaceRepository.products.collectAsState()
  val trustedShops by MarketplaceRepository.shops.collectAsState()
  val categories by MarketplaceRepository.categories.collectAsState()
  val homepageHeroes by MarketplaceRepository.homepageHeroes.collectAsState()
  val homepagePromotions by MarketplaceRepository.homepagePromotions.collectAsState()
  val activeProductCampaigns by MarketplaceRepository.activeProductCampaigns.collectAsState()
  val isLoading by MarketplaceRepository.isLoading.collectAsState()
  val cartCount by MarketplaceRepository.cartCount.collectAsState()
  val currentLocation by MarketplaceRepository.currentLocation.collectAsState()
  val addresses by MarketplaceRepository.addresses.collectAsState()
  val selectedAddress by MarketplaceRepository.selectedAddress.collectAsState()

  var dismissedLaunchAdId by remember { mutableStateOf<String?>(null) }
  val nowIso = remember { java.time.Instant.now().toString() }
  val visibleBannerCampaigns = homepagePromotions.filter { campaign -> campaign.campaignType == "banner" && !campaign.showOnLaunch && (campaign.targetPlatform == "android" || campaign.targetPlatform == "both") && (campaign.startsAt.isNullOrBlank() || campaign.startsAt <= nowIso) && (campaign.endsAt.isNullOrBlank() || campaign.endsAt >= nowIso) }
  val launchAd = homepagePromotions.firstOrNull { campaign ->
    campaign.showOnLaunch &&
      (campaign.targetPlatform == "android" || campaign.targetPlatform == "both") &&
      (campaign.startsAt.isNullOrBlank() || campaign.startsAt <= nowIso) &&
      (campaign.endsAt.isNullOrBlank() || campaign.endsAt >= nowIso)
  }
  if (launchAd != null && dismissedLaunchAdId != launchAd.id) {
    val launchImageUrl = launchAd.bannerImageUrls.firstOrNull() ?: launchAd.bannerImageUrl
    if (!launchImageUrl.isNullOrBlank()) {
      Dialog(
        onDismissRequest = { dismissedLaunchAdId = launchAd.id },
        properties = DialogProperties(usePlatformDefaultWidth = false)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
          contentAlignment = Alignment.Center
        ) {
          AsyncImage(
            model = launchImageUrl,
            contentDescription = "JTBazar promotion",
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 760.dp)
              .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit
          )
          IconButton(
            onClick = { dismissedLaunchAdId = launchAd.id },
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(8.dp)
              .size(34.dp)
              .background(Color.White, CircleShape)
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close promotion", tint = Color.Black)
          }
        }
      }
    }
  }

  var showAddressDialog by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { MarketplaceRepository.refreshShops() }
  var searchQuery by remember { mutableStateOf("") }
  var isSearching by remember { mutableStateOf(false) }
  val voiceSearchLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    if (result.resultCode == android.app.Activity.RESULT_OK) {
      val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
      if (spoken.isNotBlank()) { searchQuery = spoken; isSearching = true }
    }
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

  var sellerStatus by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(Unit) {
    runCatching {
      val app = com.example.data.supabase.VendorService.getOwnApplication()
      sellerStatus = app?.status
    }
  }

  val infiniteAnim = rememberInfiniteTransition(label = "home_ambient_anim")
  val orb1OffsetY by infiniteAnim.animateFloat(
    initialValue = -14f,
    targetValue = 22f,
    animationSpec = infiniteRepeatable(
      animation = tween(4500, easing = EaseInOutSine),
      repeatMode = RepeatMode.Reverse
    ),
    label = "orb1_y"
  )
  val orb2OffsetX by infiniteAnim.animateFloat(
    initialValue = 18f,
    targetValue = -18f,
    animationSpec = infiniteRepeatable(
      animation = tween(5500, easing = EaseInOutSine),
      repeatMode = RepeatMode.Reverse
    ),
    label = "orb2_x"
  )

  Scaffold(
    containerColor = Color(0xFFFCFAF7),
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
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .background(
          brush = Brush.verticalGradient(
            listOf(
              Color(0xFFFFF9F2),
              Color(0xFFFFF5EB),
              Color(0xFFFCFAF7)
            )
          )
        )
    ) {
      // Ambient floating glowing orange orbs in background
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .offset(x = (60 + orb2OffsetX).dp, y = ((-25) + orb1OffsetY).dp)
          .size(240.dp)
          .clip(CircleShape)
          .background(
            Brush.radialGradient(
              listOf(
                Color(0xFFFF8000).copy(alpha = 0.14f),
                Color(0xFFFFB366).copy(alpha = 0.05f),
                Color.Transparent
              )
            )
          )
      )
      Box(
        modifier = Modifier
          .align(Alignment.TopStart)
          .offset(x = ((-60) - orb2OffsetX * 0.5f).dp, y = (70 + orb1OffsetY).dp)
          .size(210.dp)
          .clip(CircleShape)
          .background(
            Brush.radialGradient(
              listOf(
                Color(0xFFFF6600).copy(alpha = 0.11f),
                Color(0xFFFFCA80).copy(alpha = 0.04f),
                Color.Transparent
              )
            )
          )
      )

      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
      ) {
      // 1. Search Bar with voice search
      item {
        SearchInputBar(
          query = searchQuery,
          onQueryChange = {
            searchQuery = it
            isSearching = it.isNotBlank()
          },
          onVoiceSearch = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
              putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
              putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-PK")
              putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a product name")
            }
            runCatching { voiceSearchLauncher.launch(intent) }
          },
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
        )
      }

      if (searchQuery.isBlank()) {
        item { PromotionCampaignPlacement(visibleBannerCampaigns, "top_promo", onExploreCategoriesClick) }
        item { PromotionCampaignPlacement(visibleBannerCampaigns, "hero_banner", onExploreCategoriesClick) }

        // Website Parity: Full Homepage Hero Section
        item {
          val heroProduct = products.firstOrNull { !it.imageUrl.isNullOrBlank() }
          val heroShop = trustedShops.firstOrNull { !it.logoUrl.isNullOrBlank() }
          MarketplaceHero(
            heroes = homepageHeroes,
            featuredProduct = heroProduct,
            featuredShop = heroShop,
            sellerStatus = sellerStatus,
            onPrimaryCtaClick = { dest ->
              when {
                dest.isNullOrBlank() || dest.startsWith("/shop") -> onExploreCategoriesClick()
                dest.startsWith("/vendor") -> onAccountClick()
                else -> onExploreCategoriesClick()
              }
            },
            onSecondaryCtaClick = { _ ->
              onAccountClick()
            },
            onProductClick = onProductClick,
            onShopClick = { _ -> onExploreShopsClick() }
          )
        }

        item {
          OutlinedButton(
            onClick = onExploreShopsClick,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
          ) {
            Icon(Icons.Filled.Storefront, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Browse Trusted Shops")
          }
        }

        item { PromotionCampaignPlacement(visibleBannerCampaigns, "seller_banner", onExploreCategoriesClick) }
      }

      // 4. Shop by Category (Horizontal Row)
      if (searchQuery.isBlank()) {
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
                        model = category.imageUrl?.takeIf { it.isNotBlank() } ?: category.drawableRes,
                        contentDescription = category.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                      )
                    } else {
                      AsyncImage(
                        model = category.drawableRes,
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

      }

      // Approved vendor shops are shown directly on the home page.
      if (searchQuery.isBlank()) {
        item {
          Column(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Storefront, contentDescription = null, tint = JTOrange, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(7.dp))
                Text("Trusted Vendor Shops", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = JTTextPrimary)
              }
              Text(
                "View All",
                color = JTPurplePrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onExploreShopsClick() }.padding(4.dp)
              )
            }
            if (trustedShops.isEmpty()) {
              Text(
                "Trusted shops will appear here when vendors are approved.",
                fontSize = 12.sp,
                color = JTTextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
              )
            } else {
              LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                items(trustedShops, key = { it.id }) { shop ->
                  Card(
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.width(148.dp).clickable { onExploreShopsClick() }
                  ) {
                    Column(
                      modifier = Modifier.fillMaxWidth().padding(10.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                      verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                      if (!shop.logoUrl.isNullOrBlank()) {
                        AsyncImage(
                          model = shop.logoUrl,
                          contentDescription = shop.name,
                          contentScale = ContentScale.Crop,
                          modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                        )
                      } else {
                        Box(
                          modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(JTSurfaceVariant),
                          contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Storefront, contentDescription = null, tint = JTTextSecondary, modifier = Modifier.size(30.dp)) }
                      }
                      Text(shop.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                      Text(
                        "${shop.productCount} products",
                        fontSize = 10.sp,
                        color = JTTextSecondary
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }


      // Active product deals synced from the same Supabase campaign RPC as the website.
      if (searchQuery.isBlank() && activeProductCampaigns.isNotEmpty()) {
        items(activeProductCampaigns, key = { "deal_${it.campaign.id}" }) { activeCampaign ->
          ProductCampaignSection(
            activeCampaign = activeCampaign,
            onProductClick = onProductClick,
            onAddToCart = { MarketplaceRepository.addToCart(it) },
            onToggleWishlist = { MarketplaceRepository.toggleWishlist(it.id) }
          )
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
                text = if (searchQuery.isBlank()) "Popular Products" else "Search Results",
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

      if (searchQuery.isBlank()) item { PromotionCampaignPlacement(visibleBannerCampaigns, "category_feature", onExploreCategoriesClick) }

      // 6. 2x2 Grid of Popular Products
      item {
        Spacer(modifier = Modifier.height(10.dp))
        val homeProducts = products.filter { it.isFeatured }.ifEmpty { products }
        val displayProducts = if (searchQuery.isBlank()) {
          homeProducts.take(4)
        } else {
          products.filter {
            it.title.contains(searchQuery.trim(), ignoreCase = true) ||
              it.category.contains(searchQuery.trim(), ignoreCase = true) ||
              it.vendor.contains(searchQuery.trim(), ignoreCase = true) ||
              it.craftStory.contains(searchQuery.trim(), ignoreCase = true)
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

      if (searchQuery.isBlank()) item { PromotionCampaignPlacement(visibleBannerCampaigns, "product_strip", onExploreCategoriesClick) }


      // New arrivals: same latest-published catalog ordering as the website.
      item {
        val newArrivals = products.take(4)
        Column(modifier = Modifier.fillMaxWidth().padding(top = 22.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("NEW ARRIVALS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = JTOrangeDark)
              Text("Fresh additions to the store.", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = JTTextPrimary)
              Text("Recently added live listings.", fontSize = 11.sp, color = JTTextSecondary)
            }
            Text(
              "Browse catalog  →",
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = JTOrangeDark,
              modifier = Modifier.clickable { onExploreCategoriesClick() }.padding(4.dp)
            )
          }
          Spacer(Modifier.height(10.dp))
          if (newArrivals.isNotEmpty()) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              newArrivals.chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                  rowItems.forEach { product ->
                    Box(Modifier.weight(1f)) {
                      ProductCard(
                        product = product,
                        onProductClick = { onProductClick(product) },
                        onAddToCart = { MarketplaceRepository.addToCart(product) },
                        onToggleWishlist = { MarketplaceRepository.toggleWishlist(product.id) }
                      )
                    }
                  }
                  if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
              }
            }
          }
        }
      }

      if (searchQuery.isBlank()) item { PromotionCampaignPlacement(visibleBannerCampaigns, "offer_section", onExploreCategoriesClick) }

      // 8. Trust Badges
      item {
        Spacer(modifier = Modifier.height(8.dp))
        TrustBadgesRow()
      }

      // 9. Budget Shopping Callout Banner
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = Color(0xFFFEF3C7),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onBudgetClick() }
            .testTag("home_budget_banner")
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFFDE68A)),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = JTAmberDark, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text("Budget Shopping Corner", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF92400E))
              Text("Explore products under Rs. 500, 1,000 & 2,000", fontSize = 11.sp, color = Color(0xFFB45309))
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF92400E), modifier = Modifier.size(16.dp))
          }
        }
      }

      // 10. Social Media Links Section (AT THE VERY END OF HOME SCREEN)
      item {
        SocialMediaSection()
      }
    }
    }
  }
}

@Composable
fun SearchInputBar(
  query: String,
  onQueryChange: (String) -> Unit,
  onVoiceSearch: () -> Unit = {},
  modifier: Modifier = Modifier
 ) {
  val keyboardController = LocalSoftwareKeyboardController.current
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
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
          textStyle = androidx.compose.ui.text.TextStyle(
            color = JTTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
          ),
          modifier = Modifier.fillMaxWidth().testTag("search_text_input")
        )
      }

      IconButton(
        onClick = onVoiceSearch,
        modifier = Modifier.size(28.dp)
      ) {
        Icon(
          imageVector = Icons.Filled.Mic,
          contentDescription = "Voice Search",
          tint = JTTextSecondary,
          modifier = Modifier.size(19.dp)
        )
      }


    }
  }
}


@Composable
private fun PromotionCampaignPlacement(
  campaigns: List<com.example.data.supabase.SupabasePromotionDto>,
  position: String,
  onExploreCategoriesClick: () -> Unit
) {
  val context = LocalContext.current
  val placedCampaigns = campaigns.filter { it.displayPosition == position }
  if (placedCampaigns.isEmpty()) return
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    placedCampaigns.forEach { campaign ->
      val image = campaign.bannerImageUrls.firstOrNull() ?: campaign.bannerImageUrl
      if (!image.isNullOrBlank()) {
        Card(
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().clickable {
            val destination = campaign.ctaDestination.orEmpty()
            if (destination.startsWith("https://") || destination.startsWith("http://")) {
              runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(destination))) }
            } else {
              onExploreCategoriesClick()
            }
          }
        ) {
          Column {
            AsyncImage(
              model = image,
              contentDescription = campaign.title,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp)
            )
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(campaign.title, fontWeight = FontWeight.Bold, color = JTTextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
              campaign.description?.let { Text(it, fontSize = 12.sp, color = JTTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
              campaign.ctaText?.let { Text(it, fontSize = 11.sp, color = JTOrangeDark, fontWeight = FontWeight.Bold) }
            }
          }
        }
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
        // Use Coil for both remote and bundled product images. Calling
        // painterResource() here can crash composition when a fallback resource
        // is not a supported Android drawable (for example an unsupported asset).
        AsyncImage(
          model = product.imageUrl?.takeIf { it.isNotBlank() } ?: product.drawableRes,
          contentDescription = product.title,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )

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

          // Never offer add-to-cart for confirmed out-of-stock products or unavailable daily food.
          val dailyState = product.dailyStatus?.trim()?.lowercase()
          val isOutOfStock = (product.availabilityKnown && !product.isAvailable) ||
            dailyState in setOf("out_of_stock", "out-of-stock", "unavailable", "not_available", "not available", "sold_out", "sold out", "closed")
          if (isOutOfStock) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color(0xFFFFE4E6),
              modifier = Modifier.testTag("out_of_stock_${product.id}")
            ) {
              Text(
                text = "Out of Stock",
                color = Color(0xFFBE123C),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
              )
            }
          } else {
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
}

@Composable
fun SocialMediaSection(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = "Connect with JTBazar",
      fontSize = 14.sp,
      fontWeight = FontWeight.Bold,
      color = JTTextPrimary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = "Follow our official channels for live updates, vendor stories & deals",
      fontSize = 11.sp,
      color = JTTextSecondary,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))

    Row(
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Instagram: https://www.instagram.com/jtbazarofficial/
      Surface(
        shape = CircleShape,
        color = Color(0xFFE1306C),
        shadowElevation = 3.dp,
        modifier = Modifier
          .size(46.dp)
          .clickable {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/jtbazarofficial/"))
            context.startActivity(intent)
          }
          .testTag("social_instagram")
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(Icons.Filled.CameraAlt, contentDescription = "Instagram", tint = Color.White, modifier = Modifier.size(22.dp))
        }
      }

      // Facebook: https://www.facebook.com/profile.php?id=61595825413366
      Surface(
        shape = CircleShape,
        color = Color(0xFF1877F2),
        shadowElevation = 3.dp,
        modifier = Modifier
          .size(46.dp)
          .clickable {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/profile.php?id=61595825413366"))
            context.startActivity(intent)
          }
          .testTag("social_facebook")
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(Icons.Filled.Share, contentDescription = "Facebook", tint = Color.White, modifier = Modifier.size(22.dp))
        }
      }

      // TikTok: https://www.tiktok.com/@jtbazarofficial
      Surface(
        shape = CircleShape,
        color = Color.Black,
        shadowElevation = 3.dp,
        modifier = Modifier
          .size(46.dp)
          .clickable {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/@jtbazarofficial"))
            context.startActivity(intent)
          }
          .testTag("social_tiktok")
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(Icons.Filled.MusicNote, contentDescription = "TikTok", tint = Color.White, modifier = Modifier.size(22.dp))
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))
    Text(
      text = "© 2026 JTBazar • Authentic Marketplace Pakistan",
      fontSize = 10.sp,
      color = JTTextSecondary.copy(alpha = 0.7f)
    )
  }
}



@Composable
private fun ProductCampaignSection(
  activeCampaign: ActiveProductCampaign,
  onProductClick: (Product) -> Unit,
  onAddToCart: (Product) -> Unit,
  onToggleWishlist: (String) -> Unit
) {
  val campaign = activeCampaign.campaign
  val title = campaign.displayTitle.ifBlank { campaign.name }
  val dealLabel = when (campaign.campaignType.lowercase()) {
    "bogo" -> "Buy ${campaign.buyQuantity}, Get ${campaign.getQuantity}"
    "bundle" -> "Bundle deal"
    else -> "${campaign.discountPercent.toInt()}% OFF"
  }
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = campaign.campaignType.replace('_', ' ').uppercase(),
          color = Color(0xFFC2410C),
          fontSize = 9.sp,
          fontWeight = FontWeight.ExtraBold
        )
        Text(title, color = Color(0xFF7C2D12), fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!campaign.description.isNullOrBlank()) {
          Text(campaign.description, color = Color(0xFF9A3412), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
      Column(horizontalAlignment = Alignment.End) {
        Text(dealLabel, color = Color(0xFF9A3412), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        CampaignDealCountdown(campaign.endsAt)
      }
    }
    Spacer(Modifier.height(9.dp))
    activeCampaign.products.chunked(2).forEach { rowProducts ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        rowProducts.forEach { product ->
          Card(
            modifier = Modifier.weight(1f).clickable { onProductClick(product) },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0DF)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF97316))
          ) {
            Column {
              Box(
                modifier = Modifier.fillMaxWidth().height(104.dp).background(Color(0xFFFFE2C2))
              ) {
                AsyncImage(
                  model = product.imageUrl?.takeIf { it.isNotBlank() } ?: product.drawableRes,
                  contentDescription = product.title,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
                Surface(
                  modifier = Modifier.align(Alignment.TopStart),
                  color = Color(0xFFEA580C),
                  shape = RoundedCornerShape(bottomEnd = 8.dp, topStart = 12.dp)
                ) {
                  Text(dealLabel, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                }
                IconButton(
                  onClick = { onToggleWishlist(product.id) },
                  modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(27.dp).background(Color.White.copy(alpha = 0.92f), CircleShape)
                ) {
                  Icon(if (product.isWishlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = "Wishlist", tint = Color(0xFFEA580C), modifier = Modifier.size(15.dp))
                }
              }
              Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(product.title, color = Color(0xFF7C2D12), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                  Text("PKR ${product.price}", color = Color(0xFF9A3412), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                  if (product.originalPrice > product.price) {
                    Text("PKR ${product.originalPrice}", color = Color(0xFF9A7A68), fontSize = 9.sp, textDecoration = TextDecoration.LineThrough, maxLines = 1)
                  }
                }
                Button(
                  onClick = { onAddToCart(product) },
                  modifier = Modifier.fillMaxWidth().height(31.dp),
                  contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Icon(Icons.Filled.ShoppingBag, contentDescription = null, modifier = Modifier.size(13.dp))
                  Spacer(Modifier.width(4.dp))
                  Text("Add to cart", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
        if (rowProducts.size == 1) Spacer(Modifier.weight(1f))
      }
      Spacer(Modifier.height(9.dp))
    }
  }
}

@Composable
private fun CampaignDealCountdown(endsAt: String?) {
  var remaining by remember(endsAt) { mutableStateOf("") }
  LaunchedEffect(endsAt) {
    if (endsAt.isNullOrBlank()) {
      remaining = "Limited time"
    } else {
      while (true) {
        val seconds = runCatching {
          ((java.time.Instant.parse(endsAt).toEpochMilli() - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        }.getOrDefault(0L)
        remaining = if (seconds <= 0) "Offer ended" else {
          val days = seconds / 86400
          val hours = (seconds % 86400) / 3600
          val minutes = (seconds % 3600) / 60
          val secs = seconds % 60
          "${days}d ${hours}h ${minutes}m ${secs}s"
        }
        if (seconds <= 0) break
        kotlinx.coroutines.delay(1000)
      }
    }
  }
  Text(remaining, color = Color(0xFF9A3412), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
}
