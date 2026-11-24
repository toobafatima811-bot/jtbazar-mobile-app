package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.supabase.ShopScheduleStatus
import com.example.data.supabase.VendorShopCategoryOption
import com.example.data.supabase.WeeklyShopHours
import com.example.localization.appText
import com.example.ui.theme.JTBorder
import com.example.ui.theme.JTEmerald
import com.example.ui.theme.JTEmeraldContainer
import com.example.ui.theme.JTNavyContainer
import com.example.ui.theme.JTNavyPrimary
import com.example.ui.theme.JTOrange
import com.example.ui.theme.JTOrangeBorder
import com.example.ui.theme.JTOrangeContainer
import com.example.ui.theme.JTOrangeDark
import com.example.ui.theme.JTRed
import com.example.ui.theme.JTRedContainer
import com.example.ui.theme.JTSurfaceVariant
import com.example.ui.theme.JTTextPrimary
import com.example.ui.theme.JTTextSecondary

@Composable
fun PublicShopStatusPill(
  status: ShopScheduleStatus,
  modifier: Modifier = Modifier
) {
  val bg = if (status.isOpen) JTEmeraldContainer else JTRedContainer
  val fg = if (status.isOpen) JTEmerald else JTRed

  Surface(
    shape = RoundedCornerShape(50),
    color = bg,
    modifier = modifier.semantics(mergeDescendants = true) {}
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
      Box(
        modifier = Modifier
          .size(7.dp)
          .clip(CircleShape)
          .background(fg)
      )
      Text(
        text = status.badgeLabel,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = fg
      )
    }
  }
}

@Composable
fun PublicShopClosedBanner(
  status: ShopScheduleStatus,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = JTRedContainer.copy(alpha = 0.7f),
    border = BorderStroke(1.dp, JTRed.copy(alpha = 0.35f)),
    modifier = modifier
      .fillMaxWidth()
      .testTag("public_shop_closed_banner")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Icon(
        imageVector = Icons.Rounded.EventBusy,
        contentDescription = null,
        tint = JTRed,
        modifier = Modifier.size(22.dp)
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = if (!status.shopIsOpen) {
            appText(
              "Shop temporarily closed",
              "دکان عارضی طور پر بند ہے"
            )
          } else {
            appText(
              "Shop is currently closed",
              "دکان اس وقت بند ہے"
            )
          },
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = JTRed
        )
        Text(
          text = if (!status.shopIsOpen) {
            appText(
              "The vendor has temporarily closed this shop. You can still browse all products, but orders cannot be placed until the shop reopens.",
              "فروخت کنندہ نے یہ دکان عارضی طور پر بند کی ہے۔ آپ تمام مصنوعات دیکھ سکتے ہیں، لیکن دکان دوبارہ کھلنے تک آرڈر نہیں دیا جا سکتا۔"
            )
          } else {
            appText(
              "${status.detailLabel}. You can still browse all products, and place your order during opening hours.",
              "${status.detailLabel}۔ آپ تمام مصنوعات دیکھ سکتے ہیں اور کھلنے کے اوقات میں آرڈر دے سکتے ہیں۔"
            )
          },
          style = MaterialTheme.typography.bodySmall,
          color = JTTextPrimary
        )
      }
    }
  }
}

@Composable
fun PublicShopWeeklyHoursCard(
  weeklyHours: WeeklyShopHours,
  status: ShopScheduleStatus,
  isRefreshingSchedule: Boolean,
  scheduleError: String?,
  onRetrySchedule: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, JTBorder),
    modifier = modifier
      .fillMaxWidth()
      .testTag("public_shop_weekly_hours_card")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f)
        ) {
          Icon(
            imageVector = Icons.Rounded.Schedule,
            contentDescription = null,
            tint = JTOrange,
            modifier = Modifier.size(20.dp)
          )
          Column {
            Text(
              text = appText("Weekly opening hours", "ہفتہ وار کھلنے کے اوقات"),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = JTTextPrimary
            )
            Text(
              text = appText("Pakistan Standard Time (Asia/Karachi)", "پاکستان کا معیاری وقت (Asia/Karachi)"),
              style = MaterialTheme.typography.labelSmall,
              color = JTTextSecondary
            )
          }
        }
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = JTNavyContainer
        ) {
          Text(
            text = if (isRefreshingSchedule) appText("Updating...", "اپڈیٹ ہو رہا ہے...") else appText("PKT", "پاکستان وقت"),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = JTNavyPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }

      if (!scheduleError.isNullOrBlank()) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = JTOrangeContainer,
          border = BorderStroke(1.dp, JTOrangeBorder),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.Info,
              contentDescription = null,
              tint = JTOrangeDark,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = scheduleError,
              style = MaterialTheme.typography.bodySmall,
              color = JTTextPrimary,
              modifier = Modifier.weight(1f)
            )
            OutlinedButton(
              onClick = onRetrySchedule,
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
              modifier = Modifier
                .heightIn(min = 36.dp)
                .testTag("public_shop_schedule_retry")
            ) {
              Text(appText("Retry", "دوبارہ کوشش"), style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }

      HorizontalDivider(color = JTBorder)

      val days = weeklyHours.orderedDays()
      days.forEachIndexed { index, dayHours ->
        val isToday = dayHours.day == status.todayDay
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = if (isToday) JTOrangeContainer else Color.Transparent,
          border = if (isToday) BorderStroke(1.dp, JTOrangeBorder) else null,
          modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag("public_shop_hours_row_${dayHours.day.key}")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.weight(1f)
            ) {
              Text(
                text = dayHours.day.localizedName(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                color = if (isToday) JTOrangeDark else JTTextPrimary
              )
              if (isToday) {
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = JTOrange,
                  modifier = Modifier.testTag("public_shop_hours_today_badge")
                ) {
                  Text(
                    text = appText("Today", "آج"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }
            }
            Text(
              text = dayHours.formattedRange(),
              style = MaterialTheme.typography.bodySmall,
              fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
              color = when {
                !dayHours.isOpen -> JTRed
                isToday -> JTOrangeDark
                else -> JTTextPrimary
              }
            )
          }
        }
        if (index < days.lastIndex) {
          HorizontalDivider(color = JTBorder.copy(alpha = 0.55f))
        }
      }
    }
  }
}

@Composable
fun PublicShopCategoryFilterStrip(
  totalActiveCount: Int,
  categories: List<VendorShopCategoryOption>,
  selectedCategoryId: String?,
  onSelectCategory: (String?) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text(
      text = appText("Shop categories", "دکان کی اقسام"),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = JTTextPrimary
    )
    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("public_shop_category_strip"),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(end = 8.dp)
    ) {
      item(key = "all_products") {
        val isSelected = selectedCategoryId == null
        FilterChip(
          selected = isSelected,
          onClick = { onSelectCategory(null) },
          label = {
            Text(
              text = appText("All Products ($totalActiveCount)", "تمام مصنوعات ($totalActiveCount)"),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = JTOrange,
            selectedLabelColor = Color.White,
            containerColor = JTSurfaceVariant,
            labelColor = JTTextPrimary
          ),
          border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = JTBorder,
            selectedBorderColor = JTOrange
          ),
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("public_shop_category_all")
        )
      }

      items(categories, key = { it.id }) { option ->
        val isSelected = selectedCategoryId == option.id
        FilterChip(
          selected = isSelected,
          onClick = { onSelectCategory(option.id) },
          label = {
            Text(
              text = "${option.title} (${option.productCount})",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = JTOrange,
            selectedLabelColor = Color.White,
            containerColor = JTSurfaceVariant,
            labelColor = JTTextPrimary
          ),
          border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = JTBorder,
            selectedBorderColor = JTOrange
          ),
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("public_shop_category_${option.id}")
        )
      }
    }
  }
}
