package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.ErrorResult
import coil.request.SuccessResult
import com.example.data.repository.BannerDestination
import com.example.data.repository.BannerPlacement
import com.example.data.repository.CampaignPopupManager
import com.example.data.repository.PromotionCampaignFilter
import com.example.data.supabase.SupabasePromotionDto
import com.example.ui.components.PromotionBannerSection
import com.example.ui.theme.JTBazarTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PromotionBannersTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val fixedNow = PromotionCampaignFilter.parseTimestampMillis("2026-11-14T12:00:00Z")!!

  @Before
  fun setUpCoil() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val fakeLoader = ImageLoader.Builder(context)
      .dispatcher(kotlinx.coroutines.Dispatchers.Main.immediate)
      .components {
        add(coil.intercept.Interceptor { chain ->
          val url = chain.request.data.toString()
          if (url.contains("broken.png")) {
            ErrorResult(
              drawable = null,
              request = chain.request,
              throwable = IOException("Simulated 404")
            )
          } else {
            val bitmap = Bitmap.createBitmap(800, 320, Bitmap.Config.ARGB_8888)
            SuccessResult(
              drawable = BitmapDrawable(context.resources, bitmap),
              request = chain.request,
              dataSource = DataSource.MEMORY
            )
          }
        })
      }
      .build()
    Coil.setImageLoader(fakeLoader)
  }

  @After
  fun tearDownCoil() {
    Coil.reset()
  }

  private fun samplePromo(
    id: String,
    position: String,
    priority: Int = 0,
    status: String = "approved",
    isActive: Boolean = true,
    platform: String = "both",
    startsAt: String? = "2026-11-01T00:00:00+00:00",
    endsAt: String? = "2026-11-30T23:59:59+00:00",
    ctaText: String? = null,
    ctaDestination: String? = null,
    showOnLaunch: Boolean = false,
    imageUrl: String = "https://xrrhbnnzjqkqnqdvayjw.supabase.co/storage/v1/object/public/product-images/promotions/$id.png",
    updatedAt: String? = "2026-11-14T10:00:00+00:00"
  ) = SupabasePromotionDto(
    id = id,
    title = "Title $id",
    description = "Description $id",
    bannerImageUrl = imageUrl,
    bannerImageUrls = listOf(imageUrl),
    ctaText = ctaText,
    ctaDestination = ctaDestination,
    displayPosition = position,
    priority = priority,
    targetPlatform = platform,
    campaignType = "banner",
    showOnLaunch = showOnLaunch,
    startsAt = startsAt,
    endsAt = endsAt,
    status = status,
    isActive = isActive,
    updatedAt = updatedAt
  )

  @Test
  fun allEightPlacementsFilterAccurately() {
    val promos = listOf(
      samplePromo("p_top", "top_promo"),
      samplePromo("p_hero", "hero_banner"),
      samplePromo("p_seller", "seller_banner"),
      samplePromo("p_cat", "category_feature"),
      samplePromo("p_above_pop", "before_popular_products"),
      samplePromo("p_strip", "product_strip"),
      samplePromo("p_below_new", "after_new_arrivals"),
      samplePromo("p_offer", "offer_section")
    )

    assertEquals(listOf("p_top"), PromotionCampaignFilter.filterForPlacement(promos, BannerPlacement.TOP_PROMO, fixedNow).map { it.id })
    assertEquals(listOf("p_hero"), PromotionCampaignFilter.filterForPlacement(promos, BannerPlacement.HERO_BANNER, fixedNow).map { it.id })
    assertEquals(listOf("p_seller"), PromotionCampaignFilter.filterForPlacement(promos, BannerPlacement.SELLER_BANNER, fixedNow).map { it.id })
    assertEquals(listOf("p_cat"), PromotionCampaignFilter.filterForPlacement(promos, BannerPlacement.CATEGORY_FEATURE, fixedNow).map { it.id })
    assertEquals(listOf("p_above_pop"), PromotionCampaignFilter.filterForPlacement(promos, BannerPlacement.ABOVE_POPULAR_PRODUCTS, fixedNow).map { it.id })
    assertEquals(listOf("p_strip"), PromotionCampaignFilter.filterForPlacement(promos, BannerPlacement.PRODUCT_STRIP, fixedNow).map { it.id })
    assertEquals(listOf("p_below_new"), PromotionCampaignFilter.filterForPlacement(promos, BannerPlacement.BELOW_NEW_ARRIVALS, fixedNow).map { it.id })
    assertEquals(listOf("p_offer"), PromotionCampaignFilter.filterForPlacement(promos, BannerPlacement.OFFER_SECTION, fixedNow).map { it.id })
  }

  @Test
  fun filtersExpiredRejectedInactiveWebOnlyAndDuplicatesAndSortsByPriority() {
    val sharedUrl = "https://xrrhbnnzjqkqnqdvayjw.supabase.co/storage/v1/object/public/product-images/promotions/shared.png"
    val promos = listOf(
      samplePromo("low_prio", "hero_banner", priority = 5),
      samplePromo("high_prio", "hero_banner", priority = 20),
      samplePromo("dup_high_id", "hero_banner", priority = 20, imageUrl = sharedUrl),
      samplePromo("dup_same_image", "hero_banner", priority = 10, imageUrl = sharedUrl),
      samplePromo("expired", "hero_banner", priority = 50, endsAt = "2026-11-14T11:59:59+00:00"),
      samplePromo("scheduled_future", "hero_banner", priority = 50, startsAt = "2026-11-14T12:05:00+00:00"),
      samplePromo("rejected", "hero_banner", priority = 50, status = "rejected"),
      samplePromo("pending", "hero_banner", priority = 50, status = "pending_review"),
      samplePromo("disabled", "hero_banner", priority = 50, isActive = false),
      samplePromo("web_only", "hero_banner", priority = 50, platform = "web"),
      samplePromo("launch_popup", "hero_banner", priority = 50, showOnLaunch = true)
    )

    val filtered = PromotionCampaignFilter.filterForPlacement(promos, BannerPlacement.HERO_BANNER, fixedNow)
    assertEquals(listOf("high_prio", "dup_high_id", "low_prio"), filtered.map { it.id })
  }

  @Test
  fun editedBannerUpdatesFilterAndPopupStateCleanly() {
    val v1 = samplePromo("editable", "category_feature", priority = 10, ctaText = "Shop Now", ctaDestination = "/categories")
    val v2Edited = v1.copy(
      displayPosition = "after_new_arrivals",
      ctaText = "Buy Deals",
      ctaDestination = "/budget",
      updatedAt = "2026-11-14T12:01:00+00:00"
    )

    assertEquals(1, PromotionCampaignFilter.filterForPlacement(listOf(v1), BannerPlacement.CATEGORY_FEATURE, fixedNow).size)
    assertTrue(PromotionCampaignFilter.filterForPlacement(listOf(v2Edited), BannerPlacement.CATEGORY_FEATURE, fixedNow).isEmpty())
    val moved = PromotionCampaignFilter.filterForPlacement(listOf(v2Edited), BannerPlacement.BELOW_NEW_ARRIVALS, fixedNow)
    assertEquals("Buy Deals", moved.single().ctaText)
    assertEquals("/budget", moved.single().ctaDestination)

    val popup = samplePromo("pop_1", "hero_banner", showOnLaunch = true)
    CampaignPopupManager.evaluateLaunchCampaign(listOf(popup), fixedNow)
    assertEquals("pop_1", CampaignPopupManager.activePopupCampaign.value?.id)

    CampaignPopupManager.evaluateLaunchCampaign(listOf(popup.copy(status = "rejected")), fixedNow)
    assertNull(CampaignPopupManager.activePopupCampaign.value)
  }

  @Test
  fun destinationResolutionHandlesInAppAndExternalUrls() {
    assertEquals(BannerDestination.Categories, PromotionCampaignFilter.resolveDestination("/categories"))
    assertEquals(BannerDestination.CategoryByQuery("pottery"), PromotionCampaignFilter.resolveDestination("https://jtbazar.vercel.app/category/pottery"))
    assertEquals(BannerDestination.ProductById("abc-123"), PromotionCampaignFilter.resolveDestination("/products/abc-123"))
    assertEquals(BannerDestination.ShopsList, PromotionCampaignFilter.resolveDestination("/shops"))
    assertEquals(BannerDestination.ShopByQuery("multan-bazaar"), PromotionCampaignFilter.resolveDestination("/shops/multan-bazaar"))
    assertEquals(BannerDestination.BudgetShopping, PromotionCampaignFilter.resolveDestination("/budget"))
    assertEquals(BannerDestination.Discover, PromotionCampaignFilter.resolveDestination("/discover"))
    assertEquals(BannerDestination.VendorCenter, PromotionCampaignFilter.resolveDestination("/vendor"))
    assertEquals(BannerDestination.ExternalUrl("https://example.com/offer"), PromotionCampaignFilter.resolveDestination("https://example.com/offer"))
    assertNull(PromotionCampaignFilter.resolveDestination("  "))
  }

  @Test
  fun composeBannerRendersImageOnlyOptionalOverlayCtaAndHidesFailedImages() {
    var clickedDest: String? = null
    val withCta = samplePromo(
      id = "with_cta",
      position = "hero_banner",
      ctaText = "Shop Now",
      ctaDestination = "/categories"
    )
    val withoutCta = samplePromo(
      id = "no_cta",
      position = "hero_banner",
      ctaText = null,
      ctaDestination = null
    )
    val brokenBanner = samplePromo(
      id = "broken",
      position = "hero_banner",
      ctaText = "Broken CTA",
      ctaDestination = "/categories",
      imageUrl = "https://xrrhbnnzjqkqnqdvayjw.supabase.co/storage/v1/object/public/product-images/promotions/broken.png"
    )

    composeTestRule.setContent {
      JTBazarTheme(darkTheme = false) {
        PromotionBannerSection(
          placement = BannerPlacement.HERO_BANNER,
          promotions = listOf(withCta, withoutCta, brokenBanner),
          onDestinationClick = { clickedDest = it }
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("promo_placement_hero_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("promo_banner_item_with_cta").assertIsDisplayed()
    composeTestRule.onNodeWithTag("promo_banner_cta_with_cta").assertIsDisplayed()
    composeTestRule.onNodeWithTag("promo_banner_cta_with_cta").assertTextEquals("Shop Now")

    // Title and description must NOT be rendered outside/below the banner image
    composeTestRule.onNodeWithText("Title with_cta").assertDoesNotExist()
    composeTestRule.onNodeWithText("Description with_cta").assertDoesNotExist()
    composeTestRule.onNodeWithText("Title no_cta").assertDoesNotExist()
    composeTestRule.onNodeWithText("Description no_cta").assertDoesNotExist()

    // Banner without CTA must NOT display a CTA button
    composeTestRule.onNodeWithTag("promo_banner_item_no_cta").assertIsDisplayed()
    composeTestRule.onNodeWithTag("promo_banner_cta_no_cta").assertDoesNotExist()

    // Failed image banner collapses gracefully
    composeTestRule.onNodeWithTag("promo_banner_item_broken").assertDoesNotExist()

    composeTestRule.onNodeWithTag("promo_banner_cta_with_cta").performClick()
    composeTestRule.waitForIdle()
    assertEquals("/categories", clickedDest)
  }
}
