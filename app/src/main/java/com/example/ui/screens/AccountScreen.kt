package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.MarketplaceRepository
import com.example.data.supabase.SupabaseAuthService
import com.example.data.supabase.SupabaseClient
import com.example.ui.components.JTBazarTopBar
import com.example.ui.components.UserAvatarView
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray

@Composable
fun AccountScreen(
  onNavigateToLogin: () -> Unit,
  onNavigateToRegister: () -> Unit,
  onNavigateToProfile: () -> Unit,
  onNavigateToOrders: () -> Unit,
  onNavigateToAddresses: () -> Unit,
  onNavigateToWishlist: () -> Unit,
  onNavigateToNotifications: () -> Unit,
  onNavigateToReturns: () -> Unit,
  onNavigateToSupport: () -> Unit,
  onNavigateToSettings: () -> Unit,
  onNavigateToSecurity: () -> Unit,
  onNavigateToBudget: () -> Unit,
  onNavigateToVendorCenter: () -> Unit,
  onNavigateToDriver: () -> Unit,
  onCartClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val currentSession by SupabaseClient.session.collectAsState()
  val currentUser by SupabaseClient.currentUser.collectAsState()
  val userProfile by SupabaseAuthService.userProfile.collectAsState()
  val cartCount by MarketplaceRepository.cartCount.collectAsState()
  val currentLocation by MarketplaceRepository.currentLocation.collectAsState()
  val notifications by MarketplaceRepository.notifications.collectAsState()
  var driverSystemEnabled by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    val result = SupabaseClient.get("store_settings?select=driver_system_enabled&limit=1")
    result.onSuccess { raw ->
      val rows = JSONArray(raw)
      driverSystemEnabled = rows.length() > 0 && rows.getJSONObject(0).optBoolean("driver_system_enabled", false)
    }
  }

  val isLoggedIn = currentSession != null
  var showLogoutConfirmDialog by remember { mutableStateOf(false) }

  val unreadNotifications = notifications.count { !it.isRead }

  // Refresh the persisted profile whenever the signed-in account screen is opened.
  LaunchedEffect(currentUser?.id) {
    val userId = currentUser?.id
    if (!userId.isNullOrBlank()) {
      SupabaseAuthService.fetchProfile(userId)
      MarketplaceRepository.refreshNotifications(userId)
    }
  }

  Scaffold(
    containerColor = Color.White,
    topBar = {
      JTBazarTopBar(
        title = "Account",
        onCartClick = onCartClick,
        cartCount = cartCount,
        currentLocation = currentLocation
      )
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      if (!isLoggedIn) {
        // Unauthenticated Welcome Card
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = JTPurpleContainer,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              UserAvatarView(
                avatarUrl = null,
                fullName = null,
                size = 54.dp
              )
              Spacer(modifier = Modifier.width(14.dp))
              Column {
                Text(
                  text = "Welcome to JTBazar",
                  fontWeight = FontWeight.Bold,
                  fontSize = 17.sp,
                  color = JTPurplePrimary
                )
                Text(
                  text = "Sign in to track orders, manage addresses & wishlist",
                  fontSize = 12.sp,
                  color = JTTextSecondary,
                  lineHeight = 16.sp
                )
              }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Button(
                onClick = onNavigateToLogin,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White),
                modifier = Modifier
                  .weight(1f)
                  .height(44.dp)
                  .testTag("account_signin_button")
              ) {
                Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }

              OutlinedButton(
                onClick = onNavigateToRegister,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, JTOrange),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = JTOrange),
                modifier = Modifier
                  .weight(1f)
                  .height(44.dp)
                  .testTag("account_register_button")
              ) {
                Text("Register", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
            }
          }
        }
      } else {
        // Authenticated Customer Profile Card
        val displayName = userProfile?.fullName?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: currentUser?.fullName?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: "JTBazar Customer"
        val displayEmail = userProfile?.email?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: currentUser?.email?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: ""
        val displayPhone = userProfile?.phone?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: currentUser?.phone?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: ""

        Surface(
          shape = RoundedCornerShape(16.dp),
          color = JTCardBg,
          border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
          modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigateToProfile)
            .testTag("account_header_profile")
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Actual user uploaded profile photo or initial badge (NO default stock person photo)
            UserAvatarView(
              avatarUrl = userProfile?.avatarUrl,
              fullName = displayName,
              size = 60.dp,
              modifier = Modifier.testTag("account_avatar_badge")
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = displayName,
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold,
                  color = JTTextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                  imageVector = Icons.Filled.Verified,
                  contentDescription = "Verified Customer",
                  tint = JTPurplePrimary,
                  modifier = Modifier.size(16.dp)
                )
              }

              if (displayEmail.isNotBlank()) {
                Text(
                  text = displayEmail,
                  fontSize = 12.sp,
                  color = JTTextSecondary,
                  maxLines = 1
                )
              }

              if (displayPhone.isNotBlank()) {
                Text(
                  text = displayPhone,
                  fontSize = 12.sp,
                  color = JTTextSecondary,
                  maxLines = 1
                )
              }
            }

            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = "Edit Profile",
              tint = JTTextSecondary,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }

      // Quick Hub Banner: Budget Shopping
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFEF3C7),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
        modifier = Modifier
          .fillMaxWidth()
          .clickable(onClick = onNavigateToBudget)
          .testTag("account_budget_hub")
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = JTAmberDark, modifier = Modifier.size(24.dp))
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text("Budget Shopping", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF92400E))
            Text("Find handcrafted items under Rs. 500, 1,000 & 2,000", fontSize = 11.sp, color = Color(0xFFB45309))
          }
          Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF92400E), modifier = Modifier.size(16.dp))
        }
      }

      // Main Menu Sections
      Text(
        text = "Orders & Activity",
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = JTTextSecondary
      )

      Surface(
        shape = RoundedCornerShape(14.dp),
        color = JTCardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column {
          AccountMenuItem(
            icon = Icons.Filled.ShoppingBag,
            title = "My Orders",
            subtitle = "Track orders, dispatches & history",
            onClick = {
              if (isLoggedIn) onNavigateToOrders() else onNavigateToLogin()
            },
            tag = "menu_my_orders"
          )
          HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 16.dp))
          AccountMenuItem(
            icon = Icons.Filled.Favorite,
            title = "Wishlist",
            subtitle = "Saved products & favorite vendor items",
            onClick = onNavigateToWishlist,
            tag = "menu_wishlist"
          )
          HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 16.dp))
          AccountMenuItem(
            icon = Icons.Filled.Notifications,
            title = "Notifications",
            subtitle = if (unreadNotifications > 0) "$unreadNotifications unread notification(s)" else "Updates on orders & store promotions",
            badge = if (unreadNotifications > 0) "$unreadNotifications" else null,
            onClick = {
              if (isLoggedIn) onNavigateToNotifications() else onNavigateToLogin()
            },
            tag = "menu_notifications"
          )
          HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 16.dp))
          AccountMenuItem(
            icon = Icons.AutoMirrored.Filled.AssignmentReturn,
            title = "Returns & Refunds",
            subtitle = "Return requests & store credit status",
            onClick = {
              if (isLoggedIn) onNavigateToReturns() else onNavigateToLogin()
            },
            tag = "menu_returns"
          )
        }
      }

      Text(
        text = "Account & Preferences",
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = JTTextSecondary
      )

      Surface(
        shape = RoundedCornerShape(14.dp),
        color = JTCardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column {
          AccountMenuItem(
            icon = Icons.Filled.Person,
            title = "My Profile",
            subtitle = "Name, mobile number, and city details",
            onClick = {
              if (isLoggedIn) onNavigateToProfile() else onNavigateToLogin()
            },
            tag = "menu_my_profile"
          )
          HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 16.dp))
          AccountMenuItem(
            icon = Icons.Filled.LocationOn,
            title = "My Addresses",
            subtitle = "Manage home, work & saved locations",
            onClick = {
              if (isLoggedIn) onNavigateToAddresses() else onNavigateToLogin()
            },
            tag = "menu_my_addresses"
          )
          HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 16.dp))
          AccountMenuItem(
            icon = Icons.Filled.Lock,
            title = "Security & Password",
            subtitle = "Update password and secure access",
            onClick = {
              if (isLoggedIn) onNavigateToSecurity() else onNavigateToLogin()
            },
            tag = "menu_security"
          )
          HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 16.dp))
          AccountMenuItem(
            icon = Icons.Filled.Settings,
            title = "Account Settings",
            subtitle = "App settings, notifications & version",
            onClick = onNavigateToSettings,
            tag = "menu_settings"
          )
          HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 16.dp))
          AccountMenuItem(
            icon = Icons.Filled.Storefront,
            title = "Become a Vendor / Vendor Center",
            subtitle = "Apply to sell or manage your approved shop",
            onClick = { if (isLoggedIn) onNavigateToVendorCenter() else onNavigateToLogin() },
            tag = "menu_vendor_center"
          )
          HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 16.dp))
          if (driverSystemEnabled) {
            AccountMenuItem(
              icon = Icons.Filled.LocalShipping,
              title = "Become a Driver / Driver Center",
              subtitle = "Apply to deliver and manage assigned orders",
              onClick = { if (isLoggedIn) onNavigateToDriver() else onNavigateToLogin() },
              tag = "menu_driver_center"
            )
            HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 16.dp))
  
          }          AccountMenuItem(
            icon = Icons.Filled.HeadsetMic,
            title = "Customer Support & Complaints",
            subtitle = "File an inquiry or message live support",
            onClick = onNavigateToSupport,
            tag = "menu_support"
          )
        }
      }

      // Logout Button (Only if authenticated)
      if (isLoggedIn) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = Color(0xFFFEF2F2),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { showLogoutConfirmDialog = true }
            .testTag("account_logout_button")
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Logout,
              contentDescription = null,
              tint = Color(0xFFDC2626),
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = "Log Out of JTBazar",
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
              color = Color(0xFFDC2626)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }

  // Logout Confirmation Dialog (Requirement 5)
  if (showLogoutConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showLogoutConfirmDialog = false },
      icon = {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.Logout,
          contentDescription = null,
          tint = Color(0xFFDC2626),
          modifier = Modifier.size(28.dp)
        )
      },
      title = {
        Text("Log Out of JTBazar?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
      },
      text = {
        Text(
          "Are you sure you want to log out of your account? Your cart and saved wishlist will remain secure for your next visit.",
          fontSize = 13.sp,
          color = JTTextSecondary,
          lineHeight = 17.sp
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showLogoutConfirmDialog = false
            coroutineScope.launch {
              SupabaseAuthService.signOut(context)
              MarketplaceRepository.clearUserData()
              Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White),
          modifier = Modifier.testTag("confirm_logout_button")
        ) {
          Text("Log Out", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showLogoutConfirmDialog = false },
          modifier = Modifier.testTag("cancel_logout_button")
        ) {
          Text("Cancel", fontWeight = FontWeight.SemiBold)
        }
      }
    )
  }
}

@Composable
private fun AccountMenuItem(
  icon: ImageVector,
  title: String,
  subtitle: String,
  badge: String? = null,
  onClick: () -> Unit,
  tag: String
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 14.dp)
      .testTag(tag),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .background(JTSurfaceVariant, RoundedCornerShape(10.dp)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = JTPurplePrimary,
        modifier = Modifier.size(20.dp)
      )
    }

    Spacer(modifier = Modifier.width(14.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = JTTextPrimary
      )
      Text(
        text = subtitle,
        fontSize = 11.sp,
        color = JTTextSecondary,
        maxLines = 1
      )
    }

    if (badge != null) {
      Surface(
        shape = CircleShape,
        color = JTOrange,
        modifier = Modifier.padding(end = 8.dp)
      ) {
        Text(
          text = badge,
          color = Color.White,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
      }
    }

    Icon(
      imageVector = Icons.AutoMirrored.Filled.ArrowForward,
      contentDescription = null,
      tint = JTTextSecondary.copy(alpha = 0.6f),
      modifier = Modifier.size(16.dp)
    )
  }
}
