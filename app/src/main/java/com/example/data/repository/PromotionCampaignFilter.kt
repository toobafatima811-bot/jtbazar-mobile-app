package com.example.data.repository

import com.example.data.supabase.SupabasePromotionDto
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Canonical homepage placements matching Admin Dashboard `promotions.display_position`.
 */
enum class BannerPlacement(
  val canonicalKey: String,
  private val aliases: Set<String>
) {
  TOP_PROMO(
    canonicalKey = "top_promo",
    aliases = setOf("top_promo", "top_promo_bar", "top_bar", "promo_bar")
  ),
  HERO_BANNER(
    canonicalKey = "hero_banner",
    aliases = setOf("hero_banner", "hero", "homepage_hero")
  ),
  SELLER_BANNER(
    canonicalKey = "seller_banner",
    aliases = setOf("seller_banner", "become_seller_banner", "become_a_seller", "become_a_seller_banner", "seller_promo")
  ),
  CATEGORY_FEATURE(
    canonicalKey = "category_feature",
    aliases = setOf("category_feature", "categories_feature", "category_banner")
  ),
  ABOVE_POPULAR_PRODUCTS(
    canonicalKey = "before_popular_products",
    aliases = setOf("before_popular_products", "above_popular_products", "above_featured_products", "before_featured_products")
  ),
  PRODUCT_STRIP(
    canonicalKey = "product_strip",
    aliases = setOf("product_strip", "products_strip")
  ),
  BELOW_NEW_ARRIVALS(
    canonicalKey = "after_new_arrivals",
    aliases = setOf("after_new_arrivals", "below_new_arrivals")
  ),
  OFFER_SECTION(
    canonicalKey = "offer_section",
    aliases = setOf("offer_section", "offers_section", "deals_section")
  );

  companion object {
    fun fromRaw(raw: String?): BannerPlacement? {
      val normalized = raw
        ?.trim()
        ?.lowercase()
        ?.replace('-', '_')
        ?.replace(' ', '_')
        ?.takeIf { it.isNotBlank() }
        ?: return null
      return entries.firstOrNull { placement -> normalized in placement.aliases }
    }
  }
}

sealed interface BannerDestination {
  data object Home : BannerDestination
  data object Categories : BannerDestination
  data class CategoryByQuery(val query: String) : BannerDestination
  data class ProductById(val productId: String) : BannerDestination
  data object ShopsList : BannerDestination
  data class ShopByQuery(val query: String) : BannerDestination
  data object BudgetShopping : BannerDestination
  data object Discover : BannerDestination
  data object Wishlist : BannerDestination
  data object CartCheckout : BannerDestination
  data object VendorCenter : BannerDestination
  data object Account : BannerDestination
  data class ExternalUrl(val url: String) : BannerDestination
}

object PromotionCampaignFilter {
  private val ANDROID_PLATFORMS = setOf("", "android", "both", "all", "mobile", "app", "web_and_android", "website_and_android")
  private val ALLOWED_BANNER_CAMPAIGN_TYPES = setOf("", "banner", "ad", "ads", "image", "image_banner", "promo", "promotion", "hero", "seller_banner")

  fun parseTimestampMillis(raw: String?, isEndBound: Boolean = false): Long? {
    val clean = raw?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: return null
    clean.toLongOrNull()?.let { numeric ->
      return if (numeric in 1_000_000_000L..9_999_999_999L) numeric * 1000L else numeric
    }
    runCatching { return Instant.parse(clean).toEpochMilli() }
    runCatching { return OffsetDateTime.parse(clean).toInstant().toEpochMilli() }
    // Handle PostgREST / Postgres timestamps like "2026-11-14 10:30:00+00" or "2026-11-14T10:30:00"
    val normalizedSpace = clean.replace(' ', 'T')
    runCatching { return OffsetDateTime.parse(normalizedSpace).toInstant().toEpochMilli() }
    runCatching {
      return LocalDateTime.parse(normalizedSpace).toInstant(ZoneOffset.UTC).toEpochMilli()
    }
    runCatching {
      val date = LocalDate.parse(clean)
      val time = if (isEndBound) date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1L
      else date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
      return time
    }
    return null
  }

  fun isPlatformEligible(targetPlatform: String?): Boolean {
    val normalized = targetPlatform
      ?.trim()
      ?.lowercase()
      ?.replace('-', '_')
      ?.replace(' ', '_')
      .orEmpty()
    return normalized in ANDROID_PLATFORMS
  }

  fun isApprovedAndActive(promotion: SupabasePromotionDto): Boolean {
    return promotion.status.trim().equals("approved", ignoreCase = true) && promotion.isActive
  }

  fun isWithinSchedule(promotion: SupabasePromotionDto, nowMillis: Long): Boolean {
    val startMs = parseTimestampMillis(promotion.startsAt, isEndBound = false)
    val endMs = parseTimestampMillis(promotion.endsAt, isEndBound = true)
    if (!promotion.startsAt.isNullOrBlank() && startMs == null) return false
    if (!promotion.endsAt.isNullOrBlank() && endMs == null) return false
    if (startMs != null && startMs > nowMillis) return false
    if (endMs != null && endMs <= nowMillis) return false
    return true
  }

  fun isBannerCampaignType(campaignType: String?): Boolean {
    val normalized = campaignType
      ?.trim()
      ?.lowercase()
      ?.replace('-', '_')
      ?.replace(' ', '_')
      .orEmpty()
    return normalized in ALLOWED_BANNER_CAMPAIGN_TYPES
  }

  /**
   * Filters, deduplicates, and sorts active approved banner campaigns for a specific placement
   * using admin-configured priority (descending).
   */
  fun filterForPlacement(
    promotions: List<SupabasePromotionDto>,
    placement: BannerPlacement,
    nowMillis: Long = System.currentTimeMillis()
  ): List<SupabasePromotionDto> {
    val seenIds = HashSet<String>()
    val seenImages = HashSet<String>()

    return promotions
      .asSequence()
      .filter { promotion ->
        isApprovedAndActive(promotion) &&
          isPlatformEligible(promotion.targetPlatform) &&
          !promotion.showOnLaunch &&
          isBannerCampaignType(promotion.campaignType) &&
          BannerPlacement.fromRaw(promotion.displayPosition) == placement &&
          isWithinSchedule(promotion, nowMillis) &&
          promotion.primaryBannerImageUrl() != null
      }
      .sortedWith(
        compareByDescending<SupabasePromotionDto> { it.priority }
          .thenByDescending { parseTimestampMillis(it.createdAt) ?: 0L }
      )
      .filter { promotion ->
        val idKey = promotion.id.trim()
        val imageKey = promotion.primaryBannerImageUrl()?.trim().orEmpty()
        val idUnique = idKey.isBlank() || seenIds.add(idKey)
        val imageUnique = imageKey.isBlank() || seenImages.add("${placement.canonicalKey}|$imageKey")
        idUnique && imageUnique
      }
      .toList()
  }

  fun resolveDestination(rawDestination: String?): BannerDestination? {
    val raw = rawDestination?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: return null
    val normalizedPath = when {
      raw.startsWith("https://jtbazar.vercel.app", ignoreCase = true) ->
        raw.substringAfter("jtbazar.vercel.app", "").ifBlank { "/" }
      raw.startsWith("http://jtbazar.vercel.app", ignoreCase = true) ->
        raw.substringAfter("jtbazar.vercel.app", "").ifBlank { "/" }
      raw.startsWith("https://", ignoreCase = true) || raw.startsWith("http://", ignoreCase = true) ->
        return BannerDestination.ExternalUrl(raw)
      raw.startsWith("jtbazar://", ignoreCase = true) ->
        "/" + raw.removePrefix("jtbazar://").trimStart('/')
      else -> if (raw.startsWith("/")) raw else "/$raw"
    }

    val pathWithoutQuery = normalizedPath.substringBefore('?').substringBefore('#').trimEnd('/').ifBlank { "/" }
    val segments = pathWithoutQuery.trimStart('/').split('/').filter { it.isNotBlank() }
    val first = segments.firstOrNull()?.lowercase().orEmpty()
    val second = segments.getOrNull(1)

    return when (first) {
      "" -> BannerDestination.Home
      "categories", "collections" ->
        if (!second.isNullOrBlank()) BannerDestination.CategoryByQuery(second) else BannerDestination.Categories
      "category" ->
        if (!second.isNullOrBlank()) BannerDestination.CategoryByQuery(second) else BannerDestination.Categories
      "product", "products" ->
        if (!second.isNullOrBlank()) BannerDestination.ProductById(second) else BannerDestination.Categories
      "shops", "trusted-shops", "stores" ->
        if (!second.isNullOrBlank()) BannerDestination.ShopByQuery(second) else BannerDestination.ShopsList
      "shop" ->
        if (!second.isNullOrBlank()) BannerDestination.ShopByQuery(second) else BannerDestination.Categories
      "budget", "deals", "offers" -> BannerDestination.BudgetShopping
      "discover", "reels" -> BannerDestination.Discover
      "wishlist" -> BannerDestination.Wishlist
      "cart", "checkout" -> BannerDestination.CartCheckout
      "vendor", "sell", "become-a-seller" -> BannerDestination.VendorCenter
      "account", "profile" -> BannerDestination.Account
      else -> BannerDestination.ExternalUrl("https://jtbazar.vercel.app$normalizedPath")
    }
  }
}
