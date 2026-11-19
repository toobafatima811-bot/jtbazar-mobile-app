package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.data.model.CategoryItem
import com.example.data.model.Product
import com.example.data.model.ShopRecord
import com.example.data.supabase.AppErrorMapper
import com.example.data.supabase.ErrorContext
import com.example.data.supabase.PAKISTAN_ZONE_ID
import com.example.data.supabase.ShopDayOfWeek
import com.example.data.supabase.ShopScheduleInfo
import com.example.data.supabase.SupabaseApiService
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseSession
import com.example.data.supabase.SupabaseUser
import com.example.data.supabase.VendorApplication
import com.example.data.supabase.VendorShopCatalogFilter
import com.example.data.supabase.WeeklyShopHours
import com.example.localization.AppLanguage
import com.example.localization.AppLanguageManager
import com.example.ui.screens.PublicShopDetailScreen
import com.example.ui.screens.VendorShopSettingsScreen
import com.example.ui.theme.JTBazarTheme
import com.example.ui.theme.VendorCenterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
@OptIn(ExperimentalCoroutinesApi::class)
class VendorShopHoursAndCategoriesTest {

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
    SupabaseApiService.clearCaches()
    AppLanguageManager.setLanguageForTest(AppLanguage.ENGLISH)
  }

  private data class CapturedRequest(
    val method: String,
    val target: String,
    val body: String
  )

  private fun startStubServer(
    handler: (method: String, target: String, body: String) -> Pair<Int, String>
  ): Pair<ServerSocket, CopyOnWriteArrayList<CapturedRequest>> {
    val server = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
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

  private fun seedVendorSession() {
    SupabaseClient.setSession(
      SupabaseSession(
        accessToken = "vendor-jwt-token",
        refreshToken = "refresh-token",
        user = SupabaseUser(id = "vendor-user-1", email = "seller@jtbazar.pk", fullName = "Multan Crafts")
      )
    )
  }

  private fun validVendorApplication(
    shopIsOpen: Boolean = true,
    shopHours: WeeklyShopHours = WeeklyShopHours.defaults()
  ) = VendorApplication(
    id = "seller-1",
    status = "approved",
    shopName = "Multan Craft House",
    slug = "multan-craft-house",
    description = "Authentic hand-glazed blue pottery and crafts from Multan.",
    phone = "03001234567",
    currentAddress = "Gulberg III, Lahore",
    completeAddress = "Shop 14, Main Boulevard, Gulberg III, Lahore",
    contactEmail = "seller@jtbazar.pk",
    vendorType = "registered_business",
    ownerFullName = "Muhammad Usman",
    cnicNumber = "3520212345678",
    shopLocationText = "Near Liberty Market Roundabout",
    shopLat = 31.5112,
    shopLng = 74.3441,
    showShopOnMap = true,
    shopIsOpen = shopIsOpen,
    shopHours = shopHours,
    documentPath = "vendor-user-1/verification.pdf",
    cnicFrontPath = "vendor-user-1/cnic-front.jpg",
    cnicBackPath = "vendor-user-1/cnic-back.jpg"
  )

  private fun karachiInstant(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
    ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of("Asia/Karachi")).toInstant()

  @Test
  fun defaultScheduleUsesPakistanTimezoneAndExpectedMonToSunDefaults() {
    assertEquals(ZoneId.of("Asia/Karachi"), PAKISTAN_ZONE_ID)
    val defaults = WeeklyShopHours.parse(null)

    listOf(
      ShopDayOfWeek.MONDAY,
      ShopDayOfWeek.TUESDAY,
      ShopDayOfWeek.WEDNESDAY,
      ShopDayOfWeek.THURSDAY,
      ShopDayOfWeek.FRIDAY
    ).forEach { day ->
      val d = defaults.forDay(day)
      assertTrue("${day.key} must be open by default", d.isOpen)
      assertEquals("09:00", d.openTime)
      assertEquals("22:00", d.closeTime)
    }

    val sat = defaults.forDay(ShopDayOfWeek.SATURDAY)
    assertTrue(sat.isOpen)
    assertEquals("10:00", sat.openTime)
    assertEquals("23:00", sat.closeTime)

    val sun = defaults.forDay(ShopDayOfWeek.SUNDAY)
    assertFalse("Sunday must be closed by default", sun.isOpen)

    val info = ShopScheduleInfo(sellerId = "seller-1", shopIsOpen = true, weeklyHours = defaults)
    // Monday 2026-11-16
    assertTrue(info.evaluateAt(karachiInstant(2026, 11, 16, 9, 0)).isOpen)
    assertTrue(info.evaluateAt(karachiInstant(2026, 11, 16, 21, 59)).isOpen)
    assertFalse(info.evaluateAt(karachiInstant(2026, 11, 16, 8, 59)).isOpen)
    assertFalse(info.evaluateAt(karachiInstant(2026, 11, 16, 22, 0)).isOpen)

    // Saturday 2026-11-21
    assertTrue(info.evaluateAt(karachiInstant(2026, 11, 21, 22, 30)).isOpen)
    assertFalse(info.evaluateAt(karachiInstant(2026, 11, 21, 9, 30)).isOpen)

    // Sunday 2026-11-22 (closed by default)
    val sunStatus = info.evaluateAt(karachiInstant(2026, 11, 22, 14, 0))
    assertFalse(sunStatus.isOpen)
    assertEquals("Closed", sunStatus.badgeLabel)

    // Manual override overrides even within scheduled Monday hours
    val closedManually = info.copy(shopIsOpen = false).evaluateAt(karachiInstant(2026, 11, 16, 14, 0))
    assertFalse(closedManually.isOpen)
    assertFalse(closedManually.shopIsOpen)
    assertEquals("Closed", closedManually.badgeLabel)

    // Invalid time range detection
    val invalid = defaults.withDayUpdated(ShopDayOfWeek.MONDAY, isOpen = true, openTime = "18:00", closeTime = "10:00")
    assertEquals(listOf(ShopDayOfWeek.MONDAY), invalid.invalidDays())
  }

  @Test
  fun vendorCanEditMondayOpeningClosingHoursToggleEmergencyCloseAndSave() {
    seedVendorSession()
    val patchedPayloads = CopyOnWriteArrayList<JSONObject>()

    val (server, captured) = startStubServer { method, target, body ->
      when {
        method == "PATCH" && target.contains("seller_profiles") -> {
          patchedPayloads.add(JSONObject(body))
          200 to "[{\"id\":\"seller-1\"}]"
        }
        target.contains("list_public_shops") -> 200 to "[]"
        else -> 200 to "[]"
      }
    }

    try {
      SupabaseConfig.projectUrlOverride = "http://127.0.0.1:${server.localPort}"
      var savedCallbackCount = 0

      composeTestRule.setContent {
        VendorCenterTheme {
          VendorShopSettingsScreen(
            application = validVendorApplication(),
            accountEmail = "seller@jtbazar.pk",
            onSaved = { savedCallbackCount++ }
          )
        }
      }

      // 1. Scroll to Weekly Hours & Sunday row -> Default Sunday row shows Closed
      composeTestRule.onNodeWithTag("vendor_shop_settings_list")
        .performScrollToNode(hasTestTag("vendor_hours_day_sunday"))
      composeTestRule.onNodeWithTag("vendor_hours_day_sunday").performScrollTo().assertIsDisplayed()
      composeTestRule.onNodeWithText("Closed all day on Sunday").performScrollTo().assertIsDisplayed()

      // 2. Edit Monday opening & closing times
      composeTestRule.onNodeWithTag("vendor_hours_open_monday").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("shop_time_slots_list")
        .performScrollToNode(hasTestTag("shop_time_slot_10:00"))
      composeTestRule.onNodeWithTag("shop_time_slot_10:00").performClick()
      composeTestRule.waitForIdle()

      composeTestRule.onNodeWithTag("vendor_hours_close_monday").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("shop_time_slots_list")
        .performScrollToNode(hasTestTag("shop_time_slot_20:00"))
      composeTestRule.onNodeWithTag("shop_time_slot_20:00").performClick()
      composeTestRule.waitForIdle()

      // 3. Toggle emergency close OFF (shop_is_open = false)
      composeTestRule.onNodeWithTag("vendor_shop_is_open_switch").performScrollTo().performClick()
      composeTestRule.waitForIdle()

      // 4. Save settings
      composeTestRule.onNodeWithTag("vendor_shop_settings_save_button").performClick()
      composeTestRule.waitUntil(timeoutMillis = 5_000L) { patchedPayloads.isNotEmpty() }

      val savedJson = patchedPayloads.first()
      assertFalse("Emergency close switch should persist shop_is_open = false", savedJson.getBoolean("shop_is_open"))
      val savedHours = savedJson.getJSONObject("shop_hours")
      val mon = savedHours.getJSONObject("monday")
      assertTrue(mon.getBoolean("is_open"))
      assertEquals("10:00", mon.getString("open"))
      assertEquals("20:00", mon.getString("close"))
      val sun = savedHours.getJSONObject("sunday")
      assertFalse(sun.getBoolean("is_open"))
      assertTrue(captured.any { it.method == "PATCH" && it.target.contains("seller_profiles") })
    } finally {
      server.close()
    }
  }

  @Test
  fun publicShopDisplaysClosedWhenManuallyClosedAndAllowsBrowsingProducts() {
    val mondayAfternoonPkt = karachiInstant(2026, 11, 16, 14, 0) // Mon 2:00 PM PKT
    val stubScheduleJson = JSONObject().apply {
      put("seller_id", "seller-1")
      put("shop_is_open", false)
      put("timezone", "Asia/Karachi")
      put("shop_hours", WeeklyShopHours.defaults().toJson())
    }.toString()

    val (server, _) = startStubServer { _, target, _ ->
      when {
        target.contains("/rest/v1/rpc/get_public_shop_schedule") -> 200 to stubScheduleJson
        target.contains("/rest/v1/categories") -> 200 to JSONArray().apply {
          put(JSONObject().put("id", "cat-pottery").put("name", "Pottery").put("display_order", 1))
        }.toString()
        target.contains("/rest/v1/products?") -> 200 to JSONArray().apply {
          put(JSONObject()
            .put("id", "prod-1")
            .put("seller_id", "seller-1")
            .put("category_id", "cat-pottery")
            .put("name", "Blue Pottery Bowl")
            .put("price", 1500)
            .put("publication_status", "published")
            .put("moderation_status", "approved")
            .put("stock_verification_status", "verified"))
        }.toString()
        target.contains("/rest/v1/rpc/get_public_inventory") -> 200 to JSONArray().apply {
          put(JSONObject().put("product_id", "prod-1").put("quantity", 5).put("in_stock", true))
        }.toString()
        target.contains("/rest/v1/rpc/get_public_availability") -> 200 to "[]"
        else -> 200 to "[]"
      }
    }

    try {
      SupabaseConfig.projectUrlOverride = "http://127.0.0.1:${server.localPort}"
      val shop = ShopRecord(id = "seller-1", name = "Multan Craft House", city = "Multan", isVerified = true)

      composeTestRule.setContent {
        JTBazarTheme {
          PublicShopDetailScreen(
            shop = shop,
            fallbackProducts = emptyList(),
            onBack = {},
            onProductClick = {},
            nowProvider = { mondayAfternoonPkt },
            scheduleRefreshIntervalMs = 60_000L
          )
        }
      }

      composeTestRule.waitUntil(timeoutMillis = 5_000L) {
        composeTestRule.onAllNodesWithText("Shop temporarily closed").fetchSemanticsNodes().isNotEmpty()
      }

      composeTestRule.onNodeWithTag("public_shop_open_status_badge")
        .assertTextContains("Closed")
      composeTestRule.onNodeWithTag("public_shop_closed_banner").assertIsDisplayed()

      // Weekly hours show Sunday Closed by default
      composeTestRule.onNodeWithTag("public_shop_detail_list")
        .performScrollToNode(hasTestTag("public_shop_hours_row_sunday"))
      composeTestRule.onNodeWithTag("public_shop_hours_row_sunday")
        .assertTextContains("Closed", substring = true)

      // Products remain visible and browsable even while shop is closed
      composeTestRule.waitUntil(timeoutMillis = 5_000L) {
        runCatching {
          composeTestRule.onNodeWithTag("public_shop_detail_list")
            .performScrollToNode(androidx.compose.ui.test.hasText("Blue Pottery Bowl"))
          true
        }.getOrDefault(false)
      }
      composeTestRule.onNodeWithText("Blue Pottery Bowl").assertIsDisplayed()
    } finally {
      server.close()
    }
  }

  @Test
  fun publicShopDisplaysOpenWhenReopenedAndFiltersCategoriesHidingEmptyOnes() {
    val mondayAfternoonPkt = karachiInstant(2026, 11, 16, 14, 0) // Mon 2:00 PM PKT
    val stubScheduleJson = JSONObject().apply {
      put("seller_id", "seller-1")
      put("shop_is_open", true)
      put("timezone", "Asia/Karachi")
      put("shop_hours", WeeklyShopHours.defaults().toJson())
    }.toString()

    val categoriesJson = JSONArray().apply {
      put(JSONObject().put("id", "cat-food").put("name", "Fresh Food").put("display_order", 1))
      put(JSONObject().put("id", "cat-pottery").put("name", "Pottery").put("display_order", 2))
      put(JSONObject().put("id", "cat-empty").put("name", "Books").put("display_order", 3))
      put(JSONObject().put("id", "cat-paused").put("name", "Electronics").put("display_order", 4))
    }.toString()

    val productsJson = JSONArray().apply {
      put(JSONObject()
        .put("id", "prod-food-1")
        .put("seller_id", "seller-1")
        .put("category_id", "cat-food")
        .put("name", "Multani Sohan Halwa")
        .put("price", 1200)
        .put("publication_status", "published")
        .put("moderation_status", "approved")
        .put("stock_verification_status", "verified"))
      put(JSONObject()
        .put("id", "prod-pottery-1")
        .put("seller_id", "seller-1")
        .put("category_id", "cat-pottery")
        .put("name", "Camel Skin Lamp")
        .put("price", 3200)
        .put("publication_status", "published")
        .put("moderation_status", "approved")
        .put("stock_verification_status", "verified"))
      // Paused daily item should be excluded from active vendor categories and products
      put(JSONObject()
        .put("id", "prod-paused-1")
        .put("seller_id", "seller-1")
        .put("category_id", "cat-paused")
        .put("name", "Paused Smart Watch")
        .put("price", 5000)
        .put("daily_status", "paused")
        .put("publication_status", "published")
        .put("moderation_status", "approved"))
    }.toString()

    val (server, captured) = startStubServer { _, target, _ ->
      when {
        target.contains("/rest/v1/rpc/get_public_shop_schedule") -> 200 to stubScheduleJson
        target.contains("/rest/v1/categories") -> 200 to categoriesJson
        target.contains("/rest/v1/products?") -> 200 to productsJson
        target.contains("/rest/v1/rpc/get_public_inventory") -> 200 to JSONArray().apply {
          put(JSONObject().put("product_id", "prod-food-1").put("quantity", 10).put("in_stock", true))
          put(JSONObject().put("product_id", "prod-pottery-1").put("quantity", 4).put("in_stock", true))
        }.toString()
        target.contains("/rest/v1/rpc/get_public_availability") -> 200 to "[]"
        else -> 200 to "[]"
      }
    }

    try {
      SupabaseConfig.projectUrlOverride = "http://127.0.0.1:${server.localPort}"
      val shop = ShopRecord(id = "seller-1", name = "Multan Craft House", city = "Multan")

      composeTestRule.setContent {
        JTBazarTheme {
          PublicShopDetailScreen(
            shop = shop,
            fallbackProducts = emptyList(),
            onBack = {},
            onProductClick = {},
            nowProvider = { mondayAfternoonPkt },
            scheduleRefreshIntervalMs = 60_000L
          )
        }
      }

      // Wait until schedule & products finish loading
      composeTestRule.waitUntil(timeoutMillis = 5_000L) {
        composeTestRule.onAllNodesWithText("Open until 10:00 PM PKT", substring = true).fetchSemanticsNodes().isNotEmpty() &&
          runCatching {
            composeTestRule.onNodeWithTag("public_shop_detail_list")
              .performScrollToNode(hasTestTag("public_shop_category_strip"))
            true
          }.getOrDefault(false)
      }

      // 1. Open status displayed in PKT
      composeTestRule.onNodeWithTag("public_shop_detail_list")
        .performScrollToNode(hasTestTag("public_shop_open_status_badge"))
      composeTestRule.onNodeWithTag("public_shop_open_status_badge")
        .assertTextContains("Open")
      composeTestRule.onNodeWithTag("public_shop_status_detail")
        .assertTextContains("Open until 10:00 PM PKT", substring = true)
      assertTrue(composeTestRule.onAllNodesWithText("Shop temporarily closed").fetchSemanticsNodes().isEmpty())

      // 2. Active categories present; empty & paused-only categories hidden
      composeTestRule.onNodeWithTag("public_shop_detail_list")
        .performScrollToNode(hasTestTag("public_shop_category_strip"))
      composeTestRule.onNodeWithTag("public_shop_category_all")
        .assertTextContains("All Products (2)")
      composeTestRule.onNodeWithTag("public_shop_category_cat-food")
        .assertTextContains("Fresh Food (1)")
      composeTestRule.onNodeWithTag("public_shop_category_cat-pottery")
        .assertTextContains("Pottery (1)")
      assertTrue(composeTestRule.onAllNodesWithText("Books", substring = true).fetchSemanticsNodes().isEmpty())
      assertTrue(composeTestRule.onAllNodesWithText("Electronics", substring = true).fetchSemanticsNodes().isEmpty())
      assertTrue(composeTestRule.onAllNodesWithText("Paused Smart Watch").fetchSemanticsNodes().isEmpty())

      // 3. Selecting Pottery shows only Pottery product
      composeTestRule.onNodeWithTag("public_shop_category_cat-pottery").performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("public_shop_detail_list")
        .performScrollToNode(androidx.compose.ui.test.hasText("Camel Skin Lamp"))
      composeTestRule.onNodeWithText("Camel Skin Lamp").assertIsDisplayed()
      assertTrue(composeTestRule.onAllNodesWithText("Multani Sohan Halwa").fetchSemanticsNodes().isEmpty())

      // 4. Selecting All Products restores both
      composeTestRule.onNodeWithTag("public_shop_detail_list")
        .performScrollToNode(hasTestTag("public_shop_category_strip"))
      composeTestRule.onNodeWithTag("public_shop_category_all").performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("public_shop_detail_list")
        .performScrollToNode(androidx.compose.ui.test.hasText("Multani Sohan Halwa"))
      composeTestRule.onNodeWithText("Multani Sohan Halwa").assertIsDisplayed()

      assertTrue(captured.any { it.target.contains("get_public_shop_schedule") })
    } finally {
      server.close()
    }
  }

  @Test
  fun closedVendorCheckoutServerTriggerMapsToClearEnglishAndUrduCustomerError() {
    val triggerExceptions = listOf(
      IOException("""RPC place_order error 400: {"code":"P0001","message":"Vendor shop is currently closed (enforce_vendor_daily_order_rules)"}"""),
      IOException("""RPC place_order error 400: {"code":"P0001","message":"Orders cannot be placed outside vendor opening hours"}"""),
      IOException("""RPC place_order error 403: {"message":"Seller shop_is_open is false; shop is not accepting orders right now"}""")
    )

    // English
    AppLanguageManager.setLanguageForTest(AppLanguage.ENGLISH)
    triggerExceptions.forEach { err ->
      val msg = AppErrorMapper.toCustomerMessage(err, ErrorContext.ORDER_PLACE, isOnline = true)
      assertTrue("Expected clear vendor closed message, got: $msg", msg.contains("vendor shop is currently closed", ignoreCase = true))
      assertFalse(msg.contains("P0001"))
      assertFalse(msg.contains("400"))
      assertFalse(msg.contains("enforce_vendor_daily_order_rules"))
    }

    // Urdu
    AppLanguageManager.setLanguageForTest(AppLanguage.URDU)
    triggerExceptions.forEach { err ->
      val msg = AppErrorMapper.toCustomerMessage(err, ErrorContext.ORDER_PLACE, isOnline = true)
      assertTrue("Expected Urdu vendor closed message, got: $msg", msg.contains("یہ دکان اس وقت بند ہے"))
      assertFalse(msg.contains("P0001"))
    }

    // Storewide closure stays distinct from vendor shop closure
    AppLanguageManager.setLanguageForTest(AppLanguage.ENGLISH)
    val globalClosed = AppErrorMapper.toCustomerMessage(
      IOException("""RPC place_order error 400: {"message":"JTBazar is currently closed"}"""),
      ErrorContext.ORDER_PLACE,
      isOnline = true
    )
    assertTrue(globalClosed.contains("JTBazar is currently closed", ignoreCase = true))
  }

  @Test
  fun sundayClosedDefaultIsReflectedOnPublicShopScheduleInKarachiTimezone() {
    val sundayAfternoonPkt = karachiInstant(2026, 11, 22, 14, 0) // Sun 2:00 PM PKT
    val stubScheduleJson = JSONObject().apply {
      put("seller_id", "seller-sun")
      put("shop_is_open", true)
      put("timezone", "Asia/Karachi")
      // No shop_hours -> falls back to defaults where Sunday is Closed
    }.toString()

    val (server, _) = startStubServer { _, target, _ ->
      when {
        target.contains("/rest/v1/rpc/get_public_shop_schedule") -> 200 to stubScheduleJson
        else -> 200 to "[]"
      }
    }

    try {
      SupabaseConfig.projectUrlOverride = "http://127.0.0.1:${server.localPort}"
      val shop = ShopRecord(id = "seller-sun", name = "Sunday Test Shop")

      composeTestRule.setContent {
        JTBazarTheme {
          PublicShopDetailScreen(
            shop = shop,
            fallbackProducts = emptyList(),
            onBack = {},
            onProductClick = {},
            nowProvider = { sundayAfternoonPkt },
            scheduleRefreshIntervalMs = 60_000L
          )
        }
      }

      composeTestRule.waitUntil(timeoutMillis = 5_000L) {
        composeTestRule.onAllNodesWithText("Opens Monday at 09:00 AM PKT", substring = true).fetchSemanticsNodes().isNotEmpty()
      }
      composeTestRule.onNodeWithTag("public_shop_open_status_badge")
        .assertTextContains("Closed")
      composeTestRule.onNodeWithTag("public_shop_closed_banner").assertIsDisplayed()
    } finally {
      server.close()
    }
  }

  @Test
  fun vendorShopCatalogFilterHidesCategoriesWithZeroActiveProducts() {
    val categories = listOf(
      CategoryItem(id = "c1", title = "Spices", countText = "", sampleItems = "", displayOrder = 1),
      CategoryItem(id = "c2", title = "Handicrafts", countText = "", sampleItems = "", displayOrder = 2),
      CategoryItem(id = "c3", title = "Zero Items", countText = "", sampleItems = "", displayOrder = 3)
    )
    val products = listOf(
      Product(id = "p1", title = "Garam Masala", vendor = "S", category = "Spices", categoryId = "c1", price = 500, originalPrice = 500, discountPercent = 0, rating = 0.0, reviewCount = 0),
      Product(id = "p2", title = "Paused Clay Pot", vendor = "S", category = "Handicrafts", categoryId = "c2", price = 900, originalPrice = 900, discountPercent = 0, rating = 0.0, reviewCount = 0, dailyStatus = "paused")
    )

    val built = VendorShopCatalogFilter.buildCategoriesForProducts(products, categories)
    assertEquals(1, built.size)
    assertEquals("c1", built.first().id)
    assertEquals("Spices", built.first().title)
    assertEquals(1, built.first().productCount)
  }
}
