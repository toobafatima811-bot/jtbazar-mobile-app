package com.example.data.repository

import com.example.data.supabase.SupabasePromotionDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * Keeps launch campaign presentation global and limited to once per campaign
 * signature during the current Android process.
 */
object CampaignPopupManager {
  private var shownCampaignSignature: String? = null
  private var hasShownInThisProcess: Boolean = false

  private val _activePopupCampaign = MutableStateFlow<SupabasePromotionDto?>(null)
  val activePopupCampaign: StateFlow<SupabasePromotionDto?> = _activePopupCampaign.asStateFlow()

  fun evaluateLaunchCampaign(promotions: List<SupabasePromotionDto>, nowMillis: Long = System.currentTimeMillis()) {
    val campaign = promotions
      .asSequence()
      .filter { item ->
        item.showOnLaunch &&
          PromotionCampaignFilter.isApprovedAndActive(item) &&
          PromotionCampaignFilter.isPlatformEligible(item.targetPlatform) &&
          PromotionCampaignFilter.isWithinSchedule(item, nowMillis) &&
          item.primaryBannerImageUrl() != null
      }
      .sortedByDescending { it.priority }
      .firstOrNull()

    if (campaign == null) {
      _activePopupCampaign.value = null
      return
    }

    val signature = listOf(
      campaign.id,
      campaign.title,
      campaign.bannerImageUrl.orEmpty(),
      campaign.bannerImageUrls.joinToString("|"),
      campaign.startsAt.orEmpty(),
      campaign.endsAt.orEmpty(),
      campaign.updatedAt.orEmpty()
    ).joinToString("_")

    if (hasShownInThisProcess && shownCampaignSignature == signature) return

    shownCampaignSignature = signature
    hasShownInThisProcess = true
    _activePopupCampaign.value = campaign
  }

  fun dismiss() {
    _activePopupCampaign.value = null
  }
}
