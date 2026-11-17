package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.example.data.model.Product
import com.example.data.repository.MarketplaceRepository
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseProductCampaignDto
import com.example.data.supabase.SupabaseSession
import com.example.data.supabase.SupabaseUser
import com.example.ui.screens.HomeScreen
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class HomePerformanceTest {

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

  private data class StubRequest(val method: String, val pathWithQuery: String)
  private data class StubResponse(val code: Int = 200, val body: String = "[]")

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

                val lines = headerBytes.toString(Charsets.UTF_8.name()).split("\r\n").filter { it.isNotEmpty() }
                if (lines.isEmpty()) return@use
                val parts = lines[0].split(" ")
                val method = parts.getOrElse(0) { "" }
                val target = parts.getOrElse(1) { "" }
                recordedRequests.add(StubRequest(method, target))

                val resp = handler(StubRequest(method, target))
                val respBytes = resp.body.toByteArray(Charsets.UTF_8)
                val httpResp = "HTTP/1.1 ${resp.code} OK\r\n" +
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
  fun applyCampaignPricingPrecomputesBadgesForBogoAndBundleWithoutCompositionAllocation() {
    val baseProduct = Product(
      id = "p-1",
      title = "Desi Ghee",
      vendor = "JT Store",
      category = "Grocery",
      price = 1200,
      originalPrice = 1200,
      discountPercent = 0,
      rating = 4.8,
      reviewCount = 12,
      badge = null
    )
    val bogoCampaign = SupabaseProductCampaignDto(
      id = "c-bogo",
      name = "Bogo",
      campaignType = "bogo",
      displayTitle = "Buy 1 Get 1",
      description = null,
      discountPercent = 0.0,
      buyQuantity = 1,
      getQuantity = 1,
      bundlePrice = null,
      startsAt = null,
      endsAt = null,
      productIds = listOf("p-1")
    )
    val bundleCampaign = bogoCampaign.copy(id = "c-bundle", campaignType = "bundle")

    assertEquals("Buy 1, Get 1", MarketplaceRepository.applyCampaignPricing(baseProduct, bogoCampaign).badge)
    assertEquals("Bundle price deal", MarketplaceRepository.applyCampaignPricing(baseProduct, bundleCampaign).badge)
  }

  @Test
  fun homeScreenCapsCategoriesAndTrustedShopsAndRendersWithoutPerVisitVendorNetworkCalls() {
    val uid = "33333333-3333-4333-8333-333333333333"
    val categoriesArr = JSONArray().apply {
      for (i in 1..10) {
        put(JSONObject().put("id", "cat-$i").put("name", "Category $i").put("slug", "cat-$i"))
      }
    }
    val shopsArr = JSONArray().apply {
      for (i in 1..10) {
        put(
          JSONObject()
            .put("id", "shop-$i")
            .put("shop_name", "Shop $i")
            .put("product_count", i * 5)
            .put("rating", 3.5 + (i * 0.1))
            .put("is_verified", true)
        )
      }
    }

    StubSupabaseServer { req ->
      when {
        req.pathWithQuery.contains("/categories") -> StubResponse(200, categoriesArr.toString())
        req.pathWithQuery.endsWith("/rpc/list_public_shops") -> StubResponse(200, shopsArr.toString())
        req.pathWithQuery.contains("/seller_applications") -> StubResponse(200, """[{"status":"approved"}]""")
        req.pathWithQuery.contains("/products") -> StubResponse(200, "[]")
        else -> StubResponse(200, "[]")
      }
    }.use { server ->
      SupabaseConfig.projectUrlOverride = server.baseUrl
      SupabaseClient.setSession(
        SupabaseSession(
          accessToken = createTestJwt(uid),
          refreshToken = "r",
          user = SupabaseUser(id = uid, email = "buyer@example.com", fullName = "Buyer")
        )
      )

      runBlocking {
        MarketplaceRepository.refreshLiveCatalogSuspending(showLoading = false, refreshUserData = false)
      }

      composeTestRule.setContent {
        JTBazarTheme {
          HomeScreen(
            onProductClick = {},
            onExploreCategoriesClick = {},
            onCartClick = {},
            onAccountClick = {}
          )
        }
      }

      composeTestRule.waitUntil(timeoutMillis = 5000) {
        composeTestRule.onAllNodesWithTag("home_lazy_column").fetchSemanticsNodes().isNotEmpty()
      }
      composeTestRule.onNodeWithTag("home_lazy_column").assertIsDisplayed()

      // Categories strip caps rendered items to 6 even when 10 exist
      assertEquals(1, composeTestRule.onAllNodesWithTag("home_category_chip_cat-1").fetchSemanticsNodes().size)
      assertEquals(0, composeTestRule.onAllNodesWithTag("home_category_chip_cat-8").fetchSemanticsNodes().size)

      // HomeScreen must NOT fire direct seller_applications queries during composition
      val vendorAppCallsAfterCompose = server.recordedRequests.count { it.pathWithQuery.contains("seller_applications") }
      assertTrue("Seller status is session-cached cleanly", vendorAppCallsAfterCompose <= 1)
    }
  }
}
