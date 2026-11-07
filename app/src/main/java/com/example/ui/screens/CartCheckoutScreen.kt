package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CartItem
import com.example.data.repository.MarketplaceRepository
import com.example.ui.components.AddressSelectionSheet
import com.example.ui.components.JTBazarTopBar
import com.example.ui.components.QuantityStepper
import com.example.ui.theme.*

@Composable
fun CartCheckoutScreen(
  onBackClick: () -> Unit,
  onOrderPlaced: () -> Unit,
  onAccountClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val cartItems by MarketplaceRepository.cartItems.collectAsState()
  val cartCount by MarketplaceRepository.cartCount.collectAsState()
  val currentLocation by MarketplaceRepository.currentLocation.collectAsState()
  val addresses by MarketplaceRepository.addresses.collectAsState()
  val selectedAddress by MarketplaceRepository.selectedAddress.collectAsState()

  val isPlacingOrder by MarketplaceRepository.isPlacingOrder.collectAsState()

  var showAddressDialog by remember { mutableStateOf(false) }
  var deliveryNote by remember { mutableStateOf("Leave parcel with security guard / gate # 2") }

  // Server-aligned Calculations
  val subtotal = cartItems.sumOf { it.totalPrice }
  val deliveryCharges = if (subtotal >= 1999 || subtotal == 0) 0 else 150
  val discount = 0
  val grandTotal = (subtotal - discount + deliveryCharges).coerceAtLeast(0)

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
        title = "Cart & COD Checkout",
        showBack = true,
        onBackClick = onBackClick,
        onCartClick = { /* already here */ },
        onAvatarClick = onAccountClick,
        onLocationClick = { showAddressDialog = true },
        cartCount = cartCount,
        currentLocation = currentLocation
      )
    },
    bottomBar = {
      if (cartItems.isNotEmpty()) {
        // Bottom sticky bar: PAY ON DELIVERY & Place Order Button
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
                text = "PAY ON DELIVERY",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = JTTextSecondary
              )
              Text(
                text = "PKR ${String.format("%,d", grandTotal)}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = JTPurplePrimary
              )
            }

            Button(
              onClick = {
                if (selectedAddress == null) {
                  Toast.makeText(context, "Please select or add a delivery address first", Toast.LENGTH_SHORT).show()
                  showAddressDialog = true
                } else {
                  MarketplaceRepository.placeCurrentOrder(deliveryNote = deliveryNote) { result ->
                    if (result.isSuccess) {
                      Toast.makeText(context, "Order Placed Successfully! Shukriya!", Toast.LENGTH_SHORT).show()
                      onOrderPlaced()
                    } else {
                      val err = result.exceptionOrNull()?.message ?: "Order placement failed"
                      Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                  }
                }
              },
              enabled = !isPlacingOrder && selectedAddress != null,
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = JTOrange,
                contentColor = Color.White
              ),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
              modifier = Modifier.testTag("place_order_button")
            ) {
              if (isPlacingOrder) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text("PLACING ORDER...", fontWeight = FontWeight.Bold, fontSize = 11.sp)
              } else {
                Icon(
                  imageVector = Icons.Filled.LocalShipping,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "PLACE ORDER VIA CASH ON DELIVERY",
                  fontWeight = FontWeight.Bold,
                  fontSize = 11.sp,
                  letterSpacing = 0.3.sp
                )
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
      contentPadding = PaddingValues(bottom = 28.dp)
    ) {
      // 1. SSL & Genuine Marketplace Banner
      item {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = JTPurpleContainer.copy(alpha = 0.5f),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f)
            ) {
              Icon(
                imageVector = Icons.Filled.VerifiedUser,
                contentDescription = null,
                tint = JTPurplePrimary,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "100% Genuine Pakistani Marketplace Checkout",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = JTTextPrimary
              )
            }

            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color.White
            ) {
              Text(
                text = "SSL Secure",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = JTPurplePrimary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
              )
            }
          }
        }
      }

      if (cartItems.isEmpty()) {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 24.dp, vertical = 60.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Box(
                modifier = Modifier
                  .size(80.dp)
                  .clip(CircleShape)
                  .background(JTPurpleContainer),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.ShoppingCart,
                  contentDescription = null,
                  tint = JTPurplePrimary,
                  modifier = Modifier.size(40.dp)
                )
              }
              Spacer(modifier = Modifier.height(16.dp))
              Text(
                text = "Your Cart is Empty",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = JTTextPrimary
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Explore live Pakistani crafts, authentic spices, and daily essentials from verified vendors.",
                fontSize = 12.sp,
                color = JTTextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )
              Spacer(modifier = Modifier.height(20.dp))
              Button(
                onClick = onBackClick,
                colors = ButtonDefaults.buttonColors(containerColor = JTPurplePrimary),
                shape = RoundedCornerShape(10.dp)
              ) {
                Icon(Icons.Filled.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Shopping", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      } else {
        // 2. Delivery Address Card
        item {
          val currentAddr = selectedAddress
          Spacer(modifier = Modifier.height(6.dp))
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
                      .size(28.dp)
                      .clip(CircleShape)
                      .background(JTPurpleContainer),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Filled.LocationOn,
                      contentDescription = null,
                      tint = JTPurplePrimary,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "DELIVERY ADDRESS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = JTTextPrimary,
                    letterSpacing = 0.5.sp
                  )
                  if (currentAddr != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                      shape = RoundedCornerShape(4.dp),
                      color = JTAmberContainer
                    ) {
                      Text(
                        text = currentAddr.tag,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = JTAmberDark,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                  }
                }

                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = JTPurpleContainer,
                  modifier = Modifier.clickable { showAddressDialog = true }
                ) {
                  Text(
                    text = if (currentAddr == null) "+ Add" else "Change",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = JTPurplePrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                  )
                }
              }

              Spacer(modifier = Modifier.height(10.dp))

              if (currentAddr != null) {
                Text(
                  text = currentAddr.name,
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp,
                  color = JTTextPrimary
                )
                Text(
                  text = currentAddr.phone,
                  fontSize = 12.sp,
                  color = JTTextSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "${currentAddr.addressLine}, ${currentAddr.area}, ${currentAddr.city}, ${currentAddr.province}",
                  fontSize = 11.sp,
                  color = JTTextSecondary,
                  lineHeight = 15.sp
                )
              } else {
                Text(
                  text = "No delivery address selected. Please add your Lahore or Pakistan delivery address to proceed with Cash on Delivery.",
                  fontSize = 12.sp,
                  color = JTRed,
                  fontWeight = FontWeight.Medium
                )
              }
            }
          }
        }

        // 3. Dynamic Grouped Items by Vendor
        val groupedVendors = cartItems.groupBy { it.product.vendor.ifBlank { "JTBazar Verified Vendor" } }
        groupedVendors.forEach { (vendorName, vendorItems) ->
          item(key = vendorName) {
            Spacer(modifier = Modifier.height(14.dp))
            VendorItemsCard(
              vendorName = vendorName,
              vendorTypeIcon = Icons.Filled.Storefront,
              badge = "${vendorItems.size} ${if (vendorItems.size == 1) "item" else "items"}",
              verifiedText = "Verified Vendor",
              items = vendorItems
            )
          }
        }
      }

      // 4. Delivery & Instructions Card
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
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.LocalShipping,
                contentDescription = null,
                tint = JTPurplePrimary,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "DELIVERY & INSTRUCTIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = JTTextPrimary,
                letterSpacing = 0.5.sp
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Standard Courier Radio Box
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = JTSurfaceVariant,
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Box(
                    modifier = Modifier
                      .size(14.dp)
                      .clip(CircleShape)
                      .background(JTPurplePrimary)
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Text(
                      text = "Standard Courier (2-3 Days)",
                      fontWeight = FontWeight.Bold,
                      fontSize = 12.sp,
                      color = JTTextPrimary
                    )
                    Text(
                      text = "TCS / Leopards Express Tracking",
                      fontSize = 10.sp,
                      color = JTTextSecondary
                    )
                  }
                }

                Text(
                  text = "Rs. 150",
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = JTTextPrimary
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Delivery Note (Optional)
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.EditNote,
                contentDescription = null,
                tint = JTTextSecondary,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Delivery Note (Optional)",
                fontSize = 11.sp,
                color = JTTextSecondary,
                fontWeight = FontWeight.SemiBold
              )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
              shape = RoundedCornerShape(10.dp),
              color = JTSurfaceVariant,
              modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
            ) {
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
              ) {
                androidx.compose.foundation.text.BasicTextField(
                  value = deliveryNote,
                  onValueChange = { deliveryNote = it },
                  singleLine = true,
                  textStyle = androidx.compose.ui.text.TextStyle(
                    color = JTTextPrimary,
                    fontSize = 11.sp
                  ),
                  modifier = Modifier.fillMaxWidth().testTag("delivery_note_input")
                )
              }
            }
          }
        }
      }

      // 5. Payment Method Card (COD Preferred)
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
                Icon(
                  imageVector = Icons.Filled.Payment,
                  contentDescription = null,
                  tint = JTPurplePrimary,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "PAYMENT METHOD",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = JTTextPrimary,
                  letterSpacing = 0.5.sp
                )
              }

              Surface(
                shape = RoundedCornerShape(6.dp),
                color = JTAmberContainer
              ) {
                Text(
                  text = "Preferred",
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  color = JTAmberDark,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // COD Option Container
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = JTPurpleSurface,
              border = androidx.compose.foundation.BorderStroke(1.dp, JTPurpleContainerBorder),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(JTPurpleContainer),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Filled.Money,
                        contentDescription = null,
                        tint = JTPurplePrimary,
                        modifier = Modifier.size(20.dp)
                      )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                          text = "Cash on Delivery (COD)",
                          fontWeight = FontWeight.Bold,
                          fontSize = 13.sp,
                          color = JTTextPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                          imageVector = Icons.Filled.CheckCircle,
                          contentDescription = null,
                          tint = JTPurplePrimary,
                          modifier = Modifier.size(13.dp)
                        )
                      }
                      Text(
                        text = "Pay with PKR cash at delivery",
                        fontSize = 10.sp,
                        color = JTTextSecondary
                      )
                    }
                  }

                  // Radio checked icon
                  Box(
                    modifier = Modifier
                      .size(20.dp)
                      .clip(CircleShape)
                      .background(JTPurplePrimary),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Check,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(14.dp)
                    )
                  }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Info note
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = Color.White
                ) {
                  Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.Top
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Info,
                      contentDescription = null,
                      tint = JTAmberDark,
                      modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "Pay securely in cash at your doorstep upon receiving your parcel. No advance card required.",
                      fontSize = 10.sp,
                      color = JTTextSecondary,
                      lineHeight = 14.sp
                    )
                  }
                }
              }
            }
          }
        }
      }

      // 6. Bill Breakdown Card
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
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = null,
                tint = JTPurplePrimary,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "BILL BREAKDOWN",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = JTTextPrimary,
                letterSpacing = 0.5.sp
              )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Subtotal
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "Items Subtotal (${cartCount} items)",
                fontSize = 12.sp,
                color = JTTextSecondary
              )
              Text(
                text = "Rs. ${String.format("%,d", subtotal)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = JTTextPrimary
              )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Store Discount
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Filled.LocalOffer,
                  contentDescription = null,
                  tint = JTAmberDark,
                  modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "Store Discount (JTBAZAR100)",
                  fontSize = 12.sp,
                  color = JTAmberDark,
                  fontWeight = FontWeight.SemiBold
                )
              }
              Text(
                text = "- Rs. 100",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = JTEmerald
              )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Delivery Charges
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "Delivery Charges",
                fontSize = 12.sp,
                color = JTTextSecondary
              )
              Text(
                text = "Rs. ${String.format("%,d", deliveryCharges)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = JTTextPrimary
              )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = JTBorder)
            Spacer(modifier = Modifier.height(10.dp))

            // Grand Total
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "Grand Total",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = JTTextPrimary
                )
                Text(
                  text = "Includes all applicable taxes",
                  fontSize = 9.sp,
                  color = JTTextSecondary
                )
              }
              Text(
                text = "Rs. ${String.format("%,d", grandTotal)}",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = JTPurplePrimary
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun VendorItemsCard(
  vendorName: String,
  vendorTypeIcon: androidx.compose.ui.graphics.vector.ImageVector,
  badge: String,
  verifiedText: String? = null,
  fastDispatch: Boolean = false,
  items: List<CartItem>
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = JTCardBg,
    border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      // Vendor Header Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = vendorTypeIcon,
            contentDescription = null,
            tint = JTPurplePrimary,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = vendorName,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = JTTextPrimary
          )
          Spacer(modifier = Modifier.width(6.dp))
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = JTPurpleContainer
          ) {
            Text(
              text = badge,
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = JTPurplePrimary,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }

        if (verifiedText != null) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.CheckCircle,
              contentDescription = null,
              tint = JTAmberDark,
              modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = verifiedText,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = JTAmberDark
            )
          }
        } else if (fastDispatch) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.LocalShipping,
              contentDescription = null,
              tint = JTPurplePrimary,
              modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = "Fast dispatch",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = JTPurplePrimary
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Cart Items List
      items.forEachIndexed { index, cartItem ->
        if (index > 0) {
          Spacer(modifier = Modifier.height(10.dp))
          HorizontalDivider(color = JTBorder)
          Spacer(modifier = Modifier.height(10.dp))
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(68.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(JTSurfaceVariant)
          ) {
            if (!cartItem.product.imageUrl.isNullOrBlank()) {
              coil.compose.AsyncImage(
                model = cartItem.product.imageUrl,
                contentDescription = cartItem.product.title,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = cartItem.product.drawableRes),
                error = painterResource(id = cartItem.product.drawableRes),
                modifier = Modifier.fillMaxSize()
              )
            } else {
              Image(
                painter = painterResource(id = cartItem.product.drawableRes),
                contentDescription = cartItem.product.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            }
          }

          Spacer(modifier = Modifier.width(10.dp))

          Column(modifier = Modifier.weight(1f)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.Top
            ) {
              Text(
                text = if (cartItem.selectedPalette != null) "${cartItem.product.title.split("(")[0]} (${cartItem.selectedPalette}, ${cartItem.selectedSize ?: ""})" else cartItem.product.title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = JTTextPrimary,
                maxLines = 2,
                modifier = Modifier.weight(1f)
              )

              IconButton(
                onClick = { MarketplaceRepository.removeFromCart(cartItem.id) },
                modifier = Modifier.size(24.dp)
              ) {
                Icon(
                  imageVector = Icons.Outlined.Delete,
                  contentDescription = "Remove",
                  tint = JTTextSecondary,
                  modifier = Modifier.size(16.dp)
                )
              }
            }

            val subtitleText = when (cartItem.product.id) {
              "prod_multani_plate" -> "Glazed Clay • Multan City"
              "prod_shan_biryani" -> "Rs. 380 each"
              "prod_pure_honey" -> "100% Natural Organic"
              else -> cartItem.product.category
            }
            Text(
              text = subtitleText,
              fontSize = 10.sp,
              color = JTTextSecondary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Rs. ${String.format("%,d", cartItem.totalPrice)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = JTPurplePrimary
              )

              QuantityStepper(
                quantity = cartItem.quantity,
                onIncrement = { MarketplaceRepository.updateQuantity(cartItem.id, 1) },
                onDecrement = { MarketplaceRepository.updateQuantity(cartItem.id, -1) }
              )
            }
          }
        }
      }
    }
  }
}
