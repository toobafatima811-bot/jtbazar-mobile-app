package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.MarketplaceRepository
import com.example.data.supabase.SupabaseNotificationDto
import com.example.ui.theme.*

@Composable
fun NotificationsScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val notifications by MarketplaceRepository.notifications.collectAsState()

  LaunchedEffect(Unit) { MarketplaceRepository.refreshNotifications() }

  Scaffold(
    containerColor = Color.White,
    topBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("notif_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = JTTextPrimary
            )
          }
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Notifications",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = JTTextPrimary
          )
        }

        if (notifications.any { !it.isRead }) {
          TextButton(
            onClick = { MarketplaceRepository.markAllNotificationsRead() },
            modifier = Modifier.testTag("mark_all_read_button")
          ) {
            Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp), tint = JTOrange)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Mark all read", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JTOrange)
          }
        }
      }
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    if (notifications.isEmpty()) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Surface(
          shape = RoundedCornerShape(24.dp),
          color = JTSurfaceVariant,
          modifier = Modifier.size(80.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.NotificationsNone, contentDescription = null, tint = JTPurplePrimary, modifier = Modifier.size(40.dp))
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "No Notifications Yet",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = JTTextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "We'll notify you about order updates, fresh arrivals, and seller dispatches.",
          fontSize = 13.sp,
          color = JTTextSecondary,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(notifications, key = { it.id }) { notif ->
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (!notif.isRead) JTPurpleContainer.copy(alpha = 0.4f) else JTCardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (!notif.isRead) JTPurpleContainerBorder else JTBorder),
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                if (!notif.isRead) MarketplaceRepository.markNotificationRead(notif.id)
              }
              .testTag("notif_item_${notif.id}")
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.Top
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(if (!notif.isRead) JTPurplePrimary else JTSurfaceVariant),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = when (notif.type.lowercase()) {
                    "order" -> Icons.Filled.LocalShipping
                    "account" -> Icons.Filled.Person
                    "store" -> Icons.Filled.Storefront
                    else -> Icons.Filled.Notifications
                  },
                  contentDescription = null,
                  tint = if (!notif.isRead) Color.White else JTTextSecondary,
                  modifier = Modifier.size(18.dp)
                )
              }

              Spacer(modifier = Modifier.width(12.dp))

              Column(modifier = Modifier.weight(1f)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = notif.title,
                    fontWeight = if (!notif.isRead) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = JTTextPrimary
                  )
                  if (!notif.isRead) {
                    Box(
                      modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(JTOrange)
                    )
                  }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = notif.message,
                  fontSize = 12.sp,
                  color = JTTextSecondary,
                  lineHeight = 16.sp
                )

                if (notif.createdAt.isNotBlank()) {
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = notif.createdAt.take(10),
                    fontSize = 10.sp,
                    color = JTTextSecondary.copy(alpha = 0.7f)
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
