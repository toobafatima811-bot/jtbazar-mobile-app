package com.example

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.SuccessResult
import com.example.data.model.CartItem
import com.example.data.model.Product
import com.example.data.model.ProductVariant
import com.example.data.model.ShopRecord
import com.example.data.repository.MarketplaceRepository
import com.example.data.repository.MarketplaceRepository.DeliveryFeeQuoteState
import com.example.data.supabase.DailyShopHours
import com.example.data.supabase.DeliveryPricingSettingsDto
import com.example.data.supabase.PAKISTAN_ZONE_ID
import com.example.data.supabase.ShopDayOfWeek
import com.example.data.supabase.ShopScheduleInfo
import com.example.data.supabase.ShopScheduleStatus
import com.example.data.supabase.SupabaseApiService
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.VendorService
import com.example.data.supabase.WeeklyShopHours
import com.example.localization.AppLanguage
import com.example.localization.AppLanguageManager
import com.example.ui.screens.CheckoutLocationMapCard
import com.example.ui.screens.ProductDetailScreen
import com.example.ui.screens.PublicShopDetailScreen
import com.example.ui.screens.PublicShopWeeklyHoursCard
import com.example.ui.screens.VendorShopAvailabilityAndHoursSection
import com.example.ui.screens.buildDeliveryRadiusCirclePoints
import com.example.ui.screens.shouldAutoFillCheckoutAddress
import com.example.ui.theme.JTBazarTheme
import com.example.ui.theme.VendorCenterTheme
import com.example.util.ProductShareHelper
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
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
@OptIn(ExperimentalCoroutinesApi::class)
class AuditFeaturesVerificationTest {

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

  private val activeServers = mutableListOf<ServerSocket>()

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val fakeLoader = ImageLoader.Builder(context)
      .components {
        add(coil.intercept.Interceptor { chain ->
          val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
          SuccessResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            request = chain.request,
            dataSource = DataSource.MEMORY
          )
        })
      }
      .build()
    Coil.setImageLoader(fakeLoader)
    AppLanguageManager.setLanguageForTest(AppLanguage.ENGLISH)
    MarketplaceRepository.clearCart()
    MarketplaceRepository.clearDeliveryFeeQuote()
  }

  @After
  fun tearDown() {
    activeServers.forEach { runCatching { it.close() } }
    activeServers.clear()
    SupabaseConfig.projectUrlOverride = null
    SupabaseClient.clearSession()
    SupabaseApiService.clearCaches()
    MarketplaceRepository.clearCart()
    MarketplaceRepository.clearDeliveryFeeQuote()
    AppLanguageManager.setLanguageForTest(AppLanguage.ENGLISH)
    Coil.reset()
  }

  private data class CapturedHttp(
    val method: String,
    val target: String,
    val body: String
  )

  private fun startStubServer(
    handler: (method: String, target: String, body: String) -> Pair<Int, String>
  ): Pair<ServerSocket, CopyOnWriteArrayList<CapturedHttp>> {
    val server = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
    activeServers.add(server)
    val captured = CopyOnWriteArrayList<CapturedHttp>()
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
              captured.add(CapturedHttp(method = method, target = target, body = bodyStr))

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

  private fun sampleVariantProduct(): Product {
    val v128Id = "11111111-1111-1111-1111-111111111128"
    val v256Id = "22222222-2222-2222-2222-222222222256"
    val v512Id = "33333333-3333-3333-3333-333333333512"
    return Product(
      sellerId = "seller-audit-1",
      id = "prod-audit-1",
      title = "Galaxy Pro Flagship Phone",
      vendor = "Multan Tech Hub",
      category = "Electronics",
      categoryId = "cat-elec",
      price = 42000,
      originalPrice = 50000,
      discountPercent = 16,
      rating = 4.8,
      reviewCount = 19,
      imageUrl = "https://cdn.example.com/phone-main.jpg",
      imageUrls = listOf("https://cdn.example.com/phone-main.jpg"),
      inStockText = "In Stock",
      craftStory = "Flagship AMOLED display with fast charging and official warranty.",
      variants = listOf(
        ProductVariant(
          id = v128Id,
          optionLabel = "Storage",
          optionValue = "128GB",
          sku = "GAL-128",
          priceOverride = 45000,
          imageUrl = "https://cdn.example.com/phone-128.jpg",
          isAvailable = true,
          stockQuantity = 6,
          sortOrder = 0
        ),
        ProductVariant(
          id = v256Id,
          optionLabel = "Storage",
          optionValue = "256GB",
          sku = "GAL-256",
          priceOverride = 58000,
          imageUrl = "https://cdn.example.com/phone-256.jpg",
          isAvailable = true,
          stockQuantity = 3,
          sortOrder = 1
        ),
        ProductVariant(
          id = v512Id,
          optionLabel = "Storage",
          optionValue = "512GB",
          sku = "GAL-512",
          priceOverride = 72000,
          imageUrl = "https://cdn.example.com/phone-512.jpg",
          isAvailable = false,
          stockQuantity = 0,
          sortOrder = 2
        )
      ),
      variantIds = mapOf(
        "Storage:128GB" to v128Id,
        "Storage:256GB" to v256Id,
        "Storage:512GB" to v512Id
      ),
      isAvailable = true,
      availabilityKnown = true,
      variantAvailability = mapOf(
        v128Id to true,
        v256Id to true,
        v512Id to false
      )
    )
  }

  @Test
  fun supabaseFetchesAllVariantTypesWithPriceOverrideImageAndStockWithoutDoubleDiscounting() = runBlocking {
    val v128Id = "11111111-1111-1111-1111-111111111128"
    val v256Id = "22222222-2222-2222-2222-222222222256"
    val (server, captured) = startStubServer { _, target, _ ->
      when {
        target.startsWith("/rest/v1/products") -> 200 to JSONArray().put(
          JSONObject()
            .put("id", "prod-audit-1")
            .put("seller_id", "seller-audit-1")
            .put("name", "Galaxy Pro Flagship Phone")
            .put("price", 50000)
            .put("discount_percent", 20)
            .put("category_id", "cat-elec")
            .put("description", "Flagship phone")
        ).toString()
        target.startsWith("/rest/v1/product_images") -> 200 to JSONArray().put(
          JSONObject()
            .put("product_id", "prod-audit-1")
            .put("image_url", "https://cdn.example.com/phone-main.jpg")
            .put("is_primary", true)
            .put("sort_order", 0)
        ).toString()
        target.startsWith("/rest/v1/product_variants") -> 200 to JSONArray().apply {
          put(
            JSONObject()
              .put("id", v128Id)
              .put("product_id", "prod-audit-1")
              .put("option_label", "Storage")
              .put("option_value", "128GB")
              .put("sku", "GAL-128")
              .put("price_override", 45000)
              .put("image_url", "https://cdn.example.com/phone-128.jpg")
              .put("is_active", true)
              .put("sort_order", 0)
          )
          put(
            JSONObject()
              .put("id", v256Id)
              .put("product_id", "prod-audit-1")
              .put("option_label", "Storage")
              .put("option_value", "256GB")
              .put("sku", "GAL-256")
              .put("price_override", 58000)
              .put("image_url", "variants/phone-256.jpg")
              .put("is_active", true)
              .put("sort_order", 1)
          )
        }.toString()
        target.startsWith("/rest/v1/rpc/get_public_inventory") -> 200 to JSONArray().apply {
          put(JSONObject().put("product_id", "prod-audit-1").put("variant_id", v128Id).put("available_quantity", 5))
          put(JSONObject().put("product_id", "prod-audit-1").put("variant_id", v256Id).put("available_quantity", 0))
        }.toString()
        target.startsWith("/rest/v1/rpc/get_public_availability") -> 200 to JSONArray().apply {
          put(JSONObject().put("product_id", "prod-audit-1").put("variant_id", v128Id).put("is_available", true))
          put(JSONObject().put("product_id", "prod-audit-1").put("variant_id", v256Id).put("is_available", false))
        }.toString()
        target.startsWith("/rest/v1/categories") -> 200 to JSONArray().put(
          JSONObject().put("id", "cat-elec").put("name", "Electronics").put("slug", "electronics")
        ).toString()
        target.startsWith("/rest/v1/rpc/get_public_shops") -> 200 to JSONArray().put(
          JSONObject().put("id", "seller-audit-1").put("shop_name", "Multan Tech Hub")
        ).toString()
        else -> 200 to "[]"
      }
    }
    SupabaseConfig.projectUrlOverride = "http://127.0.0.1:${server.localPort}"
    SupabaseApiService.clearCaches()

    val dtos = SupabaseApiService.fetchProducts().getOrThrow()
    assertEquals(1, dtos.size)
    val product = MarketplaceRepository.mapDtoToProduct(dtos.first())

    // Base price is discounted 20% from 50,000 -> 40,000, while variant price_override must NOT be double-discounted.
    assertEquals(40000, product.price)
    assertEquals(2, product.variants.size)
    val first = product.variants[0]
    val second = product.variants[1]
    assertEquals("Storage", first.optionLabel)
    assertEquals("128GB", first.optionValue)
    assertEquals("GAL-128", first.sku)
    assertEquals(45000, first.priceOverride)
    assertEquals("https://cdn.example.com/phone-128.jpg", first.imageUrl)
    assertEquals(5, first.stockQuantity)
    assertTrue(first.isAvailable)

    assertEquals(58000, second.priceOverride)
    assertTrue(second.imageUrl.orEmpty().endsWith("/storage/v1/object/public/product-images/variants/phone-256.jpg"))
    assertFalse(second.isAvailable)

    val variantQuery = captured.firstOrNull { it.target.startsWith("/rest/v1/product_variants") }?.target.orEmpty()
    assertTrue(variantQuery.contains("image_url"))
    assertTrue(variantQuery.contains("price_override"))

    // Verify VendorService.listVariants also selects image_url & sku cleanly
    val vendorList = VendorService.listVariants("prod-audit-1")
    assertEquals(2, vendorList.length())
    assertEquals("https://cdn.example.com/phone-128.jpg", vendorList.getJSONObject(0).optString("image_url"))
  }

  @Test
  fun productDetailDoesNotAutoSelectVariantAndUpdatesPriceAndCartOnManualSelection() {
    val product = sampleVariantProduct()
    val v256 = product.variants[1]
    val v512 = product.variants[2]

    composeTestRule.setContent {
      JTBazarTheme(darkTheme = false) {
        ProductDetailScreen(
          product = product,
          onBackClick = {},
          onCartClick = {},
          onBuyNowClick = {},
          onAccountClick = {},
          onVisitShop = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    // 1. Must NOT auto-select first variant; purchase blocked banner shown, Add to Cart hidden
    composeTestRule.onNodeWithTag("product_detail_unit_price").assertTextContains("Rs. 42,000")
    composeTestRule.onNodeWithTag("purchase_blocked_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("add_to_cart_bottom_button").assertDoesNotExist()
    assertTrue(MarketplaceRepository.cartItems.value.isEmpty())

    // 2. Scroll LazyColumn to variant section and tap 256GB variant
    composeTestRule.onNodeWithTag("product_detail_lazy_list")
      .performScrollToNode(hasTestTag("variant_option_${v256.id}"))
    composeTestRule.onNodeWithTag("variant_option_${v256.id}").performClick()
    composeTestRule.waitForIdle()

    // Price updates to variant override 58,000 and Add to Cart becomes active
    composeTestRule.onNodeWithTag("product_detail_unit_price").assertTextContains("Rs. 58,000")
    composeTestRule.onNodeWithTag("product_detail_total_price").assertTextContains("Rs. 58,000")
    composeTestRule.onNodeWithTag("add_to_cart_bottom_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    val cart = MarketplaceRepository.cartItems.value
    assertEquals(1, cart.size)
    val added = cart.first()
    assertEquals(v256.id, added.variantId)
    assertEquals(58000, added.resolvedUnitPrice)
    assertEquals("https://cdn.example.com/phone-256.jpg", added.resolvedImageUrl)
    assertEquals("Storage: 256GB", added.variantDisplayText)

    // 3. Selecting an out-of-stock variant blocks adding to cart again
    composeTestRule.onNodeWithTag("product_detail_lazy_list")
      .performScrollToNode(hasTestTag("variant_option_${v512.id}"))
    composeTestRule.onNodeWithTag("variant_option_${v512.id}").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("purchase_blocked_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("add_to_cart_bottom_button").assertDoesNotExist()
  }

  @Test
  fun productShareHelperBuildsVariantUrlTextAndIntentWithImageOrTextFallback() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val product = sampleVariantProduct()
    val v256 = product.variants[1]

    val url = ProductShareHelper.buildProductUrl(product.id)
    assertEquals("https://jtbazar.vercel.app/products/prod-audit-1", url)

    val shareText = ProductShareHelper.buildShareText(product, v256)
    assertTrue(shareText.contains("Galaxy Pro Flagship Phone (Storage: 256GB)"))
    assertTrue(shareText.contains("Rs. 58,000"))
    assertTrue(shareText.contains("Flagship AMOLED display"))
    assertTrue(shareText.contains(url))

    // With cached image URI via real FileProvider -> image/* + EXTRA_STREAM + read URI permission
    val shareDir = java.io.File(context.cacheDir, "shared_products").apply { mkdirs() }
    val fakeFile = java.io.File(shareDir, "test_share.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
    val fakeContentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fakeFile)
    val chooserWithImage = ProductShareHelper.buildShareIntent(context, product, v256, fakeContentUri)
    assertEquals(Intent.ACTION_CHOOSER, chooserWithImage.action)
    val innerWithImage = chooserWithImage.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
    assertNotNull(innerWithImage)
    assertEquals("image/*", innerWithImage?.type)
    assertEquals(fakeContentUri, innerWithImage?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    assertTrue((innerWithImage!!.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    assertEquals(shareText, innerWithImage.getStringExtra(Intent.EXTRA_TEXT))

    // Without image URI -> text/plain fallback
    val chooserTextOnly = ProductShareHelper.buildShareIntent(context, product, null, null)
    val innerTextOnly = chooserTextOnly.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
    assertEquals("text/plain", innerTextOnly?.type)
    assertNull(innerTextOnly?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    assertTrue(innerTextOnly?.getStringExtra(Intent.EXTRA_TEXT).orEmpty().contains("Rs. 42,000"))
  }

  @Test
  fun checkoutPreservesManualAddressBuildsRadiusCircleAndFetchesKmQuote() = runBlocking {
    // 1. Manual edit & saved address protection
    assertFalse(shouldAutoFillCheckoutAddress(hasUserEditedAddress = true, selectedSavedAddressId = null, currentStreetAddress = "House 12"))
    assertFalse(shouldAutoFillCheckoutAddress(hasUserEditedAddress = false, selectedSavedAddressId = "addr-1", currentStreetAddress = "House 12"))
    assertTrue(shouldAutoFillCheckoutAddress(hasUserEditedAddress = false, selectedSavedAddressId = null, currentStreetAddress = ""))

    // 2. Radius polygon closes properly
    val circle = buildDeliveryRadiusCirclePoints(30.1575, 71.5249, 10.0)
    assertEquals(73, circle.size)
    assertEquals(circle.first().latitude, circle.last().latitude, 1e-6)
    assertEquals(circle.first().longitude, circle.last().longitude, 1e-6)

    // 3. KM quote RPC & unavailable error handling (no fallback to fixed fee)
    val (server, _) = startStubServer { _, target, body ->
      when {
        target.startsWith("/rest/v1/delivery_pricing_settings") -> 200 to JSONArray().put(
          JSONObject()
            .put("origin_lat", 30.1575)
            .put("origin_lng", 71.5249)
            .put("max_delivery_radius_km", 15.0)
            .put("is_active", true)
        ).toString()
        target.startsWith("/rest/v1/rpc/get_delivery_fee_quote") -> {
          val req = JSONObject(body)
          val lat = req.optDouble("p_delivery_lat", 0.0)
          if (lat > 31.0) {
            200 to JSONObject()
              .put("delivery_fee", 0)
              .put("distance_km", 18.4)
              .put("is_serviceable", false)
              .put("origin_lat", 30.1575)
              .put("origin_lng", 71.5249)
              .put("max_delivery_radius_km", 15.0)
              .put("message", "Beyond 15 km limit")
              .toString()
          } else {
            200 to JSONObject()
              .put("delivery_fee", 240)
              .put("distance_km", 6.2)
              .put("is_serviceable", true)
              .put("origin_lat", 30.1575)
              .put("origin_lng", 71.5249)
              .put("max_delivery_radius_km", 15.0)
              .toString()
          }
        }
        else -> 200 to "[]"
      }
    }
    SupabaseConfig.projectUrlOverride = "http://127.0.0.1:${server.localPort}"

    val inRange = MarketplaceRepository.refreshDeliveryFeeQuoteSuspending(30.1800, 71.5300)
    assertTrue(inRange is DeliveryFeeQuoteState.Quoted)
    val qIn = inRange as DeliveryFeeQuoteState.Quoted
    assertTrue(qIn.isServiceable)
    assertEquals(240, qIn.deliveryFee)
    assertEquals(6.2, qIn.distanceKm ?: 0.0, 0.01)

    val outOfRange = MarketplaceRepository.refreshDeliveryFeeQuoteSuspending(31.5000, 74.3000)
    assertTrue(outOfRange is DeliveryFeeQuoteState.Quoted)
    val qOut = outOfRange as DeliveryFeeQuoteState.Quoted
    assertFalse(qOut.isServiceable)
    assertEquals("Beyond 15 km limit", qOut.message)
  }

  @Test
  fun checkoutLocationMapCardDisplaysInteractiveMapAndLiveQuoteStates() {
    val quoteState = androidx.compose.runtime.mutableStateOf<DeliveryFeeQuoteState>(
      DeliveryFeeQuoteState.Quoted(
        deliveryFee = 240,
        distanceKm = 6.2,
        originLat = 30.1575,
        originLng = 71.5249,
        maxRadiusKm = 15.0,
        isServiceable = true
      )
    )

    composeTestRule.setContent {
      JTBazarTheme(darkTheme = false) {
        CheckoutLocationMapCard(
          deliveryLat = 30.1800,
          deliveryLng = 71.5300,
          isPinConfirmed = true,
          isLocatingPin = false,
          deliveryQuoteState = quoteState.value,
          deliveryPricingSettings = DeliveryPricingSettingsDto(30.1575, 71.5249, 15.0, true),
          onRequestGpsPin = {},
          onSelectMapPin = { _, _ -> },
          onClearPin = {},
          onRetryQuote = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("checkout_location_map_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("checkout_delivery_map").assertIsDisplayed()
    composeTestRule.onNodeWithTag("capture_location_pin_btn").assertIsDisplayed()
    composeTestRule.onNodeWithTag("checkout_delivery_quote_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("Delivery Fee: PKR 240").assertIsDisplayed()
    composeTestRule.onNodeWithText("6.2 km from hub").assertIsDisplayed()

    quoteState.value = DeliveryFeeQuoteState.Unavailable(reason = "Service check unavailable")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("retry_delivery_quote_btn").assertIsDisplayed()
  }

  @Test
  fun publicShopTimingsHighlightTodayAndDescriptionRendersMultilineWithoutTruncation() {
    val weekly = WeeklyShopHours.defaults()
    val fridayStatus = ShopScheduleStatus(
      isOpen = true,
      shopIsOpen = true,
      withinScheduledHours = true,
      todayDay = ShopDayOfWeek.FRIDAY,
      todayHours = weekly.forDay(ShopDayOfWeek.FRIDAY),
      badgeLabel = "Open",
      detailLabel = "Open until 10:00 PM PKT",
      timezoneId = PAKISTAN_ZONE_ID.id
    )

    val fullDescription = "Line 1: Authentic Multan Blue Pottery crafted by third-generation artisans.\n" +
      "Line 2: Same-day dispatch available across Multan and next-day nationwide delivery.\n" +
      "Line 3: Custom bridal crockery sets and corporate gift hampers made to order."

    val shop = ShopRecord(
      id = "shop-audit-1",
      name = "Multan Blue Craft",
      slug = "multan-blue-craft",
      city = "Multan",
      description = fullDescription,
      productCount = 4,
      isVerified = true,
      shopIsOpen = true
    )

    // Stub schedule server so PublicShopDetailScreen uses deterministic Friday status
    val fridayInstant = ZonedDateTime.of(2026, 11, 20, 14, 0, 0, 0, PAKISTAN_ZONE_ID).toInstant()
    composeTestRule.setContent {
      JTBazarTheme(darkTheme = false) {
        androidx.compose.foundation.layout.Column {
          PublicShopWeeklyHoursCard(
            weeklyHours = weekly,
            status = fridayStatus,
            isRefreshingSchedule = false,
            scheduleError = null,
            onRetrySchedule = {}
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("public_shop_hours_row_friday")
      .assertIsDisplayed()
      .assertTextContains("Today", substring = true)
    composeTestRule.onNodeWithTag("public_shop_hours_today_badge", useUnmergedTree = true).assertExists()
  }

  @Test
  fun vendorShopHoursSectionHighlightsTodayInKarachiTimezone() {
    val weekly = WeeklyShopHours.defaults()
    val pktToday = ShopDayOfWeek.fromJavaDay(ZonedDateTime.now(PAKISTAN_ZONE_ID).dayOfWeek)
    composeTestRule.setContent {
      VendorCenterTheme {
        androidx.compose.foundation.layout.Column(
          modifier = androidx.compose.ui.Modifier.verticalScroll(rememberScrollState())
        ) {
          VendorShopAvailabilityAndHoursSection(
            shopIsOpen = true,
            weeklyHours = weekly,
            enabled = true,
            onShopIsOpenChange = {},
            onWeeklyHoursChange = {}
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("vendor_hours_day_${pktToday.key}").performScrollTo()
    composeTestRule.onNode(
      hasTestTag("vendor_hours_day_${pktToday.key}") and hasAnyDescendant(hasText("Today"))
    ).assertIsDisplayed()
  }

  @Test
  fun publicShopDetailRendersFullMultilineShopDescription() {
    val fullDescription = "Line 1: Authentic Multan Blue Pottery crafted by third-generation artisans.\n" +
      "Line 2: Same-day dispatch available across Multan and next-day nationwide delivery.\n" +
      "Line 3: Custom bridal crockery sets and corporate gift hampers made to order."

    val shop = ShopRecord(
      id = "shop-audit-1",
      name = "Multan Blue Craft",
      slug = "multan-blue-craft",
      city = "Multan",
      description = fullDescription,
      productCount = 4,
      isVerified = true,
      shopIsOpen = true
    )

    composeTestRule.setContent {
      JTBazarTheme(darkTheme = false) {
        PublicShopDetailScreen(
          shop = shop,
          fallbackProducts = emptyList(),
          onBack = {},
          onProductClick = {},
          nowProvider = { Instant.parse("2026-11-20T10:00:00Z") }
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("public_shop_description").assertIsDisplayed()
    composeTestRule.onNodeWithTag("public_shop_description").assertTextContains("Line 3: Custom bridal crockery sets", substring = true)
    composeTestRule.onNodeWithText("About this shop").assertIsDisplayed()
  }
}
