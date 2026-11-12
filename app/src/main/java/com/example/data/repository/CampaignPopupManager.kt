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

  fun evaluateLaunchCampaign(promotions: List<SupabasePromotionDto>) {
    val nowIso = Instant.now().toString()
    val campaign = promotions.firstOrNull { item ->
      item.showOnLaunch &&
        (item.targetPlatform.equals("android", true) || item.targetPlatform.equals("both", true)) &&
        (item.startsAt.isNullOrBlank() || item.startsAt <= nowIso) &&
        (item.endsAt.isNullOrBlank() || item.endsAt >= nowIso) &&
        (!item.bannerImageUrl.isNullOrBlank() || item.bannerImageUrls.any { it.isNotBlank() })
    } ?: return

    val signature = listOf(
      campaign.id,
      campaign.title,
      campaign.bannerImageUrl.orEmpty(),
      campaign.bannerImageUrls.joinToString("|"),
      campaign.startsAt.orEmpty(),
      campaign.endsAt.orEmpty()
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
