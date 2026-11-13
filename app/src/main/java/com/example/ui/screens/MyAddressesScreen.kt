package com.example.ui.screens

import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.example.location.AddressReverseGeocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DeliveryAddress
import com.example.data.repository.MarketplaceRepository
import com.example.ui.theme.*

@Composable
fun MyAddressesScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val addresses by MarketplaceRepository.addresses.collectAsState()
  val selectedAddress by MarketplaceRepository.selectedAddress.collectAsState()

  var showAddAddressSheet by remember { mutableStateOf(false) }

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
          modifier = Modifier.testTag("addresses_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = JTTextPrimary
          )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "My Addresses",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = JTTextPrimary
        )
      }
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showAddAddressSheet = true },
        containerColor = JTOrange,
        contentColor = Color.White,
        modifier = Modifier.testTag("fab_add_address")
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Add Address")
      }
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    if (addresses.isEmpty()) {
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
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = JTPurplePrimary, modifier = Modifier.size(40.dp))
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "No Saved Addresses",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = JTTextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Add your home, office, or family delivery address for swift checkout.",
          fontSize = 13.sp,
          color = JTTextSecondary,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
          onClick = { showAddAddressSheet = true },
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White),
          modifier = Modifier.height(44.dp)
        ) {
          Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Add New Address", fontWeight = FontWeight.Bold)
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        items(addresses, key = { it.id }) { address ->
          val isSelected = selectedAddress?.id == address.id
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isSelected) JTPurpleContainer.copy(alpha = 0.35f) else JTCardBg,
            border = androidx.compose.foundation.BorderStroke(
              1.dp,
              if (isSelected) JTPurplePrimary else JTBorder
            ),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { MarketplaceRepository.selectAddress(address) }
              .testTag("address_item_${address.id}")
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.Top
            ) {
              Icon(
                imageVector = if (address.tag.equals("WORK", true)) Icons.Filled.Work else Icons.Filled.Home,
                contentDescription = null,
                tint = JTPurplePrimary,
                modifier = Modifier.size(24.dp)
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = address.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = JTTextPrimary
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = JTSurfaceVariant
                  ) {
                    Text(
                      text = address.tag,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      color = JTPurplePrimary,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                  if (address.isDefault) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                      shape = RoundedCornerShape(4.dp),
                      color = Color(0xFFDEF7EC)
                    ) {
                      Text(
                        text = "DEFAULT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF03543F),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                  }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "${address.addressLine}, ${address.area}, ${address.city}",
                  fontSize = 12.sp,
                  color = JTTextPrimary
                )
                Text(
                  text = address.phone,
                  fontSize = 12.sp,
                  color = JTTextSecondary
                )
              }

              IconButton(
                onClick = {
                  MarketplaceRepository.deleteAddress(address.id)
                  Toast.makeText(context, "Address deleted", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(32.dp)
              ) {
                Icon(
                  imageVector = Icons.Filled.DeleteOutline,
                  contentDescription = "Delete",
                  tint = Color(0xFFDC2626),
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }
        }
      }
    }
  }

  if (showAddAddressSheet) {
    AddAddressDialog(
      onDismiss = { showAddAddressSheet = false },
      onSave = { newAddr ->
        MarketplaceRepository.addOrUpdateAddress(newAddr)
        showAddAddressSheet = false
      }
    )
  }
}

@Composable
private fun AddAddressDialog(
  onDismiss: () -> Unit,
  onSave: (DeliveryAddress) -> Unit
) {
  var name by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf("") }
  var street by remember { mutableStateOf("") }
  var area by remember { mutableStateOf("") }
  var city by remember { mutableStateOf("") }
  var tag by remember { mutableStateOf("HOME") }
  var isDefault by remember { mutableStateOf(false) }
  var latitude by remember { mutableStateOf<Double?>(null) }
  var longitude by remember { mutableStateOf<Double?>(null) }
  var locating by remember { mutableStateOf(false) }
  var locationMessage by remember { mutableStateOf<String?>(null) }
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val locationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    if (!granted) {
      locationMessage = "Location permission is needed to detect your address."
    } else {
      try {
        locating = true
        LocationServices.getFusedLocationProviderClient(context)
          .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
          .addOnSuccessListener { location ->
            if (location == null) {
              locating = false
              locationMessage = "Could not get a fresh GPS fix. Please try again."
            } else {
              latitude = location.latitude
              longitude = location.longitude
              scope.launch {
                val resolved = AddressReverseGeocoder.resolve(context, location.latitude, location.longitude)
                if (resolved != null) {
                  if (resolved.street.isNotBlank()) street = resolved.street
                  if (resolved.area.isNotBlank()) area = resolved.area
                  if (resolved.city.isNotBlank()) city = resolved.city
                  locationMessage = if (resolved.street.isBlank()) "Location found. Please enter your street and house number." else "Address detected. Please verify your house number."
                } else {
                  locationMessage = "GPS captured. Please enter street and area manually."
                }
                locating = false
              }
            }
          }
          .addOnFailureListener {
            locating = false
            locationMessage = "Unable to detect location. Please try again."
          }
      } catch (_: SecurityException) {
        locating = false
        locationMessage = "Location permission is required."
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Delivery Address", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedButton(
          enabled = !locating,
          onClick = {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fine || coarse) locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Icon(Icons.Filled.MyLocation, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text(if (locating) "Detecting address..." else "Use Current Location")
        }
        locationMessage?.let { Text(it, color = JTTextSecondary, fontSize = 11.sp) }
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Recipient Name *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = phone,
          onValueChange = { phone = it },
          label = { Text("Phone Number *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = street,
          onValueChange = { street = it },
          label = { Text("Street Address *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = area,
          onValueChange = { area = it },
          label = { Text("Area / Locality") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = city,
          onValueChange = { city = it },
          label = { Text("City *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf("HOME", "WORK", "OTHER").forEach { t ->
            FilterChip(
              selected = tag == t,
              onClick = { tag = t },
              label = { Text(t, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = isDefault, onCheckedChange = { isDefault = it })
          Spacer(modifier = Modifier.width(4.dp))
          Text("Set as default delivery address", fontSize = 12.sp)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (name.isNotBlank() && phone.isNotBlank() && street.isNotBlank() && city.isNotBlank()) {
            onSave(
              DeliveryAddress(
                id = "addr_${System.currentTimeMillis()}",
                name = name.trim(),
                phone = phone.trim(),
                addressLine = street.trim(),
                area = area.trim(),
                city = city.trim(),
                tag = tag,
                isDefault = isDefault,
                latitude = latitude,
                longitude = longitude
              )
            )
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = JTOrange)
      ) {
        Text("Save Address")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}
