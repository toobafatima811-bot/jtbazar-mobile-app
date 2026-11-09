package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingBag
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
import com.example.R
import com.example.data.model.DeliveryAddress
import com.example.data.repository.MarketplaceRepository
import com.example.ui.theme.*

@Composable
fun JTBazarBrandLogo(modifier: Modifier = Modifier) {
  Image(
    painter = painterResource(id = R.drawable.logo_jtbazar_header),
    contentDescription = "JTBazar Logo",
    contentScale = ContentScale.Fit,
    modifier = modifier
      .height(30.dp)
      .widthIn(max = 135.dp)
  )
}

@Composable
fun JTBazarTopBar(
  title: String? = null,
  showBack: Boolean = false,
  onBackClick: () -> Unit = {},
  onCartClick: () -> Unit = {},
  onAvatarClick: () -> Unit = {},
  onLocationClick: () -> Unit = {},
  cartCount: Int = 2,
  currentLocation: String = "Select delivery address",
  modifier: Modifier = Modifier
) {
  Surface(
    color = Color.White,
    tonalElevation = 0.dp,
    shadowElevation = 0.5.dp,
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f, fill = false)
      ) {
        if (showBack) {
          IconButton(
            onClick = onBackClick,
            modifier = Modifier
              .size(38.dp)
              .testTag("back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = JTTextPrimary
            )
          }
          Spacer(modifier = Modifier.width(4.dp))
        }

        JTBazarBrandLogo()

        if (title != null) {
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = JTTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // Location dropdown chip if no title
        if (title == null) {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = JTPurpleContainer.copy(alpha = 0.5f),
            modifier = Modifier
              .clickable { onLocationClick() }
              .testTag("location_picker")
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = JTPurplePrimary,
                modifier = Modifier.size(13.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = currentLocation,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = JTTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = JTTextSecondary,
                modifier = Modifier.size(14.dp)
              )
            }
          }
        }

        // Cart Icon with Badge
        Box(
          modifier = Modifier
            .clickable(onClick = onCartClick)
            .testTag("cart_icon_badge")
            .padding(4.dp)
        ) {
          Icon(
            imageVector = Icons.Outlined.ShoppingBag,
            contentDescription = "Shopping Cart",
            tint = JTTextPrimary,
            modifier = Modifier.size(24.dp)
          )
          if (cartCount > 0) {
            Box(
              modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-4).dp)
                .size(17.dp)
                .clip(CircleShape)
                .background(JTPurplePrimary),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "$cartCount",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }

        // User Avatar
        val currentProfile by com.example.data.supabase.SupabaseAuthService.userProfile.collectAsState()
        UserAvatarView(
          avatarUrl = currentProfile?.avatarUrl,
          fullName = currentProfile?.fullName,
          size = 32.dp,
          onClick = onAvatarClick,
          modifier = Modifier.testTag("user_avatar_button")
        )
      }
    }
  }
}

@Composable
fun UserAvatarView(
  avatarUrl: String? = null,
  fullName: String? = null,
  size: androidx.compose.ui.unit.Dp = 32.dp,
  onClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
  Box(
    modifier = modifier
      .size(size)
      .clip(CircleShape)
      .border(1.5.dp, JTPurpleContainerBorder, CircleShape)
      .then(clickModifier),
    contentAlignment = Alignment.Center
  ) {
    val validAvatarUrl = avatarUrl?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    if (validAvatarUrl != null) {
      coil.compose.AsyncImage(
        model = validAvatarUrl,
        contentDescription = "User Avatar",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
    } else if (!fullName.isNullOrBlank()) {
      val initial = fullName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "J"
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(JTPurplePrimary),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = initial,
          color = Color.White,
          fontWeight = FontWeight.Bold,
          fontSize = (size.value * 0.42f).sp
        )
      }
    } else {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(JTSurfaceVariant),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Filled.AccountCircle,
          contentDescription = "Account",
          tint = JTPurplePrimary,
          modifier = Modifier.size((size.value * 0.7f).dp)
        )
      }
    }
  }
}

@Composable
fun QuantityStepper(
  quantity: Int,
  onIncrement: () -> Unit,
  onDecrement: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .background(JTSurfaceVariant, RoundedCornerShape(8.dp))
      .padding(horizontal = 4.dp, vertical = 2.dp)
  ) {
    IconButton(
      onClick = onDecrement,
      modifier = Modifier
        .size(32.dp)
        .testTag("decrement_qty")
    ) {
      Icon(
        imageVector = Icons.Filled.Remove,
        contentDescription = "Decrease",
        modifier = Modifier.size(16.dp),
        tint = JTTextPrimary
      )
    }

    Text(
      text = "$quantity",
      fontWeight = FontWeight.Bold,
      fontSize = 14.sp,
      color = JTTextPrimary,
      modifier = Modifier.padding(horizontal = 10.dp)
    )

    IconButton(
      onClick = onIncrement,
      modifier = Modifier
        .size(32.dp)
        .testTag("increment_qty")
    ) {
      Icon(
        imageVector = Icons.Filled.Add,
        contentDescription = "Increase",
        modifier = Modifier.size(16.dp),
        tint = JTTextPrimary
      )
    }
  }
}

@Composable
fun TrustBadgesRow(modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 14.dp, horizontal = 16.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    TrustItem(icon = Icons.Filled.Security, title = "100% Genuine")
    TrustItem(icon = Icons.Filled.Payment, title = "Cash On Delivery")
    TrustItem(icon = Icons.Filled.HeadsetMic, title = "24/7 Helpline")
  }
}

@Composable
private fun TrustItem(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = JTPurpleContainer.copy(alpha = 0.45f),
    modifier = Modifier.padding(horizontal = 2.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = JTPurplePrimary,
        modifier = Modifier.size(15.dp)
      )
      Spacer(modifier = Modifier.width(5.dp))
      Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = JTTextPrimary
      )
    }
  }
}

@Composable
fun AddressSelectionSheet(
  addresses: List<DeliveryAddress>,
  selectedAddress: DeliveryAddress?,
  onSelect: (DeliveryAddress) -> Unit,
  onDismiss: () -> Unit,
  onAddNewAddress: ((DeliveryAddress) -> Unit)? = null
) {
  var showNewAddressForm by remember { mutableStateOf(addresses.isEmpty()) }
  var name by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf("") }
  var addressLine by remember { mutableStateOf("") }
  var area by remember { mutableStateOf("Gulberg III") }
  var city by remember { mutableStateOf("") }
  var tag by remember { mutableStateOf("HOME") }

  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      if (showNewAddressForm) {
        Button(
          onClick = {
            if (name.isNotBlank() && phone.isNotBlank() && addressLine.isNotBlank()) {
              val newAddr = DeliveryAddress(
                id = "addr_${System.currentTimeMillis()}",
                name = name.trim(),
                phone = phone.trim(),
                addressLine = addressLine.trim(),
                area = area.trim(),
                city = city.trim(),
                province = "Punjab",
                tag = tag,
                isDefault = addresses.isEmpty()
              )
              onAddNewAddress?.invoke(newAddr)
              onSelect(newAddr)
              onDismiss()
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = JTPurplePrimary)
        ) {
          Text("Save & Deliver Here", fontWeight = FontWeight.Bold)
        }
      } else {
        TextButton(onClick = onDismiss) {
          Text("Done", color = JTPurplePrimary, fontWeight = FontWeight.Bold)
        }
      }
    },
    dismissButton = {
      if (showNewAddressForm && addresses.isNotEmpty()) {
        TextButton(onClick = { showNewAddressForm = false }) {
          Text("Cancel", color = JTTextSecondary)
        }
      }
    },
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = JTPurplePrimary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = if (showNewAddressForm) "Add Delivery Address" else "Select Delivery Area",
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp
        )
      }
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        if (showNewAddressForm) {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Recipient Full Name") },
            placeholder = { Text("e.g. Tariq Mehmood") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Mobile Phone Number") },
            placeholder = { Text("+92 300 1234567") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = addressLine,
            onValueChange = { addressLine = it },
            label = { Text("House / Street / Building") },
            placeholder = { Text("House # 12, Street 4") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = area,
              onValueChange = { area = it },
              label = { Text("Area / Colony") },
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
              value = city,
              onValueChange = { city = it },
              label = { Text("City") },
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
          }
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            listOf("HOME", "OFFICE").forEach { t ->
              FilterChip(
                selected = tag == t,
                onClick = { tag = t },
                label = { Text(t, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
              )
            }
          }
        } else {
          if (addresses.isEmpty()) {
            Text(
              text = "No saved addresses found. Add an address to receive your Cash on Delivery orders.",
              fontSize = 12.sp,
              color = JTTextSecondary
            )
          } else {
            addresses.forEach { addr ->
              val isSelected = addr.id == selectedAddress?.id
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) JTPurpleContainer.copy(alpha = 0.6f) else JTBackground,
                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, JTPurplePrimary) else androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { onSelect(addr) }
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(
                        text = "${addr.area}, ${addr.city}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = JTAmberContainer
                      ) {
                        Text(
                          text = addr.tag,
                          fontSize = 10.sp,
                          fontWeight = FontWeight.Bold,
                          color = JTAmberDark,
                          modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                      }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = addr.addressLine,
                      fontSize = 12.sp,
                      color = JTTextSecondary
                    )
                  }
                  if (isSelected) {
                    Icon(
                      Icons.Filled.Check,
                      contentDescription = "Selected",
                      tint = JTPurplePrimary,
                      modifier = Modifier.size(20.dp)
                    )
                  }
                }
              }
            }
          }

          OutlinedButton(
            onClick = { showNewAddressForm = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
          ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add New Delivery Address", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  )
}
