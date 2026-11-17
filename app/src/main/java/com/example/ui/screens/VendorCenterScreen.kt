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
import com.example.data.supabase.VendorOrderStatus
import com.example.data.supabase.VendorService
import com.example.ui.components.AppCard
import com.example.ui.components.ErrorState
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.components.StatusTone
import com.example.ui.components.VendorAlertBanner
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
  var vendorType by remember { mutableStateOf("individual_seller") }
  var currentAddress by remember { mutableStateOf("") }
  var fullAddress by remember { mutableStateOf("") }
  var shopLocation by remember { mutableStateOf("") }
  var latitude by remember { mutableStateOf("") }
  var longitude by remember { mutableStateOf("") }
  var showMapPicker by remember { mutableStateOf(false) }
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
      ?: throw IllegalStateException("Selected file could not be read.")
    require(bytes.size <= 10 * 1024 * 1024) { "Each verification file must be 10 MB or smaller." }
    require(mime in listOf("application/pdf", "image/jpeg", "image/png", "image/webp")) {
      "Use PDF, JPG, PNG or WebP files."
    }
    val ext = when (mime) {
      "image/jpeg" -> "jpg"
      "image/png" -> "png"
      "image/webp" -> "webp"
      else -> "pdf"
    }
    val path = "${user?.id}/$side-${System.currentTimeMillis()}.$ext"
    return VendorService.uploadPrivateDocument(path, bytes, mime)
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
        info = "Document uploaded securely."
      } catch (e: Exception) {
        actionErrorMessage = e.message ?: "Document upload failed."
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
    activeSubScreen == "product_add" -> "Add listing"
    activeSubScreen == "product_edit" -> "Edit product"
    activeSubScreen == "product_details" -> "Product details"
    activeSubScreen == "order_details" -> "Order details"
    isApprovedVendor && selectedTab == VendorBottomTab.MORE -> when (moreSection) {
      VendorMoreSection.CASES_SUPPORT -> "Cases & Support"
      VendorMoreSection.SHOP_SETTINGS -> "Shop Settings"
      VendorMoreSection.ADVANCED_TOOLS -> "More Tools"
      VendorMoreSection.MENU -> "Vendor Center"
    }
    else -> "Vendor Center"
  }

  val topBarSubtitle = when {
    activeSubScreen == "order_details" && selectedOrderGroup != null ->
      "Order ${selectedOrderGroup.orderNumber}"
    activeSubScreen != null -> application?.shopName?.takeIf { it.isNotBlank() }
    isApprovedVendor -> application?.shopName?.takeIf { it.isNotBlank() } ?: "Your shop, products & pickup"
    else -> "Your shop, products & pickup preparation"
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
                  contentDescription = "Back"
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
                      contentDescription = "Notifications",
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
                      contentDescription = "Refresh Vendor Center",
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
                          contentDescription = tab.label,
                          modifier = Modifier.size(24.dp)
                        )
                      }
                    } else {
                      Icon(
                        imageVector = icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(24.dp)
                      )
                    }
                  },
                  label = {
                    Text(
                      text = tab.label,
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
                title = "Connection problem",
                message = "$errorMessage\n\nNo application or shop data was submitted. Check your internet connection and try again.",
                retryLabel = "Retry connection",
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
                title = "Vendor account restricted",
                subtitle = "Seller access is currently paused for this account."
              )
              StatusPanel(application!!)
              VendorAlertBanner(
                message = application?.lockReason
                  ?: "Vendor tools are temporarily unavailable. Contact JTBazar support for details.",
                isError = true
              )
              SecondaryButton(
                text = "Refresh status",
                icon = Icons.Rounded.Refresh,
                onClick = { refresh() },
                modifier = Modifier.fillMaxWidth()
              )
            }
          }

          isApprovedVendor -> {
            when (activeSubScreen) {
              "product_add" -> VendorListingEditor(
                onCancel = { activeSubScreen = null },
                onSaved = { successMessage ->
                  info = successMessage
                  try {
                    products = VendorService.listProducts()
                    productDeletionRequests = VendorService.listProductDeletionRequests()
                  } catch (e: Exception) {
                    actionErrorMessage = e.message
                      ?: "Listing was submitted, but the product list could not refresh."
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
                    onBack = {
                      editingProduct = null
                      activeSubScreen = if (viewingProduct != null) "product_details" else null
                    },
                    onSaved = {
                      products = VendorService.listProducts()
                      editingProduct = null
                      viewingProduct = null
                      activeSubScreen = null
                      selectedTab = VendorBottomTab.PRODUCTS
                      info = "Product updated and sent for moderation where required."
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
                          info = "Marked ready for JTBazar pickup."
                          refreshOrdersInternal(showLoader = false)
                        } catch (e: Exception) {
                          actionErrorMessage = e.message ?: "Could not update order."
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
                      onOpenNotifications = onOpenNotifications
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
                              "Product is now visible to shoppers."
                            } else {
                              "Product hidden from shoppers."
                            }
                          } catch (e: Exception) {
                            actionErrorMessage = e.message ?: "Visibility update failed."
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
                                info = "Product archived and hidden from your storefront."
                              } catch (e: Exception) {
                                actionErrorMessage = e.message ?: "Archive update failed."
                              } finally {
                                saving = false
                              }
                            }
                          }
                          model.isHidden -> {
                            info = "This product is already archived/hidden from shoppers."
                          }
                          else -> {
                            actionErrorMessage = "Only approved listings can be archived or hidden."
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
                            info = "Marked ready for JTBazar pickup."
                            refreshOrdersInternal(showLoader = false)
                          } catch (e: Exception) {
                            actionErrorMessage = e.message ?: "Could not update order."
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
                  title = "Seller application",
                  subtitle = "Review your current application status and feedback."
                )
                StatusPanel(application!!)
                if (application?.status == "rejected") {
                  VendorAlertBanner(
                    message = "Reason: ${application?.rejectionReason ?: "Please review your application."}",
                    isError = true
                  )
                  AppCard {
                    VendorApplicationResubmissionPanel(application!!) {
                      application = it
                      info = "Application resubmitted."
                    }
                  }
                }
                SecondaryButton(
                  text = "Refresh application status",
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
                    text = "Seller onboarding",
                    tone = StatusTone.PRIMARY,
                    icon = Icons.Rounded.Storefront
                  )
                  Text(
                    text = "Become a JTBazar Vendor",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                  )
                  Text(
                    text = "Submit your shop and identity details. All verification files are stored privately for JTBazar review.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VendorColors.NavyHeaderSubtitle
                  )
                }
              }

              AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
                  SectionHeader(
                    title = "Shop details",
                    subtitle = "Storefront name, description and business type"
                  )
                  VendorOutlinedTextField(
                    value = shopName,
                    onValueChange = { shopName = it },
                    label = "Shop name *",
                    leadingIcon = Icons.Rounded.Storefront
                  )
                  VendorOutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Shop description (at least 10 characters) *",
                    leadingIcon = Icons.Rounded.Description,
                    minLines = 3
                  )
                  VendorTypeDropdown(
                    selectedType = vendorType,
                    onTypeSelected = { vendorType = it }
                  )
                }
              }

              AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
                  SectionHeader(
                    title = "Owner & addresses",
                    subtitle = "Legal verification and contact details"
                  )
                  VendorOutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    label = "Owner full legal name *",
                    leadingIcon = Icons.Rounded.Person
                  )
                  VendorOutlinedTextField(
                    value = cnic,
                    onValueChange = { cnic = it.filter(Char::isDigit).take(13) },
                    label = "CNIC number (13 digits, no dashes) *",
                    leadingIcon = Icons.Rounded.CreditCard,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                  )
                  VendorOutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = "Contact phone (03XXXXXXXXX) *",
                    leadingIcon = Icons.Rounded.Phone,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                  )
                  VendorOutlinedTextField(
                    value = currentAddress,
                    onValueChange = { currentAddress = it },
                    label = "Current address *",
                    leadingIcon = Icons.Rounded.LocationOn
                  )
                  VendorOutlinedTextField(
                    value = fullAddress,
                    onValueChange = { fullAddress = it },
                    label = "Complete address *",
                    leadingIcon = Icons.Rounded.Home,
                    minLines = 2
                  )
                  VendorOutlinedTextField(
                    value = shopLocation,
                    onValueChange = { shopLocation = it },
                    label = "Shop location / landmark details *",
                    leadingIcon = Icons.Rounded.LocationCity,
                    minLines = 2
                  )
                }
              }

              AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
                  SectionHeader(
                    title = "Shop location & picture",
                    subtitle = "Pin your location on the map and add a shop photo"
                  )
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(
                      text = if (latitude.isNotBlank() && longitude.isNotBlank()) {
                        "Selected: $latitude, $longitude"
                      } else {
                        "Choose your shop location on the map."
                      },
                      style = MaterialTheme.typography.bodySmall,
                      color = VendorColors.TextSecondary,
                      modifier = Modifier.weight(1f)
                    )
                    StatusBadge(
                      text = if (latitude.isNotBlank() && longitude.isNotBlank()) "Pinned" else "Required",
                      tone = if (latitude.isNotBlank() && longitude.isNotBlank()) {
                        StatusTone.SUCCESS
                      } else {
                        StatusTone.WARNING
                      }
                    )
                  }
                  SecondaryButton(
                    text = if (latitude.isNotBlank() && longitude.isNotBlank()) {
                      "Change shop location on map"
                    } else {
                      "Choose shop location on map"
                    },
                    icon = Icons.Rounded.Map,
                    onClick = { showMapPicker = true },
                    modifier = Modifier.fillMaxWidth()
                  )

                  HorizontalDivider(color = VendorColors.Divider)

                  shopImageUri?.let { uri ->
                    AsyncImage(
                      model = uri,
                      contentDescription = "Shop picture preview",
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(VendorShapes.input),
                      contentScale = ContentScale.Crop
                    )
                  }
                  SecondaryButton(
                    text = if (shopImageUri == null) "Choose public shop picture" else "Change selected shop picture",
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
                        text = "Verification documents",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VendorColors.TextPrimary
                      )
                      Text(
                        text = "Stored privately for JTBazar admin review only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VendorColors.TextSecondary
                      )
                    }
                  }
                  DocumentButton(
                    label = "Identity / business verification document",
                    uploaded = verificationPath != null
                  ) {
                    pendingDoc = "verification"
                    documentPicker.launch("*/*")
                  }
                  DocumentButton(
                    label = "CNIC front image",
                    uploaded = frontPath != null
                  ) {
                    pendingDoc = "cnic-front"
                    documentPicker.launch("image/*")
                  }
                  DocumentButton(
                    label = "CNIC back image",
                    uploaded = backPath != null
                  ) {
                    pendingDoc = "cnic-back"
                    documentPicker.launch("image/*")
                  }
                }
              }

              PrimaryButton(
                text = if (saving) "Submitting..." else "Submit vendor application",
                icon = Icons.Rounded.Send,
                loading = saving,
                enabled = !saving,
                onClick = {
                  scope.launch {
                    saving = true
                    actionErrorMessage = ""
                    info = ""
                    try {
                      val u = user ?: error("Please sign in again.")
                      val normalizedPhone = normalizePakistaniPhone(phone)
                      require(shopName.trim().length in 2..160) {
                        "Shop name must be between 2 and 160 characters."
                      }
                      require(description.trim().length in 10..2000) {
                        "Shop description must be between 10 and 2,000 characters."
                      }
                      require(ownerName.trim().length >= 3) { "Enter the owner's full legal name." }
                      require(cnic.matches(Regex("^[0-9]{13}$"))) {
                        "Enter a 13-digit CNIC number without dashes."
                      }
                      require(normalizedPhone.matches(Regex("^(03[0-9]{9}|\\+923[0-9]{9})$"))) {
                        "Enter a valid Pakistani phone number, e.g. 03001234567."
                      }
                      require(
                        currentAddress.trim().length >= 3 &&
                          fullAddress.trim().length >= 10 &&
                          shopLocation.trim().length >= 10
                      ) {
                        "Complete your current address, full address and shop landmark (at least 10 characters)."
                      }
                      val lat = latitude.toDoubleOrNull() ?: error("Choose your shop location on the map.")
                      val lng = longitude.toDoubleOrNull() ?: error("Choose your shop location on the map.")
                      require(
                        lat in -90.0..90.0 &&
                          lng in -180.0..180.0 &&
                          !(lat == 0.0 && lng == 0.0)
                      ) {
                        "Map coordinates are invalid. Select your actual shop location."
                      }
                      require(
                        !verificationPath.isNullOrBlank() &&
                          !frontPath.isNullOrBlank() &&
                          !backPath.isNullOrBlank()
                      ) {
                        "Upload all three verification documents before submitting."
                      }
                      require(u.email?.isNotBlank() == true) {
                        "Your account email is missing. Sign in again."
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
                          ?: error("Shop picture could not be read.")
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
                        .put("owner_full_name", ownerName.trim())
                        .put("cnic_number", cnic)
                        .put("shop_location_text", shopLocation.trim())
                        .put("shop_lat", lat)
                        .put("shop_lng", lng)
                        .put("show_shop_on_map", true)
                      application = VendorService.submitApplication(body)
                      info = "Application submitted successfully. JTBazar admin review is pending."
                    } catch (e: Exception) {
                      actionErrorMessage = e.message ?: "Application could not be submitted."
                    } finally {
                      saving = false
                    }
                  }
                },
                modifier = Modifier.fillMaxWidth()
              )

              Text(
                text = "Your application will remain pending until JTBazar admin approves it.",
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
          text = "Request product deletion",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = VendorColors.TextPrimary
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
          Text(
            text = "This sends a request to JTBazar for review. The product will not be deleted immediately.",
            style = MaterialTheme.typography.bodySmall,
            color = VendorColors.TextSecondary
          )
          Text(
            text = target.optString("name", "Product"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = VendorColors.TextPrimary
          )
          VendorOutlinedTextField(
            value = deletionReason,
            onValueChange = { deletionReason = it },
            label = "Reason for deletion *",
            leadingIcon = Icons.Rounded.Description,
            minLines = 3,
            enabled = !saving
          )
        }
      },
      confirmButton = {
        PrimaryButton(
          text = if (saving) "Submitting..." else "Submit request",
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
                info = "Product deletion request submitted for JTBazar review."
                deletionTarget = null
                deletionReason = ""
              } catch (e: Exception) {
                actionErrorMessage = e.message ?: "Deletion request could not be submitted."
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
          Text("Cancel", color = VendorColors.TextSecondary, fontWeight = FontWeight.SemiBold)
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
    mutableStateOf("Move the map and tap your shop location to place the pin.")
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
        title = "Shop location"
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
              locationMessage = "Current device location selected. Adjust the pin if needed."
            } else {
              locationMessage = "Current location unavailable. Move the map and tap your shop location."
            }
          }
          .addOnFailureListener {
            locationMessage = it.message ?: "Could not get current location."
          }
      } catch (_: SecurityException) {
        locationMessage = "Location permission is required to use current location."
      }
    } else {
      locationMessage = "Location permission denied. You can still select a location manually on the map."
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
          text = "Choose shop location",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = VendorColors.TextPrimary
        )
        Text(
          text = "Drag the map and tap the exact shop location. The pin will be saved automatically.",
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
                  locationMessage = "Location pin selected."
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
          text = "Use my current location",
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
                      locationMessage = "Current device location selected. Adjust the pin if needed."
                    } else {
                      locationMessage = "Current location unavailable. Move the map and tap your shop location."
                    }
                  }
                  .addOnFailureListener {
                    locationMessage = it.message ?: "Could not get current location."
                  }
              } catch (_: SecurityException) {
                locationMessage = "Location permission is required."
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
            text = "Cancel",
            onClick = onDismiss,
            modifier = Modifier.weight(1f)
          )
          PrimaryButton(
            text = "Use location",
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
      text = if (uploaded) "$label uploaded" else "Upload $label",
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
    }.getOrElse { "Recently" }
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
          text = "Pending review",
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
          text = "Your application is under verification by the JTBazar marketplace team.",
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
          text = "Application Under Review",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = VendorColors.TextPrimary
        )
        Text(
          text = "Thank you for applying to become a JTBazar seller. We are currently reviewing your documents.",
          style = MaterialTheme.typography.bodySmall,
          color = VendorColors.TextSecondary,
          textAlign = TextAlign.Center
        )
      }
    }

    AppCard {
      Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
        Text(
          text = "Application progress",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = VendorColors.TextPrimary
        )
        VendorProgressStep(
          icon = Icons.Rounded.CheckCircle,
          title = "Application received",
          description = "Your shop details have been submitted successfully.",
          active = true,
          iconTint = VendorColors.SuccessGreen,
          iconBg = VendorColors.SuccessGreenBg
        )
        VendorProgressStep(
          icon = Icons.Rounded.Schedule,
          title = "Verification in progress",
          description = "Our marketplace team is reviewing your shop information.",
          active = true,
          iconTint = VendorColors.PrimaryOrange,
          iconBg = VendorColors.PrimaryOrangeTint
        )
        VendorProgressStep(
          icon = Icons.Rounded.VerifiedUser,
          title = "Seller access",
          description = "We'll notify you by email when your seller dashboard is ready.",
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
          text = "If we need any additional information or your application is not approved, we will notify you by email.",
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
          text = "Submitted",
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
      text = "Refresh application status",
      icon = Icons.Rounded.Refresh,
      loading = refreshing,
      enabled = !refreshing,
      onClick = onRefresh,
      modifier = Modifier.fillMaxWidth()
    )
    Text(
      text = "Your seller tools will become available here once your application is approved.",
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
          text = "Application status",
          style = MaterialTheme.typography.bodySmall,
          color = VendorColors.TextSecondary
        )
      }
      StatusBadge(
        text = app.status.uppercase(),
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
      "JTBazar server ka address abhi resolve nahi ho raha. Ye DNS ya internet network ka masla ho sakta hai. Wi-Fi/mobile data check karein aur dobara koshish karein."
    details.contains("timeout") || details.contains("timed out") ->
      "JTBazar server se response nahi mila. Internet connection check karke dobara koshish karein."
    details.contains("failed to connect") || details.contains("network is unreachable") ->
      "Internet connection available nahi lag raha. Network check karke dobara koshish karein."
    else -> "Vendor information load nahi ho saki. Dobara koshish karein."
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
  val html = """<!doctype html><html><head><meta charset="utf-8"><style>
    body{font-family:Arial,sans-serif;padding:24px;color:#222}header{display:flex;justify-content:space-between;border-bottom:2px solid #222;padding-bottom:14px}
    h1{margin:6px 0;font-size:24px}small{color:#555}table{width:100%;border-collapse:collapse;margin-top:24px}
    th,td{text-align:left;border:1px solid #bbb;padding:10px;font-size:13px}th{background:#f1f1f1}
    footer{margin-top:28px;font-size:11px;color:#555}
  </style></head><body><header><div><small>Product packing slip</small><h1>JTBazar</h1></div>
  <div><strong>${esc(first.optString("order_number", ""))}</strong><br><small>${esc(first.optString("order_created_at", first.optString("created_at", "")))}</small></div></header>
  <table><thead><tr><th>Product</th><th>SKU</th><th>Variant</th><th>Qty</th></tr></thead><tbody>$rows</tbody></table>
  <footer>Product and quantity summary only. Customer contact and delivery details are managed by the marketplace team.</footer></body></html>"""
  val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
  val webView = WebView(context)
  webView.settings.javaScriptEnabled = false
  webView.webViewClient = object : WebViewClient() {
    override fun onPageFinished(view: WebView, url: String?) {
      val adapter = view.createPrintDocumentAdapter(
        "JTBazar-Packing-Slip-${first.optString("order_number", "order")}"
      )
      printManager.print("JTBazar product packing slip", adapter, PrintAttributes.Builder().build())
    }
  }
  webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
}
