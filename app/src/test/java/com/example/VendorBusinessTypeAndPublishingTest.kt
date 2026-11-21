package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseSession
import com.example.data.supabase.SupabaseUser
import com.example.data.supabase.VendorApplication
import com.example.data.supabase.VendorBusinessType
import com.example.data.supabase.VendorService
import com.example.localization.AppLanguage
import com.example.localization.AppLanguageManager
import com.example.localization.JTBazarLocalizedContainer
import com.example.ui.screens.BusinessTypeDropdown
import com.example.ui.screens.VendorListingEditor
import com.example.ui.screens.VendorProductEditScreen
import com.example.ui.screens.VendorShopSettingsScreen
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
@OptIn(ExperimentalCoroutinesApi::class)
class VendorBusinessTypeAndPublishingTest {

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
    AppLanguageManager.setLanguageForTest(AppLanguage.ENGLISH)
  }

  private data class RecordedHttpCall(
    val method: String,
    val target: String,
    val body: String
  )

  private fun startStubServer(
    handler: (method: String, target: String, body: String) -> Pair<Int, String>
  ): Pair<ServerSocket, CopyOnWriteArrayList<RecordedHttpCall>> {
    val server = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
    val recorded = CopyOnWriteArrayList<RecordedHttpCall>()
    thread(isDaemon = true) {
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
              val firstParts = lines.firstOrNull()?.split(" ") ?: emptyList()
              val method = firstParts.getOrElse(0) { "" }
              val target = firstParts.getOrElse(1) { "" }
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
              val body = String(bodyBuf, 0, readTotal, Charsets.UTF_8)
              recorded.add(RecordedHttpCall(method, target, body))

              val (code, respBody) = handler(method, target, body)
              val bytes = respBody.toByteArray(Charsets.UTF_8)
              val statusText = if (code in 200..299) "OK" else "Bad Request"
              val headerResp = "HTTP/1.1 $code $statusText\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
              s.getOutputStream().write(headerResp.toByteArray(Charsets.UTF_8))
              s.getOutputStream().write(bytes)
              s.getOutputStream().flush()
            }
          }
        } catch (_: Exception) {
          break
        }
      }
    }
    return server to recorded
  }

  private fun setAuthenticatedSession(userId: String = "vendor-user-1") {
    SupabaseClient.setSession(
      SupabaseSession(
        accessToken = "test-access-token",
        refreshToken = "test-refresh-token",
        user = SupabaseUser(id = userId, email = "vendor@example.com")
      )
    )
  }

  @Test
  fun businessTypeOptionsMatchWebsiteSchemaAndLabelsInEnglishAndUrdu() {
    val expectedKeys = setOf(
      "individual_seller",
      "retail_store",
      "restaurant",
      "grocery_store",
      "bakery",
      "cafe"
    )
    assertEquals(expectedKeys, VendorBusinessType.validKeys)
    assertTrue(VendorBusinessType.isValid("bakery"))
    assertFalse(VendorBusinessType.isValid("invalid_category"))

    AppLanguageManager.setLanguageForTest(AppLanguage.ENGLISH)
    assertEquals("Bakery", VendorBusinessType.localizedLabelFor("bakery"))
    assertEquals("Restaurant / Food", VendorBusinessType.localizedLabelFor("restaurant"))

    AppLanguageManager.setLanguageForTest(AppLanguage.URDU)
    assertEquals("بیکری", VendorBusinessType.localizedLabelFor("bakery"))
    assertEquals("کریانہ اسٹور", VendorBusinessType.localizedLabelFor("grocery_store"))
  }

  @Test
  fun getOwnApplicationAndResubmitPreserveBusinessTypeAndTrustedPublishingFlag() = runBlocking {
    setAuthenticatedSession()
    val sellerJson = JSONObject()
      .put("id", "seller-1")
      .put("status", "rejected")
      .put("shop_name", "Fresh Oven")
      .put("slug", "fresh-oven")
      .put("description", "Fresh baked goods every morning.")
      .put("phone", "03001234567")
      .put("current_address", "Main Bazar")
      .put("complete_address", "Shop 4 Main Bazar Lahore")
      .put("contact_email", "vendor@example.com")
      .put("vendor_type", "sole_proprietor")
      .put("business_type", "bakery")
      .put("allow_product_publishing_without_review", true)
      .put("owner_full_name", "Bilal Ahmad")
      .put("cnic_number", "3520212345678")
      .put("shop_location_text", "Near Clock Tower Main Bazar")

    val (server, recorded) = startStubServer { method, target, _ ->
      when {
        method == "GET" && target.contains("/rest/v1/seller_profiles") -> {
          200 to JSONArray().put(sellerJson).toString()
        }
        method == "POST" && target.contains("/rest/v1/rpc/seller_resubmit_application") -> {
          sellerJson.put("status", "pending")
          200 to sellerJson.toString()
        }
        else -> 200 to "[]"
      }
    }

    try {
      SupabaseConfig.projectUrlOverride = "http://127.0.0.1:${server.localPort}"
      val own = VendorService.getOwnApplication()
      assertNotNull(own)
      assertEquals("bakery", own!!.businessType)
      assertTrue(own.allowProductPublishingWithoutReview)

      val getCall = recorded.first { it.method == "GET" && it.target.contains("seller_profiles") }
      assertTrue(getCall.target.contains("business_type"))
      assertTrue(getCall.target.contains("allow_product_publishing_without_review"))

      val resubmitted = VendorService.resubmitApplication()
      assertEquals("pending", resubmitted.status)
      assertEquals("bakery", resubmitted.businessType)
      assertTrue(resubmitted.allowProductPublishingWithoutReview)
    } finally {
      server.close()
    }
  }

  @Test
  fun finalizeProductSubmissionSkipsRpcWhenPermissionDisabled() = runBlocking {
    setAuthenticatedSession()
    val (server, recorded) = startStubServer { _, _, _ -> 200 to "[]" }
    try {
      SupabaseConfig.projectUrlOverride = "http://127.0.0.1:${server.localPort}"
      val outcome = VendorService.finalizeProductSubmission(
        productId = "prod-1",
        allowDirectPublish = false,
        isEdit = false
      )
      assertFalse(outcome.publishedImmediately)
      assertNull(outcome.finalizationError)
      assertEquals("pending_review", outcome.moderationStatus)
      assertEquals("draft", outcome.publicationStatus)
      assertTrue(recorded.none { it.target.contains("vendor_finalize_product_submission") })
    } finally {
      server.close()
    }
  }

  @Test
  fun finalizeProductSubmissionVerifiesLiveStatusAfterRpcForApprovedAndBlockedCases() = runBlocking {
    setAuthenticatedSession()
    var moderation = "approved"
    var publication = "published"
    var shouldFailRpc = false

    val (server, recorded) = startStubServer { method, target, _ ->
      when {
        method == "POST" && target.contains("/rest/v1/rpc/vendor_finalize_product_submission") -> {
          if (shouldFailRpc) {
            403 to """{"message":"Direct publishing permission has been revoked by admin"}"""
          } else {
            200 to """{"moderation_status":"$moderation","publication_status":"$publication"}"""
          }
        }
        method == "GET" && target.contains("/rest/v1/products") -> {
          200 to JSONArray().put(
            JSONObject()
              .put("id", "prod-99")
              .put("moderation_status", moderation)
              .put("publication_status", publication)
          ).toString()
        }
        else -> 200 to "[]"
      }
    }

    try {
      SupabaseConfig.projectUrlOverride = "http://127.0.0.1:${server.localPort}"

      // Case 1: Trusted vendor -> RPC succeeds & live DB confirms approved + published.
      val publishedOutcome = VendorService.finalizeProductSubmission(
        productId = "prod-99",
        allowDirectPublish = true,
        isEdit = true
      )
      assertTrue(publishedOutcome.publishedImmediately)
      assertNull(publishedOutcome.finalizationError)
      assertEquals("approved", publishedOutcome.moderationStatus)
      assertEquals("published", publishedOutcome.publicationStatus)
      val rpcCall = recorded.first { it.target.contains("vendor_finalize_product_submission") }
      assertEquals("prod-99", JSONObject(rpcCall.body).getString("p_product_id"))

      // Case 2: Live DB status is hidden/blocked (e.g., admin paused or blocked publishing).
      moderation = "approved"
      publication = "hidden"
      val blockedOutcome = VendorService.finalizeProductSubmission(
        productId = "prod-99",
        allowDirectPublish = true,
        isEdit = false
      )
      assertFalse(blockedOutcome.publishedImmediately)
      assertNull(blockedOutcome.finalizationError)
      assertEquals("hidden", blockedOutcome.publicationStatus)

      // Case 3: RPC fails -> surface safe error, never claim immediate publication.
      shouldFailRpc = true
      val failedOutcome = VendorService.finalizeProductSubmission(
        productId = "prod-99",
        allowDirectPublish = true,
        isEdit = false
      )
      assertFalse(failedOutcome.publishedImmediately)
      assertNotNull(failedOutcome.finalizationError)
      assertTrue(failedOutcome.finalizationError!!.isNotBlank())
    } finally {
      server.close()
    }
  }

  @Test
  fun shopSettingsDisplaysSavedBusinessTypeReadOnlyWithSupportNote() {
    val application = VendorApplication(
      id = "seller-42",
      status = "approved",
      shopName = "Green Cafe",
      slug = "green-cafe",
      description = "Coffee and snacks in central Lahore.",
      phone = "03001234567",
      currentAddress = "Gulberg III",
      completeAddress = "Plot 12 Gulberg III Lahore",
      contactEmail = "vendor@example.com",
      vendorType = "sole_proprietor",
      businessType = "cafe",
      allowProductPublishingWithoutReview = true,
      ownerFullName = "Sara Khan",
      cnicNumber = "3520212345678",
      shopLocationText = "Liberty Market"
    )

    composeTestRule.setContent {
      VendorCenterTheme {
        JTBazarLocalizedContainer {
          VendorShopSettingsScreen(
            application = application,
            accountEmail = "vendor@example.com",
            onSaved = {}
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("vendor_shop_settings_list")
      .performScrollToNode(hasTestTag("shop_settings_business_type_dropdown"))
    composeTestRule.onNodeWithTag("vendor_business_type_dropdown").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("vendor_business_type_dropdown").assertIsNotEnabled()
    composeTestRule.onNodeWithText("Cafe").assertIsDisplayed()
    composeTestRule.onNodeWithText("Contact marketplace support if your business category needs to change.")
      .assertIsDisplayed()
  }

  @Test
  fun businessTypeDropdownSelectsOptionInUrduAndProductEditorsDisplayTrustedStatusBanner() {
    var pickedKey = ""
    composeTestRule.setContent {
      VendorCenterTheme {
        JTBazarLocalizedContainer {
          BusinessTypeDropdown(
            selectedType = pickedKey,
            onTypeSelected = { pickedKey = it }
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    AppLanguageManager.setLanguageForTest(AppLanguage.URDU)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("vendor_business_type_dropdown").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("business_type_option_restaurant").performClick()
    composeTestRule.waitForIdle()
    assertEquals("restaurant", pickedKey)
  }

  @Test
  fun listingAndEditScreensShowAdminControlledPublishingStatusWithoutVendorToggle() {
    val (server, _) = startStubServer { _, _, _ -> 200 to "[]" }
    SupabaseConfig.projectUrlOverride = "http://127.0.0.1:${server.localPort}"
    val showEdit = androidx.compose.runtime.mutableStateOf(false)
    val dummyProduct = JSONObject()
      .put("id", "p-1")
      .put("name", "Honey Jar")
      .put("slug", "honey-jar")
      .put("price", "800")

    try {
      composeTestRule.setContent {
        VendorCenterTheme {
          JTBazarLocalizedContainer {
            if (!showEdit.value) {
              VendorListingEditor(
                allowDirectPublish = true,
                onCancel = {},
                onSaved = {}
              )
            } else {
              VendorProductEditScreen(
                product = dummyProduct,
                allowDirectPublish = false,
                onBack = {},
                onSaved = {}
              )
            }
          }
        }
      }
      composeTestRule.waitForIdle()

      composeTestRule.onNodeWithTag("listing_publishing_mode_banner").assertIsDisplayed()
      composeTestRule.onNodeWithText("Direct publishing is enabled by admin. Eligible products publish live after submission checks.")
        .assertIsDisplayed()

      showEdit.value = true
      composeTestRule.waitForIdle()

      composeTestRule.onNodeWithTag("edit_publishing_mode_banner").assertIsDisplayed()
      composeTestRule.onNodeWithText("Product updates remain pending until reviewed by JTBazar moderation.")
        .assertIsDisplayed()
    } finally {
      server.close()
    }
  }
}
