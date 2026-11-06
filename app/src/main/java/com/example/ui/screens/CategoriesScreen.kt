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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CategoryItem
import com.example.data.repository.MarketplaceRepository
import com.example.ui.components.AddressSelectionSheet
import com.example.ui.components.JTBazarTopBar
import com.example.ui.theme.*

@Composable
fun CategoriesScreen(
  onCategorySelect: (CategoryItem) -> Unit,
  onCartClick: () -> Unit,
  onAccountClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val cartCount by MarketplaceRepository.cartCount.collectAsState()
  val currentLocation by MarketplaceRepository.currentLocation.collectAsState()
  val addresses by MarketplaceRepository.addresses.collectAsState()
  val selectedAddress by MarketplaceRepository.selectedAddress.collectAsState()
  val categories by MarketplaceRepository.categories.collectAsState()

  var showAddressDialog by remember { mutableStateOf(false) }
  var selectedChip by remember { mutableStateOf("All Categories") }
  var searchQuery by remember { mutableStateOf("") }

  if (showAddressDialog) {
    AddressSelectionSheet(
      addresses = addresses,
      selectedAddress = selectedAddress,
      onSelect = {
        MarketplaceRepository.selectAddress(it)
        showAddressDialog = false
      },
      onDismiss = { showAddressDialog = false }
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
      contentPadding = PaddingValues(bottom = 32.dp)
    ) {
      // 1. Search Bar
      item {
        Surface(
          shape = RoundedCornerShape(24.dp),
          color = JTSurfaceVariant,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 16.dp)
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
              if (searchQuery.isEmpty()) {
                Text(
                  text = "Search 12+ categories & verified stores",
                  color = JTTextSecondary,
                  fontSize = 13.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              androidx.compose.foundation.text.BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                  color = JTTextPrimary,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.fillMaxWidth().testTag("category_search_input")
              )
            }
            IconButton(
              onClick = { /* Voice Search */ },
              modifier = Modifier.size(28.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Mic",
                tint = JTTextSecondary,
                modifier = Modifier.size(18.dp)
              )
            }
            Spacer(modifier = Modifier.width(2.dp))
            IconButton(
              onClick = { /* Filters */ },
              modifier = Modifier.size(28.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Filter",
                tint = JTTextSecondary,
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }
      }

      // 2. Horizontal Filter Chips
      item {
        Spacer(modifier = Modifier.height(10.dp))
        val chips = listOf("All Categories", "Food & Groceries", "Local Artisan", "Home Living")
        LazyRow(
          contentPadding = PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(chips) { chip ->
            val isSelected = chip == selectedChip
            Surface(
              shape = RoundedCornerShape(20.dp),
              color = if (isSelected) JTPurplePrimary else Color.Transparent,
              border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
              modifier = Modifier.clickable { selectedChip = chip }
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
              ) {
                if (chip == "All Categories") {
                  Icon(
                    imageVector = Icons.Filled.GridView,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else JTTextSecondary,
                    modifier = Modifier.size(15.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                  text = chip,
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) Color.White else JTTextPrimary
                )
              }
            }
          }
        }
      }

      // 3. Featured Category Banner: "JTBazar Food & Fresh Market"
      item {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = JTPurplePrimary,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        ) {
          Column(
            modifier = Modifier.padding(16.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.25f)
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                  Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "Verified Kitchens & Marts",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                  )
                }
              }

              Text(
                text = "PKR Best Rates",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.8f)
              )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = "JTBazar Food & Fresh Market",
              fontSize = 16.sp,
              fontWeight = FontWeight.ExtraBold,
              color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
              text = "Selling by Kg, Dozen, Litre & Family Packs with express verified delivery in Lahore.",
              fontSize = 11.sp,
              color = Color.White.copy(alpha = 0.85f),
              lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.15f)
              ) {
                Text(
                  text = "Atta • Daal • Parathas",
                  color = Color.White,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.15f)
              ) {
                Text(
                  text = "Fresh Dairy Daily",
                  color = Color.White,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }
          }
        }
      }

      // 4. Section Header: Explore Categories (10 Departments)
      item {
        Spacer(modifier = Modifier.height(18.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Explore Categories",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = JTTextPrimary
          )
          Text(
            text = "10 Departments",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = JTTextSecondary
          )
        }
      }

      // 5. 2-Column Grid of Categories
      val filteredCategories = if (searchQuery.isBlank()) {
        categories
      } else {
        categories.filter {
          it.title.contains(searchQuery, ignoreCase = true) ||
            it.sampleItems.contains(searchQuery, ignoreCase = true)
        }
      }

      val chunkedCats = filteredCategories.chunked(2)
      items(chunkedCats) { rowItems ->
        Spacer(modifier = Modifier.height(12.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          rowItems.forEach { cat ->
            Box(modifier = Modifier.weight(1f)) {
              CategoryCard(
                category = cat,
                onClick = { onCategorySelect(cat) }
              )
            }
          }
          if (rowItems.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }

      // 6. Food & Groceries Hub Preview Card
      item {
        Spacer(modifier = Modifier.height(24.dp))
        FoodGroceryHubPreview(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        )
      }
    }
  }
}

@Composable
fun CategoryCard(
  category: CategoryItem,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = JTCardBg),
    border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("category_card_${category.id}")
  ) {
    Column {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(98.dp)
          .background(JTSurfaceVariant)
      ) {
        if (!category.imageUrl.isNullOrBlank()) {
          AsyncImage(
            model = category.imageUrl,
            contentDescription = category.title,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = category.drawableRes),
            error = painterResource(id = category.drawableRes),
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

        // Badge if present (Fast Delivery, Fresh Home Chefs, Coming Soon)
        if (category.badge != null) {
          val badgeBg = if (category.badge == "Coming Soon") Color.Black.copy(alpha = 0.7f) else if (category.badge == "Fresh Home Chefs") JTAmber else JTPurplePrimary
          Surface(
            shape = RoundedCornerShape(bottomEnd = 8.dp, topStart = 16.dp),
            color = badgeBg,
            modifier = Modifier.align(Alignment.TopStart)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
              if (category.badge == "Coming Soon") {
                Icon(
                  Icons.Filled.Schedule,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
              }
              Text(
                text = category.badge,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }

      Column(
        modifier = Modifier.padding(10.dp)
      ) {
        Text(
          text = category.title,
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          color = JTTextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        Text(
          text = category.countText,
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
          color = JTPurplePrimary
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
          text = category.sampleItems,
          fontSize = 10.sp,
          color = JTTextSecondary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          lineHeight = 13.sp
        )
      }
    }
  }
}

@Composable
fun FoodGroceryHubPreview(
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = JTPurpleContainer.copy(alpha = 0.45f),
    border = androidx.compose.foundation.BorderStroke(1.dp, JTPurpleContainerBorder),
    modifier = modifier.fillMaxWidth()
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
              .size(28.dp)
              .clip(CircleShape)
              .background(JTPurplePrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Filled.ShoppingBag,
              contentDescription = null,
              tint = JTPurplePrimary,
              modifier = Modifier.size(16.dp)
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = "Food & Groceries Hub Preview",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = JTTextPrimary
            )
            Text(
              text = "Instant stock updates • Delivered within 45 mins",
              fontSize = 10.sp,
              color = JTTextSecondary
            )
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.clickable { /* View All */ }
        ) {
          Text(
            text = "View All",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = JTPurplePrimary
          )
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = JTPurplePrimary,
            modifier = Modifier.size(13.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        HubItem(
          icon = Icons.Filled.Grain,
          title = "Staples",
          sub = "Daal, Chawal, Atta",
          modifier = Modifier.weight(1f)
        )
        HubItem(
          icon = Icons.Filled.LocalCafe,
          title = "Dairy & Bakery",
          sub = "Milk, Eggs, Bread",
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        HubItem(
          icon = Icons.Filled.DinnerDining,
          title = "Spices & Masalas",
          sub = "Haldi, Lal Mirch, Gara...",
          modifier = Modifier.weight(1f)
        )
        HubItem(
          icon = Icons.Filled.Liquor,
          title = "Beverages",
          sub = "Tea, Rooh Afza, Juices",
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}

@Composable
private fun HubItem(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  sub: String,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = JTCardBg,
    border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
    modifier = modifier
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
      Box(
        modifier = Modifier
          .size(24.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(JTSurfaceVariant),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = JTPurplePrimary,
          modifier = Modifier.size(14.dp)
        )
      }
      Spacer(modifier = Modifier.width(6.dp))
      Column {
        Text(
          text = title,
          fontWeight = FontWeight.Bold,
          fontSize = 11.sp,
          color = JTTextPrimary
        )
        Text(
          text = sub,
          fontSize = 9.sp,
          color = JTTextSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}
