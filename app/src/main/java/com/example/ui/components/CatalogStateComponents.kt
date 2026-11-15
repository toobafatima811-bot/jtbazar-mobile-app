package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun ProductSkeletonGrid(
  columns: Int = 2,
  rows: Int = 3,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .testTag("product_skeleton_grid"),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    repeat(rows) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        repeat(columns) {
          ProductCardSkeleton(modifier = Modifier.weight(1f))
        }
      }
    }
  }
}

@Composable
fun ProductCardSkeleton(modifier: Modifier = Modifier) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, JTBorder),
    modifier = modifier.fillMaxWidth()
  ) {
    Column {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(1.08f)
          .background(JTSurfaceVariant)
      )
      Column(
        modifier = Modifier.padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth(0.45f)
            .height(14.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(JTSurfaceVariant)
        )
        Box(
          modifier = Modifier
            .fillMaxWidth(0.92f)
            .height(16.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(JTSurfaceVariant)
        )
        Box(
          modifier = Modifier
            .fillMaxWidth(0.65f)
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(JTSurfaceVariant)
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .width(72.dp)
              .height(18.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(JTSurfaceVariant)
          )
          Box(
            modifier = Modifier
              .size(30.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(JTSurfaceVariant)
          )
        }
      }
    }
  }
}

@Composable
fun PaginationFooter(
  isLoadingMore: Boolean,
  paginationError: String?,
  hasMore: Boolean,
  itemCount: Int,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier
) {
  when {
    isLoadingMore -> {
      Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFF3F4F6),
        border = BorderStroke(1.dp, JTBorder),
        modifier = modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 10.dp)
          .testTag("pagination_loading_footer")
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = JTOrange,
            strokeWidth = 2.2.dp
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = "Loading more products...",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = JTTextSecondary
          )
        }
      }
    }
    paginationError != null -> {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFFF1F0),
        border = BorderStroke(1.dp, Color(0xFFFECDCA)),
        modifier = modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 10.dp)
          .testTag("pagination_error_footer")
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = paginationError,
            color = Color(0xFF912018),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
          )
          Spacer(modifier = Modifier.width(10.dp))
          TextButton(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = 48.dp).testTag("pagination_retry_button")
          ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = JTOrangeDark, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Retry", color = JTOrangeDark, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
    !hasMore && itemCount > 0 -> {
      Text(
        text = "You have seen all $itemCount products",
        fontSize = 12.sp,
        color = JTTextSecondary,
        textAlign = TextAlign.Center,
        modifier = modifier
          .fillMaxWidth()
          .padding(vertical = 14.dp, horizontal = 16.dp)
          .testTag("pagination_end_footer")
      )
    }
  }
}

@Composable
fun FriendlyErrorState(
  title: String,
  message: String,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
  actionLabel: String = "Try Again"
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = Color.White,
    border = BorderStroke(1.dp, JTBorder),
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 16.dp)
      .testTag("friendly_error_state")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(JTOrangeContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Rounded.ErrorOutline,
          contentDescription = null,
          tint = JTOrange,
          modifier = Modifier.size(26.dp)
        )
      }
      Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = JTTextPrimary,
        textAlign = TextAlign.Center
      )
      Text(
        text = message,
        fontSize = 12.sp,
        color = JTTextSecondary,
        textAlign = TextAlign.Center
      )
      Button(
        onClick = onRetry,
        colors = ButtonDefaults.buttonColors(containerColor = JTOrange, contentColor = Color.White),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.heightIn(min = 48.dp).testTag("friendly_error_retry_button")
      ) {
        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(actionLabel, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
fun FriendlyEmptyState(
  title: String,
  message: String,
  icon: ImageVector = Icons.Rounded.Inventory2,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = JTSurfaceVariant,
    border = BorderStroke(1.dp, JTBorder),
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 16.dp)
      .testTag("friendly_empty_state")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(Color.White),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = JTOrange,
          modifier = Modifier.size(26.dp)
        )
      }
      Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = JTTextPrimary,
        textAlign = TextAlign.Center
      )
      Text(
        text = message,
        fontSize = 12.sp,
        color = JTTextSecondary,
        textAlign = TextAlign.Center
      )
      if (!actionLabel.isNullOrBlank() && onAction != null) {
        OutlinedButton(
          onClick = onAction,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.heightIn(min = 48.dp).testTag("friendly_empty_action_button")
        ) {
          Text(actionLabel, color = JTOrangeDark, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}
