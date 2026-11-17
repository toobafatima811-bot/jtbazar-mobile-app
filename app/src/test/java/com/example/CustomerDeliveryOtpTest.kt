package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.DeliveryAddress
import com.example.data.model.OrderRecord
import com.example.data.repository.MarketplaceRepository
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseSession
import com.example.data.supabase.SupabaseUser
import com.example.ui.screens.CustomerDeliveryOtpEligibility
import com.example.ui.screens.CustomerOtpErrorReason
import com.example.ui.screens.OrderTrackingScreen
import com.example.ui.screens.evaluateDeliveryOtpEligibility
import com.example.ui.screens.mapCustomerDeliveryOtpError
import com.example.ui.screens.maskEmailForDisplay
import com.example.ui.screens.parseCustomerOtpDispatchResponse
import com.example.ui.screens.requestCustomerDeliveryOtp
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
import org.junit.Assert.assertNotNull
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
import kotlin.concurrent.thread

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class CustomerDeliveryOtpTest {

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
    MarketplaceRepository.clearUserData()
  }

  private fun createTestJwt(subject: String): String {
    val header = """{"alg":"HS256","typ":"JWT"}""".encodeUtf8().base64Url().trimEnd('=')
    val payload = """{"sub":"$subject","role":"authenticated"}""".encodeUtf8().base64Url().trimEnd('=')
    return "$header.$payload.test_signature"
  }

  private fun setAuthenticatedCustomer(
    userId: String = "22222222-2222-4222-8222-222222222222",
    email: String = "sara.khan@example.com"
  ) {
    SupabaseClient.setSession(
      SupabaseSession(
        accessToken = createTestJwt(userId),
        refreshToken = "refresh_tok",
        user = SupabaseUser(id = userId, email = email, fullName = "Sara Khan")
      )
    )
  }

  private data class StubRequest(
    val method: String,
    val pathWithQuery: String,
    val headers: Map<String, String>,
    val body: String
  )

  private data class StubResponse(val code: Int = 200, val body: String = "{}")

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
                  401 -> "Unauthorized"
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

  @Test
  fun eligibilityGatesPrePickupAndMissingAssignmentBeforeCallingRpc() {
    assertEquals(CustomerDeliveryOtpEligibility.NOT_SIGNED_IN, evaluateDeliveryOtpEligibility(false, "SHIPPED", "picked_up"))
    assertEquals(CustomerDeliveryOtpEligibility.ORDER_TERMINAL, evaluateDeliveryOtpEligibility(true, "DELIVERED", "delivered"))
    assertEquals(CustomerDeliveryOtpEligibility.ORDER_TERMINAL, evaluateDeliveryOtpEligibility(true, "CANCELLED", null))
    assertEquals(CustomerDeliveryOtpEligibility.MISSING_DRIVER_ASSIGNMENT, evaluateDeliveryOtpEligibility(true, "CONFIRMED", "none"))
    assertEquals(CustomerDeliveryOtpEligibility.PARCEL_NOT_PICKED_UP, evaluateDeliveryOtpEligibility(true, "CONFIRMED", "assigned"))
    assertEquals(CustomerDeliveryOtpEligibility.PARCEL_NOT_PICKED_UP, evaluateDeliveryOtpEligibility(true, "PENDING", null))
    assertEquals(CustomerDeliveryOtpEligibility.ELIGIBLE, evaluateDeliveryOtpEligibility(true, "SHIPPED", null))
    assertEquals(CustomerDeliveryOtpEligibility.ELIGIBLE, evaluateDeliveryOtpEligibility(true, "SHIPPED", "picked_up"))
  }

  @Test
  fun mapCustomerDeliveryOtpErrorSeparatesWrongCustomerMissingDriverNotPickedUpAndUnauthenticated() {
    val unauth = mapCustomerDeliveryOtpError(IOException("Authentication required"))
    assertEquals(CustomerOtpErrorReason.UNAUTHENTICATED, unauth.first)

    val wrongCustomer = mapCustomerDeliveryOtpError(
      IOException("""RPC customer_generate_delivery_otp error 403: {"code":"42501","message":"Wrong customer: this order belongs to a different account."}"""),
      orderStatus = "SHIPPED",
      assignmentStatus = "picked_up"
    )
    assertEquals(CustomerOtpErrorReason.WRONG_CUSTOMER, wrongCustomer.first)
    assertFalse(wrongCustomer.second.contains("42501"))
    assertFalse(wrongCustomer.second.contains("403"))

    val missingDriver = mapCustomerDeliveryOtpError(
      IOException("""RPC customer_generate_delivery_otp error 403: {"code":"42501","message":"Delivery not found for this customer"}"""),
      orderStatus = "CONFIRMED",
      assignmentStatus = "none"
    )
    assertEquals(CustomerOtpErrorReason.MISSING_ASSIGNMENT, missingDriver.first)

    val notPickedUp = mapCustomerDeliveryOtpError(
      IOException("""RPC customer_generate_delivery_otp error 403: {"code":"42501","message":"Delivery not found for this customer"}"""),
      orderStatus = "READY_FOR_PICKUP",
      assignmentStatus = "assigned"
    )
    assertEquals(CustomerOtpErrorReason.NOT_PICKED_UP, notPickedUp.first)

    val emailFailed = mapCustomerDeliveryOtpError(IOException("SMTP email relay failed"))
    assertEquals(CustomerOtpErrorReason.EMAIL_DELIVERY_FAILED, emailFailed.first)
  }

  @Test
  fun parseDispatchResponseNeverExposesOtpAndMasksEmail() {
    assertEquals("sa***@example.com", maskEmailForDisplay("sara.khan@example.com"))
    val parsed = parseCustomerOtpDispatchResponse(
      rawBody = """{"sent":true,"expires_in_minutes":30,"email":"sara.khan@example.com"}""",
      registeredEmail = "sara.khan@example.com"
    )
    assertTrue(parsed.dispatched)
    assertEquals("sa***@example.com", parsed.maskedEmail)
    assertEquals(30, parsed.expiresInMinutes)
  }

  @Test
  fun unauthenticatedCustomerCannotSendAnonBearerToCustomerGenerateDeliveryOtp() = runBlocking {
    SupabaseClient.clearSession()
    StubSupabaseServer { _ -> StubResponse(200, """{"sent":true}""") }.use { server ->
      SupabaseConfig.projectUrlOverride = server.baseUrl
      val res = requestCustomerDeliveryOtp(
        orderId = "order-1",
        orderStatus = "SHIPPED",
        assignmentStatus = "picked_up"
      )
      assertTrue(res.isFailure)
      assertEquals("Must not hit network when unauthenticated", 0, server.recordedRequests.size)
    }
  }

  @Test
  fun orderTrackingScreenSendsOtpToEmailForPickedUpOrderAndNeverRendersPlaintextOtp() {
    val uid = "22222222-2222-4222-8222-222222222222"
    setAuthenticatedCustomer(uid, "sara.khan@example.com")

    val order = OrderRecord(
      orderId = "ord-picked-1",
      orderNumber = "JT-8881",
      date = "2026-11-17T10:00:00Z",
      items = emptyList(),
      subtotal = 1500,
      discount = 0,
      deliveryFee = 0,
      grandTotal = 1500,
      address = DeliveryAddress("addr-1", "Sara Khan", "03001112233", "Street 5", "DHA", "Lahore", "Punjab"),
      deliveryNote = "",
      statusHeader = "Out for Delivery",
      orderStatus = "SHIPPED"
    )
    MarketplaceRepository.selectOrder(order)

    StubSupabaseServer { req ->
      when {
        req.pathWithQuery.contains("/driver_order_assignments") ->
          StubResponse(200, JSONArray().put(JSONObject().put("id", "assign-88").put("status", "picked_up")).toString())
        req.pathWithQuery.endsWith("/rpc/customer_generate_delivery_otp") ->
          StubResponse(200, """{"sent":true,"expires_in_minutes":30}""")
        req.pathWithQuery.contains("/orders") ->
          StubResponse(
            200,
            JSONArray().put(
              JSONObject()
                .put("id", "ord-picked-1")
                .put("order_number", "JT-8881")
                .put("customer_id", uid)
                .put("status", "shipped")
                .put("payment_method", "cod")
                .put("payment_status", "pending")
                .put("subtotal", 1500)
                .put("discount_amount", 0)
                .put("delivery_fee", 0)
                .put("total_amount", 1500)
                .put("created_at", "2026-11-17T10:00:00Z")
                .put("shipping_address", JSONObject().put("name", "Sara Khan").put("phone", "03001112233").put("address_line_1", "Street 5").put("area", "DHA").put("city", "Lahore"))
            ).toString()
          )
        else -> StubResponse(200, "[]")
      }
    }.use { server ->
      SupabaseConfig.projectUrlOverride = server.baseUrl
      composeTestRule.setContent {
        JTBazarTheme {
          OrderTrackingScreen(
            onBackClick = {},
            onContinueShopping = {},
            onAccountClick = {}
          )
        }
      }

      composeTestRule.waitUntil(timeoutMillis = 5000) {
        composeTestRule.onAllNodesWithTag("generate_delivery_otp_button").fetchSemanticsNodes().isNotEmpty()
      }

      composeTestRule.onNodeWithTag("generate_delivery_otp_button").performScrollTo().performClick()

      composeTestRule.waitUntil(timeoutMillis = 5000) {
        composeTestRule.onAllNodesWithTag("delivery_otp_email_sent_card").fetchSemanticsNodes().isNotEmpty()
      }

      composeTestRule.onNodeWithText("OTP Sent to Registered Email").assertIsDisplayed()
      composeTestRule.onNodeWithText("A 6-digit verification code was sent to sa***@example.com. Check your inbox or spam folder.").assertIsDisplayed()

      val rpcReq = server.recordedRequests.firstOrNull { it.pathWithQuery.endsWith("/rpc/customer_generate_delivery_otp") }
      assertNotNull(rpcReq)
      assertEquals("ord-picked-1", JSONObject(rpcReq!!.body).getString("p_order_id"))
      assertTrue(rpcReq.headers["authorization"]?.startsWith("Bearer ") == true)
    }
  }

  @Test
  fun orderTrackingScreenLocksOtpButtonWhenOrderIsStillPendingWithoutPickup() {
    val uid = "22222222-2222-4222-8222-222222222222"
    setAuthenticatedCustomer(uid, "sara.khan@example.com")

    val pendingOrder = OrderRecord(
      orderId = "ord-pending-2",
      orderNumber = "JT-8882",
      date = "2026-11-17T10:00:00Z",
      items = emptyList(),
      subtotal = 900,
      discount = 0,
      deliveryFee = 0,
      grandTotal = 900,
      address = DeliveryAddress("addr-1", "Sara Khan", "03001112233", "Street 5", "DHA", "Lahore", "Punjab"),
      deliveryNote = "",
      statusHeader = "Order Placed",
      orderStatus = "PENDING"
    )
    MarketplaceRepository.selectOrder(pendingOrder)

    StubSupabaseServer { req ->
      when {
        req.pathWithQuery.contains("/driver_order_assignments") -> StubResponse(200, "[]")
        req.pathWithQuery.contains("/orders") ->
          StubResponse(
            200,
            JSONArray().put(
              JSONObject()
                .put("id", "ord-pending-2")
                .put("order_number", "JT-8882")
                .put("status", "pending")
                .put("subtotal", 900)
                .put("discount_amount", 0)
                .put("delivery_fee", 0)
                .put("total_amount", 900)
                .put("created_at", "2026-11-17T10:00:00Z")
                .put("shipping_address", JSONObject().put("name", "Sara Khan").put("phone", "03001112233").put("address_line_1", "Street 5").put("area", "DHA").put("city", "Lahore"))
            ).toString()
          )
        else -> StubResponse(200, "[]")
      }
    }.use { server ->
      SupabaseConfig.projectUrlOverride = server.baseUrl
      composeTestRule.setContent {
        JTBazarTheme {
          OrderTrackingScreen(
            onBackClick = {},
            onContinueShopping = {},
            onAccountClick = {}
          )
        }
      }

      composeTestRule.waitUntil(timeoutMillis = 5000) {
        composeTestRule.onAllNodesWithTag("generate_delivery_otp_button").fetchSemanticsNodes().isNotEmpty()
      }

      composeTestRule.onNodeWithTag("generate_delivery_otp_button").performScrollTo().assertIsNotEnabled()
      composeTestRule.onNodeWithTag("delivery_otp_status_hint").assertIsDisplayed()
      assertEquals(0, server.recordedRequests.count { it.pathWithQuery.endsWith("/rpc/customer_generate_delivery_otp") })
    }
  }
}
