package com.example

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.DeliveryAddress
import com.example.data.supabase.SupabaseAddressDto
import com.example.data.supabase.SupabaseApiService
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseSession
import com.example.data.supabase.SupabaseUser
import com.example.ui.screens.SavedAddressDropdown
import com.example.ui.theme.JTBazarTheme
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
@OptIn(ExperimentalCoroutinesApi::class)
class CustomerAddressTest {

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

  private fun startStubRestServer(
    responses: List<Pair<Int, String>>
  ): Pair<ServerSocket, CopyOnWriteArrayList<CapturedRequest>> {
    val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
    val captured = CopyOnWriteArrayList<CapturedRequest>()
    val ready = CountDownLatch(1)

    thread(isDaemon = true) {
      ready.countDown()
      responses.forEach { (statusCode, responseBody) ->
        try {
          server.accept().use { socket ->
            val input = BufferedInputStream(socket.getInputStream())
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

            val respBytes = responseBody.toByteArray(Charsets.UTF_8)
            val statusText = if (statusCode in 200..299) "OK" else "Bad Request"
            val httpResp = "HTTP/1.1 $statusCode $statusText\r\n" +
              "Content-Type: application/json\r\n" +
              "Content-Length: ${respBytes.size}\r\n" +
              "Connection: close\r\n\r\n"
            socket.getOutputStream().write(httpResp.toByteArray(Charsets.UTF_8))
            socket.getOutputStream().write(respBytes)
            socket.getOutputStream().flush()
          }
        } catch (_: Exception) {
        }
      }
    }
    ready.await(2, TimeUnit.SECONDS)
    return server to captured
  }

  @Test
  fun saveAddressOmitsNonexistentTagColumnNormalizesPhoneAndParsesReturnedUuid() = runBlocking {
    val returnedRow = JSONArray().put(
      JSONObject().apply {
        put("id", "11111111-2222-3333-4444-555555555555")
        put("user_id", "user-uuid-1")
        put("recipient_name", "Ali Khan")
        put("phone", "+923001234567")
        put("address_line_1", "House 14, Street 6")
        put("address_line_2", "Gulberg III")
        put("city", "Lahore")
        put("province", JSONObject.NULL)
        put("postal_code", JSONObject.NULL)
        put("is_default", true)
        put("latitude", 31.5102)
        put("longitude", 74.3441)
      }
    ).toString()

    val (server, captured) = startStubRestServer(
      listOf(
        201 to returnedRow,
        200 to "[]"
      )
    )

    try {
      SupabaseConfig.projectUrlOverride = "http://127.0.0.1:${server.localPort}"
      SupabaseClient.setSession(
        SupabaseSession(
          accessToken = "token-abc",
          user = SupabaseUser(id = "user-uuid-1", email = "ali@example.com")
        )
      )

      val result = SupabaseApiService.saveAddress(
        SupabaseAddressDto(
          id = "addr_1700000000000",
          userId = "user-uuid-1",
          name = "  Ali Khan ",
          phone = "0300 123-4567",
          addressLine = "House 14, Street 6",
          area = "Gulberg III",
          city = "Lahore",
          province = "",
          tag = "HOME",
          isDefault = true,
          latitude = 31.5102,
          longitude = 74.3441
        )
      )

      assertTrue("saveAddress must succeed: ${result.exceptionOrNull()}", result.isSuccess)
      val saved = result.getOrThrow()
      assertEquals("11111111-2222-3333-4444-555555555555", saved.id)
      assertEquals("+923001234567", saved.phone)
      assertEquals("", saved.province)
      assertNull(saved.postalCode)

      assertTrue("Expected POST to customer_addresses", captured.isNotEmpty())
      val postReq = captured[0]
      assertEquals("POST", postReq.method)
      assertEquals("/rest/v1/customer_addresses", postReq.target)
      val sentJson = JSONObject(postReq.body)
      assertFalse("Payload must NOT contain nonexistent 'tag' column", sentJson.has("tag"))
      assertFalse("Payload must NOT send blank province", sentJson.has("province"))
      assertEquals("+923001234567", sentJson.getString("phone"))
      assertEquals("Ali Khan", sentJson.getString("recipient_name"))
      assertEquals("House 14, Street 6", sentJson.getString("address_line_1"))
      assertEquals("Lahore", sentJson.getString("city"))
      assertEquals(31.5102, sentJson.getDouble("latitude"), 0.0001)
    } finally {
      server.close()
    }
  }

  @Test
  fun phoneNormalizationAcceptsSpacedAndDashedPakistaniNumbers() {
    assertEquals("+923001234567", SupabaseApiService.normalizePakistaniMobile("0300 1234567"))
    assertEquals("+923001234567", SupabaseApiService.normalizePakistaniMobile("0300-123-4567"))
    assertEquals("+923001234567", SupabaseApiService.normalizePakistaniMobile("+92 300 1234567"))
    assertFalse(SupabaseApiService.isValidPakistaniMobile("12345"))
  }

  @Test
  fun checkoutSavedAddressDropdownDisplaysAllSavedAddressesAndHandlesSelection() {
    val addresses = (1..4).map { idx ->
      DeliveryAddress(
        id = "addr_uuid_$idx",
        name = "Customer $idx",
        phone = "+92300123456$idx",
        addressLine = "House $idx, Main Boulevard",
        area = "Phase $idx",
        city = "Lahore",
        isDefault = idx == 1,
        latitude = 31.5 + idx * 0.01,
        longitude = 74.3 + idx * 0.01
      )
    }

    val selectedIdState = mutableStateOf<String?>(addresses[0].id)
    val streetState = mutableStateOf(addresses[0].addressLine)
    var useNewCount = 0

    composeTestRule.setContent {
      JTBazarTheme(darkTheme = false) {
        SavedAddressDropdown(
          savedAddresses = addresses,
          selectedAddressId = selectedIdState.value,
          currentStreetAddress = streetState.value,
          onSelectSavedAddress = { addr ->
            selectedIdState.value = addr.id
            streetState.value = addr.addressLine
          },
          onUseNewAddress = {
            useNewCount++
            selectedIdState.value = null
            streetState.value = ""
          }
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Saved Delivery Addresses").assertIsDisplayed()
    composeTestRule.onNodeWithText("4 saved").assertIsDisplayed()

    // Open dropdown
    composeTestRule.onNodeWithTag("checkout_saved_address_dropdown").performClick()
    composeTestRule.waitForIdle()

    // Verify all 4 saved addresses (beyond old 3-chip cap) and "Use a New Delivery Address" option exist
    composeTestRule.onNodeWithTag("saved_address_option_addr_uuid_1").assertExists()
    composeTestRule.onNodeWithTag("saved_address_option_addr_uuid_4").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("saved_address_option_new").performScrollTo().assertIsDisplayed()

    // Select 4th address
    composeTestRule.onNodeWithTag("saved_address_option_addr_uuid_4").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals("addr_uuid_4", selectedIdState.value)
    assertEquals("House 4, Main Boulevard", streetState.value)

    // Reopen and choose "Use a New Delivery Address"
    composeTestRule.onNodeWithTag("checkout_saved_address_dropdown").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("saved_address_option_new").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, useNewCount)
    assertNull(selectedIdState.value)
  }
}
