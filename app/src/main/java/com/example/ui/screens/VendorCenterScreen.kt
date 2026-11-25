package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.example.data.repository.MarketplaceRepository
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseRealtimeClient
import com.example.data.supabase.VendorApplication
import com.example.data.supabase.VendorBusinessType
import com.example.data.supabase.VendorOrderStatus
import com.example.data.supabase.VendorService
import com.example.localization.AppLanguageManager
import com.example.localization.appText
import com.example.ui.components.AppCard
import com.example.ui.components.ErrorState
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.components.StatusTone
import com.example.ui.components.VendorAlertBanner
import com.example.ui.components.VendorCnicPrivacyNotice
import com.example.ui.components.VendorOutlinedTextField
import com.example.ui.components.VendorSkeletonCard
import com.example.ui.components.VendorStatGridSkeleton
import com.example.ui.theme.VendorCenterTheme
import com.example.ui.theme.VendorColors
import com.example.ui.theme.VendorShapes
import com.example.ui.theme.VendorSpacing
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorCenterScreen(
  onBack: () -> Unit,
  onOpenNotifications: () -> Unit = {},
  initialTab: VendorBottomTab? = null,
  initialOrderId: String? = null
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val user by SupabaseClient.currentUser.collectAsState()
  var application by remember { mutableStateOf<VendorApplication?>(null) }
  var isVendor by remember { mutableStateOf(false) }
  var products by remember { mutableStateOf(JSONArray()) }
  var productImageUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
  var productDeletionRequests by remember { mutableStateOf(JSONArray()) }
  var deletionTarget by remember { mutableStateOf<JSONObject?>(null) }
  var deletionReason by remember { mutableStateOf("") }
  var allOrdersRaw by remember { mutableStateOf(JSONArray()) }
  var ordersSearch by remember { mutableStateOf("") }
  var ordersStatus by remember { mutableStateOf("all") }
  var ordersPage by remember { mutableIntStateOf(0) }
  var ordersLoading by remember { mutableStateOf(false) }
  var loading by remember { mutableStateOf(true) }
  var hasLoadedData by remember { mutableStateOf(false) }
  var saving by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }
  var actionErrorMessage by remember { mutableStateOf("") }
  var info by remember { mutableStateOf("") }
  var shopName by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf(user?.phone.orEmpty()) }
  var ownerName by remember { mutableStateOf(user?.fullName.orEmpty()) }
  var cnic by remember { mutableStateOf("") }
  var cnicPrivacyAcknowledged by remember(user?.id) { mutableStateOf(false) }
  var vendorType by remember { mutableStateOf("individual_seller") }
  var businessType by remember { mutableStateOf("") }
  var currentAddress by remember { mutableStateOf("") }
  var fullAddress by remember { mutableStateOf("") }
  var shopLocation by remember { mutableStateOf("") }
  var latitude by remember { mutableStateOf("") }
  var longitude by remember { mutableStateOf("") }
  var showMapPicker by remember { mutableStateOf(false) }
  var showVendorGuide by remember { mutableStateOf(false) }
  var verificationPath by remember { mutableStateOf<String?>(null) }
  var frontPath by remember { mutableStateOf<String?>(null) }
  var backPath by remember { mutableStateOf<String?>(null) }
  var shopImageUri by remember { mutableStateOf<Uri?>(null) }
  var editingProduct by remember { mutableStateOf<JSONObject?>(null) }
  var viewingProduct by remember { mutableStateOf<JSONObject?>(null) }
  var selectedOrderId by remember { mutableStateOf(initialOrderId) }

  var selectedTab by remember { mutableStateOf(initialTab ?: VendorBottomTab.OVERVIEW) }
  var moreSection by remember { mutableStateOf(VendorMoreSection.MENU) }
  var activeSubScreen by remember {
    mutableStateOf<String?>(if (!initialOrderId.isNullOrBlank()) "order_details" else null)
  } // "product_add", "product_edit", "product_details", "order_details"

  val isApprovedVendor = isVendor && application?.status == "approved" && application?.vendorLocked != true
  val notifications by MarketplaceRepository.notifications.collectAsState()
  val unreadNotificationsCount = remember(notifications) { notifications.count { !it.isRead } }
  val ordersSyncMutex = remember { Mutex() }
  val lifecycleOwner = LocalLifecycleOwner.current

  suspend fun refreshOrdersInternal(showLoader: Boolean, surfaceErrors: Boolean = showLoader) {
    ordersSyncMutex.withLock {
      if (showLoader) {
        ordersLoading = true
        actionErrorMessage = ""
      }
      try {
        allOrdersRaw = VendorService.listAllOrderItemsReconciled()
        MarketplaceRepository.refreshNotifications(user?.id)
      } catch (e: Exception) {
        if (surfaceErrors) {
          actionErrorMessage = vendorLoadErrorMessage(e)
        }
      } finally {
        if (showLoader) {
          ordersLoading = false
        }
      }
    }
  }

  fun refresh() {
    scope.launch {
      loading = true
      errorMessage = ""
      try {
        application = VendorService.getOwnApplication()
        isVendor = VendorService.isVendor()
        if (isVendor && application?.status == "approved") {
          products = VendorService.listProducts()
          productDeletionRequests = VendorService.listProductDeletionRequests()
          refreshOrdersInternal(showLoader = false, surfaceErrors = false)
        }
        MarketplaceRepository.refreshNotifications(user?.id)
        hasLoadedData = true
        actionErrorMessage = ""
      } catch (e: Exception) {
        val message = vendorLoadErrorMessage(e)
        if (hasLoadedData) actionErrorMessage = message else errorMessage = message
      } finally {
        loading = false
      }
    }
  }

  LaunchedEffect(user?.id) { refresh() }

  LaunchedEffect(initialTab, initialOrderId) {
    if (initialTab != null) {
      selectedTab = initialTab
    }
    if (!initialOrderId.isNullOrBlank()) {
      selectedOrderId = initialOrderId
      selectedTab = VendorBottomTab.ORDERS
      activeSubScreen = "order_details"
    }
  }

  LaunchedEffect(selectedTab, activeSubScreen, isApprovedVendor) {
    if (isApprovedVendor && hasLoadedData &&
      (selectedTab == VendorBottomTab.OVERVIEW || selectedTab == VendorBottomTab.ORDERS || activeSubScreen == "order_details")
    ) {
      refreshOrdersInternal(showLoader = false, surfaceErrors = false)
    }
  }

  DisposableEffect(lifecycleOwner, isApprovedVendor, user?.id) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME && isApprovedVendor) {
        scope.launch {
          refreshOrdersInternal(showLoader = false, surfaceErrors = false)
        }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  LaunchedEffect(lifecycleOwner, isApprovedVendor, user?.id) {
    val uid = user?.id
    if (!isApprovedVendor || uid.isNullOrBlank()) return@LaunchedEffect
    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
      val realtime = SupabaseRealtimeClient(
        subscribeToCatalogTables = false,
        subscribeToVendorOrderTables = true,
        subscribeToNotificationTables = true,
        onOrdersChanged = {
          scope.launch {
            refreshOrdersInternal(showLoader = false, surfaceErrors = false)
          }
        },
        onNotificationsChanged = {
          MarketplaceRepository.refreshNotifications(uid)
        }
      )
      realtime.start()
      try {
        while (isActive) {
          delay(15_000L)
          refreshOrdersInternal(showLoader = false, surfaceErrors = false)
        }
      } finally {
        realtime.stop()
      }
    }
  }

  // Pre-fetch product thumbnail URLs whenever the products array updates
  LaunchedEffect(products) {
    val ids = (0 until products.length())
      .mapNotNull { products.optJSONObject(it)?.optString("id")?.takeIf { id -> id.isNotBlank() } }
      .distinct()
    if (ids.isEmpty()) {
      productImageUrls = emptyMap()
      return@LaunchedEffect
    }
    val loaded = coroutineScope {
      ids.map { id ->
        async {
          val url = runCatching { VendorService.listProductImageUrls(id).firstOrNull().orEmpty() }
            .getOrDefault("")
          id to url
        }
      }.awaitAll().toMap()
    }
    productImageUrls = loaded
  }

  suspend fun upload(uri: Uri, side: String): String {
    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
      ?: throw IllegalStateException(AppLanguageManager.tr("Selected file could not be read.", "منتخب کردہ فائل پڑھی نہیں جا سکی۔"))
    try {
      require(bytes.size <= 10 * 1024 * 1024) {
        AppLanguageManager.tr("Each verification file must be 10 MB or smaller.", "ہر تصدیقی فائل 10 ایم بی یا اس سے کم ہونی چاہیے۔")
      }
      require(mime in listOf("application/pdf", "image/jpeg", "image/png", "image/webp")) {
        AppLanguageManager.tr("Use PDF, JPG, PNG or WebP files.", "صرف PDF، JPG، PNG یا WebP فائلیں استعمال کریں۔")
      }
      val ext = when (mime) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "pdf"
      }
      val path = "${user?.id}/$side-${System.currentTimeMillis()}.$ext"
      return VendorService.uploadPrivateDocument(path, bytes, mime)
    } finally {
      bytes.fill(0)
    }
  }

  val shopImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    shopImageUri = uri
  }

  var pendingDoc by remember { mutableStateOf("verification") }
  val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri != null) scope.launch {
      saving = true
      actionErrorMessage = ""
      info = ""
      try {
        val path = upload(uri, pendingDoc)
        when (pendingDoc) {
          "verification" -> verificationPath = path
          "cnic-front" -> frontPath = path
          "cnic-back" -> backPath = path
        }
        info = AppLanguageManager.tr("Document uploaded securely.", "دستاویز محفوظ طریقے سے اپ لوڈ ہو گئی۔")
      } catch (e: Exception) {
        actionErrorMessage = e.message ?: AppLanguageManager.tr("Document upload failed.", "دستاویز اپ لوڈ ناکام ہو گئی۔")
      } finally {
        saving = false
      }
    }
  }

  val handleBackNavigation: () -> Unit = {
    when {
      activeSubScreen == "product_edit" -> {
        editingProduct = null
        activeSubScreen = if (viewingProduct != null) "product_details" else null
      }
      activeSubScreen == "product_details" -> {
        viewingProduct = null
        activeSubScreen = null
      }
      activeSubScreen == "product_add" -> {
        activeSubScreen = null
      }
      activeSubScreen == "order_details" -> {
        selectedOrderId = null
        activeSubScreen = null
      }
      isApprovedVendor && selectedTab == VendorBottomTab.MORE && moreSection != VendorMoreSection.MENU -> {
        moreSection = VendorMoreSection.MENU
      }
      else -> onBack()
    }
  }

  val canHandleSubBack = activeSubScreen != null ||
    (isApprovedVendor && selectedTab == VendorBottomTab.MORE && moreSection != VendorMoreSection.MENU)

  BackHandler(enabled = canHandleSubBack) {
    handleBackNavigation()
  }

  val productUiModels = remember(products, productDeletionRequests, productImageUrls) {
    mapProductsToUiModels(products, productDeletionRequests, productImageUrls)
  }
  val allOrderGroups = remember(allOrdersRaw) { mapOrdersToUiGroups(allOrdersRaw) }
  val ordersSummary = remember(allOrderGroups, ordersSearch, ordersStatus, ordersPage) {
    filterAndPaginateVendorOrders(
      allGroups = allOrderGroups,
      searchQuery = ordersSearch,
      selectedStatus = ordersStatus,
      page = ordersPage,
      pageSize = 12
    )
  }
  val selectedOrderGroup = remember(allOrderGroups, selectedOrderId) {
    val target = selectedOrderId ?: return@remember null
    allOrderGroups.firstOrNull { it.orderId == target }
  }

  val topBarTitle = when {
    activeSubScreen == "product_add" -> appText("Add listing", "نئی پروڈکٹ شامل کریں")
    activeSubScreen == "product_edit" -> appText("Edit product", "پروڈکٹ میں ترمیم")
    activeSubScreen == "product_details" -> appText("Product details", "پروڈکٹ کی تفصیلات")
    activeSubScreen == "order_details" -> appText("Order details", "آرڈر کی تفصیلات")
    isApprovedVendor && selectedTab == VendorBottomTab.MORE -> when (moreSection) {
      VendorMoreSection.CASES_SUPPORT -> appText("Cases & Support", "کیسز اور سپورٹ")
      VendorMoreSection.SHOP_SETTINGS -> appText("Shop Settings", "دکان کی ترتیبات")
      VendorMoreSection.ADVANCED_TOOLS -> appText("More Tools", "مزید ٹولز")
      VendorMoreSection.MENU -> appText("Vendor Center", "وینڈر سینٹر")
    }
    else -> appText("Vendor Center", "وینڈر سینٹر")
  }

  val topBarSubtitle = when {
    activeSubScreen == "order_details" && selectedOrderGroup != null ->
      appText("Order ${selectedOrderGroup.orderNumber}", "آرڈر ${selectedOrderGroup.orderNumber}")
    activeSubScreen != null -> application?.shopName?.takeIf { it.isNotBlank() }
    isApprovedVendor -> application?.shopName?.takeIf { it.isNotBlank() } ?: appText("Your shop, products & pickup", "آپ کی دکان، مصنوعات اور پک اپ")
    else -> appText("Your shop, products & pickup preparation", "آپ کی دکان، مصنوعات اور پک اپ کی تیاری")
  }

  VendorCenterTheme {
    Scaffold(
      containerColor = VendorColors.Background,
      topBar = {
        Column(modifier = Modifier.fillMaxWidth()) {
          TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
              containerColor = VendorColors.CardWhite,
              titleContentColor = VendorColors.TextPrimary,
              navigationIconContentColor = VendorColors.TextPrimary,
              actionIconContentColor = VendorColors.TextPrimary
            ),
            navigationIcon = {
              IconButton(
                onClick = handleBackNavigation,
                modifier = Modifier
                  .size(VendorSpacing.touchMin)
                  .testTag("vendor_top_back_button")
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                  contentDescription = appText("Back", "واپس")
                )
              }
            },
            title = {
              Column {
                Text(
                  text = topBarTitle,
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                if (!topBarSubtitle.isNullOrBlank()) {
                  Text(
                    text = topBarSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = VendorColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            },
            actions = {
              if (user != null && activeSubScreen == null) {
                IconButton(
                  onClick = onOpenNotifications,
                  modifier = Modifier
                    .size(VendorSpacing.touchMin)
                    .testTag("vendor_top_notifications_button")
                ) {
                  BadgedBox(
                    badge = {
                      if (unreadNotificationsCount > 0) {
                        Badge(
                          containerColor = VendorColors.DangerRed,
                          contentColor = Color.White,
                          modifier = Modifier.testTag("vendor_top_notifications_badge")
                        ) {
                          Text(
                            text = unreadNotificationsCount.coerceAtMost(99).toString(),
                            style = MaterialTheme.typography.labelSmall
                          )
                        }
                      }
                    }
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Notifications,
                      contentDescription = appText("Notifications", "نوٹیفیکیشنز"),
                      tint = VendorColors.TextPrimary
                    )
                  }
                }
              }
              if (activeSubScreen == null) {
                IconButton(
                  onClick = { refresh() },
                  enabled = !loading,
                  modifier = Modifier
                    .size(VendorSpacing.touchMin)
                    .testTag("vendor_top_refresh_button")
                ) {
                  if (loading && hasLoadedData) {
                    CircularProgressIndicator(
                      modifier = Modifier.size(20.dp),
                      color = VendorColors.PrimaryOrange,
                      strokeWidth = 2.dp
                    )
                  } else {
                    Icon(
                      imageVector = Icons.Rounded.Refresh,
                      contentDescription = appText("Refresh Vendor Center", "وینڈر سینٹر ریفریش کریں"),
                      tint = VendorColors.PrimaryOrange
                    )
                  }
                }
              }
            }
          )
          HorizontalDivider(color = VendorColors.BorderSubtle)
        }
      },
      bottomBar = {
        if (isApprovedVendor && activeSubScreen == null) {
          Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = VendorColors.BorderSubtle)
            NavigationBar(
              containerColor = VendorColors.CardWhite,
              tonalElevation = 0.dp
            ) {
              VendorBottomTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                val icon: ImageVector = when (tab) {
                  VendorBottomTab.OVERVIEW -> Icons.Rounded.Home
                  VendorBottomTab.PRODUCTS -> Icons.Rounded.ShoppingBag
                  VendorBottomTab.INVENTORY -> Icons.Rounded.Inventory2
                  VendorBottomTab.ORDERS -> Icons.Rounded.LocalShipping
                  VendorBottomTab.MORE -> Icons.Rounded.MoreHoriz
                }
                NavigationBarItem(
                  selected = isSelected,
                  onClick = {
                    if (tab == VendorBottomTab.MORE && selectedTab == VendorBottomTab.MORE) {
                      moreSection = VendorMoreSection.MENU
                    } else {
                      selectedTab = tab
                      if (tab != VendorBottomTab.MORE) {
                        moreSection = VendorMoreSection.MENU
                      }
                    }
                  },
                  icon = {
                    val ordersBadgeCount = if (tab == VendorBottomTab.ORDERS) ordersSummary.itemsToPrepareCount else 0
                    val showMoreBadge = tab == VendorBottomTab.MORE && unreadNotificationsCount > 0
                    if (ordersBadgeCount > 0 || showMoreBadge) {
                      BadgedBox(
                        badge = {
                          if (ordersBadgeCount > 0) {
                            Badge(
                              containerColor = VendorColors.PrimaryOrange,
                              contentColor = Color.White,
                              modifier = Modifier.testTag("vendor_orders_nav_badge")
                            ) {
                              Text(
                                text = ordersBadgeCount.coerceAtMost(99).toString(),
                                style = MaterialTheme.typography.labelSmall
                              )
                            }
                          } else if (showMoreBadge) {
                            Badge(
                              containerColor = VendorColors.DangerRed,
                              modifier = Modifier.testTag("vendor_more_nav_badge")
                            )
                          }
                        }
                      ) {
                        Icon(
                          imageVector = icon,
                          contentDescription = tab.localizedLabel(),
                          modifier = Modifier.size(24.dp)
                        )
                      }
                    } else {
                      Icon(
                        imageVector = icon,
                        contentDescription = tab.localizedLabel(),
                        modifier = Modifier.size(24.dp)
                      )
                    }
                  },
                  label = {
                    Text(
                      text = tab.localizedLabel(),
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                      maxLines = 1,
                      softWrap = false,
                      overflow = TextOverflow.Ellipsis
                    )
                  },
                  colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = VendorColors.PrimaryOrange,
                    selectedTextColor = VendorColors.PrimaryOrange,
                    indicatorColor = VendorColors.PrimaryOrangeTint,
                    unselectedIconColor = VendorColors.TextSecondary,
                    unselectedTextColor = VendorColors.TextSecondary
                  ),
                  modifier = Modifier.testTag("vendor_nav_${tab.routeKey}")
                )
              }
            }
          }
        }
      }
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .background(VendorColors.Background)
      ) {
        when {
          loading && !hasLoadedData -> {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(VendorSpacing.md),
              verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
            ) {
              VendorStatGridSkeleton()
            }
          }

          errorMessage.isNotBlank() && !hasLoadedData -> {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(VendorSpacing.md),
              verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
            ) {
              ErrorState(
                title = appText("Connection problem", "کنکشن کا مسئلہ"),
                message = "$errorMessage\n\n" + appText(
                  "No application or shop data was submitted. Check your internet connection and try again.",
                  "کوئی درخواست یا دکان کا ڈیٹا جمع نہیں ہوا۔ اپنا انٹرنیٹ کنکشن چیک کریں اور دوبارہ کوشش کریں۔"
                ),
                retryLabel = appText("Retry connection", "دوبارہ کوشش کریں"),
                onRetry = { refresh() }
              )
            }
          }

          application?.vendorLocked == true -> {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(VendorSpacing.md),
              verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
            ) {
              SectionHeader(
                title = appText("Vendor account restricted", "فروش اکاؤنٹ محدود ہے"),
                subtitle = appText("Seller access is currently paused for this account.", "اس اکاؤنٹ کے لیے فروش تک رسائی فی الحال معطل ہے۔")
              )
              StatusPanel(application!!)
              VendorAlertBanner(
                message = application?.lockReason
                  ?: appText("Vendor tools are temporarily unavailable. Contact JTBazar support for details.", "فروش ٹولز عارضی طور پر دستیاب نہیں ہیں۔ تفصیلات کے لیے جے ٹی بازار سپورٹ سے رابطہ کریں۔"),
                isError = true
              )
              SecondaryButton(
                text = appText("Refresh status", "حیثیت ریفریش کریں"),
                icon = Icons.Rounded.Refresh,
                onClick = { refresh() },
                modifier = Modifier.fillMaxWidth()
              )
            }
          }

          isApprovedVendor -> {
            when (activeSubScreen) {
              "product_add" -> VendorListingEditor(
                allowDirectPublish = application?.allowProductPublishingWithoutReview == true,
                onCancel = { activeSubScreen = null },
                onSaved = { outcome ->
                  if (outcome.finalizationError != null) {
                    actionErrorMessage = outcome.finalizationError
                    info = ""
                  } else {
                    info = outcome.message
                  }
                  try {
                    products = VendorService.listProducts()
                    productDeletionRequests = VendorService.listProductDeletionRequests()
                  } catch (e: Exception) {
                    actionErrorMessage = e.message
                      ?: AppLanguageManager.tr("Listing was submitted, but the product list could not refresh.", "پروڈکٹ جمع ہو گئی، لیکن فہرست ریفریش نہیں ہو سکی۔")
                  }
                  activeSubScreen = null
                  selectedTab = VendorBottomTab.PRODUCTS
                }
              )

              "product_details" -> {
                val selected = viewingProduct
                if (selected != null) {
                  VendorProductDetailsScreen(
                    product = selected,
                    onBack = {
                      viewingProduct = null
                      activeSubScreen = null
                    },
                    onEdit = {
                      editingProduct = selected
                      activeSubScreen = "product_edit"
                    }
                  )
                } else {
                  activeSubScreen = null
                }
              }

              "product_edit" -> {
                val selected = editingProduct
                if (selected != null) {
                  VendorProductEditScreen(
                    product = selected,
                    allowDirectPublish = application?.allowProductPublishingWithoutReview == true,
                    onBack = {
                      editingProduct = null
                      activeSubScreen = if (viewingProduct != null) "product_details" else null
                    },
                    onSaved = { outcome ->
                      if (outcome.finalizationError != null) {
                        actionErrorMessage = outcome.finalizationError
                        info = ""
                      } else {
                        info = outcome.message
                      }
                      try {
                        products = VendorService.listProducts()
                      } catch (e: Exception) {
                        actionErrorMessage = e.message
                          ?: AppLanguageManager.tr("Product updated, but the product list could not refresh.", "پروڈکٹ اپڈیٹ ہو گئی، لیکن فہرست ریفریش نہیں ہو سکی۔")
                      }
                      editingProduct = null
                      viewingProduct = null
                      activeSubScreen = null
                      selectedTab = VendorBottomTab.PRODUCTS
                    }
                  )
                } else {
                  activeSubScreen = null
                }
              }

              "order_details" -> {
                val group = selectedOrderGroup
                when {
                  group != null -> VendorOrderDetailsScreen(
                    group = group,
                    isBusy = saving || ordersLoading,
                    actionError = actionErrorMessage,
                    infoMessage = info,
                    onBack = {
                      selectedOrderId = null
                      activeSubScreen = null
                    },
                    onRefresh = {
                      scope.launch {
                        refreshOrdersInternal(showLoader = true)
                      }
                    },
                    onPrintPackingSlip = {
                      printVendorPackingSlip(context, group.rawItems)
                    },
                    onMarkReady = { note ->
                      scope.launch {
                        saving = true
                        actionErrorMessage = ""
                        info = ""
                        try {
                          VendorService.markOrderReady(group.orderId, note)
                          info = AppLanguageManager.tr("Marked ready for JTBazar pickup.", "جے ٹی بازار پک اپ کے لیے تیار نشان زد کر دیا گیا۔")
                          refreshOrdersInternal(showLoader = false)
                        } catch (e: Exception) {
                          actionErrorMessage = e.message ?: AppLanguageManager.tr("Could not update order.", "آرڈر اپڈیٹ نہیں ہو سکا۔")
                        } finally {
                          saving = false
                        }
                      }
                    },
                    onDismissAlert = {
                      actionErrorMessage = ""
                      info = ""
                    }
                  )
                  ordersLoading || !hasLoadedData -> Column(
                    modifier = Modifier
                      .fillMaxSize()
                      .padding(VendorSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
                  ) {
                    repeat(3) { VendorSkeletonCard() }
                  }
                  else -> LaunchedEffect(selectedOrderId) {
                    selectedOrderId = null
                    activeSubScreen = null
                  }
                }
              }

              else -> {
                when (selectedTab) {
                  VendorBottomTab.OVERVIEW -> {
                    val recentState = when {
                      loading && !hasLoadedData -> VendorSectionState.Loading
                      errorMessage.isNotBlank() && productUiModels.isEmpty() ->
                        VendorSectionState.Error(errorMessage)
                      productUiModels.isEmpty() -> VendorSectionState.Empty
                      else -> VendorSectionState.Content(productUiModels.take(4))
                    }
                    VendorOverviewContent(
                      uiState = VendorOverviewUiState(
                        shopName = application?.shopName.orEmpty(),
                        isSellerActive = true,
                        totalListings = products.length(),
                        totalOrdersCount = ordersSummary.totalAllOrders,
                        orderItemsCount = ordersSummary.totalOrderItemsCount,
                        itemsToPrepareCount = ordersSummary.itemsToPrepareCount,
                        statusCounts = ordersSummary.statusCounts,
                        unreadNotificationsCount = unreadNotificationsCount,
                        recentOrders = allOrderGroups.take(3),
                        shopStatusLabel = "Active",
                        recentProductsState = recentState,
                        isRefreshing = loading || ordersLoading
                      ),
                      onRefresh = { refresh() },
                      onAddProduct = { activeSubScreen = "product_add" },
                      onViewAllProducts = { selectedTab = VendorBottomTab.PRODUCTS },
                      onOpenProductDetails = { model ->
                        viewingProduct = model.rawJson
                        activeSubScreen = "product_details"
                      },
                      onSelectOrderStatus = { status ->
                        ordersStatus = status.key
                        ordersPage = 0
                        selectedTab = VendorBottomTab.ORDERS
                      },
                      onOpenOrders = {
                        selectedTab = VendorBottomTab.ORDERS
                      },
                      onOpenOrderDetails = { group ->
                        selectedOrderId = group.orderId
                        activeSubScreen = "order_details"
                      },
                      onOpenNotifications = onOpenNotifications,
                      onOpenGuide = { showVendorGuide = true }
                    )
                  }

                  VendorBottomTab.PRODUCTS -> {
                    val productsState = when {
                      loading && !hasLoadedData -> VendorSectionState.Loading
                      errorMessage.isNotBlank() && productUiModels.isEmpty() ->
                        VendorSectionState.Error(errorMessage)
                      productUiModels.isEmpty() -> VendorSectionState.Empty
                      else -> VendorSectionState.Content(productUiModels)
                    }
                    VendorProductsContent(
                      uiState = VendorProductsUiState(
                        productsState = productsState,
                        isMutating = saving,
                        actionError = actionErrorMessage,
                        infoMessage = info
                      ),
                      onRefresh = { refresh() },
                      onAddProduct = { activeSubScreen = "product_add" },
                      onViewDetails = { model ->
                        viewingProduct = model.rawJson
                        activeSubScreen = "product_details"
                      },
                      onEditProduct = { model ->
                        editingProduct = model.rawJson
                        activeSubScreen = "product_edit"
                      },
                      onToggleVisibility = { model ->
                        scope.launch {
                          saving = true
                          actionErrorMessage = ""
                          info = ""
                          try {
                            VendorService.setProductVisibility(model.id, !model.isHidden)
                            products = VendorService.listProducts()
                            info = if (model.isHidden) {
                              AppLanguageManager.tr("Product is now visible to shoppers.", "پروڈکٹ اب خریداروں کو نظر آ رہی ہے۔")
                            } else {
                              AppLanguageManager.tr("Product hidden from shoppers.", "پروڈکٹ خریداروں سے مخفی کر دی گئی۔")
                            }
                          } catch (e: Exception) {
                            actionErrorMessage = e.message ?: AppLanguageManager.tr("Visibility update failed.", "ظاہری حیثیت اپڈیٹ ناکام ہو گئی۔")
                          } finally {
                            saving = false
                          }
                        }
                      },
                      onArchiveProduct = { model ->
                        when {
                          model.moderationStatus == "approved" && !model.isHidden -> {
                            scope.launch {
                              saving = true
                              actionErrorMessage = ""
                              info = ""
                              try {
                                VendorService.setProductVisibility(model.id, true)
                                products = VendorService.listProducts()
                                info = AppLanguageManager.tr("Product archived and hidden from your storefront.", "پروڈکٹ محفوظ کر دی گئی اور آپ کی دکان سے مخفی ہے۔")
                              } catch (e: Exception) {
                                actionErrorMessage = e.message ?: AppLanguageManager.tr("Archive update failed.", "آرکائیو اپڈیٹ ناکام ہو گئی۔")
                              } finally {
                                saving = false
                              }
                            }
                          }
                          model.isHidden -> {
                            info = AppLanguageManager.tr("This product is already archived/hidden from shoppers.", "یہ پروڈکٹ پہلے ہی خریداروں سے مخفی ہے۔")
                          }
                          else -> {
                            actionErrorMessage = AppLanguageManager.tr("Only approved listings can be archived or hidden.", "صرف منظور شدہ مصنوعات کو مخفی یا آرکائیو کیا جا سکتا ہے۔")
                          }
                        }
                      },
                      onRequestDeletion = { model ->
                        deletionTarget = model.rawJson
                        deletionReason = ""
                      },
                      onDismissAlert = {
                        actionErrorMessage = ""
                        info = ""
                      }
                    )
                  }

                  VendorBottomTab.INVENTORY -> VendorInventoryPanel()

                  VendorBottomTab.ORDERS -> {
                    val ordersState = when {
                      (loading && !hasLoadedData) || (ordersLoading && allOrderGroups.isEmpty()) -> VendorSectionState.Loading
                      errorMessage.isNotBlank() && allOrderGroups.isEmpty() ->
                        VendorSectionState.Error(errorMessage)
                      ordersSummary.pageOrders.isEmpty() -> VendorSectionState.Empty
                      else -> VendorSectionState.Content(ordersSummary.pageOrders)
                    }
                    VendorOrdersContent(
                      uiState = VendorOrdersUiState(
                        searchQuery = ordersSearch,
                        selectedStatus = ordersStatus,
                        page = ordersSummary.effectivePage,
                        totalOrders = ordersSummary.filteredOrders.size,
                        totalAllOrders = ordersSummary.totalAllOrders,
                        statusCounts = ordersSummary.statusCounts,
                        ordersState = ordersState,
                        isBusy = saving || ordersLoading,
                        actionError = actionErrorMessage,
                        infoMessage = info
                      ),
                      onSearchChange = {
                        ordersSearch = it
                        ordersPage = 0
                      },
                      onSearchSubmit = {
                        ordersPage = 0
                        scope.launch { refreshOrdersInternal(showLoader = false) }
                      },
                      onStatusSelect = { status ->
                        ordersStatus = status
                        ordersPage = 0
                        scope.launch { refreshOrdersInternal(showLoader = false) }
                      },
                      onPrevPage = {
                        if (ordersSummary.effectivePage > 0) {
                          ordersPage = ordersSummary.effectivePage - 1
                        }
                      },
                      onNextPage = {
                        if ((ordersSummary.effectivePage + 1) * 12 < ordersSummary.filteredOrders.size) {
                          ordersPage = ordersSummary.effectivePage + 1
                        }
                      },
                      onPrintPackingSlip = { group ->
                        printVendorPackingSlip(context, group.rawItems)
                      },
                      onMarkReady = { group ->
                        scope.launch {
                          saving = true
                          actionErrorMessage = ""
                          info = ""
                          try {
                            VendorService.markOrderReady(group.orderId)
                            info = AppLanguageManager.tr("Marked ready for JTBazar pickup.", "جے ٹی بازار پک اپ کے لیے تیار نشان زد کر دیا گیا۔")
                            refreshOrdersInternal(showLoader = false)
                          } catch (e: Exception) {
                            actionErrorMessage = e.message ?: AppLanguageManager.tr("Could not update order.", "آرڈر اپڈیٹ نہیں ہو سکا۔")
                          } finally {
                            saving = false
                          }
                        }
                      },
                      onRefresh = {
                        scope.launch { refreshOrdersInternal(showLoader = true) }
                      },
                      onDismissAlert = {
                        actionErrorMessage = ""
                        info = ""
                      },
                      onOpenOrderDetails = { group ->
                        selectedOrderId = group.orderId
                        activeSubScreen = "order_details"
                      }
                    )
                  }

                  VendorBottomTab.MORE -> {
                    when (moreSection) {
                      VendorMoreSection.MENU -> VendorMoreMenuContent(
                        shopName = application?.shopName.orEmpty(),
                        accountEmail = user?.email.orEmpty(),
                        unreadNotificationsCount = unreadNotificationsCount,
                        onOpenCasesSupport = { moreSection = VendorMoreSection.CASES_SUPPORT },
                        onOpenShopSettings = { moreSection = VendorMoreSection.SHOP_SETTINGS },
                        onOpenAdvancedTools = { moreSection = VendorMoreSection.ADVANCED_TOOLS },
                        onOpenNotifications = onOpenNotifications
                      )

                      VendorMoreSection.CASES_SUPPORT -> VendorCasesScreen()

                      VendorMoreSection.SHOP_SETTINGS -> VendorShopSettingsScreen(
                        application = application!!,
                        accountEmail = user?.email.orEmpty()
                      ) { refresh() }

                      VendorMoreSection.ADVANCED_TOOLS -> {
                        Column(
                          modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(VendorSpacing.md),
                          verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
                        ) {
                          VendorAdvancedPanel(application!!, products) { refresh() }
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          application != null -> {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(VendorSpacing.md),
              verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
            ) {
              if (actionErrorMessage.isNotBlank()) {
                VendorAlertBanner(
                  message = actionErrorMessage,
                  isError = true,
                  onDismiss = { actionErrorMessage = "" }
                )
              }
              if (info.isNotBlank()) {
                VendorAlertBanner(
                  message = info,
                  isError = false,
                  onDismiss = { info = "" }
                )
              }
              if (application?.status.equals("pending", ignoreCase = true)) {
                PendingVendorApplicationScreen(
                  application = application!!,
                  onRefresh = { refresh() },
                  refreshing = loading
                )
              } else {
                SectionHeader(
                  title = appText("Seller application", "فروش کی درخواست"),
                  subtitle = appText("Review your current application status and feedback.", "اپنی درخواست کی موجودہ حیثیت اور تاثرات دیکھیں۔")
                )
                StatusPanel(application!!)
                if (application?.status == "rejected") {
                  VendorAlertBanner(
                    message = appText("Reason: ", "وجہ: ") +
                      (application?.rejectionReason ?: appText("Please review your application.", "براہ کرم اپنی درخواست کا جائزہ لیں۔")),
                    isError = true
                  )
                  AppCard {
                    VendorApplicationResubmissionPanel(application!!) {
                      application = it
                      info = AppLanguageManager.tr("Application resubmitted.", "درخواست دوبارہ جمع کرا دی گئی۔")
                    }
                  }
                }
                SecondaryButton(
                  text = appText("Refresh application status", "درخواست کی حیثیت ریفریش کریں"),
                  icon = Icons.Rounded.Refresh,
                  onClick = { refresh() },
                  modifier = Modifier.fillMaxWidth()
                )
              }
            }
          }

          else -> {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(VendorSpacing.md),
              verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
            ) {
              if (actionErrorMessage.isNotBlank()) {
                VendorAlertBanner(
                  message = actionErrorMessage,
                  isError = true,
                  onDismiss = { actionErrorMessage = "" }
                )
              }
              if (info.isNotBlank()) {
                VendorAlertBanner(
                  message = info,
                  isError = false,
                  onDismiss = { info = "" }
                )
              }

              AppCard(
                containerColor = VendorColors.NavyHeader,
                borderColor = VendorColors.NavyHeader
              ) {
                Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.xs)) {
                  StatusBadge(
                    text = appText("Seller onboarding", "فروش اندراج"),
                    tone = StatusTone.PRIMARY,
                    icon = Icons.Rounded.Storefront
                  )
                  Text(
                    text = appText("Become a JTBazar Vendor", "جے ٹی بازار فروش بنیں"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                  )
                  Text(
                    text = appText(
                      "Submit your shop and identity details. All verification files are stored privately for JTBazar review.",
                      "اپنی دکان اور شناخت کی تفصیلات جمع کرائیں۔ تمام تصدیقی فائلیں جے ٹی بازار کے جائزے کے لیے نجی طور پر محفوظ کی جاتی ہیں۔"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = VendorColors.NavyHeaderSubtitle
                  )
                }
              }

              AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
                  SectionHeader(
                    title = appText("Shop details", "دکان کی تفصیلات"),
                    subtitle = appText("Storefront name, description and business type", "دکان کا نام، تفصیل اور کاروبار کی قسم")
                  )
                  VendorOutlinedTextField(
                    value = shopName,
                    onValueChange = { shopName = it },
                    label = appText("Shop name *", "دکان کا نام *"),
                    leadingIcon = Icons.Rounded.Storefront
                  )
                  VendorOutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = appText("Shop description (at least 10 characters) *", "دکان کی تفصیل (کم از کم 10 حروف) *"),
                    leadingIcon = Icons.Rounded.Description,
                    minLines = 3
                  )
                  VendorTypeDropdown(
                    selectedType = vendorType,
                    onTypeSelected = { vendorType = it }
                  )
                  BusinessTypeDropdown(
                    selectedType = businessType,
                    onTypeSelected = { businessType = it },
                    modifier = Modifier.testTag("onboarding_business_type_dropdown")
                  )
                }
              }

              VendorCnicPrivacyNotice(
                acknowledged = cnicPrivacyAcknowledged,
                onAcknowledgedChange = { cnicPrivacyAcknowledged = it },
                modifier = Modifier.fillMaxWidth()
              )

              AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
                  SectionHeader(
                    title = appText("Owner & addresses", "مالک اور پتے"),
                    subtitle = appText("Legal verification and contact details", "قانونی تصدیق اور رابطے کی تفصیلات")
                  )
                  VendorOutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    label = appText("Owner full legal name *", "مالک کا پورا قانونی نام *"),
                    leadingIcon = Icons.Rounded.Person
                  )
                  VendorOutlinedTextField(
                    value = cnic,
                    onValueChange = { cnic = it.filter(Char::isDigit).take(13) },
                    label = appText("CNIC number (13 digits, no dashes) *", "شناختی کارڈ نمبر (13 ہندسے، بغیر ڈیش) *"),
                    leadingIcon = Icons.Rounded.CreditCard,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                  )
                  VendorOutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = appText("Contact phone (03XXXXXXXXX) *", "رابطہ نمبر (03XXXXXXXXX) *"),
                    leadingIcon = Icons.Rounded.Phone,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                  )
                  VendorOutlinedTextField(
                    value = currentAddress,
                    onValueChange = { currentAddress = it },
                    label = appText("Current address *", "موجودہ پتہ *"),
                    leadingIcon = Icons.Rounded.LocationOn
                  )
                  VendorOutlinedTextField(
                    value = fullAddress,
                    onValueChange = { fullAddress = it },
                    label = appText("Complete address *", "مکمل پتہ *"),
                    leadingIcon = Icons.Rounded.Home,
                    minLines = 2
                  )
                  VendorOutlinedTextField(
                    value = shopLocation,
                    onValueChange = { shopLocation = it },
                    label = appText("Shop location / landmark details *", "دکان کا مقام / قریبی نشانی *"),
                    leadingIcon = Icons.Rounded.LocationCity,
                    minLines = 2
                  )
                }
              }

              AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
                  SectionHeader(
                    title = appText("Shop location & picture", "دکان کا مقام اور تصویر"),
                    subtitle = appText("Pin your location on the map and add a shop photo", "نقشے پر اپنا مقام پن کریں اور دکان کی تصویر شامل کریں")
                  )
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(
                      text = if (latitude.isNotBlank() && longitude.isNotBlank()) {
                        appText("Selected: $latitude, $longitude", "منتخب شدہ: $latitude, $longitude")
                      } else {
                        appText("Choose your shop location on the map.", "نقشے پر اپنی دکان کا مقام منتخب کریں۔")
                      },
                      style = MaterialTheme.typography.bodySmall,
                      color = VendorColors.TextSecondary,
                      modifier = Modifier.weight(1f)
                    )
                    StatusBadge(
                      text = if (latitude.isNotBlank() && longitude.isNotBlank()) appText("Pinned", "پن شدہ") else appText("Required", "ضروری"),
                      tone = if (latitude.isNotBlank() && longitude.isNotBlank()) {
                        StatusTone.SUCCESS
                      } else {
                        StatusTone.WARNING
                      }
                    )
                  }
                  SecondaryButton(
                    text = if (latitude.isNotBlank() && longitude.isNotBlank()) {
                      appText("Change shop location on map", "نقشے پر دکان کا مقام تبدیل کریں")
                    } else {
                      appText("Choose shop location on map", "نقشے پر دکان کا مقام منتخب کریں")
                    },
                    icon = Icons.Rounded.Map,
                    onClick = { showMapPicker = true },
                    modifier = Modifier.fillMaxWidth()
                  )

                  HorizontalDivider(color = VendorColors.Divider)

                  shopImageUri?.let { uri ->
                    AsyncImage(
                      model = uri,
                      contentDescription = appText("Shop picture preview", "دکان کی تصویر کا پیش نظارہ"),
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(VendorShapes.input),
                      contentScale = ContentScale.Crop
                    )
                  }
                  SecondaryButton(
                    text = if (shopImageUri == null) appText("Choose public shop picture", "دکان کی تصویر منتخب کریں")
                    else appText("Change selected shop picture", "منتخب کردہ تصویر تبدیل کریں"),
                    icon = Icons.Rounded.Image,
                    onClick = { shopImagePicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                  )
                }
              }

              AppCard(borderColor = VendorColors.NavyHeader.copy(alpha = 0.2f)) {
                Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VendorSpacing.xs)
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Lock,
                      contentDescription = null,
                      tint = VendorColors.NavyHeader,
                      modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = appText("Verification documents", "تصدیقی دستاویزات"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VendorColors.TextPrimary
                      )
                      Text(
                        text = appText("Stored privately for JTBazar admin review only.", "صرف جے ٹی بازار ایڈمن کے جائزے کے لیے نجی طور پر محفوظ۔"),
                        style = MaterialTheme.typography.bodySmall,
                        color = VendorColors.TextSecondary
                      )
                    }
                  }
                  DocumentButton(
                    label = appText("Identity / business verification document", "شناخت / کاروباری تصدیقی دستاویز"),
                    uploaded = verificationPath != null
                  ) {
                    pendingDoc = "verification"
                    documentPicker.launch("*/*")
                  }
                  DocumentButton(
                    label = appText("CNIC front image", "شناختی کارڈ کا سامنے کا عکس"),
                    uploaded = frontPath != null
                  ) {
                    pendingDoc = "cnic-front"
                    documentPicker.launch("image/*")
                  }
                  DocumentButton(
                    label = appText("CNIC back image", "شناختی کارڈ کا پیچھے کا عکس"),
                    uploaded = backPath != null
                  ) {
                    pendingDoc = "cnic-back"
                    documentPicker.launch("image/*")
                  }
                }
              }

              PrimaryButton(
                text = if (saving) appText("Submitting...", "جمع کرایا جا رہا ہے...") else appText("Submit vendor application", "فروش درخواست جمع کرائیں"),
                icon = Icons.Rounded.Send,
                loading = saving,
                enabled = !saving,
                onClick = {
                  scope.launch {
                    saving = true
                    actionErrorMessage = ""
                    info = ""
                    try {
                      val u = user ?: error(AppLanguageManager.tr("Please sign in again.", "براہ کرم دوبارہ سائن ان کریں۔"))
                      val normalizedPhone = normalizePakistaniPhone(phone)
                      require(shopName.trim().length in 2..160) {
                        AppLanguageManager.tr("Shop name must be between 2 and 160 characters.", "دکان کا نام 2 سے 160 حروف کے درمیان ہونا چاہیے۔")
                      }
                      require(description.trim().length in 10..2000) {
                        AppLanguageManager.tr("Shop description must be between 10 and 2,000 characters.", "دکان کی تفصیل 10 سے 2,000 حروف کے درمیان ہونی چاہیے۔")
                      }
                      require(VendorBusinessType.isValid(businessType)) {
                        AppLanguageManager.tr("Select what type of business you operate.", "آپ کس قسم کا کاروبار کرتے ہیں، یہ منتخب کریں۔")
                      }
                      require(ownerName.trim().length >= 3) {
                        AppLanguageManager.tr("Enter the owner's full legal name.", "مالک کا پورا قانونی نام درج کریں۔")
                      }
                      require(cnicPrivacyAcknowledged) {
                        AppLanguageManager.tr(
                          "Read and acknowledge the CNIC privacy notice before submitting.",
                          "جمع کرانے سے پہلے شناختی کارڈ کی رازداری کا نوٹس پڑھ کر اس کی تصدیق کریں۔"
                        )
                      }
                      require(cnic.matches(Regex("^[0-9]{13}$"))) {
                        AppLanguageManager.tr("Enter a 13-digit CNIC number without dashes.", "بغیر ڈیش کے 13 ہندسوں کا شناختی کارڈ نمبر درج کریں۔")
                      }
                      require(normalizedPhone.matches(Regex("^(03[0-9]{9}|\\+923[0-9]{9})$"))) {
                        AppLanguageManager.tr("Enter a valid Pakistani phone number, e.g. 03001234567.", "درست پاکستانی فون نمبر درج کریں، مثلاً 03001234567۔")
                      }
                      require(
                        currentAddress.trim().length >= 3 &&
                          fullAddress.trim().length >= 10 &&
                          shopLocation.trim().length >= 10
                      ) {
                        AppLanguageManager.tr(
                          "Complete your current address, full address and shop landmark (at least 10 characters).",
                          "اپنا موجودہ پتہ، مکمل پتہ اور دکان کی نشانی مکمل کریں (کم از کم 10 حروف)۔"
                        )
                      }
                      val lat = latitude.toDoubleOrNull() ?: error(AppLanguageManager.tr("Choose your shop location on the map.", "نقشے پر اپنی دکان کا مقام منتخب کریں۔"))
                      val lng = longitude.toDoubleOrNull() ?: error(AppLanguageManager.tr("Choose your shop location on the map.", "نقشے پر اپنی دکان کا مقام منتخب کریں۔"))
                      require(
                        lat in -90.0..90.0 &&
                          lng in -180.0..180.0 &&
                          !(lat == 0.0 && lng == 0.0)
                      ) {
                        AppLanguageManager.tr("Map coordinates are invalid. Select your actual shop location.", "نقشے کے نقاط درست نہیں ہیں۔ اپنی دکان کا اصل مقام منتخب کریں۔")
                      }
                      require(
                        !verificationPath.isNullOrBlank() &&
                          !frontPath.isNullOrBlank() &&
                          !backPath.isNullOrBlank()
                      ) {
                        AppLanguageManager.tr("Upload all three verification documents before submitting.", "جمع کرانے سے پہلے تینوں تصدیقی دستاویزات اپ لوڈ کریں۔")
                      }
                      require(u.email?.isNotBlank() == true) {
                        AppLanguageManager.tr("Your account email is missing. Sign in again.", "آپ کے اکاؤنٹ کا ای میل موجود نہیں۔ دوبارہ سائن ان کریں۔")
                      }
                      val slugBase = shopName.lowercase(Locale.ROOT)
                        .replace(Regex("[^a-z0-9]+"), "-")
                        .trim('-')
                        .take(120)
                        .trim('-')
                        .ifBlank { "shop" }
                      val slug = "$slugBase-${java.util.UUID.randomUUID().toString().take(8)}"
                      val uploadedShopImage = shopImageUri?.let { uri ->
                        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                          ?: error(AppLanguageManager.tr("Shop picture could not be read.", "دکان کی تصویر پڑھی نہیں جا سکی۔"))
                        VendorService.uploadShopImage(bytes, mime)
                      }
                      val body = JSONObject()
                        .put("user_id", u.id)
                        .put("shop_name", shopName.trim())
                        .put("slug", slug)
                        .put("description", description.trim().take(2000))
                        .put("current_address", currentAddress.trim())
                        .put("complete_address", fullAddress.trim())
                        .put("shop_image_url", uploadedShopImage ?: JSONObject.NULL)
                        .put("shop_location_label", currentAddress.trim().take(120))
                        .put("contact_email", u.email)
                        .put("phone", normalizedPhone)
                        .put("document_path", verificationPath)
                        .put("cnic_front_path", frontPath)
                        .put("cnic_back_path", backPath)
                        .put("vendor_type", vendorType.trim())
                        .put("business_type", businessType.trim())
                        .put("owner_full_name", ownerName.trim())
                        .put("cnic_number", cnic)
                        .put("shop_location_text", shopLocation.trim())
                        .put("shop_lat", lat)
                        .put("shop_lng", lng)
                        .put("show_shop_on_map", true)
                      application = VendorService.submitApplication(body)
                      cnic = ""
                      cnicPrivacyAcknowledged = false
                      info = AppLanguageManager.tr(
                        "Application submitted successfully. JTBazar admin review is pending.",
                        "درخواست کامیابی سے جمع ہو گئی۔ جے ٹی بازار ایڈمن کا جائزہ زیر التواء ہے۔"
                      )
                    } catch (e: Exception) {
                      actionErrorMessage = e.message ?: AppLanguageManager.tr("Application could not be submitted.", "درخواست جمع نہیں ہو سکی۔")
                    } finally {
                      saving = false
                    }
                  }
                },
                modifier = Modifier.fillMaxWidth()
              )

              Text(
                text = appText(
                  "Your application will remain pending until JTBazar admin approves it.",
                  "جے ٹی بازار ایڈمن کی منظوری تک آپ کی درخواست زیر التواء رہے گی۔"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = VendorColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
              )
            }
          }
        }
      }
    }
  }

  if (showVendorGuide) {
    VendorGuideDialog(onDismiss = { showVendorGuide = false })
  }

  if (showMapPicker) {
    ShopLocationPickerDialog(
      initialLatitude = latitude.toDoubleOrNull(),
      initialLongitude = longitude.toDoubleOrNull(),
      onDismiss = { showMapPicker = false },
      onConfirm = { point ->
        latitude = String.format(Locale.US, "%.6f", point.latitude)
        longitude = String.format(Locale.US, "%.6f", point.longitude)
        showMapPicker = false
      }
    )
  }

  if (deletionTarget != null) {
    val target = deletionTarget!!
    AlertDialog(
      onDismissRequest = { if (!saving) deletionTarget = null },
      containerColor = VendorColors.CardWhite,
      shape = VendorShapes.card,
      title = {
        Text(
          text = appText("Request product deletion", "پروڈکٹ حذف کرنے کی درخواست"),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = VendorColors.TextPrimary
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
          Text(
            text = appText(
              "This sends a request to JTBazar for review. The product will not be deleted immediately.",
              "یہ درخواست جے ٹی بازار کو جائزے کے لیے بھیجی جاتی ہے۔ پروڈکٹ فوری طور پر حذف نہیں ہوگی۔"
            ),
            style = MaterialTheme.typography.bodySmall,
            color = VendorColors.TextSecondary
          )
          Text(
            text = target.optString("name", appText("Product", "پروڈکٹ")),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = VendorColors.TextPrimary
          )
          VendorOutlinedTextField(
            value = deletionReason,
            onValueChange = { deletionReason = it },
            label = appText("Reason for deletion *", "حذف کرنے کی وجہ *"),
            leadingIcon = Icons.Rounded.Description,
            minLines = 3,
            enabled = !saving
          )
        }
      },
      confirmButton = {
        PrimaryButton(
          text = if (saving) appText("Submitting...", "جمع کرایا جا رہا ہے...") else appText("Submit request", "درخواست جمع کرائیں"),
          loading = saving,
          enabled = !saving && deletionReason.trim().length >= 3,
          containerColor = VendorColors.DangerRed,
          onClick = {
            scope.launch {
              saving = true
              actionErrorMessage = ""
              try {
                VendorService.requestProductDeletion(target.optString("id"), deletionReason.trim())
                productDeletionRequests = VendorService.listProductDeletionRequests()
                info = AppLanguageManager.tr("Product deletion request submitted for JTBazar review.", "پروڈکٹ حذف کرنے کی درخواست جے ٹی بازار کے جائزے کے لیے جمع کرا دی گئی۔")
                deletionTarget = null
                deletionReason = ""
              } catch (e: Exception) {
                actionErrorMessage = e.message ?: AppLanguageManager.tr("Deletion request could not be submitted.", "حذف کرنے کی درخواست جمع نہیں ہو سکی۔")
              } finally {
                saving = false
              }
            }
          }
        )
      },
      dismissButton = {
        TextButton(
          onClick = { deletionTarget = null },
          enabled = !saving,
          modifier = Modifier.height(VendorSpacing.touchMin)
        ) {
          Text(appText("Cancel", "منسوخ کریں"), color = VendorColors.TextSecondary, fontWeight = FontWeight.SemiBold)
        }
      }
    )
  }
}

@Composable
fun ShopLocationPickerDialog(
  initialLatitude: Double?,
  initialLongitude: Double?,
  onDismiss: () -> Unit,
  onConfirm: (GeoPoint) -> Unit
) {
  val context = LocalContext.current
  val fusedLocation = remember { LocationServices.getFusedLocationProviderClient(context) }
  val startPoint = remember {
    if (initialLatitude != null && initialLongitude != null &&
      initialLatitude in -90.0..90.0 && initialLongitude in -180.0..180.0
    ) GeoPoint(initialLatitude, initialLongitude) else GeoPoint(0.0, 0.0)
  }
  var selectedPoint by remember { mutableStateOf(startPoint) }
  var hasSelectedPoint by remember {
    mutableStateOf(
      initialLatitude != null && initialLongitude != null &&
        initialLatitude in -90.0..90.0 && initialLongitude in -180.0..180.0 &&
        !(initialLatitude == 0.0 && initialLongitude == 0.0)
    )
  }
  var mapView by remember { mutableStateOf<MapView?>(null) }
  var locationMessage by remember {
    mutableStateOf(
      AppLanguageManager.tr(
        "Move the map and tap your shop location to place the pin.",
        "نقشہ حرکت دیں اور پن لگانے کے لیے اپنی دکان کے مقام پر ٹیپ کریں۔"
      )
    )
  }

  fun placePin(point: GeoPoint) {
    selectedPoint = point
    hasSelectedPoint = point.latitude in -90.0..90.0 &&
      point.longitude in -180.0..180.0 &&
      !(point.latitude == 0.0 && point.longitude == 0.0)
    mapView?.let { map ->
      map.overlays.filterIsInstance<Marker>().forEach { map.overlays.remove(it) }
      val marker = Marker(map).apply {
        position = point
        title = AppLanguageManager.tr("Shop location", "دکان کا مقام")
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
      }
      map.overlays.add(marker)
      map.controller.animateTo(point)
      map.invalidate()
    }
  }

  val locationPermission = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    if (granted) {
      try {
        fusedLocation.getCurrentLocation(
          Priority.PRIORITY_BALANCED_POWER_ACCURACY,
          CancellationTokenSource().token
        )
          .addOnSuccessListener { location: Location? ->
            if (location != null) {
              placePin(GeoPoint(location.latitude, location.longitude))
              locationMessage = AppLanguageManager.tr(
                "Current device location selected. Adjust the pin if needed.",
                "موجودہ ڈیوائس لوکیشن منتخب ہو گئی۔ ضرورت ہو تو پن ایڈجسٹ کریں۔"
              )
            } else {
              locationMessage = AppLanguageManager.tr(
                "Current location unavailable. Move the map and tap your shop location.",
                "موجودہ مقام دستیاب نہیں۔ نقشہ حرکت دیں اور اپنی دکان کے مقام پر ٹیپ کریں۔"
              )
            }
          }
          .addOnFailureListener {
            locationMessage = it.message ?: AppLanguageManager.tr("Could not get current location.", "موجودہ مقام حاصل نہیں ہو سکا۔")
          }
      } catch (_: SecurityException) {
        locationMessage = AppLanguageManager.tr("Location permission is required to use current location.", "موجودہ مقام استعمال کرنے کے لیے لوکیشن کی اجازت درکار ہے۔")
      }
    } else {
      locationMessage = AppLanguageManager.tr(
        "Location permission denied. You can still select a location manually on the map.",
        "لوکیشن کی اجازت مسترد ہو گئی۔ آپ اب بھی نقشے پر دستی طور پر مقام منتخب کر سکتے ہیں۔"
      )
    }
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = VendorShapes.card,
      color = VendorColors.CardWhite,
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 680.dp)
    ) {
      Column(
        modifier = Modifier.padding(VendorSpacing.md),
        verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
      ) {
        Text(
          text = appText("Choose shop location", "دکان کا مقام منتخب کریں"),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = VendorColors.TextPrimary
        )
        Text(
          text = appText(
            "Drag the map and tap the exact shop location. The pin will be saved automatically.",
            "نقشے کو گھسیٹیں اور دکان کے درست مقام پر ٹیپ کریں۔ پن خود بخود محفوظ ہو جائے گا۔"
          ),
          color = VendorColors.TextSecondary,
          style = MaterialTheme.typography.bodySmall
        )
        AndroidView(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp, max = 380.dp)
            .aspectRatio(1.05f)
            .clip(VendorShapes.input),
          factory = { ctx ->
            Configuration.getInstance().userAgentValue = ctx.packageName
            MapView(ctx).apply {
              setTileSource(TileSourceFactory.MAPNIK)
              setMultiTouchControls(true)
              controller.setZoom(if (hasSelectedPoint) 16.0 else 2.5)
              controller.setCenter(startPoint)
              val receiver = object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                  placePin(point)
                  locationMessage = AppLanguageManager.tr("Location pin selected.", "مقام کا پن منتخب ہو گیا۔")
                  return true
                }

                override fun longPressHelper(point: GeoPoint): Boolean = false
              }
              overlays.add(MapEventsOverlay(receiver))
              mapView = this
              if (initialLatitude != null && initialLongitude != null) placePin(startPoint)
            }
          },
          update = { map -> mapView = map }
        )
        Text(
          text = locationMessage,
          color = VendorColors.TextSecondary,
          style = MaterialTheme.typography.bodySmall
        )
        SecondaryButton(
          text = appText("Use my current location", "میرا موجودہ مقام استعمال کریں"),
          icon = Icons.Rounded.MyLocation,
          onClick = {
            val fine = ContextCompat.checkSelfPermission(
              context,
              Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
              context,
              Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (fine || coarse) {
              try {
                fusedLocation.getCurrentLocation(
                  Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                  CancellationTokenSource().token
                )
                  .addOnSuccessListener { location ->
                    if (location != null) {
                      placePin(GeoPoint(location.latitude, location.longitude))
                      locationMessage = AppLanguageManager.tr(
                        "Current device location selected. Adjust the pin if needed.",
                        "موجودہ ڈیوائس لوکیشن منتخب ہو گئی۔ ضرورت ہو تو پن ایڈجسٹ کریں۔"
                      )
                    } else {
                      locationMessage = AppLanguageManager.tr(
                        "Current location unavailable. Move the map and tap your shop location.",
                        "موجودہ مقام دستیاب نہیں۔ نقشہ حرکت دیں اور اپنی دکان کے مقام پر ٹیپ کریں۔"
                      )
                    }
                  }
                  .addOnFailureListener {
                    locationMessage = it.message ?: AppLanguageManager.tr("Could not get current location.", "موجودہ مقام حاصل نہیں ہو سکا۔")
                  }
              } catch (_: SecurityException) {
                locationMessage = AppLanguageManager.tr("Location permission is required.", "لوکیشن کی اجازت درکار ہے۔")
              }
            } else {
              locationPermission.launch(
                arrayOf(
                  Manifest.permission.ACCESS_FINE_LOCATION,
                  Manifest.permission.ACCESS_COARSE_LOCATION
                )
              )
            }
          },
          modifier = Modifier.fillMaxWidth()
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(VendorSpacing.xs)
        ) {
          SecondaryButton(
            text = appText("Cancel", "منسوخ کریں"),
            onClick = onDismiss,
            modifier = Modifier.weight(1f)
          )
          PrimaryButton(
            text = appText("Use location", "مقام استعمال کریں"),
            enabled = hasSelectedPoint,
            onClick = { onConfirm(selectedPoint) },
            modifier = Modifier.weight(1f)
          )
        }
      }
    }
  }
  DisposableEffect(Unit) {
    onDispose {
      mapView?.onDetach()
      mapView = null
    }
  }
}

@Composable
private fun DocumentButton(label: String, uploaded: Boolean, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(VendorSpacing.xs)
  ) {
    SecondaryButton(
      text = if (uploaded) appText("$label uploaded", "$label اپ لوڈ ہو گئی") else appText("Upload $label", "$label اپ لوڈ کریں"),
      icon = if (uploaded) Icons.Rounded.CheckCircle else Icons.Rounded.UploadFile,
      contentColor = if (uploaded) VendorColors.SuccessGreen else VendorColors.TextPrimary,
      borderColor = if (uploaded) VendorColors.SuccessGreen.copy(alpha = 0.35f) else VendorColors.BorderSubtle,
      onClick = onClick,
      modifier = Modifier.fillMaxWidth()
    )
  }
}

@Composable
private fun PendingVendorApplicationScreen(
  application: VendorApplication,
  onRefresh: () -> Unit,
  refreshing: Boolean
) {
  val submittedDate = remember(application.createdAt) {
    runCatching {
      java.time.Instant.parse(application.createdAt)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))
    }.getOrElse { AppLanguageManager.tr("Recently", "حال ہی میں") }
  }

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
  ) {
    AppCard(
      containerColor = VendorColors.NavyHeader,
      borderColor = VendorColors.NavyHeader
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.xs)) {
        StatusBadge(
          text = appText("Pending review", "جائزہ زیر التواء"),
          tone = StatusTone.WARNING,
          icon = Icons.Rounded.Schedule
        )
        Text(
          text = application.shopName,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
        Text(
          text = appText(
            "Your application is under verification by the JTBazar marketplace team.",
            "آپ کی درخواست جے ٹی بازار ٹیم کی تصدیق کے مراحل میں ہے۔"
          ),
          style = MaterialTheme.typography.bodySmall,
          color = VendorColors.NavyHeaderSubtitle
        )
      }
    }

    AppCard(
      containerColor = VendorColors.PrimaryOrangeSoft,
      borderColor = VendorColors.PrimaryOrangeBorder
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VendorSpacing.xs)
      ) {
        Box(
          modifier = Modifier
            .size(56.dp)
            .clip(VendorShapes.iconBox)
            .background(VendorColors.PrimaryOrangeTint),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Rounded.Schedule,
            contentDescription = null,
            tint = VendorColors.PrimaryOrange,
            modifier = Modifier.size(28.dp)
          )
        }
        Text(
          text = appText("Application Under Review", "درخواست زیر غور ہے"),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = VendorColors.TextPrimary
        )
        Text(
          text = appText(
            "Thank you for applying to become a JTBazar seller. We are currently reviewing your documents.",
            "جے ٹی بازار فروش بننے کے لیے درخواست دینے کا شکریہ۔ ہم اس وقت آپ کی دستاویزات کا جائزہ لے رہے ہیں۔"
          ),
          style = MaterialTheme.typography.bodySmall,
          color = VendorColors.TextSecondary,
          textAlign = TextAlign.Center
        )
      }
    }

    AppCard {
      Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
        Text(
          text = appText("Application progress", "درخواست کی پیشرفت"),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = VendorColors.TextPrimary
        )
        VendorProgressStep(
          icon = Icons.Rounded.CheckCircle,
          title = appText("Application received", "درخواست موصول ہو گئی"),
          description = appText("Your shop details have been submitted successfully.", "آپ کی دکان کی تفصیلات کامیابی سے جمع ہو گئی ہیں۔"),
          active = true,
          iconTint = VendorColors.SuccessGreen,
          iconBg = VendorColors.SuccessGreenBg
        )
        VendorProgressStep(
          icon = Icons.Rounded.Schedule,
          title = appText("Verification in progress", "تصدیق جاری ہے"),
          description = appText("Our marketplace team is reviewing your shop information.", "ہماری ٹیم آپ کی دکان کی معلومات کا جائزہ لے رہی ہے۔"),
          active = true,
          iconTint = VendorColors.PrimaryOrange,
          iconBg = VendorColors.PrimaryOrangeTint
        )
        VendorProgressStep(
          icon = Icons.Rounded.VerifiedUser,
          title = appText("Seller access", "فروش تک رسائی"),
          description = appText("We'll notify you by email when your seller dashboard is ready.", "جب آپ کا فروش ڈیش بورڈ تیار ہوگا تو ہم آپ کو ای میل کے ذریعے مطلع کریں گے۔"),
          active = false,
          iconTint = VendorColors.TextSecondary,
          iconBg = VendorColors.NeutralBadgeBg
        )
      }
    }

    AppCard {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm),
        verticalAlignment = Alignment.Top
      ) {
        Icon(
          imageVector = Icons.Rounded.Info,
          contentDescription = null,
          tint = VendorColors.NavyHeader,
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = appText(
            "If we need any additional information or your application is not approved, we will notify you by email.",
            "اگر ہمیں مزید معلومات درکار ہوں یا آپ کی درخواست منظور نہ ہو تو ہم آپ کو ای میل کے ذریعے آگاہ کریں گے۔"
          ),
          style = MaterialTheme.typography.bodySmall,
          color = VendorColors.TextSecondary,
          modifier = Modifier.weight(1f)
        )
      }
    }

    AppCard(contentPadding = PaddingValues(horizontal = VendorSpacing.md, vertical = VendorSpacing.sm)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = appText("Submitted", "جمع کرانے کی تاریخ"),
          style = MaterialTheme.typography.bodySmall,
          color = VendorColors.TextSecondary
        )
        Text(
          text = submittedDate,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = VendorColors.TextPrimary
        )
      }
    }

    PrimaryButton(
      text = appText("Refresh application status", "درخواست کی حیثیت ریفریش کریں"),
      icon = Icons.Rounded.Refresh,
      loading = refreshing,
      enabled = !refreshing,
      onClick = onRefresh,
      modifier = Modifier.fillMaxWidth()
    )
    Text(
      text = appText(
        "Your seller tools will become available here once your application is approved.",
        "درخواست منظور ہونے کے بعد آپ کے فروش ٹولز یہاں دستیاب ہوں گے۔"
      ),
      style = MaterialTheme.typography.bodySmall,
      color = VendorColors.TextSecondary,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth()
    )
  }
}

@Composable
private fun VendorProgressStep(
  icon: ImageVector,
  title: String,
  description: String,
  active: Boolean,
  iconTint: Color,
  iconBg: Color
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm),
    verticalAlignment = Alignment.Top
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(iconBg),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(20.dp)
      )
    }
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
        color = VendorColors.TextPrimary
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = VendorColors.TextSecondary
      )
    }
  }
}

@Composable
private fun StatusPanel(app: VendorApplication) {
  val (tone, icon) = when (app.status.lowercase()) {
    "approved" -> StatusTone.SUCCESS to Icons.Rounded.CheckCircle
    "rejected" -> StatusTone.DANGER to Icons.Rounded.ErrorOutline
    else -> StatusTone.WARNING to Icons.Rounded.Schedule
  }
  val localizedAppStatus = when (app.status.lowercase()) {
    "approved" -> appText("APPROVED", "منظور شدہ")
    "rejected" -> appText("REJECTED", "مسترد")
    "pending" -> appText("PENDING", "زیر التواء")
    else -> app.status.uppercase()
  }
  AppCard {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = app.shopName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = VendorColors.TextPrimary
        )
        Text(
          text = appText("Application status", "درخواست کی حیثیت"),
          style = MaterialTheme.typography.bodySmall,
          color = VendorColors.TextSecondary
        )
      }
      StatusBadge(
        text = localizedAppStatus,
        tone = tone,
        icon = icon
      )
    }
  }
}

private fun normalizePakistaniPhone(input: String): String {
  var value = input.trim().replace(Regex("[\\s()\\-]"), "")
  if (value.startsWith("0092")) value = "+92" + value.removePrefix("0092")
  if (value.startsWith("92") && !value.startsWith("+92")) value = "+$value"
  if (value.startsWith("+920")) value = "+92" + value.removePrefix("+920")
  if (value.matches(Regex("^3[0-9]{9}$"))) value = "0$value"
  return value
}

private fun vendorLoadErrorMessage(error: Throwable): String {
  val details = generateSequence(error) { it.cause }
    .mapNotNull { it.message }
    .joinToString(" ")
    .lowercase()
  return when {
    details.contains("unable to resolve host") ||
      details.contains("unknownhostexception") ||
      details.contains("no address associated with hostname") ->
      AppLanguageManager.tr(
        "Unable to resolve the JTBazar server address. Please check your Wi-Fi or mobile data connection and try again.",
        "جے ٹی بازار سرور کا پتہ فی الحال حل نہیں ہو رہا۔ براہ کرم اپنا وائی فائی یا موبائل ڈیٹا چیک کر کے دوبارہ کوشش کریں۔"
      )
    details.contains("timeout") || details.contains("timed out") ->
      AppLanguageManager.tr(
        "No response received from the JTBazar server. Please check your internet connection and try again.",
        "جے ٹی بازار سرور سے جواب موصول نہیں ہوا۔ براہ کرم انٹرنیٹ کنکشن چیک کر کے دوبارہ کوشش کریں۔"
      )
    details.contains("failed to connect") || details.contains("network is unreachable") ->
      AppLanguageManager.tr(
        "Internet connection appears unavailable. Please check your network and try again.",
        "انٹرنیٹ کنکشن دستیاب نہیں لگ رہا۔ براہ کرم اپنا نیٹ ورک چیک کر کے دوبارہ کوشش کریں۔"
      )
    else -> AppLanguageManager.tr(
      "Could not load vendor information. Please try again.",
      "فروش کی معلومات لوڈ نہیں ہو سکیں۔ براہ کرم دوبارہ کوشش کریں۔"
    )
  }
}

private fun printVendorPackingSlip(context: android.content.Context, items: List<JSONObject>) {
  val first = items.firstOrNull() ?: return
  fun esc(value: String) = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

  val rows = items.joinToString("") { item ->
    val variant = item.optJSONObject("variant_snapshot")?.let { v ->
      "${v.optString("label", v.optString("option_label"))}: ${v.optString("value", v.optString("option_value"))}"
    } ?: "—"
    "<tr><td>${esc(item.optString("product_name_snapshot", item.optString("product_name", "Product")))}</td><td>${esc(item.optString("sku_snapshot", "—"))}</td><td>${esc(variant)}</td><td>${esc(item.optString("quantity", "1"))}</td></tr>"
  }
  val titleLabel = esc(AppLanguageManager.tr("Product packing slip", "پروڈکٹ پیکنگ سلپ"))
  val productCol = esc(AppLanguageManager.tr("Product", "پروڈکٹ"))
  val skuCol = esc(AppLanguageManager.tr("SKU", "ایس کے یو"))
  val variantCol = esc(AppLanguageManager.tr("Variant", "قسم"))
  val qtyCol = esc(AppLanguageManager.tr("Qty", "تعداد"))
  val footerNote = esc(AppLanguageManager.tr(
    "Product and quantity summary only. Customer contact and delivery details are managed by the marketplace team.",
    "صرف پروڈکٹ اور تعداد کا خلاصہ۔ صارف سے رابطہ اور ڈیلیوری کی تفصیلات مارکیٹ پلیس ٹیم سنبھالتی ہے۔"
  ))
  val dir = if (AppLanguageManager.isUrdu) "rtl" else "ltr"
  val brandTitle = esc(AppLanguageManager.tr("JTBazar", "جے ٹی بازار"))
  val html = """<!doctype html><html dir="$dir"><head><meta charset="utf-8"><style>
    body{font-family:Arial,sans-serif;padding:24px;color:#222}header{display:flex;justify-content:space-between;border-bottom:2px solid #222;padding-bottom:14px}
    h1{margin:6px 0;font-size:24px}small{color:#555}table{width:100%;border-collapse:collapse;margin-top:24px}
    th,td{text-align:start;border:1px solid #bbb;padding:10px;font-size:13px}th{background:#f1f1f1}
    footer{margin-top:28px;font-size:11px;color:#555}
  </style></head><body><header><div><small>$titleLabel</small><h1>$brandTitle</h1></div>
  <div><strong>${esc(first.optString("order_number", ""))}</strong><br><small>${esc(first.optString("order_created_at", first.optString("created_at", "")))}</small></div></header>
  <table><thead><tr><th>$productCol</th><th>$skuCol</th><th>$variantCol</th><th>$qtyCol</th></tr></thead><tbody>$rows</tbody></table>
  <footer>$footerNote</footer></body></html>"""
  val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
  val webView = WebView(context)
  webView.settings.javaScriptEnabled = false
  webView.webViewClient = object : WebViewClient() {
    override fun onPageFinished(view: WebView, url: String?) {
      val adapter = view.createPrintDocumentAdapter(
        "JTBazar-Packing-Slip-${first.optString("order_number", "order")}"
      )
      printManager.print(AppLanguageManager.tr("JTBazar product packing slip", "جے ٹی بازار پروڈکٹ پیکنگ سلپ"), adapter, PrintAttributes.Builder().build())
    }
  }
  webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
}
