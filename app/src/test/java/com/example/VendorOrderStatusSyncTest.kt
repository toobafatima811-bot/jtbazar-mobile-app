package com.example

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseSession
import com.example.data.supabase.SupabaseUser
import com.example.data.supabase.VendorOrderStatus
import com.example.data.supabase.VendorService
import com.example.ui.screens.VendorOrderDetailsScreen
import com.example.ui.screens.VendorOrderGroupUiModel
import com.example.ui.screens.VendorOrderLineItemUiModel
import com.example.ui.screens.VendorOrdersContent
import com.example.ui.screens.VendorOrdersUiState
import com.example.ui.screens.VendorOverviewContent
import com.example.ui.screens.VendorOverviewUiState
import com.example.ui.screens.VendorSectionState
import com.example.ui.screens.filterAndPaginateVendorOrders
import com.example.ui.screens.mapOrdersToUiGroups
import com.example.ui.theme.VendorCenterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
@OptIn(ExperimentalCoroutinesApi::class)
class VendorOrderStatusSyncTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  companion object {
    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterClass
    @JvmStatic
    fun tearDownClass() {
      Dispatchers.resetMain()
    }
  }

  @After
  fun tearDown() {
    SupabaseConfig.projectUrlOverride = null
    SupabaseClient.clearSession()
  }

  private data class CapturedRequest(
    val method: String,
    val target: String,
    val body: String
  )

  private fun startRoutingStubServer(
    handler: (method: String, target: String, body: String) -> Pair<Int, String>
  ): Pair<ServerSocket, CopyOnWriteArrayList<CapturedRequest>> {
    val server = ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"))
    val captured = CopyOnWriteArrayList<CapturedRequest>()
    val ready = CountDownLatch(1)

    thread(isDaemon = true) {
      ready.countDown()
      while (!server.isClosed) {
        try {
          val socket = server.accept()
          thread(isDaemon = true) {
            socket.use { s ->
              val input = BufferedInputStream(s.getInputStream())
              val headerBytes = ByteArrayOutputStream()
              var state = 0
              while (true) {
                val b = input.read()
                if (b == -1) break
                headerBytes.write(b)
                state = when {
                  state == 0 && b == '\r'.code -> 1
                  state == 1 && b == '\n'.code -> 2
                  state == 2 && b == '\r'.code -> 3
                  state == 3 && b == '\n'.code -> 4
                  b == '\r'.code -> 1
                  else -> 0
                }
                if (state == 4) break
              }
              val rawHeaders = headerBytes.toString(Charsets.UTF_8.name())
              if (rawHeaders.isBlank()) return@use
              val lines = rawHeaders.split("\r\n").filter { it.isNotEmpty() }
              val parts = lines.firstOrNull()?.split(" ") ?: emptyList()
              val method = parts.getOrElse(0) { "" }
              val target = parts.getOrElse(1) { "" }
              val contentLength = lines.drop(1)
                .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                ?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0

              val bodyBuf = ByteArray(contentLength)
              var readTotal = 0
              while (readTotal < contentLength) {
                val n = input.read(bodyBuf, readTotal, contentLength - readTotal)
                if (n <= 0) break
                readTotal += n
              }
              val bodyStr = String(bodyBuf, 0, readTotal, Charsets.UTF_8)
              captured.add(CapturedRequest(method = method, target = target, body = bodyStr))

              val (statusCode, responseBody) = handler(method, target, bodyStr)
              val respBytes = responseBody.toByteArray(Charsets.UTF_8)
              val statusText = if (statusCode in 200..299) "OK" else "Bad Request"
              val httpResp = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${respBytes.size}\r\n" +
                "Connection: close\r\n\r\n"
              s.getOutputStream().write(httpResp.toByteArray(Charsets.UTF_8))
              s.getOutputStream().write(respBytes)
              s.getOutputStream().flush()
            }
          }
        } catch (_: Exception) {
          if (server.isClosed) break
        }
      }
    }
    ready.await(2, TimeUnit.SECONDS)
    return server to captured
  }

  private fun seedSession() {
    SupabaseClient.setSession(
      SupabaseSession(
        accessToken = "vendor-jwt-token",
        refreshToken = "refresh-token",
        user = SupabaseUser(id = "vendor-user-1", email = "seller@jtbazar.pk", fullName = "Multan Crafts")
      )
    )
  }

  @Test
  fun resolveOrderStatusNeverStaysStuckOnPendingWhenAdminOrPickupUpdatesStatus() {
    val scenarios = listOf(
      "confirmed" to VendorOrderStatus.CONFIRMED,
      "processing" to VendorOrderStatus.PROCESSING,
      "shipped" to VendorOrderStatus.SHIPPED,
      "delivered" to VendorOrderStatus.DELIVERED,
      "not_delivered" to VendorOrderStatus.NOT_DELIVERED,
      "cancelled" to VendorOrderStatus.CANCELLED
    )

    scenarios.forEach { (adminOrderStatus, expected) ->
      val row = JSONObject().apply {
        put("id", "item-1")
        put("order_id", "ord-$adminOrderStatus")
        put("order_number", "JT-$adminOrderStatus")
        put("vendor_fulfillment_status", "pending")
        put("order_status", adminOrderStatus)
      }
      val resolved = VendorOrderStatus.resolveGroupStatus(listOf(row))
      assertEquals(
        "Admin status '$adminOrderStatus' must override stale vendor_fulfillment_status='pending'",
        expected,
        resolved
      )
    }

    // Pickup status = ready_for_pickup while item fulfillment still says pending
    val pickupRow = JSONObject().apply {
      put("id", "item-pickup")
      put("order_id", "ord-pickup")
      put("vendor_fulfillment_status", "pending")
      put("order_status", "confirmed")
      put("pickup_status", "ready_for_pickup")
    }
    assertEquals(VendorOrderStatus.READY_FOR_PICKUP, VendorOrderStatus.resolveGroupStatus(listOf(pickupRow)))

    // Cancelled order beats even shipped/processing item states
    val cancelledRow = JSONObject().apply {
      put("id", "item-cancel")
      put("order_id", "ord-cancel")
      put("vendor_fulfillment_status", "processing")
      put("order_status", "cancelled")
    }
    assertEquals(VendorOrderStatus.CANCELLED, VendorOrderStatus.resolveGroupStatus(listOf(cancelledRow)))
  }

  @Test
  fun listAllOrderItemsReconciledMergesLiveOrdersPickupsAndOrderItemsAndReflectsMarkReadyUpdate() = runBlocking {
    seedSession()
    var markedReady = false

    val (server, captured) = startRoutingStubServer { _, target, body ->
      when {
        target.contains("/rest/v1/rpc/vendor_order_items_page") -> {
          val offset = runCatching { JSONObject(body).optInt("p_offset", 0) }.getOrDefault(0)
          if (offset > 0) {
            200 to "[]"
          } else {
            val rows = JSONArray().apply {
              // Order 1 (2 items): vendor_fulfillment_status stuck on 'pending', admin moved orders.order_status -> 'processing' (until markedReady -> pickup 'ready_for_pickup')
              put(JSONObject().apply {
                put("id", "item-1a")
                put("order_id", "ord-1")
                put("order_number", "JT-1001")
                put("product_name_snapshot", "Multani Blue Vase")
                put("quantity", 2)
                put("sku_snapshot", "MC-01")
                put("vendor_fulfillment_status", if (markedReady) "shipped" else "pending")
                put("created_at", "2026-11-15T10:00:00Z")
                put("total_orders", 3)
              })
              put(JSONObject().apply {
                put("id", "item-1b")
                put("order_id", "ord-1")
                put("order_number", "JT-1001")
                put("product_name_snapshot", "Ceramic Bowl")
                put("quantity", 1)
                put("sku_snapshot", "MC-02")
                put("vendor_fulfillment_status", "pending")
                put("created_at", "2026-11-15T10:00:00Z")
                put("total_orders", 3)
              })
              // Order 2: Delivered order
              put(JSONObject().apply {
                put("id", "item-2")
                put("order_id", "ord-2")
                put("order_number", "JT-1002")
                put("product_name_snapshot", "Handwoven Rug")
                put("quantity", 1)
                put("sku_snapshot", "HR-05")
                put("vendor_fulfillment_status", "pending")
                put("created_at", "2026-11-14T08:00:00Z")
                put("total_orders", 3)
              })
              // Order 3: Confirmed order (needs prep)
              put(JSONObject().apply {
                put("id", "item-3")
                put("order_id", "ord-3")
                put("order_number", "JT-1003")
                put("product_name_snapshot", "Copper Tray")
                put("quantity", 3)
                put("sku_snapshot", "CT-09")
                put("vendor_fulfillment_status", "pending")
                put("created_at", "2026-11-16T09:00:00Z")
                put("total_orders", 3)
              })
            }
            200 to rows.toString()
          }
        }
        target.contains("/rest/v1/rpc/vendor_mark_order_ready") -> {
          markedReady = true
          200 to "true"
        }
        target.startsWith("/rest/v1/orders?") -> {
          200 to JSONArray().apply {
            put(JSONObject().apply {
              put("id", "ord-1")
              put("order_status", if (markedReady) "processing" else "processing")
              put("confirmation_status", "confirmed")
              put("updated_at", "2026-11-16T11:00:00Z")
            })
            put(JSONObject().apply {
              put("id", "ord-2")
              put("order_status", "delivered")
              put("confirmation_status", "confirmed")
              put("delivered_at", "2026-11-15T15:00:00Z")
            })
            put(JSONObject().apply {
              put("id", "ord-3")
              put("order_status", "pending")
              put("confirmation_status", "confirmed")
            })
          }.toString()
        }
        target.startsWith("/rest/v1/vendor_order_pickups?") -> {
          200 to JSONArray().apply {
            if (markedReady) {
              put(JSONObject().apply {
                put("order_id", "ord-1")
                put("status", "ready_for_pickup")
                put("ready_at", "2026-11-16T11:05:00Z")
                put("vendor_note", "Packed safely")
              })
            }
          }.toString()
        }
        target.startsWith("/rest/v1/order_items?") -> {
          200 to JSONArray().apply {
            put(JSONObject().apply {
              put("id", "item-1a")
              put("order_id", "ord-1")
              put("vendor_fulfillment_status", "processing")
            })
          }.toString()
        }
        else -> 200 to "[]"
      }
    }

    try {
      SupabaseConfig.projectUrlOverride = "http://127.0.0.1:${server.localPort}"

      // Initial load: admin updated ord-1 to Processing, ord-2 to Delivered, ord-3 is Confirmed (0 Pending!)
      val initialGroups = mapOrdersToUiGroups(VendorService.listAllOrderItemsReconciled())
      assertEquals(3, initialGroups.size)

      val initialSummary = filterAndPaginateVendorOrders(
        allGroups = initialGroups,
        searchQuery = "",
        selectedStatus = "all",
        page = 0
      )
      assertEquals(3, initialSummary.totalAllOrders)
      assertEquals(4, initialSummary.totalOrderItemsCount) // 2 + 1 + 1 items across 3 distinct orders
      assertEquals(0, initialSummary.statusCounts[VendorOrderStatus.PENDING])
      assertEquals(1, initialSummary.statusCounts[VendorOrderStatus.CONFIRMED])
      assertEquals(1, initialSummary.statusCounts[VendorOrderStatus.PROCESSING])
      assertEquals(1, initialSummary.statusCounts[VendorOrderStatus.DELIVERED])
      assertEquals(2, initialSummary.itemsToPrepareCount) // Confirmed + Processing

      // Verify multi-item ord-1 resolved to Processing (not Pending, not counted twice)
      val ord1 = initialGroups.first { it.orderId == "ord-1" }
      assertEquals(VendorOrderStatus.PROCESSING, ord1.canonicalStatus)
      assertEquals(2, ord1.items.size)

      // Vendor marks ord-1 ready for pickup -> re-fetch immediately updates counts & status
      VendorService.markOrderReady("ord-1", "Packed safely")
      val updatedGroups = mapOrdersToUiGroups(VendorService.listAllOrderItemsReconciled())
      val updatedSummary = filterAndPaginateVendorOrders(
        allGroups = updatedGroups,
        searchQuery = "",
        selectedStatus = "ready_for_pickup",
        page = 0
      )
      assertEquals(0, updatedSummary.statusCounts[VendorOrderStatus.PROCESSING])
      assertEquals(1, updatedSummary.statusCounts[VendorOrderStatus.READY_FOR_PICKUP])
      assertEquals(1, updatedSummary.itemsToPrepareCount) // only ord-3 remains Confirmed
      assertEquals(1, updatedSummary.filteredOrders.size)
      assertEquals("ord-1", updatedSummary.filteredOrders.first().orderId)
      assertEquals("Packed safely", updatedSummary.filteredOrders.first().vendorNote)
      assertTrue(captured.any { it.target.contains("vendor_mark_order_ready") })
    } finally {
      server.close()
    }
  }

  @Test
  fun overviewAndOrdersScreensDisplayAccurateStatusBadgesCountsAndOpenOrderDetails() {
    val processingOrder = VendorOrderGroupUiModel(
      orderId = "ord-proc",
      orderNumber = "JT-5501",
      createdAt = "2026-11-16T08:00:00Z",
      status = VendorOrderStatus.PROCESSING.key,
      canonicalStatus = VendorOrderStatus.PROCESSING,
      orderStatusLabel = "Processing",
      confirmationStatusLabel = "Confirmed",
      pickupStatusLabel = "Pending",
      trackingNumber = "TRK-991",
      items = listOf(
        VendorOrderLineItemUiModel(
          id = "i-1",
          productName = "Handloom Shawl",
          quantity = "1",
          sku = "HS-01",
          variantSummary = "Color: Ivory",
          unitPrice = "2500",
          lineTotal = "2500",
          canonicalStatus = VendorOrderStatus.PROCESSING,
          rawJson = JSONObject()
        )
      ),
      rawItems = emptyList()
    )
    val readyOrder = VendorOrderGroupUiModel(
      orderId = "ord-ready",
      orderNumber = "JT-5502",
      createdAt = "2026-11-15T12:00:00Z",
      status = VendorOrderStatus.READY_FOR_PICKUP.key,
      canonicalStatus = VendorOrderStatus.READY_FOR_PICKUP,
      orderStatusLabel = "Ready for Pickup",
      confirmationStatusLabel = "Confirmed",
      pickupStatusLabel = "Ready for Pickup",
      items = listOf(
        VendorOrderLineItemUiModel(
          id = "i-2",
          productName = "Brass Lamp",
          quantity = "2",
          sku = "BL-02",
          variantSummary = "—",
          canonicalStatus = VendorOrderStatus.READY_FOR_PICKUP,
          rawJson = JSONObject()
        )
      ),
      rawItems = emptyList()
    )

    val allGroups = listOf(processingOrder, readyOrder)
    val filterState = filterAndPaginateVendorOrders(allGroups, "", "all", 0)
    val selectedOrder = mutableStateOf<VendorOrderGroupUiModel?>(null)
    val selectedFilter = mutableStateOf("all")

    composeTestRule.setContent {
      VendorCenterTheme {
        val currentDetails = selectedOrder.value
        if (currentDetails != null) {
          VendorOrderDetailsScreen(
            group = currentDetails,
            isBusy = false,
            actionError = "",
            infoMessage = "",
            onBack = { selectedOrder.value = null },
            onRefresh = {},
            onPrintPackingSlip = {},
            onMarkReady = {},
            onDismissAlert = {}
          )
        } else {
          VendorOverviewContent(
            uiState = VendorOverviewUiState(
              shopName = "Lahore Craft Guild",
              isSellerActive = true,
              totalListings = 12,
              totalOrdersCount = filterState.totalAllOrders,
              orderItemsCount = filterState.totalOrderItemsCount,
              itemsToPrepareCount = filterState.itemsToPrepareCount,
              statusCounts = filterState.statusCounts,
              unreadNotificationsCount = 3,
              recentOrders = allGroups,
              recentProductsState = VendorSectionState.Empty
            ),
            onRefresh = {},
            onAddProduct = {},
            onViewAllProducts = {},
            onOpenProductDetails = {},
            onSelectOrderStatus = { selectedFilter.value = it.key },
            onOpenOrderDetails = { selectedOrder.value = it }
          )
        }
      }
    }

    // Verify unread notification banner & live dashboard status counts render cleanly
    composeTestRule.onNodeWithTag("vendor_dashboard_notifications_banner").assertIsDisplayed()
    composeTestRule.onNodeWithText("3 unread notification(s)").assertIsDisplayed()
    composeTestRule.onNodeWithTag("vendor_dashboard_status_processing").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("vendor_dashboard_status_ready_for_pickup").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("vendor_dashboard_status_not_delivered").performScrollTo().assertIsDisplayed()

    // Ensure raw database status keys like ready_for_pickup / not_delivered are NEVER displayed to user
    assertTrue(composeTestRule.onAllNodesWithText("ready_for_pickup").fetchSemanticsNodes().isEmpty())
    assertTrue(composeTestRule.onAllNodesWithText("not_delivered").fetchSemanticsNodes().isEmpty())

    // Clicking Processing status tile updates selected filter
    composeTestRule.onNodeWithTag("vendor_dashboard_status_processing").performClick()
    assertEquals("processing", selectedFilter.value)

    // Clicking recent order opens Order Details showing timeline, tracking number & Mark ready button
    composeTestRule.onNodeWithTag("vendor_overview_list")
      .performScrollToNode(hasTestTag("vendor_dashboard_recent_order_ord-proc"))
    composeTestRule.onNodeWithTag("vendor_dashboard_recent_order_ord-proc").performClick()
    composeTestRule.onNodeWithTag("vendor_order_details_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("vendor_order_details_status_badge").assertIsDisplayed()
    composeTestRule.onNodeWithTag("vendor_order_details_screen")
      .performScrollToNode(hasText("TRK-991"))
    composeTestRule.onNodeWithText("TRK-991").assertIsDisplayed()
    composeTestRule.onNodeWithTag("vendor_order_details_screen")
      .performScrollToNode(hasTestTag("vendor_order_details_mark_ready"))
    composeTestRule.onNodeWithTag("vendor_order_details_mark_ready").assertIsDisplayed()
  }

  @Test
  fun vendorOrdersTabHidesMarkReadyOnCompletedOrReadyOrdersAndDisplaysLiveFilterCounts() {
    val readyOrder = VendorOrderGroupUiModel(
      orderId = "ord-rdy",
      orderNumber = "JT-8801",
      createdAt = "2026-11-16T08:00:00Z",
      status = VendorOrderStatus.READY_FOR_PICKUP.key,
      canonicalStatus = VendorOrderStatus.READY_FOR_PICKUP,
      items = listOf(
        VendorOrderLineItemUiModel(
          id = "i-r1",
          productName = "Copper Kettle",
          quantity = "1",
          sku = "CK-1",
          variantSummary = "—",
          canonicalStatus = VendorOrderStatus.READY_FOR_PICKUP,
          rawJson = JSONObject()
        )
      ),
      rawItems = emptyList()
    )

    val counts = mapOf(
      VendorOrderStatus.PENDING to 0,
      VendorOrderStatus.CONFIRMED to 2,
      VendorOrderStatus.PROCESSING to 1,
      VendorOrderStatus.READY_FOR_PICKUP to 1,
      VendorOrderStatus.SHIPPED to 3,
      VendorOrderStatus.DELIVERED to 4,
      VendorOrderStatus.NOT_DELIVERED to 0,
      VendorOrderStatus.CANCELLED to 1
    )

    composeTestRule.setContent {
      VendorCenterTheme {
        VendorOrdersContent(
          uiState = VendorOrdersUiState(
            selectedStatus = "all",
            totalOrders = 1,
            totalAllOrders = 12,
            statusCounts = counts,
            ordersState = VendorSectionState.Content(listOf(readyOrder))
          ),
          onSearchChange = {},
          onSearchSubmit = {},
          onStatusSelect = {},
          onPrevPage = {},
          onNextPage = {},
          onPrintPackingSlip = {},
          onMarkReady = {},
          onRefresh = {},
          onDismissAlert = {}
        )
      }
    }

    composeTestRule.onNodeWithText("All (12)").assertIsDisplayed()
    composeTestRule.onNodeWithText("Confirmed (2)").assertIsDisplayed()
    composeTestRule.onNodeWithTag("vendor_orders_list")
      .performScrollToNode(hasTestTag("vendor_order_card_ord-rdy"))
    composeTestRule.onNodeWithTag("vendor_order_card_ord-rdy").assertIsDisplayed()
    composeTestRule.onNodeWithTag("vendor_order_status_badge_ord-rdy", useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule.onNodeWithText("Ready for Pickup", useUnmergedTree = true).assertIsDisplayed()

    // Mark Ready button must NOT render when order is already Ready for Pickup
    assertFalse(
      composeTestRule.onAllNodesWithText("Mark ready for pickup", useUnmergedTree = true)
        .fetchSemanticsNodes().isNotEmpty()
    )
  }
}
