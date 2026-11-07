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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.DeliveryAddress
import com.example.data.repository.MarketplaceRepository
import com.example.data.supabase.*
import com.example.ui.components.AddressSelectionSheet
import com.example.ui.components.JTBazarTopBar
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(
  onViewOrderTracking: () -> Unit,
  onCartClick: () -> Unit,
  onShowSplash: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val cartCount by MarketplaceRepository.cartCount.collectAsState()
  val currentLocation by MarketplaceRepository.currentLocation.collectAsState()
  val selectedAddress by MarketplaceRepository.selectedAddress.collectAsState()
  val addresses by MarketplaceRepository.addresses.collectAsState()
  val activeOrder by MarketplaceRepository.activeOrder.collectAsState()
  val notifications by MarketplaceRepository.notifications.collectAsState()
  val reviews by MarketplaceRepository.reviews.collectAsState()

  val currentSession by SupabaseClient.session.collectAsState()
  val userProfile by SupabaseAuthService.userProfile.collectAsState()
  val isLoggedIn = currentSession != null

  // Dialogs state
  var showAddressDialog by remember { mutableStateOf(false) }
  var showAuthDialog by remember { mutableStateOf(false) }
  var authTab by remember { mutableIntStateOf(0) } // 0: Login, 1: Register, 2: Forgot Password

  var showEditProfileDialog by remember { mutableStateOf(false) }
  var showNotificationsDialog by remember { mutableStateOf(false) }
  var showReviewsDialog by remember { mutableStateOf(false) }
  var showReturnDialog by remember { mutableStateOf(false) }
  var showComplaintDialog by remember { mutableStateOf(false) }
  var showPolicyDialog by remember { mutableStateOf(false) }
  var policyTitle by remember { mutableStateOf("") }
  var policyContent by remember { mutableStateOf("") }

  // Auth form states
  var authEmail by remember { mutableStateOf("") }
  var authPassword by remember { mutableStateOf("") }
  var authFullName by remember { mutableStateOf("") }
  var authPhone by remember { mutableStateOf("") }
  var authLoading by remember { mutableStateOf(false) }
  var authError by remember { mutableStateOf<String?>(null) }

  // Edit Profile form states
  var editFullName by remember { mutableStateOf(userProfile?.fullName ?: selectedAddress?.name ?: "") }
  var editPhone by remember { mutableStateOf(userProfile?.phone ?: selectedAddress?.phone ?: "") }
  var editCity by remember { mutableStateOf(userProfile?.city ?: selectedAddress?.city ?: "") }

  // Complaint / Support form states
  var complaintSubject by remember { mutableStateOf("") }
  var complaintMessage by remember { mutableStateOf("") }

  // Return request form states
  var returnReason by remember { mutableStateOf("Damaged item") }
  var returnDescription by remember { mutableStateOf("") }

  // ----------------------------------------------------
  // AUTH DIALOG (LOGIN / REGISTER / FORGOT PASSWORD)
  // ----------------------------------------------------
  if (showAuthDialog) {
    AlertDialog(
      onDismissRequest = {
        if (!authLoading) {
          showAuthDialog = false
          authError = null
        }
      },
      title = {
        Text(
          text = when (authTab) {
            0 -> "Sign In to JTBazar"
            1 -> "Create Customer Account"
            else -> "Reset Password"
          },
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp,
          color = JTTextPrimary
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          if (authTab < 2) {
            TabRow(
              selectedTabIndex = authTab,
              containerColor = JTSurfaceVariant,
              contentColor = JTPurplePrimary
            ) {
              Tab(
                selected = authTab == 0,
                onClick = { authTab = 0; authError = null },
                text = { Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
              )
              Tab(
                selected = authTab == 1,
                onClick = { authTab = 1; authError = null },
                text = { Text("Register", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
              )
            }
          }

          if (authError != null) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = JTRedContainer,
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = authError ?: "",
                color = JTRed,
                fontSize = 11.sp,
                modifier = Modifier.padding(8.dp)
              )
            }
          }

          if (authTab == 1) {
            OutlinedTextField(
              value = authFullName,
              onValueChange = { authFullName = it },
              label = { Text("Full Name") },
              placeholder = { Text("Enter your full name") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = authPhone,
              onValueChange = { authPhone = it },
              label = { Text("Mobile Number") },
              placeholder = { Text("+92 300 1234567") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
          }

          OutlinedTextField(
            value = authEmail,
            onValueChange = { authEmail = it },
            label = { Text("Email Address") },
            placeholder = { Text("customer@jtbazar.pk") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          if (authTab < 2) {
            OutlinedTextField(
              value = authPassword,
              onValueChange = { authPassword = it },
              label = { Text("Password") },
              singleLine = true,
              visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
              modifier = Modifier.fillMaxWidth()
            )
          }

          if (authTab == 0) {
            TextButton(
              onClick = { authTab = 2; authError = null },
              contentPadding = PaddingValues(0.dp)
            ) {
              Text("Forgot Password?", color = JTPurplePrimary, fontSize = 11.sp)
            }
          } else if (authTab == 2) {
            Text(
              text = "Enter your email address and we will send you a password recovery link.",
              fontSize = 11.sp,
              color = JTTextSecondary
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            authError = null
            authLoading = true
            coroutineScope.launch {
              when (authTab) {
                0 -> {
                  val res = SupabaseAuthService.signIn(authEmail, authPassword, context)
                  authLoading = false
                  if (res.isSuccess) {
                    showAuthDialog = false
                    Toast.makeText(context, "Welcome back to JTBazar!", Toast.LENGTH_SHORT).show()
                  } else {
                    authError = res.exceptionOrNull()?.message ?: "Sign in failed"
                  }
                }
                1 -> {
                  val res = SupabaseAuthService.signUp(authEmail, authPassword, authFullName, authPhone, context)
                  authLoading = false
                  if (res.isSuccess) {
                    showAuthDialog = false
                    Toast.makeText(context, "Account created! Welcome to JTBazar!", Toast.LENGTH_SHORT).show()
                  } else {
                    authError = res.exceptionOrNull()?.message ?: "Registration failed"
                  }
                }
                2 -> {
                  val res = SupabaseAuthService.resetPassword(authEmail)
                  authLoading = false
                  if (res.isSuccess) {
                    showAuthDialog = false
                    Toast.makeText(context, "Password reset instructions sent to your email", Toast.LENGTH_LONG).show()
                  } else {
                    authError = res.exceptionOrNull()?.message ?: "Password reset failed"
                  }
                }
              }
            }
          },
          enabled = !authLoading && authEmail.isNotBlank(),
          colors = ButtonDefaults.buttonColors(containerColor = JTOrange)
        ) {
          if (authLoading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
          } else {
            Text(if (authTab == 0) "Sign In" else if (authTab == 1) "Register" else "Send Reset Link")
          }
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            if (authTab == 2) authTab = 0 else showAuthDialog = false
          },
          enabled = !authLoading
        ) {
          Text(if (authTab == 2) "Back to Sign In" else "Cancel", color = JTTextSecondary)
        }
      }
    )
  }

  // ----------------------------------------------------
  // EDIT PROFILE DIALOG
  // ----------------------------------------------------
  if (showEditProfileDialog) {
    AlertDialog(
      onDismissRequest = { showEditProfileDialog = false },
      title = { Text("Edit Customer Profile", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = editFullName,
            onValueChange = { editFullName = it },
            label = { Text("Full Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = editPhone,
            onValueChange = { editPhone = it },
            label = { Text("Phone Number") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = editCity,
            onValueChange = { editCity = it },
            label = { Text("City") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val userId = SupabaseClient.currentUserId ?: "cust_user"
            val newProfile = SupabaseProfile(
              id = userId,
              fullName = editFullName.ifBlank { "JTBazar Customer" },
              phone = editPhone,
              email = userProfile?.email ?: "customer@jtbazar.pk",
              city = editCity
            )
            coroutineScope.launch {
              SupabaseAuthService.upsertProfile(newProfile)
              // Also update selected address name and phone if one exists
              selectedAddress?.let { addr ->
                MarketplaceRepository.addOrUpdateAddress(
                  addr.copy(
                    name = newProfile.fullName,
                    phone = newProfile.phone,
                    city = newProfile.city
                  )
                )
              }
            }
            showEditProfileDialog = false
            Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = JTPurplePrimary)
        ) {
          Text("Save Profile")
        }
      },
      dismissButton = {
        TextButton(onClick = { showEditProfileDialog = false }) {
          Text("Cancel", color = JTTextSecondary)
        }
      }
    )
  }

  // ----------------------------------------------------
  // NOTIFICATIONS DIALOG
  // ----------------------------------------------------
  if (showNotificationsDialog) {
    AlertDialog(
      onDismissRequest = { showNotificationsDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.Notifications, contentDescription = null, tint = JTOrange, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Notifications", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
      },
      text = {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (notifications.isEmpty()) {
            item {
              Text("No notifications right now.", fontSize = 12.sp, color = JTTextSecondary)
            }
          } else {
            items(notifications) { notif ->
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = JTSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(10.dp)) {
                  Text(text = notif.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = JTTextPrimary)
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(text = notif.message, fontSize = 11.sp, color = JTTextSecondary)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(text = notif.createdAt, fontSize = 9.sp, color = JTTextTertiary)
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showNotificationsDialog = false }) {
          Text("Close", color = JTPurplePrimary)
        }
      }
    )
  }

  // ----------------------------------------------------
  // MY REVIEWS DIALOG
  // ----------------------------------------------------
  if (showReviewsDialog) {
    AlertDialog(
      onDismissRequest = { showReviewsDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.RateReview, contentDescription = null, tint = JTAmberDark, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("My Product Reviews", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
      },
      text = {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (reviews.isEmpty()) {
            item {
              Text(
                "You haven't reviewed any products yet. Go to any product details screen to submit your verified review!",
                fontSize = 11.sp,
                color = JTTextSecondary
              )
            }
          } else {
            items(reviews) { rev ->
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
                    Text(text = rev.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = JTTextPrimary)
                    Row {
                      repeat(rev.rating) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = JTAmber, modifier = Modifier.size(11.dp))
                      }
                    }
                  }
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(text = rev.comment, fontSize = 11.sp, color = JTTextSecondary)
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showReviewsDialog = false }) {
          Text("Close", color = JTPurplePrimary)
        }
      }
    )
  }

  // ----------------------------------------------------
  // RETURN & REFUND REQUEST DIALOG
  // ----------------------------------------------------
  if (showReturnDialog) {
    var returnOrderId by remember { mutableStateOf(activeOrder?.orderId ?: "") }
    AlertDialog(
      onDismissRequest = { showReturnDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.AssignmentReturn, contentDescription = null, tint = JTRed, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Return & Refund Request", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = returnOrderId,
            onValueChange = { returnOrderId = it },
            label = { Text("Order Number") },
            placeholder = { Text("e.g. JT-12345-PK") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = returnReason,
            onValueChange = { returnReason = it },
            label = { Text("Reason for Return") },
            placeholder = { Text("e.g. Damaged item, wrong size") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = returnDescription,
            onValueChange = { returnDescription = it },
            label = { Text("Detailed Description") },
            placeholder = { Text("Explain the issue in detail...") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
          )
          Text(
            "Under JTBazar 7-day guarantee, our central team coordinates courier pickup and refund.",
            fontSize = 10.sp,
            color = JTTextSecondary
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (returnOrderId.isNotBlank() && returnDescription.isNotBlank()) {
              MarketplaceRepository.submitReturnRequest(returnOrderId.trim(), returnReason, returnDescription)
              showReturnDialog = false
              returnDescription = ""
              Toast.makeText(context, "Return requests require the delivered order item and supporting evidence. This app flow is not ready yet.", Toast.LENGTH_LONG).show()
            } else {
              Toast.makeText(context, "Please enter order number and description", Toast.LENGTH_SHORT).show()
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = JTRed)
        ) {
          Text("Submit Request")
        }
      },
      dismissButton = {
        TextButton(onClick = { showReturnDialog = false }) {
          Text("Cancel", color = JTTextSecondary)
        }
      }
    )
  }

  // ----------------------------------------------------
  // CUSTOMER SUPPORT / COMPLAINT DIALOG
  // ----------------------------------------------------
  if (showComplaintDialog) {
    AlertDialog(
      onDismissRequest = { showComplaintDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.SupportAgent, contentDescription = null, tint = JTPurplePrimary, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Customer Support & Complaints", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            "JTBazar Lahore Support Team operates 9 AM – 10 PM PKT.",
            fontSize = 11.sp,
            color = JTTextSecondary
          )
          OutlinedTextField(
            value = complaintSubject,
            onValueChange = { complaintSubject = it },
            label = { Text("Subject") },
            placeholder = { Text("e.g. Delivery delay / Order query") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = complaintMessage,
            onValueChange = { complaintMessage = it },
            label = { Text("Message / Complaint Details") },
            placeholder = { Text("Tell us how we can help you...") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (complaintMessage.isNotBlank()) {
              MarketplaceRepository.submitComplaint(activeOrder?.orderId, complaintSubject, complaintMessage)
              showComplaintDialog = false
              complaintSubject = ""
              complaintMessage = ""
              Toast.makeText(context, "Ticket submitted to JTBazar Support!", Toast.LENGTH_SHORT).show()
            } else {
              Toast.makeText(context, "Please enter your message", Toast.LENGTH_SHORT).show()
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = JTPurplePrimary)
        ) {
          Text("Send Ticket")
        }
      },
      dismissButton = {
        TextButton(onClick = { showComplaintDialog = false }) {
          Text("Cancel", color = JTTextSecondary)
        }
      }
    )
  }

  // ----------------------------------------------------
  // POLICY & STORE INFO DIALOG
  // ----------------------------------------------------
  if (showPolicyDialog) {
    AlertDialog(
      onDismissRequest = { showPolicyDialog = false },
      title = { Text(policyTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
      text = {
        Column(modifier = Modifier.heightIn(max = 300.dp)) {
          Text(
            text = policyContent,
            fontSize = 12.sp,
            color = JTTextSecondary,
            lineHeight = 17.sp
          )
        }
      },
      confirmButton = {
        Button(
          onClick = { showPolicyDialog = false },
          colors = ButtonDefaults.buttonColors(containerColor = JTPurplePrimary)
        ) {
          Text("Got It")
        }
      }
    )
  }

  // ----------------------------------------------------
  // DELIVERY ADDRESS SELECTION SHEET
  // ----------------------------------------------------
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
                text = userProfile?.fullName ?: if (isLoggedIn) "JTBazar Customer" else "Guest Customer",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = JTTextPrimary
              )
              Text(
                text = userProfile?.email ?: if (isLoggedIn) (currentSession?.user?.email ?: "Verified Account") else "Sign in to sync your cart & orders",
                fontSize = 12.sp,
                color = JTTextSecondary
              )
              Spacer(modifier = Modifier.height(4.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = if (isLoggedIn) JTPurpleContainer else JTAmberContainer
                ) {
                  Text(
                    text = if (isLoggedIn) "Supabase connected" else "Guest Account",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLoggedIn) JTPurplePrimary else JTAmberDark,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }
            }

            // Edit Profile / Sign In button
            IconButton(
              onClick = {
                if (isLoggedIn) {
                  showEditProfileDialog = true
                } else {
                  showAuthDialog = true
                }
              },
              modifier = Modifier.size(36.dp)
            ) {
              Icon(
                imageVector = if (isLoggedIn) Icons.Filled.Edit else Icons.Filled.Login,
                contentDescription = if (isLoggedIn) "Edit Profile" else "Sign In",
                tint = JTPurplePrimary,
                modifier = Modifier.size(20.dp)
              )
            }
          }
        }
      }

      // 2. Active Order Card (Rendered only when customer has an active order)
      if (activeOrder != null) {
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
                    text = "Order #${activeOrder!!.orderId}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = JTTextPrimary
                  )
                  Text(
                    text = "${activeOrder!!.statusHeader} • ${activeOrder!!.orderStatus}",
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
      }


      // 3. Main Menu Settings
      item {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = JTCardBg,
          border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(vertical = 4.dp)) {
            // Authentication Action
            AccountMenuItem(
              icon = if (isLoggedIn) Icons.Filled.Logout else Icons.Filled.AccountCircle,
              title = if (isLoggedIn) "Log Out (${userProfile?.email ?: "Active"})" else "Customer Sign In / Register",
              subtitle = if (isLoggedIn) "Sign out of your Supabase session" else "Sync orders, addresses & persistent cart",
              onClick = {
                if (isLoggedIn) {
                  coroutineScope.launch {
                    SupabaseAuthService.signOut(context)
                    Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                  }
                } else {
                  showAuthDialog = true
                }
              }
            )
            HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 14.dp))

            // Delivery Addresses
            AccountMenuItem(
              icon = Icons.Outlined.LocationOn,
              title = "Delivery Addresses",
              subtitle = selectedAddress?.let { "${it.area}, ${it.city}" } ?: "Add or manage delivery addresses",
              onClick = { showAddressDialog = true }
            )
            HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 14.dp))

            // Track Orders
            AccountMenuItem(
              icon = Icons.AutoMirrored.Outlined.ReceiptLong,
              title = "Track Orders & COD Status",
              subtitle = "Live tracking on JTBazar centralized logistics",
              onClick = { onViewOrderTracking() }
            )
            HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 14.dp))

            // Notifications
            AccountMenuItem(
              icon = Icons.Outlined.Notifications,
              title = "Notifications (${notifications.size})",
              subtitle = "Order updates, dispatch notices and savings deals",
              onClick = { showNotificationsDialog = true }
            )
            HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 14.dp))

            // My Reviews
            AccountMenuItem(
              icon = Icons.Outlined.RateReview,
              title = "My Product Reviews",
              subtitle = "Verified ratings and reviews submitted by you",
              onClick = { showReviewsDialog = true }
            )
            HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 14.dp))

            // Returns & Refunds
            AccountMenuItem(
              icon = Icons.Outlined.AssignmentReturn,
              title = "Return & Refund Requests",
              subtitle = "Return requests within 7 calendar days of delivery",
              onClick = { showReturnDialog = true }
            )
            HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 14.dp))

            // Customer Support
            AccountMenuItem(
              icon = Icons.Outlined.SupportAgent,
              title = "Customer Support & Complaints",
              subtitle = "Direct helpdesk ticket for order resolution",
              onClick = { showComplaintDialog = true }
            )
          }
        }
      }

      // 4. Store Policies & Info
      item {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = JTCardBg,
          border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(vertical = 4.dp)) {
            AccountMenuItem(
              icon = Icons.Outlined.Description,
              title = "Terms & Conditions",
              subtitle = "Marketplace buyer agreements & COD conditions",
              onClick = {
                policyTitle = "Terms & Conditions"
                policyContent = "Welcome to JTBazar. All orders placed through our mobile application are governed by Pakistani e-commerce laws. JTBazar coordinates centralized order delivery and handles payment collection exclusively via Cash on Delivery (COD) or verified bank channels. Customers are entitled to inspect exterior delivery packaging before payment."
                showPolicyDialog = true
              }
            )
            HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 14.dp))

            AccountMenuItem(
              icon = Icons.Outlined.Policy,
              title = "Privacy Policy",
              subtitle = "Data protection & zero third-party disclosure",
              onClick = {
                policyTitle = "Privacy Policy"
                policyContent = "JTBazar values your privacy. Customer information such as delivery address, contact numbers, and purchase history is securely stored on Supabase with Row Level Security (RLS). We never sell or distribute your personal data to unauthorized third parties."
                showPolicyDialog = true
              }
            )
            HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 14.dp))

            AccountMenuItem(
              icon = Icons.Outlined.LocalShipping,
              title = "Shipping & Delivery Policy",
              subtitle = "Central delivery across supported locations",
              onClick = {
                policyTitle = "Shipping & Delivery Policy"
                policyContent = "JTBazar manages centralized delivery. We collect products from participating vendors and deliver orders through the JTBazar delivery operation. Delivery fee and availability are confirmed at checkout. General return requests must be submitted within 7 calendar days of delivery, subject to the published return conditions."
                showPolicyDialog = true
              }
            )
            HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 14.dp))

            AccountMenuItem(
              icon = Icons.Outlined.AssignmentReturn,
              title = "Return & Refund Policy",
              subtitle = "Request within 7 calendar days of delivery",
              onClick = {
                policyTitle = "Return & Refund Policy"
                policyContent = "JTBazar accepts eligible general return requests within 7 calendar days of delivery. The request must relate to the original order and include the information and evidence required by the return process. Refund or replacement is subject to review under the published return conditions."
                showPolicyDialog = true
              }
            )
            HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 14.dp))

            AccountMenuItem(
              icon = Icons.Outlined.Info,
              title = "About JTBazar & Contact Us",
              subtitle = "JTBazar marketplace and customer support",
              onClick = {
                policyTitle = "About JTBazar"
                policyContent = "JTBazar is a centralized multi-vendor marketplace in Pakistan. Customers place orders through JTBazar, and the JTBazar team coordinates vendor collection and customer delivery. For support, use the contact details published on the JTBazar website."
                showPolicyDialog = true
              }
            )
          }
        }
      }

      // 5. Version info
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "JTBazar v1.0.0 (Production)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = JTTextSecondary
          )
          Text(
            text = "Connected to Supabase (xrrhbnnzjqkqnqdvayjw) • Lahore, Pakistan",
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
      .padding(horizontal = 14.dp, vertical = 11.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
