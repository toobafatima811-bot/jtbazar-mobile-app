package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.repository.BannerPlacement
import com.example.data.supabase.SupabasePromotionDto
import com.example.ui.theme.JTOrange

/**
 * Renders admin-approved promotional banner images for a specific homepage placement.
 * Strictly renders pure banner images (no border, no card wrapper, no white background,
 * no shadow, no external title/description block), auto-adjusts height to each image's
 * original aspect ratio, applies consistent responsive 16-22dp vertical spacing above/below
 * and 16-20dp spacing between multiple banners, and overlays an optional CTA button inside
 * the bottom-left image bounds.
 */
@Composable
fun PromotionBannerSection(
  placement: BannerPlacement,
  promotions: List<SupabasePromotionDto>,
  onDestinationClick: (String?) -> Unit,
  modifier: Modifier = Modifier
) {
  val renderablePromotions = remember(promotions) {
    promotions.filter { it.primaryBannerImageUrl() != null }
  }
  if (renderablePromotions.isEmpty()) return

  var failedIds by remember(renderablePromotions) { mutableStateOf(emptySet<String>()) }
  val visiblePromotions = remember(renderablePromotions, failedIds) {
    renderablePromotions.filterNot { it.id in failedIds }
  }
  if (visiblePromotions.isEmpty()) return

  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .clipToBounds(),
    contentAlignment = Alignment.Center
  ) {
    val isExpanded = maxWidth >= 840.dp
    val isMedium = maxWidth >= 600.dp
    val horizontalPadding = when {
      isExpanded -> 24.dp
      isMedium -> 20.dp
      else -> 14.dp
    }
    val verticalPadding = when {
      isExpanded -> 22.dp
      isMedium -> 20.dp
      else -> 18.dp
    }
    val interBannerSpacing = when {
      isExpanded -> 20.dp
      isMedium -> 18.dp
      else -> 16.dp
    }

    Column(
      modifier = Modifier
        .widthIn(max = 960.dp)
        .fillMaxWidth()
        .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        .testTag("promo_placement_${placement.canonicalKey}"),
      verticalArrangement = Arrangement.spacedBy(interBannerSpacing)
    ) {
      visiblePromotions.forEach { promotion ->
        key(promotion.id) {
          PromotionBannerItem(
            promotion = promotion,
            onDestinationClick = onDestinationClick,
            onLoadFailed = {
              failedIds = failedIds + promotion.id
            }
          )
        }
      }
    }
  }
}

@Composable
fun PromotionBannerItem(
  promotion: SupabasePromotionDto,
  onDestinationClick: (String?) -> Unit,
  modifier: Modifier = Modifier,
  onLoadFailed: (() -> Unit)? = null
) {
  val imageUrl = promotion.primaryBannerImageUrl() ?: return
  val context = LocalContext.current
  val cacheKey = remember(imageUrl, promotion.updatedAt) {
    "${imageUrl}_${promotion.updatedAt.orEmpty()}"
  }
  val imageRequest = remember(imageUrl, cacheKey, context) {
    ImageRequest.Builder(context)
      .data(imageUrl)
      .allowRgb565(true)
      .memoryCacheKey(cacheKey)
      .diskCacheKey(cacheKey)
      .crossfade(true)
      .build()
  }

  var intrinsicAspectRatio by remember(cacheKey) { mutableStateOf<Float?>(null) }
  var isLoading by remember(cacheKey) { mutableStateOf(true) }
  var isFailed by remember(cacheKey) { mutableStateOf(false) }

  if (isFailed) return

  val destination = promotion.ctaDestination?.trim()?.takeIf { it.isNotBlank() }
  val ctaText = promotion.ctaText?.trim()?.takeIf { it.isNotBlank() }

  val sizeModifier = if (intrinsicAspectRatio != null) {
    Modifier
      .fillMaxWidth()
      .aspectRatio(intrinsicAspectRatio!!)
  } else {
    Modifier
      .fillMaxWidth()
      .wrapContentHeight()
  }

  val clickModifier = if (destination != null) {
    Modifier.clickable { onDestinationClick(destination) }
  } else {
    Modifier
  }

  Box(
    modifier = modifier
      .then(sizeModifier)
      .clipToBounds()
      .then(clickModifier)
      .testTag("promo_banner_item_${promotion.id}")
  ) {
    if (isLoading && intrinsicAspectRatio == null) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(140.dp)
          .background(Color(0xFFF3EFEA))
          .testTag("promo_banner_loading_${promotion.id}")
      )
    }

    AsyncImage(
      model = imageRequest,
      contentDescription = promotion.title.ifBlank { com.example.localization.appText("Promotional Banner", "تشہیری بینر") },
      contentScale = if (intrinsicAspectRatio != null) ContentScale.Fit else ContentScale.FillWidth,
      onLoading = {
        isLoading = true
        isFailed = false
      },
      onSuccess = { state ->
        isLoading = false
        isFailed = false
        val size = state.painter.intrinsicSize
        if (size.width.isFinite() && size.height.isFinite() && size.width > 0f && size.height > 0f) {
          intrinsicAspectRatio = size.width / size.height
        }
      },
      onError = {
        isLoading = false
        isFailed = true
        onLoadFailed?.invoke()
      },
      modifier = Modifier
        .then(if (intrinsicAspectRatio != null) Modifier.fillMaxSize() else Modifier.fillMaxWidth().wrapContentHeight())
        .testTag("promo_banner_image_${promotion.id}")
    )

    if (ctaText != null) {
      Button(
        onClick = { onDestinationClick(destination) },
        colors = ButtonDefaults.buttonColors(
          containerColor = JTOrange,
          contentColor = Color.White
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        modifier = Modifier
          .align(Alignment.BottomStart)
          .padding(start = 8.dp, bottom = 8.dp, end = 8.dp)
          .widthIn(max = 220.dp)
          .defaultMinSize(minWidth = 48.dp, minHeight = 36.dp)
          .testTag("promo_banner_cta_${promotion.id}")
      ) {
        Text(
          text = ctaText,
          color = Color.White,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}
