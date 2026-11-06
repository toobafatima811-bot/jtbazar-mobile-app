package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.repository.MarketplaceRepository
import com.example.ui.components.AddressSelectionSheet
import com.example.ui.components.JTBazarTopBar
import com.example.ui.theme.*

@Composable
fun AccountScreen(
  onViewOrderTracking: () -> Unit,
  onCartClick: () -> Unit,
  onShowSplash: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val cartCount by MarketplaceRepository.cartCount.collectAsState()
  val currentLocation by MarketplaceRepository.currentLocation.collectAsState()
  val selectedAddress by MarketplaceRepository.selectedAddress.collectAsState()
  val addresses by MarketplaceRepository.addresses.collectAsState()
  val activeOrder by MarketplaceRepository.activeOrder.collectAsState()

  var showAddressDialog by remember { mutableStateOf(false) }

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
        title = "My Account",
        onCartClick = onCartClick,
        onAvatarClick = {},
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
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // 1. User Header Card
      item {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = JTCardBg,
          border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .border(2.dp, JTPurplePrimary, CircleShape)
            ) {
              Image(
                painter = painterResource(id = R.drawable.user_avatar),
                contentDescription = "User Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = selectedAddress.name,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = JTTextPrimary
              )
              Text(
                text = selectedAddress.phone,
                fontSize = 12.sp,
                color = JTTextSecondary
              )
              Spacer(modifier = Modifier.height(4.dp))
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = JTPurpleContainer
              ) {
                Text(
                  text = "Verified Buyer • Lahore",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = JTPurplePrimary,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
          }
        }
      }

      // 2. Active Order Card
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = JTPurpleSurface,
          border = androidx.compose.foundation.BorderStroke(1.dp, JTPurpleContainerBorder),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewOrderTracking() }
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(JTPurplePrimary),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.LocalShipping,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(20.dp)
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Order #${activeOrder.orderId}",
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = JTTextPrimary
                )
                Text(
                  text = "Packing & Verification • In Progress",
                  fontSize = 11.sp,
                  color = JTPurplePrimary,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }

            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = "Track Order",
              tint = JTPurplePrimary,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }

      // 3. Menu Settings
      item {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = JTCardBg,
          border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(vertical = 6.dp)) {
            AccountMenuItem(
              icon = Icons.Outlined.LocationOn,
              title = "Delivery Addresses",
              subtitle = "${selectedAddress.area}, ${selectedAddress.city}",
              onClick = { showAddressDialog = true }
            )
            HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 14.dp))
            AccountMenuItem(
              icon = Icons.Outlined.ReceiptLong,
              title = "Track Orders & Invoices",
              subtitle = "Live tracking on TCS & partner logistics",
              onClick = { onViewOrderTracking() }
            )
            HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 14.dp))
            AccountMenuItem(
              icon = Icons.Outlined.Payment,
              title = "Payment Preferences",
              subtitle = "Cash on Delivery (COD) Active",
              onClick = {
                Toast.makeText(context, "Cash on Delivery is set as default", Toast.LENGTH_SHORT).show()
              }
            )
            HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 14.dp))
            AccountMenuItem(
              icon = Icons.Outlined.Translate,
              title = "App Language",
              subtitle = "English (اردو دستیاب ہے)",
              onClick = {
                Toast.makeText(context, "English & Urdu supported", Toast.LENGTH_SHORT).show()
              }
            )
            HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 14.dp))
            HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 14.dp))
            AccountMenuItem(
              icon = Icons.Outlined.AutoAwesome,
              title = "Mascot & Splash Screen",
              subtitle = "View JTBazar Welcome Experience",
              onClick = onShowSplash
            )
          }
        }
      }

      // 4. Version info
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "JTBazar v1.0.0",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = JTTextSecondary
          )
          Text(
            text = "100% Genuine Pakistani Marketplace • Lahore, Pakistan",
            fontSize = 10.sp,
            color = JTTextTertiary
          )
        }
      }
    }
  }
}

@Composable
private fun AccountMenuItem(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(JTSurfaceVariant),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = JTPurplePrimary,
          modifier = Modifier.size(18.dp)
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column {
        Text(
          text = title,
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          color = JTTextPrimary
        )
        Text(
          text = subtitle,
          fontSize = 10.sp,
          color = JTTextSecondary
        )
      }
    }

    Icon(
      imageVector = Icons.AutoMirrored.Filled.ArrowForward,
      contentDescription = null,
      tint = JTTextSecondary,
      modifier = Modifier.size(16.dp)
    )
  }
}
