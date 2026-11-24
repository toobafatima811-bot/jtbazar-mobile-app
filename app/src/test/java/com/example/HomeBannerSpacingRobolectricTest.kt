package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.SuccessResult
import com.example.data.repository.MarketplaceRepository
import com.example.data.supabase.SupabaseApiService
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseConfig
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.JTBazarTheme
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.Before
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp")
class HomeBannerSpacingRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val activeServers = mutableListOf<ServerSocket>()

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val fakeLoader = ImageLoader.Builder(context)
      .components {
        add(coil.intercept.Interceptor { chain ->
          val bitmap = Bitmap.createBitmap(900, 340, Bitmap.Config.ARGB_8888)
          SuccessResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            request = chain.request,
            dataSource = DataSource.MEMORY
          )
        })
      }
      .build()
    Coil.setImageLoader(fakeLoader)
  }

  @After
  fun tearDown() {
    activeServers.forEach { runCatching { it.close() } }
    activeServers.clear()
    SupabaseConfig.projectUrlOverride = null
    SupabaseClient.clearSession()
    SupabaseApiService.clearCaches()
    Coil.reset()
  }

  private fun startPromotionsStubServer(promotionsJson: JSONArray): ServerSocket {
    val server = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
    activeServers.add(server)
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
              val target = lines.firstOrNull()?.split(" ")?.getOrElse(1) { "" }.orEmpty()
              val body = when {
                target.startsWith("/rest/v1/promotions") -> promotionsJson.toString()
                target.startsWith("/rest/v1/homepage_heroes") -> "[]"
                else -> "[]"
              }
              val bytes = body.toByteArray(Charsets.UTF_8)
              val httpResp = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
              s.getOutputStream().write(httpResp.toByteArray(Charsets.UTF_8))
              s.getOutputStream().write(bytes)
              s.getOutputStream().flush()
            }
          }
        } catch (_: Exception) {
          if (server.isClosed) break
        }
      }
    }
    ready.await(2, TimeUnit.SECONDS)
    return server
  }

  private fun promoJson(
    id: String,
    position: String,
    priority: Int = 10,
    ctaText: String? = null,
    ctaDestination: String? = null
  ) = JSONObject()
    .put("id", id)
    .put("title", "Promo $id")
    .put("description", "Desc $id")
    .put("banner_image_url", "https://cdn.example.com/$id.png")
    .put("banner_image_urls", JSONArray().put("https://cdn.example.com/$id.png"))
    .put("display_position", position)
    .put("priority", priority)
    .put("target_platform", "both")
    .put("campaign_type", "banner")
    .put("show_on_launch", false)
    .put("status", "approved")
    .put("is_active", true)
    .apply {
      if (ctaText != null) put("cta_text", ctaText)
      if (ctaDestination != null) put("cta_destination", ctaDestination)
    }

  @Test
  @Config(qualifiers = "w360dp-h780dp")
  fun homeScreenCompactSeparatesBecomeSellerAndMultiCampaignBannersFromAdjacentSections() {
    val promos = JSONArray().apply {
      put(promoJson("top_1", "top_promo", priority = 20, ctaText = "Flash Deal", ctaDestination = "/categories"))
      put(promoJson("seller_1", "seller_banner", priority = 20, ctaText = "Become a Seller", ctaDestination = "/vendor/apply"))
      put(promoJson("seller_2", "seller_banner", priority = 10, ctaText = "Open Shop", ctaDestination = "/vendor/apply"))
    }
    val server = startPromotionsStubServer(promos)
    SupabaseConfig.projectUrlOverride = "http://127.0.0.1:${server.localPort}"
    runBlocking { MarketplaceRepository.refreshPromotionsSuspending(force = true) }

    composeTestRule.setContent {
      JTBazarTheme(darkTheme = false) {
        HomeScreen(
          onProductClick = {},
          onExploreCategoriesClick = {},
          onCartClick = {},
          onAccountClick = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    // 1. Top promo banner is properly spaced above the search bar on small phone width
    val topBannerBounds = composeTestRule.onNodeWithTag("promo_banner_item_top_1").getUnclippedBoundsInRoot()
    val searchBounds = composeTestRule.onNodeWithTag("search_text_input").getUnclippedBoundsInRoot()
    assertTrue(
      "Expected >= 16dp between top banner and search bar, got ${searchBounds.top - topBannerBounds.bottom}",
      (searchBounds.top - topBannerBounds.bottom) >= 16.dp
    )

    // 2. Scroll to Become a Seller banners and verify spacing above (Shop by Category), between banners, and below (Trusted Shops)
    composeTestRule.onNodeWithTag("home_lazy_column")
      .performScrollToNode(hasTestTag("home_trusted_shops_section"))
    composeTestRule.waitForIdle()

    val categoryBounds = composeTestRule.onNodeWithTag("home_shop_by_category_section").getUnclippedBoundsInRoot()
    val seller1Bounds = composeTestRule.onNodeWithTag("promo_banner_item_seller_1").getUnclippedBoundsInRoot()
    val seller2Bounds = composeTestRule.onNodeWithTag("promo_banner_item_seller_2").getUnclippedBoundsInRoot()
    val trustedShopsBounds = composeTestRule.onNodeWithTag("home_trusted_shops_section").getUnclippedBoundsInRoot()

    composeTestRule.onNodeWithTag("promo_banner_item_seller_1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("promo_banner_item_seller_2").assertIsDisplayed()

    assertTrue("Expected >= 16dp above Become a Seller banner, got ${seller1Bounds.top - categoryBounds.bottom}", (seller1Bounds.top - categoryBounds.bottom) >= 16.dp)
    assertTrue("Expected >= 16dp between two banners, got ${seller2Bounds.top - seller1Bounds.bottom}", (seller2Bounds.top - seller1Bounds.bottom) >= 16.dp)
    assertTrue("Expected >= 16dp below Become a Seller banner, got ${trustedShopsBounds.top - seller2Bounds.bottom}", (trustedShopsBounds.top - seller2Bounds.bottom) >= 16.dp)
  }

  @Test
  @Config(qualifiers = "w960dp-h1000dp")
  fun homeScreenTabletMaintainsGenerousResponsiveSpacingAroundSellerBanner() {
    val promos = JSONArray().apply {
      put(promoJson("seller_tablet", "seller_banner", priority = 20, ctaText = "Become a Seller", ctaDestination = "/vendor/apply"))
    }
    val server = startPromotionsStubServer(promos)
    SupabaseConfig.projectUrlOverride = "http://127.0.0.1:${server.localPort}"
    runBlocking { MarketplaceRepository.refreshPromotionsSuspending(force = true) }

    composeTestRule.setContent {
      JTBazarTheme(darkTheme = false) {
        HomeScreen(
          onProductClick = {},
          onExploreCategoriesClick = {},
          onCartClick = {},
          onAccountClick = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("home_lazy_column")
      .performScrollToNode(hasTestTag("home_trusted_shops_section"))
    composeTestRule.waitForIdle()

    val categoryBounds = composeTestRule.onNodeWithTag("home_shop_by_category_section").getUnclippedBoundsInRoot()
    val sellerBounds = composeTestRule.onNodeWithTag("promo_banner_item_seller_tablet").getUnclippedBoundsInRoot()
    val trustedShopsBounds = composeTestRule.onNodeWithTag("home_trusted_shops_section").getUnclippedBoundsInRoot()

    assertTrue("Expected >= 20dp above banner on tablet, got ${sellerBounds.top - categoryBounds.bottom}", (sellerBounds.top - categoryBounds.bottom) >= 20.dp)
    assertTrue("Expected >= 20dp below banner on tablet, got ${trustedShopsBounds.top - sellerBounds.bottom}", (trustedShopsBounds.top - sellerBounds.bottom) >= 20.dp)
    assertTrue("Banner width should stay <= 960dp on wide tablet", (sellerBounds.right - sellerBounds.left) <= 960.dp)
  }
}
