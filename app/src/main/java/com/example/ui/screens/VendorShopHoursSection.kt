package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.supabase.DailyShopHours
import com.example.data.supabase.ShopDayOfWeek
import com.example.data.supabase.ShopScheduleInfo
import com.example.data.supabase.ShopTimeParser
import com.example.data.supabase.WeeklyShopHours
import com.example.localization.AppLanguageManager
import com.example.localization.appText
import com.example.ui.components.AppCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.components.StatusTone
import com.example.ui.theme.VendorColors
import com.example.ui.theme.VendorShapes
import com.example.ui.theme.VendorSpacing

private data class PendingTimeEdit(
  val day: ShopDayOfWeek,
  val isOpeningTime: Boolean,
  val currentTime24: String
)

@Composable
fun VendorShopAvailabilityAndHoursSection(
  shopIsOpen: Boolean,
  weeklyHours: WeeklyShopHours,
  enabled: Boolean,
  onShopIsOpenChange: (Boolean) -> Unit,
  onWeeklyHoursChange: (WeeklyShopHours) -> Unit
) {
  var editingTime by remember { mutableStateOf<PendingTimeEdit?>(null) }

  val liveStatus = remember(shopIsOpen, weeklyHours) {
    ShopScheduleInfo(
      sellerId = "self",
      shopIsOpen = shopIsOpen,
      weeklyHours = weeklyHours
    ).evaluateAt()
  }

  Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
    // Emergency Close / Shop Enabled Toggle Card
    AppCard(
      borderColor = if (shopIsOpen) VendorColors.BorderSubtle else VendorColors.DangerRed.copy(alpha = 0.4f)
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(VendorShapes.iconBox)
              .background(if (shopIsOpen) VendorColors.SuccessGreenBg else VendorColors.DangerRedBg),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (shopIsOpen) Icons.Rounded.Storefront else Icons.Rounded.PowerSettingsNew,
              contentDescription = null,
              tint = if (shopIsOpen) VendorColors.SuccessGreen else VendorColors.DangerRed,
              modifier = Modifier.size(22.dp)
            )
          }
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = appText("Shop availability & emergency close", "دکان کی دستیابی اور ہنگامی بندش"),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = VendorColors.TextPrimary
            )
            Text(
              text = appText(
                "All open/closed checks use Pakistan Standard Time (Asia/Karachi).",
                "کھلنے اور بند ہونے کا تمام حساب پاکستان کے معیاری وقت (Asia/Karachi) کے مطابق ہوتا ہے۔"
              ),
              style = MaterialTheme.typography.bodySmall,
              color = VendorColors.TextSecondary
            )
          }
        }

        Surface(
          shape = VendorShapes.input,
          color = if (shopIsOpen) VendorColors.Background else VendorColors.DangerRedBg.copy(alpha = 0.45f),
          border = BorderStroke(
            1.dp,
            if (shopIsOpen) VendorColors.BorderSubtle else VendorColors.DangerRed.copy(alpha = 0.35f)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .clip(VendorShapes.input)
            .clickable(enabled = enabled) { onShopIsOpenChange(!shopIsOpen) }
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = VendorSpacing.sm, vertical = VendorSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
          ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = if (shopIsOpen) {
                  appText("Shop enabled (follow weekly schedule)", "دکان فعال ہے (ہفتہ وار اوقات کے مطابق)")
                } else {
                  appText("Emergency close active", "ہنگامی بندش فعال ہے")
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (shopIsOpen) VendorColors.TextPrimary else VendorColors.DangerRed
              )
              Text(
                text = if (shopIsOpen) {
                  appText(
                    "Turn off to immediately close your shop to customers.",
                    "صارفین کے لیے اپنی دکان فوری طور پر بند کرنے کے لیے اسے بند کریں۔"
                  )
                } else {
                  appText(
                    "Customers currently see this shop as Closed and cannot place orders.",
                    "صارفین کو فی الحال یہ دکان بند نظر آ رہی ہے اور وہ آرڈر نہیں دے سکتے۔"
                  )
                },
                style = MaterialTheme.typography.bodySmall,
                color = VendorColors.TextSecondary
              )
            }
            Switch(
              checked = shopIsOpen,
              onCheckedChange = { if (enabled) onShopIsOpenChange(it) },
              enabled = enabled,
              colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = VendorColors.SuccessGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = VendorColors.DangerRed.copy(alpha = 0.7f)
              ),
              modifier = Modifier.testTag("vendor_shop_is_open_switch")
            )
          }
        }

        // Live Karachi status summary row
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(VendorSpacing.xs)
        ) {
          StatusBadge(
            text = if (liveStatus.isOpen) appText("Open now", "ابھی کھلی ہے") else appText("Closed now", "ابھی بند ہے"),
            tone = if (liveStatus.isOpen) StatusTone.SUCCESS else StatusTone.DANGER,
            icon = if (liveStatus.isOpen) Icons.Rounded.CheckCircle else Icons.Rounded.EventBusy
          )
          Text(
            text = liveStatus.detailLabel,
            style = MaterialTheme.typography.bodySmall,
            color = VendorColors.TextSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }

    // Weekly Opening Hours (Monday – Sunday)
    AppCard {
      Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
        SectionHeader(
          title = appText("Weekly opening hours", "ہفتہ وار کھلنے کے اوقات"),
          subtitle = appText(
            "Configure Monday to Sunday separately (PKT • Asia/Karachi)",
            "پیر سے اتوار تک الگ الگ اوقات مقرر کریں (پاکستان وقت)"
          ),
          actionLabel = appText("Reset defaults", "ڈیفالٹ بحال کریں"),
          onActionClick = if (enabled) {
            { onWeeklyHoursChange(WeeklyShopHours.defaults()) }
          } else null
        )

        val orderedDays = weeklyHours.orderedDays()
        orderedDays.forEachIndexed { index, dayHours ->
          VendorDayScheduleRow(
            dayHours = dayHours,
            enabled = enabled,
            onToggleOpen = { open ->
              onWeeklyHoursChange(weeklyHours.withDayUpdated(dayHours.day, isOpen = open))
            },
            onEditOpenTime = {
              editingTime = PendingTimeEdit(
                day = dayHours.day,
                isOpeningTime = true,
                currentTime24 = dayHours.openTime
              )
            },
            onEditCloseTime = {
              editingTime = PendingTimeEdit(
                day = dayHours.day,
                isOpeningTime = false,
                currentTime24 = dayHours.closeTime
              )
            }
          )
          if (index < orderedDays.lastIndex) {
            HorizontalDivider(color = VendorColors.Divider)
          }
        }
      }
    }
  }

  val pending = editingTime
  if (pending != null) {
    ShopTimePickerDialog(
      title = if (pending.isOpeningTime) {
        appText(
          "${pending.day.englishName} – Opening time",
          "${pending.day.urduName} – کھلنے کا وقت"
        )
      } else {
        appText(
          "${pending.day.englishName} – Closing time",
          "${pending.day.urduName} – بند ہونے کا وقت"
        )
      },
      initialTime24 = pending.currentTime24,
      onDismiss = { editingTime = null },
      onConfirm = { newTime24 ->
        val updated = if (pending.isOpeningTime) {
          weeklyHours.withDayUpdated(pending.day, openTime = newTime24)
        } else {
          weeklyHours.withDayUpdated(pending.day, closeTime = newTime24)
        }
        onWeeklyHoursChange(updated)
        editingTime = null
      }
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VendorDayScheduleRow(
  dayHours: DailyShopHours,
  enabled: Boolean,
  onToggleOpen: (Boolean) -> Unit,
  onEditOpenTime: () -> Unit,
  onEditCloseTime: () -> Unit
) {
  val dayKey = dayHours.day.key
  val hasInvalidRange = dayHours.isOpen && !dayHours.isValidRange()

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
      .testTag("vendor_hours_day_$dayKey"),
    verticalArrangement = Arrangement.spacedBy(VendorSpacing.xs)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VendorSpacing.xs),
        modifier = Modifier.weight(1f)
      ) {
        Text(
          text = dayHours.day.localizedName(),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = VendorColors.TextPrimary
        )
        StatusBadge(
          text = if (dayHours.isOpen) appText("Open", "کھلا") else appText("Closed", "بند"),
          tone = if (dayHours.isOpen) StatusTone.SUCCESS else StatusTone.NEUTRAL
        )
      }
      Switch(
        checked = dayHours.isOpen,
        onCheckedChange = { if (enabled) onToggleOpen(it) },
        enabled = enabled,
        colors = SwitchDefaults.colors(
          checkedThumbColor = Color.White,
          checkedTrackColor = VendorColors.PrimaryOrange
        ),
        modifier = Modifier.testTag("vendor_hours_toggle_$dayKey")
      )
    }

    if (dayHours.isOpen) {
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VendorSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(VendorSpacing.xs)
      ) {
        TimeSelectorPill(
          label = appText("Opens", "کھلنے کا وقت"),
          value = dayHours.formattedOpenTime(),
          enabled = enabled,
          onClick = onEditOpenTime,
          modifier = Modifier
            .weight(1f, fill = true)
            .testTag("vendor_hours_open_$dayKey")
        )
        TimeSelectorPill(
          label = appText("Closes", "بند ہونے کا وقت"),
          value = dayHours.formattedCloseTime(),
          enabled = enabled,
          onClick = onEditCloseTime,
          modifier = Modifier
            .weight(1f, fill = true)
            .testTag("vendor_hours_close_$dayKey")
        )
      }
      if (hasInvalidRange) {
        Text(
          text = appText(
            "Closing time must be later than opening time.",
            "بند ہونے کا وقت کھلنے کے وقت کے بعد ہونا چاہیے۔"
          ),
          style = MaterialTheme.typography.labelSmall,
          color = VendorColors.DangerRed
        )
      }
    } else {
      Text(
        text = appText(
          "Closed all day on ${dayHours.day.englishName}",
          "${dayHours.day.urduName} کو دکان پورا دن بند رہے گی"
        ),
        style = MaterialTheme.typography.bodySmall,
        color = VendorColors.TextSecondary
      )
    }
  }
}

@Composable
private fun TimeSelectorPill(
  label: String,
  value: String,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = VendorShapes.input,
    color = VendorColors.Background,
    border = BorderStroke(1.dp, VendorColors.BorderSubtle),
    modifier = modifier.heightIn(min = 48.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = VendorSpacing.sm, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Icon(
        imageVector = Icons.Rounded.Schedule,
        contentDescription = null,
        tint = VendorColors.PrimaryOrange,
        modifier = Modifier.size(16.dp)
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          color = VendorColors.TextSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = value,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          color = VendorColors.TextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShopTimePickerDialog(
  title: String,
  initialTime24: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit
) {
  val parsed = remember(initialTime24) {
    ShopTimeParser.parseLocalTime(initialTime24) ?: java.time.LocalTime.of(9, 0)
  }
  val timePickerState = rememberTimePickerState(
    initialHour = parsed.hour,
    initialMinute = parsed.minute,
    is24Hour = false
  )
  var showPresetSlots by remember { mutableStateOf(true) }

  val presetSlots = remember {
    buildList {
      for (h in 6..23) {
        add(ShopTimeParser.fromHourMinute(h, 0))
        if (h < 23) add(ShopTimeParser.fromHourMinute(h, 30))
      }
      add("23:59")
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VendorSpacing.xs)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Surface(
            onClick = { showPresetSlots = true },
            shape = RoundedCornerShape(8.dp),
            color = if (showPresetSlots) VendorColors.PrimaryOrangeTint else VendorColors.Background,
            border = BorderStroke(
              1.dp,
              if (showPresetSlots) VendorColors.PrimaryOrange else VendorColors.BorderSubtle
            ),
            modifier = Modifier.weight(1f)
          ) {
            Text(
              text = appText("Quick times", "فوری اوقات"),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = if (showPresetSlots) VendorColors.PrimaryOrange else VendorColors.TextSecondary,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
          }
          Surface(
            onClick = { showPresetSlots = false },
            shape = RoundedCornerShape(8.dp),
            color = if (!showPresetSlots) VendorColors.PrimaryOrangeTint else VendorColors.Background,
            border = BorderStroke(
              1.dp,
              if (!showPresetSlots) VendorColors.PrimaryOrange else VendorColors.BorderSubtle
            ),
            modifier = Modifier.weight(1f)
          ) {
            Text(
              text = appText("Custom clock", "کسٹم گھڑی"),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = if (!showPresetSlots) VendorColors.PrimaryOrange else VendorColors.TextSecondary,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
          }
        }

        if (showPresetSlots) {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 280.dp)
              .testTag("shop_time_slots_list"),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            items(presetSlots, key = { it }) { slot24 ->
              val isSelected = slot24 == ShopTimeParser.normalize24Hour(initialTime24)
              Surface(
                onClick = { onConfirm(slot24) },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) VendorColors.PrimaryOrangeTint else VendorColors.Background,
                border = BorderStroke(
                  1.dp,
                  if (isSelected) VendorColors.PrimaryOrange else VendorColors.BorderSubtle
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("shop_time_slot_$slot24")
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = ShopTimeParser.format12Hour(slot24),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) VendorColors.PrimaryOrange else VendorColors.TextPrimary
                  )
                  if (isSelected) {
                    Icon(
                      imageVector = Icons.Rounded.CheckCircle,
                      contentDescription = null,
                      tint = VendorColors.PrimaryOrange,
                      modifier = Modifier.size(18.dp)
                    )
                  }
                }
              }
            }
          }
        } else {
          Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
          ) {
            TimePicker(state = timePickerState)
          }
        }
      }
    },
    confirmButton = {
      if (!showPresetSlots) {
        TextButton(
          onClick = {
            onConfirm(ShopTimeParser.fromHourMinute(timePickerState.hour, timePickerState.minute))
          }
        ) {
          Text(appText("Confirm", "تصدیق کریں"))
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(appText("Cancel", "منسوخ کریں"))
      }
    }
  )
}
