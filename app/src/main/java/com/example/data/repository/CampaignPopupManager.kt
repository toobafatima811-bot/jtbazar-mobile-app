package com.example.data.repository

import com.example.data.model.HomepagePromotionCampaign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * Manages the launch campaign popup lifecycle across the application.
 * Ensures the popup is displayed at most once per fresh app launch session,
 * regardless of screen navigation (Home -> Categories -> Home), minimizing/resuming,
 * or background syncs. A new or updated campaign ID/version is automatically recognized.
 */
object CampaignPopupManager {
  private var shownCampaignSignature: String? = null
  private var hasShownInThisProcess: Boolean = false

  private val _activePopupCampaign = MutableStateFlow<HomepagePromotionCampaign?>(null)
  val activePopupCampaign: StateFlow<HomepagePromotionCampaign?> = _activePopupCampaign.asStateFlow()

  fun evaluateLaunchCampaign(promotions: List<HomepagePromotionCampaign>) {
    val nowIso = runCatching { Instant.now().toString() }.getOrDefault("")
    val qualifyingCampaign = promotions.firstOrNull { campaign ->
      campaign.showOnLaunch &&
        (campaign.targetPlatform.equals("android", ignoreCase = true) || campaign.targetPlatform.equals("both", ignoreCase = true)) &&
        (campaign.startsAt.isNullOrBlank() || campaign.startsAt <= nowIso) &&
        (campaign.endsAt.isNullOrBlank() || campaign.endsAt >= nowIso) &&
        (!campaign.bannerImageUrl.isNullOrBlank() || campaign.bannerImageUrls.isNotEmpty())
    } ?: return

    val signature = "${qualifyingCampaign.id}_${qualifyingCampaign.updatedAt.orEmpty()}_${qualifyingCampaign.bannerImageUrl.orEmpty()}"

    // If already presented in this process run and signature has not changed, do not show again
    if (hasShownInThisProcess && shownCampaignSignature == signature) {
      return
    }

    shownCampaignSignature = signature
    hasShownInThisProcess = true
    _activePopupCampaign.value = qualifyingCampaign
  }

  fun dismiss() {
    _activePopupCampaign.value = null
  }
}
