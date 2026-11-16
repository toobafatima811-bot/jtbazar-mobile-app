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
import com.example.ui.components.rememberAdaptiveColumnCount

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
  val categoryColumns = rememberAdaptiveColumnCount(minCellWidth = 148.dp, horizontalPadding = 32.dp, spacing = 12.dp, maxColumns = 4)

  var showAddressDialog by remember { mutableStateOf(false) }
  var selectedCategoryId by remember { mutableStateOf<String?>(null) }
  var searchQuery by remember { mutableStateOf("") }

  val filteredCategories = remember(categories, selectedCategoryId, searchQuery) {
    categories.filter { category ->
      (selectedCategoryId == null || category.id == selectedCategoryId) &&
        (searchQuery.isBlank() ||
          category.title.contains(searchQuery, ignoreCase = true) ||
          category.sampleItems.contains(searchQuery, ignoreCase = true))
    }
  }
  val chunkedCats = remember(filteredCategories, categoryColumns) {
    filteredCategories.chunked(categoryColumns)
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

      // 2. Live category filters sourced from Supabase
      item {
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item {
            FilterChip(selected = selectedCategoryId == null, onClick = { selectedCategoryId = null },
              label = { Text("All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
              leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null, modifier = Modifier.size(15.dp)) },
              colors = FilterChipDefaults.filterChipColors(selectedContainerColor = JTOrange, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White))
          }
          items(categories, key = { it.id }) { category ->
            FilterChip(selected = selectedCategoryId == category.id,
              onClick = { selectedCategoryId = if (selectedCategoryId == category.id) null else category.id },
              label = { Text(category.title, fontSize = 12.sp, maxLines = 1) },
              colors = FilterChipDefaults.filterChipColors(selectedContainerColor = JTOrange, selectedLabelColor = Color.White))
          }
        }
      }
      // 3. Section Header: Explore Categories (10 Departments)
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
            text = "${categories.size} Departments",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = JTTextSecondary
          )
        }
      }

      // 5. 2-Column Grid of Categories
      if (filteredCategories.isEmpty()) {
        item {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = JTSurfaceVariant,
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 24.dp)
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(Icons.Filled.Category, contentDescription = null, tint = JTPurplePrimary, modifier = Modifier.size(36.dp))
              Spacer(modifier = Modifier.height(10.dp))
              Text("No categories available", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = JTTextPrimary)
              Spacer(modifier = Modifier.height(4.dp))
              Text("Tap refresh to fetch live categories from JTBazar.", fontSize = 12.sp, color = JTTextSecondary)
              Spacer(modifier = Modifier.height(14.dp))
              Button(
                onClick = { MarketplaceRepository.refreshLiveCatalog() },
                colors = ButtonDefaults.buttonColors(containerColor = JTPurplePrimary),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text("Refresh Categories", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      items(
        items = chunkedCats,
        key = { row -> "cat_row_${row.firstOrNull()?.id ?: row.hashCode()}" },
        contentType = { "category_row" }
      ) { rowItems ->
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
                allCategories = categories,
                onClick = { onCategorySelect(cat) }
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

@Composable
fun CategoryCard(
  category: CategoryItem,
  allCategories: List<CategoryItem> = emptyList(),
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
          .aspectRatio(1.65f)
          .background(JTSurfaceVariant)
      ) {
        CategoryVisualThumbnail(
          category = category,
          allCategories = allCategories,
          modifier = Modifier.fillMaxSize()
        )

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
