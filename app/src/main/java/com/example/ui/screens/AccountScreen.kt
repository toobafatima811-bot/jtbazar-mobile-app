package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
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
import com.example.localization.AppLanguage
import com.example.localization.AppLanguageManager
import com.example.localization.LocalAppLanguage
import com.example.localization.appText
import com.example.ui.components.JTBazarTopBar
import com.example.ui.components.UserAvatarView
import com.example.ui.theme.*
import kotlinx.coroutines.launch

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
  modifier: Modifier = Modifier,
  scrollState: ScrollState = rememberScrollState()
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val activeLanguage = LocalAppLanguage.current

  val currentSession by SupabaseClient.session.collectAsState()
  val currentUser by SupabaseClient.currentUser.collectAsState()
  val userProfile by SupabaseAuthService.userProfile.collectAsState()
  val cartCount by MarketplaceRepository.cartCount.collectAsState()
  val currentLocation by MarketplaceRepository.currentLocation.collectAsState()
  val notifications by MarketplaceRepository.notifications.collectAsState()
  var driverSystemEnabled by remember { mutableStateOf(false) }

  LaunchedEffect(currentUser?.id) {
    driverSystemEnabled = false
    if (currentUser?.id != null) {
      SupabaseClient.rpc("get_my_driver_access").onSuccess { raw ->
        driverSystemEnabled = raw.trim().equals("true", ignoreCase = true)
      }
    }
  }

  val isLoggedIn = currentSession != null
  var showLogoutConfirmDialog by remember { mutableStateOf(false) }

  val unreadNotifications = notifications.count { !it.isRead }

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
        title = appText("Account", "اکاؤنٹ"),
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
        .verticalScroll(scrollState)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      if (!isLoggedIn) {
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
                  text = appText("Welcome to JTBazar", "جے ٹی بازار میں خوش آمدید"),
                  fontWeight = FontWeight.Bold,
                  fontSize = 17.sp,
                  color = JTPurplePrimary
                )
                Text(
                  text = appText(
                    "Sign in to track orders, manage addresses & wishlist",
                    "آرڈرز ٹریک کرنے، پتے اور پسندیدہ فہرست سنبھالنے کے لیے سائن ان کریں"
                  ),
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
                Text(appText("Sign In", "سائن ان"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                Text(appText("Register", "رجسٹر کریں"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
            }
          }
        }
      } else {
        val displayName = userProfile?.fullName?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
          ?: currentUser?.fullName?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
          ?: appText("JTBazar Customer", "جے ٹی بازار صارف")
        val displayEmail = userProfile?.email?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
          ?: currentUser?.email?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
          ?: ""
        val displayPhone = userProfile?.phone?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
          ?: currentUser?.phone?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
          ?: ""

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
                  contentDescription = appText("Verified Customer", "تصدیق شدہ صارف"),
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
              contentDescription = appText("Edit Profile", "پروفائل ترمیم کریں"),
              tint = JTTextSecondary,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }

      // Quick Language Switcher directly accessible on Account screen
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = JTCardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, JTBorder),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(Icons.Filled.Language, contentDescription = null, tint = JTPurplePrimary, modifier = Modifier.size(20.dp))
            Column {
              Text(
                text = appText("Language / زبان", "زبان / Language"),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = JTTextPrimary
              )
              Text(
                text = appText("English or Urdu", "انگریزی یا اردو"),
                fontSize = 11.sp,
                color = JTTextSecondary
              )
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
              selected = activeLanguage == AppLanguage.ENGLISH,
              onClick = { AppLanguageManager.setLanguage(AppLanguage.ENGLISH, context) },
              label = { Text("English", fontSize = 11.sp) },
              modifier = Modifier.testTag("account_lang_en")
            )
            FilterChip(
              selected = activeLanguage == AppLanguage.URDU,
              onClick = { AppLanguageManager.setLanguage(AppLanguage.URDU, context) },
              label = { Text("اردو", fontSize = 11.sp) },
              modifier = Modifier.testTag("account_lang_ur")
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
            Text(
              appText("Budget Shopping", "کم بجٹ خریداری"),
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = Color(0xFF92400E)
            )
            Text(
              appText(
                "Find handcrafted items under Rs. 500, 1,000 & 2,000",
                "500، 1,000 اور 2,000 روپے سے کم میں معیاری اشیاء تلاش کریں"
              ),
              fontSize = 11.sp,
              color = Color(0xFFB45309)
            )
          }
          Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF92400E), modifier = Modifier.size(16.dp))
        }
      }

      Text(
        text = appText("Orders & Activity", "آرڈرز اور سرگرمیاں"),
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
            title = appText("My Orders", "میرے آرڈرز"),
            subtitle = appText("Track orders, dispatches & history", "آرڈرز، ترسیل اور تفصیل دیکھیں"),
            onClick = {
              if (isLoggedIn) onNavigateToOrders() else onNavigateToLogin()
            },
            tag = "menu_my_orders"
          )
          HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 16.dp))
          AccountMenuItem(
            icon = Icons.Filled.Favorite,
            title = appText("Wishlist", "پسندیدہ اشیاء"),
            subtitle = appText("Saved products & favorite vendor items", "محفوظ مصنوعات اور پسندیدہ اشیاء"),
            onClick = onNavigateToWishlist,
            tag = "menu_wishlist"
          )
          HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 16.dp))
          AccountMenuItem(
            icon = Icons.Filled.Notifications,
            title = appText("Notifications", "اطلاعات"),
            subtitle = if (unreadNotifications > 0) {
              appText("$unreadNotifications unread notification(s)", "$unreadNotifications غیر پڑھی اطلاع")
            } else {
              appText("Updates on orders & store promotions", "آرڈرز اور اسٹور آفرز کی تازہ معلومات")
            },
            badge = if (unreadNotifications > 0) "$unreadNotifications" else null,
            onClick = {
              if (isLoggedIn) onNavigateToNotifications() else onNavigateToLogin()
            },
            tag = "menu_notifications"
          )
          HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 16.dp))
          AccountMenuItem(
            icon = Icons.AutoMirrored.Filled.AssignmentReturn,
            title = appText("Returns & Refunds", "واپسی اور رقم کی واپسی"),
            subtitle = appText("Return requests & store credit status", "واپسی کی درخواستیں اور کریڈٹ کی صورتحال"),
            onClick = {
              if (isLoggedIn) onNavigateToReturns() else onNavigateToLogin()
            },
            tag = "menu_returns"
          )
        }
      }

      Text(
        text = appText("Account & Preferences", "اکاؤنٹ اور ترجیحات"),
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
            title = appText("My Profile", "میرا پروفائل"),
            subtitle = appText("Name, mobile number, and city details", "نام، موبائل نمبر اور شہر کی تفصیلات"),
            onClick = {
              if (isLoggedIn) onNavigateToProfile() else onNavigateToLogin()
            },
            tag = "menu_my_profile"
          )
          HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 16.dp))
          AccountMenuItem(
            icon = Icons.Filled.LocationOn,
            title = appText("My Addresses", "میرے پتے"),
            subtitle = appText("Manage home, work & saved locations", "گھر، دفتر اور محفوظ پتے منظم کریں"),
            onClick = {
              if (isLoggedIn) onNavigateToAddresses() else onNavigateToLogin()
            },
            tag = "menu_my_addresses"
          )
          HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 16.dp))
          AccountMenuItem(
            icon = Icons.Filled.Lock,
            title = appText("Security & Password", "سیکیورٹی اور پاس ورڈ"),
            subtitle = appText("Update password and secure access", "پاس ورڈ اور اکاؤنٹ سیکیورٹی اپ ڈیٹ کریں"),
            onClick = {
              if (isLoggedIn) onNavigateToSecurity() else onNavigateToLogin()
            },
            tag = "menu_security"
          )
          HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 16.dp))
          AccountMenuItem(
            icon = Icons.Filled.Settings,
            title = appText("Account Settings", "اکاؤنٹ کی ترتیبات"),
            subtitle = appText("Language, notifications & app info", "زبان، اطلاعات اور ایپ کی تفصیل"),
            onClick = onNavigateToSettings,
            tag = "menu_settings"
          )
          HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 16.dp))
          AccountMenuItem(
            icon = Icons.Filled.Storefront,
            title = appText("Vendor Center", "فروخت کنندہ مرکز"),
            subtitle = appText("Apply to sell or manage your shop", "فروخت کے لیے درخواست دیں یا دکان سنبھالیں"),
            onClick = { if (isLoggedIn) onNavigateToVendorCenter() else onNavigateToLogin() },
            tag = "menu_vendor_center"
          )
          HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 16.dp))
          if (driverSystemEnabled) {
            AccountMenuItem(
              icon = Icons.Filled.LocalShipping,
              title = appText("Driver Center", "ڈرائیور مرکز"),
              subtitle = appText("Apply to deliver or manage assigned deliveries", "ترسیل کے لیے درخواست دیں یا تفویض آرڈرز سنبھالیں"),
              onClick = { if (isLoggedIn) onNavigateToDriver() else onNavigateToLogin() },
              tag = "menu_driver_center"
            )
            HorizontalDivider(color = JTBorder, modifier = Modifier.padding(horizontal = 16.dp))
          }
          AccountMenuItem(
            icon = Icons.Filled.HeadsetMic,
            title = appText("Customer Support & Complaints", "کسٹمر سپورٹ اور شکایات"),
            subtitle = appText("File an inquiry or message live support", "سوال پوچھیں یا لائیو سپورٹ کو پیغام بھیجیں"),
            onClick = onNavigateToSupport,
            tag = "menu_support"
          )
        }
      }

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
              text = appText("Log Out of JTBazar", "جے ٹی بازار سے لاگ آؤٹ کریں"),
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
        Text(
          appText("Log Out of JTBazar?", "جے ٹی بازار سے لاگ آؤٹ کریں؟"),
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp
        )
      },
      text = {
        Text(
          appText(
            "Are you sure you want to log out of your account? Your cart and saved wishlist will remain secure for your next visit.",
            "کیا آپ واقعی اپنے اکاؤنٹ سے لاگ آؤٹ کرنا چاہتے ہیں؟ آپ کا کارٹ اور محفوظ پسندیدہ فہرست محفوظ رہے گی۔"
          ),
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
              Toast.makeText(
                context,
                AppLanguageManager.tr("Logged out successfully", "کامیابی سے لاگ آؤٹ ہو گیا"),
                Toast.LENGTH_SHORT
              ).show()
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White),
          modifier = Modifier.testTag("confirm_logout_button")
        ) {
          Text(appText("Log Out", "لاگ آؤٹ"), fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showLogoutConfirmDialog = false },
          modifier = Modifier.testTag("cancel_logout_button")
        ) {
          Text(appText("Cancel", "منسوخ کریں"), fontWeight = FontWeight.SemiBold)
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
