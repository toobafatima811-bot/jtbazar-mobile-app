package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.supabase.SupabasePromotionDto

/**
 * Global launch campaign modal dialog.
 * Displayed once per fresh app launch session at the application root level.
 */
@Composable
fun CampaignLaunchPopupDialog(
  campaign: SupabasePromotionDto,
  onDismiss: () -> Unit
) {
  val imageUrl = campaign.bannerImageUrls.firstOrNull() ?: campaign.bannerImageUrl
  if (imageUrl.isNullOrBlank()) return

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnBackPress = true,
      dismissOnClickOutside = true
    )
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.65f))
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = onDismiss
        )
        .padding(horizontal = 20.dp, vertical = 32.dp),
      contentAlignment = Alignment.Center
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {} // Do not close when tapping campaign image
          )
      ) {
        AsyncImage(
          model = imageUrl,
          contentDescription = campaign.title.ifBlank { "Special Campaign Offer" },
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 680.dp)
            .clip(RoundedCornerShape(18.dp)),
          contentScale = ContentScale.Fit
        )
        IconButton(
          onClick = onDismiss,
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(10.dp)
            .size(36.dp)
            .background(Color.White.copy(alpha = 0.95f), CircleShape)
        ) {
          Icon(Icons.Default.Close, contentDescription = "Close promotion", tint = Color.Black)
        }
      }
    }
  }
}
