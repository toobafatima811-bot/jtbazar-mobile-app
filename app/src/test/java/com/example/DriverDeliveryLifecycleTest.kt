package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseSession
import com.example.data.supabase.SupabaseUser
import com.example.ui.screens.DriverCenterScreen
import com.example.ui.screens.DriverNavigationScreen
import com.example.ui.screens.MAX_DRIVER_IMAGE_BYTES
import com.example.ui.screens.buildDeliveryProofPath
import com.example.ui.screens.canCompleteDelivery
import com.example.ui.screens.extractDriverFriendlyError
import com.example.ui.screens.extractDriverRejectionReason
import com.example.ui.screens.hasDeliveryProofSaved
import com.example.ui.screens.hasDriverDeliveryAccess
import com.example.ui.screens.isDeliveryOtpVerified
import com.example.ui.screens.missingCompletionStepsMessage
import com.example.ui.screens.validateDeliveryProofImage
import com.example.ui.theme.JTBazarTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okio.ByteString.Companion.encodeUtf8
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class DriverDeliveryLifecycleTest {

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
    SupabaseClient.storageUrlOverride = null
    SupabaseClient.clearSession()
  }

  private fun createTestJwt(subject: String): String {
    val header = """{"alg":"HS256","typ":"JWT"}""".encodeUtf8().base64Url().trimEnd('=')
    val payload = """{"sub":"$subject","role":"authenticated"}""".encodeUtf8().base64Url().trimEnd('=')
    return "$header.$payload.test_signature"
  }

  private fun setAuthenticatedDriver(userId: String = "11111111-1111-4111-8111-111111111111") {
    SupabaseClient.setSession(
      SupabaseSession(
        accessToken = createTestJwt(userId),
        refreshToken = "refresh_tok",
        user = SupabaseUser(id = userId, email = "driver@jtbazar.pk", fullName = "Ali Rider")
      )
    )
  }

  private data class StubRequest(
    val method: String,
    val pathWithQuery: String,
    val headers: Map<String, String>,
    val body: String
  )

  private data class StubResponse(
    val code: Int = 200,
    val body: String = "{}"
  )

  private class StubSupabaseServer(
    private val handler: (StubRequest) -> StubResponse
  ) : AutoCloseable {
    private val server = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
    val recordedRequests = CopyOnWriteArrayList<StubRequest>()
    @Volatile private var running = true
    private val ready = CountDownLatch(1)

    val baseUrl: String get() = "http://127.0.0.1:${server.localPort}"

    init {
      thread(isDaemon = true) {
        ready.countDown()
        while (running) {
          try {
            val socket = server.accept()
            thread(isDaemon = true) {
              socket.use { sock ->
                val input = BufferedInputStream(sock.getInputStream())
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
                val lines = rawHeaders.split("\r\n").filter { it.isNotEmpty() }
                if (lines.isEmpty()) return@use
                val requestLineParts = lines[0].split(" ")
                val method = requestLineParts.getOrElse(0) { "" }
                val target = requestLineParts.getOrElse(1) { "" }

                val headers = linkedMapOf<String, String>()
                for (line in lines.drop(1)) {
                  val colon = line.indexOf(':')
                  if (colon > 0) {
                    headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
                  }
                }

                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                val bodyBytes = ByteArrayOutputStream()
                val buf = ByteArray(1024)
                var remaining = contentLength
                while (remaining > 0) {
                  val read = input.read(buf, 0, minOf(buf.size, remaining))
                  if (read <= 0) break
                  bodyBytes.write(buf, 0, read)
                  remaining -= read
                }

                val req = StubRequest(
                  method = method,
                  pathWithQuery = target,
                  headers = headers,
                  body = bodyBytes.toString(Charsets.UTF_8.name())
                )
                recordedRequests.add(req)

                val resp = handler(req)
                val statusText = when (resp.code) {
                  200 -> "OK"
                  400 -> "Bad Request"
                  403 -> "Forbidden"
                  else -> "Error"
                }
                val respBytes = resp.body.toByteArray(Charsets.UTF_8)
                val httpResp = "HTTP/1.1 ${resp.code} $statusText\r\n" +
                  "Content-Type: application/json\r\n" +
                  "Content-Length: ${respBytes.size}\r\n" +
                  "Connection: close\r\n\r\n"
                sock.getOutputStream().write(httpResp.toByteArray(Charsets.UTF_8))
                sock.getOutputStream().write(respBytes)
                sock.getOutputStream().flush()
              }
            }
          } catch (_: Exception) {
            if (!running) break
          }
        }
      }
      ready.await(2, TimeUnit.SECONDS)
    }

    override fun close() {
      running = false
      runCatching { server.close() }
    }
  }

  // ---- Pure Unit Tests ----

  @Test
  fun otpVerificationReadsPersistedBackendFieldsSafely() {
    assertFalse(isDeliveryOtpVerified(JSONObject()))
    assertFalse(isDeliveryOtpVerified(JSONObject().put("otp_verified", false).put("otp_verified_at", JSONObject.NULL)))
    assertTrue(isDeliveryOtpVerified(JSONObject().put("otp_verified", true)))
    assertTrue(isDeliveryOtpVerified(JSONObject().put("delivery_otp_verified", true)))
    assertTrue(isDeliveryOtpVerified(JSONObject().put("otp_verified_at", "2026-11-17T12:00:00Z")))
    assertTrue(isDeliveryOtpVerified(JSONObject().put("delivery_otp_verified_at", "2026-11-17T12:00:00Z")))
    assertTrue(isDeliveryOtpVerified(JSONObject().put("delivery_otp_status", "verified")))
  }

  @Test
  fun deliveryProofCheckIgnoresJsonNullAndRequiresSavedPath() {
    assertFalse(hasDeliveryProofSaved(JSONObject()))
    assertFalse(hasDeliveryProofSaved(JSONObject("""{"delivery_proof_path": null}""")))
    assertFalse(hasDeliveryProofSaved(JSONObject().put("delivery_proof_path", "null")))
    assertFalse(hasDeliveryProofSaved(JSONObject().put("delivery_proof_path", "   ")))
    assertTrue(hasDeliveryProofSaved(JSONObject().put("delivery_proof_path", "uid/delivery_proofs/a_1.jpg")))
  }

  @Test
  fun completeDeliveryEnabledOnlyWhenPickedUpOtpVerifiedAndProofSaved() {
    assertFalse(canCompleteDelivery("assigned", otpVerified = true, proofSaved = true))
    assertFalse(canCompleteDelivery("picked_up", otpVerified = false, proofSaved = false))
    assertFalse(canCompleteDelivery("picked_up", otpVerified = true, proofSaved = false))
    assertFalse(canCompleteDelivery("picked_up", otpVerified = false, proofSaved = true))
    assertFalse(canCompleteDelivery("picked_up", otpVerified = true, proofSaved = true, isBusy = true))
    assertTrue(canCompleteDelivery("picked_up", otpVerified = true, proofSaved = true))

    assertTrue(missingCompletionStepsMessage(otpVerified = false, proofSaved = false)!!.contains("OTP"))
    assertTrue(missingCompletionStepsMessage(otpVerified = false, proofSaved = false)!!.contains("photo proof"))
    assertTrue(missingCompletionStepsMessage(otpVerified = true, proofSaved = false)!!.contains("photo proof"))
    assertTrue(missingCompletionStepsMessage(otpVerified = false, proofSaved = true)!!.contains("OTP"))
    assertNull(missingCompletionStepsMessage(otpVerified = true, proofSaved = true))
  }

  @Test
  fun deliveryProofValidationEnforcesImageMimeAndEightMbLimit() {
    val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2)
    val pngHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    val validatedJpg = validateDeliveryProofImage(jpegHeader, "image/jpeg")
    assertEquals("image/jpeg", validatedJpg.mimeType)
    assertEquals("jpg", validatedJpg.extension)

    val validatedPng = validateDeliveryProofImage(pngHeader, null)
    assertEquals("image/png", validatedPng.mimeType)
    assertEquals("png", validatedPng.extension)

    val nonImageRejected = runCatching {
      validateDeliveryProofImage("not-an-image".toByteArray(), "application/pdf")
    }
    assertTrue(nonImageRejected.isFailure)

    val oversized = ByteArray(MAX_DRIVER_IMAGE_BYTES + 1)
    oversized[0] = 0xFF.toByte()
    oversized[1] = 0xD8.toByte()
    oversized[2] = 0xFF.toByte()
    val oversizedRejected = runCatching {
      validateDeliveryProofImage(oversized, "image/jpeg")
    }
    assertTrue(oversizedRejected.isFailure)
    assertTrue(oversizedRejected.exceptionOrNull()?.message.orEmpty().contains("8 MB"))
  }

  @Test
  fun deliveryProofPathFollowsPrivateDriverOwnedConvention() {
    val path = buildDeliveryProofPath(
      userId = "driver-uid-1",
      assignmentId = "assign-99",
      extension = "png",
      timestampMs = 1700000123456L
    )
    assertEquals("driver-uid-1/delivery_proofs/assign-99_1700000123456.png", path)
  }

  @Test
  fun driverAccessRequiresAuthenticatedEnabledAndApproved() {
    assertFalse(hasDriverDeliveryAccess(isAuthenticated = false, accessEnabled = true, applicationStatus = "approved"))
    assertFalse(hasDriverDeliveryAccess(isAuthenticated = true, accessEnabled = false, applicationStatus = "approved"))
    assertFalse(hasDriverDeliveryAccess(isAuthenticated = true, accessEnabled = true, applicationStatus = "pending"))
    assertFalse(hasDriverDeliveryAccess(isAuthenticated = true, accessEnabled = true, applicationStatus = "rejected"))
    assertFalse(hasDriverDeliveryAccess(isAuthenticated = true, accessEnabled = true, applicationStatus = null))
    assertTrue(hasDriverDeliveryAccess(isAuthenticated = true, accessEnabled = true, applicationStatus = "approved"))
  }

  @Test
  fun extractDriverFriendlyErrorPreservesAttemptLimitMessageWithoutRawSqlOrHttp() {
    val rpcErr = IOException(
      """RPC driver_verify_delivery_otp error 400: {"code":"P0001","details":null,"message":"Maximum verification attempts (5) exceeded. Ask the customer for a new OTP."}"""
    )
    val msg = extractDriverFriendlyError(rpcErr, "Fallback")
    assertEquals("Maximum verification attempts (5) exceeded. Ask the customer for a new OTP.", msg)
    assertEquals("Blurred CNIC image", extractDriverRejectionReason(JSONObject().put("admin_note", "Blurred CNIC image")))
  }

  @Test
  fun deliveryProofUploadPostsToPrivateBucketWithoutUpsertAndSavesPath() = runBlocking {
    val uid = "11111111-1111-4111-8111-111111111111"
    val assignmentId = "assign-100"
    setAuthenticatedDriver(uid)

    StubSupabaseServer { req ->
      when {
        req.pathWithQuery.contains("/storage/v1/object/delivery-proofs/") ->
          StubResponse(200, """{"Key":"driver-documents/ok"}""")
        req.pathWithQuery.endsWith("/rest/v1/rpc/driver_set_delivery_proof_path") ->
          StubResponse(200, "true")
        else -> StubResponse(404, """{"message":"Not found"}""")
      }
    }.use { server ->
      SupabaseConfig.projectUrlOverride = server.baseUrl
      SupabaseClient.storageUrlOverride = "${server.baseUrl}/storage/v1"

      val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
      val proofPath = buildDeliveryProofPath(uid, assignmentId, "jpg", 1700000000999L)
      val uploadResult = SupabaseClient.uploadPrivateFile("delivery-proofs", proofPath, jpegBytes, "image/jpeg")
      assertTrue(uploadResult.isSuccess)

      val rpcResult = SupabaseClient.rpc(
        "driver_set_delivery_proof_path",
        JSONObject().put("p_assignment_id", assignmentId).put("p_storage_path", proofPath).toString(),
        requireAuth = true
      )
      assertTrue(rpcResult.isSuccess)

      val storageReq = server.recordedRequests.first { it.pathWithQuery.contains("/storage/v1/object/") }
      assertEquals("/storage/v1/object/delivery-proofs/$uid/delivery_proofs/${assignmentId}_1700000000999.jpg", storageReq.pathWithQuery)
      assertFalse("Private proof upload must not send x-upsert", storageReq.headers.containsKey("x-upsert"))

      val rpcReq = server.recordedRequests.first { it.pathWithQuery.endsWith("/rpc/driver_set_delivery_proof_path") }
      assertEquals(proofPath, JSONObject(rpcReq.body).getString("p_storage_path"))
      assertEquals("Bearer test-access-token", rpcReq.headers["Authorization"])
    }
  }

  // ---- Robolectric Compose UI Tests ----

  @Test
  fun stepAOtpVerificationCallsOnlyVerifyRpcAndKeepsCompleteDeliveryDisabledUntilProofUploaded() {
    val uid = "11111111-1111-4111-8111-111111111111"
    setAuthenticatedDriver(uid)

    val otpVerifiedOnBackend = AtomicBoolean(false)

    StubSupabaseServer { req ->
      when {
        req.pathWithQuery.endsWith("/rpc/get_my_driver_access") -> StubResponse(200, "true")
        req.pathWithQuery.contains("/driver_applications") -> StubResponse(
          200,
          JSONArray().put(
            JSONObject()
              .put("id", "app-1")
              .put("full_name", "Ali Rider")
              .put("phone", "03001234567")
              .put("vehicle_type", "Bike")
              .put("current_address", "Lahore")
              .put("status", "approved")
              .put("admin_note", JSONObject.NULL)
          ).toString()
        )
        req.pathWithQuery.endsWith("/rpc/driver_list_assigned_orders_v2") -> StubResponse(
          200,
          JSONArray().put(
            JSONObject()
              .put("assignment_id", "assign-1")
              .put("order_number", "JT-1001")
              .put("assignment_status", "picked_up")
              .put("order_status", "shipped")
              .put("customer_name", "Sara Khan")
              .put("customer_phone", "03110001111")
              .put("delivery_location_name", "Model Town, Lahore")
              .put("delivery_proof_path", JSONObject.NULL)
              .put("otp_verified", otpVerifiedOnBackend.get())
              .put("items", JSONArray().put(JSONObject().put("name", "Fresh Honey").put("quantity", 1)))
          ).toString()
        )
        req.pathWithQuery.endsWith("/rpc/driver_verify_delivery_otp") -> {
          val code = JSONObject(req.body).optString("p_code")
          if (code == "123456") {
            otpVerifiedOnBackend.set(true)
            StubResponse(200, "true")
          } else {
            StubResponse(200, "false")
          }
        }
        req.pathWithQuery.endsWith("/rpc/driver_update_delivery_status") ->
          StubResponse(200, "true")
        else -> StubResponse(200, "[]")
      }
    }.use { server ->
      SupabaseConfig.projectUrlOverride = server.baseUrl
      composeTestRule.setContent {
        JTBazarTheme {
          DriverCenterScreen(onBack = {})
        }
      }

      composeTestRule.waitUntil(timeoutMillis = 5000) {
        composeTestRule.onAllNodesWithTag("driver_application_status_card").fetchSemanticsNodes().isNotEmpty()
      }
      composeTestRule.onNodeWithTag("driver_center_list")
        .performScrollToNode(hasTestTag("driver_delivery_card_assign-1"))

      composeTestRule.waitUntil(timeoutMillis = 5000) {
        composeTestRule.onAllNodesWithTag("verify_otp_button_assign-1").fetchSemanticsNodes().isNotEmpty()
      }

      // Complete Delivery must start disabled with incomplete-steps hint
      composeTestRule.onNodeWithTag("complete_delivery_button_assign-1").performScrollTo().assertIsNotEnabled()
      composeTestRule.onNodeWithTag("completion_requirement_hint_assign-1").assertIsDisplayed()

      // Open OTP dialog & try incorrect OTP first -> dialog stays open with error
      composeTestRule.onNodeWithTag("verify_otp_button_assign-1").performScrollTo().performClick()
      composeTestRule.waitUntil(timeoutMillis = 5000) {
        composeTestRule.onAllNodesWithTag("driver_otp_input").fetchSemanticsNodes().isNotEmpty()
      }

      composeTestRule.onNodeWithTag("driver_otp_input")
        .performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("999999")) }
      composeTestRule.onNodeWithTag("confirm_verify_otp_button").performClick()

      composeTestRule.waitUntil(timeoutMillis = 5000) {
        composeTestRule.onAllNodesWithTag("driver_otp_error").fetchSemanticsNodes().isNotEmpty()
      }
      composeTestRule.onNodeWithTag("driver_otp_error").assertIsDisplayed()

      // Enter correct OTP -> ONLY driver_verify_delivery_otp is called; dialog closes; OTP Verified shown; Complete Delivery STILL disabled (proof missing)
      composeTestRule.onNodeWithTag("driver_otp_input")
        .performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("123456")) }
      composeTestRule.onNodeWithTag("confirm_verify_otp_button").performClick()

      composeTestRule.waitUntil(timeoutMillis = 5000) {
        composeTestRule.onAllNodesWithTag("otp_verified_badge_assign-1").fetchSemanticsNodes().isNotEmpty()
      }

      // CRITICAL: driver_update_delivery_status must NEVER be called by Step A OTP verification!
      val statusCalls = server.recordedRequests.count { it.pathWithQuery.endsWith("/rpc/driver_update_delivery_status") }
      assertEquals("Step A must not call driver_update_delivery_status", 0, statusCalls)

      // Complete Delivery remains disabled because delivery_proof_path is still missing
      composeTestRule.onNodeWithTag("complete_delivery_button_assign-1").performScrollTo().assertIsNotEnabled()
      composeTestRule.onNodeWithText("Incomplete step: Upload a delivery photo proof before completing delivery.").assertIsDisplayed()
    }
  }

  @Test
  fun stepCCompleteDeliveryEnabledWhenBackendHasOtpVerifiedAndProofSavedAndCallsDeliveredOnce() {
    val uid = "11111111-1111-4111-8111-111111111111"
    setAuthenticatedDriver(uid)

    val assignmentStatusOnBackend = AtomicReference("picked_up")

    StubSupabaseServer { req ->
      when {
        req.pathWithQuery.endsWith("/rpc/get_my_driver_access") -> StubResponse(200, "true")
        req.pathWithQuery.contains("/driver_applications") -> StubResponse(
          200,
          JSONArray().put(
            JSONObject()
              .put("id", "app-1")
              .put("full_name", "Ali Rider")
              .put("phone", "03001234567")
              .put("vehicle_type", "Bike")
              .put("current_address", "Lahore")
              .put("status", "approved")
          ).toString()
        )
        req.pathWithQuery.endsWith("/rpc/driver_list_assigned_orders_v2") -> {
          val currentStatus = assignmentStatusOnBackend.get()
          StubResponse(
            200,
            JSONArray().put(
              JSONObject()
                .put("assignment_id", "assign-2")
                .put("order_number", "JT-2002")
                .put("assignment_status", currentStatus)
                .put("order_status", if (currentStatus == "delivered") "delivered" else "shipped")
                .put("customer_name", "Bilal Ahmed")
                .put("customer_phone", "03210002222")
                .put("delivery_location_name", "Gulberg, Lahore")
                .put("delivery_proof_path", "$uid/delivery_proofs/assign-2_1700000000000.jpg")
                .put("otp_verified_at", "2026-11-17T12:15:00Z")
                .put("items", JSONArray().put(JSONObject().put("name", "Organic Honey").put("quantity", 2)))
            ).toString()
          )
        }
        req.pathWithQuery.endsWith("/rpc/driver_update_delivery_status") -> {
          assignmentStatusOnBackend.set("delivered")
          StubResponse(200, "true")
        }
        else -> StubResponse(200, "[]")
      }
    }.use { server ->
      SupabaseConfig.projectUrlOverride = server.baseUrl
      composeTestRule.setContent {
        JTBazarTheme {
          DriverCenterScreen(onBack = {})
        }
      }

      composeTestRule.waitUntil(timeoutMillis = 5000) {
        composeTestRule.onAllNodesWithTag("driver_application_status_card").fetchSemanticsNodes().isNotEmpty()
      }
      composeTestRule.onNodeWithTag("driver_center_list")
        .performScrollToNode(hasTestTag("driver_delivery_card_assign-2"))

      composeTestRule.waitUntil(timeoutMillis = 5000) {
        composeTestRule.onAllNodesWithTag("complete_delivery_button_assign-2").fetchSemanticsNodes().isNotEmpty()
      }

      // Reloaded from backend with otp_verified_at + delivery_proof_path -> OTP Verified, Uploaded, and Complete Delivery enabled!
      composeTestRule.onNodeWithTag("otp_verified_badge_assign-2").assertIsDisplayed()
      composeTestRule.onNodeWithTag("proof_uploaded_badge_assign-2").assertIsDisplayed()
      composeTestRule.onNodeWithTag("complete_delivery_button_assign-2").performScrollTo().assertIsEnabled().performClick()

      composeTestRule.waitUntil(timeoutMillis = 5000) {
        composeTestRule.onAllNodesWithText("Delivery completed successfully.").fetchSemanticsNodes().isNotEmpty()
      }

      val statusCalls = server.recordedRequests.filter { it.pathWithQuery.endsWith("/rpc/driver_update_delivery_status") }
      assertEquals(1, statusCalls.size)
      assertEquals("delivered", JSONObject(statusCalls.first().body).getString("p_action"))
    }
  }

  @Test
  fun approvedDriverWithDisabledAccessCannotFetchOrViewCustomerDeliveries() {
    val uid = "11111111-1111-4111-8111-111111111111"
    setAuthenticatedDriver(uid)

    StubSupabaseServer { req ->
      when {
        req.pathWithQuery.endsWith("/rpc/get_my_driver_access") -> StubResponse(200, "false")
        req.pathWithQuery.contains("/driver_applications") -> StubResponse(
          200,
          JSONArray().put(
            JSONObject()
              .put("id", "app-1")
              .put("full_name", "Ali Rider")
              .put("phone", "03001234567")
              .put("vehicle_type", "Bike")
              .put("current_address", "Lahore")
              .put("status", "approved")
          ).toString()
        )
        else -> StubResponse(200, "[]")
      }
    }.use { server ->
      SupabaseConfig.projectUrlOverride = server.baseUrl
      composeTestRule.setContent {
        JTBazarTheme {
          DriverCenterScreen(onBack = {})
        }
      }

      composeTestRule.waitUntil(timeoutMillis = 5000) {
        composeTestRule.onAllNodesWithTag("driver_access_disabled_card").fetchSemanticsNodes().isNotEmpty()
      }

      composeTestRule.onNodeWithTag("driver_access_disabled_card").assertIsDisplayed()
      assertEquals(
        "Disabled driver must NOT call driver_list_assigned_orders_v2",
        0,
        server.recordedRequests.count { it.pathWithQuery.endsWith("/rpc/driver_list_assigned_orders_v2") }
      )
    }
  }

  @Test
  fun rejectedApplicationDisplaysRejectionReasonAndResubmitWithoutExposingDeliveries() {
    val uid = "11111111-1111-4111-8111-111111111111"
    setAuthenticatedDriver(uid)

    StubSupabaseServer { req ->
      when {
        req.pathWithQuery.endsWith("/rpc/get_my_driver_access") -> StubResponse(200, "true")
        req.pathWithQuery.contains("/driver_applications") -> StubResponse(
          200,
          JSONArray().put(
            JSONObject()
              .put("id", "app-rej")
              .put("full_name", "Ali Rider")
              .put("phone", "03001234567")
              .put("vehicle_type", "Bike")
              .put("current_address", "Johar Town, Lahore")
              .put("status", "rejected")
              .put("admin_note", "CNIC front photo is blurry, please upload a clear photo.")
          ).toString()
        )
        else -> StubResponse(200, "[]")
      }
    }.use { server ->
      SupabaseConfig.projectUrlOverride = server.baseUrl
      composeTestRule.setContent {
        JTBazarTheme {
          DriverCenterScreen(onBack = {})
        }
      }

      composeTestRule.waitUntil(timeoutMillis = 5000) {
        composeTestRule.onAllNodesWithTag("driver_status_rejected_card").fetchSemanticsNodes().isNotEmpty()
      }

      composeTestRule.onNodeWithTag("driver_rejection_reason").assertIsDisplayed()
      composeTestRule.onNodeWithText("Reason: CNIC front photo is blurry, please upload a clear photo.").assertIsDisplayed()
      composeTestRule.onNodeWithTag("resubmit_application_button").performScrollTo().assertIsDisplayed().performClick()
      composeTestRule.onNodeWithTag("submit_driver_application_button").performScrollTo().assertIsDisplayed()
      assertEquals(
        0,
        server.recordedRequests.count { it.pathWithQuery.endsWith("/rpc/driver_list_assigned_orders_v2") }
      )
    }
  }

  @Test
  fun unauthenticatedDriverShowsSignInRequiredAndMakesZeroBackendCalls() {
    SupabaseClient.clearSession()
    StubSupabaseServer { _ -> StubResponse(200, "[]") }.use { server ->
      SupabaseConfig.projectUrlOverride = server.baseUrl
      composeTestRule.setContent {
        JTBazarTheme {
          DriverCenterScreen(onBack = {})
        }
      }

      composeTestRule.waitUntil(timeoutMillis = 5000) {
        composeTestRule.onAllNodesWithTag("driver_signin_required_card").fetchSemanticsNodes().isNotEmpty()
      }
      composeTestRule.onNodeWithTag("driver_signin_required_card").assertIsDisplayed()
      assertEquals(0, server.recordedRequests.size)
    }
  }

  @Test
  fun driverNavigationScreenBlocksDeliveredOrdersAndRedirectsIncompletePickedUpToOtpProof() {
    val uid = "11111111-1111-4111-8111-111111111111"
    setAuthenticatedDriver(uid)

    composeTestRule.setContent {
      JTBazarTheme {
        DriverNavigationScreen(
          orderNumber = "JT-3003",
          customerName = "Zainab Bibi",
          customerPhone = "03009998877",
          deliveryAddress = "DHA Phase 5, Lahore",
          customerLat = 31.4697,
          customerLng = 74.4092,
          assignmentStatus = "delivered",
          assignmentId = "assign-3",
          otpVerified = true,
          hasDeliveryProof = true,
          onStatusUpdated = {},
          onBack = {}
        )
      }
    }
    composeTestRule.onNodeWithText("Customer Location Unavailable").assertIsDisplayed()
  }
}
