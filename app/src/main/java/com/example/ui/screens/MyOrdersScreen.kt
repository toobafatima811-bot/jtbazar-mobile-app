package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OrderRecord
import com.example.data.repository.MarketplaceRepository
import com.example.data.supabase.VendorOrderStatus
import com.example.ui.theme.*

@Composable
fun MyOrdersScreen(
  onOrderClick: (OrderRecord) -> Unit,
  onExploreProducts: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val orders by MarketplaceRepository.orders.collectAsState()

  Scaffold(
    containerColor = Color.White,
    topBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.testTag("orders_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = JTTextPrimary
          )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "My Orders",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = JTTextPrimary
        )
      }
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    if (orders.isEmpty()) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Surface(
          shape = RoundedCornerShape(24.dp),
          color = JTSurfaceVariant,
          modifier = Modifier.size(80.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ShoppingBag, contentDescription = null, tint = JTPurplePrimary, modifier = Modifier.size(40.dp))
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "No Orders Placed Yet",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = JTTextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Your purchases from verified Pakistani artisans and food vendors will appear here.",
          fontSize = 13.sp,
          color = JTTextSecondary,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
          onClick = onExploreProducts,
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White),
          modifier = Modifier.height(44.dp)
        ) {
          Text("Start Shopping", fontWeight = FontWeight.Bold)
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        items(orders, key = { it.orderId }) { order ->
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = JTCardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onOrderClick(order) }
              .testTag("order_item_${order.orderId}")
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text(
                    text = order.orderNumber,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = JTTextPrimary
                  )
                  Text(
                    text = order.date.take(10),
                    fontSize = 11.sp,
                    color = JTTextSecondary
                  )
                }
                val canonical = VendorOrderStatus.parse(order.orderStatus) ?: VendorOrderStatus.PENDING
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = when (canonical) {
                    VendorOrderStatus.DELIVERED,
                    VendorOrderStatus.READY_FOR_PICKUP -> Color(0xFFDEF7EC)
                    VendorOrderStatus.CANCELLED,
                    VendorOrderStatus.NOT_DELIVERED -> Color(0xFFFDE8E8)
                    VendorOrderStatus.CONFIRMED,
                    VendorOrderStatus.PROCESSING,
                    VendorOrderStatus.SHIPPED -> Color(0xFFE1EFFE)
                    VendorOrderStatus.PENDING -> JTPurpleContainer
                  }
                ) {
                  Text(
                    text = canonical.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (canonical) {
                      VendorOrderStatus.DELIVERED,
                      VendorOrderStatus.READY_FOR_PICKUP -> Color(0xFF03543F)
                      VendorOrderStatus.CANCELLED,
                      VendorOrderStatus.NOT_DELIVERED -> Color(0xFF9B1C1C)
                      VendorOrderStatus.CONFIRMED,
                      VendorOrderStatus.PROCESSING,
                      VendorOrderStatus.SHIPPED -> Color(0xFF1E429F)
                      VendorOrderStatus.PENDING -> JTPurplePrimary
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                  )
                }
              }

              Spacer(modifier = Modifier.height(10.dp))
              HorizontalDivider(color = JTBorder)
              Spacer(modifier = Modifier.height(10.dp))

              Text(
                text = "${order.items.size} item(s) • Total: Rs. ${String.format("%,d", order.grandTotal)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = JTTextPrimary
              )

              if (order.address.addressLine.isNotBlank()) {
                Text(
                  text = "Delivering to: ${order.address.addressLine}, ${order.address.city}",
                  fontSize = 12.sp,
                  color = JTTextSecondary,
                  maxLines = 1,
                  modifier = Modifier.padding(top = 2.dp)
                )
              }

              Spacer(modifier = Modifier.height(12.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Track Order Details",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = JTOrange
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                  contentDescription = null,
                  tint = JTOrange,
                  modifier = Modifier.size(14.dp)
                )
              }
            }
          }
        }
      }
    }
  }
}
